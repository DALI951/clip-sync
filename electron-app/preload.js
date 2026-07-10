const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("clipSync", {
  getConfig: () => ipcRenderer.invoke("get-config"),
  getDevices: () => ipcRenderer.invoke("get-devices"),
  getPaired: () => ipcRenderer.invoke("get-paired"),
  setDeviceName: (name) => ipcRenderer.send("set-device-name", name),
  pairDevice: (device) => ipcRenderer.send("pair-device", device),
  unpair: () => ipcRenderer.send("unpair"),
  showDeviceNamePrompt: () => ipcRenderer.send("show-device-name-prompt"),

  onDeviceFound: (cb) => ipcRenderer.on("device-found", (_, d) => cb(d)),
  onPaired: (cb) => ipcRenderer.on("paired", (_, d) => cb(d)),
  onShowNamePrompt: (cb) => ipcRenderer.on("show-name-prompt", () => cb()),
});
