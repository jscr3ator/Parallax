# Parallax

A pocket remote for OpenCode. Talk to your coding agents, queue follow-ups, and approve their next steps from Android.

[Download the Android APK](https://github.com/jscr3ator/Parallax/releases/latest) · Android 10+

<img src="docs/parallax.png" alt="Parallax in dark mode with two active coding sessions" width="300">

**Connect.** Choose OpenCode and scan the QR from `opencode pair --remote`, or sign in with your OpenChamber address and password.

IOS is on the roadmap!

**Web.** Use Parallax from any browser: phone, tablet or laptop. On any computer on your network with Node 18+:

```bash
git clone --depth 1 https://github.com/jscr3ator/Parallax && node Parallax/web/server.mjs --backend http://10.0.0.177:3080
```

Point `--backend` at your OpenChamber (port 3080) or opencode server (port 4096; the default is `http://localhost:4096`). Open the address it prints on any device and sign in with your OpenChamber or opencode password. `--port` changes the port (default 8787). It has no dependencies, so there's nothing to install. The web server stores nothing: chats, projects and folders stay on your server, and the browser keeps only its sign-in token.

**Build.** With JDK 21 and the Android SDK installed, run `./gradlew assembleDebug`.
