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
import dns from "node:dns/promises";
import net from "node:net";
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
  --host <addr>    address to listen on             (default 0.0.0.0, use 127.0.0.1 for this machine only)
  --allow-any      let the sign-in page connect to public addresses too (default: your network,
                   Tailscale and the --backend server only)`);
  process.exit(0);
}

let backendRaw = flag("backend", process.env.PARALLAX_BACKEND || "http://127.0.0.1:4096");
if (!/^https?:\/\//i.test(backendRaw)) backendRaw = `http://${backendRaw}`;
const backend = new URL(backendRaw);
const allowAny = args.includes("--allow-any") || process.env.PARALLAX_ALLOW_ANY === "1";
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

// ---- which servers the sign-in page may reach ----------------------------------------------
// The browser picks a server with the X-Parallax-Backend header (its origin). The default
// --backend is always allowed; others must resolve to a private, LAN or Tailscale address
// unless --allow-any is set. The connection is pinned to the checked IP (no DNS rebinding).

function isPrivateIp(ip) {
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split(".").map(Number);
    return a === 10 || a === 127 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) ||
      (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254);
  }
  const v = ip.toLowerCase();
  if (v.startsWith("::ffff:")) return isPrivateIp(v.slice(7));
  return v === "::1" || v.startsWith("fc") || v.startsWith("fd") || v.startsWith("fe80:");
}

class TargetError extends Error { constructor(msg, code = 403) { super(msg); this.code = code; } }

async function resolveTarget(raw) {
  if (!raw) return { url: backend, address: null };
  let url;
  try { url = new URL(raw); } catch { throw new TargetError("That server address isn't valid.", 400); }
  if (!/^https?:$/.test(url.protocol) || url.username || url.password) throw new TargetError("Use an http:// or https:// server address.", 400);
  url = new URL(url.origin);
  if (url.origin === backend.origin) return { url, address: null };
  const host = url.hostname.replace(/^\[|\]$/g, "");
  let addresses;
  try {
    addresses = net.isIP(host) ? [host] : (await dns.lookup(host, { all: true })).map((a) => a.address);
  } catch {
    throw new TargetError(`Can't find ${url.host}. Check the address.`, 502);
  }
  if (!allowAny && !addresses.every(isPrivateIp)) {
    throw new TargetError(`${url.host} is a public address. Restart Parallax web with --allow-any to connect to it.`);
  }
  return { url, address: addresses[0] };
}

async function proxy(req, res) {
  let target;
  try {
    target = await resolveTarget(req.headers["x-parallax-backend"]);
  } catch (e) {
    res.writeHead(e.code || 400, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ error: e.message }));
  }
  const backend = target.url;
  const lib = backend.protocol === "https:" ? https : http;
  const headers = { ...req.headers };
  delete headers.host;
  delete headers.cookie; // credentials travel only in Authorization
  delete headers.connection;
  delete headers["x-parallax-backend"];
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
      ...(target.address ? { lookup: (_h, opts, cb) => opts?.all ? cb(null, [{ address: target.address, family: net.isIP(target.address) }]) : cb(null, target.address, net.isIP(target.address)) } : {}),
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
    return res.end(JSON.stringify({ backend: backend.origin, allowAny }));
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
  console.log(`\n  Sign in with your opencode or OpenChamber password, or enter another server's address`);
  console.log(`  (or an OpenChamber connect link) on the sign-in page.${allowAny ? "" : " Public addresses need --allow-any."} Ctrl+C to stop.`);
  if (host === "0.0.0.0" && fs.existsSync("/usr/sbin/ufw")) {
    console.log(`  Blocked on other devices? Allow the port once: sudo ufw allow from ${(lan[0] || "10.0.0.1").replace(/\.\d+$/, ".0")}/24 to any port ${port} proto tcp`);
  }
  console.log("");
});
