const ROLL_COOLDOWN_MS = 3000;   // 서버의 쿨다운(3초)과 맞춤. 더블클릭으로 두 번 굴러지는 것을 화면에서도 막음
const STAT_ORDER = ['STRENGTH', 'MIND', 'CHARM'];
const STAT_LABEL = { STRENGTH: '힘', MIND: '머리', CHARM: '매력' };

let diceCharacter = null;     // 내 캐릭터 (GM은 null). 능력치 값을 버튼에 표시하는 데 사용
let diceStat = 'STRENGTH';    // 지금 고른 능력치
let lastDiceId = null;        // 가장 최근에 화면에 그린 판정의 번호 (새 결과 강조용)
let diceInitialized = false;  // 처음 기록을 불러왔는지 (처음 불러올 때는 강조하지 않음)

const rollBtn = document.getElementById('dice-roll');
const reasonInput = document.getElementById('dice-reason');

// game.js의 renderRole이 호출: 내 캐릭터를 받아 능력치 버튼을 다시 그림
function setDiceCharacter(character) {
    const firstTime = diceCharacter === null && character !== null;
    diceCharacter = character;
    if (firstTime) {
        // 처음에는 가장 높은 능력치를 기본으로 선택
        diceStat = STAT_ORDER.reduce((best, key) => (statValue(key) > statValue(best) ? key : best), STAT_ORDER[0]);
    }
    renderStatButtons();
}

function statValue(key) {
    if (!diceCharacter) return 0;
    return { STRENGTH: diceCharacter.strength, MIND: diceCharacter.mind, CHARM: diceCharacter.charm }[key];
    }

    function renderStatButtons() {
    const box = document.getElementById('dice-stats');
    box.replaceChildren();
    if (!diceCharacter) return;

    STAT_ORDER.forEach((key) => {
        const value = statValue(key);
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'secondary' + (key === diceStat ? ' selected' : '');
        button.textContent = STAT_LABEL[key] + ' ' + (value >= 0 ? '+' : '') + value;
        button.addEventListener('click', () => {
        diceStat = key;
        renderStatButtons();
        });
        box.appendChild(button);
    });
}

// 카드 표시 여부 (game.js의 renderPlayers가 호출). 게임 진행 중에만 표시
function renderDiceCard(room) {
    document.getElementById('dice-card').hidden = room.status !== 'PLAYING';
    // 굴리기 컨트롤은 플레이어에게만, 안내 문구는 GM에게만 (서버도 GM은 굴릴 수 없게 막음)
    document.getElementById('dice-controls').hidden = session.gm;
    document.getElementById('dice-gm-hint').hidden = !session.gm;
}

// 판정 기록을 받아 화면에 그림 (방송 수신과 새로고침 조회가 같이 사용). list는 최신순
function renderDice(list) {
    const log = document.getElementById('dice-log');
    log.replaceChildren();
    document.getElementById('dice-empty').hidden = list.length > 0;

    const topId = list.length > 0 ? list[0].id : null;
    const isNew = diceInitialized && topId !== null && topId !== lastDiceId;

    list.forEach((r, index) => {
        const row = document.createElement('div');
        row.className = 'dice-row' + (index === 0 && isNew ? ' fresh' : '');

        const head = document.createElement('div');
        head.className = 'dice-head';
        const who = document.createElement('strong');
        // 닉네임과 행동 설명은 사용자 입력이므로 textContent로 넣어 HTML 삽입을 막음
        who.textContent = r.nickname + (r.characterName ? ' (' + r.characterName + ')' : '');
        head.appendChild(who);
        head.appendChild(makeBadge(r.statLabel));
        head.appendChild(makeBadge(r.outcomeLabel, 'outcome-' + r.outcome.toLowerCase()));
        row.appendChild(head);

        const math = document.createElement('p');
        math.className = 'dice-math';
        const sign = r.modifier >= 0 ? '+' : '-';
        math.textContent = r.die1 + ' + ' + r.die2 + ' ' + sign + ' ' + Math.abs(r.modifier) + ' = ' + r.total;
        row.appendChild(math);

        if (r.reason) {
        const reason = document.createElement('p');
        reason.className = 'dice-reason';
        reason.textContent = r.reason;
        row.appendChild(reason);
        }

        const time = document.createElement('p');
        time.className = 'hint';
        time.textContent = new Date(r.createdAtMillis).toLocaleTimeString('ko-KR');
        row.appendChild(time);

        log.appendChild(row);
    });

    lastDiceId = topId;
    diceInitialized = true;
}

// 처음 한 번, 그리고 재연결할 때마다 서버에서 최근 기록을 가져옴
async function loadDice() {
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/dice', {
        headers: { 'X-Player-Token': session.token }
        });
        if (res.ok) renderDice(await res.json());
    } catch (err) {
        // 실패해도 다음 방송 때 갱신되므로 무시
    }
}

// 주사위 굴리기. 능력치 값은 보내지 않음 (서버가 내 캐릭터의 값을 찾아서 더함)
rollBtn.addEventListener('click', async () => {
    if (!diceCharacter) return;

    rollBtn.disabled = true;   // 연속 클릭 방지
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/dice/roll', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Player-Token': session.token },
        body: JSON.stringify({ stat: diceStat, reason: reasonInput.value })
        });
        if (!res.ok) {
        let message = '주사위를 굴리지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
        reasonInput.value = '';
        // 결과는 서버 방송으로 모두에게 동시에 도착함. 쿨다운이 지나면 다시 누를 수 있음
        setTimeout(() => { rollBtn.disabled = false; }, ROLL_COOLDOWN_MS);
    } catch (err) {
        showError(err.message);
        rollBtn.disabled = false;
    }
});