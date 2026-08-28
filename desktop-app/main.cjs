const { app, BrowserWindow, ipcMain, safeStorage } = require("electron");
const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const APP_USER_MODEL_ID = "com.appforge.studio.desktop";
const AUTH_TOKEN_FILE = "auth-token.bin";
const STATE_FILE = "desktop-state.bin";
const MAX_STATE_BYTES = 8 * 1024 * 1024;
const MAX_KEYSTORE_BYTES = 4 * 1024 * 1024;
let localServer;

function assets() {
  if (app.isPackaged) {
    return {
      studio: path.join(process.resourcesPath, "public", "studio"),
      monaco: path.join(process.resourcesPath, "public", "vendor", "monaco"),
      fflate: path.join(process.resourcesPath, "public", "vendor", "fflate")
    };
  }
  const root = path.join(__dirname, "..", "build-service");
  return {
    studio: path.join(root, "public", "studio"),
    monaco: path.join(__dirname, "node_modules", "monaco-editor", "min", "vs"),
    fflate: path.join(__dirname, "node_modules", "fflate", "umd")
  };
}

function encryptedFile(name) {
  return path.join(app.getPath("userData"), name);
}

function clearEncryptedFile(name) {
  try { fs.rmSync(encryptedFile(name), { force: true }); } catch {}
  return true;
}

function readEncryptedString(name) {
  try {
    if (!safeStorage.isEncryptionAvailable()) return "";
    return safeStorage.decryptString(fs.readFileSync(encryptedFile(name)));
  } catch {
    return "";
  }
}

function writeEncryptedString(name, value) {
  if (!safeStorage.isEncryptionAvailable()) {
    throw new Error("Windows secure storage is unavailable.");
  }
  const text = String(value ?? "");
  const target = encryptedFile(name);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, safeStorage.encryptString(text));
  return true;
}

function readAuthToken() {
  return readEncryptedString(AUTH_TOKEN_FILE);
}

function writeAuthToken(value) {
  const token = typeof value === "string" ? value.trim() : "";
  if (!token) return clearEncryptedFile(AUTH_TOKEN_FILE);
  return writeEncryptedString(AUTH_TOKEN_FILE, token);
}

function readState() {
  try {
    const raw = readEncryptedString(STATE_FILE);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function writeState(state) {
  const raw = JSON.stringify(state || {});
  if (Buffer.byteLength(raw, "utf8") > MAX_STATE_BYTES) {
    throw new Error("Desktop state is too large.");
  }
  return writeEncryptedString(STATE_FILE, raw);
}

function cleanStoreKey(value) {
  const key = String(value || "");
  if (!/^[A-Za-z0-9_.:-]{1,80}$/.test(key)) throw new Error("Invalid store key.");
  return key;
}

function storeGet(key) {
  return readState().store?.[cleanStoreKey(key)] ?? null;
}

function storeSet(key, value) {
  key = cleanStoreKey(key);
  const state = readState();
  state.store ||= {};
  const probe = JSON.stringify(value);
  if (Buffer.byteLength(probe, "utf8") > 1024 * 1024) throw new Error("Store value is too large.");
  state.store[key] = value;
  writeState(state);
  return true;
}

function storeRemove(key) {
  key = cleanStoreKey(key);
  const state = readState();
  if (state.store) delete state.store[key];
  writeState(state);
  return true;
}

function saveKeystore(payload) {
  const name = path.basename(String(payload?.name || "release.jks")).slice(0, 180);
  const base64 = String(payload?.base64 || "");
  const bytes = Buffer.from(base64, "base64");
  if (!bytes.length || bytes.length > MAX_KEYSTORE_BYTES) throw new Error("Keystore must be 1 byte to 4 MB.");

  const state = readState();
  state.keystore = {
    name,
    base64: bytes.toString("base64"),
    alias: String(payload?.alias || "").slice(0, 256),
    storePassword: String(payload?.storePassword || "").slice(0, 1024),
    keyPassword: String(payload?.keyPassword || "").slice(0, 1024),
    sha1: crypto.createHash("sha1").update(bytes).digest("hex").match(/.{2}/g).join(":").toUpperCase(),
    sha256: crypto.createHash("sha256").update(bytes).digest("hex").match(/.{2}/g).join(":").toUpperCase()
  };
  writeState(state);
  return {
    name: state.keystore.name,
    alias: state.keystore.alias,
    sha1: state.keystore.sha1,
    sha256: state.keystore.sha256
  };
}

function getKeystore() {
  return readState().keystore || null;
}

function clearKeystore() {
  const state = readState();
  delete state.keystore;
  writeState(state);
  return true;
}

async function localAiChat(payload) {
  const model = String(payload?.model || "qwen2.5-coder:7b").trim().slice(0, 100);
  if (!model) throw new Error("Model name is required.");

  const input = Array.isArray(payload?.messages) ? payload.messages : [];
  const messages = input.slice(-20).map(message => {
    const role = ["system", "user", "assistant"].includes(message?.role) ? message.role : "user";
    const content = String(message?.content || "").slice(0, 16000);
    return { role, content };
  });
  if (!messages.length) throw new Error("AI message is required.");

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 90_000);
  try {
    const response = await fetch("http://127.0.0.1:11434/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model, messages, stream: false }),
      signal: controller.signal
    });
    const text = await response.text();
    let data = {};
    try { data = text ? JSON.parse(text) : {}; } catch {}
    if (!response.ok) throw new Error(data?.error || `Ollama HTTP ${response.status}`);
    return { text: String(data?.message?.content || "") };
  } catch (error) {
    if (error?.name === "AbortError") throw new Error("Yerel AI zaman aşımına uğradı.");
    throw new Error(`Ollama yerel AI kullanılamadı: ${String(error?.message || error)}`);
  } finally {
    clearTimeout(timeout);
  }
}

function registerDesktopIpc() {
  ipcMain.handle("appforge:auth:get", () => readAuthToken());
  ipcMain.handle("appforge:auth:set", (_event, token) => writeAuthToken(token));
  ipcMain.handle("appforge:auth:clear", () => clearEncryptedFile(AUTH_TOKEN_FILE));

  ipcMain.handle("appforge:store:get", (_event, key) => storeGet(key));
  ipcMain.handle("appforge:store:set", (_event, key, value) => storeSet(key, value));
  ipcMain.handle("appforge:store:remove", (_event, key) => storeRemove(key));

  ipcMain.handle("appforge:keystore:save", (_event, payload) => saveKeystore(payload));
  ipcMain.handle("appforge:keystore:get", () => getKeystore());
  ipcMain.handle("appforge:keystore:clear", () => clearKeystore());

  ipcMain.handle("appforge:local-ai:chat", (_event, payload) => localAiChat(payload));
}

function contentType(file) {
  const extension = path.extname(file).toLowerCase();
  return {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".svg": "image/svg+xml",
    ".ttf": "font/ttf",
    ".woff": "font/woff",
    ".woff2": "font/woff2"
  }[extension] || "application/octet-stream";
}

function safeAsset(root, relativePath) {
  const candidate = path.resolve(root, relativePath.replace(/^\/+/, ""));
  const normalizedRoot = path.resolve(root);
  return candidate === normalizedRoot || candidate.startsWith(`${normalizedRoot}${path.sep}`) ? candidate : null;
}

function startLocalStudio() {
  const staticAssets = assets();
  localServer = http.createServer((request, response) => {
    const parsed = new URL(request.url || "/", "http://127.0.0.1");
    const pathname = decodeURIComponent(parsed.pathname);
    let root;
    let relativePath;

    if (pathname === "/" || pathname === "/studio" || pathname === "/studio/") {
      root = staticAssets.studio;
      relativePath = "index.html";
    } else if (pathname.startsWith("/studio/")) {
      root = staticAssets.studio;
      relativePath = pathname.slice("/studio/".length);
    } else if (pathname.startsWith("/vendor/monaco/")) {
      root = staticAssets.monaco;
      relativePath = pathname.slice("/vendor/monaco/".length);
    } else if (pathname.startsWith("/vendor/fflate/")) {
      root = staticAssets.fflate;
      relativePath = pathname.slice("/vendor/fflate/".length);
    }

    const file = root && safeAsset(root, relativePath || "");
    if (!file) {
      response.writeHead(404).end("Not found");
      return;
    }

    fs.readFile(file, (error, content) => {
      if (error) {
        response.writeHead(error.code === "ENOENT" ? 404 : 500).end("Not found");
        return;
      }
      response.writeHead(200, {
        "Content-Type": contentType(file),
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff"
      });
      response.end(content);
    });
  });

  return new Promise((resolve, reject) => {
    localServer.once("error", reject);
    localServer.listen(0, "127.0.0.1", () => {
      const address = localServer.address();
      resolve(`http://127.0.0.1:${address.port}/studio/`);
    });
  });
}

async function createWindow() {
  const url = await startLocalStudio();
  const window = new BrowserWindow({
    width: 1540,
    height: 960,
    minWidth: 980,
    minHeight: 680,
    backgroundColor: "#07101f",
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  await window.loadURL(url);
}

app.whenReady().then(async () => {
  app.setAppUserModelId(APP_USER_MODEL_ID);
  registerDesktopIpc();
  await createWindow();
});

app.on("window-all-closed", () => app.quit());
app.on("before-quit", () => localServer?.close());
