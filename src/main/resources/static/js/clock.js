const CLOCK_MAX = 6;

let lastRoom = null;           // 마지막으로 받은 방 정보 (실패 시 화면을 되돌리는 데 사용)
let currentClock = 0;          // 마지막으로 받은 시계 값 (버튼이 이 값을 기준으로 목표 칸을 계산)
let clockEvents = null;        // GM 전용 칸별 사건 메모 (한 번만 가져옴)
let clockEventsLoading = false;

// 시계 카드 전체를 방 정보에 맞춰 다시 그림 (game.js의 renderPlayers가 호출)
function renderClock(room) {
    lastRoom = room;

    // 게임 진행 중일 때만 표시
    const show = room.status === 'PLAYING';
    document.getElementById('clock-card').hidden = !show;
    document.getElementById('clock-card').classList.toggle('with-events', session.gm);   // [추가] GM은 칸별 사건 목록이 있어서 카드를 크게 잡음
    if (!show) return;

    currentClock = room.clockValue;

    const cells = document.getElementById('clock-cells');
    cells.replaceChildren();
    for (let i = 1; i <= CLOCK_MAX; i++) {
        const cell = document.createElement('span');
        cell.className = 'clock-cell' + (i <= room.clockValue ? ' filled' : '');
        cells.appendChild(cell);
    }

    document.getElementById('clock-note').textContent = room.clockValue >= CLOCK_MAX
        ? '시계가 가득 찼습니다. 열차가 출발합니다.'
        : room.clockValue + ' / ' + CLOCK_MAX + '칸';

    // GM에게만 조작 버튼 표시 (서버도 GM만 허용하므로 화면과 서버가 일치)
    document.getElementById('clock-gm').hidden = !session.gm;
    document.getElementById('clock-minus').disabled = room.clockValue <= 0;
    document.getElementById('clock-plus').disabled = room.clockValue >= CLOCK_MAX;

    renderClockEvents(room.clockValue);
}

// GM 전용 칸별 사건 메모 (현재 칸은 강조, 지난 칸은 흐리게)
function renderClockEvents(value) {
    const title = document.getElementById('clock-events-title');
    const list = document.getElementById('clock-events');

    if (!session.gm) {
        title.hidden = true;
        list.hidden = true;
        return;
    }
    if (clockEvents === null) {
        loadClockEvents();   // 처음 한 번만 서버에서 가져옴 (도착하면 다시 그려짐)
        title.hidden = true;
        list.hidden = true;
        return;
    }

    title.hidden = false;
    list.hidden = false;
    list.replaceChildren();
    clockEvents.forEach((text, index) => {
        const li = document.createElement('li');
        li.textContent = text;
        if (index + 1 === value) li.className = 'current';
        else if (index + 1 < value) li.className = 'done';
        list.appendChild(li);
    });
}

async function loadClockEvents() {
    if (clockEventsLoading) return;
    clockEventsLoading = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/gm/clock-events', {
        headers: { 'X-Player-Token': session.token }
        });
        if (res.ok) {
        clockEvents = await res.json();
        if (lastRoom) renderClock(lastRoom);
        }
    } catch (err) {
        // 실패해도 다음 방송 때 다시 시도됨
    } finally {
        clockEventsLoading = false;
    }
}

// 시계를 "n칸으로 설정" 요청. 같은 값을 여러 번 보내도 결과가 같음
async function postClock(value, button) {
    button.disabled = true;   // 연속 클릭 방지
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/clock', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Player-Token': session.token },
        body: JSON.stringify({ value: value })
        });
        if (!res.ok) {
        let message = '시계를 바꾸지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
        // 성공하면 서버가 방송을 보내고, 그 방송으로 화면과 버튼 상태가 다시 정해짐
    } catch (err) {
        showError(err.message);
        if (lastRoom) renderClock(lastRoom);   // 실패했으니 버튼 상태를 원래대로
    }
}

document.getElementById('clock-minus').addEventListener('click', (e) => {
    postClock(currentClock - 1, e.currentTarget);
});
document.getElementById('clock-plus').addEventListener('click', (e) => {
    postClock(currentClock + 1, e.currentTarget);
});