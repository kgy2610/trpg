const SESSION_KEY = 'trpg.session';
const STATUS_LABEL = {
  WAITING: '플레이어 입장을 기다리는 중',
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
const leaveBtn = document.getElementById('leave');
const startBtn = document.getElementById('start');

let stompClient = null;   // 나가기 때 연결을 끊기 위해 바깥에서도 접근
let stopped = false;      // 복구 불가능하거나 일부러 멈춘 경우

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

// GM 전용 요청 (내보내기, 재접속 허용/해제). 토큰은 URL이 아니라 헤더로 전달
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
    // 성공하면 서버가 대기실 방송을 보내므로 화면은 자동으로 갱신됨
  } catch (err) {
    showError(err.message);
  } finally {
    button.disabled = false;
  }
}

// 참가자 한 줄에 붙는 GM 버튼들 (버튼을 한 묶음에 담아 오른쪽 끝에 배치)
function appendGmControls(li, player, roomStatus) {
  // GM 본인에게만, 그리고 GM이 아닌 오프라인 참가자에게만 표시
  if (!session.gm || player.gm || player.online) return;
  if (roomStatus !== 'WAITING') return;

  const box = document.createElement('span');
  box.className = 'gm-controls';
  box.appendChild(makeActionButton('내보내기', (btn) => {
    if (!confirm(player.nickname + ' 님을 내보낼까요?')) return;
    gmPost('/kick', { nickname: player.nickname }, btn);
  }, 'danger'));
  li.appendChild(box);
}

function render(room) {
  // 게임이 시작되었으면 게임 화면으로 이동 (연결 끊김 문구가 깜빡이지 않도록 먼저 stopped 표시)
  if (room.status !== 'WAITING') {
    stopped = true;
    location.href = '/game.html';
    return;
  }

  document.getElementById('room-code').textContent = room.code;
  document.getElementById('count').textContent =
    '(' + room.players.filter(p => !p.gm).length + ' / ' + room.maxPlayers + ')';
  document.getElementById('status').textContent = STATUS_LABEL[room.status] || room.status;

  const list = document.getElementById('players');
  list.replaceChildren();
  room.players.forEach(p => {
    const li = document.createElement('li');
    if (p.nickname === session.nickname) li.classList.add('me');   // 내 줄은 배경을 채워서 표시

    // 접속 상태: 닉네임 앞의 점. 마우스를 올리면 글자로 안내
    const dot = document.createElement('span');
    const label = p.online ? '접속 중' : '오프라인';
    dot.className = 'dot ' + (p.online ? 'on' : 'off');
    dot.dataset.tip = label;
    dot.setAttribute('role', 'img');
    dot.setAttribute('aria-label', label);
    li.appendChild(dot);

    // 닉네임은 사용자 입력이므로 textContent로 넣어 HTML 삽입을 막음
    const nick = document.createElement('span');
    nick.textContent = p.nickname;
    li.appendChild(nick);

    if (p.gm) li.appendChild(makeBadge('GM'));
    appendGmControls(li, p, room.status);   // GM 전용 버튼은 그대로
    list.appendChild(li);
  });

  // 이 줄에 도달했다는 것은 대기 중(WAITING)이라는 뜻
  leaveBtn.hidden = session.gm;     // 나가기: GM이 아닐 때만
  startBtn.hidden = !session.gm;    // 게임 시작: GM에게만
}

async function loadSnapshot() {
  try {
    const res = await fetch('/api/rooms/' + encodeURIComponent(session.code));
    if (!res.ok) throw new Error('방 정보를 불러오지 못했습니다. (방이 사라졌을 수 있습니다)');
    render(await res.json());
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
      stompClient.subscribe('/topic/room/' + session.code + '/lobby', (m) => {
        render(JSON.parse(m.body));
      });
      loadSnapshot();
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

// 게임 시작 (GM)
startBtn.addEventListener('click', async () => {
  if (!confirm('게임을 시작할까요?\n시작하면 역할이 배정되고, 참가자를 내보내거나 새로 받을 수 없습니다.')) return;

  startBtn.disabled = true;   // 연속 클릭 방지
  errorBox.hidden = true;
  try {
    const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/start', {
      method: 'POST',
      headers: { 'X-Player-Token': session.token }
    });
    if (!res.ok) {
      let message = '게임을 시작하지 못했습니다.';
      try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
      throw new Error(message);
    }
    // 성공하면 서버가 방 상태 방송을 보내고, render()가 게임 화면으로 이동시킴
  } catch (err) {
    showError(err.message);
  } finally {
    startBtn.disabled = false;
  }
});

// 나가기
leaveBtn.addEventListener('click', async () => {
  if (!confirm('대기실에서 나갈까요?')) return;

  leaveBtn.disabled = true;   // 연속 클릭 방지
  errorBox.hidden = true;
  try {
    const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/players/me', {
      method: 'DELETE',
      headers: { 'X-Player-Token': session.token }
    });
    if (!res.ok) {
      let message = '나가기에 실패했습니다.';
      try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
      throw new Error(message);
    }

    // 성공: 실시간 연결을 끊고 저장된 세션을 지운 뒤 첫 화면으로
    stopped = true;
    if (stompClient) await stompClient.deactivate();
    sessionStorage.removeItem(SESSION_KEY);
    location.href = '/';
  } catch (err) {
    showError(err.message);
    leaveBtn.disabled = false;
  }
});

if (session) {
  startRealtime();
}