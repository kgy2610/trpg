const SESSION_KEY = 'trpg.session';
const PHASE_LABEL = {
    READING: '역할을 확인하는 중',
    PLAYING: '게임 진행 중',
    ENDED: '게임 종료'
};

const session = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null');
if (!session) {
    location.href = '/';
}

const errorBox = document.getElementById('error');
const connBox = document.getElementById('conn');
const secretTitle = document.getElementById('secret-title');
const secretBody = document.getElementById('secret-body');
const secretToggle = document.getElementById('secret-toggle');
const readyBtn = document.getElementById('ready-btn');
const beginBtn = document.getElementById('begin-btn');

let stompClient = null;
let stopped = false;     // 복구 불가능한 오류로 연결을 멈춘 경우
let lastStatus = null;   // 마지막으로 화면에 반영한 방 상태
let timerId = null;
let secret = null;       // 내 비밀 카드 (GM은 null)
let revealed = false;    // 비밀 카드를 펼쳐 둔 상태인지

function setText(id, text) {
    document.getElementById(id).textContent = text;
}

function setConn(text) {
    connBox.textContent = text;
}

function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
}

function makeBadge(text, extraClass) {
    const badge = document.createElement('span');
    badge.className = 'badge' + (extraClass ? ' ' + extraClass : '');
    badge.textContent = text;
    return badge;
}

// 작은 버튼 만들기
function makeActionButton(label, onClick, extraClass) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'mini' + (extraClass ? ' ' + extraClass : '');
    button.textContent = label;
    button.addEventListener('click', () => onClick(button));
    return button;
}

// GM 전용 요청 (재접속 허용/해제). 토큰은 URL이 아니라 헤더로 전달
async function gmPost(path, body, button) {
  button.disabled = true;   // 연속 클릭 방지
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Player-Token': session.token },
        body: JSON.stringify(body)
        });
        if (!res.ok) {
        let message = '요청에 실패했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
        // 성공하면 서버가 방송을 보내므로 화면은 자동으로 갱신됨
    } catch (err) {
        showError(err.message);
    } finally {
        button.disabled = false;
    }
}

// 본문 없는 요청 (읽었어요, 바로 시작). 실패했을 때만 버튼을 다시 누를 수 있게 함
// 성공하면 서버 방송으로 화면이 다시 그려지면서 버튼 상태가 정해지므로 여기서 건드리지 않음
async function postAction(path, button) {
    button.disabled = true;   // 연속 클릭 방지
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + path, {
        method: 'POST',
        headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) {
        let message = '요청에 실패했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
    } catch (err) {
        showError(err.message);
        button.disabled = false;
    }
}

// 읽었어요 (플레이어)
readyBtn.addEventListener('click', () => postAction('/ready', readyBtn));

// 바로 게임 시작 (GM)
beginBtn.addEventListener('click', () => {
    if (!confirm('읽기 시간을 건너뛰고 바로 게임을 시작할까요?')) return;
    postAction('/begin', beginBtn);
});

// 캐릭터 카드
function renderCharacter(c) {
    setText('char-name', c.name);
    setText('char-job', c.job);
    setText('char-concept', c.concept);
    setText('stat-strength', c.strength);
    setText('stat-mind', c.mind);
    setText('stat-charm', c.charm);

    const list = document.getElementById('char-abilities');
    list.replaceChildren();
    c.abilities.forEach((text) => {
        const li = document.createElement('li');
        li.textContent = text;
        list.appendChild(li);
    });
}

// 비밀 카드: 가려 둔 상태에서는 문구를 화면 요소에서 아예 지워 둠 (CSS로 가리기만 하면 복사/검색으로 보일 수 있음)
function updateSecret() {
    if (!secret) return;
    secretTitle.textContent = revealed ? secret.title : '비밀 카드';
    secretBody.textContent = revealed
        ? secret.description
        : '버튼을 눌러 확인하세요. 먼저 주변 사람이 화면을 볼 수 없는지 확인하세요.';
    secretToggle.textContent = revealed ? '가리기' : '탭해서 보기';
}

secretToggle.addEventListener('click', () => {
    revealed = !revealed;
    updateSecret();
});

// GM 배정표
function renderAssignments(list) {
    const body = document.getElementById('assign-body');
    body.replaceChildren();
    list.forEach((a) => {
        const tr = document.createElement('tr');

        const nameTd = document.createElement('td');
        nameTd.textContent = a.nickname;
        tr.appendChild(nameTd);

        const charTd = document.createElement('td');
        charTd.textContent = a.characterName;
        tr.appendChild(charTd);

        const roleTd = document.createElement('td');
        roleTd.appendChild(makeBadge(a.secretTitle, a.secretTitle === '밀항자' ? 'stowaway' : ''));
        tr.appendChild(roleTd);

        body.appendChild(tr);
    });
}

// 참가자 한 줄에 붙는 GM 버튼 (GM에게만, 게임 중에 오프라인인 일반 참가자에게만 표시)
function appendGmControls(li, player, roomStatus) {
    if (!session.gm || player.gm || player.online) return;
    if (roomStatus !== 'READING' && roomStatus !== 'PLAYING') return;

    const box = document.createElement('span');
    box.className = 'gm-controls';

    if (player.rejoinAllowed) {
        box.appendChild(makeBadge('재접속 허용됨', 'on'));
        box.appendChild(makeActionButton('허용 해제', (btn) => {
        gmPost('/rejoin-permission', { nickname: player.nickname, allowed: false }, btn);
        }));
    } else {
        box.appendChild(makeActionButton('재접속 허용', (btn) => {
        gmPost('/rejoin-permission', { nickname: player.nickname, allowed: true }, btn);
        }));
    }

    li.appendChild(box);
}

// 읽기 단계의 버튼과 "읽은 사람 n / N" 표시를 방 정보에 맞춰 갱신
function updateReadingControls(room) {
    const players = room.players.filter((p) => !p.gm);
    const readyCount = players.filter((p) => p.ready).length;
    setText('ready-count', '읽은 사람 ' + readyCount + ' / ' + players.length);

    const reading = room.status === 'READING';
    const me = room.players.find((p) => p.nickname === session.nickname);
    const iAmReady = !!(me && me.ready);

    readyBtn.hidden = !(reading && !session.gm);   // 읽었어요: 읽기 단계의 플레이어에게만
    readyBtn.disabled = iAmReady;
    readyBtn.textContent = iAmReady ? '읽었어요 ✓' : '읽었어요';
    beginBtn.hidden = !(reading && session.gm);    // 바로 시작: 읽기 단계의 GM에게만
}

// 참가자 목록 (모두에게 접속 상태 표시)
function renderPlayers(room) {
    const list = document.getElementById('players');
    list.replaceChildren();
    room.players.forEach((p) => {
        const li = document.createElement('li');
        // 닉네임은 사용자 입력이므로 textContent로 넣어 HTML 삽입을 막음
        li.textContent = p.nickname;
        if (p.gm) li.appendChild(makeBadge('GM'));
        if (p.nickname === session.nickname) li.appendChild(makeBadge('나'));
        li.appendChild(p.online ? makeBadge('접속 중', 'on') : makeBadge('오프라인', 'off'));
        // 읽기 단계에서는 읽음 여부도 표시
        if (room.status === 'READING' && !p.gm) {
        li.appendChild(p.ready ? makeBadge('읽음', 'on') : makeBadge('읽는 중'));
        }
        appendGmControls(li, p, room.status);
        list.appendChild(li);
    });
    updateReadingControls(room);
    renderClock(room);
}

// 참가자 목록을 처음 한 번 REST로 가져옴 (구독 후 호출해서 그 사이의 변화를 놓치지 않음)
async function loadPlayers() {
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code));
        if (res.ok) renderPlayers(await res.json());
    } catch (err) {
        // 실패해도 다음 방송 때 갱신되므로 무시
    }
}

// 카운트다운: 서버가 알려준 "지금 시각"과 내 시계의 차이를 보정해서 모두가 같은 시간을 보게 함
function stopTimer() {
    if (timerId !== null) {
        clearInterval(timerId);
        timerId = null;
    }
}

function startTimer(endsAtMillis, serverNowMillis) {
    stopTimer();
    const offset = serverNowMillis - Date.now();

    const tick = () => {
        const remaining = endsAtMillis - (Date.now() + offset);
        if (remaining <= 0) {
        setText('timer', '00:00');
        setText('timer-note', '읽기 시간이 끝났습니다. 곧 게임이 시작됩니다.');   // [수정] 서버가 자동으로 전환함
        stopTimer();
        return;
        }
        const total = Math.ceil(remaining / 1000);
        const mm = String(Math.floor(total / 60)).padStart(2, '0');
        const ss = String(total % 60).padStart(2, '0');
        setText('timer', mm + ':' + ss);
    };

    setText('timer-note', '남은 시간 동안 아래 카드를 읽고 숙지하세요.');
    tick();
    timerId = setInterval(tick, 1000);
}

// 서버에서 받은 내 역할을 화면에 반영 (조회 API 응답과 개인 채널 푸시가 같은 형식)
function renderRole(role) {
    lastStatus = role.status;
    setText('phase', PHASE_LABEL[role.status] || role.status);
    revealed = false;   // 다시 그릴 때마다 비밀 카드는 가린 상태로 시작

    document.getElementById('gm-card').hidden = !role.gm;
    document.getElementById('character-card').hidden = role.gm;
    document.getElementById('secret-card').hidden = role.gm;
    document.getElementById('players-hint').hidden = !role.gm;   // 안내 문구는 GM에게만

    if (role.gm) {
        secret = null;
        renderAssignments(role.assignments);
    } else {
        renderCharacter(role.character);
        secret = role.secret;
        updateSecret();
    }

    const showTimer = role.status === 'READING' && role.readingEndsAtMillis;
    document.getElementById('timer-card').hidden = !showTimer;
    if (showTimer) {
        startTimer(role.readingEndsAtMillis, role.serverNowMillis);
    } else {
        stopTimer();
    }
}

// 내 역할 가져오기 (페이지를 열 때, 새로고침했을 때, 재연결했을 때)
async function loadRole() {
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/me/role', {
        headers: { 'X-Player-Token': session.token }
        });
        if (res.status === 401 || res.status === 403) {
        // 토큰이 무효: 저장된 세션을 지우고 첫 화면으로
        sessionStorage.removeItem(SESSION_KEY);
        location.href = '/';
        return;
        }
        if (!res.ok) {
        let message = '내 역할을 불러오지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
        renderRole(await res.json());
        errorBox.hidden = true;
    } catch (err) {
        showError(err.message);
    }
}

function startRealtime() {
    const protocol = location.protocol === 'https:' ? 'wss://' : 'ws://';
    stompClient = new StompJs.Client({
        brokerURL: protocol + location.host + '/ws',
        connectHeaders: { token: session.token },
        reconnectDelay: 3000,
        onConnect: () => {
        setConn('실시간 연결됨');
        // 게임 시작 때 서버가 보내는 내 역할 (본인 것만 도착)
        stompClient.subscribe('/user/queue/role', (m) => renderRole(JSON.parse(m.body)));
        // 방 변동 방송: 참가자 목록 갱신 + 방 상태가 바뀌면 내 역할 화면을 다시 불러옴
        stompClient.subscribe('/topic/room/' + session.code + '/lobby', (m) => {
            const room = JSON.parse(m.body);
            renderPlayers(room);
            if (room.status !== lastStatus) loadRole();
        });
        loadRole();      // 구독 후에 한 번 조회해서 그 사이의 변화를 놓치지 않음
        loadPlayers();
        },
        onWebSocketClose: () => {
        if (!stopped) setConn('연결이 끊겼습니다. 다시 연결하는 중…');
        },
        onStompError: (frame) => {
        stopped = true;
        setConn('실시간 연결에 실패했습니다.');
        showError('연결 오류: ' + (frame.headers['message'] || '알 수 없는 오류') + ' — 처음 화면에서 다시 입장해 주세요.');
        stompClient.deactivate();
        }
    });

    setConn('연결 중…');
    stompClient.activate();
}

if (session) {
    startRealtime();
}