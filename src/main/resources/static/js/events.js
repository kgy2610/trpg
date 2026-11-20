const eventsList = document.getElementById('events-list');
const eventsForm = document.getElementById('events-form');
const eventsSpeaker = document.getElementById('events-speaker');
const eventsText = document.getElementById('events-text');
const eventsSend = document.getElementById('events-send');

let eventEntries = [];                 // 화면에 있는 기록 (오래된 것부터)
const deletedEventIds = new Set();     // 방송으로 먼저 도착한 삭제를 조회 결과가 되살리지 않도록 기억
let freshEventId = null;               // 방금 방송으로 온 기록 (깜빡임 표시용)
let eventSpeakersRequested = false;

// 입력 줄 표시 여부 (game.js의 renderPlayers가 호출). 게임 진행 중에만 쓸 수 있음
// 카드 자체의 표시/숨김은 ending.js가 담당
function renderEventsCard(room) {
    const playing = room.status === 'PLAYING';
    eventsForm.hidden = !playing;
    eventsSpeaker.hidden = !session.gm;   // NPC 선택은 GM만

    if (playing && session.gm && !eventSpeakersRequested) {
        eventSpeakersRequested = true;
        loadEventSpeakers();
    }
}

// GM 전용: 말할 NPC 목록을 서버에서 받아 드롭다운을 채움
async function loadEventSpeakers() {
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/gm/event-speakers', {
            headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) { eventSpeakersRequested = false; return; }
        const names = await res.json();
        eventsSpeaker.replaceChildren();
        names.forEach((name) => {
            const option = document.createElement('option');
            option.value = name;
            option.textContent = name;
            eventsSpeaker.appendChild(option);
        });
    } catch (err) {
        eventSpeakersRequested = false;   // 다음 화면 갱신 때 다시 시도
    }
}

// 기록 한 줄 만들기. 글 내용은 사용자 입력이므로 textContent로 넣어 HTML 삽입을 막음
function makeEventItem(e) {
    const item = document.createElement('div');
    item.className = 'ev-item ev-' + e.kind.toLowerCase() + (e.id === freshEventId ? ' fresh' : '');

    const body = document.createElement('div');
    body.className = 'ev-body';
    if (e.kind !== 'SYSTEM') {
        const name = document.createElement('span');
        name.className = 'ev-name';
        name.textContent = e.speaker;
        body.appendChild(name);
    }
    const text = document.createElement('span');
    text.textContent = e.text;
    body.appendChild(text);
    item.appendChild(body);

    // GM은 모든 기록을 지울 수 있음
    if (session.gm) {
        item.appendChild(makeActionButton('×', () => deleteEvent(e.id), 'ev-del'));
    }
    return item;
}

// 목록 다시 그리기. 맨 아래를 보고 있었거나 처음 그릴 때만 맨 아래로 내림 (위를 읽는 중이면 위치 유지)
function renderEvents(forceBottom) {
    const nearBottom = eventsList.scrollHeight - eventsList.scrollTop - eventsList.clientHeight < 40;
    eventsList.replaceChildren();

    if (eventEntries.length === 0) {
        const empty = document.createElement('p');
        empty.className = 'hint';
        empty.textContent = '아직 기록이 없습니다.';
        eventsList.appendChild(empty);
    }
    eventEntries.forEach((e) => eventsList.appendChild(makeEventItem(e)));
    freshEventId = null;

    if (forceBottom || nearBottom) {
        eventsList.scrollTop = eventsList.scrollHeight;
    }
}

// 처음 한 번, 그리고 재연결할 때마다 서버에서 현재 기록을 가져옴
async function loadEvents() {
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/events', {
            headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) return;
        const list = await res.json();
        // 조회 중에 방송으로 먼저 도착한 더 새로운 기록은 유지
        const maxId = list.length > 0 ? list[list.length - 1].id : 0;
        const newer = eventEntries.filter((e) => e.id > maxId);
        eventEntries = list.concat(newer).filter((e) => !deletedEventIds.has(e.id));
        renderEvents(true);
    } catch (err) {
        // 실패해도 다음 방송 때 갱신되므로 무시
    }
}

// 방송 처리: ADDED는 추가, DELETED는 제거
function onEventsChange(change) {
    if (change.type === 'ADDED') {
        if (eventEntries.some((e) => e.id === change.entry.id)) return;   // 조회 결과와 겹친 경우
        eventEntries.push(change.entry);
        freshEventId = change.entry.id;
    } else if (change.type === 'DELETED') {
        deletedEventIds.add(change.deletedId);
        eventEntries = eventEntries.filter((e) => e.id !== change.deletedId);
    }
    renderEvents(false);
}

// 보내기. 결과는 서버 방송으로 돌아오므로 여기서 직접 추가하지 않음
async function sendEvent() {
    const text = eventsText.value.trim();
    if (!text) return;

    eventsSend.disabled = true;   // 연속 클릭 방지
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/events', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Player-Token': session.token },
            body: JSON.stringify({ speaker: session.gm ? eventsSpeaker.value : null, text: text })
        });
        if (!res.ok) {
            let message = '보내지 못했습니다.';
            try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
            throw new Error(message);
        }
        eventsText.value = '';
    } catch (err) {
        showError(err.message);
    } finally {
        eventsSend.disabled = false;
        eventsText.focus();
    }
}

async function deleteEvent(id) {
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/events/' + id, {
            method: 'DELETE',
            headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) {
            let message = '지우지 못했습니다.';
            try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
            throw new Error(message);
        }
        // 화면에서는 서버 방송이 오면 사라짐
    } catch (err) {
        showError(err.message);
    }
}

// <form>이라서 Enter 키로도 보내짐
eventsForm.addEventListener('submit', (ev) => {
    ev.preventDefault();
    sendEvent();
});