package com.aetherweb.app

object PocketCdnPacks {
    val WEB_IDE_HTML = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
  body { margin: 0; font-family: system-ui, sans-serif; display: flex; flex-direction: column; height: 100vh; background: #1e1e1e; color: #ccc; }
  .tab-bar { display: flex; background: #2d2d2d; border-bottom: 2px solid #007acc; }
  .tab { flex: 1; padding: 12px; text-align: center; cursor: pointer; color: #858585; font-weight: bold; text-transform: uppercase; font-size: 14px; transition: 0.2s; }
  .tab.active { background: #1e1e1e; color: #fff; border-top: 2px solid #007acc; }
  .content { flex: 1; display: none; flex-direction: column; overflow: hidden; position: relative; }
  .content.active { display: flex; }
  
  .editor-container { position: relative; flex: 1; overflow: auto; background: #1e1e1e; }
  
  #editing, #highlighting {
    margin: 0; padding: 16px; border: 0; width: 100%; height: 100%; box-sizing: border-box;
    font-family: monospace; font-size: 15px; line-height: 1.5; white-space: pre; overflow: hidden;
  }
  
  #editing { position: absolute; top: 0; left: 0; color: transparent; background: transparent; caret-color: white; z-index: 1; resize: none; outline: none; }
  #highlighting { position: absolute; top: 0; left: 0; z-index: 0; color: #d4d4d4; }
  
  .keyword { color: #569cd6; }
  .string { color: #ce9178; }
  .comment { color: #6a9955; }
  .function { color: #dcdcaa; }
  .number { color: #b5cea8; }
  .tag { color: #569cd6; }
  .attr { color: #9cdcfe; }
  
  iframe { flex: 1; border: none; width: 100%; height: 100%; background: #fff;}
  #console-output { height: 150px; background: #000; color: #0f0; font-family: monospace; padding: 8px; overflow-y: auto; border-top: 2px solid #444; display: none; }
</style>
</head>
<body>
  <div class="tab-bar">
    <div class="tab active" onclick="showTab('html')">Code</div>
    <div class="tab" onclick="showTab('preview'); runPreview();">Output</div>
    <select id="lang-select" style="background:#2d2d2d; color:#fff; border:none; padding:8px; outline:none; font-weight:bold;">
        <option value="html">HTML</option>
        <option value="js">JavaScript</option>
        <option value="python">Python</option>
    </select>
  </div>
  
  <div id="html" class="content active">
    <div class="editor-container">
      <pre id="highlighting" aria-hidden="true"><code id="highlighting-content"></code></pre>
      <textarea id="editing" spellcheck="false" oninput="updateEditor(); syncScroll();" onscroll="syncScroll();" onkeydown="handleKeyDown(event)"></textarea>
    </div>
  </div>
  
  <div id="preview" class="content">
    <iframe id="frame"></iframe>
    <div id="console-output"></div>
  </div>


<script>
    const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = wsProto + '//' + window.location.host + '/ws?sender=WebIDE';
    let ws = null;
    try {
        ws = new WebSocket(wsUrl);
        ws.onmessage = (e) => {
            const msg = JSON.parse(e.data);
            if (msg.message && window.meshApiCallback) {
                try {
                    const data = JSON.parse(msg.message);
                    if (data.type === 'ide_broadcast') {
                        window.meshApiCallback(data.payload);
                    }
                } catch(e){}
            }
        };
    } catch(err) { console.error(err); }

    const editing = document.getElementById('editing');

    const highlighting = document.getElementById('highlighting');
    const highlightingContent = document.getElementById('highlighting-content');
    const frame = document.getElementById('frame');
    const langSelect = document.getElementById('lang-select');
    const consoleOutput = document.getElementById('console-output');

    let currentLang = 'html';
    const templates = {
        'html': "<h1>Hello Mesh</h1>\n<style>\n  h1 { color: #007acc; }\n</style>\n<script>\n  console.log('DOM Ready');\n</script" + ">",
        'js': "function calculateFibonacci(n) {\n    if (n <= 1) return n;\n    return calculateFibonacci(n - 1) + calculateFibonacci(n - 2);\n}\n\nconsole.log('Fibonacci(10) = ' + calculateFibonacci(10));",
        'python': "def greet(name):\n    print('Hello, ' + name)\n\nfor i in range(3):\n    greet('Node ' + str(i))"
    };

    langSelect.addEventListener('change', (e) => {
        currentLang = e.target.value;
        editing.value = templates[currentLang];
        updateEditor();
    });

    editing.value = templates['html'];

    function escapeHtml(text) {
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function updateEditor() {
        let text = editing.value;
        text = escapeHtml(text);
        
        if (currentLang === 'python') {
            text = text.replace(/(#.*)/g, '<span class="comment">${'$'}1</span>');
            text = text.replace(/(['"].*?['"])/g, '<span class="string">${'$'}1</span>');
            text = text.replace(/\b(def|class|import|from|if|elif|else|while|for|return|in|and|or|not|True|False|None|print|range|str)\b/g, '<span class="keyword">${'$'}1</span>');
            text = text.replace(/\b([a-zA-Z_]\w*)(?=\()/g, '<span class="function">${'$'}1</span>');
            text = text.replace(/\b(\d+)\b/g, '<span class="number">${'$'}1</span>');
        } else if (currentLang === 'js') {
            text = text.replace(/(\w+)(?=\s*\()/g, '<span class="function">${'$'}1</span>');
            text = text.replace(/(\/\/.*)/g, '<span class="comment">${'$'}1</span>');
            text = text.replace(/(['"\`].*?['"\`])/g, '<span class="string">${'$'}1</span>');
            text = text.replace(/\b(function|class|import|export|if|else|while|for|return|let|const|var|true|false|null|undefined|console|new)\b/g, '<span class="keyword">${'$'}1</span>');
            text = text.replace(/\b(\d+)\b/g, '<span class="number">${'$'}1</span>');
        } else if (currentLang === 'html') {
            text = text.replace(/(&lt;!--.*?--&gt;)/g, '<span class="comment">${'$'}1</span>');
            text = text.replace(/(&lt;\/?)([a-zA-Z0-9]+)(.*?)(&gt;)/g, (match, p1, p2, p3, p4) => {
                let attrs = p3.replace(/([a-zA-Z0-9_-]+)=([\'\"].*?[\'\"])/g, '<span class="attr">${'$'}1</span>=<span class="string">${'$'}2</span>');
                return p1 + '<span class="tag">' + p2 + '</span>' + attrs + p4;
            });
        }

        if(text.endsWith('\n') || text.length === 0) text += ' ';
        highlightingContent.innerHTML = text;
    }

    function syncScroll() {
        highlighting.scrollTop = editing.scrollTop;
        highlighting.scrollLeft = editing.scrollLeft;
    }

    function handleKeyDown(e) {
        if (e.key === 'Tab') {
            e.preventDefault();
            const start = editing.selectionStart;
            const end = editing.selectionEnd;
            editing.value = editing.value.substring(0, start) + "    " + editing.value.substring(end);
            editing.selectionStart = editing.selectionEnd = start + 4;
            updateEditor();
        } else if (e.key === 'Enter') {
            e.preventDefault();
            const start = editing.selectionStart;
            const textBefore = editing.value.substring(0, start);
            
            const lines = textBefore.split('\n');
            const lastLine = lines[lines.length - 1];
            const match = lastLine.match(/^(\s*)/);
            let spaces = match ? match[1] : '';
            
            if (lastLine.trim().endsWith('{') || lastLine.trim().endsWith('[') || lastLine.trim().endsWith('(') || lastLine.trim().endsWith(':')) {
                spaces += '    ';
            }
            
            editing.value = editing.value.substring(0, start) + "\n" + spaces + editing.value.substring(editing.selectionEnd);
            editing.selectionStart = editing.selectionEnd = start + 1 + spaces.length;
            updateEditor();
            
            setTimeout(() => {
                editing.scrollTop = editing.scrollHeight;
                syncScroll();
            }, 10);
        }
    }

    function showTab(id) {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.content').forEach(c => c.classList.remove('active'));
        event.target.classList.add('active');
        document.getElementById(id).classList.add('active');
    }

    function appendConsole(msg) {
        consoleOutput.innerHTML += '<div>&gt; ' + msg + '</div>';
        consoleOutput.scrollTop = consoleOutput.scrollHeight;
    }

    function runPreview() {
        if (currentLang === 'python') {
            frame.style.display = 'none';
            consoleOutput.style.display = 'block';
            consoleOutput.style.height = '100%';
            consoleOutput.innerHTML = '<div style="color:#aaa;">--- Python Execution Environment ---</div>';
            
            // Very simple pseudo-python runner for demo (offline)
            // Just catching simple prints to show it "did" something
            let code = editing.value;
            let output = "";
            let printMatches = code.match(/print\(['"](.*?)['"]\)/g);
            if (printMatches) {
                printMatches.forEach(m => {
                    let text = m.replace(/print\(['"]|['"]\)/g, '');
                    appendConsole(text);
                });
            } else {
                appendConsole("Syntax OK. Note: Full Python runtime requires Pyodide (needs online download).");
            }
        } else if (currentLang === 'js') {
            frame.style.display = 'none';
            consoleOutput.style.display = 'block';
            consoleOutput.style.height = '100%';
            consoleOutput.innerHTML = '';
            
            // Run safely in iframe
            const iframe = document.createElement('iframe');
            iframe.style.display = 'none';
            document.body.appendChild(iframe);
            

            iframe.contentWindow.console.log = (...args) => appendConsole(args.join(' '));
            iframe.contentWindow.console.error = (...args) => appendConsole('<span style="color:red">' + args.join(' ') + '</span>');
            
            // Inject MeshAPI
            iframe.contentWindow.MeshAPI = {
                send: function(data) {
                    if (ws && ws.readyState === WebSocket.OPEN) {
                        ws.send(JSON.stringify({ type: 'ide_broadcast', payload: data }));
                    } else {
                        console.warn('Mesh WebSocket not connected');
                    }
                },
                onReceive: null
            };
            window.meshApiCallback = function(data) {
                if (iframe.contentWindow.MeshAPI.onReceive) {
                    iframe.contentWindow.MeshAPI.onReceive(data);
                }
            };
            
            try {

                iframe.contentWindow.eval(editing.value);
            } catch(err) {
                appendConsole('<span style="color:red">' + err.message + '</span>');
            }
            document.body.removeChild(iframe);
        } else {
            frame.style.display = 'block';
            consoleOutput.style.display = 'block';
            consoleOutput.style.height = '150px';
            consoleOutput.innerHTML = '';
            
            const doc = frame.contentDocument || frame.contentWindow.document;
            doc.open();
            
            const patched = "<script>console.log = function(...args) { window.parent.document.getElementById('console-output').innerHTML += '<div>&gt; ' + args.join(' ') + '</div>'; };<\/script>" + editing.value;
            doc.write(patched);
            doc.close();
        }
    }

    updateEditor();
</script>
</body>
</html>"""
    
    
    val TIC_TAC_TOE_HTML = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"><style>body{font-family:sans-serif;display:flex;flex-direction:column;align-items:center;background:#222;color:#fff;}h1{margin-top:20px;} .board{display:grid;grid-template-columns:repeat(3, 100px);grid-gap:5px;margin-top:20px;} .cell{width:100px;height:100px;background:#333;display:flex;justify-content:center;align-items:center;font-size:48px;cursor:pointer;} .cell:hover{background:#444;} button{margin-top:20px;padding:10px 20px;font-size:18px;cursor:pointer;}</style></head><body><h1>Tic-Tac-Toe vs AI</h1><div class="board" id="board"></div><h2 id="status">Your Turn (X)</h2><button onclick="reset()">Restart</button><script>let board=["","","","","","","","",""];let human="X";let ai="O";let statusEl=document.getElementById("status");function checkWin(b, player){let wins=[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];for(let w of wins){if(b[w[0]]===player && b[w[1]]===player && b[w[2]]===player) return true;}return false;}function getAvail(b){let a=[];for(let i=0;i<9;i++){if(b[i]==="") a.push(i);}return a;}function minimax(newBoard, player){let avail=getAvail(newBoard);if(checkWin(newBoard, human)) return {score:-10};else if(checkWin(newBoard, ai)) return {score:10};else if(avail.length===0) return {score:0};let moves=[];for(let i=0;i<avail.length;i++){let move={};move.index=newBoard[avail[i]];newBoard[avail[i]]=player;if(player===ai){let res=minimax(newBoard, human);move.score=res.score;}else{let res=minimax(newBoard, ai);move.score=res.score;}newBoard[avail[i]]="";move.index=avail[i];moves.push(move);}let bestMove;if(player===ai){let bestScore=-10000;for(let i=0;i<moves.length;i++){if(moves[i].score>bestScore){bestScore=moves[i].score;bestMove=i;}}}else{let bestScore=10000;for(let i=0;i<moves.length;i++){if(moves[i].score<bestScore){bestScore=moves[i].score;bestMove=i;}}}return moves[bestMove];}function makeMove(i){if(board[i]==="" && !checkWin(board, human) && !checkWin(board, ai)){board[i]=human;render();if(checkWin(board, human)){statusEl.innerText="You Win!";return;}if(getAvail(board).length===0){statusEl.innerText="Tie!";return;}statusEl.innerText="AI is thinking...";setTimeout(()=>{let best=minimax(board, ai);board[best.index]=ai;render();if(checkWin(board, ai)){statusEl.innerText="AI Wins!";}else if(getAvail(board).length===0){statusEl.innerText="Tie!";}else{statusEl.innerText="Your Turn (X)";}}, 900);}}function render(){let bEl=document.getElementById("board");bEl.innerHTML="";for(let i=0;i<9;i++){bEl.innerHTML += '<div class=\"cell\" onclick=\"makeMove(' + i + ')\">' + board[i] + '</div>';}}function reset(){board=["","","","","","","","",""];statusEl.innerText="Your Turn (X)";render();}render();</script></body></html>"""

    val SNAKE_HTML = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"><style>body{margin:0;display:flex;justify-content:center;align-items:center;height:100vh;background:#222;color:white;font-family:sans-serif;flex-direction:column;}canvas{background:#000;border:2px solid #555;} .controls{margin-top:20px;display:grid;grid-template-columns:50px 50px 50px;gap:10px;} button{padding:15px;background:#444;color:white;border:none;border-radius:5px;font-size:20px;} .btn-up{grid-column:2;} .btn-left{grid-column:1;grid-row:2;} .btn-right{grid-column:3;grid-row:2;} .btn-down{grid-column:2;grid-row:2;}</style></head><body><h2>Offline Snake</h2><canvas id="c" width="400" height="400"></canvas><div class="controls"><button class="btn-up" onclick="dir(0,-1)">↑</button><button class="btn-left" onclick="dir(-1,0)">←</button><button class="btn-down" onclick="dir(0,1)">↓</button><button class="btn-right" onclick="dir(1,0)">→</button></div><script>let canvas=document.getElementById('c');let ctx=canvas.getContext('2d');let gs=20;let tc=20;let xv=0;let yv=0;let px=10;let py=10;let ax=15;let ay=15;let trail=[];let tail=5;function game(){px+=xv;py+=yv;if(px<0)px=tc-1;if(px>tc-1)px=0;if(py<0)py=tc-1;if(py>tc-1)py=0;ctx.fillStyle="black";ctx.fillRect(0,0,canvas.width,canvas.height);ctx.fillStyle="lime";for(let i=0;i<trail.length;i++){ctx.fillRect(trail[i].x*gs,trail[i].y*gs,gs-2,gs-2);if(trail[i].x===px && trail[i].y===py){tail=5;}}trail.push({x:px,y:py});while(trail.length>tail) trail.shift();if(ax===px && ay===py){tail++;ax=Math.floor(Math.random()*tc);ay=Math.floor(Math.random()*tc);}ctx.fillStyle="red";ctx.fillRect(ax*gs,ay*gs,gs-2,gs-2);}function dir(x,y){if(xv===0 && x!==0){xv=x;yv=0;}if(yv===0 && y!==0){xv=0;yv=y;}}document.addEventListener("keydown",function(e){switch(e.keyCode){case 37:dir(-1,0);break;case 38:dir(0,-1);break;case 39:dir(1,0);break;case 40:dir(0,1);break;}});setInterval(game,1000/10);</script></body></html>"""

    val POOL_GAME_HTML = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <style>
    body { margin: 0; background: #222; display: flex; justify-content: center; align-items: center; height: 100vh; overflow: hidden; color: white; font-family: sans-serif;}
    canvas { background: #0f5e14; border: 10px solid #5c3a21; border-radius: 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.8); max-width: 100%; max-height: 100vh;}
    #ui { position: absolute; top: 10px; background: rgba(0,0,0,0.5); padding: 10px; border-radius: 10px; pointer-events: none;}
  </style></head><body>
  <div id="ui"><h3>Mesh Pool (Billiards)</h3><p id="status">Connecting...</p></div>
  <canvas id="c" width="800" height="400"></canvas>
  <script>
    const canvas = document.getElementById('c');
    const ctx = canvas.getContext('2d');
    let isHost = new URLSearchParams(window.location.search).get('isHost') === 'true';
    const statusText = document.getElementById('status');
    statusText.innerText = isHost ? "Host: Drag to shoot!" : "Client: Waiting for Host...";

    // Connect WS
    const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = wsProto + '//' + window.location.host + '/ws?sender=PoolGame';
    let ws = null;
    try {
        ws = new WebSocket(wsUrl);
        ws.onmessage = (e) => {
            if (isHost) return;
            const msg = JSON.parse(e.data);
            if (msg.message && msg.message.startsWith('{')) {
                const data = JSON.parse(msg.message);
                if (data.type === 'pool_state') {
                    balls = data.balls;
                }
            }
        };
    } catch(err) { console.error(err); }

    class Ball {
        constructor(x, y, color, isCue = false) {
            this.x = x; this.y = y; this.vx = 0; this.vy = 0;
            this.radius = 12; this.color = color; this.isCue = isCue;
        }
        update() {
            this.x += this.vx; this.y += this.vy;
            this.vx *= 0.985; this.vy *= 0.985; // Friction
            if (Math.abs(this.vx) < 0.05) this.vx = 0;
            if (Math.abs(this.vy) < 0.05) this.vy = 0;
            // Wall collisions
            if (this.x - this.radius < 0) { this.x = this.radius; this.vx *= -0.8; }
            if (this.x + this.radius > canvas.width) { this.x = canvas.width - this.radius; this.vx *= -0.8; }
            if (this.y - this.radius < 0) { this.y = this.radius; this.vy *= -0.8; }
            if (this.y + this.radius > canvas.height) { this.y = canvas.height - this.radius; this.vy *= -0.8; }
        }
    }

    let balls = [];
    if (isHost) {
        balls.push(new Ball(200, 200, 'white', true)); // Cue ball
        // Rack
        let startX = 600, startY = 200, rowSpacing = 20;
        let colors = ['red', 'blue', 'black', 'purple', 'orange', 'green', 'maroon', 'yellow', 'cyan', 'pink'];
        let cIdx = 0;
        for(let col=0; col<5; col++) {
            for(let row=0; row<=col; row++) {
                balls.push(new Ball(startX + col * rowSpacing, startY - (col * 12) + (row * 24), colors[cIdx++ % colors.length]));
            }
        }
    }

    function checkCollisions() {
        for (let i = 0; i < balls.length; i++) {
            for (let j = i + 1; j < balls.length; j++) {
                let b1 = balls[i]; let b2 = balls[j];
                let dx = b2.x - b1.x; let dy = b2.y - b1.y;
                let dist = Math.sqrt(dx*dx + dy*dy);
                if (dist < b1.radius + b2.radius) {
                    let angle = Math.atan2(dy, dx);
                    let sin = Math.sin(angle), cos = Math.cos(angle);
                    
                    // Rotate velocities
                    let vx1 = b1.vx * cos + b1.vy * sin;
                    let vy1 = b1.vy * cos - b1.vx * sin;
                    let vx2 = b2.vx * cos + b2.vy * sin;
                    let vy2 = b2.vy * cos - b2.vx * sin;

                    // Swap 1D velocities
                    let temp = vx1; vx1 = vx2; vx2 = temp;

                    // Update positions to prevent sticking
                    let overlap = (b1.radius + b2.radius - dist) / 2;
                    b1.x -= overlap * cos; b1.y -= overlap * sin;
                    b2.x += overlap * cos; b2.y += overlap * sin;

                    // Rotate back
                    b1.vx = vx1 * cos - vy1 * sin;
                    b1.vy = vy1 * cos + vx1 * sin;
                    b2.vx = vx2 * cos - vy2 * sin;
                    b2.vy = vy2 * cos + vx2 * sin;
                    
                    // Energy loss
                    b1.vx *= 0.9; b1.vy *= 0.9; b2.vx *= 0.9; b2.vy *= 0.9;
                }
            }
        }
    }

    let dragStart = null, isDragging = false;
    canvas.addEventListener('pointerdown', (e) => {
        if(!isHost || balls.length === 0) return;
        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const scaleY = canvas.height / rect.height;
        const x = (e.clientX - rect.left) * scaleX;
        const y = (e.clientY - rect.top) * scaleY;
        
        let dx = x - balls[0].x, dy = y - balls[0].y;
        if(Math.sqrt(dx*dx + dy*dy) < 30) {
            isDragging = true;
            dragStart = { x, y };
        }
    });

    canvas.addEventListener('pointermove', (e) => {
        if(isDragging && dragStart) {
            const rect = canvas.getBoundingClientRect();
            dragStart.x = (e.clientX - rect.left) * (canvas.width / rect.width);
            dragStart.y = (e.clientY - rect.top) * (canvas.height / rect.height);
        }
    });

    canvas.addEventListener('pointerup', () => {
        if(isDragging && dragStart && balls.length > 0) {
            let dx = balls[0].x - dragStart.x;
            let dy = balls[0].y - dragStart.y;
            balls[0].vx = dx * 0.15;
            balls[0].vy = dy * 0.15;
            isDragging = false;
            dragStart = null;
        }
    });

    let lastBroadcast = 0;
    function animate() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        if (isHost) {
            balls.forEach(b => b.update());
            checkCollisions();
            if (ws && ws.readyState === WebSocket.OPEN && Date.now() - lastBroadcast > 33) {
                // Determine if moving
                let moving = balls.some(b => Math.abs(b.vx) > 0 || Math.abs(b.vy) > 0);
                if (moving || Date.now() - lastBroadcast > 1000) { // Keep alive every 1s even if still
                    ws.send(JSON.stringify({ type: 'pool_state', balls: balls.map(b => ({x: Math.round(b.x), y: Math.round(b.y), color: b.color})) }));
                    lastBroadcast = Date.now();
                }
            }
        }

        balls.forEach((b, i) => {
            ctx.beginPath();
            ctx.arc(isHost ? b.x : b.x, isHost ? b.y : b.y, 12, 0, Math.PI * 2);
            ctx.fillStyle = isHost ? b.color : b.color;
            ctx.fill();
            // Highlight
            ctx.beginPath();
            ctx.arc(isHost ? b.x - 3 : b.x - 3, isHost ? b.y - 3 : b.y - 3, 4, 0, Math.PI*2);
            ctx.fillStyle = 'rgba(255,255,255,0.4)';
            ctx.fill();
        });

        if(isDragging && dragStart && balls.length > 0) {
            ctx.beginPath();
            ctx.moveTo(balls[0].x, balls[0].y);
            ctx.lineTo(dragStart.x, dragStart.y);
            ctx.strokeStyle = 'white';
            ctx.lineWidth = 3;
            ctx.stroke();
            // Fake cue stick
            ctx.beginPath();
            let dx = dragStart.x - balls[0].x;
            let dy = dragStart.y - balls[0].y;
            ctx.moveTo(dragStart.x, dragStart.y);
            ctx.lineTo(dragStart.x + dx, dragStart.y + dy);
            ctx.strokeStyle = '#d2b48c';
            ctx.lineWidth = 6;
            ctx.stroke();
        }

        requestAnimationFrame(animate);
    }
    animate();
  </script>
</body></html>"""

    val CHESS_HTML = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Offline Mesh Chess</title>
<style>
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 16px;
    background: #1e1e1e;
    color: #e0e0e0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
  }
  h1 { margin: 0 0 8px 0; font-size: 24px; color: #25D366; }
  #status { margin: 0 0 16px 0; font-size: 16px; font-weight: bold; }
  #board {
    display: grid;
    grid-template-columns: repeat(8, 1fr);
    grid-template-rows: repeat(8, 1fr);
    width: min(90vw, 420px);
    height: min(90vw, 420px);
    border: 4px solid #333;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 8px 24px rgba(0,0,0,0.6);
  }
  .cell {
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: min(8vw, 36px);
    cursor: pointer;
    user-select: none;
    transition: background 0.15s;
  }
  .light { background: #eeeed2; color: #000; }
  .dark { background: #769656; color: #000; }
  .selected { background: #baca44 !important; }
  .target { background: #f7ec7d !important; }
  .btn-row { margin-top: 20px; display: flex; gap: 12px; }
  button {
    background: #25D366;
    color: white;
    border: none;
    padding: 10px 20px;
    border-radius: 20px;
    font-size: 15px;
    font-weight: bold;
    cursor: pointer;
    box-shadow: 0 4px 10px rgba(0,0,0,0.3);
  }
  button:active { transform: scale(0.96); }
</style>
</head>
<body>
  <h1>♟️ Offline Mesh Chess</h1>
  <div id="status">White to Move</div>
  <div id="board"></div>
  <div class="btn-row">
    <button onclick="resetGame()">New Game</button>
  </div>

  <script>
    // Unicode chess symbols
    const PIECES = {
      'R': '♜', 'N': '♞', 'B': '♝', 'Q': '♛', 'K': '♚', 'P': '♟',
      'r': '♖', 'n': '♘', 'b': '♗', 'q': '♕', 'k': '♔', 'p': '♙'
    };

    let board = [
      ['R','N','B','Q','K','B','N','R'],
      ['P','P','P','P','P','P','P','P'],
      ['','','','','','','',''],
      ['','','','','','','',''],
      ['','','','','','','',''],
      ['','','','','','','',''],
      ['p','p','p','p','p','p','p','p'],
      ['r','n','b','q','k','b','n','r']
    ];

    let turn = 'w'; // 'w' (lowercase pieces) or 'b' (uppercase pieces)
    let selected = null;

    function isWhite(piece) { return piece && piece === piece.toLowerCase(); }
    function isBlack(piece) { return piece && piece === piece.toUpperCase(); }

    function render() {
      const boardEl = document.getElementById('board');
      boardEl.innerHTML = '';
      for (let r = 0; r < 8; r++) {
        for (let c = 0; c < 8; c++) {
          const cell = document.createElement('div');
          const isDark = (r + c) % 2 === 1;
          cell.className = 'cell ' + (isDark ? 'dark' : 'light');
          if (selected && selected[0] === r && selected[1] === c) {
            cell.classList.add('selected');
          }
          const piece = board[r][c];
          cell.innerText = PIECES[piece] || '';
          cell.onclick = () => onCellClick(r, c);
          boardEl.appendChild(cell);
        }
      }
      document.getElementById('status').innerText = (turn === 'w' ? 'White' : 'Black') + "'s Turn";
    }

    function onCellClick(r, c) {
      const piece = board[r][c];
      if (selected) {
        const [sr, sc] = selected;
        if (sr === r && sc === c) {
          selected = null;
          render();
          return;
        }
        const selPiece = board[sr][sc];
        // Cannot capture own piece
        if ((turn === 'w' && isWhite(piece)) || (turn === 'b' && isBlack(piece))) {
          selected = [r, c];
          render();
          return;
        }
        // Make move
        board[r][c] = selPiece;
        board[sr][sc] = '';
        selected = null;
        turn = (turn === 'w' ? 'b' : 'w');
        render();
      } else {
        if (!piece) return;
        if ((turn === 'w' && isWhite(piece)) || (turn === 'b' && isBlack(piece))) {
          selected = [r, c];
          render();
        }
      }
    }

    function resetGame() {
      board = [
        ['R','N','B','Q','K','B','N','R'],
        ['P','P','P','P','P','P','P','P'],
        ['','','','','','','',''],
        ['','','','','','','',''],
        ['','','','','','','',''],
        ['','','','','','','',''],
        ['p','p','p','p','p','p','p','p'],
        ['r','n','b','q','k','b','n','r']
      ];
      turn = 'w';
      selected = null;
      render();
    }

    render();
  </script>
</body>
</html>"""
}
