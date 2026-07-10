const { Service } = require("node-windows");
const path = require("path");

const svc = new Service({
  name: "ClipSync",
  description: "Clipboard sync between PC and phone",
  script: path.join(__dirname, "server.js"),
  nodeOptions: [],
});

svc.on("install", () => {
  console.log("ClipSync service installed!");
  svc.start();
  console.log("ClipSync service started!");
});

svc.on("alreadyinstalled", () => {
  console.log("ClipSync service already installed. Starting...");
  svc.start();
});

svc.on("start", () => {
  console.log("ClipSync service is running.");
});

svc.on("error", (err) => {
  console.error("Error:", err);
});

svc.install();
