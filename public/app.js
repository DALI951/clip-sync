const socket = io();

const list = document.getElementById("list");
const statusEl = document.getElementById("status");
const sendInput = document.getElementById("sendInput");
const sendBtn = document.getElementById("sendBtn");
const clearBtn = document.getElementById("clearBtn");
const toast = document.getElementById("toast");

let items = [];

socket.on("connect", () => {
  statusEl.textContent = "Connected";
  statusEl.className = "status connected";
});

socket.on("disconnect", () => {
  statusEl.textContent = "Offline";
  statusEl.className = "status disconnected";
});

socket.on("history", (history) => {
  items = history;
  renderList();
});

socket.on("clipboard-update", (item) => {
  items.unshift(item);
  if (items.length > 50) items.pop();
  renderList();
});

function renderList() {
  if (items.length === 0) {
    list.innerHTML = `
      <div class="empty-state">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.3">
          <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
          <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
        </svg>
        <p>No clipboard items yet</p>
        <span>Copy something on your PC or<br>send text from here</span>
      </div>`;
    return;
  }

  list.innerHTML = items
    .map(
      (item) => `
    <div class="clip-item" data-id="${item.id}">
      <div class="clip-meta">
        <span class="clip-source ${item.source === "Phone" ? "phone" : ""}">${item.source}</span>
        <span class="clip-time">${item.timestamp}</span>
      </div>
      <div class="clip-text">${escapeHtml(item.text)}</div>
      <div class="clip-actions">
        <button class="copy-btn" onclick="copyItem(event, ${item.id})">Copy</button>
        <button class="delete-btn" onclick="deleteItem(event, ${item.id})">Delete</button>
      </div>
    </div>`
    )
    .join("");
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function copyItem(e, id) {
  e.stopPropagation();
  const item = items.find((i) => i.id === id);
  if (!item) return;

  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(item.text).then(() => showToast("Copied!"));
  } else {
    const ta = document.createElement("textarea");
    ta.value = item.text;
    ta.style.position = "fixed";
    ta.style.left = "-9999px";
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    document.body.removeChild(ta);
    showToast("Copied!");
  }
}

function deleteItem(e, id) {
  e.stopPropagation();
  socket.emit("delete-item", id);
}

function showToast(msg) {
  toast.textContent = msg || "Copied!";
  toast.classList.add("show");
  setTimeout(() => toast.classList.remove("show"), 1500);
}

sendBtn.addEventListener("click", () => {
  const text = sendInput.value.trim();
  if (!text) return;
  socket.emit("clipboard-update", { text, source: "Phone" });
  sendInput.value = "";
  sendInput.blur();
});

sendInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendBtn.click();
  }
});

clearBtn.addEventListener("click", () => {
  if (items.length === 0) return;
  socket.emit("clear-all");
});
