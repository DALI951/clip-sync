const http = require("http");
const { Server } = require("socket.io");
const { execSync } = require("child_process");
const os = require("os");
const dgram = require("dgram");

const PORT = process.env.PORT || 3000;
const hostname = os.hostname();
let lastClipText = "";
let io;

function getLocalIP() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === "IPv4" && !iface.internal) return iface.address;
    }
  }
  return "127.0.0.1";
}

function readClipboard() {
  try {
    return execSync('powershell -command "Get-Clipboard"', { encoding: "utf-8", timeout: 2000 }).trim();
  } catch { return ""; }
}

function writeClipboard(text) {
  try {
    const escaped = text.replace(/'/g, "''");
    execSync(`powershell -command "Set-Clipboard -Value '${escaped}'"`, { timeout: 2000 });
  } catch {}
}

// --- UDP Discovery ---
function startUDPDiscovery() {
  const udp = dgram.createSocket({ type: "udp4", reuseAddr: true });
  udp.on("message", (msg, rinfo) => {
    const message = msg.toString().trim();
    if (message === "CLIPSYNC_DISCOVER") {
      const response = Buffer.from(`CLIPSYNC:${hostname}|${getLocalIP()}|${PORT}`);
      udp.send(response, 0, response.length, rinfo.port, rinfo.address);
      console.log(`[ClipSync] Discovery reply sent to ${rinfo.address}`);
    }
  });
  udp.bind(3000, () => {
    udp.setBroadcast(true);
    console.log(`[ClipSync] UDP discovery listening on port 3000`);
  });
  return udp;
}

// --- mDNS ---
function startMDNS() {
  try {
    const mdns = require("multicast-dns")();
    mdns.on("query", (query) => {
      query.questions.forEach((q) => {
        if (q.type === "PTR" && q.name === "_clipsync._tcp.local") {
          mdns.respond({
            answers: [{ name: "_clipsync._tcp.local", type: "PTR", class: "IN", ttl: 120, data: `${hostname}._clipsync._tcp.local` }],
            additionals: [
              { name: `${hostname}._clipsync._tcp.local`, type: "SRV", class: "IN", ttl: 120, data: { priority: 0, weight: 0, port: PORT, target: hostname + ".local" } },
              { name: `${hostname}._clipsync._tcp.local`, type: "TXT", class: "IN", ttl: 120, data: [Buffer.from("name=" + hostname)] },
            ],
          });
        }
      });
    });
    console.log(`[ClipSync] mDNS: advertising as "${hostname}"`);
  } catch (e) {
    console.log("[ClipSync] mDNS not available");
  }
}

// --- Socket.IO Server ---
const server = http.createServer();
io = new Server(server, { cors: { origin: "*" }, pingTimeout: 60000, pingInterval: 25000 });

io.on("connection", (socket) => {
  console.log(`[ClipSync] Phone connected: ${socket.id}`);
  socket.emit("server-info", { name: hostname });

  socket.on("clipboard-update", (data) => {
    if (!data.text || data.text.trim() === "") return;
    if (data.source === "Phone" && data.text !== lastClipText) {
      lastClipText = data.text;
      writeClipboard(data.text);
    }
    socket.broadcast.emit("clipboard-update", {
      id: Date.now() + Math.random(), text: data.text, source: data.source, timestamp: Date.now(),
    });
  });

  socket.on("disconnect", () => console.log(`[ClipSync] Phone disconnected: ${socket.id}`));
});

// --- Clipboard Monitor ---
function startClipboardMonitor() {
  setInterval(() => {
    const current = readClipboard();
    if (current && current !== lastClipText && current.trim() !== "") {
      lastClipText = current;
      io.emit("clipboard-update", { id: Date.now() + Math.random(), text: current, source: "PC", timestamp: Date.now() });
    }
  }, 500);
}

const udp = startUDPDiscovery();
server.listen(PORT, "0.0.0.0", () => {
  console.log(`[ClipSync] PC "${hostname}" ready on ${getLocalIP()}:${PORT}`);
  startMDNS();
  startClipboardMonitor();
});
