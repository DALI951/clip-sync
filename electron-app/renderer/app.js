const devicesContainer = document.getElementById("devices");
const noDevices = document.getElementById("noDevices");
const statusText = document.getElementById("statusText");
const pairedStatus = document.getElementById("pairedStatus");
const pairedSection = document.getElementById("pairedSection");
const pairedName = document.getElementById("pairedName");
const namePrompt = document.getElementById("namePrompt");
const mainContent = document.getElementById("mainContent");
const nameInput = document.getElementById("nameInput");
const nameSubmit = document.getElementById("nameSubmit");
const unpairBtn = document.getElementById("unpairBtn");

document.getElementById("closeBtn").onclick = () => window.close();
unpairBtn.onclick = () => window.clipSync.unpair();

// First time name setup
window.clipSync.getConfig().then((config) => {
  if (!config.deviceName || config.deviceName === "DESKTOP-877EFL9") {
    namePrompt.classList.remove("hidden");
    mainContent.classList.add("hidden");
    nameInput.value = "My PC";
    nameInput.focus();
    nameInput.select();
  }
});

nameSubmit.onclick = () => {
  const name = nameInput.value.trim() || "My PC";
  window.clipSync.setDeviceName(name);
  namePrompt.classList.add("hidden");
  mainContent.classList.remove("hidden");
  statusText.textContent = `Scanning as "${name}"...`;
};

nameInput.onkeydown = (e) => { if (e.key === "Enter") nameSubmit.click(); };

// Show name prompt if triggered from main process
window.clipSync.onShowNamePrompt(() => {
  namePrompt.classList.remove("hidden");
  mainContent.classList.add("hidden");
});

// Device discovery
window.clipSync.onDeviceFound((device) => {
  noDevices.classList.add("hidden");
  const row = document.createElement("div");
  row.className = "device-row";
  row.innerHTML = `
    <div>
      <div class="device-name">${escapeHtml(device.name)}</div>
      <div class="device-ip">${device.ip}</div>
    </div>
    <span class="device-connect">Connect</span>
  `;
  row.onclick = () => {
    window.clipSync.pairDevice(device);
    devicesContainer.querySelectorAll(".device-row").forEach(r => r.remove());
    noDevices.classList.remove("hidden");
    noDevices.textContent = `Paired with ${device.name}`;
  };
  devicesContainer.appendChild(row);
  statusText.textContent = `Found: ${device.name}`;
});

// Paired
window.clipSync.onPaired((device) => {
  if (device) {
    pairedSection.classList.remove("hidden");
    pairedName.textContent = device.name;
    pairedStatus.textContent = "Connected";
    statusText.textContent = "Ready";
  } else {
    pairedSection.classList.add("hidden");
    pairedStatus.textContent = "";
    statusText.textContent = "Scanning for phones...";
  }
});

// Load existing state
window.clipSync.getPaired().then((device) => {
  if (device) {
    pairedSection.classList.remove("hidden");
    pairedName.textContent = device.name;
    pairedStatus.textContent = "Connected";
    statusText.textContent = "Paired";
  }
});

window.clipSync.getDevices().then((devices) => {
  if (devices.length > 0) {
    noDevices.classList.add("hidden");
    devices.forEach((device) => {
      const row = document.createElement("div");
      row.className = "device-row";
      row.innerHTML = `
        <div>
          <div class="device-name">${escapeHtml(device.name)}</div>
          <div class="device-ip">${device.ip}</div>
        </div>
        <span class="device-connect">Connect</span>
      `;
      row.onclick = () => window.clipSync.pairDevice(device);
      devicesContainer.appendChild(row);
    });
    statusText.textContent = `Found ${devices.length} device(s)`;
  }
});

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}
