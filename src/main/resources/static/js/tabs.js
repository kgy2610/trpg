(function () {
    const bar = document.getElementById("tab-bar");
    const tabs = [...bar.querySelectorAll(".tab")];
    let activeId = null; // 지금 열린 패널의 id
    let userPicked = false; // 사용자가 직접 탭을 눌렀는지 (누르기 전에는 항상 첫 번째 탭을 기본으로 보여 줌)

    const paneOf = (tab) => document.getElementById(tab.dataset.tab);

    const isAvailable = (tab) => {
        if (paneOf(tab).hidden) return false;
        if (
        document.body.classList.contains("is-ended") &&
        tab.hasAttribute("data-hide-on-end")
        )
        return false;
        return true;
    };

    function refresh() {
        tabs.forEach((tab) => {
        tab.hidden = !isAvailable(tab);
        });

        const available = tabs.filter(isAvailable);
        const stillThere = available.some((t) => t.dataset.tab === activeId);
        if (!userPicked || !stillThere) {
        activeId = available.length > 0 ? available[0].dataset.tab : null;
        }

        tabs.forEach((tab) => {
        const on = tab.dataset.tab === activeId;
        tab.classList.toggle("is-active", on);
        tab.setAttribute("aria-selected", on ? "true" : "false");
        paneOf(tab).classList.toggle("is-active", on);
        });
    }

    tabs.forEach((tab) => {
        tab.addEventListener("click", () => {
        activeId = tab.dataset.tab;
        userPicked = true;
        refresh();
        });
    });

    const observer = new MutationObserver(refresh);
    tabs.forEach((tab) =>
        observer.observe(paneOf(tab), {
        attributes: true,
        attributeFilter: ["hidden"],
        }),
    );
    observer.observe(document.body, {
        attributes: true,
        attributeFilter: ["class"],
    });

    refresh();
})();
