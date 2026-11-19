let endingStateLoaded = false;    // GM의 조건 체크 상태를 서버에서 한 번 불러왔는지
let endingResultLoaded = false;   // 종료 후 결과를 불러왔는지
let endingResultLoading = false;  // 결과를 불러오는 중인지 (중복 요청 방지)

const condEngine = document.getElementById('cond-engine');
const condDocs = document.getElementById('cond-docs');
const endingFinishBtn = document.getElementById('ending-finish');
const endingRestartBtn = document.getElementById('ending-restart');

// 카드 표시 여부 (game.js의 renderPlayers가 호출)
function renderEndingCards(room) {
    const playing = room.status === 'PLAYING';
    const ended = room.status === 'ENDED';

    // GM의 결말 진행 카드는 게임 진행 중에만, 결말 카드는 종료 후에만
    document.getElementById('ending-gm-card').hidden = !(playing && session.gm);
    document.getElementById('ending-card').hidden = !ended;
    endingRestartBtn.hidden = !(ended && session.gm);
    document.getElementById('opening-card').hidden = !!ended;
    document.body.classList.toggle('is-ended', !!ended);
    document.getElementById('events-card').hidden = !!ended;

    if (playing && session.gm && !endingStateLoaded) {
        loadEndingState();
    }
    if (ended && !endingResultLoaded) {
        loadEndingResult();
    }
}

// GM 전용: 서버에 저장된 조건과 결말 미리 보기를 불러와 화면에 반영 (서버 값이 기준)
async function loadEndingState() {
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/gm/ending', {
        headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) return;
        const state = await res.json();
        condEngine.checked = state.engineStopped;
        condDocs.checked = state.docsSecured;
        setText('ending-preview', state.endingTitle);
        endingStateLoaded = true;
    } catch (err) {
        // 실패해도 다시 화면을 열 때 불러오므로 무시
    }
}

// 체크박스를 바꿀 때마다 서버에 저장하고, 저장된 값으로 미리 보기를 갱신
async function saveConditions() {
    condEngine.disabled = true;
    condDocs.disabled = true;
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/ending/conditions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Player-Token': session.token },
        body: JSON.stringify({ engineStopped: condEngine.checked, docsSecured: condDocs.checked })
        });
        if (!res.ok) {
        let message = '조건을 저장하지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
    } catch (err) {
        showError(err.message);
    } finally {
        await loadEndingState();   // 성공이든 실패든 서버 값으로 화면을 맞춤
        condEngine.disabled = false;
        condDocs.disabled = false;
    }
}

condEngine.addEventListener('change', saveConditions);
condDocs.addEventListener('change', saveConditions);

// 결말 진행 (GM)
endingFinishBtn.addEventListener('click', async () => {
    const preview = document.getElementById('ending-preview').textContent;
    if (!confirm('결말을 진행할까요?\n지금 조건이면 「' + preview + '」입니다. 진행하면 되돌릴 수 없습니다.')) return;

    endingFinishBtn.disabled = true;   // 연속 클릭 방지
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/ending/finish', {
        method: 'POST',
        headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) {
        let message = '결말을 진행하지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
        // 성공하면 서버가 방 상태(ENDED)를 방송하고, 모든 화면이 결말 카드를 불러옴
    } catch (err) {
        showError(err.message);
    } finally {
        endingFinishBtn.disabled = false;
    }
});

// 종료 후 결과(결말, 후일담, 정체 공개)를 불러와 그림. 서버는 종료 상태가 아니면 거부함
async function loadEndingResult() {
    if (endingResultLoading) return;
    endingResultLoading = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/ending', {
        headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) return;
        renderEndingResult(await res.json());
        endingResultLoaded = true;
    } catch (err) {
        // 실패해도 다음 방송이나 새로고침 때 다시 시도됨
    } finally {
        endingResultLoading = false;
    }
}

function renderEndingResult(r) {
    setText('ending-title', r.title);
    setText('ending-epilogue', r.epilogue);

    // 밀항자 이름은 배정표에서 찾아 "성 + 닉네임" 전체 이름으로 표시
    const stowaway = r.assignments.find((a) => a.secretTitle === '밀항자');
    setText('ending-stowaway', stowaway ? '이번 판의 밀항자는 ' + stowaway.characterName + ' 님이었습니다.' : '');

    const body = document.getElementById('ending-assign');
    body.replaceChildren();
    r.assignments.forEach((a) => {
        const tr = document.createElement('tr');
        if (a.nickname === session.nickname) tr.classList.add('me');

        const nameTd = document.createElement('td');
        nameTd.textContent = a.characterName;
        tr.appendChild(nameTd);

        const roleTd = document.createElement('td');
        roleTd.appendChild(makeBadge(a.secretTitle, a.secretTitle === '밀항자' ? 'stowaway' : ''));
        tr.appendChild(roleTd);

        body.appendChild(tr);
    });
}

// 새 판 시작 (GM). 서버가 WAITING으로 방송하면 모든 화면이 대기실로 이동함
endingRestartBtn.addEventListener('click', async () => {
    if (!confirm('새 판을 시작할까요?\n모든 참가자가 대기실로 돌아가고, 이번 판의 기록이 초기화됩니다.')) return;

    endingRestartBtn.disabled = true;
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/ending/restart', {
        method: 'POST',
        headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) {
        let message = '새 판을 시작하지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
    } catch (err) {
        showError(err.message);
    } finally {
        endingRestartBtn.disabled = false;
    }
});