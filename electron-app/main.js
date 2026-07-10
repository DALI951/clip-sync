const { app, BrowserWindow, Tray, Menu, ipcMain, Notification } = require("electron");
const path = require("path");
const { Server } = require("socket.io");
const http = require("http");
const { execSync } = require("child_process");
const os = require("os");
const dgram = require("dgram");
const fs = require("fs");

const PORT = 3000;
let mainWindow = null;
let tray = null;
let io = null;
let lastClipText = "";
let discoveredDevices = [];
let pairedDevice = null;
let config = { deviceName: os.hostname() };

const configPath = path.join(app.getPath("userData"), "config.json");

function loadConfig() {
  try { config = JSON.parse(fs.readFileSync(configPath, "utf-8")); } catch {}
}

function saveConfig() {
  fs.writeFileSync(configPath, JSON.stringify(config, null, 2));
}

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
  try { return execSync('powershell -command "Get-Clipboard"', { encoding: "utf-8", timeout: 2000 }).trim(); } catch { return ""; }
}

function writeClipboard(text) {
  try {
    const escaped = text.replace(/'/g, "''");
    execSync(`powershell -command "Set-Clipboard -Value '${escaped}'"`, { timeout: 2000 });
  } catch {}
}

function sendToWindow(channel, data) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, data);
  }
}

// --- UDP Discovery (listen for phone responses) ---
function startDiscovery() {
  const udp = dgram.createSocket({ type: "udp4", reuseAddr: true, so_reuseport: true });

  udp.on("message", (msg, rinfo) => {
    const text = msg.toString().trim();
    if (text.startsWith("CLIPSYNC_PHONE:")) {
      const name = text.replace("CLIPSYNC_PHONE:", "").trim();
      const exists = discoveredDevices.find(d => d.ip === rinfo.address);
      if (!exists) {
        const device = { name, ip: rinfo.address, port: PORT, lastSeen: Date.now() };
        discoveredDevices.push(device);
        sendToWindow("device-found", device);
        if (Notification.isSupported()) {
          new Notification({ title: "ClipSync", body: `Found: ${name}` }).show();
        }
      }
    }
  });

  udp.bind(PORT + 1, () => {
    udp.setBroadcast(true);
    // Broadcast discovery every 5 seconds
    setInterval(() => {
      const msg = Buffer.from(`CLIPSYNC_PC:${config.deviceName}`);
      udp.send(msg, 0, msg.length, PORT + 1, "255.255.255.255");
    }, 5000);
  });

  return udp;
}

// --- Clipboard Server ---
function startServer() {
  const server = http.createServer();
  io = new Server(server, { cors: { origin: "*" }, pingTimeout: 60000, pingInterval: 25000 });

  io.on("connection", (socket) => {
    socket.emit("server-info", { name: config.deviceName });

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
  });

  server.listen(PORT, "0.0.0.0");
  return server;
}

// --- Clipboard Monitor ---
function startClipboardMonitor() {
  setInterval(() => {
    const current = readClipboard();
    if (current && current !== lastClipText && current.trim() !== "") {
      lastClipText = current;
      io?.emit("clipboard-update", { id: Date.now() + Math.random(), text: current, source: "PC", timestamp: Date.now() });
    }
  }, 500);
}

// --- Window ---
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 420,
    height: 560,
    resizable: false,
    frame: false,
    transparent: false,
    backgroundColor: "#0F172A",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.loadFile(path.join(__dirname, "renderer", "index.html"));
  mainWindow.on("close", (e) => {
    if (!app.isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });
}

// --- Tray ---
function createTray() {
  const { nativeImage } = require("electron");
  const iconData = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAMklEQVQ4T2NkYPj/n4EBBJgYKAQMowYMfAgwUhI7jIMiDBgpSTGDAYMGDBowKEIHAAD//wMJFk0RZQAAAABJRU5ErkJggg==";
  const trayIcon = nativeImage.createFromDataURL("data:image/png;base64," + iconData);

  tray = new Tray(trayIcon);
  tray.setToolTip("ClipSync");

  const contextMenu = Menu.buildFromTemplate([
    { label: `ClipSync (${config.deviceName})`, enabled: false },
    { type: "separator" },
    {
      label: "Show",
      click: () => { mainWindow?.show(); mainWindow?.focus(); },
    },
    {
      label: pairedDevice ? `Paired: ${pairedDevice.name}` : "Not paired",
      enabled: false,
    },
    { type: "separator" },
    {
      label: "Quit",
      click: () => { app.isQuitting = true; app.quit(); },
    },
  ]);

  tray.setContextMenu(contextMenu);
  tray.on("click", () => {
    mainWindow?.show();
    mainWindow?.focus();
  });
}

// --- IPC ---
ipcMain.handle("get-config", () => config);
ipcMain.handle("get-devices", () => discoveredDevices);
ipcMain.handle("get-paired", () => pairedDevice);

ipcMain.on("set-device-name", (_, name) => {
  config.deviceName = name;
  saveConfig();
  if (tray) tray.setToolTip(`ClipSync (${name})`);
});

ipcMain.on("pair-device", (_, device) => {
  pairedDevice = device;
  sendToWindow("paired", device);
  if (Notification.isSupported()) {
    new Notification({ title: "ClipSync", body: `Paired with ${device.name}` }).show();
  }
});

ipcMain.on("unpair", () => {
  pairedDevice = null;
  sendToWindow("paired", null);
});

ipcMain.on("show-device-name-prompt", () => {
  sendToWindow("show-name-prompt", true);
});

// --- App Lifecycle ---
app.whenReady().then(() => {
  loadConfig();
  startServer();
  startDiscovery();
  startClipboardMonitor();
  createWindow();
  createTray();
});

app.on("window-all-closed", () => {});

app.on("before-quit", () => {
  app.isQuitting = true;
});
