let revealedList = [];        // 공개된 단서 (공개한 순서)
let revealedKeys = new Set(); // 공개된 단서의 키 모음
let gmClues = null;           // GM 전용: 공개 전 단서까지 포함한 전체 목록

// 단서 카드를 보일지 결정 (game.js의 renderPlayers가 호출). 게임 진행 중에만 표시
function renderClueCards(room) {
    const show = room.status === 'PLAYING';
    document.getElementById('clues-card').hidden = !show || session.gm;        // 플레이어에게만
    document.getElementById('clue-gm-card').hidden = !(show && session.gm);    // GM에게만
}

// 공개된 단서 목록을 받아 두 화면(플레이어 카드, GM 패널)을 다시 그림. 방송 수신과 새로고침 조회가 같이 사용
function renderClues(list) {
    revealedList = list;
    revealedKeys = new Set(list.map((c) => c.key));
    renderRevealedClues();
    renderGmPanel();
}

// 플레이어용: 공개된 단서 카드 (새로 공개된 것이 맨 위)
function renderRevealedClues() {
    const box = document.getElementById('clues-list');
    box.replaceChildren();
    document.getElementById('clues-empty').hidden = revealedList.length > 0;

    [...revealedList].reverse().forEach((clue) => {
        const item = document.createElement('div');
        item.className = 'clue-item';

        const head = document.createElement('div');
        head.className = 'clue-head';
        head.appendChild(makeBadge(clue.location));
        const title = document.createElement('strong');
        title.textContent = clue.title;
        head.appendChild(title);
        item.appendChild(head);

        const body = document.createElement('p');
        body.className = 'clue-body';
        body.textContent = clue.content;   // 서버가 준 글이라도 textContent로 넣어 HTML 삽입을 막음
        item.appendChild(body);

        box.appendChild(item);
    });
}

// GM용: 장소별 전체 단서와 공개/숨기기 버튼
function renderGmPanel() {
    if (!session.gm || !gmClues) return;

    const box = document.getElementById('clue-gm-list');
    box.replaceChildren();

    let lastLocation = null;
    gmClues.forEach((clue) => {
        if (clue.location !== lastLocation) {
        const heading = document.createElement('h3');
        heading.textContent = clue.location;
        box.appendChild(heading);
        lastLocation = clue.location;
        }

        const revealed = revealedKeys.has(clue.key);
        const item = document.createElement('div');
        item.className = 'clue-item' + (revealed ? ' revealed' : '');

        const head = document.createElement('div');
        head.className = 'clue-head';
        const title = document.createElement('strong');
        title.textContent = clue.title;
        head.appendChild(title);
        head.appendChild(revealed ? makeBadge('공개됨', 'on') : makeBadge('비공개'));
        item.appendChild(head);

        const body = document.createElement('p');
        body.className = 'clue-body';
        body.textContent = clue.content;
        item.appendChild(body);

        const actions = document.createElement('div');
        actions.className = 'clue-actions';
        actions.appendChild(makeActionButton(revealed ? '숨기기' : '공개', (btn) => {
        postReveal(clue.key, !revealed, btn);
        }));
        item.appendChild(actions);

        box.appendChild(item);
    });
}

// 처음 한 번, 그리고 재연결할 때마다 서버에서 현재 상태를 가져옴
async function loadClues() {
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/clues', {
        headers: { 'X-Player-Token': session.token }
        });
        if (res.ok) renderClues(await res.json());

        if (session.gm) {
        // GM에게만 전체 단서(공개 전 내용 포함)를 요청. 서버도 GM이 아니면 거부함
        const gmRes = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/gm/clues', {
            headers: { 'X-Player-Token': session.token }
        });
        if (gmRes.ok) {
            gmClues = await gmRes.json();
            renderGmPanel();
        }
        }
    } catch (err) {
        // 실패해도 다음 방송 때 갱신되므로 무시
    }
}

// 공개(revealed=true) 또는 숨기기(false). 같은 요청이 두 번 가도 결과가 같음
async function postReveal(key, revealed, button) {
    button.disabled = true;   // 연속 클릭 방지
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/clues/reveal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Player-Token': session.token },
        body: JSON.stringify({ key: key, revealed: revealed })
        });
        if (!res.ok) {
        let message = '단서를 바꾸지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
        // 성공하면 서버가 방송을 보내고, 그 방송으로 화면과 버튼 상태가 다시 그려짐
    } catch (err) {
        showError(err.message);
    } finally {
        button.disabled = false;
    }
}