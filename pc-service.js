const { io } = require("socket.io-client");
const { execSync } = require("child_process");

const SERVER_URL = process.env.CLIPSYNC_SERVER || "http://localhost:3000";
let lastClipText = "";
let connected = false;

function readClipboard() {
  try {
    return execSync("powershell -command \"Get-Clipboard\"", {
      encoding: "utf-8",
      timeout: 2000,
    }).trim();
  } catch {
    return "";
  }
}

function writeClipboard(text) {
  try {
    const escaped = text.replace(/'/g, "''");
    execSync(`powershell -command "Set-Clipboard -Value '${escaped}'"`, {
      timeout: 2000,
    });
  } catch {}
}

const socket = io(SERVER_URL, {
  reconnection: true,
  reconnectionDelay: 2000,
  reconnectionAttempts: -1,
});

socket.on("connect", () => {
  connected = true;
  console.log("[PC] Connected to server");
});

socket.on("disconnect", () => {
  connected = false;
  console.log("[PC] Disconnected - reconnecting...");
});

socket.on("clipboard-update", (item) => {
  if (item.source !== "PC" && item.text && item.text.trim() !== "") {
    lastClipText = item.text;
    writeClipboard(item.text);
    console.log(`[PC] Synced from ${item.source}: ${item.text.substring(0, 80)}`);
  }
});

setInterval(() => {
  if (!connected) return;
  const current = readClipboard();
  if (current && current !== lastClipText && current.trim() !== "") {
    lastClipText = current;
    socket.emit("clipboard-update", { text: current, source: "PC" });
    console.log(`[PC] Sent: ${current.substring(0, 80)}`);
  }
}, 500);

console.log("[PC] ClipSync clipboard monitor started");
console.log(`[PC] Server: ${SERVER_URL}`);
