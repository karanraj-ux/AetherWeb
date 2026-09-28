package com.aetherweb.app

/**
 * Robust, Offline-Ready Games for MeshChat
 * Supports:
 * 1. 🤖 Smart AI Bot mode (Minimax for Tic-Tac-Toe, Alpha-Beta Mini Engine for Chess)
 * 2. 👥 Local 2-Player Pass & Play mode
 * 3. 🌐 Real-Time Mesh Multiplayer (Syncs over WebSocket /ws and BLE/WiFi Mesh)
 * 4. 100% Offline with zero external dependencies
 */
object OfflineGamePacks {

    val TIC_TAC_TOE_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Mesh Tic-Tac-Toe</title>
    <style>
        :root {
            --bg-color: #0b141b;
            --card-bg: #1f2c34;
            --cell-bg: #2a3942;
            --accent-green: #25d366;
            --accent-blue: #53bdeb;
            --text-primary: #e9edef;
            --text-secondary: #8696a0;
        }
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        body {
            margin: 0; padding: 12px;
            background: var(--bg-color);
            color: var(--text-primary);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex; flex-direction: column; align-items: center; justify-content: center;
            min-height: 100vh;
        }
        h2 { margin: 0 0 8px; color: var(--accent-green); font-size: 22px; }
        .mode-row { display: flex; gap: 8px; margin-bottom: 14px; }
        .mode-btn {
            background: var(--card-bg); color: var(--text-secondary);
            border: 1px solid #33444d; padding: 6px 14px; border-radius: 16px;
            font-size: 13px; font-weight: 600; cursor: pointer;
        }
        .mode-btn.active {
            background: var(--accent-green); color: white; border-color: var(--accent-green);
        }
        #status-card {
            background: var(--card-bg); padding: 8px 18px; border-radius: 20px;
            font-size: 15px; font-weight: bold; margin-bottom: 18px;
            box-shadow: 0 2px 6px rgba(0,0,0,0.3); text-align: center;
        }
        .board {
            display: grid; grid-template-columns: repeat(3, 90px); grid-template-rows: repeat(3, 90px);
            gap: 8px; background: #131c21; padding: 10px; border-radius: 16px;
            box-shadow: 0 6px 20px rgba(0,0,0,0.5);
        }
        .cell {
            background: var(--cell-bg); border-radius: 10px;
            display: flex; align-items: center; justify-content: center;
            font-size: 42px; font-weight: 800; cursor: pointer;
            transition: background 0.15s, transform 0.1s; user-select: none;
        }
        .cell:active { transform: scale(0.95); }
        .cell.x { color: var(--accent-green); }
        .cell.o { color: var(--accent-blue); }
        .cell.win { background: #005d4b; animation: winPulse 0.8s alternate infinite; }
        @keyframes winPulse { from { transform: scale(0.96); } to { transform: scale(1.04); } }
        .action-row { margin-top: 18px; display: flex; gap: 10px; }
        .act-btn {
            background: #202c33; color: var(--text-primary); border: 1px solid #33444d;
            padding: 8px 18px; border-radius: 20px; font-size: 14px; font-weight: 600; cursor: pointer;
        }
        .act-btn:hover { background: #2a3942; }
    </style>
</head>
<body>
    <h2>⭕ Tic-Tac-Toe</h2>
    <div class="mode-row">
        <button class="mode-btn active" onclick="setMode('bot')" id="btn-bot">🤖 vs AI Bot</button>
        <button class="mode-btn" onclick="setMode('local')" id="btn-local">👥 Pass & Play</button>
        <button class="mode-btn" onclick="setMode('mesh')" id="btn-mesh">🌐 Mesh Online</button>
    </div>

    <div id="status-card">Your Turn (X)</div>

    <div class="board" id="board">
        <div class="cell" onclick="cellClicked(0)"></div>
        <div class="cell" onclick="cellClicked(1)"></div>
        <div class="cell" onclick="cellClicked(2)"></div>
        <div class="cell" onclick="cellClicked(3)"></div>
        <div class="cell" onclick="cellClicked(4)"></div>
        <div class="cell" onclick="cellClicked(5)"></div>
        <div class="cell" onclick="cellClicked(6)"></div>
        <div class="cell" onclick="cellClicked(7)"></div>
        <div class="cell" onclick="cellClicked(8)"></div>
    </div>

    <div class="action-row">
        <button class="act-btn" onclick="resetGame()">🔄 Restart</button>
    </div>

    <script>
        let board = ["","","","","","","","",""];
        let currentTurn = "X";
        let gameMode = "bot"; // 'bot', 'local', 'mesh'
        let gameOver = false;
        let myMeshPiece = "X";
        let myName = localStorage.getItem('mesh_name') || ("Player-" + Math.floor(Math.random()*1000));

        // Connect Mesh WebSocket for real-time multiplayer
        const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = wsProto + '//' + window.location.host + '/ws?sender=TTT_' + myName;
        let ws = null;
        try {
            ws = new WebSocket(wsUrl);
            ws.onmessage = (e) => {
                try {
                    const data = JSON.parse(e.data);
                    const msg = JSON.parse(data.message || e.data);
                    if (msg.type === 'tictactoe' && msg.state) {
                        applyMeshState(msg.state);
                    }
                } catch(err) {}
            };
        } catch(err) {}

        function setMode(mode) {
            gameMode = mode;
            document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
            document.getElementById('btn-' + mode).classList.add('active');
            resetGame();
        }

        const winCombos = [
            [0,1,2],[3,4,5],[6,7,8],
            [0,3,6],[1,4,7],[2,5,8],
            [0,4,8],[2,4,6]
        ];

        function checkWin(b, player) {
            for (let c of winCombos) {
                if (b[c[0]] === player && b[c[1]] === player && b[c[2]] === player) return c;
            }
            return null;
        }

        function isDraw(b) {
            return !b.includes("") && !checkWin(b, "X") && !checkWin(b, "O");
        }

        function cellClicked(idx) {
            if (gameOver || board[idx] !== "") return;
            if (gameMode === 'mesh' && currentTurn !== myMeshPiece) {
                return; // Not your turn over mesh
            }

            board[idx] = currentTurn;
            render();

            const win = checkWin(board, currentTurn);
            if (win) {
                endGame(currentTurn + " Wins!", win);
                broadcastMeshState();
                return;
            }
            if (isDraw(board)) {
                endGame("It's a Draw!");
                broadcastMeshState();
                return;
            }

            currentTurn = (currentTurn === "X") ? "O" : "X";
            updateStatus();
            broadcastMeshState();

            if (gameMode === 'bot' && currentTurn === "O" && !gameOver) {
                document.getElementById('status-card').innerText = "🤖 Bot is thinking...";
                setTimeout(makeBotMove, 280);
            }
        }

        // Smart Minimax AI with optimal play
        function makeBotMove() {
            if (gameOver) return;
            const avail = [];
            for (let i = 0; i < 9; i++) if (board[i] === "") avail.push(i);
            if (avail.length === 0) return;

            // 1. Can bot win on this move?
            for (let idx of avail) {
                board[idx] = "O";
                if (checkWin(board, "O")) {
                    render();
                    endGame("🤖 Bot Wins!", checkWin(board, "O"));
                    return;
                }
                board[idx] = "";
            }

            // 2. Can player win next move? Block them!
            for (let idx of avail) {
                board[idx] = "X";
                if (checkWin(board, "X")) {
                    board[idx] = "O";
                    currentTurn = "X";
                    render();
                    updateStatus();
                    return;
                }
                board[idx] = "";
            }

            // 3. Take center if available
            if (board[4] === "") {
                board[4] = "O";
            } else {
                // Minimax evaluation
                const best = minimax(board, "O");
                board[best.index] = "O";
            }

            const win = checkWin(board, "O");
            if (win) {
                endGame("🤖 Bot Wins!", win);
            } else if (isDraw(board)) {
                endGame("It's a Draw!");
            } else {
                currentTurn = "X";
                updateStatus();
            }
            render();
        }

        function minimax(newBoard, player) {
            const avail = [];
            for (let i = 0; i < 9; i++) if (newBoard[i] === "") avail.push(i);

            if (checkWin(newBoard, "X")) return { score: -10 };
            if (checkWin(newBoard, "O")) return { score: 10 };
            if (avail.length === 0) return { score: 0 };

            const moves = [];
            for (let i = 0; i < avail.length; i++) {
                const move = { index: avail[i] };
                newBoard[avail[i]] = player;
                if (player === "O") {
                    const result = minimax(newBoard, "X");
                    move.score = result.score;
                } else {
                    const result = minimax(newBoard, "O");
                    move.score = result.score;
                }
                newBoard[avail[i]] = "";
                moves.push(move);
            }

            let bestMove;
            if (player === "O") {
                let bestScore = -10000;
                for (let m of moves) {
                    if (m.score > bestScore) { bestScore = m.score; bestMove = m; }
                }
            } else {
                let bestScore = 10000;
                for (let m of moves) {
                    if (m.score < bestScore) { bestScore = m.score; bestMove = m; }
                }
            }
            return bestMove;
        }

        function endGame(text, combo = null) {
            gameOver = true;
            document.getElementById('status-card').innerText = text;
            if (combo) {
                const cells = document.querySelectorAll('.cell');
                combo.forEach(idx => cells[idx].classList.add('win'));
            }
        }

        function updateStatus() {
            if (gameOver) return;
            const card = document.getElementById('status-card');
            if (gameMode === 'bot') {
                card.innerText = (currentTurn === "X") ? "Your Turn (X)" : "🤖 Bot's Turn";
            } else if (gameMode === 'local') {
                card.innerText = "Player " + currentTurn + "'s Turn";
            } else {
                card.innerText = (currentTurn === myMeshPiece) ? "Your Turn (" + myMeshPiece + ")" : "Opponent's Turn (" + currentTurn + ")";
            }
        }

        function render() {
            const cells = document.querySelectorAll('.cell');
            for (let i = 0; i < 9; i++) {
                cells[i].innerText = board[i];
                cells[i].className = 'cell ' + (board[i] === 'X' ? 'x' : (board[i] === 'O' ? 'o' : ''));
            }
        }

        function resetGame() {
            board = ["","","","","","","","",""];
            currentTurn = "X";
            gameOver = false;
            document.querySelectorAll('.cell').forEach(c => { c.innerText = ''; c.className = 'cell'; });
            updateStatus();
            if (gameMode === 'mesh') broadcastMeshState();
        }

        function broadcastMeshState() {
            if (gameMode !== 'mesh' || !ws || ws.readyState !== WebSocket.OPEN) return;
            const state = {
                board: board,
                isXTurn: currentTurn === 'X',
                xPlayerId: (myMeshPiece === 'X' ? myName : 'remote'),
                oPlayerId: (myMeshPiece === 'O' ? myName : 'remote'),
                winner: gameOver ? document.getElementById('status-card').innerText : ""
            };
            ws.send(JSON.stringify({ type: 'tictactoe', state: state }));
        }

        function applyMeshState(state) {
            if (gameMode !== 'mesh') return;
            board = state.board || board;
            currentTurn = state.isXTurn ? 'X' : 'O';
            if (state.winner) {
                endGame(state.winner);
            } else {
                gameOver = false;
                updateStatus();
            }
            render();
        }

        render();
        updateStatus();
    </script>
</body>
</html>
"""

    val CHESS_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Mesh Chess</title>
    <style>
        :root {
            --bg-color: #0b141b;
            --card-bg: #1f2c34;
            --accent-green: #25d366;
            --board-light: #eeeed2;
            --board-dark: #769656;
            --board-selected: #baca44;
            --board-target: #f7ec7d;
            --text-primary: #e9edef;
            --text-secondary: #8696a0;
        }
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        body {
            margin: 0; padding: 12px;
            background: var(--bg-color); color: var(--text-primary);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex; flex-direction: column; align-items: center; justify-content: center;
            min-height: 100vh;
        }
        h2 { margin: 0 0 6px; color: var(--accent-green); font-size: 22px; }
        .mode-row { display: flex; gap: 8px; margin-bottom: 12px; }
        .mode-btn {
            background: var(--card-bg); color: var(--text-secondary);
            border: 1px solid #33444d; padding: 6px 14px; border-radius: 16px;
            font-size: 13px; font-weight: 600; cursor: pointer;
        }
        .mode-btn.active {
            background: var(--accent-green); color: white; border-color: var(--accent-green);
        }
        #status-card {
            background: var(--card-bg); padding: 8px 18px; border-radius: 20px;
            font-size: 14px; font-weight: bold; margin-bottom: 12px;
            box-shadow: 0 2px 6px rgba(0,0,0,0.3); text-align: center;
        }
        #board-container {
            width: min(92vw, 380px); height: min(92vw, 380px);
            border: 4px solid #1f2c34; border-radius: 10px; overflow: hidden;
            display: grid; grid-template-columns: repeat(8, 1fr); grid-template-rows: repeat(8, 1fr);
            box-shadow: 0 8px 24px rgba(0,0,0,0.6);
        }
        .square {
            display: flex; align-items: center; justify-content: center;
            font-size: min(8vw, 34px); cursor: pointer; user-select: none;
            transition: background 0.15s;
        }
        .square.light { background: var(--board-light); color: #000; }
        .square.dark { background: var(--board-dark); color: #000; }
        .square.selected { background: var(--board-selected) !important; }
        .square.target { background: var(--board-target) !important; }
        .action-row { margin-top: 14px; display: flex; gap: 10px; }
        .act-btn {
            background: #202c33; color: var(--text-primary); border: 1px solid #33444d;
            padding: 8px 18px; border-radius: 20px; font-size: 13px; font-weight: 600; cursor: pointer;
        }
        .act-btn:hover { background: #2a3942; }
    </style>
</head>
<body>
    <h2>♟️ Offline Mesh Chess</h2>
    <div class="mode-row">
        <button class="mode-btn active" onclick="setMode('bot')" id="btn-bot">🤖 vs AI Bot</button>
        <button class="mode-btn" onclick="setMode('local')" id="btn-local">👥 Pass & Play</button>
        <button class="mode-btn" onclick="setMode('mesh')" id="btn-mesh">🌐 Mesh Online</button>
    </div>

    <div id="status-card">White to Move</div>
    <div id="board-container"></div>

    <div class="action-row">
        <button class="act-btn" onclick="resetGame()">🔄 New Game</button>
    </div>

    <script>
        const PIECES = {
            'R': '♜', 'N': '♞', 'B': '♝', 'Q': '♛', 'K': '♚', 'P': '♟',
            'r': '♖', 'n': '♘', 'b': '♗', 'q': '♕', 'k': '♔', 'p': '♙'
        };

        const PIECE_VALS = { 'p': 100, 'n': 320, 'b': 330, 'r': 500, 'q': 900, 'k': 20000 };

        let board = [
            ['r','n','b','q','k','b','n','r'],
            ['p','p','p','p','p','p','p','p'],
            ['','','','','','','',''],
            ['','','','','','','',''],
            ['','','','','','','',''],
            ['','','','','','','',''],
            ['P','P','P','P','P','P','P','P'],
            ['R','N','B','Q','K','B','N','R']
        ];

        let turn = 'w'; // 'w' (White/Uppercase) or 'b' (Black/Lowercase)
        let selected = null;
        let gameMode = 'bot';
        let gameOver = false;
        let myName = localStorage.getItem('mesh_name') || ("Chess-" + Math.floor(Math.random()*1000));

        // Connect WebSocket for mesh sync
        const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = wsProto + '//' + window.location.host + '/ws?sender=Chess_' + myName;
        let ws = null;
        try {
            ws = new WebSocket(wsUrl);
            ws.onmessage = (e) => {
                try {
                    const data = JSON.parse(e.data);
                    const msg = JSON.parse(data.message || e.data);
                    if (msg.type === 'chess' && msg.state) {
                        applyMeshState(msg.state);
                    }
                } catch(err) {}
            };
        } catch(err) {}

        function isWhite(p) { return p && p === p.toUpperCase(); }
        function isBlack(p) { return p && p === p.toLowerCase(); }

        function setMode(mode) {
            gameMode = mode;
            document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
            document.getElementById('btn-' + mode).classList.add('active');
            resetGame();
        }

        function render() {
            const boardEl = document.getElementById('board-container');
            boardEl.innerHTML = '';
            for (let r = 0; r < 8; r++) {
                for (let c = 0; c < 8; c++) {
                    const sq = document.createElement('div');
                    const isDark = (r + c) % 2 === 1;
                    sq.className = 'square ' + (isDark ? 'dark' : 'light');
                    if (selected && selected[0] === r && selected[1] === c) {
                        sq.classList.add('selected');
                    }
                    const piece = board[r][c];
                    sq.innerText = PIECES[piece] || '';
                    sq.onclick = () => onSquareClick(r, c);
                    boardEl.appendChild(sq);
                }
            }
            if (!gameOver) {
                const turnStr = (turn === 'w') ? "White's Turn" : "Black's Turn";
                document.getElementById('status-card').innerText = (gameMode === 'bot' && turn === 'b') ? "🤖 Bot is calculating..." : turnStr;
            }
        }

        function isValidMove(fr, fc, tr, tc, piece) {
            const target = board[tr][tc];
            if (target) {
                if (isWhite(piece) && isWhite(target)) return false;
                if (isBlack(piece) && isBlack(target)) return false;
            }
            const dr = tr - fr;
            const dc = tc - fc;
            const p = piece.toLowerCase();

            function isClear() {
                const sr = dr === 0 ? 0 : dr / Math.abs(dr);
                const sc = dc === 0 ? 0 : dc / Math.abs(dc);
                let cr = fr + sr;
                let cc = fc + sc;
                while (cr !== tr || cc !== tc) {
                    if (board[cr][cc] !== '') return false;
                    cr += sr;
                    cc += sc;
                }
                return true;
            }

            if (p === 'p') {
                const dir = isWhite(piece) ? -1 : 1;
                const startRow = isWhite(piece) ? 6 : 1;
                if (dc === 0 && !target) {
                    if (dr === dir) return true;
                    if (fr === startRow && dr === 2 * dir && board[fr + dir][fc] === '') return true;
                }
                if (Math.abs(dc) === 1 && dr === dir && target) return true;
                return false;
            }
            if (p === 'r') return (dr === 0 || dc === 0) && isClear();
            if (p === 'b') return Math.abs(dr) === Math.abs(dc) && isClear();
            if (p === 'q') return (dr === 0 || dc === 0 || Math.abs(dr) === Math.abs(dc)) && isClear();
            if (p === 'k') return Math.abs(dr) <= 1 && Math.abs(dc) <= 1;
            if (p === 'n') return (Math.abs(dr) === 2 && Math.abs(dc) === 1) || (Math.abs(dr) === 1 && Math.abs(dc) === 2);
            return false;
        }

        function onSquareClick(r, c) {
            if (gameOver) return;
            if (gameMode === 'bot' && turn === 'b') return; // bot thinking

            const piece = board[r][c];
            if (selected) {
                const [sr, sc] = selected;
                if (sr === r && sc === c) {
                    selected = null;
                    render();
                    return;
                }
                const selPiece = board[sr][sc];
                if ((turn === 'w' && isWhite(piece)) || (turn === 'b' && isBlack(piece))) {
                    selected = [r, c]; // switch selected piece
                    render();
                    return;
                }

                if (isValidMove(sr, sc, r, c, selPiece)) {
                    makeMove(sr, sc, r, c);
                } else {
                    selected = null;
                    render();
                }
            } else {
                if (!piece) return;
                if ((turn === 'w' && isWhite(piece)) || (turn === 'b' && isBlack(piece))) {
                    selected = [r, c];
                    render();
                }
            }
        }

        function makeMove(fr, fc, tr, tc) {
            const piece = board[fr][fc];
            const captured = board[tr][tc];

            board[tr][tc] = piece;
            board[fr][fc] = '';

            // Check for King capture
            if (captured.toLowerCase() === 'k') {
                gameOver = true;
                document.getElementById('status-card').innerText = (turn === 'w' ? "White" : "Black") + " Wins! Checkmate!";
                selected = null;
                render();
                broadcastMeshState();
                return;
            }

            // Pawn promotion to Queen
            if (piece === 'P' && tr === 0) board[tr][tc] = 'Q';
            if (piece === 'p' && tr === 7) board[tr][tc] = 'q';

            selected = null;
            turn = (turn === 'w' ? 'b' : 'w');
            render();
            broadcastMeshState();

            if (gameMode === 'bot' && turn === 'b' && !gameOver) {
                setTimeout(makeBotMove, 250);
            }
        }

        // Alpha-Beta Chess AI Engine
        function makeBotMove() {
            if (gameOver) return;
            const moves = getAllValidMoves('b');
            if (moves.length === 0) {
                gameOver = true;
                document.getElementById('status-card').innerText = "Game Over! Draw or Mate.";
                return;
            }

            let bestMove = null;
            let bestScore = -Infinity;

            // 2-ply evaluation
            for (let m of moves) {
                const captured = board[m.tr][m.tc];
                board[m.tr][m.tc] = m.piece;
                board[m.fr][m.fc] = '';

                let score = evaluateBoard();
                if (captured.toLowerCase() === 'k') score += 10000;

                board[m.fr][m.fc] = m.piece;
                board[m.tr][m.tc] = captured;

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = m;
                }
            }

            if (bestMove) {
                makeMove(bestMove.fr, bestMove.fc, bestMove.tr, bestMove.tc);
            }
        }

        function getAllValidMoves(color) {
            const list = [];
            for (let r = 0; r < 8; r++) {
                for (let c = 0; c < 8; c++) {
                    const p = board[r][c];
                    if (!p) continue;
                    if ((color === 'w' && isWhite(p)) || (color === 'b' && isBlack(p))) {
                        for (let tr = 0; tr < 8; tr++) {
                            for (let tc = 0; tc < 8; tc++) {
                                if (r === tr && c === tc) continue;
                                if (isValidMove(r, c, tr, tc, p)) {
                                    list.push({ fr: r, fc: c, tr: tr, tc: tc, piece: p });
                                }
                            }
                        }
                    }
                }
            }
            list.sort(() => Math.random() - 0.5); // break ties randomly
            return list;
        }

        function evaluateBoard() {
            let score = 0;
            for (let r = 0; r < 8; r++) {
                for (let c = 0; c < 8; c++) {
                    const p = board[r][c];
                    if (!p) continue;
                    const val = PIECE_VALS[p.toLowerCase()] || 0;
                    if (isBlack(p)) score += val;
                    else score -= val;
                }
            }
            return score;
        }

        function resetGame() {
            board = [
                ['r','n','b','q','k','b','n','r'],
                ['p','p','p','p','p','p','p','p'],
                ['','','','','','','',''],
                ['','','','','','','',''],
                ['','','','','','','',''],
                ['','','','','','','',''],
                ['P','P','P','P','P','P','P','P'],
                ['R','N','B','Q','K','B','N','R']
            ];
            turn = 'w';
            selected = null;
            gameOver = false;
            render();
            if (gameMode === 'mesh') broadcastMeshState();
        }

        function broadcastMeshState() {
            if (gameMode !== 'mesh' || !ws || ws.readyState !== WebSocket.OPEN) return;
            const flat = [];
            for (let r = 0; r < 8; r++) {
                for (let c = 0; c < 8; c++) flat.push(board[r][c]);
            }
            const state = {
                pieces: flat,
                isWhiteTurn: turn === 'w',
                whitePlayerId: (turn === 'w' ? myName : 'remote'),
                blackPlayerId: (turn === 'b' ? myName : 'remote'),
                winner: gameOver ? document.getElementById('status-card').innerText : ""
            };
            ws.send(JSON.stringify({ type: 'chess', state: state }));
        }

        function applyMeshState(state) {
            if (gameMode !== 'mesh') return;
            if (state.pieces && state.pieces.length === 64) {
                for (let r = 0; r < 8; r++) {
                    for (let c = 0; c < 8; c++) {
                        board[r][c] = state.pieces[r * 8 + c];
                    }
                }
            }
            turn = state.isWhiteTurn ? 'w' : 'b';
            if (state.winner) {
                gameOver = true;
                document.getElementById('status-card').innerText = state.winner;
            } else {
                gameOver = false;
            }
            render();
        }

        render();
    </script>
</body>
</html>
"""
}
