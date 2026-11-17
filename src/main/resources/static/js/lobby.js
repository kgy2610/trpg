const SESSION_KEY = "trpg.session";
const errorBox = document.getElementById("error");

function showError(message) {
  errorBox.textContent = message;
  errorBox.hidden = false;
}

async function post(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  let data = null;
  try {
    data = await res.json();
  } catch (e) {
    /* 본문이 JSON이 아닌 경우 */
  }
  if (!res.ok) {
    throw new Error((data && data.message) || "요청에 실패했습니다.");
  }
  return data;
}

function saveAndGo(data) {
  // 토큰 포함 응답을 탭에 저장 (임시: sessionStorage, 설명은 아래 표 참고)
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(data));
  location.href = "/room.html";
}

document.getElementById("create-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  errorBox.hidden = true;
  try {
    const nickname = document.getElementById("create-nickname").value;
    saveAndGo(await post("/api/rooms", { nickname }));
  } catch (err) {
    showError(err.message);
  }
});

document.getElementById("join-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  errorBox.hidden = true;
  try {
    const code = document
      .getElementById("join-code")
      .value.trim()
      .toUpperCase();
    const nickname = document.getElementById("join-nickname").value;
    saveAndGo(
      await post("/api/rooms/" + encodeURIComponent(code) + "/join", {
        nickname,
      }),
    );
  } catch (err) {
    showError(err.message);
  }
});
