package com.aetherweb.app.ui.screens

import androidx.compose.ui.unit.dp
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.SslErrorHandler
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.net.http.SslError
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass

/**
 * Returns the bundled HTML for host-rendered web content, or null if the URL
 * must go through HTTP (e.g. the portal guest preview).
 *
 * The APK already contains the IDE/Pool/Snake HTML, so the host renders it
 * directly instead of an HTTP round-trip via a possibly-stale IP.
 */
private fun bundledWebHtmlFor(url: String): String? {
    val path = try {
        android.net.Uri.parse(url).path ?: ""
    } catch (e: Exception) {
        ""
    }
    return when {
        path.endsWith("/ide") -> com.aetherweb.app.PocketCdnPacks.WEB_IDE_HTML
        path.endsWith("/pool") -> com.aetherweb.app.PocketCdnPacks.POOL_GAME_HTML
        path.endsWith("/snake") -> com.aetherweb.app.PocketCdnPacks.SNAKE_HTML
        else -> null
    }
}

@Composable
fun SharedMediaScreen(
    uiState: com.aetherweb.app.MeshState,
    modifier: Modifier = Modifier
) {
    val mediaType = uiState.sharedMediaType
    val viewModel: com.aetherweb.app.MeshViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val mediaUrl = uiState.sharedMediaUrl
    val mediaHostId = uiState.mediaHostId
    val isHost = com.aetherweb.app.MeshNetworkManager.localNodeId == mediaHostId
    val context = androidx.compose.ui.platform.LocalContext.current

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentWebUrl by remember { mutableStateOf(mediaUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val defaultHostIp = remember(uiState.hotspotIp) {
        if (uiState.hotspotIp.isNotBlank()) uiState.hotspotIp else "127.0.0.1"
    }

    val browserTabs = remember(defaultHostIp) {
        listOf(
            Triple("IDE", "💻 Code IDE", "http://$defaultHostIp:8080/ide"),
            Triple("Chat", "💬 WebChat", "http://$defaultHostIp:8080/chat"),
            Triple("Portal", "🌐 Mesh Portal", "http://$defaultHostIp:8080/"),
            Triple("Pool", "🎱 Pool", "http://$defaultHostIp:8080/pool"),
            Triple("Chess", "♟️ Chess", "http://$defaultHostIp:8080/chess"),
            Triple("Snake", "🐍 Snake", "http://$defaultHostIp:8080/snake")
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (mediaType == "web") {
                // Sleek Browser Bar & Tab Switcher
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        // URL Bar & Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { webViewInstance?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (canGoBack) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { webViewInstance?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Forward",
                                    tint = if (canGoForward) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(20.dp).rotate(180f)
                                )
                            }

                            IconButton(
                                onClick = { webViewInstance?.reload() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Address pill
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Offline Mesh",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentWebUrl.ifBlank { mediaUrl },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Open in external browser (Chrome / Safari / Firefox)
                            IconButton(
                                onClick = {
                                    try {
                                        val target = if (currentWebUrl.startsWith("http")) currentWebUrl else "http://$defaultHostIp:8080/ide"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(target))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open external browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.OpenInBrowser,
                                    contentDescription = "Open in Chrome/Browser",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Browsing Tabs Horizontal Row
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            items(browserTabs.size) { index ->
                                val (id, label, url) = browserTabs[index]
                                val isSelected = currentWebUrl.contains("/${id.lowercase()}") || (id == "Portal" && currentWebUrl.endsWith(":8080/"))
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        currentWebUrl = url
                                        webViewInstance?.loadUrl(url)
                                        viewModel.updateLocalSharedMedia("web", url, com.aetherweb.app.MeshNetworkManager.localNodeId, "local", 0, true)
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            } else if (isHost && mediaType != "none") {
                Button(
                    onClick = { viewModel.setSharedMedia("none") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Sharing & Clear Board")
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (mediaType == "web") {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        url?.let { currentWebUrl = it }
                                        canGoBack = canGoBack()
                                        canGoForward = canGoForward()
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                
                                val base = if (mediaUrl.startsWith("http")) mediaUrl else "http://$defaultHostIp:8080/ide"
                                val fullUrl = if (base.contains("sim/physics")) base + "?isHost=$isHost" else base
                                // Direct-load bundled games/IDE: the APK already contains the HTML,
                                // so the host renders it locally instead of an HTTP round-trip via a
                                // possibly-stale IP. Browser guests still use the server.
                                val bundledHtml = bundledWebHtmlFor(fullUrl)
                                if (bundledHtml != null) {
                                    loadDataWithBaseURL("http://127.0.0.1:8080/", bundledHtml, "text/html", "UTF-8", null)
                                    currentWebUrl = fullUrl
                                } else {
                                    loadUrl(fullUrl)
                                }
                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                            canGoBack = webView.canGoBack()
                            canGoForward = webView.canGoForward()
                            val base = if (mediaUrl.startsWith("http")) mediaUrl else "https://$mediaUrl"
                            val targetUrl = if (base.contains("sim/physics")) base + "?isHost=$isHost" else base
                            if (webView.url != targetUrl && mediaUrl.isNotBlank() && targetUrl != currentWebUrl) {
                                val bundledHtml = bundledWebHtmlFor(targetUrl)
                                if (bundledHtml != null) {
                                    webView.loadDataWithBaseURL("http://127.0.0.1:8080/", bundledHtml, "text/html", "UTF-8", null)
                                    currentWebUrl = targetUrl
                                } else {
                                    webView.loadUrl(targetUrl)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mediaType == "tictactoe") {
                    val vm: com.aetherweb.app.MeshViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    val state by vm.uiState.collectAsState()
                    com.aetherweb.app.ui.screens.TicTacToeScreen(
                        state = state.ticTacToeState,
                        onStateChange = { newState -> vm.broadcastTicTacToeState(newState) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mediaType == "connect4") {
                    val vm: com.aetherweb.app.MeshViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    val state by vm.uiState.collectAsState()
                    com.aetherweb.app.ui.screens.Connect4Screen(
                        state = state.connect4State,
                        onStateChange = { newState -> vm.broadcastConnect4State(newState) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mediaType == "ludo") {
                    val vm: com.aetherweb.app.MeshViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    val state by vm.uiState.collectAsState()
                    LudoScreen(
                        state = state.ludoState,
                        onStateChange = { newState -> vm.broadcastLudoState(newState) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mediaType == "chess") {
                    val vm: com.aetherweb.app.MeshViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    val state by vm.uiState.collectAsState()
                    ChessScreen(
                        state = state.chessState,
                        onStateChange = { newState -> vm.broadcastChessState(newState) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mediaType == "canvas") {
                    val vm: com.aetherweb.app.MeshViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    val state by vm.uiState.collectAsState()
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    com.aetherweb.app.ui.screens.CanvasScreen(
                        canvasPaths = state.canvasPaths,
                        lasers = state.lasers,
                        backgroundUrl = state.canvasBackground,
                        onSendCanvasMessage = { action, id, color, x, y, strokeWidth, data -> 
                            vm.sendCanvasMessage(action, id, color, x, y, strokeWidth, data) 
                        },
                        onUploadBackground = { uri -> vm.uploadCanvasBackground(uri) },
                        onSaveCanvas = { vm.saveCanvasToGallery(ctx) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Phase 1 Optimization: Floating In-Game PIP Chat Drawer (Active for all multiplayer games)
                if (mediaType in listOf("tictactoe", "connect4", "ludo", "chess", "web")) {
                    val vm: com.aetherweb.app.MeshViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    val state by vm.uiState.collectAsState()
                    GamePipChatOverlay(
                        messages = state.messages,
                        onSendMessage = { text -> vm.sendMessage(text) },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}
