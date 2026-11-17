const devCard = document.getElementById('dev-card');
const devResetBtn = document.getElementById('dev-reset');

// GM에게만 표시 (서버도 GM만 허용하므로 화면과 서버가 일치)
devCard.hidden = !session.gm;

devResetBtn.addEventListener('click', async () => {
    if (!confirm('게임을 강제로 중단하고 모든 참가자를 대기실로 돌려보낼까요?\n역할, 시계, 단서, 판정 기록이 모두 초기화됩니다.')) return;

    devResetBtn.disabled = true;   // 연속 클릭 방지
    errorBox.hidden = true;
    try {
        const res = await fetch('/api/rooms/' + encodeURIComponent(session.code) + '/dev/reset', {
        method: 'POST',
        headers: { 'X-Player-Token': session.token }
        });
        if (!res.ok) {
        let message = '게임을 중단하지 못했습니다.';
        try { message = (await res.json()).message || message; } catch (e) { /* 본문이 JSON이 아닌 경우 */ }
        throw new Error(message);
        }
        // 성공하면 서버가 방송을 보내고, 모든 화면이 대기실로 이동함
    } catch (err) {
        showError(err.message);
    } finally {
        devResetBtn.disabled = false;
    }
});