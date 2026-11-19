const MAX_WHISPER = 200;

let whisperTargets = [];        // GM 전용: 보낼 수 있는 대상 [{ nickname, label }]
let lastWhisperId = null;       // 가장 최근에 화면에 그린 귓속말의 번호 (새 글 알림용)
let whisperInitialized = false; // 처음 기록을 불러왔는지 (처음 불러올 때는 알리지 않음)
let toastTimerId = null;

const whisperTarget = document.getElementById('whisper-target');
const whisperText = document.getElementById('whisper-text');
const whisperSend = document.getElementById('whisper-send');
const whisperToast = document.getElementById('whisper-toast');

// 카드 표시 여부 (game.js의 renderPlayers가 호출). 게임 진행 중에만, GM과 플레이어가 서로 다른 카드를 봄
function renderWhisperCards(room) {
    const playing = room.status === 'PLAYING';
    document.getElementById('whisper-card').hidden = !(playing && !session.gm);
    document.getElementById('whisper-gm-card').hidden = !(playing && session.gm);
}

// GM 전용: 배정표를 받아 받는 사람 목록을 만듦 (game.js의 renderRole이 호출). 플레이어는 null
function setWhisperAssignments(assignments) {
    if (!assignments) {
        whisperTargets = [];
        return;
    }
    whisperTargets = assignments.map((a) => ({
        nickname: a.nickname,   // 서버에 보낼 때는 닉네임으로 받는 사람을 찾으므로 값은 그대로 유지
        label: a.characterName + ' · ' + a.job + (a.secretTitle === '밀항자' ? ' · 밀항자' : '')
    }));
    renderWhisperTargets();
}

function renderWhisperTargets() {
    const selected = whisperTarget.value;   // 다시 그려도 고른 사람이 유지되도록 기억
    whisperTarget.replaceChildren();
    whisperTargets.forEach((t) => {
        const option = document.createElement('option');
        option.value = t.nickname;
        option.textContent = t.label;   // 닉네임은 사용자 입력이므로 textContent로 넣어 HTML 삽입을 막음
        whisperTarget.appendChild(option);
    });
    if ([...whisperTarget.options].some((o) => o.value === selected)) {
        whisperTarget.value = selected;
    }
}

// 글자 수 표시
whisperText.addEventListener('input', () => {
    setText('whisper-count', whisperText.value.length + ' / ' + MAX_WHISPER);
});

// 플레이어용: 내가 받은 귓속말 (최신순). notify=true는 서버가 방금 보낸 푸시일 때만
function renderWhispers(list, notify) {
    const box = document.getElementById('whisper-list');
    box.replaceChildren();
    document.getElementById('whisper-empty').hidden = list.length > 0;

    const topId = list.length > 0 ? list[0].id : null;
    const isNew = notify && whisperInitialized && topId !== null && topId !== lastWhisperId;

    list.forEach((w, index) => {
        const item = document.createElement('div');
        item.className = 'whisper-item' + (index === 0 && isNew ? ' fresh' : '');

        const text = document.createElement('p');
        text.className = 'whisper-text';
        text.textContent = w.text;   // 글 내용은 textContent로 넣어 HTML 삽입을 막음
        item.appendChild(text);

        const time = document.createElement('p');
        time.className = 'hint';
        time.textContent = new Date(w.createdAtMillis).toLocaleTimeString('ko-KR');
        item.appendChild(time);

        box.appendChild(item);
    });

    lastWhisperId = topId;
    whisperInitialized = true;
    if (isNew) showWhisperToast();
}

// GM용: 내가 보낸 기록 (최신순)
function renderGmWhispers(list) {
    const box = document.getElementById('whisper-log');
    box.replaceChildren();
    document.getElementById('whisper-log-empty').hidden = list.length > 0;

    list.forEach((w) => {
        const item = document.createElement('div');
        item.className = 'whisper-item';

        const head = document.createElement('div');
        head.className = 'clue-head';
        head.appendChild(makeBadge('→'));
        const who = document.createElement('strong');
        who.textContent = (w.recipientCharacter ? w.recipientCharacter + ' ' : '') + '(' + w.recipientNickname + ')';
        head.appendChild(who);
        item.appendChild(head);

        const text = document.createElement('p');
        text.className = 'whisper-text';
        text.textContent = w.text;
        item.appendChild(text);

        const time = document.createElement('p');
        time.className = 'hint';
        time.textContent = new Date(w.createdAtMillis).toLocaleTimeString('ko-KR');
        item.appendChild(time);

        box.appendChild(item);
    });
}

// 알림 띠: 눌러서 귓속말 카드로 이동. 8초 뒤 자동으로 사라짐
function showWhisperToast() {
    whisperToast.hidden = false;
    if (toastTimerId !== null) clearTimeout(toastTimerId);
    toastTimerId = setTimeout(() => { whisperToast.hidden = true; }, 8000);
    }

    whisperToast.addEventListener('click', () => {
    whisperToast.hidden = true;
    document.getElementById('whisper-card').scrollIntoView({ behavior: 'smooth', block: 'start' });
});

// 처음 한 번, 그리고 재연결할 때마다 서버에서 현재 기록을 가져옴. 서버가 사람에 맞는 것만 돌려줌
async function loadWhispers() {
    try {
        const path = session.gm ? '/gm/whispers' : '/whispers';
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + path, {
        headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) return;
        const list = await res.json();
        if (session.gm) {
        renderGmWhispers(list);
        } else {
        renderWhispers(list, false);   // 조회로 불러올 때는 알림을 띄우지 않음
        }
    } catch (err) {
        // 실패해도 다음 푸시 때 갱신되므로 무시
    }
}

// 귓속말 보내기 (GM)
whisperSend.addEventListener('click', async () => {
    const nickname = whisperTarget.value;
    const text = whisperText.value;
    if (!nickname) {
        showError('받는 사람을 고르세요.');
        return;
    }
    if (!text.trim()) {
        showError('내용을 입력해 주세요.');
        return;
    }

    whisperSend.disabled = true;   // 연속 클릭 방지
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/whispers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Player-Token': session.token },
        body: JSON.stringify({ nickname: nickname, text: text })
        });
        if (!res.ok) {
        let message = '귓속말을 보내지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
        whisperText.value = '';
        setText('whisper-count', '0 / ' + MAX_WHISPER);
        // 보낸 기록은 서버가 GM의 개인 채널로 보내 주므로 여기서 직접 추가하지 않음
    } catch (err) {
        showError(err.message);
    } finally {
        whisperSend.disabled = false;
    }
});