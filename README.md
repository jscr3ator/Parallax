# Parallax

A pocket remote for OpenCode. Talk to your coding agents, queue follow-ups, and approve their next steps from Android.

[Download the Android APK](https://github.com/jscr3ator/Parallax/releases/latest) · Android 10+

<img src="docs/parallax.png" alt="Parallax in dark mode with two active coding sessions" width="300">

**Connect.** Choose OpenCode and scan the QR from `opencode pair --remote`, or sign in with your OpenChamber address and password.

IOS is on the roadmap!

**Web.** Use Parallax from any browser: phone, tablet or laptop. On any computer on your network with Node 18+:

```bash
git clone --depth 1 https://github.com/jscr3ator/Parallax && node Parallax/web/server.mjs
```

Open the address it prints on any device. On the sign-in page, enter your OpenChamber or opencode server address (a Raspberry Pi at `10.0.0.177:3080`, a Tailscale name, a tunnel) with its password, or paste an OpenChamber connect link from Settings → Remote Instances → Add a device. Servers on your network and Tailscale work out of the box. Start it with `--allow-any` to reach public addresses, and with `--backend <url>` to change the server used when the address is left empty. `--port` changes the port (default 8787). It has no dependencies. The web server stores nothing: chats, projects and folders stay on your server, and the browser keeps only its sign-in token.

**Build.** With JDK 21 and the Android SDK installed, run `./gradlew assembleDebug`.
