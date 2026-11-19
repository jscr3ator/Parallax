// Parallax web client — the same server-side data as the Android app, in any browser.
// Plain ES modules, no build step. Talks to the backend through web/server.mjs (/api, /auth).

// ------------------------------------------------------------------ utilities

const $ = (sel, el = document) => el.querySelector(sel);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** h("div.cls#id", {attrs, on:{click}}, ...children) */
function h(tag, props, ...kids) {
  const [, name = "div", rest = ""] = tag.match(/^([a-z0-9]*)(.*)$/i);
  const el = document.createElement(name || "div");
  for (const m of rest.matchAll(/([.#])([\w-]+)/g)) m[1] === "." ? el.classList.add(m[2]) : (el.id = m[2]);
  if (props && (typeof props !== "object" || props instanceof Node || Array.isArray(props))) { kids.unshift(props); props = null; }
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k === "on") for (const [ev, fn] of Object.entries(v)) el.addEventListener(ev, fn);
    else if (k === "class") el.className += " " + v;
    else if (k === "html") el.innerHTML = v;
    else if (k in el && k !== "list" && typeof v !== "string") el[k] = v;
    else el.setAttribute(k, v === true ? "" : v);
  }
  for (const kid of kids.flat(Infinity)) if (kid != null && kid !== false) el.append(kid instanceof Node ? kid : String(kid));
  return el;
}

const ICONS = {
  plus: "M12 5v14M5 12h14",
  send: "M12 19V5M5 12l7-7 7 7",
  stop: "M7 7h10v10H7z",
  back: "M15 18l-6-6 6-6",
  chev: "M6 9l6 6 6-6",
  search: "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-3.5-3.5",
  more: "M5 12h.01M12 12h.01M19 12h.01",
  folder: "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
  folderPlus: "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM12 11v5M9.5 13.5h5",
  folderOff: "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM4 4l16 16",
  edit: "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4",
  archive: "M4 7h16v3H4zM5 10v9h14v-9M10 14h4",
  check: "M5 12l5 5 9-10",
  bolt: "M13 3L5 13h6l-1 8 8-10h-6z",
  moon: "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z",
  out: "M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4",
  bell: "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0",
  refresh: "M20 11a8 8 0 1 0-2.3 5.7M20 5v6h-6",
  steps: "M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01",
};
const icon = (name) => {
  const s = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  s.setAttribute("viewBox", "0 0 24 24");
  s.setAttribute("class", "i");
  const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
  p.setAttribute("d", ICONS[name]);
  s.append(p);
  return s;
};

const PALETTE = ["#E87068", "#99C69B", "#C39879", "#687DCA", "#AF97DC", "#F4A964", "#80C8BE", "#111111"];
function seedHash(s) { let x = 0x811c9dc5; for (const c of String(s)) { x ^= c.charCodeAt(0); x = Math.imul(x, 0x01000193) >>> 0; } return x; }

/** Flat Grok-style character, seeded by id, matching the app's mascot. */
function mascot(size, seed, { busy = false, need = false, live = false } = {}) {
  const n = seedHash(seed);
  const body = need ? "#F4A964" : PALETTE[n % PALETTE.length];
  const dark = body === "#111111";
  const eye = dark ? "#FFF8E9" : "#111111";
  const shapes = [
    '<circle cx="32" cy="32" r="30"/>',
    '<rect x="2" y="2" width="60" height="60" rx="22"/>',
    '<path d="M32 2c17 0 30 11 30 29s-12 31-30 31S2 50 2 31 15 2 32 2z"/>',
  ];
  const shape = shapes[(n >>> 5) % shapes.length].replace(/\/>$/, ` fill="${body}"/>`);
  const el = h("span.mascot" + (busy ? ".busy" : ""), { style: `width:${size}px;height:${size}px` });
  el.innerHTML = `<svg viewBox="0 0 64 64" aria-hidden="true">${shape}<g class="eyes" fill="${eye}">` +
    `<rect x="21" y="22" width="7" height="15" rx="3.5"/><rect x="36" y="22" width="7" height="15" rx="3.5"/></g>` +
    (need ? `<text x="32" y="56" text-anchor="middle" font-size="12" font-weight="800" fill="${eye}">!</text>` : "") + "</svg>";
  if (live || need) el.append(h("span.badge" + (need ? ".need" : "")));
  return el;
}

function ago(t) {
  if (!t) return "";
  const s = (Date.now() - t) / 1000;
  if (s < 60) return "now";
  if (s < 3600) return `${Math.floor(s / 60)}m`;
  if (s < 86400) return `${Math.floor(s / 3600)}h`;
  if (s < 604800) return `${Math.floor(s / 86400)}d`;
  return new Date(t).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

/** Small, safe markdown: everything is escaped first, then a few constructs are re-added. */
function markdown(src) {
  const blocks = [];
  let text = String(src).replace(/```([\w+-]*)\n?([\s\S]*?)(```|$)/g, (_, lang, code) => {
    blocks.push(`<pre><code>${esc(code.replace(/\n$/, ""))}</code></pre>`);
    return `\u0000${blocks.length - 1}\u0000`;
  });
  const inline = (s) => esc(s)
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, "$1<em>$2</em>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
  const out = [];
  let list = null;
  const closeList = () => { if (list) { out.push(`</${list}>`); list = null; } };
  for (const line of text.split("\n")) {
    const block = line.match(/^\u0000(\d+)\u0000$/);
    if (block) { closeList(); out.push(blocks[+block[1]]); continue; }
    const head = line.match(/^(#{1,3})\s+(.*)/);
    const ul = line.match(/^\s*[-*+]\s+(.*)/);
    const ol = line.match(/^\s*\d+[.)]\s+(.*)/);
    if (head) { closeList(); out.push(`<h${head[1].length + 1}>${inline(head[2])}</h${head[1].length + 1}>`); }
    else if (ul || ol) {
      const want = ul ? "ul" : "ol";
      if (list !== want) { closeList(); out.push(`<${want}>`); list = want; }
      out.push(`<li>${inline((ul || ol)[1])}</li>`);
    } else if (line.startsWith("> ")) { closeList(); out.push(`<blockquote>${inline(line.slice(2))}</blockquote>`); }
    else if (!line.trim()) { closeList(); }
    else { closeList(); out.push(`<p>${inline(line)}</p>`); }
  }
  closeList();
  return out.join("").replace(/\u0000(\d+)\u0000/g, (_, i) => blocks[+i]);
}

function toast(text) {
  document.querySelectorAll(".toast").forEach((t) => t.remove());
  const t = h("div.toast", text);
  document.body.append(t);
  setTimeout(() => t.remove(), 3200);
}

function menu(anchor, items) {
  const scrim = h("div.scrim", { on: { click: () => close() } });
  const m = h("div.menu", items.map((it) => {
    if (it.label && !it.run) return h("div.lbl", it.label);
    return h("button" + (it.danger ? ".danger" : ""), { on: { click: () => { close(); it.run(); } } }, it.icon ? icon(it.icon) : null, it.text,
      it.checked ? h("span", { style: "margin-left:auto;color:var(--accent)" }, icon("check")) : null);
  }));
  const close = () => { scrim.remove(); m.remove(); };
  document.body.append(scrim, m);
  const r = anchor.getBoundingClientRect();
  const w = m.offsetWidth, hgt = m.offsetHeight;
  m.style.left = Math.max(12, Math.min(r.right - w, innerWidth - w - 12)) + "px";
  m.style.top = (r.bottom + 6 + hgt > innerHeight ? Math.max(12, r.top - hgt - 6) : r.bottom + 6) + "px";
}

// ------------------------------------------------------------------ storage

const store = {
  get(k, d) { try { const v = localStorage.getItem("px." + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { v == null ? localStorage.removeItem("px." + k) : localStorage.setItem("px." + k, JSON.stringify(v)); } catch {} },
};
const session = {
  get() { try { return JSON.parse(sessionStorage.getItem("px.auth") || "null") || store.get("auth", null); } catch { return store.get("auth", null); } },
  set(v, remember) {
    try { sessionStorage.setItem("px.auth", JSON.stringify(v)); } catch {}
    store.set("auth", remember ? v : null);
  },
  clear() { try { sessionStorage.removeItem("px.auth"); } catch {} store.set("auth", null); },
};

// ------------------------------------------------------------------ state

const S = {
  auth: session.get(),           // { kind: "opencode"|"openchamber", header: string|null, label }
  backend: "",
  conn: "connecting",            // connecting | live | offline
  sessions: [],
  workspace: { projects: [], folders: [], chatsRoot: null, supportsFolders: false },
  models: [],
  defaultModel: null,
  model: store.get("model", null),
  dir: store.get("dir", null),
  current: null,                 // open session id
  messages: [],                  // raw messages of the open session, oldest first
  live: {},                      // assistantMessageID:ordinal -> streamed text not yet in messages
  queued: [],                    // optimistic user prompts
  perms: {},                     // id -> permission request
  closed: new Set(store.get("closed", [])),
  expanded: new Set(),
  query: "",
  notified: new Set(),
};

// ------------------------------------------------------------------ api

class ApiError extends Error { constructor(msg, code) { super(msg); this.code = code; } }

function authHeaders(extra = {}) {
  const hd = { Accept: "application/json", ...extra };
  if (S.auth?.header) hd.Authorization = S.auth.header;
  return hd;
}

async function api(method, path, body, { raw = false } = {}) {
  let r;
  try {
    r = await fetch(path, {
      method,
      headers: authHeaders(body !== undefined ? { "Content-Type": "application/json" } : {}),
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError("Can't reach the Parallax web server.");
  }
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch {}
  if (!r.ok) {
    if (r.status === 401 && S.auth && !raw) { signOut("Your sign-in expired. Sign in again."); }
    const e = json?.error;
    const msg = (typeof e === "string" ? e : e?.message) || json?.message || (r.status === 502 ? "Can't reach your server." : `Server error ${r.status}`);
    throw new ApiError(msg, r.status);
  }
  return json;
}
const data = (j) => (j && typeof j === "object" && !Array.isArray(j) && "data" in j ? j.data : j);

// ------------------------------------------------------------------ sign in

function deviceKey() {
  let k = store.get("device", null);
  if (!k) { k = crypto.randomUUID?.() || String(Math.random()).slice(2); store.set("device", k); }
  return k;
}

async function signIn(password) {
  // 1) OpenChamber: exchange the UI password for a per-device token (the password is never stored).
  try {
    const r = await fetch("/auth/session", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        password, trustDevice: true, issueClientToken: true,
        clientKind: "web", clientLabel: "Parallax web", deviceName: "Browser",
        devicePlatform: "web", deviceModel: navigator.userAgent.slice(0, 80), appVersion: "web-0.1", dedupeKey: deviceKey(),
      }),
    });
    const j = await r.json().catch(() => null);
    if (j?.clientToken) return { kind: "openchamber", header: `Bearer ${j.clientToken}` };
    if (r.ok && j && (j.authenticated === true || j.disabled)) return { kind: "openchamber", header: null };
  } catch {}
  // 2) Plain opencode: HTTP Basic with user "opencode".
  const header = password ? "Basic " + btoa(unescape(encodeURIComponent(`opencode:${password}`))) : null;
  const r = await fetch("/api/info", { headers: header ? { Authorization: header, Accept: "application/json" } : { Accept: "application/json" } });
  if (r.status === 401) throw new ApiError(password ? "Wrong password." : "This server needs a password.", 401);
  if (r.status === 502) throw new ApiError((await r.json().catch(() => null))?.error || "Can't reach your server.");
  if (!r.ok) throw new ApiError(`Server error ${r.status}`);
  // An OpenChamber with no UI password also answers here; its settings route tells them apart.
  const oc = await fetch("/api/config/settings", { headers: header ? { Authorization: header } : {} })
    .then((x) => x.ok && (x.headers.get("content-type") || "").includes("json")).catch(() => false);
  return { kind: oc ? "openchamber" : "opencode", header };
}

function signOut(reason) {
  stopStream();
  session.clear();
  S.auth = null;
  S.sessions = []; S.current = null; S.messages = [];
  render();
  if (reason) setTimeout(() => { const e = $(".err"); if (e) e.textContent = reason; }, 0);
}

// ------------------------------------------------------------------ loading

function mapSession(j) {
  const t = j.time || {};
  const busy = t.busy, idle = t.idle;
  const running = busy != null && (idle == null || idle < busy);
  return {
    id: j.id,
    title: j.title || j.slug || "Untitled chat",
    dir: (j.location?.directory || j.directory || "").replace(/\/+$/, ""),
    parent: j.parentID || null,
    projectId: j.projectID || null,
    model: j.model ? { providerID: j.model.providerID, id: j.model.id || j.model.modelID } : null,
    created: t.created || 0,
    updated: t.updated || t.created || 0,
    running,
    outcome: j.outcome || null,
    unread: j.outcome != null && (t.updated || 0) > (t.viewed || 0),
    archived: !!(t.archived || j.archived),
  };
}

async function loadSessions() {
  const list = [];
  let cursor = null;
  for (let page = 0; page < 10; page++) {
    const q = new URLSearchParams({ limit: "200", archived: "false" });
    if (cursor) q.set("cursor", cursor);
    const j = await api("GET", `/api/session?${q}`);
    const items = data(j) || [];
    for (const it of items) if (it?.id) list.push(mapSession(it));
    cursor = j?.cursor?.next;
    if (items.length < 200 || !cursor) break;
  }
  S.sessions = list.filter((s) => !s.archived).sort((a, b) => b.updated - a.updated);
}

async function loadWorkspace() {
  const ws = { projects: [], folders: [], chatsRoot: null, supportsFolders: false };
  if (S.auth.kind === "openchamber") {
    const settings = await api("GET", "/api/config/settings").catch(() => null);
    ws.projects = (settings?.projects || []).filter((p) => p?.path).map((p) => ({
      id: p.id || p.path, name: p.label || p.path.replace(/\/+$/, "").split("/").pop(), path: p.path.replace(/\/+$/, ""), color: p.color || null,
    }));
    const snap = await api("GET", "/api/session-folders").catch(() => null);
    if (snap?.foldersMap) {
      ws.supportsFolders = true;
      for (const [scope, arr] of Object.entries(snap.foldersMap)) {
        if (scope.startsWith("__archived__")) continue;
        for (const f of arr || []) if (f?.id) ws.folders.push({ id: f.id, name: f.name || "Folder", scope, parentId: f.parentId || null, sessionIds: f.sessionIds || [] });
      }
    }
  }
  if (!ws.projects.length) {
    const projects = data(await api("GET", "/api/project").catch(() => [])) || [];
    ws.projects = projects.filter((p) => p?.id && (p.canonical || p.directory)).map((p) => {
      const path = (p.canonical || p.directory).replace(/\/+$/, "");
      return { id: p.id, name: p.name || path.split("/").pop() || path, path, color: null, active: p.time?.active || p.time?.updated || 0 };
    }).sort((a, b) => b.active - a.active);
  }
  const chat = S.sessions.find((s) => isChat(s.dir));
  ws.chatsRoot = chat ? chat.dir.split("/").slice(0, -2).join("/") : null;
  S.workspace = ws;
}

async function loadModels() {
  const list = data(await api("GET", "/api/model").catch(() => [])) || [];
  S.models = list.filter((m) => m?.providerID && (m.id || m.modelID)).map((m) => ({
    providerID: m.providerID, id: m.id || m.modelID, name: m.name || m.id || m.modelID,
  })).sort((a, b) => (b.providerID === "opencode-go") - (a.providerID === "opencode-go") || a.providerID.localeCompare(b.providerID) || a.name.localeCompare(b.name));
  const d = data(await api("GET", "/api/model/default").catch(() => null));
  S.defaultModel = d?.providerID ? { providerID: d.providerID, id: d.id || d.modelID } : null;
  if (S.model && !S.models.some((m) => sameModel(m, S.model))) S.model = null;
}

async function loadPermissions() {
  const dirs = new Set([""]);
  for (const s of S.sessions) if (s.running) dirs.add(s.dir);
  const found = {};
  await Promise.all([...dirs].map(async (dir) => {
    const q = dir ? `?${new URLSearchParams({ "location[directory]": dir })}` : "";
    const list = data(await api("GET", `/api/permission/request${q}`).catch(() => [])) || [];
    for (const p of list) if (p?.id) found[p.id] = p;
  }));
  S.perms = found;
}

async function loadMessages(id) {
  const j = await api("GET", `/api/session/${encodeURIComponent(id)}/message?limit=200`);
  if (S.current !== id) return;
  S.messages = (data(j) || []).slice().reverse();
  // Streamed text that has now landed in a saved message is no longer "live".
  const saved = new Set(S.messages.map((m) => m.id));
  for (const key of Object.keys(S.live)) if (saved.has(key.split(":")[0])) delete S.live[key];
  const users = new Set(S.messages.filter((m) => m.type === "user").map((m) => m.text));
  S.queued = S.queued.filter((q) => q.session === id && !users.has(q.text));
}

async function refreshAll() {
  await loadSessions();
  await Promise.all([loadWorkspace(), loadPermissions()]);
  renderSide();
  if (S.current) { await loadMessages(S.current).catch(() => {}); renderThread(); }
}

// ------------------------------------------------------------------ live events

let streamCtl = null;
function stopStream() { streamCtl?.abort(); streamCtl = null; }

async function stream() {
  stopStream();
  const ctl = new AbortController();
  streamCtl = ctl;
  let backoff = 1000;
  while (!ctl.signal.aborted) {
    try {
      const r = await fetch("/api/event", { headers: authHeaders({ Accept: "text/event-stream" }), signal: ctl.signal });
      if (r.status === 401) return signOut("Your sign-in expired. Sign in again.");
      if (!r.ok || !r.body) throw new Error(`HTTP ${r.status}`);
      setConn("live");
      backoff = 1000;
      const reader = r.body.pipeThrough(new TextDecoderStream()).getReader();
      let buf = "";
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buf += value.replace(/\r/g, "");
        let i;
        while ((i = buf.indexOf("\n\n")) >= 0) {
          const chunk = buf.slice(0, i);
          buf = buf.slice(i + 2);
          const payload = chunk.split("\n").filter((l) => l.startsWith("data:")).map((l) => l.slice(5).trimStart()).join("\n");
          if (payload) { try { onEvent(JSON.parse(payload)); } catch (e) { console.warn(e); } }
        }
      }
    } catch (e) {
      if (ctl.signal.aborted) return;
    }
    setConn("offline");
    await sleep(backoff);
    backoff = Math.min(backoff * 2, 15000);
    if (!ctl.signal.aborted) refreshAll().catch(() => {});
  }
}

const timers = {};
function debounce(key, ms, fn) { clearTimeout(timers[key]); timers[key] = setTimeout(fn, ms); }

function onEvent(ev) {
  const type = ev.type || "";
  const d = ev.data || {};
  const sid = d.sessionID;
  const s = sid && S.sessions.find((x) => x.id === sid);

  if (type === "session.text.delta" && sid) {
    if (sid === S.current) {
      const key = `${d.assistantMessageID}:${d.ordinal ?? 0}`;
      S.live[key] = (S.live[key] || "") + (d.delta || "");
      renderThread();
    }
    return;
  }
  if (type === "session.execution.started" && s) { s.running = true; s.updated = ev.created || Date.now(); renderSide(); renderHead(); }
  if ((type === "session.execution.succeeded" || type === "session.execution.failed") && s) {
    s.running = false; s.outcome = type.endsWith("failed") ? "failed" : "succeeded"; s.updated = ev.created || Date.now();
    if (sid !== S.current || document.hidden) s.unread = true;
    else api("POST", `/api/session/${encodeURIComponent(sid)}/view`, { idle: Date.now() }).catch(() => {});
    notify(s, type.endsWith("failed") ? "Run failed" : "Finished");
    renderSide(); renderHead();
  }
  if (type === "permission.asked") {
    if (d.id) S.perms[d.id] = d;
    const ps = S.sessions.find((x) => x.id === d.sessionID);
    if (ps) notify(ps, "Needs your OK");
    renderSide(); if (d.sessionID === S.current) renderThread();
  }
  if (type === "permission.replied") {
    delete S.perms[d.requestID];
    renderSide(); renderThread();
  }
  if (sid && sid === S.current && /^session\.(text\.ended|step\.ended|tool\.|inbox\.|execution\.)/.test(type)) {
    debounce("msgs", 250, () => loadMessages(sid).then(renderThread).catch(() => {}));
  }
  if (/^session\.(created|renamed|archived|unarchived|moved|metadata|deleted|updated|viewed)/.test(type) || (type.startsWith("session.") && !s && sid)) {
    debounce("sessions", 700, () => loadSessions().then(() => { renderSide(); renderHead(); }).catch(() => {}));
  }
}

function notify(s, what) {
  if (s.parent) return;
  const key = `${s.id}:${what}:${Math.floor(Date.now() / 5000)}`;
  if (S.notified.has(key)) return;
  S.notified.add(key);
  updateTitle();
  if (!document.hidden && s.id === S.current) return;
  if ("Notification" in window && Notification.permission === "granted" && document.hidden) {
    const n = new Notification(s.title, { body: what, tag: s.id, icon: "icon.svg" });
    n.onclick = () => { window.focus(); openSession(s.id); n.close(); };
  }
}

function updateTitle() {
  const need = new Set(Object.values(S.perms).map((p) => p.sessionID)).size;
  document.title = need ? `(${need}) Parallax` : "Parallax";
}

function setConn(c) { if (S.conn !== c) { S.conn = c; renderSideTop(); renderHead(); } }

// ------------------------------------------------------------------ grouping (mirrors the app's Grouping.kt)

const CHAT_LEAF = /^session-[0-9a-f-]{8,}$/i;
const isChat = (dir) => !!dir && CHAT_LEAF.test(dir.split("/").pop());

function buildSections(sessions) {
  const ws = S.workspace;
  const byLen = [...ws.projects].sort((a, b) => b.path.length - a.path.length);
  const byProject = new Map(), other = new Map(), chats = [];
  for (const s of sessions) {
    const p = byLen.find((p) => s.dir === p.path || s.dir.startsWith(p.path + "/")) || ws.projects.find((p) => p.id === s.projectId);
    if (isChat(s.dir)) chats.push(s);
    else if (p) (byProject.get(p.id) || byProject.set(p.id, []).get(p.id)).push(s);
    else (other.get(s.dir || "unknown") || other.set(s.dir || "unknown", []).get(s.dir || "unknown")).push(s);
  }
  const withFolders = (scope, list) => {
    const folders = ws.folders.filter((f) => f.scope.replace(/\/+$/, "") === scope.replace(/\/+$/, ""));
    const filed = new Set();
    const groups = folders.map((f) => {
      const inside = list.filter((s) => f.sessionIds.includes(s.id));
      inside.forEach((s) => filed.add(s.id));
      return { folder: f, sessions: inside };
    }).filter((g) => g.sessions.length);
    return { folders: groups, loose: list.filter((s) => !filed.has(s.id)) };
  };
  const out = [];
  for (const p of ws.projects) {
    const list = byProject.get(p.id);
    if (list) out.push({ key: "p-" + p.id, title: p.name, color: p.color, scope: p.path, path: p.path, ...withFolders(p.path, list) });
  }
  if (chats.length) {
    const root = ws.chatsRoot || chats[0].dir.split("/").slice(0, -2).join("/");
    out.push({ key: "chats", title: "Chats", scope: root, path: null, ...withFolders(root, chats) });
  }
  [...other.entries()].sort((a, b) => b[1][0].updated - a[1][0].updated).forEach(([dir, list]) => {
    out.push({ key: "o-" + dir, title: dir.split("/").pop() || dir, scope: dir, path: dir, ...withFolders(dir, list) });
  });
  return out;
}

function scopeFor(s) {
  if (isChat(s.dir)) return S.workspace.chatsRoot || s.dir.split("/").slice(0, -2).join("/");
  const p = [...S.workspace.projects].sort((a, b) => b.path.length - a.path.length).find((p) => s.dir === p.path || s.dir.startsWith(p.path + "/"));
  return p ? p.path : s.dir;
}

const needsYou = (id) => Object.values(S.perms).some((p) => p.sessionID === id);

// ------------------------------------------------------------------ rendering

const app = $("#app");

function render() {
  app.replaceChildren();
  if (!S.auth) return renderConnect();
  const shell = h("div.shell", { id: "shell" },
    h("aside.side", { id: "side" }, h("div", { id: "sideTop" }), searchBox(), h("div.list", { id: "list" })),
    h("main.main", { id: "main" }, h("div.head", { id: "head" }), h("div.scroll", { id: "scroll" }), composer()),
  );
  app.append(shell);
  renderSide();
  renderMain();
}

function renderConnect() {
  let busy = false;
  const pw = h("input.field", { type: "password", placeholder: "Password", autocomplete: "current-password", autofocus: true });
  const remember = h("input", { type: "checkbox", checked: true, style: "accent-color:var(--accent)" });
  const err = h("div.err");
  const btn = h("button.primary", { type: "submit" }, "Connect");
  const form = h("form", { on: { submit: async (e) => {
    e.preventDefault();
    if (busy) return;
    busy = true; btn.disabled = true; btn.textContent = "Connecting…"; err.textContent = "";
    try {
      const auth = await signIn(pw.value);
      session.set(auth, remember.checked);
      S.auth = auth;
      boot();
    } catch (ex) {
      err.textContent = ex.message;
      busy = false; btn.disabled = false; btn.textContent = "Connect";
    }
  } } }, pw, btn, err,
    h("label.hint", { style: "display:flex;gap:8px;align-items:center;justify-content:center" }, remember, "Stay signed in on this device"));
  app.append(h("div.connect", h("div.connect-card",
    mascot(88, "parallax"),
    h("h1", "Parallax"),
    h("p", "Your agents, in any browser."),
    h("p.hint", { id: "backendHint" }, S.backend ? `Server: ${S.backend}` : ""),
    form,
    h("p.hint", "Use your opencode server password or your OpenChamber UI password. Leave it empty if the server has none."),
  )));
  setTimeout(() => pw.focus(), 50);
}

function renderSideTop() {
  const el = $("#sideTop");
  if (!el) return;
  const label = S.backend || "Server";
  const status = S.conn === "live" ? "Live" : S.conn === "offline" ? "Reconnecting…" : "Connecting…";
  const avatar = h("button.avatar", { title: "Menu", on: { click: (e) => settingsMenu(e.currentTarget) } }, S.auth.kind === "openchamber" ? "OC" : "O");
  el.replaceChildren(h("div.side-top", avatar,
    h("div.who", h("b", S.auth.kind === "openchamber" ? "OpenChamber" : "opencode"),
      h("small", h("span.dot" + (S.conn === "live" ? ".live" : S.conn === "offline" ? ".warn" : "")), `${status} · ${label}`)),
    h("button.circle.ink", { title: "New chat", on: { click: () => openSession(null) } }, icon("plus"))));
}

function searchBox() {
  const input = h("input", { placeholder: "Search chats", value: S.query, on: { input: (e) => { S.query = e.target.value; renderList(); } } });
  return h("div.search", icon("search"), input);
}

function renderSide() { renderSideTop(); renderList(); updateTitle(); }

function sessionRow(s) {
  const need = needsYou(s.id);
  const sub = need ? "Needs your OK" : s.running ? "Working…" : s.outcome === "failed" ? "Run failed" : modelName(s.model) || prettyDir(s.dir);
  return h("button.row" + (s.id === S.current ? ".on" : ""), {
    on: {
      click: () => openSession(s.id),
      contextmenu: (e) => { e.preventDefault(); sessionMenu(e.currentTarget, s); },
    },
  },
  mascot(40, s.id, { busy: s.running, need, live: s.running }),
  h("span.t", h("b", s.title), h("small", sub)),
  h("span.when", ago(s.updated)),
  s.unread && !need && s.id !== S.current && Date.now() - s.updated < 86400000 ? h("span.unread") : null);
}

function renderList() {
  const el = $("#list");
  if (!el) return;
  const q = S.query.trim().toLowerCase();
  const top = S.sessions.filter((s) => !s.parent && (!q || s.title.toLowerCase().includes(q) || s.dir.toLowerCase().includes(q)));
  const kids = [];
  if (!top.length) {
    el.replaceChildren(h("div.empty", q ? "No chats match." : S.conn === "connecting" ? "Loading…" : "No chats yet. Start one with +."));
    return;
  }
  const active = top.filter((s) => s.running || needsYou(s.id));
  if (active.length && !q) kids.push(section({ key: "running", title: "Running", loose: active, folders: [] }));
  for (const sec of buildSections(top)) kids.push(section(sec));
  el.replaceChildren(...kids);
}

function section(sec) {
  const closed = S.closed.has(sec.key);
  const count = sec.loose.length + sec.folders.reduce((n, f) => n + f.sessions.length, 0);
  const head = h("button.sec-h" + (closed ? ".closed" : ""), {
    on: { click: () => { closed ? S.closed.delete(sec.key) : S.closed.add(sec.key); store.set("closed", [...S.closed]); renderList(); } },
  }, icon("chev"), sec.color ? h("span.sw", { style: `background:${sec.color}` }) : null, h("span", sec.title), h("span.n", String(count)));
  if (closed) return h("div", head);
  const body = [];
  for (const g of sec.folders) {
    const fk = `${sec.key}/f/${g.folder.id}`;
    const fclosed = S.closed.has(fk);
    body.push(h("button.folder-h", { on: { click: () => { fclosed ? S.closed.delete(fk) : S.closed.add(fk); store.set("closed", [...S.closed]); renderList(); } } },
      icon("folder"), g.folder.name, h("span", { style: "margin-left:auto;color:var(--faint)" }, String(g.sessions.length))));
    if (!fclosed) body.push(h("div.folder-body", g.sessions.map(sessionRow)));
  }
  const limit = S.expanded.has(sec.key) ? Infinity : 8;
  sec.loose.slice(0, limit).forEach((s) => body.push(sessionRow(s)));
  if (sec.loose.length > limit) {
    body.push(h("button.more", { on: { click: () => { S.expanded.add(sec.key); renderList(); } } }, `Show ${sec.loose.length - limit} more`));
  }
  return h("div", head, body);
}

function renderMain() {
  const shell = $("#shell");
  if (!shell) return;
  shell.classList.toggle("in-chat", S.current != null || S.composeHome === true);
  renderHead();
  renderThread();
  syncComposer();
}

function renderHead() {
  const el = $("#head");
  if (!el) return;
  const back = h("button.circle.back", { title: "Back", on: { click: () => { S.current = null; S.composeHome = false; renderMain(); renderList(); } } }, icon("back"));
  const s = S.sessions.find((x) => x.id === S.current);
  if (!s) {
    el.replaceChildren(back, h("div.t", h("b", "New chat"), h("small", prettyDir(S.dir) || "Pick a project below")));
    return;
  }
  const need = needsYou(s.id);
  const status = need ? "Needs your OK" : s.running ? "Working…" : modelName(s.model) || "Ready";
  el.replaceChildren(back, mascot(40, s.id, { busy: s.running, need, live: s.running }),
    h("div.t", h("b", s.title), h("small", h("span.dot" + (s.running ? ".live" : need ? ".warn" : "")), status, " · ", prettyDir(s.dir))),
    h("button.circle", { title: "Chat actions", on: { click: (e) => sessionMenu(e.currentTarget, s) } }, icon("more")));
}

function friendlyStep(part) {
  const name = (part.name || part.tool || "").toLowerCase();
  const input = part.state?.input || part.input || {};
  const file = (input.filePath || input.path || input.file || "").split("/").pop();
  if (/shell|bash|command/.test(name)) return "Ran a command";
  if (/read/.test(name)) return file ? `Read ${file}` : "Read a file";
  if (/edit|write|patch/.test(name)) return file ? `Edited ${file}` : "Edited files";
  if (/grep|glob|search|list|ls/.test(name)) return "Searched the project";
  if (/fetch|web/.test(name)) return "Read a web page";
  if (/task|agent/.test(name)) return "Asked a helper";
  if (/todo/.test(name)) return "Updated the plan";
  return name ? `Used ${name.replace(/[_-]+/g, " ")}` : "Worked";
}

function renderThread() {
  const scroll = $("#scroll");
  if (!scroll) return;
  const nearBottom = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 120;
  const s = S.sessions.find((x) => x.id === S.current);
  if (!S.current) { scroll.replaceChildren(homeView()); return; }
  const rows = [];
  let lastDay = "", steps = [];
  const flushSteps = (done) => {
    if (!steps.length) return;
    const list = steps;
    rows.push(h("details.steps", h("summary", icon("steps"), done ? `Took ${list.length} step${list.length > 1 ? "s" : ""}` : `Working · ${list[list.length - 1]}`),
      h("ul", list.map((t) => h("li", t)))));
    steps = [];
  };
  const day = (t) => {
    const d = new Date(t).toDateString();
    if (t && d !== lastDay) { lastDay = d; rows.push(h("div.day", new Date(t).toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" }))); }
  };
  for (const m of S.messages) {
    const t = m.time?.created || 0;
    if (m.type === "user") {
      flushSteps(true); day(t);
      rows.push(h("div.msg.user", m.text || ""));
    } else if (m.type === "assistant") {
      day(t);
      (m.content || []).forEach((part, ordinal) => {
        if (part.type === "tool") steps.push(friendlyStep(part));
        else if (part.type === "text" && part.text?.trim()) {
          flushSteps(true);
          rows.push(h("div.msg.agent", { html: markdown(part.text) }));
          delete S.live[`${m.id}:${ordinal}`];
        }
      });
      if (m.error) { flushSteps(true); rows.push(h("div.notice.bad", typeof m.error === "string" ? m.error : m.error.message || "The model returned an error.")); }
    } else if (m.type === "idle" && m.outcome === "failed") {
      flushSteps(true); rows.push(h("div.notice.bad", "Run failed"));
    }
  }
  const liveTexts = Object.entries(S.live).filter(([, v]) => v.trim());
  flushSteps(!s?.running || liveTexts.length > 0);
  for (const [, text] of liveTexts) rows.push(h("div.msg.agent", { html: markdown(text) }));
  for (const q of S.queued.filter((q) => q.session === S.current)) rows.push(h("div.msg.user.queued", q.text));
  for (const p of Object.values(S.perms).filter((p) => p.sessionID === S.current)) rows.push(approvalCard(p));
  if (s?.running && !liveTexts.length && !needsYou(s.id)) rows.push(h("div.typing", h("i"), h("i"), h("i")));
  if (!rows.length) rows.push(h("div.empty", "Loading…"));
  scroll.replaceChildren(h("div.thread", rows));
  if (nearBottom || S.pinBottom) { scroll.scrollTop = scroll.scrollHeight; S.pinBottom = false; }
}

function approvalCard(p) {
  const action = p.action || "do something";
  const res = (p.resources || []).join("\n");
  const reply = async (decision) => {
    try {
      await api("POST", `/api/session/${encodeURIComponent(p.sessionID)}/permission/${encodeURIComponent(p.id)}/reply`, { decision });
      delete S.perms[p.id];
      renderThread(); renderSide();
    } catch (e) { toast(e.message); }
  };
  return h("div.approval", h("b", `Parallax wants to ${action.replace(/[_-]+/g, " ")}`),
    p.message ? h("div", { style: "color:var(--muted);font-size:14px" }, p.message) : null,
    res ? h("div.res", res) : null,
    h("div.acts", h("button.pill.go", { on: { click: () => reply("once") } }, "Allow"),
      h("button.pill", { on: { click: () => reply("always") } }, "Always allow"),
      h("button.pill", { on: { click: () => reply("reject") } }, "Deny")));
}

function homeView() {
  const ideas = ["Fix the failing tests", "Explain this codebase", "Review my last commit"];
  return h("div.home", { style: "min-height:100%" },
    mascot(104, "parallax", { live: S.conn === "live" }),
    h("h2", "What should we build?"),
    h("p", `${S.auth.kind === "openchamber" ? "OpenChamber" : "opencode"} · ${S.backend}`),
    h("div.chips", ideas.map((t) => h("button.pill", { on: { click: () => { const ta = $("#prompt"); ta.value = t; ta.dispatchEvent(new Event("input")); ta.focus(); } } }, t))));
}

// ------------------------------------------------------------------ composer

function composer() {
  const ta = h("textarea", { id: "prompt", rows: 1, placeholder: "Ask anything", on: {
    input: () => { ta.style.height = "auto"; ta.style.height = Math.min(ta.scrollHeight, innerHeight * 0.4) + "px"; syncComposer(); },
    keydown: (e) => { if (e.key === "Enter" && !e.shiftKey && !e.isComposing && !matchMedia("(pointer: coarse)").matches) { e.preventDefault(); submit(); } },
  } });
  const proj = h("select", { id: "projSel", title: "Project", on: { change: (e) => { S.dir = e.target.value || null; store.set("dir", S.dir); renderHead(); } } });
  const model = h("select", { id: "modelSel", title: "Model", on: { change: (e) => {
    const [providerID, ...rest] = e.target.value.split("/");
    S.model = e.target.value ? { providerID, id: rest.join("/") } : null;
    store.set("model", S.model);
    if (S.current && S.model) api("POST", `/api/session/${encodeURIComponent(S.current)}/model`, { model: S.model }).catch((x) => toast(x.message));
  } } });
  const send = h("button.send", { id: "sendBtn", title: "Send", on: { click: () => submit() } }, icon("send"));
  return h("div.composer-wrap", h("div.composer", ta, h("div.bar", proj, model, h("span.sp"), send)));
}

function syncComposer() {
  const ta = $("#prompt"), btn = $("#sendBtn"), proj = $("#projSel"), model = $("#modelSel");
  if (!ta) return;
  const s = S.sessions.find((x) => x.id === S.current);
  const running = !!s?.running;
  const stopMode = running && !ta.value.trim();
  btn.replaceChildren(icon(stopMode ? "stop" : "send"));
  btn.title = stopMode ? "Stop" : "Send";
  btn.disabled = !stopMode && !ta.value.trim();
  ta.placeholder = running ? "Add to the queue…" : S.current ? "Reply" : "Ask anything";
  // Project picker only when starting a chat.
  proj.classList.toggle("hidden", !!S.current);
  if (!S.current) {
    const opts = [h("option", { value: "" }, "Default folder"), ...S.workspace.projects.map((p) => h("option", { value: p.path, selected: p.path === S.dir }, p.name))];
    proj.replaceChildren(...opts);
  }
  const current = s?.model || S.model || S.defaultModel;
  const opts = [h("option", { value: "" }, S.defaultModel ? `Default · ${modelName(S.defaultModel)}` : "Default model")];
  let group = null, lastProvider = null;
  for (const m of S.models) {
    if (m.providerID !== lastProvider) { lastProvider = m.providerID; group = h("optgroup", { label: providerName(m.providerID) }); opts.push(group); }
    group.append(h("option", { value: `${m.providerID}/${m.id}`, selected: !!(current && (s?.model || S.model) && sameModel(m, current)) }, m.name));
  }
  model.replaceChildren(...opts);
}

async function submit() {
  const ta = $("#prompt");
  const text = ta.value.trim();
  const s = S.sessions.find((x) => x.id === S.current);
  if (!text) {
    if (s?.running) api("POST", `/api/session/${encodeURIComponent(s.id)}/interrupt`).catch((e) => toast(e.message));
    return;
  }
  ta.value = ""; ta.style.height = "auto"; syncComposer();
  try {
    if (S.current) {
      S.queued.push({ session: S.current, text });
      S.pinBottom = true; renderThread();
      await api("POST", `/api/session/${encodeURIComponent(S.current)}/prompt`, s?.running ? { text, delivery: "queue" } : { text });
    } else {
      const body = { metadata: { parallax: true }, title: text.split("\n")[0].slice(0, 60) };
      const model = S.model || S.defaultModel;
      if (model) body.model = { providerID: model.providerID, id: model.id };
      if (S.dir) body.location = { directory: S.dir };
      const created = data(await api("POST", "/api/session", body));
      const ns = mapSession(created);
      S.sessions = [ns, ...S.sessions.filter((x) => x.id !== ns.id)];
      S.current = ns.id; S.messages = []; S.live = {};
      S.queued.push({ session: ns.id, text });
      history.pushState({ s: ns.id }, "", `#${ns.id}`);
      renderSide(); renderMain();
      await api("POST", `/api/session/${encodeURIComponent(ns.id)}/prompt`, { text });
    }
  } catch (e) {
    toast(e.message);
    S.queued = S.queued.filter((q) => q.text !== text);
    if (!ta.value) ta.value = text;
    renderThread(); syncComposer();
  }
}

// ------------------------------------------------------------------ actions

async function openSession(id, { push = true } = {}) {
  S.current = id;
  S.composeHome = id == null;
  S.messages = []; S.live = {};
  if (push) history.pushState({ s: id }, "", id ? `#${id}` : "#new");
  renderMain(); renderList();
  if (!id) { $("#prompt")?.focus(); return; }
  S.pinBottom = true;
  try {
    await loadMessages(id);
    S.pinBottom = true;
    renderThread();
    const s = S.sessions.find((x) => x.id === id);
    if (s?.unread) { s.unread = false; renderList(); api("POST", `/api/session/${encodeURIComponent(id)}/view`, { idle: Date.now() }).catch(() => {}); }
  } catch (e) { toast(e.message); }
  if (!matchMedia("(pointer: coarse)").matches) $("#prompt")?.focus();
}

function sessionMenu(anchor, s) {
  const items = [
    { icon: "edit", text: "Rename", run: async () => {
      const t = prompt("Rename chat", s.title);
      if (!t?.trim()) return;
      try { await api("PATCH", `/api/session/${encodeURIComponent(s.id)}`, { title: t.trim() }); s.title = t.trim(); renderSide(); renderHead(); } catch (e) { toast(e.message); }
    } },
  ];
  if (S.workspace.supportsFolders) items.push({ icon: "folder", text: "Move to folder", run: () => folderMenu(anchor, s) });
  items.push({ icon: "archive", text: "Archive", danger: true, run: async () => {
    try {
      await api("PATCH", `/api/session/${encodeURIComponent(s.id)}`, { archived: true });
      S.sessions = S.sessions.filter((x) => x.id !== s.id);
      if (S.current === s.id) openSession(null);
      renderSide(); toast("Archived");
    } catch (e) { toast(e.message); }
  } });
  menu(anchor, items);
}

function folderMenu(anchor, s) {
  const scope = scopeFor(s);
  const folders = S.workspace.folders.filter((f) => f.scope.replace(/\/+$/, "") === scope.replace(/\/+$/, ""));
  const current = folders.find((f) => f.sessionIds.includes(s.id));
  const move = async (folderId) => {
    const change = { type: folderId ? "assign" : "unassign", scopeKey: scope, sessionIds: [s.id], from: { [s.id]: current?.id ?? null } };
    if (folderId) change.folderId = folderId;
    try {
      await api("PATCH", "/api/session-folders", { changes: [change] });
      await loadWorkspace(); renderList(); toast(folderId ? "Moved" : "Removed from folder");
    } catch (e) { toast(e.message); }
  };
  menu(anchor, [
    { label: "Move to folder" },
    { icon: "folderOff", text: "No folder", checked: !current, run: () => move(null) },
    ...folders.filter((f) => !f.parentId).map((f) => ({ icon: "folder", text: f.name, checked: current?.id === f.id, run: () => move(f.id) })),
    { icon: "folderPlus", text: "New folder…", run: async () => {
      const name = prompt("Folder name");
      if (!name?.trim()) return;
      const id = "folder-" + (crypto.randomUUID?.() || String(Math.random()).slice(2)).replace(/-/g, "").slice(0, 12);
      try {
        await api("PATCH", "/api/session-folders", { changes: [{ type: "create", scopeKey: scope, folder: { id, name: name.trim(), parentId: null, createdAt: Date.now() } }] });
        await move(id);
      } catch (e) { toast(e.message); }
    } },
  ]);
}

function settingsMenu(anchor) {
  const theme = document.documentElement.dataset.theme || "";
  const items = [
    { label: `${S.auth.kind === "openchamber" ? "OpenChamber" : "opencode"} · ${S.backend}` },
    { icon: "refresh", text: "Refresh", run: () => refreshAll().then(() => toast("Up to date")).catch((e) => toast(e.message)) },
    { icon: "moon", text: theme === "dark" ? "Light mode" : theme === "light" ? "Match system" : "Dark mode", run: () => {
      const next = theme === "dark" ? "light" : theme === "light" ? "" : "dark";
      if (next) document.documentElement.dataset.theme = next; else delete document.documentElement.dataset.theme;
      store.set("theme", next || null);
    } },
  ];
  if ("Notification" in window && Notification.permission === "default") {
    items.push({ icon: "bell", text: "Turn on notifications", run: () => Notification.requestPermission() });
  }
  items.push({ icon: "out", text: "Sign out", danger: true, run: () => signOut() });
  menu(anchor, items);
}

// ------------------------------------------------------------------ names

const sameModel = (a, b) => a && b && a.providerID === b.providerID && a.id === b.id;
function providerName(p) {
  return { "opencode-go": "OpenCode Go", opencode: "OpenCode Zen", anthropic: "Anthropic", openai: "OpenAI", google: "Google", deepseek: "DeepSeek", openrouter: "OpenRouter" }[p]
    || p.replace(/[-_]+/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}
function modelName(m) {
  if (!m) return "";
  const found = S.models.find((x) => sameModel(x, m));
  if (found) return found.name;
  return m.id.replace(/[-_]+/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}
function prettyDir(dir) {
  if (!dir) return "";
  if (isChat(dir)) return "Chat";
  const p = S.workspace.projects.find((p) => p.path === dir);
  return p ? p.name : dir.split("/").filter(Boolean).pop() || dir;
}

// ------------------------------------------------------------------ boot

async function boot() {
  render();
  setConn("connecting");
  try {
    await loadSessions();
    renderSide();
    await Promise.all([loadWorkspace(), loadModels(), loadPermissions()]);
    renderSide(); syncComposer();
    const fromHash = location.hash.slice(1);
    if (fromHash && fromHash !== "new" && S.sessions.some((s) => s.id === fromHash)) openSession(fromHash, { push: false });
    else renderMain();
  } catch (e) {
    if (!S.auth) return;
    toast(e.message);
  }
  stream();
  // Folders and projects change from other OpenChamber clients without an event; poll gently.
  clearInterval(timers.ws);
  timers.ws = setInterval(() => { if (!document.hidden && S.auth) loadWorkspace().then(renderList).catch(() => {}); }, 20000);
}

window.addEventListener("popstate", () => {
  if (!S.auth) return;
  const id = location.hash.slice(1);
  openSession(id && id !== "new" ? id : null, { push: false });
  if (!id) { S.composeHome = false; renderMain(); }
});
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && S.auth) { updateTitle(); refreshAll().catch(() => {}); }
});
setInterval(() => { if (S.auth && !document.hidden) renderList(); }, 60000); // keep "5m ago" fresh

const savedTheme = store.get("theme", null);
if (savedTheme) document.documentElement.dataset.theme = savedTheme;

fetch("/_px/info").then((r) => r.json()).then((j) => { S.backend = j.backend || ""; const el = $("#backendHint"); if (el) el.textContent = `Server: ${S.backend}`; if (S.auth) renderSideTop(); }).catch(() => {});

if (S.auth) boot(); else render();
