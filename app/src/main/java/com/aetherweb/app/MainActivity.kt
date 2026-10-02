package com.aetherweb.app
import com.aetherweb.app.ui.screens.AetherRoot
import com.aetherweb.app.ui.screens.OnboardingScreens

import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import android.Manifest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aetherweb.app.ui.components.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetherweb.app.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.isGranted

class MainActivity : ComponentActivity() {
    companion object {
        var isAppInForeground = false
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private val viewModel: MeshViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // bitchat parity: stable mesh identity across restarts (idempotent)
        com.aetherweb.app.MeshNetworkManager.initPersistentIdentity(this)
        CallManager.init(this)
        MeshMusicManager.init(this)
        enableEdgeToEdge()
        setContent {
            val permissionsList = mutableListOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                permissionsList.add(android.Manifest.permission.BLUETOOTH_SCAN)
                permissionsList.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                permissionsList.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionsList.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
                permissionsList.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            val permissionsState = rememberMultiplePermissionsState(permissions = permissionsList)

            val canProceed = permissionsState.permissions.filter {
                it.permission != android.Manifest.permission.ACCESS_FINE_LOCATION &&
                it.permission != android.Manifest.permission.ACCESS_COARSE_LOCATION
            }.all { it.status.isGranted } && permissionsState.permissions.any {
                (it.permission == android.Manifest.permission.ACCESS_COARSE_LOCATION || 
                 it.permission == android.Manifest.permission.ACCESS_FINE_LOCATION) && it.status.isGranted
            }

            // Phase 2 (zero-friction onboarding): a warm, plain-language rationale shown ONCE
            // before the system permission prompts, so first-timers aren't ambushed.
            val permPrefs = remember { this@MainActivity.getSharedPreferences("aether_perm_prefs", MODE_PRIVATE) }
            var showPermRationale by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                if (!canProceed) {
                    if (permPrefs.getBoolean("perm_rationale_shown", false)) {
                        permissionsState.launchMultiplePermissionRequest()
                    } else {
                        showPermRationale = true
                    }
                }
            }

            if (showPermRationale) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("A quick heads-up ✋") },
                    text = {
                        Text(
                            "AetherWeb talks phone-to-phone with no internet, so it needs its radios:\n\n" +
                            "• Bluetooth — finds nearby phones and relays chat through the mesh\n" +
                            "• Wi-Fi — carries voice/video calls, music sync and file beams\n" +
                            "• Notifications — lets you know when messages arrive\n\n" +
                            "No accounts, no servers, no tracking — the radios never leave your control."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            permPrefs.edit().putBoolean("perm_rationale_shown", true).apply()
                            showPermRationale = false
                            permissionsState.launchMultiplePermissionRequest()
                        }) { Text("Got it, continue") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            permPrefs.edit().putBoolean("perm_rationale_shown", true).apply()
                            showPermRationale = false
                        }) { Text("Not now") }
                    }
                )
            }
            MyApplicationTheme {
                // Removed blocking UI completely.
                
                LaunchedEffect(Unit) {
                    if (true) {
                        val serviceIntent = Intent(this@MainActivity, MeshForegroundService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    }
                }
                

                    // First-run onboarding (3 steps), then the Rooms-first root.
                    val onboardPrefs = remember { this@MainActivity.getSharedPreferences("aether_onboarding", MODE_PRIVATE) }
                    var onboardingDone by remember { mutableStateOf(onboardPrefs.getBoolean("done", false)) }

                    if (!onboardingDone) {
                        OnboardingScreens(
                            viewModel = viewModel,
                            onDone = {
                                onboardPrefs.edit().putBoolean("done", true).apply()
                                onboardingDone = true
                            }
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                            AetherRoot(viewModel = viewModel)
                            com.aetherweb.app.ui.screens.CallScreenOverlay(
                                sendMessage = { payload ->
                                    com.aetherweb.app.SearchNetworkManager.meshRouter.routeLocalMessage(payload)
                                    com.aetherweb.app.SearchNetworkManager.webServerManager?.broadcastMessage(payload, com.aetherweb.app.SearchNetworkManager.localNodeId)
                                }
                            )
                        }
                    }
        }
    }
}
