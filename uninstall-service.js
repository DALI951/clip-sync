const { Service } = require("node-windows");
const path = require("path");

const svc = new Service({
  name: "ClipSync",
  script: path.join(__dirname, "server.js"),
});

svc.on("uninstall", () => {
  console.log("ClipSync service uninstalled.");
});

svc.on("error", (err) => {
  console.error("Error:", err);
});

svc.uninstall();
