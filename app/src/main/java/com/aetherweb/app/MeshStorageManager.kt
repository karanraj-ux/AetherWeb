package com.aetherweb.app

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

data class CacheSweepResult(
    val totalFreedBytes: Long,
    val filesDeleted: Int,
    val currentCacheSizeBytes: Long,
    val currentFileCount: Int,
    val thresholdEnforced: Boolean
)

object MeshStorageManager {
    private const val TAG = "MeshStorageManager"
    private const val CACHE_DIR_NAME = "mesh_cache"
    const val MAX_CACHE_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB max cache cap
    const val PURGE_TARGET_BYTES = 60 * 1024 * 1024L   // Purge down to 60 MB
    private const val MAX_FILE_AGE_MS = 24 * 60 * 60 * 1000L   // 24 hours age limit
    private const val TEMP_AUDIO_AGE_MS = 6 * 60 * 60 * 1000L  // 6 hours limit for temp audio

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCacheFile(context: Context, fileName: String): File {
        return File(getCacheDir(context), sanitizeFileName(fileName))
    }

    /**
     * Sanitizes fileName to avoid directory traversal or invalid characters.
     */
    fun sanitizeFileName(rawName: String): String {
        val cleanName = rawName.substringAfterLast("/").substringAfterLast("\\").trim()
        return if (cleanName.isBlank()) "mesh_file_${System.currentTimeMillis()}" else cleanName
    }

    /**
     * Resolves local file if available in cache or legacy shared directory.
     */
    fun findLocalFile(context: Context, urlOrName: String): File? {
        val fileName = sanitizeFileName(urlOrName)
        
        // 1. Primary: Check context.cacheDir/mesh_cache/
        val cacheFile = File(getCacheDir(context), fileName)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }

        // 2. Secondary: Check context.filesDir/shared_files/ (legacy fallback)
        val legacyFile = File(File(context.filesDir, "shared_files"), fileName)
        if (legacyFile.exists() && legacyFile.length() > 0) {
            return legacyFile
        }

        // 3. Tertiary: If urlOrName was a direct local path or URI
        if (urlOrName.startsWith("/")) {
            val directFile = File(urlOrName)
            if (directFile.exists() && directFile.length() > 0) return directFile
        }

        return null
    }

    /**
     * Returns a content:// FileProvider URI for direct local sharing or opening.
     */
    fun getFileProviderUri(context: Context, file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create FileProvider URI for ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Saves an incoming Uri (e.g., picked photo or document) into context.cacheDir/mesh_cache/.
     */
    suspend fun saveUriToCache(context: Context, uri: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
        var fileName = getDisplayNameFromUri(context, uri) ?: "mesh_upload_${System.currentTimeMillis()}"
        val mimeType = context.contentResolver.getType(uri) ?: getMimeType(fileName)
        
        // Ensure extension exists
        if (!fileName.contains(".") && mimeType.isNotBlank()) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (ext != null) {
                fileName += ".$ext"
            } else if (mimeType.startsWith("image/")) {
                fileName += ".jpg"
            }
        }

        fileName = sanitizeFileName(fileName)
        val targetFile = File(getCacheDir(context), fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        // Trigger asynchronous background cache purge check
        autoPurgeCache(context)

        Pair(targetFile, fileName)
    }

    /**
     * Streams an InputStream into context.cacheDir/mesh_cache/ under the given fileName.
     */
    suspend fun saveStreamToCache(context: Context, fileName: String, inputStream: InputStream): File = withContext(Dispatchers.IO) {
        val cleanName = sanitizeFileName(fileName)
        val targetFile = File(getCacheDir(context), cleanName)
        
        FileOutputStream(targetFile).use { output ->
            inputStream.copyTo(output)
        }

        autoPurgeCache(context)
        targetFile
    }

    /**
     * Resumable Byte-Range file downloader.
     * Uses .part staging file and HTTP Range headers.
     * If connection drops midway (e.g. peer walks out of WiFi hotspot),
     * reconnecting will resume from the exact byte rather than starting over.
     * Completely safe across all Android versions.
     */
    suspend fun downloadFileWithResume(
        context: Context,
        urlStr: String,
        targetFileName: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val cleanName = sanitizeFileName(targetFileName)
        val finalFile = getCacheFile(context, cleanName)
        if (finalFile.exists() && finalFile.length() > 0) {
            return@withContext finalFile
        }

        val partFile = File(getCacheDir(context), "$cleanName.part")
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        val url = java.net.URL(urlStr)
        val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }

        val responseCode = connection.responseCode
        val isPartial = (responseCode == java.net.HttpURLConnection.HTTP_PARTIAL)
        val isOk = (responseCode == java.net.HttpURLConnection.HTTP_OK)

        if (!isPartial && !isOk) {
            if (responseCode == 416) { // Range Not Satisfiable (file may already be complete)
                if (partFile.exists() && partFile.length() > 0) {
                    partFile.renameTo(finalFile)
                    return@withContext finalFile
                }
            }
            throw java.io.IOException("HTTP error $responseCode while downloading $urlStr")
        }

        val append = isPartial && existingBytes > 0L
        if (!append) {
            existingBytes = 0L
        }

        val contentLength = connection.contentLengthLong
        val totalBytes = if (contentLength > 0) existingBytes + contentLength else -1L

        connection.inputStream.use { input ->
            FileOutputStream(partFile, append).use { output ->
                val buffer = ByteArray(64 * 1024) // 64KB high-speed buffer
                var bytesRead: Int
                var currentDownloaded = existingBytes
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead
                    onProgress?.invoke(currentDownloaded, totalBytes)
                }
                output.flush()
            }
        }

        if (partFile.exists()) {
            if (finalFile.exists()) finalFile.delete()
            val renamed = partFile.renameTo(finalFile)
            if (!renamed) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
        }

        autoPurgeCache(context)
        finalFile
    }

    /**
     * Auto-purges temporary cached transfer files and enforces the 100MB media cache threshold.
     */
    fun autoPurgeCache(context: Context) {
        pruneCacheSweep(context)
    }

    /**
     * Enforces the 100MB media cache threshold with an automatic background pruner sweep:
     * 1. Evicts files older than 24 hours (and temp audio files older than 6 hours).
     * 2. If remaining cache exceeds 100MB (MAX_CACHE_SIZE_BYTES), evicts oldest files (LRU) until below 60MB.
     * 3. Logs sweep diagnostics and returns detailed result statistics.
     */
    fun pruneCacheSweep(context: Context): CacheSweepResult {
        var totalFreed = 0L
        var filesDeleted = 0
        var currentSize = 0L
        var currentCount = 0
        var thresholdEnforced = false

        try {
            val cacheDir = getCacheDir(context)
            val files = cacheDir.listFiles() ?: emptyArray()
            val now = System.currentTimeMillis()

            val survivingFiles = mutableListOf<File>()

            for (file in files) {
                if (!file.isFile) continue
                val fileSize = file.length()
                val age = now - file.lastModified()
                val isTempAudio = file.name.startsWith("mesh_voice_") || file.name.startsWith("temp_") || file.name.endsWith(".tmp")
                val isDownload = file.name.endsWith(".download")

                // Protect active in-progress transfers (< 10 minutes old) from eviction
                val isRecentActiveTransfer = (isDownload || isTempAudio) && age < (10 * 60 * 1000L)
                val isStrandedDownload = isDownload && age > (60 * 60 * 1000L)

                val shouldEvict = (age > MAX_FILE_AGE_MS) || (isTempAudio && age > TEMP_AUDIO_AGE_MS) || isStrandedDownload
                if (shouldEvict && !isRecentActiveTransfer) {
                    if (file.delete()) {
                        totalFreed += fileSize
                        filesDeleted++
                        Log.d(TAG, "Auto-purged expired cache file: ${file.name} (${formatFileSize(fileSize)})")
                    }
                } else {
                    currentSize += fileSize
                    // Do not mark active transfers as LRU eviction candidates
                    if (!isRecentActiveTransfer) {
                        survivingFiles.add(file)
                    }
                }
            }

            // Also inspect legacy filesDir/shared_files
            val legacyDir = File(context.filesDir, "shared_files")
            if (legacyDir.exists()) {
                val legacyFiles = legacyDir.listFiles() ?: emptyArray()
                for (lf in legacyFiles) {
                    if (!lf.isFile) continue
                    val lfSize = lf.length()
                    if (now - lf.lastModified() > MAX_FILE_AGE_MS && lf.name != "MeshChat.apk") {
                        if (lf.delete()) {
                            totalFreed += lfSize
                            filesDeleted++
                        }
                    }
                }
            }

            // Enforce the 100MB media cache threshold
            if (currentSize > MAX_CACHE_SIZE_BYTES) {
                thresholdEnforced = true
                survivingFiles.sortBy { it.lastModified() } // LRU (oldest first)
                val iterator = survivingFiles.iterator()
                while (iterator.hasNext()) {
                    val file = iterator.next()
                    val size = file.length()
                    if (file.delete()) {
                        currentSize -= size
                        totalFreed += size
                        filesDeleted++
                        iterator.remove()
                        Log.d(TAG, "Evicted cache file due to 100MB threshold: ${file.name}")
                    }
                    if (currentSize <= PURGE_TARGET_BYTES) break
                }
            }

            currentCount = survivingFiles.size
            if (filesDeleted > 0 || thresholdEnforced) {
                DiagnosticLogger.log(
                    "Storage Optimizer",
                    "Cache Sweep",
                    "Pruned $filesDeleted files, freed ${formatFileSize(totalFreed)}. Cache: ${formatFileSize(currentSize)} / 100MB",
                    EventStatus.SUCCESS
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache sweep", e)
            DiagnosticLogger.log("Storage Optimizer", "Sweep Error", "Cache sweep failed: ${e.message}", EventStatus.ERROR)
        }

        return CacheSweepResult(
            totalFreedBytes = totalFreed,
            filesDeleted = filesDeleted,
            currentCacheSizeBytes = currentSize,
            currentFileCount = currentCount,
            thresholdEnforced = thresholdEnforced
        )
    }

    /**
     * Returns total cache usage (bytes) and file count.
     */
    fun getCacheStats(context: Context): Pair<Long, Int> {
        return try {
            val cacheDir = getCacheDir(context)
            val files = cacheDir.listFiles() ?: emptyArray()
            var total = 0L
            var count = 0
            for (f in files) {
                if (f.isFile) {
                    total += f.length()
                    count++
                }
            }
            Pair(total, count)
        } catch (e: Exception) {
            Pair(0L, 0)
        }
    }

    /**
     * Network Throttling & Packet Compression:
     * Compresses media payloads (images) to ensure smooth delivery over weak 2G or satellite connections.
     * Weak 2G / Satellite Mode: Downsamples to max 800x800 and compresses at 60% quality (~40KB-80KB).
     * Normal Mode: Downsamples to max 1600x1600 and compresses at 80% quality.
     */
    suspend fun compressImageForTransfer(
        context: Context,
        sourceFile: File,
        isDataSaver: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        if (!isImageFile(sourceFile.name)) return@withContext sourceFile
        val originalSize = sourceFile.length()
        // If file is already tiny (< 80KB), skip compression
        if (originalSize < 80 * 1024) return@withContext sourceFile

        try {
            // First decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            val origWidth = options.outWidth
            val origHeight = options.outHeight

            if (origWidth <= 0 || origHeight <= 0) return@withContext sourceFile

            val maxDimension = if (isDataSaver) 800 else 1600
            val quality = if (isDataSaver) 60 else 80

            var sampleSize = 1
            var w = origWidth
            var h = origHeight
            while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Half memory footprint
            }

            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions) ?: return@withContext sourceFile

            // Scale if still exceeds max dimension
            val currentMax = maxOf(bitmap.width, bitmap.height)
            val finalBitmap = if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax.toFloat()
                val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled != bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }

            val baseName = sourceFile.nameWithoutExtension
            val optFileName = "opt_${baseName}_${if (isDataSaver) "2g" else "std"}.jpg"
            val targetFile = getCacheFile(context, optFileName)

            FileOutputStream(targetFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                fos.flush()
            }
            finalBitmap.recycle()

            val compressedSize = targetFile.length()
            val ratio = if (originalSize > 0) ((originalSize - compressedSize) * 100.0 / originalSize).toInt() else 0

            DiagnosticLogger.log(
                "Media Optimizer",
                "Payload Compression",
                "Optimized image for ${if (isDataSaver) "2G/Satellite" else "Mesh"}: ${formatFileSize(originalSize)} -> ${formatFileSize(compressedSize)} (-$ratio%)",
                EventStatus.SUCCESS
            )

            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Image compression failed, using original", e)
            sourceFile
        }
    }

    /**
     * Streams file directly into public Downloads/MeshChat/ folder via MediaStore on Android 10+
     * or public Downloads directory on legacy Android.
     */
    suspend fun saveFileToPublicDownloads(
        context: Context,
        sourceFile: File,
        displayName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = sanitizeFileName(displayName ?: sourceFile.name)
        val mimeType = getMimeType(fileName)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MeshChat")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Log.i(TAG, "Saved to public Downloads/MeshChat: $uri")
                    return@withContext uri
                }
            } else {
                // Legacy external storage (< Android 10)
                @Suppress("DEPRECATION")
                val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MeshChat")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, fileName)
                FileInputStream(sourceFile).use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                return@withContext Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file to public Downloads", e)
        }
        return@withContext null
    }

    /**
     * Streams image file directly into public Pictures/MeshChat/ folder via MediaStore.
     */
    suspend fun saveImageToPublicGallery(
        context: Context,
        sourceFile: File,
        displayName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = sanitizeFileName(displayName ?: sourceFile.name)
        val mimeType = getMimeType(fileName).let { if (it.startsWith("image/")) it else "image/jpeg" }

        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MeshChat")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Log.i(TAG, "Saved image to public Gallery: $uri")
                return@withContext uri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image to Gallery", e)
        }
        return@withContext null
    }

    /**
     * Robust MIME type determination for all common extensions.
     */
    fun getMimeType(fileNameOrUrl: String): String {
        val ext = getFileExtension(fileNameOrUrl).lowercase()
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mapped.isNullOrBlank()) return mapped

        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "json" -> "application/json"
            "txt", "log" -> "text/plain"
            "html", "htm" -> "text/html"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            else -> "application/octet-stream"
        }
    }

    fun getFileExtension(fileNameOrUrl: String): String {
        val clean = sanitizeFileName(fileNameOrUrl)
        return clean.substringAfterLast(".", "")
    }

    fun isImageFile(fileNameOrUrl: String): Boolean {
        val ext = getFileExtension(fileNameOrUrl).lowercase()
        return ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic") ||
                getMimeType(fileNameOrUrl).startsWith("image/")
    }

    fun isAudioFile(fileNameOrUrl: String): Boolean {
        val ext = getFileExtension(fileNameOrUrl).lowercase()
        return ext in listOf("m4a", "mp3", "aac", "wav", "ogg", "opus", "flac") ||
                fileNameOrUrl.contains("/audio_") ||
                fileNameOrUrl.contains("web_audio") ||
                (ext == "webm" && fileNameOrUrl.contains("audio")) ||
                getMimeType(fileNameOrUrl).startsWith("audio/")
    }

    fun isVideoFile(fileNameOrUrl: String): Boolean {
        val ext = getFileExtension(fileNameOrUrl).lowercase()
        return ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp") ||
                getMimeType(fileNameOrUrl).startsWith("video/")
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    /**
     * Returns a list of all files stored in the cache or legacy shared folder for the web file vault.
     */
    data class SharedFileInfo(
        val name: String,
        val size: Long,
        val formattedSize: String,
        val lastModified: Long,
        val isImage: Boolean,
        val isAudio: Boolean,
        val isVideo: Boolean
    )

    fun getAllSharedFiles(context: Context): List<SharedFileInfo> {
        val list = mutableListOf<SharedFileInfo>()
        val seen = mutableSetOf<String>()

        fun addFile(f: File) {
            if (!f.isFile || f.name.endsWith(".tmp") || f.name.endsWith(".download") || f.length() == 0L) return
            if (seen.add(f.name)) {
                list.add(
                    SharedFileInfo(
                        name = f.name,
                        size = f.length(),
                        formattedSize = formatFileSize(f.length()),
                        lastModified = f.lastModified(),
                        isImage = isImageFile(f.name),
                        isAudio = isAudioFile(f.name),
                        isVideo = isVideoFile(f.name)
                    )
                )
            }
        }

        try {
            getCacheDir(context).listFiles()?.forEach { addFile(it) }
            val legacyDir = File(context.filesDir, "shared_files")
            if (legacyDir.exists()) {
                legacyDir.listFiles()?.forEach { addFile(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing shared files", e)
        }

        list.sortByDescending { it.lastModified }
        return list
    }

    @SuppressLint("Range")
    private fun getDisplayNameFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            return cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error getting display name from cursor", e)
            }
        }
        return uri.lastPathSegment?.substringAfterLast("/")
    }
}
