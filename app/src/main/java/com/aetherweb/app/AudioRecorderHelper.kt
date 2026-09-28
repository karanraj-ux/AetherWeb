package com.aetherweb.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.concurrent.thread

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var outputFile: File? = null
        private set

    @Volatile
    private var isPreparing = false
    @Volatile
    private var isCancelRequested = false
    private val lock = Any()

    fun startRecording() {
        synchronized(lock) {
            if (isPreparing || recorder != null) return
            isPreparing = true
            isCancelRequested = false
        }
        
        thread(name = "AudioRecorder-Worker") {
            var tempRecorder: MediaRecorder? = null
            var tempFile: File? = null
            try {
                val fileName = "audio_msg_${System.currentTimeMillis()}.m4a"
                val newOutputFile = File(context.cacheDir, fileName)
                tempFile = newOutputFile
                
                val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                tempRecorder = newRecorder

                // Use VOICE_COMMUNICATION or CAMCORDER for enhanced clarity and acoustic tuning
                val source = MediaRecorder.AudioSource.VOICE_COMMUNICATION
                newRecorder.setAudioSource(source)
                newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                newRecorder.setAudioSamplingRate(44100)
                newRecorder.setAudioEncodingBitRate(96000)
                newRecorder.setOutputFile(newOutputFile.absolutePath)
                
                newRecorder.prepare()

                synchronized(lock) {
                    if (isCancelRequested) {
                        try { newRecorder.release() } catch (e: Exception) {}
                        tempFile.delete()
                        recorder = null
                        outputFile = null
                        isPreparing = false
                        return@thread
                    }
                    newRecorder.start()
                    outputFile = newOutputFile
                    recorder = newRecorder
                    isPreparing = false
                }
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Start failed", e)
                try {
                    tempRecorder?.release()
                } catch (ex: Exception) {}
                tempFile?.delete()
                synchronized(lock) {
                    recorder = null
                    outputFile = null
                    isPreparing = false
                }
            }
        }
    }

    fun stopRecording(): File? {
        synchronized(lock) {
            if (isPreparing) {
                // Still preparing in background thread; flag it to abort cleanly
                isCancelRequested = true
                isPreparing = false
                try {
                    recorder?.release()
                } catch (e: Exception) {}
                recorder = null
                outputFile?.delete()
                outputFile = null
                return null
            }
        }
        
        var validFile = outputFile
        synchronized(lock) {
            try {
                recorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Stop failed", e)
                validFile?.delete()
                validFile = null
            } finally {
                recorder = null
            }
        }
        
        // Final size check
        if (validFile != null && validFile.exists() && validFile.length() > 0) {
            return validFile
        } else {
            validFile?.delete()
            outputFile = null
            return null
        }
    }
}
