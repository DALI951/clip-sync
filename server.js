const express = require("express");
const http = require("http");
const { Server } = require("socket.io");
const path = require("path");
const os = require("os");

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: "*" },
  pingTimeout: 60000,
  pingInterval: 25000,
});

app.use(express.static(path.join(__dirname, "public")));

app.get("/api/ip", (req, res) => {
  res.json({ ip: getLocalIP() });
});

let clipboardHistory = [];
const MAX_HISTORY = 50;

function getLocalIP() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === "IPv4" && !iface.internal) {
        return iface.address;
      }
    }
  }
  return "localhost";
}

io.on("connection", (socket) => {
  console.log(`[Server] Device connected: ${socket.id}`);
  socket.emit("history", clipboardHistory);

  socket.on("clipboard-update", (data) => {
    if (!data.text || data.text.trim() === "") return;
    const exists = clipboardHistory.find(
      (item) => item.text === data.text && item.source === data.source
    );
    if (!exists) {
      const item = {
        id: Date.now() + Math.random(),
        text: data.text,
        source: data.source,
        timestamp: new Date().toLocaleTimeString(),
      };
      clipboardHistory.unshift(item);
      if (clipboardHistory.length > MAX_HISTORY) clipboardHistory.pop();
      socket.broadcast.emit("clipboard-update", item);
    }
  });

  socket.on("delete-item", (id) => {
    clipboardHistory = clipboardHistory.filter((item) => item.id !== id);
    io.emit("history", clipboardHistory);
  });

  socket.on("clear-all", () => {
    clipboardHistory = [];
    io.emit("history", clipboardHistory);
  });

  socket.on("disconnect", () => {
    console.log(`[Server] Device disconnected: ${socket.id}`);
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, "0.0.0.0", () => {
  const ip = getLocalIP();
  console.log("");
  console.log("  ╔═══════════════════════════════════════╗");
  console.log("  ║         ClipSync Server Running        ║");
  console.log("  ╠═══════════════════════════════════════╣");
  console.log(`  ║  PC:     http://localhost:${PORT}         ║`);
  console.log(`  ║  Phone:  http://${ip}:${PORT}  ║`);
  console.log("  ╚═══════════════════════════════════════╝");
  console.log("");
});
