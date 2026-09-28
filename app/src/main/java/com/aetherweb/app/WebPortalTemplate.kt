package com.aetherweb.app

/**
 * Modern Unified Web Portal Template for MeshChat
 * Provides a seamless Single Page Application (SPA) with persistent top & bottom navigation.
 * Views:
 * 1. [💬 Chat] - WhatsApp-style live mesh chat with audio recording, waveforms, media preview
 * 2. [📁 Vault] - Offline file repository (Grid & List with direct downloads)
 * 3. [🎮 Arcade] - Offline games (Tic-Tac-Toe, Chess, Snake, Pool, Connect 4, Smartboard)
 * 4. [📋 Tools] - Cross-platform clipboard, Web IDE & local mesh diagnostics
 *
 * Keeps the WebSocket (/ws) and live audio/video connection alive 100% of the time without reconnecting!
 */
object WebPortalTemplate {

    fun getHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <meta name="theme-color" content="#1f2c34">
    <title>MeshChat Portal</title>
    <style>
        :root {
            --bg-color: #0b141b;
            --header-bg: #1f2c34;
            --card-bg: #182229;
            --input-bg: #2a3942;
            --accent-green: #25d366;
            --accent-dark-green: #005d4b;
            --text-primary: #e9edef;
            --text-secondary: #8696a0;
            --border-color: #222d34;
            --msg-other: #1f2c34;
            --msg-me: #005d4b;
        }

        body.light-theme {
            --bg-color: #efeae2;
            --header-bg: #008069;
            --card-bg: #ffffff;
            --input-bg: #ffffff;
            --accent-green: #00a884;
            --accent-dark-green: #008069;
            --text-primary: #111b21;
            --text-secondary: #54656f;
            --border-color: #d1d7db;
            --msg-other: #ffffff;
            --msg-me: #d9fdd3;
        }

        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        html, body { margin: 0; padding: 0; height: 100%; width: 100%; overflow: hidden; background: var(--bg-color); color: var(--text-primary); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }

        #app-container { display: flex; flex-direction: column; height: 100%; width: 100%; position: relative; }

        /* Top Header */
        #top-header {
            background: var(--header-bg);
            color: var(--text-primary);
            padding: 10px 16px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 4px rgba(0,0,0,0.2);
            z-index: 50;
            flex-shrink: 0;
        }
        .header-title-box { display: flex; align-items: center; gap: 10px; }
        .header-avatar { width: 36px; height: 36px; border-radius: 50%; background: var(--accent-dark-green); color: white; display: flex; align-items: center; justify-content: center; font-size: 18px; font-weight: bold; }
        .header-text { display: flex; flex-direction: column; }
        .header-room { font-size: 15px; font-weight: 600; }
        .header-status { font-size: 11px; color: var(--accent-green); }

        .header-actions { display: flex; align-items: center; gap: 8px; }
        .action-btn { background: rgba(255,255,255,0.1); border: none; color: var(--text-primary); padding: 6px 12px; border-radius: 16px; font-size: 12px; font-weight: 600; cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; gap: 4px; }
        .action-btn:hover { background: rgba(255,255,255,0.18); }
        .action-btn.green { background: var(--accent-green); color: white; }

        /* Main View Container */
        #viewport {
            flex: 1;
            position: relative;
            overflow: hidden;
            background: var(--bg-color);
        }

        .page-view {
            position: absolute;
            top: 0; left: 0; right: 0; bottom: 0;
            display: none;
            flex-direction: column;
            overflow: hidden;
        }
        .page-view.active { display: flex; }

        /* Bottom Tab Navigation Bar */
        #bottom-nav {
            background: var(--header-bg);
            border-top: 1px solid var(--border-color);
            display: flex;
            justify-content: space-around;
            align-items: center;
            padding: 6px 0;
            z-index: 50;
            flex-shrink: 0;
        }
        .nav-item {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            flex: 1;
            padding: 4px 0;
            color: var(--text-secondary);
            font-size: 11px;
            font-weight: 600;
            cursor: pointer;
            border: none;
            background: transparent;
            transition: color 0.15s ease;
        }
        .nav-item .icon { font-size: 18px; margin-bottom: 2px; }
        .nav-item.active { color: var(--accent-green); }
        .nav-item.active .icon { transform: scale(1.1); }

        /* PAGE 1: CHAT VIEW */
        #messages-scroller {
            flex: 1;
            overflow-y: auto;
            padding: 16px;
            display: flex;
            flex-direction: column;
            gap: 8px;
            -webkit-overflow-scrolling: touch;
        }
        .date-pill { align-self: center; background: var(--card-bg); color: var(--text-secondary); font-size: 11px; border-radius: 8px; padding: 4px 12px; margin: 6px 0; font-weight: 600; }
        .msg { max-width: 82%; padding: 8px 12px; border-radius: 12px; word-wrap: break-word; line-height: 1.4; font-size: 14.5px; box-shadow: 0 1px 2px rgba(0,0,0,0.15); position: relative; }
        .msg-other { align-self: flex-start; background: var(--msg-other); color: var(--text-primary); border-top-left-radius: 2px; }
        .msg-me { align-self: flex-end; background: var(--msg-me); color: #fff; border-top-right-radius: 2px; }
        .msg-sender { font-size: 12px; color: var(--accent-green); font-weight: bold; margin-bottom: 2px; }
        .msg-time { font-size: 10px; color: var(--text-secondary); float: right; margin-left: 8px; margin-top: 4px; }

        #chat-input-area {
            background: var(--header-bg);
            border-top: 1px solid var(--border-color);
            padding: 8px 12px;
            display: flex;
            flex-direction: column;
            gap: 6px;
            flex-shrink: 0;
        }
        .chat-row { display: flex; gap: 8px; align-items: center; width: 100%; }
        .chat-input {
            flex: 1;
            background: var(--input-bg);
            color: var(--text-primary);
            border: none;
            padding: 10px 16px;
            border-radius: 24px;
            font-size: 15px;
            outline: none;
        }
        .send-round-btn {
            width: 40px; height: 40px;
            border-radius: 50%;
            border: none;
            background: var(--accent-green);
            color: white;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 17px;
            font-weight: bold;
            cursor: pointer;
            flex-shrink: 0;
        }
        .mic-round-btn {
            width: 40px; height: 40px;
            border-radius: 50%;
            border: none;
            background: var(--accent-dark-green);
            color: white;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            cursor: pointer;
            flex-shrink: 0;
        }

        /* Voice Note Recording Bar */
        #voice-record-bar {
            display: none;
            background: #202c33;
            color: #ef5350;
            padding: 8px 14px;
            justify-content: space-between;
            align-items: center;
            border-top: 1px solid var(--border-color);
            font-weight: bold;
            font-size: 13px;
        }

        /* PAGE 2: FILE VAULT */
        .vault-container { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 14px; }
        .vault-upload-card {
            background: var(--card-bg);
            border: 2px dashed var(--accent-green);
            border-radius: 12px;
            padding: 20px;
            text-align: center;
            cursor: pointer;
        }
        .files-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 10px; }
        .file-item-card {
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 10px;
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
            gap: 6px;
            overflow: hidden;
        }
        .file-icon { font-size: 32px; }
        .file-name { font-size: 12px; font-weight: 600; width: 100%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .file-size { font-size: 10px; color: var(--text-secondary); }
        .file-download-btn { background: var(--accent-green); color: white; border: none; padding: 4px 8px; border-radius: 12px; font-size: 11px; text-decoration: none; font-weight: bold; width: 100%; margin-top: 4px; }

        /* PAGE 3: ARCADE & GAMES */
        .arcade-container { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 16px; }
        .game-selection-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; }
        .game-card {
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 16px;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            cursor: pointer;
            transition: transform 0.15s, border-color 0.15s;
        }
        .game-card:hover { transform: translateY(-2px); border-color: var(--accent-green); }
        .game-card-icon { font-size: 36px; }
        .game-card-title { font-size: 14px; font-weight: bold; }
        .game-card-desc { font-size: 11px; color: var(--text-secondary); text-align: center; }

        #active-game-frame-container {
            display: none;
            flex: 1;
            flex-direction: column;
            position: relative;
            background: #000;
        }
        #game-frame-header {
            background: var(--header-bg);
            padding: 8px 14px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid var(--border-color);
        }
        #active-game-iframe { width: 100%; flex: 1; border: none; }

        /* PAGE 4: TOOLS & CLIPBOARD */
        .tools-container { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 16px; }
        .tool-card {
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 16px;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .tool-title { font-size: 16px; font-weight: bold; display: flex; align-items: center; gap: 6px; }
        .clipboard-box {
            background: var(--bg-color);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 12px;
            font-family: monospace;
            font-size: 13px;
            min-height: 48px;
            word-break: break-all;
        }

        /* Emergency Distress Banner */
        #sos-banner {
            display: none;
            background: #d32f2f;
            color: white;
            padding: 10px 14px;
            margin: 8px 12px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(211,47,47,0.4);
            animation: pulse 1.2s infinite alternate;
        }
        @keyframes pulse { from { opacity: 0.9; } to { opacity: 1; } }

        /* Drag Overlay */
        #drop-overlay {
            display: none;
            position: fixed;
            top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0, 168, 132, 0.9);
            z-index: 999;
            align-items: center;
            justify-content: center;
            color: white;
            font-size: 20px;
            font-weight: bold;
            flex-direction: column;
        }
    </style>
</head>
<body>
    <div id="drop-overlay">
        <span>📂 Drop files to share instantly over offline mesh</span>
    </div>

    <div id="app-container">
        <!-- Top Persistent Header -->
        <header id="top-header">
            <div class="header-title-box">
                <div class="header-avatar" id="header-avatar-icon">💬</div>
                <div class="header-text">
                    <span class="header-room" id="header-page-title">MeshChat Room</span>
                    <span class="header-status" id="header-mesh-status">🟢 Online (Offline Mesh AP)</span>
                </div>
            </div>
            <div class="header-actions">
                <button class="action-btn" onclick="toggleTheme()" id="theme-btn" title="Toggle Dark/Light">🌓</button>
                <a href="/download" class="action-btn green" download="MeshChat.apk" title="Download Android APK">⬇️ APK</a>
            </div>
        </header>

        <!-- Viewport containing All Pages (SPA - No Page Reloads) -->
        <main id="viewport">
            <!-- PAGE 1: CHAT -->
            <section id="view-chat" class="page-view active">
                <div id="sos-banner">
                    <div style="font-weight:bold; font-size:14px; display:flex; justify-content:space-between;">
                        <span>🚨 EMERGENCY DISTRESS BEACON</span>
                        <span style="font-size:11px; background:rgba(0,0,0,0.3); padding:2px 6px; border-radius:4px;">ACTIVE</span>
                    </div>
                    <div id="sos-sender" style="font-size:13px; margin-top:2px;"></div>
                    <div id="sos-text" style="font-size:12px; margin-top:2px;"></div>
                    <div id="sos-gps" style="font-size:11px; margin-top:4px;"></div>
                </div>

                <div id="messages-scroller">
                    <div class="date-pill">Local Mesh Chat • End-to-End Direct</div>
                </div>

                <div id="voice-record-bar">
                    <span id="voice-record-timer">🔴 Recording Voice Note... 0:00</span>
                    <div style="display:flex; gap:6px;">
                        <button type="button" onclick="cancelVoiceRecording()" style="background:#d32f2f; color:white; border:none; padding:4px 10px; border-radius:12px; font-weight:bold; cursor:pointer;">Cancel</button>
                        <button type="button" onclick="stopAndSendVoiceRecording()" style="background:var(--accent-green); color:white; border:none; padding:4px 12px; border-radius:12px; font-weight:bold; cursor:pointer;">Send</button>
                    </div>
                </div>

                <div id="chat-input-area">
                    <form id="chat-form" class="chat-row" onsubmit="handleSendChat(event)">
                        <button type="button" class="mic-round-btn" onclick="toggleVoiceRecording()" title="Record Voice Note">🎤</button>
                        <input type="text" id="chat-text-input" class="chat-input" placeholder="Message" autocomplete="off" required>
                        <button type="submit" class="send-round-btn">➤</button>
                    </form>
                    <form id="upload-form" class="chat-row" style="margin-top:2px;" onsubmit="handleFileUpload(event)">
                        <input type="file" id="file-selector" style="flex:1; font-size:12px; color:var(--text-secondary);" required>
                        <button type="submit" id="file-upload-btn" class="action-btn" style="background:var(--input-bg);">📎 Share</button>
                    </form>
                </div>
            </section>

            <!-- PAGE 2: MUSIC & PARTY JUKEBOX -->
            <section id="view-music" class="page-view">
                <div class="tools-container">
                    <div class="tool-card" style="text-align:center; align-items:center;">
                        <div style="width:110px; height:110px; border-radius:50%; background:radial-gradient(circle, #333, #111, #000); display:flex; align-items:center; justify-content:center; box-shadow:0 6px 16px rgba(0,0,0,0.5); margin:8px 0;" id="web-vinyl-disc">
                            <div style="width:36px; height:36px; border-radius:50%; background:var(--accent-green); display:flex; align-items:center; justify-content:center; font-size:18px;">🎵</div>
                        </div>
                        <h3 id="web-music-title" style="margin:8px 0 2px;">No Track Playing</h3>
                        <p id="web-music-artist" style="margin:0; font-size:12px; color:var(--text-secondary);">Mesh Party Jukebox</p>

                        <div id="web-music-party-badge" style="background:#005d4b; color:var(--accent-green); padding:4px 12px; border-radius:12px; font-size:11px; font-weight:bold; margin:8px 0;">📻 DJ PARTY STREAM</div>

                        <audio id="mesh-web-audio" controls style="width:100%; margin-top:8px; outline:none; border-radius:8px;"></audio>

                        <div style="display:flex; gap:8px; margin-top:10px; width:100%;">
                            <button class="action-btn green" style="flex:1; justify-content:center;" onclick="syncWithDjStream()">🔄 Sync with DJ</button>
                            <button class="action-btn" style="flex:1; justify-content:center;" onclick="playRadioPreset('https://stream.zeno.fm/f3wvbbqmdg8uv', 'Lo-Fi Chill Beats')">📻 Lo-Fi Stream</button>
                        </div>
                    </div>

                    <div class="tool-card">
                        <div class="tool-title">🌐 Play Custom Audio / Radio Stream</div>
                        <p style="margin:0; font-size:12px; color:var(--text-secondary);">Listen to any direct MP3/AAC audio link or web radio station:</p>
                        <input type="text" id="web-stream-input" placeholder="https://stream.nightride.fm/nightride.m4a" style="width:100%; background:var(--bg-color); color:var(--text-primary); border:1px solid var(--border-color); border-radius:8px; padding:8px; font-size:13px; outline:none;">
                        <button class="action-btn green" onclick="playCustomStream()">▶️ Play Custom Stream</button>
                    </div>
                </div>
            </section>

            <!-- PAGE 3: FILE VAULT -->
            <section id="view-files" class="page-view">
                <div class="vault-container">
                    <div class="vault-upload-card" onclick="document.getElementById('file-selector').click(); navigateTo('chat');">
                        <div style="font-size:32px;">📁</div>
                        <div style="font-weight:bold; font-size:15px; margin-top:4px;">Share New File</div>
                        <div style="font-size:12px; color:var(--text-secondary); margin-top:2px;">Tap to select or drag and drop any file</div>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="font-weight:bold; font-size:15px;">Shared Mesh Files</span>
                        <button class="action-btn" onclick="loadSharedFiles()">🔄 Refresh</button>
                    </div>
                    <div id="files-grid-container" class="files-grid">
                        <div style="grid-column: 1/-1; text-align:center; color:var(--text-secondary); font-size:13px; padding:20px;">Scanning mesh file repository...</div>
                    </div>
                </div>
            </section>

            <!-- PAGE 3: ARCADE & GAMES -->
            <section id="view-arcade" class="page-view">
                <div id="arcade-menu" class="arcade-container">
                    <div>
                        <h3 style="margin:0 0 4px;">🎮 Offline Mesh Arcade</h3>
                        <p style="margin:0; font-size:12px; color:var(--text-secondary);">Zero-internet multiplayer and casual games powered by local Wi-Fi.</p>
                    </div>
                    <div class="game-selection-grid">
                        <div class="game-card" onclick="openEmbeddedGame('/tictactoe', '⭕ Tic-Tac-Toe')">
                            <span class="game-card-icon">⭕</span>
                            <span class="game-card-title">Tic-Tac-Toe</span>
                            <span class="game-card-desc">Quick 3x3 strategy</span>
                        </div>
                        <div class="game-card" onclick="openEmbeddedGame('/chess', '♟️ Chess')">
                            <span class="game-card-icon">♟️</span>
                            <span class="game-card-title">Chess</span>
                            <span class="game-card-desc">Classic 2-player board</span>
                        </div>
                        <div class="game-card" onclick="openEmbeddedGame('/snake', '🐍 Snake')">
                            <span class="game-card-icon">🐍</span>
                            <span class="game-card-title">Retro Snake</span>
                            <span class="game-card-desc">Classic arcade run</span>
                        </div>
                        <div class="game-card" onclick="openEmbeddedGame('/pool', '🎱 8-Ball Pool')">
                            <span class="game-card-icon">🎱</span>
                            <span class="game-card-title">8-Ball Pool</span>
                            <span class="game-card-desc">Billiards simulation</span>
                        </div>
                        <div class="game-card" onclick="openEmbeddedGame('/ide', '💻 Web IDE')">
                            <span class="game-card-icon">💻</span>
                            <span class="game-card-title">Code Scratchpad</span>
                            <span class="game-card-desc">Offline HTML/JS editor</span>
                        </div>
                    </div>
                </div>

                <!-- Embedded Game Frame (No navigation break) -->
                <div id="active-game-frame-container">
                    <div id="game-frame-header">
                        <span id="active-game-title" style="font-weight:bold; font-size:14px;">Game</span>
                        <button onclick="closeEmbeddedGame()" style="background:#ef4444; color:white; border:none; padding:4px 10px; border-radius:6px; font-weight:bold; cursor:pointer;">✕ Close Game</button>
                    </div>
                    <iframe id="active-game-iframe" src="about:blank"></iframe>
                </div>
            </section>

            <!-- PAGE 4: TOOLS & CLIPBOARD -->
            <section id="view-tools" class="page-view">
                <div class="tools-container">
                    <div class="tool-card">
                        <div class="tool-title">📋 Cross-Platform Shared Clipboard</div>
                        <p style="margin:0; font-size:12px; color:var(--text-secondary);">Real-time text copy/paste between Android phones, laptops, and web guests.</p>
                        <div id="tools-clip-text" class="clipboard-box">(Clipboard empty)</div>
                        <div style="display:flex; justify-content:space-between; align-items:center;">
                            <span id="tools-clip-meta" style="font-size:11px; color:var(--text-secondary);"></span>
                            <button class="action-btn green" onclick="copyClipboardToDevice()">Copy to Device</button>
                        </div>
                        <hr style="border:0.5px solid var(--border-color); margin:6px 0;">
                        <textarea id="tools-clip-input" placeholder="Paste or type text to sync across all mesh devices..." style="width:100%; height:60px; background:var(--bg-color); color:var(--text-primary); border:1px solid var(--border-color); border-radius:8px; padding:8px; resize:none; font-size:13px; outline:none;"></textarea>
                        <div style="display:flex; justify-content:flex-end;">
                            <button class="action-btn green" onclick="pushClipboardFromTools()">Push to Mesh</button>
                        </div>
                    </div>

                    <div class="tool-card">
                        <div class="tool-title">🌐 Mesh Diagnostics & Node Stats</div>
                        <div style="display:flex; flex-direction:column; gap:6px; font-size:13px;">
                            <div>• <strong>My Client ID:</strong> <span id="diag-my-name">Detecting...</span></div>
                            <div>• <strong>Connection Mode:</strong> <span style="color:var(--accent-green);">Wi-Fi Hotspot SoftAP (Offline LAN)</span></div>
                            <div>• <strong>WebSocket Link:</strong> <span id="diag-ws-status" style="color:var(--accent-green);">Active</span></div>
                            <div>• <strong>Local Hotspot Gateway:</strong> <span id="diag-host-ip">192.168.49.1:8080</span></div>
                        </div>
                    </div>
                </div>
            </section>
        </main>

        <!-- Bottom Persistent Tab Bar -->
        <nav id="bottom-nav">
            <button class="nav-item active" onclick="navigateTo('chat')">
                <span class="icon">💬</span>
                <span>Chat</span>
            </button>
            <button class="nav-item" onclick="navigateTo('music')">
                <span class="icon">🎵</span>
                <span>Music</span>
            </button>
            <button class="nav-item" onclick="navigateTo('files')">
                <span class="icon">📁</span>
                <span>Vault</span>
            </button>
            <button class="nav-item" onclick="navigateTo('arcade')">
                <span class="icon">🎮</span>
                <span>Arcade</span>
            </button>
            <button class="nav-item" onclick="navigateTo('tools')">
                <span class="icon">📋</span>
                <span>Tools</span>
            </button>
        </nav>
    </div>

    <!-- Client Script (Persistent WebSocket + Navigation Engine) -->
    <script>
        let myName = localStorage.getItem('mesh_name');
        if (!myName) {
            myName = 'Web-' + Math.floor(100 + Math.random() * 900);
            localStorage.setItem('mesh_name', myName);
        }
        document.getElementById('diag-my-name').innerText = myName;

        // Navigation Engine (Seamless view switching - ZERO CONNECTION BREAK)
        function navigateTo(pageId) {
            document.querySelectorAll('.page-view').forEach(p => p.classList.remove('active'));
            document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));

            const targetView = document.getElementById('view-' + pageId);
            if (targetView) targetView.classList.add('active');

            const navIndex = { 'chat': 0, 'music': 1, 'files': 2, 'arcade': 3, 'tools': 4 }[pageId] || 0;
            const navBtn = document.querySelectorAll('.nav-item')[navIndex];
            if (navBtn) navBtn.classList.add('active');

            const titles = { 'chat': 'MeshChat Room', 'music': 'Mesh Jukebox', 'files': 'Shared File Vault', 'arcade': 'Mesh Arcade', 'tools': 'Mesh Tools' };
            document.getElementById('header-page-title').innerText = titles[pageId] || 'MeshChat';

            if (pageId === 'music') fetchMusicStatus();
            if (pageId === 'files') loadSharedFiles();
            if (pageId === 'tools') fetchClipboard();
            if (pageId === 'chat') {
                const scroller = document.getElementById('messages-scroller');
                scroller.scrollTop = scroller.scrollHeight;
            }
        }

        async function fetchMusicStatus() {
            try {
                const res = await fetch('/api/music/status');
                if (res.ok) {
                    const data = await res.json();
                    document.getElementById('web-music-title').innerText = data.trackTitle || 'No Track Playing';
                    document.getElementById('web-music-artist').innerText = data.artist || 'Mesh Party Jukebox';
                    if (data.isPlaying) {
                        document.getElementById('web-music-party-badge').innerText = '🟢 LIVE DJ BROADCAST ACTIVE';
                    }
                }
            } catch(e) {}
        }

        function syncWithDjStream() {
            const audio = document.getElementById('mesh-web-audio');
            audio.src = '/audio/current?t=' + Date.now();
            audio.play().catch(e => console.log('Autoplay policy prevented playback', e));
            fetchMusicStatus();
        }

        function playRadioPreset(url, name) {
            const audio = document.getElementById('mesh-web-audio');
            audio.src = url;
            document.getElementById('web-music-title').innerText = name;
            document.getElementById('web-music-artist').innerText = 'Online Web Radio';
            audio.play().catch(e => console.log(e));
        }

        function playCustomStream() {
            const input = document.getElementById('web-stream-input');
            const url = input.value.trim();
            if (!url) return;
            playRadioPreset(url, 'Custom Audio Stream');
        }

        // Embedded Game Engine (Keeps WebSocket alive inside iframe)
        function openEmbeddedGame(url, title) {
            document.getElementById('arcade-menu').style.display = 'none';
            const frameContainer = document.getElementById('active-game-frame-container');
            const iframe = document.getElementById('active-game-iframe');
            document.getElementById('active-game-title').innerText = title;
            iframe.src = url;
            frameContainer.style.display = 'flex';
        }

        function closeEmbeddedGame() {
            const frameContainer = document.getElementById('active-game-frame-container');
            const iframe = document.getElementById('active-game-iframe');
            iframe.src = 'about:blank';
            frameContainer.style.display = 'none';
            document.getElementById('arcade-menu').style.display = 'flex';
        }

        // Shared File Repository Loader
        async function loadSharedFiles() {
            const container = document.getElementById('files-grid-container');
            try {
                const res = await fetch('/api/files');
                if (!res.ok) return;
                const files = await res.json();
                if (files.length === 0) {
                    container.innerHTML = '<div style="grid-column: 1/-1; text-align:center; color:var(--text-secondary); font-size:13px; padding:20px;">No files shared in this session yet.</div>';
                    return;
                }
                container.innerHTML = '';
                for (let f of files) {
                    const card = document.createElement('div');
                    card.className = 'file-item-card';
                    let icon = '📄';
                    if (f.isImage) icon = '🖼️';
                    else if (f.isAudio) icon = '🎵';
                    else if (f.isVideo) icon = '🎬';
                    else if (f.name.endsWith('.apk')) icon = '📦';
                    else if (f.name.endsWith('.pdf')) icon = '📕';
                    else if (f.name.endsWith('.zip')) icon = '🗜️';

                    card.innerHTML = `
                        <div class="file-icon">${'$'}{icon}</div>
                        <div class="file-name" title="${'$'}{f.name}">${'$'}{f.name}</div>
                        <div class="file-size">${'$'}{f.formattedSize}</div>
                        <a href="${'$'}{f.downloadUrl}" class="file-download-btn" download="${'$'}{f.name}">Download</a>
                    `;
                    container.appendChild(card);
                }
            } catch(e) {
                container.innerHTML = '<div style="grid-column: 1/-1; text-align:center; color:#ef5350; font-size:13px; padding:20px;">Error loading file repository.</div>';
            }
        }

        // Clipboard Sync Engine
        async function fetchClipboard() {
            try {
                const res = await fetch('/api/clipboard');
                if (res.ok) {
                    const data = await res.json();
                    document.getElementById('tools-clip-text').innerText = data.text || '(Clipboard empty)';
                    if (data.sender) {
                        const dt = new Date(data.timestamp);
                        document.getElementById('tools-clip-meta').innerText = 'Synced by ' + data.sender + ' • ' + dt.toLocaleTimeString();
                    } else {
                        document.getElementById('tools-clip-meta').innerText = '';
                    }
                }
            } catch(e) {}
        }

        async function copyClipboardToDevice() {
            const txt = document.getElementById('tools-clip-text').innerText;
            if (txt && txt !== '(Clipboard empty)') {
                try {
                    await navigator.clipboard.writeText(txt);
                    alert('Copied to device clipboard!');
                } catch(e) {
                    alert('Could not copy automatically: ' + txt);
                }
            }
        }

        async function pushClipboardFromTools() {
            const input = document.getElementById('tools-clip-input');
            const txt = input.value.trim();
            if (!txt) return;
            try {
                await fetch('/api/clipboard', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ text: txt, sender: myName })
                });
                input.value = '';
                fetchClipboard();
            } catch(e) {
                alert('Failed to push clipboard: ' + e.message);
            }
        }

        // Messaging Engine & History
        const messagesScroller = document.getElementById('messages-scroller');
        let lastHistoryHash = "";

        function appendMessageElement(sender, text, isMe, scroll = true) {
            const div = document.createElement('div');
            div.className = 'msg ' + (isMe ? 'msg-me' : 'msg-other');

            if (!isMe && sender) {
                const s = document.createElement('div');
                s.className = 'msg-sender';
                s.innerText = sender;
                div.appendChild(s);
            }

            const words = text.split(' ');
            for (let w of words) {
                if (w.startsWith('http://') || w.startsWith('https://')) {
                    const isImg = w.toLowerCase().match(/\.(jpg|jpeg|png|gif|webp)$/i) || w.includes("/files/web_shared_") || w.includes("mesh_cache");
                    const isAudio = w.match(/\.(m4a|mp3|wav|ogg|opus|aac|webm)$/i) || w.includes("web_audio_") || w.includes("/audio_");
                    if (isImg) {
                        const img = document.createElement('img');
                        img.src = w;
                        img.style.maxWidth = '100%';
                        img.style.borderRadius = '6px';
                        img.style.marginTop = '4px';
                        img.style.display = 'block';
                        div.appendChild(img);
                    } else if (isAudio) {
                        const audio = document.createElement('audio');
                        audio.controls = true;
                        audio.src = w;
                        audio.style.maxWidth = '250px';
                        audio.style.height = '36px';
                        audio.style.marginTop = '4px';
                        audio.style.display = 'block';
                        div.appendChild(audio);
                    } else {
                        const a = document.createElement('a');
                        a.href = w;
                        a.target = '_blank';
                        a.innerText = '📎 ' + (w.substring(w.lastIndexOf('/') + 1) || 'File');
                        a.style.color = isMe ? '#e9edef' : '#25d366';
                        a.style.textDecoration = 'underline';
                        a.style.display = 'inline-block';
                        a.style.marginTop = '4px';
                        div.appendChild(a);
                    }
                    div.appendChild(document.createTextNode(' '));
                } else {
                    div.appendChild(document.createTextNode(w + ' '));
                }
            }

            const timeSpan = document.createElement('span');
            timeSpan.className = 'msg-time';
            const now = new Date();
            timeSpan.innerText = String(now.getHours()).padStart(2,'0') + ':' + String(now.getMinutes()).padStart(2,'0') + (isMe ? ' ✓✓' : '');
            div.appendChild(timeSpan);

            messagesScroller.appendChild(div);
            if (scroll) messagesScroller.scrollTop = messagesScroller.scrollHeight;
        }

        async function fetchHistory() {
            try {
                const res = await fetch('/api/history');
                if (!res.ok) return;
                const data = await res.json();
                const hash = JSON.stringify(data);
                if (hash !== lastHistoryHash) {
                    lastHistoryHash = hash;
                    messagesScroller.innerHTML = '<div class="date-pill">Local Mesh Chat • End-to-End Direct</div>';
                    for (let m of data) {
                        try {
                            const p = JSON.parse(m.message);
                            if (p.type === 'sos_beacon') {
                                showSosBeacon(p);
                                continue;
                            } else if (p.type === 'sos_cancel') {
                                hideSosBeacon();
                                continue;
                            }
                        } catch(e) {}
                        appendMessageElement(m.sender, m.message, m.sender === myName, false);
                    }
                    messagesScroller.scrollTop = messagesScroller.scrollHeight;
                }
            } catch(e) {}
        }
        fetchHistory();

        function showSosBeacon(p) {
            const b = document.getElementById('sos-banner');
            b.style.display = 'block';
            document.getElementById('sos-sender').innerText = 'From: ' + (p.senderName || 'Nearby Peer');
            document.getElementById('sos-text').innerText = p.message || 'Distress signal received!';
            const gps = document.getElementById('sos-gps');
            if (p.lat && p.lng) {
                gps.innerHTML = '📍 Location: <a href="https://maps.google.com/?q=' + p.lat + ',' + p.lng + '" target="_blank" style="color:white; text-decoration:underline;">' + p.lat.toFixed(4) + ', ' + p.lng.toFixed(4) + '</a>';
            } else {
                gps.innerText = '📍 Location: Searching GPS...';
            }
        }

        function hideSosBeacon() {
            document.getElementById('sos-banner').style.display = 'none';
        }

        // Live WebSocket Link
        let ws = null;
        function connectWebSocket() {
            if (!window.WebSocket) return;
            const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const url = proto + '//' + window.location.host + '/ws?sender=' + encodeURIComponent(myName);
            ws = new WebSocket(url);
            ws.onopen = () => {
                document.getElementById('diag-ws-status').innerText = 'Active (Connected)';
            };
            ws.onmessage = (e) => {
                try {
                    const data = JSON.parse(e.data);
                    const msg = JSON.parse(data.message || e.data);
                    if (msg.type === 'mesh_music_sync') {
                        document.getElementById('web-music-title').innerText = msg.trackTitle || 'Playing Track';
                        document.getElementById('web-music-artist').innerText = (msg.artist || '') + ' (DJ ' + (msg.hostName || '') + ')';
                        document.getElementById('web-music-party-badge').innerText = '🟢 LIVE DJ BROADCAST ACTIVE';
                        const audio = document.getElementById('mesh-web-audio');
                        if (audio.src && !audio.paused && msg.positionMs) {
                            const curSec = audio.currentTime;
                            const targetSec = msg.positionMs / 1000;
                            if (Math.abs(curSec - targetSec) > 1.5) {
                                audio.currentTime = targetSec;
                            }
                        }
                        return;
                    }
                } catch(err) {}
                fetchHistory();
                playAudioChime();
            };
            ws.onclose = () => {
                document.getElementById('diag-ws-status').innerText = 'Reconnecting in 2s...';
                setTimeout(connectWebSocket, 2000);
            };
        }
        connectWebSocket();

        async function handleSendChat(e) {
            e.preventDefault();
            const input = document.getElementById('chat-text-input');
            const txt = input.value.trim();
            if (!txt) return;
            input.value = '';

            const payload = JSON.stringify({ sender: myName, message: txt });
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(payload);
            } else {
                await fetch('/api/send', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: payload
                });
            }
            fetchHistory();
        }

        async function handleFileUpload(e) {
            e.preventDefault();
            const selector = document.getElementById('file-selector');
            if (!selector.files || selector.files.length === 0) return;
            const btn = document.getElementById('file-upload-btn');
            btn.innerText = '⏳ Uploading...';

            const formData = new FormData();
            formData.append('sender', myName);
            formData.append('file', selector.files[0]);

            try {
                const res = await fetch('/upload', { method: 'POST', body: formData });
                if (res.ok) {
                    selector.value = '';
                    btn.innerText = '📎 Share';
                    fetchHistory();
                } else {
                    btn.innerText = '⚠️ Failed';
                }
            } catch(err) {
                btn.innerText = '⚠️ Error';
            }
        }

        // Voice Note Recording Engine
        let mediaRecorder = null;
        let audioChunks = [];
        let recordInterval = null;
        let recordSeconds = 0;

        async function toggleVoiceRecording() {
            if (mediaRecorder && mediaRecorder.state === 'recording') {
                stopAndSendVoiceRecording();
                return;
            }
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                mediaRecorder = new MediaRecorder(stream);
                audioChunks = [];
                mediaRecorder.ondataavailable = e => { if (e.data.size > 0) audioChunks.push(e.data); };
                mediaRecorder.start();
                recordSeconds = 0;
                document.getElementById('voice-record-bar').style.display = 'flex';
                document.getElementById('voice-record-timer').innerText = '🔴 Recording Voice Note... 0:00';
                recordInterval = setInterval(() => {
                    recordSeconds++;
                    const mins = Math.floor(recordSeconds / 60);
                    const secs = String(recordSeconds % 60).padStart(2, '0');
                    document.getElementById('voice-record-timer').innerText = '🔴 Recording Voice Note... ' + mins + ':' + secs;
                }, 1000);
            } catch(err) {
                alert('Microphone permission required for voice notes: ' + err.message);
            }
        }

        function cancelVoiceRecording() {
            if (mediaRecorder) {
                mediaRecorder.onstop = null;
                mediaRecorder.stop();
                mediaRecorder.stream.getTracks().forEach(t => t.stop());
            }
            clearInterval(recordInterval);
            document.getElementById('voice-record-bar').style.display = 'none';
        }

        function stopAndSendVoiceRecording() {
            if (!mediaRecorder) return;
            clearInterval(recordInterval);
            document.getElementById('voice-record-bar').style.display = 'none';
            mediaRecorder.onstop = async () => {
                const blob = new Blob(audioChunks, { type: 'audio/webm' });
                const formData = new FormData();
                formData.append('sender', myName);
                formData.append('file', blob, 'web_audio_' + Date.now() + '.webm');
                try {
                    await fetch('/upload', { method: 'POST', body: formData });
                    fetchHistory();
                } catch(err) {}
                mediaRecorder.stream.getTracks().forEach(t => t.stop());
            };
            mediaRecorder.stop();
        }

        function playAudioChime() {
            try {
                const AudioCtx = window.AudioContext || window.webkitAudioContext;
                if (!AudioCtx) return;
                const ctx = new AudioCtx();
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(659.25, ctx.currentTime);
                osc.frequency.setValueAtTime(880, ctx.currentTime + 0.08);
                gain.gain.setValueAtTime(0.12, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.28);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start();
                osc.stop(ctx.currentTime + 0.28);
            } catch(e) {}
        }

        function toggleTheme() {
            const isLight = document.body.classList.toggle('light-theme');
            localStorage.setItem('mesh_theme', isLight ? 'light' : 'dark');
        }
        if (localStorage.getItem('mesh_theme') === 'light') {
            document.body.classList.add('light-theme');
        }

        // Drag and Drop Upload Support
        window.addEventListener('dragover', (e) => {
            e.preventDefault();
            document.getElementById('drop-overlay').style.display = 'flex';
        });
        window.addEventListener('dragleave', (e) => {
            if (e.clientX <= 0 || e.clientY <= 0) {
                document.getElementById('drop-overlay').style.display = 'none';
            }
        });
        window.addEventListener('drop', async (e) => {
            e.preventDefault();
            document.getElementById('drop-overlay').style.display = 'none';
            if (e.dataTransfer && e.dataTransfer.files.length > 0) {
                const file = e.dataTransfer.files[0];
                const formData = new FormData();
                formData.append('sender', myName);
                formData.append('file', file);
                try {
                    await fetch('/upload', { method: 'POST', body: formData });
                    fetchHistory();
                    navigateTo('files');
                } catch(err) {
                    alert('Upload failed: ' + err.message);
                }
            }
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
}
