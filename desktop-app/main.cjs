const { app, BrowserWindow } = require("electron");
const http = require("http");
const fs = require("fs");
const path = require("path");

let localServer;

function assets() {
  if (app.isPackaged) {
    return {
      studio: path.join(process.resourcesPath, "public", "studio"),
      monaco: path.join(process.resourcesPath, "public", "vendor", "monaco")
    };
  }
  const root = path.join(__dirname, "..", "build-service");
  return {
    studio: path.join(root, "public", "studio"),
    monaco: path.join(root, "node_modules", "monaco-editor", "min", "vs")
  };
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
  return candidate.startsWith(`${path.resolve(root)}${path.sep}`) ? candidate : null;
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
    width: 1440,
    height: 920,
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

app.whenReady().then(createWindow);
app.on("window-all-closed", () => app.quit());
app.on("before-quit", () => localServer?.close());
