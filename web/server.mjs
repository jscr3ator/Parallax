#!/usr/bin/env node
// Parallax web: a zero-dependency server that serves the browser client and proxies the
// opencode / OpenChamber API, so any phone, tablet or laptop on your network can use it.
//
//   node web/server.mjs --backend http://10.0.0.177:3080      (OpenChamber on a Pi)
//   node web/server.mjs                                         (opencode on this machine, :4096)
//   npx github:jscr3ator/Parallax --backend http://...          (no clone needed)
//
// The server holds no credentials. The browser signs in to the backend through the proxy and
// keeps its token; the proxy can only ever reach the one backend it was started with.

import http from "node:http";
import https from "node:https";
import os from "node:os";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const args = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  if (i >= 0 && args[i + 1]) return args[i + 1];
  const eq = args.find((a) => a.startsWith(`--${name}=`));
  return eq ? eq.slice(name.length + 3) : fallback;
};

if (args.includes("--help") || args.includes("-h")) {
  console.log(`Parallax web client

  --backend <url>  opencode or OpenChamber server   (env PARALLAX_BACKEND, default http://127.0.0.1:4096)
  --port <n>       port to serve on                 (env PARALLAX_WEB_PORT, default 8787)
  --host <addr>    address to listen on             (default 0.0.0.0, use 127.0.0.1 for this machine only)`);
  process.exit(0);
}

let backendRaw = flag("backend", process.env.PARALLAX_BACKEND || "http://127.0.0.1:4096");
if (!/^https?:\/\//i.test(backendRaw)) backendRaw = `http://${backendRaw}`;
const backend = new URL(backendRaw);
const port = Number(flag("port", process.env.PARALLAX_WEB_PORT || 8787));
const host = flag("host", "0.0.0.0");
const root = path.join(path.dirname(fileURLToPath(import.meta.url)), "public");

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".webmanifest": "application/manifest+json",
};

const SECURITY_HEADERS = {
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "X-Frame-Options": "DENY",
  "Content-Security-Policy":
    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'",
};

// Only these backend routes are reachable through the proxy.
const PROXIED = /^\/(api|auth)(\/|$)/;

function serveStatic(req, res) {
  let rel = decodeURIComponent(new URL(req.url, "http://x").pathname);
  if (rel === "/" || !path.extname(rel)) rel = "/index.html"; // SPA fallback
  const file = path.normalize(path.join(root, rel));
  if (!file.startsWith(root + path.sep)) return send(res, 403, "Forbidden");
  fs.readFile(file, (err, body) => {
    if (err) return send(res, 404, "Not found");
    res.writeHead(200, {
      ...SECURITY_HEADERS,
      "Content-Type": TYPES[path.extname(file)] || "application/octet-stream",
      "Cache-Control": "no-cache", // tiny files; always pick up updates
    });
    res.end(body);
  });
}

function send(res, code, text) {
  res.writeHead(code, { ...SECURITY_HEADERS, "Content-Type": "text/plain; charset=utf-8" });
  res.end(text);
}

function proxy(req, res) {
  const lib = backend.protocol === "https:" ? https : http;
  const headers = { ...req.headers };
  delete headers.host;
  delete headers.cookie; // credentials travel only in Authorization
  delete headers.connection;
  // Backends check Origin on writes; present ourselves as same-origin to the backend.
  if (headers.origin) headers.origin = backend.origin;
  if (headers.referer) headers.referer = backend.origin + "/";
  const upstream = lib.request(
    {
      protocol: backend.protocol,
      hostname: backend.hostname,
      port: backend.port || (backend.protocol === "https:" ? 443 : 80),
      method: req.method,
      path: req.url,
      headers: { ...headers, host: backend.host },
    },
    (up) => {
      const out = { ...up.headers };
      delete out["www-authenticate"]; // stop the browser's native login popup
      delete out["set-cookie"];
      delete out["content-security-policy"];
      if (String(out["content-type"] || "").includes("text/event-stream")) {
        out["cache-control"] = "no-cache";
        out["x-accel-buffering"] = "no";
      }
      res.writeHead(up.statusCode || 502, out);
      up.pipe(res);
    },
  );
  upstream.setTimeout(0);
  upstream.on("error", (e) => {
    if (res.headersSent) return res.destroy();
    res.writeHead(502, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: `Can't reach ${backend.host}: ${e.code || e.message}` }));
  });
  // Client went away (tab closed, SSE dropped): stop the upstream request too.
  res.on("close", () => { if (!res.writableFinished) upstream.destroy(); });
  req.pipe(upstream);
}

const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, "http://x").pathname;
  if (pathname === "/_px/info") {
    res.writeHead(200, { ...SECURITY_HEADERS, "Content-Type": "application/json" });
    return res.end(JSON.stringify({ backend: backend.host }));
  }
  if (PROXIED.test(pathname)) return proxy(req, res);
  if (req.method !== "GET" && req.method !== "HEAD") return send(res, 405, "Method not allowed");
  serveStatic(req, res);
});
server.requestTimeout = 0;
server.headersTimeout = 30_000;

server.on("error", (e) => {
  console.error(e.code === "EADDRINUSE" ? `Port ${port} is busy. Try --port ${port + 1}` : e.message);
  process.exit(1);
});

server.listen(port, host, () => {
  const lan = Object.values(os.networkInterfaces()).flat()
    .filter((i) => i && i.family === "IPv4" && !i.internal).map((i) => i.address);
  console.log(`\n  Parallax web  →  backend ${backend.origin}\n`);
  console.log(`  On this computer:  http://localhost:${port}`);
  if (host === "0.0.0.0") for (const ip of lan) console.log(`  On your network:   http://${ip}:${port}`);
  console.log(`\n  Sign in with your opencode or OpenChamber password. Ctrl+C to stop.`);
  if (host === "0.0.0.0" && fs.existsSync("/usr/sbin/ufw")) {
    console.log(`  Blocked on other devices? Allow the port once: sudo ufw allow from ${(lan[0] || "10.0.0.1").replace(/\.\d+$/, ".0")}/24 to any port ${port} proto tcp`);
  }
  console.log("");
});
