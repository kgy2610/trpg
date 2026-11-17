const session = JSON.parse(sessionStorage.getItem('trpg.session') || 'null');
const logBox = document.getElementById('log');

function log(msg) {
  const li = document.createElement('li');
  li.textContent = msg;
  logBox.prepend(li);
}

if (!session) {
  log('세션 없음: 방에 입장한 같은 탭에서 이 페이지를 열어 주세요.');
} else {
  const protocol = location.protocol === 'https:' ? 'wss://' : 'ws://';
  const client = new StompJs.Client({
    brokerURL: protocol + location.host + '/ws',
    connectHeaders: { token: session.token },   // 토큰은 URL이 아니라 STOMP 헤더로 전달
    reconnectDelay: 3000,
    onConnect: () => {
      log('연결됨: ' + session.nickname);
      client.subscribe('/user/queue/pong', (m) => log('[개인] ' + m.body));
      client.subscribe('/topic/ping', (m) => log('[전체] ' + m.body));
      client.subscribe('/topic/room/' + session.code + '/lobby', (m) => log('[대기실] ' + m.body));
      // [추가] 게임 시작 때 서버가 보내는 내 역할 (본인 것만 도착해야 함)
      client.subscribe('/user/queue/role', (m) => log('[내 역할] ' + m.body));
    },
    onStompError: (frame) => log('STOMP 오류: ' + frame.headers['message']),
    onWebSocketClose: () => log('연결 끊김')
  });
  client.activate();

  document.getElementById('ping-me').onclick = () =>
    client.publish({ destination: '/app/ping', body: '' });
  document.getElementById('ping-all').onclick = () =>
    client.publish({ destination: '/app/ping-all', body: '' });
}