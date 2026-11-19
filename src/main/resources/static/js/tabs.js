(function () {
    const bar = document.getElementById('tab-bar');
    const tabs = [...bar.querySelectorAll('.tab')];
    let activeId = null;       // 지금 열린 패널의 id
    let userPicked = false;    // 사용자가 직접 탭을 눌렀는지 (누르기 전에는 항상 첫 번째 탭을 기본)

    const paneOf = (tab) => document.getElementById(tab.dataset.tab);
    const isAvailable = (tab) => !paneOf(tab).hidden;   // 역할이나 단계 때문에 숨겨진 패널은 선택할 수 없음

    function refresh() {
        // 선택할 수 없는 패널의 탭 버튼은 감춤
        tabs.forEach((tab) => { tab.hidden = !isAvailable(tab); });

        // 열린 탭을 정함: 사용자가 고른 탭이 있으면 유지하고, 없거나 사라졌으면 첫 번째로 선택 가능한 탭
        const available = tabs.filter(isAvailable);
        const stillThere = available.some((t) => t.dataset.tab === activeId);
        if (!userPicked || !stillThere) {
        activeId = available.length > 0 ? available[0].dataset.tab : null;
        }

        // 화면에 반영
        tabs.forEach((tab) => {
        const on = tab.dataset.tab === activeId;
        tab.classList.toggle('is-active', on);
        tab.setAttribute('aria-selected', on ? 'true' : 'false');
        paneOf(tab).classList.toggle('is-active', on);
        });
    }

    tabs.forEach((tab) => {
        tab.addEventListener('click', () => {
        activeId = tab.dataset.tab;
        userPicked = true;
        refresh();
        });
    });

    // 패널의 hidden 속성이 바뀔 때마다 탭을 다시 정리 (이 감시는 hidden 속성만 보므로 refresh가 만드는 class 변경과 서로 부딪치지 않음)
    const observer = new MutationObserver(refresh);
    tabs.forEach((tab) => observer.observe(paneOf(tab), { attributes: true, attributeFilter: ['hidden'] }));

    refresh();
})();