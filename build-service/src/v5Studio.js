import AdmZip from "adm-zip";

const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]*$/;
const SAFE_SOURCE_PATH = /^(?!\/)(?!.*(?:^|\/)\.\.(?:\/|$))[A-Za-z0-9_.\-/ ]+$/;

export function bumpVersion(versionName = "1.0.0", versionCode = 1) {
  const parts = String(versionName).trim().split(".").map(Number);
  const normalized = parts.length === 3 && parts.every(Number.isInteger)
    ? parts
    : [1, 0, 0];
  normalized[2] += 1;
  return {
    versionName: normalized.join("."),
    versionCode: Math.max(1, Number.parseInt(versionCode, 10) || 1) + 1
  };
}

function safeName(value, fallback) {
  const candidate = String(value || "").trim();
  if (!IDENTIFIER.test(candidate)) return fallback;
  return candidate;
}

function json(value) {
  return JSON.stringify(value, null, 2);
}

function html(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

export function createV5Scaffold(input = {}) {
  const appName = String(input.appName || "AppForge V5 App").trim();
  const entity = safeName(input.entity, "items").toLowerCase();
  const title = String(input.screenTitle || appName).trim();
  const safeTitle = html(title);
  const fields = Array.isArray(input.fields) && input.fields.length
    ? input.fields.map((field) => safeName(field, "value"))
    : ["title", "description", "completed"];
  const auth = input.auth !== false;
  const notifications = input.notifications !== false;
  const database = input.database || "sqlite";
  const backend = input.backend || "node-express";
  const version = input.autoVersion
    ? bumpVersion(input.versionName, input.versionCode)
    : {
        versionName: String(input.versionName || "1.0.0"),
        versionCode: Math.max(1, Number.parseInt(input.versionCode, 10) || 1)
      };

  const schema = {
    entity,
    database,
    fields: fields.map((name) => ({
      name,
      type: name === "completed" ? "boolean" : "text",
      required: name !== "description"
    }))
  };
  const sqlType = (field) => field.type === "boolean" ? "BOOLEAN" : "TEXT";
  const schemaSql = `CREATE TABLE IF NOT EXISTS ${entity} (\n  id TEXT PRIMARY KEY,\n${schema.fields.map((field) => `  ${field.name} ${sqlType(field)}${field.required ? " NOT NULL" : ""}`).join(",\n")},\n  created_at TEXT NOT NULL\n);\n`;
  const files = {
    "index.html": `<!doctype html>\n<html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${safeTitle}</title><link rel="stylesheet" href="style.css"></head><body><main><header><div><small>AppForge Studio V5</small><h1>${safeTitle}</h1></div><button id="theme">Tema</button></header>${auth ? '<section id="auth" class="card"><h2>Giriş</h2><input id="email" type="email" placeholder="E-posta"><button id="login">Devam et</button></section>' : ""}<section class="card"><form id="create"><input id="title" required placeholder="Yeni kayıt"><button>Ekle</button></form><div id="list"></div></section>${notifications ? '<button id="notify" class="floating">Bildirimleri aç</button>' : ""}</main><script src="app.js"></script></body></html>`,
    "style.css": `:root{font-family:Inter,system-ui;color:#eef3ff;background:#07101f}*{box-sizing:border-box}body{margin:0}main{max-width:760px;margin:auto;padding:24px}header,form,.item{display:flex;gap:12px;align-items:center;justify-content:space-between}.card{background:#111d31;border:1px solid #293c5d;border-radius:20px;padding:18px;margin:16px 0}input,button{border:0;border-radius:12px;padding:12px}input{flex:1}button{background:#7d8cff;color:#fff;font-weight:700}.item{padding:12px 0;border-bottom:1px solid #263650}.floating{position:fixed;right:24px;bottom:24px}@media(max-width:520px){main{padding:14px}header{align-items:flex-start}form{flex-direction:column}input,form button{width:100%}}`,
    "app.js": `const key="appforge-v5-${entity}";let rows=JSON.parse(localStorage.getItem(key)||"[]");const list=document.querySelector("#list");const esc=value=>String(value).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));function render(){list.innerHTML=rows.map((row,i)=>\`<div class="item"><span>\${esc(row.title)}</span><button data-i="\${i}">Sil</button></div>\`).join("")||"<p>Henüz kayıt yok.</p>";localStorage.setItem(key,JSON.stringify(rows))}document.querySelector("#create").addEventListener("submit",e=>{e.preventDefault();const input=document.querySelector("#title");rows.unshift({id:crypto.randomUUID(),title:input.value,createdAt:new Date().toISOString()});input.value="";render()});list.addEventListener("click",e=>{if(e.target.dataset.i!=null){rows.splice(Number(e.target.dataset.i),1);render()}});document.querySelector("#theme").onclick=()=>document.documentElement.style.filter=document.documentElement.style.filter?"":"invert(.9) hue-rotate(180deg)";${auth ? 'document.querySelector("#login").onclick=()=>localStorage.setItem("appforge-session",document.querySelector("#email").value);' : ""}${notifications ? 'document.querySelector("#notify").onclick=async()=>{if("Notification" in window)await Notification.requestPermission()};' : ""}render();`,
    "appforge.v5.json": json({
      version: 5,
      appName,
      versioning: { ...version, autoVersion: Boolean(input.autoVersion) },
      ui: { builder: "responsive-cards", livePreview: true, theme: true },
      schema,
      backend,
      auth: auth ? { providers: ["email"], session: "local-first" } : null,
      notifications: notifications ? { webPushReady: true, topics: ["all"] } : null,
      publishing: { android: ["apk", "aab"], windows: ["setup", "portable"], web: true }
    }),
    "backend/server.js": `import express from "express";\nconst app=express();app.use(express.json());const rows=[];app.get("/api/${entity}",(_req,res)=>res.json({${entity}:rows}));app.post("/api/${entity}",(req,res)=>{const row={id:crypto.randomUUID(),...req.body};rows.push(row);res.status(201).json({${entity.slice(0, -1) || "item"}:row})});app.delete("/api/${entity}/:id",(req,res)=>{const i=rows.findIndex(x=>x.id===req.params.id);if(i>=0)rows.splice(i,1);res.status(204).end()});app.listen(process.env.PORT||3000);`,
    "backend/package.json": json({
      name: `${entity}-api`, private: true, type: "module",
      scripts: { start: "node server.js" },
      dependencies: { express: "^5.1.0" }
    }),
    "database/schema.json": json(schema),
    "database/schema.sql": schemaSql
  };

  if (input.sourceZipBase64) {
    const archive = Buffer.from(String(input.sourceZipBase64), "base64");
    if (archive.length > 1_000_000) {
      throw new Error("V5 ZIP kaynağı 1 MB sınırını aşıyor.");
    }
    const zip = new AdmZip(archive);
    for (const entry of zip.getEntries()) {
      const sourcePath = entry.entryName.replaceAll("\\", "/");
      if (entry.isDirectory || !SAFE_SOURCE_PATH.test(sourcePath)) continue;
      if (!/\.(?:html?|css|js|mjs|json)$/i.test(sourcePath)) continue;
      if (entry.header.size > 300_000) continue;
      files[sourcePath] = entry.getData().toString("utf8");
    }
  }

  return {
    config: {
      appName,
      entity,
      backend,
      database,
      auth,
      notifications,
      ...version
    },
    files
  };
}
