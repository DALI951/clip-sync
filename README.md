# ClipSync

Clipboard sync between your PC and phone. Copy on one device, paste on the other.

## Quick Start

```bash
npm install
```

### Start the server
```bash
npm start
```

### Install PC clipboard service (runs in background, starts with Windows)
```bash
npm run install-service
```

### Uninstall PC service
```bash
npm run uninstall-service
```

## Phone Setup (PWA)

1. Make sure PC and phone are on the same WiFi
2. Open `http://YOUR_PC_IP:3000` on your phone browser (Chrome recommended)
3. Tap **"Add to Home Screen"** when prompted (or use browser menu)
4. The app now works like a native app — opens from your home screen

## How It Works

- **PC**: A Windows service monitors your clipboard in the background. Copy anything and it syncs to your phone instantly.
- **Phone**: The PWA receives synced clips. Paste or type text to send it back to your PC.
- Both devices stay connected via WebSocket for real-time sync.

## Requirements

- Node.js 18+ on PC
- Both devices on the same WiFi network
- Phone: any modern browser (Chrome, Edge, Samsung Internet)

## Project Structure

```
clip-sync/
├── server.js              # Sync server (Node.js + Socket.IO)
├── pc-service.js          # Windows clipboard monitor service
├── install-service.js     # Installs PC as Windows service
├── uninstall-service.js   # Uninstalls PC service
├── public/
│   ├── index.html         # PWA for phone
│   ├── app.js             # Client logic
│   ├── style.css          # Styles
│   ├── sw.js              # Service Worker (background)
│   ├── manifest.json      # PWA manifest
│   └── icon-192.svg       # App icon
└── clip-sync-android/     # Android native app (alternative)
```

## License

MIT
