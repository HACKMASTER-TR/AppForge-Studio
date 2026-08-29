(() => {
"use strict";

const $ = id => document.getElementById(id);
const qs = (sel, root=document) => root.querySelector(sel);
const qsa = (sel, root=document) => [...root.querySelectorAll(sel)];
const sleep = ms => new Promise(r => setTimeout(r, ms));
const textDecoder = new TextDecoder();
const textEncoder = new TextEncoder();

const desktop = window.AppForgeDesktop || null;
const desktopAuth = desktop?.auth || null;
const desktopStore = desktop?.store || null;
const desktopKeystore = desktop?.keystore || null;
const desktopLocalAi = desktop?.localAi || null;

let token = desktopAuth ? "" : (localStorage.getItem("afs_jwt") || "");
let challengeToken = "";
let currentUser = null;
let editor = null;
let activeProjectId = null;
let activeProject = null;
let activeTeamId = null;
let activeFile = "index.html";
let dirty = false;
let autosaveTimer = null;
let liveLogAbort = null;
let lastLiveLogId = 0;
let templateCache = [];
let templateCategory = "Tümü";
let builderStep = 1;
let lastBuilds = [];
let sourceAnalysis = null;
let analyzedSourceFiles = null;
let pendingProjectFile = null;
let processedIconFile = null;
let cachedKeystoreFile = null;
let conversionTarget = null;
let trashCache = [];
let previewConsole = [];
let previewNetwork = [];
let previewPerformance = [];
let studioSettings = {theme:"system", language:"tr", accent:"#6172ff"};
let aiHistory = [];
let backgroundBuilds = {};
let backgroundBuildPoll = null;

let files = starterFiles("blank", "AppForge App");

const standaloneWeb = location.hostname.endsWith(".github.io");
const desktopLoopback = location.hostname === "127.0.0.1" || location.hostname === "localhost";
const desktopApiBase = desktop?.apiBaseUrl || "";
if (desktopApiBase) $("baseUrl").value = desktopApiBase;
else if (standaloneWeb || desktopLoopback) $("baseUrl").value = "https://appforge-studio-production.up.railway.app";

function esc(v){
  return String(v ?? "")
    .replaceAll("&","&amp;")
    .replaceAll("<","&lt;")
    .replaceAll(">","&gt;")
    .replaceAll('"',"&quot;");
}
function pretty(v){ return JSON.stringify(v, null, 2); }
function safeFileName(v){
  return String(v || "appforge").replace(/[^\w.-]+/g,"-").replace(/-+/g,"-").slice(0,80) || "appforge";
}
function base(){
  const value = $("baseUrl").value.trim();
  return (value || location.origin).replace(/\/$/,"");
}
function bool(id){ return Boolean($(id)?.checked); }
function val(id){ return $(id)?.value ?? ""; }
function intVal(id, fallback=0){ const n=Number(val(id)); return Number.isFinite(n)?Math.trunc(n):fallback; }
function setText(id, text){ const el=$(id); if(el) el.textContent=text; }
function downloadBlob(blob, name){
  const a=document.createElement("a");
  a.href=URL.createObjectURL(blob);
  a.download=name;
  document.body.appendChild(a);
  a.click();
  setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},1000);
}

async function pGet(key, fallback=null){
  if(desktopStore?.get){
    try{
      const value=await desktopStore.get(key);
      return value == null ? fallback : value;
    }catch{}
  }
  try{
    const raw=localStorage.getItem("afs_v4_"+key);
    return raw == null ? fallback : JSON.parse(raw);
  }catch{return fallback}
}
async function pSet(key, value){
  if(desktopStore?.set){
    try{await desktopStore.set(key,value);return}catch(e){console.warn(e)}
  }
  try{localStorage.setItem("afs_v4_"+key,JSON.stringify(value))}catch{}
}
async function pRemove(key){
  if(desktopStore?.remove){
    try{await desktopStore.remove(key);return}catch{}
  }
  localStorage.removeItem("afs_v4_"+key);
}

async function persistAuthToken(value){
  token=value || "";
  if(desktopAuth?.setToken){
    try{
      await desktopAuth.setToken(token);
      localStorage.removeItem("afs_jwt");
      return true;
    }catch(e){
      // A successful login must remain usable even when Windows safeStorage
      // is temporarily unavailable. Keep the token only in renderer memory.
      console.warn("Desktop secure token persistence failed",e);
      localStorage.removeItem("afs_jwt");
      return false;
    }
  }else if(token){
    localStorage.setItem("afs_jwt",token);
  }else{
    localStorage.removeItem("afs_jwt");
  }
  return true;
}
async function clearAuthToken(){
  token="";
  if(desktopAuth?.clearToken){
    try{await desktopAuth.clearToken()}catch(e){console.warn("Desktop auth clear failed",e)}
  }
  localStorage.removeItem("afs_jwt");
}
async function restorePersistedToken(){
  if(desktopAuth?.getToken){
    try{token=await desktopAuth.getToken() || ""}catch(e){console.warn("Desktop auth restore failed",e);token=""}
  }
  return token;
}
function showAuthenticatedApp(){
  $("authCard").classList.add("hidden");
  $("app").classList.remove("hidden");
  $("logoutTopBtn").hidden=false;
}
async function resumeSession(){
  await restorePersistedToken();
  await loadSettings();
  await loadTrash();
  if(!token)return;
  try{
    const j=await api("/api/auth/me");
    currentUser=j.user;
    showAuthenticatedApp();
    await postLoginLoad();
  }catch(e){
    await clearAuthToken();
    setText("authMsg","Oturum süresi doldu. Tekrar giriş yapın.");
  }
}

async function api(path, opts={}){
  const headers={...(opts.headers||{}),Accept:"application/json"};
  if(token)headers.Authorization="Bearer "+token;
  const request={...opts,headers};
  if(request.body && typeof request.body!=="string" && !(request.body instanceof FormData) && !(request.body instanceof Blob)){
    headers["Content-Type"]="application/json";
    request.body=JSON.stringify(request.body);
  }
  const r=await fetch(base()+path,request);
  const text=await r.text();
  let data={};
  try{data=text?JSON.parse(text):{}}catch{data={raw:text}}
  if(!r.ok)throw new Error(data.error || data.detail || text || `HTTP ${r.status}`);
  return data;
}
async function apiForm(path, form, opts={}){
  const headers={...(opts.headers||{}),Accept:"application/json"};
  if(token)headers.Authorization="Bearer "+token;
  const r=await fetch(base()+path,{method:opts.method||"POST",headers,body:form});
  const text=await r.text();
  let data={};
  try{data=text?JSON.parse(text):{}}catch{data={raw:text}}
  if(!r.ok)throw new Error(data.error || data.detail || text || `HTTP ${r.status}`);
  return data;
}

window.login=async function login(){
  try{
    const j=await api("/api/auth/login",{method:"POST",body:{email:val("email"),password:val("password")}});
    if(j.requiresTwoFactor){
      challengeToken=j.challengeToken;
      $("twoFactorBox").classList.remove("hidden");
      return;
    }
    await finishLogin(j);
  }catch(e){setText("authMsg",e.message)}
};
window.verify2fa=async function verify2fa(){
  try{
    await finishLogin(await api("/api/auth/2fa/verify-login",{method:"POST",body:{challengeToken,code:val("twoFactorCode")}}));
  }catch(e){setText("authMsg",e.message)}
};
window.register=async function register(){
  try{
    await finishLogin(await api("/api/auth/register",{method:"POST",body:{email:val("email"),password:val("password"),displayName:val("email").split("@")[0]}}));
  }catch(e){setText("authMsg",e.message)}
};
window.forgotPassword=async function forgotPassword(){
  const mail=val("email").trim();
  if(!mail){setText("authMsg","Önce e-posta adresini yaz.");return}
  try{
    const j=await api("/api/auth/forgot-password",{method:"POST",body:{email:mail}});
    setText("authMsg",j.message || "İstek gönderildi.");
  }catch(e){setText("authMsg",e.message)}
};
async function finishLogin(j){
  const persisted=j?.token ? await persistAuthToken(j.token) : true;
  currentUser=j.user || null;
  showAuthenticatedApp();
  if(!persisted){
    setText("autosaveStatus","Oturum geçici");
    $("autosaveStatus").className="status yellow";
  }
  await postLoginLoad();
}
window.logout=async function logout(){
  stopLiveLogs();
  stopBackgroundBuildMonitor();
  await clearAuthToken();
  currentUser=null;
  $("app").classList.add("hidden");
  $("authCard").classList.remove("hidden");
  $("logoutTopBtn").hidden=true;
  $("password").value="";
};
async function postLoginLoad(){
  backgroundBuilds=await pGet("backgroundBuilds",{});
  await Promise.allSettled([loadMe(),loadProjects(),loadBuilds(),loadTeams(),loadTemplates(),loadProStatus(),loadApiTokens()]);
  startBackgroundBuildMonitor();
  updateHome();
  renderTree();
  preview();
  updateProjectBadges();
}

function backgroundBuildMessage(build){
  const name=build.appName || "Build";
  if(build.status==="success")return `✓ ${name} hazır`;
  if(build.status==="failed")return `✕ ${name} başarısız`;
  if(build.status==="cancelled")return `— ${name} iptal edildi`;
  return `⚙ ${name} arka planda derleniyor • %${build.progress||0}`;
}
function notifyBackgroundBuild(build, previousStatus){
  if(!previousStatus || !["queued","building"].includes(previousStatus) || !["success","failed","cancelled"].includes(build.status))return;
  if(document.hidden && "Notification" in window && Notification.permission==="granted")new Notification("AppForge Studio",{body:backgroundBuildMessage(build)});
}
function updateBackgroundBuildStatus(){
  const button=$("backgroundBuildStatus");
  if(!button)return;
  const tracked=lastBuilds.filter(build=>backgroundBuilds[build.buildId]);
  const active=tracked.filter(build=>["queued","building"].includes(build.status));
  const latest=active[0] || tracked[0];
  button.classList.toggle("hidden",!latest);
  button.classList.remove("building","success","failed");
  if(!latest)return;
  button.textContent=backgroundBuildMessage(latest);
  button.classList.add(["queued","building"].includes(latest.status)?"building":latest.status==="success"?"success":"failed");
}
async function trackBackgroundBuild(buildId){
  if(!buildId)return;
  backgroundBuilds[buildId] ||= {status:"queued"};
  await pSet("backgroundBuilds",backgroundBuilds);
  updateBackgroundBuildStatus();
  startBackgroundBuildMonitor();
}
function startBackgroundBuildMonitor(){
  if(backgroundBuildPoll || !token)return;
  backgroundBuildPoll=setInterval(()=>{if(Object.keys(backgroundBuilds).length)loadBuilds()},15000);
}
function stopBackgroundBuildMonitor(){
  if(backgroundBuildPoll){clearInterval(backgroundBuildPoll);backgroundBuildPoll=null}
}

function wireNavigation(){
  qsa(".nav-item").forEach(btn=>btn.addEventListener("click",()=>showPanel(btn.dataset.panel,btn)));
}

function featureHelpText(label){
  const text=String(label||"").replace(/\s+/g," ").trim();
  const key=text.toLocaleLowerCase("tr-TR");
  const hints=[
    [/uygulama adı|proje adı/,"Uygulamanın kullanıcıya görünen adını belirler; proje listesinde ve oluşturulan pakette kullanılır."],
    [/paket adı/,"Android uygulamasının benzersiz kimliğidir. Yayınlandıktan sonra değiştirmek yeni bir uygulama olarak değerlendirilir."],
    [/html veya zip|zip proje|kaynak proje|başlangıç türü/,"Uygulamanın içerik kaynağını seçer. HTML doğrudan açılır, ZIP ise dosyaları analiz edilerek projeye aktarılır."],
    [/çıktı|apk|aab|windows exe/,"Hangi platform paketi üretileceğini seçer. APK cihaz testi, AAB Play Store, EXE ise Windows kurulumu içindir."],
    [/otomatik sürüm|sürüm adı|sürüm kodu/,"Yeni buildlerde sürüm bilgisini yönetir. Otomatik seçenek, yayın için gerekli Android sürüm kodunu artırır."],
    [/tema|renk|splash|ikon|görünüm/,"Uygulamanın marka görünümünü değiştirir; önizlemede ve üretilen pakette uygulanır."],
    [/izin|kamera|mikrofon|konum|bildirim|nfc|wake|ağ/,"Yalnız kullandığınız cihaz özelliği için açın. Gereksiz izinler kullanıcı güvenini ve mağaza incelemesini olumsuz etkileyebilir."],
    [/firebase|analytics|crashlytics|messaging/,"Firebase hizmetini projeye bağlar. Etkinleştirmeden önce doğru google-services.json dosyasını ekleyin."],
    [/admob|reklam|ump/,"Reklam ve kullanıcı onayı yapılandırmasını ekler. Yayına çıkmadan önce gerçek reklam birim kimliklerini kullanın."],
    [/billing|satın alma|ürün id|abonelik/,"Google Play üzerinden ücretli ürün veya abonelik sunmak için ürün kimliklerini tanımlar."],
    [/native bridge|bridge|paylaşım|pano|titreşim|medya|qr/,"Web içeriğinin güvenli şekilde cihaz özelliklerini kullanmasını sağlar. Uzak web sitelerinde yalnız güvenilir HTTPS kaynakları için açın."],
    [/deep link|bağlantı/,"Uygulamayı belirli bir bağlantıdan açmak için URL şemasını ve yönlendirme kurallarını tanımlar."],
    [/imzalama|keystore|anahtar|alias/,"Yayın paketinizi doğrulayan imza ayarını belirler. Aynı uygulama güncellemelerinde aynı anahtarı koruyun."],
    [/sunucu url|api anahtarı|token/,"Studio'nun build ve proje servisleriyle güvenli iletişim kurmasını sağlar. Gizli anahtarları paylaşmayın."],
    [/dil/,"Studio arayüz dilini değiştirir; proje dosyalarınızı veya build ayarlarınızı değiştirmez."],
    [/açık|koyu|sistem/,"Arayüz görünümünü belirler. Sistem seçeneği cihazın açık/koyu tema tercihine uyar."],
    [/github/,"Projeyi GitHub deposuyla ilişkilendirir veya bir depodan kaynak içe aktarır."],
    [/takım|team/,"Projeye erişebilecek çalışma alanını seçer. Yetkiler ekip rolüne göre uygulanır."],
    [/parola|2fa|doğrulama/,"Hesap güvenliğini yönetir. 2FA açıldığında girişte doğrulama kodu istenir."]
  ];
  return hints.find(([pattern])=>pattern.test(key))?.[1] || `${text} ayarını belirler; seçimin proje yapılandırmasına kaydedilir.`;
}

function addFeatureHelp(){
  qsa("label").forEach(label=>{
    if(label.dataset.helpReady==="true" || !label.querySelector("input,textarea,select"))return;
    const copy=label.cloneNode(true);
    qsa("input,textarea,select,.field-help",copy).forEach(node=>node.remove());
    const title=copy.textContent.trim() || "Bu alan";
    const help=document.createElement("p");
    help.className="field-help";
    help.textContent=featureHelpText(title);
    label.appendChild(help);
    label.dataset.helpReady="true";
  });
}

window.showPanel=function showPanel(id,button=null){
  const nextPanel=$(id);
  qsa(".panel").forEach(x=>x.classList.add("hidden"));
  if(nextPanel){
    nextPanel.classList.remove("hidden","panel-enter");
    // Restart the entrance animation when the user changes section quickly.
    void nextPanel.offsetWidth;
    nextPanel.classList.add("panel-enter");
  }
  qsa(".nav-item").forEach(x=>x.classList.toggle("active",x.dataset.panel===id));
  if(button)button.classList.add("active");
  if(id==="projectsPanel")loadProjects();
  if(id==="historyPanel")loadRevisions();
  if(id==="buildsPanel")loadBuilds();
  if(id==="teamsPanel")loadTeams();
  if(id==="securityPanel"){loadMe();updateDesktopSecurityState()}
  if(id==="templatesPanel")loadTemplates();
  if(id==="productionPanel")loadProduction();
  if(id==="testLabPanel")populateBuildSelectors();
  if(id==="trashPanel")loadTrash();
  if(id==="localizationPanel")loadLocalizationsUi();
  if(id==="accountPanel"){loadMe();loadProStatus();loadApiTokens()}
  if(id==="previewPanel")renderLabPreview();
  if(id==="aiPanel")updateAiProjectLabel();
};

function lang(path){
  const e=path.split(".").pop().toLowerCase();
  if(e==="html")return"html";
  if(e==="css")return"css";
  if(["js","mjs","cjs","ts","tsx","jsx"].includes(e))return"javascript";
  if(e==="json")return"json";
  if(["kt","kts"].includes(e))return"kotlin";
  if(e==="java")return"java";
  if(e==="py")return"python";
  if(["c","h","cpp","hpp","cc"].includes(e))return"cpp";
  if(["cs"].includes(e))return"csharp";
  if(["xml"].includes(e))return"xml";
  if(["dart"].includes(e))return"dart";
  return"plaintext";
}
function loadMonacoLoader(){
  return new Promise((resolve,reject)=>{
    if(window.require?.config){resolve();return}
    const script=document.createElement("script");
    script.src=window.APPFORGE_MONACO_ROOT+"/loader.js";
    script.async=true;
    script.onload=resolve;
    script.onerror=()=>reject(new Error("Monaco loader yüklenemedi."));
    document.head.appendChild(script);
  });
}
function initMonaco(){
  window.require.config({paths:{vs:window.APPFORGE_MONACO_ROOT}});
  window.require(["vs/editor/editor.main"],()=>{
    editor=monaco.editor.create($("monaco"),{
      value:files[activeFile] ?? "",
      language:lang(activeFile),
      theme:document.body.classList.contains("light")?"vs":"vs-dark",
      automaticLayout:true,
      minimap:{enabled:false},
      fontSize:14,
      wordWrap:"on"
    });
    editor.onDidChangeModelContent(()=>{
      files[activeFile]=editor.getValue();
      dirty=true;
      setText("autosaveStatus","Değişiklik var");
      $("autosaveStatus").className="status yellow";
      preview();
      scheduleAutosave();
    });
    renderTree();
    preview();
  });
}
function switchFile(path){
  if(editor){
    files[activeFile]=editor.getValue();
    activeFile=path;
    monaco.editor.setModelLanguage(editor.getModel(),lang(path));
    editor.setValue(files[path] ?? "");
  }else activeFile=path;
  renderTree();preview();
}
function renderTree(){
  const tree=$("fileTree"); if(!tree)return;
  tree.innerHTML="";
  Object.keys(files).sort().forEach(p=>{
    const d=document.createElement("div");
    d.className="file"+(p===activeFile?" active":"");
    d.textContent=p;
    d.onclick=()=>switchFile(p);
    tree.appendChild(d);
  });
  $("diffFileSelect").innerHTML=Object.keys(files).sort().map(p=>`<option>${esc(p)}</option>`).join("");
}
window.newFile=function newFile(){
  const p=prompt("Dosya yolu (örn. pages/about.html):");
  if(!p||files[p]!=null)return;
  files[p]="";switchFile(p);dirty=true;scheduleAutosave();
};
window.deleteActiveFile=async function deleteActiveFile(){
  if(!confirm(activeFile+" silinsin mi?"))return;
  if(activeProjectId){
    try{await api(`/api/projects/${activeProjectId}/files?path=${encodeURIComponent(activeFile)}`,{method:"DELETE"})}
    catch(e){alert(e.message);return}
  }
  delete files[activeFile];
  activeFile=Object.keys(files)[0]||"index.html";
  if(files[activeFile]==null)files[activeFile]="";
  switchFile(activeFile);
};
window.searchWorkspace=async function searchWorkspace(){
  const q=val("workspaceSearch").trim();
  if(!activeProjectId){$("searchResults").innerHTML="<span class='muted'>Önce proje aç.</span>";return}
  if(!q){$("searchResults").innerHTML="";return}
  try{
    const j=await api(`/api/projects/${activeProjectId}/search?q=${encodeURIComponent(q)}`);
    $("searchResults").innerHTML=j.results.map(r=>`<div class="file" data-path="${esc(r.path)}">${esc(r.path)}${r.line?":"+r.line:""} ${esc(r.preview||"")}</div>`).join("") || "<span class='muted'>Sonuç yok.</span>";
    qsa("#searchResults .file").forEach(el=>el.onclick=()=>files[el.dataset.path]!=null&&switchFile(el.dataset.path));
  }catch(e){$("searchResults").innerHTML=`<span class="red">${esc(e.message)}</span>`}
};

function scheduleAutosave(){clearTimeout(autosaveTimer);autosaveTimer=setTimeout(()=>autosave(),1800)}
async function autosave(){
  if(!activeProjectId||!dirty)return;
  try{
    if(editor)files[activeFile]=editor.getValue();
    setText("autosaveStatus","Kaydediliyor...");
    $("autosaveStatus").className="status yellow";
    await Promise.all(Object.entries(files).map(([path,content])=>api(`/api/projects/${activeProjectId}/files`,{method:"PUT",body:{path,content,autosave:true}})));
    dirty=false;setText("autosaveStatus","Autosave ✓");$("autosaveStatus").className="status green";
  }catch(e){setText("autosaveStatus","Autosave hata: "+e.message);$("autosaveStatus").className="status red"}
}
async function autosaveForce(){
  if(!activeProjectId)throw new Error("Önce projeyi kaydet.");
  if(editor)files[activeFile]=editor.getValue();
  await Promise.all(Object.entries(files).map(([path,content])=>api(`/api/projects/${activeProjectId}/files`,{method:"PUT",body:{path,content,autosave:true}})));
  dirty=false;setText("autosaveStatus","Kaydedildi ✓");$("autosaveStatus").className="status green";
}
function defaultConfig(){
  return {
    appName:"",
    packageName:"com.example.myapp",
    sourceMode:"LOCAL",
    sourceTechnology:"web-static",
    sourceTechnologyLabel:"HTML / CSS / JavaScript",
    sourceBuildEngine:"webview-static",
    sourceBuildReady:true,
    webUrl:"",
    versionName:"1.0.0",
    versionCode:1,
    autoVersionCode:false,
    buildOutput:"both",
    orientation:"unspecified",
    primaryColor:"#6B7CFF",
    backgroundColor:"#07101F",
    statusBarColor:"#07101F",
    navigationBarColor:"#07101F",
    splashEnabled:true,
    splashText:"",
    appCategory:"auto",
    signingMode:"DEBUG",
    fileUpload:true,downloads:true,fullscreen:false,notifications:false,camera:false,microphone:false,location:false,
    networkState:true,wakeLock:false,nfc:false,additionalPermissions:[],offlineCache:true,
    webJavaScriptEnabled:true,webDomStorageEnabled:true,webZoomEnabled:true,webWideViewPortEnabled:true,
    webOverviewModeEnabled:true,webMediaAutoplayEnabled:true,webMixedContentAllowed:false,
    deepLinkEnabled:false,deepLinkScheme:"https",deepLinkHost:"",deepLinkPathPrefix:"/",
    javascriptBridge:true,remoteBridgeAllowed:false,shareBridge:true,clipboardBridge:true,vibrationBridge:true,
    mediaPlayerBridge:false,qrScanner:false,
    admobEnabled:false,admobAppId:"",admobBannerUnitId:"",admobInterstitialUnitId:"",admobRewardedUnitId:"",umpConsentEnabled:false,
    billingEnabled:false,billingProductIds:"",billingSubscriptionIds:"",consumableProductIds:"",removeAdsProductId:"",purchaseVerificationUrl:"",
    firebaseAnalyticsEnabled:false,firebaseCrashlyticsEnabled:false,firebaseMessagingEnabled:false
  };
}
function safeConfigForProject(c){
  const clean=structuredClone(c || {});
  for(const key of ["storePassword","keyPassword","buildApiKey","keystoreUri","firebaseConfigUri"]){
    delete clean[key];
  }
  return clean;
}
function slugPackage(name){
  let s=String(name||"app").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase()
    .replace(/ı/g,"i").replace(/ş/g,"s").replace(/ğ/g,"g").replace(/ü/g,"u").replace(/ö/g,"o").replace(/ç/g,"c")
    .replace(/[^a-z0-9]+/g,"").slice(0,28);
  if(!s)s="app";
  if(/^\d/.test(s))s="app"+s;
  return "com.appforge."+s;
}
function starterFiles(slug="blank",name="AppForge App"){
  const safeName=String(name||"AppForge App");
  const templates={
    "task-manager":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><main><h1>${safeName}</h1><form id="f"><input id="t" placeholder="Yeni görev"><button>Ekle</button></form><ul id="list"></ul></main><script src="app.js"><\/script></body></html>`,
      "style.css":"body{font-family:system-ui;margin:0;background:#07101f;color:#fff}main{max-width:720px;margin:auto;padding:24px}form{display:flex;gap:8px}input,button{padding:12px;border-radius:10px;border:0}input{flex:1}li{padding:12px;background:#13213a;margin:8px 0;border-radius:10px}",
      "app.js":"const list=document.querySelector('#list'),f=document.querySelector('#f'),t=document.querySelector('#t');const data=JSON.parse(localStorage.tasks||'[]');function r(){list.innerHTML=data.map((x,i)=>`<li>${x} <button onclick=\"del(${i})\">✓</button></li>`).join('')}window.del=i=>{data.splice(i,1);localStorage.tasks=JSON.stringify(data);r()};f.onsubmit=e=>{e.preventDefault();if(t.value.trim())data.push(t.value.trim());t.value='';localStorage.tasks=JSON.stringify(data);r()};r();"
    },
    "inventory-panel":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><main><h1>${safeName}</h1><section id="cards"></section><button onclick="add()">+ Ürün</button></main><script src="app.js"><\/script></body></html>`,
      "style.css":"body{font-family:system-ui;background:#f5f7fb;margin:0}main{max-width:900px;margin:auto;padding:24px}#cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}.c{background:white;padding:16px;border-radius:14px;box-shadow:0 4px 16px #0001}button{padding:10px 14px}",
      "app.js":"let items=[['Freze',12],['Matkap',8],['Uç',42]];function r(){cards.innerHTML=items.map(x=>`<div class=c><b>${x[0]}</b><p>Stok: ${x[1]}</p></div>`).join('')}window.add=()=>{const n=prompt('Ürün adı');if(n)items.push([n,0]);r()};r();"
    },
    "booking-form":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><form><h1>${safeName}</h1><input placeholder="Ad Soyad"><input type="date"><input type="time"><textarea placeholder="Not"></textarea><button>Rezervasyon Oluştur</button></form></body></html>`,
      "style.css":"body{font-family:system-ui;background:#07101f;color:white;display:grid;place-items:center;min-height:100vh}form{width:min(92vw,480px);display:grid;gap:10px;background:#101b2e;padding:24px;border-radius:18px}input,textarea,button{padding:12px;border-radius:10px;border:0}",
      "app.js":""
    },
    "restaurant-menu":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><header><h1>${safeName}</h1></header><main id="menu"></main><script src="app.js"><\/script></body></html>`,
      "style.css":"body{font-family:system-ui;margin:0;background:#fff8ef;color:#332419}header{padding:30px;text-align:center}main{max-width:760px;margin:auto;padding:16px}.item{display:flex;justify-content:space-between;border-bottom:1px solid #dbcbbd;padding:16px}",
      "app.js":"const items=[['Karışık Menü','₺350'],['Burger Menü','₺240'],['Tatlı','₺120']];menu.innerHTML=items.map(x=>`<div class=item><b>${x[0]}</b><span>${x[1]}</span></div>`).join('');"
    },
    "event-invitation":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><main><p>DAVETLİSİNİZ</p><h1>${safeName}</h1><h2>28 Ağustos • 20:00</h2><button>Katılım Bildir</button></main></body></html>`,
      "style.css":"body{font-family:Georgia,serif;margin:0;display:grid;place-items:center;min-height:100vh;background:#efe7dc;color:#382b25;text-align:center}main{padding:40px}h1{font-size:clamp(40px,9vw,88px)}button{padding:12px 20px;border:1px solid #382b25;background:transparent}",
      "app.js":""
    },
    "visual-designer":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><aside>Blocks</aside><main id="canvas"><h1>${safeName}</h1><p>Düzenlenebilir tasarım alanı</p></main></body></html>`,
      "style.css":"body{font-family:system-ui;margin:0;display:grid;grid-template-columns:180px 1fr;min-height:100vh}aside{padding:20px;background:#101b2e;color:white}main{padding:40px}",
      "app.js":""
    },
    "personnel-tracker":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><main><h1>${safeName}</h1><table><thead><tr><th>Personel</th><th>Durum</th></tr></thead><tbody id="rows"></tbody></table></main><script src="app.js"><\/script></body></html>`,
      "style.css":"body{font-family:system-ui;background:#f4f6fa}main{max-width:900px;margin:auto;padding:24px;background:white}table{width:100%;border-collapse:collapse}td,th{padding:12px;border-bottom:1px solid #ddd}",
      "app.js":"const people=[['Ayşe','Aktif'],['Mehmet','İzinli'],['Ali','Aktif']];rows.innerHTML=people.map(x=>`<tr><td>${x[0]}</td><td>${x[1]}</td></tr>`).join('');"
    },
    "qr-menu":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><main><h1>${safeName}</h1><p>QR menü başlangıç projesi.</p><section id="items"></section></main><script src="app.js"><\/script></body></html>`,
      "style.css":"body{font-family:system-ui;background:#111;color:#fff}main{max-width:700px;margin:auto;padding:24px}.item{padding:16px;border-bottom:1px solid #333}",
      "app.js":"items.innerHTML=['Kahve','Çay','Tatlı'].map(x=>`<div class=item>${x}</div>`).join('')"
    },
    "education-quiz":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><main><h1>${safeName}</h1><div id="q"></div></main><script src="app.js"><\/script></body></html>`,
      "style.css":"body{font-family:system-ui;background:#eef4ff}main{max-width:640px;margin:60px auto;background:white;padding:24px;border-radius:18px}button{display:block;width:100%;margin:8px 0;padding:12px}",
      "app.js":"const answers=['Kotlin','HTML','SQL'];q.innerHTML='<h2>Android için hangisi kullanılabilir?</h2>'+answers.map(x=>`<button onclick=\"alert('${x==='Kotlin'?'Doğru':'Tekrar dene'}')\">${x}</button>`).join('')"
    },
    "firebase-login":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><main><h1>${safeName}</h1><input type="email" placeholder="E-posta"><input type="password" placeholder="Parola"><button>Giriş</button><p>Firebase yapılandırmasını Builder → Firebase bölümünden ekle.</p></main></body></html>`,
      "style.css":"body{font-family:system-ui;background:#07101f;color:#fff;display:grid;place-items:center;min-height:100vh}main{width:min(92vw,420px);display:grid;gap:10px}input,button{padding:12px;border-radius:10px;border:0}",
      "app.js":""
    },
    "blank":{
      "index.html":`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"><title>${safeName}</title></head><body><main><h1>${safeName}</h1><p>AppForge Studio V4 ile oluşturuldu.</p><button id="testBtn">Test</button></main><script src="app.js"><\/script></body></html>`,
      "style.css":"body{font-family:system-ui;margin:0;background:#07101f;color:#fff}main{max-width:900px;margin:auto;padding:32px}button{padding:12px 18px;border:0;border-radius:10px;background:#6172ff;color:#fff}",
      "app.js":"document.getElementById('testBtn')?.addEventListener('click',()=>alert('Çalışıyor!'));"
    }
  };
  return structuredClone(templates[slug] || templates.blank);
}

window.setCreateMode=function setCreateMode(mode){
  $("quickCreate").classList.toggle("hidden",mode!=="quick");
  $("advancedCreate").classList.toggle("hidden",mode!=="advanced");
  $("quickModeBtn").classList.toggle("active",mode==="quick");
  $("advancedModeBtn").classList.toggle("active",mode==="advanced");
};
window.syncQuickPackage=function syncQuickPackage(){ $("quickPackage").value=slugPackage(val("quickName")); };
async function applyCreateSource(inputId,name){
  const source=$(inputId).files?.[0];
  if(!source){files=starterFiles(val("quickStarter")||"blank",name);return}
  if(/\.html?$/i.test(source.name)){
    files={"index.html":await source.text(),"style.css":"","app.js":""};
    pendingProjectFile=null;
    return;
  }
  if(!/\.zip$/i.test(source.name))throw new Error("Yalnız HTML veya ZIP seçilebilir.");
  await analyzeZipFile(source);
  if(!analyzedSourceFiles||!Object.keys(analyzedSourceFiles).length)throw new Error("ZIP içinde düzenlenebilir proje dosyası bulunamadı.");
  files=structuredClone(analyzedSourceFiles);
}
window.quickCreateProject=async function quickCreateProject(){
  const name=val("quickName").trim()||"Yeni Uygulama";
  const packageName=val("quickPackage").trim()||slugPackage(name);
  const config={...defaultConfig(),appName:name,packageName,buildOutput:val("quickOutput")||"both",autoVersionCode:bool("quickAutoVersion")};
  await applyCreateSource("quickSource",name);
  await createProjectRecord(name,packageName,config,true);
  showPanel("idePanel");
};
window.advancedCreateProject=async function advancedCreateProject(){
  const name=val("advancedName").trim()||"Yeni Uygulama";
  const packageName=val("advancedPackage").trim()||slugPackage(name);
  const config={...defaultConfig(),appName:name,packageName,sourceMode:val("advancedSourceMode"),sourceTechnology:val("advancedTechnology"),autoVersionCode:bool("advancedAutoVersion")};
  await applyCreateSource("advancedSource",name);
  await createProjectRecord(name,packageName,config,true);
  fillBuilder(config);
  showPanel("builderPanel");
};
async function createProjectRecord(name,packageName,config,saveFiles=false){
  const j=await api("/api/projects",{method:"POST",body:{name,packageName,teamId:activeTeamId,config:safeConfigForProject(config)}});
  activeProjectId=j.project.id;
  activeProject=j.project;
  $("editorName").value=name;
  $("editorPackage").value=packageName;
  if(saveFiles)await autosaveForce();
  updateProjectBadges();
  updateHome();
  return j.project;
}
window.createOrSaveProject=async function createOrSaveProject(){
  try{
    if(editor)files[activeFile]=editor.getValue();
    const existing=activeProject?.config||defaultConfig();
    const config=safeConfigForProject({...existing,...collectBuilderConfig(),appName:val("editorName")||existing.appName,packageName:val("editorPackage")||existing.packageName});
    const project=await createProjectRecord(val("editorName")||"Workspace Project",val("editorPackage")||"com.example.app",config,true);
    await api(`/api/projects/${project.id}/revisions`,{method:"POST",body:{kind:"manual",message:"Project save"}});
    alert("Proje ve dosyalar kaydedildi.");
  }catch(e){alert(e.message)}
};
async function loadProjects(){
  if(!token)return;
  try{
    const path=activeTeamId?"/api/projects?teamId="+encodeURIComponent(activeTeamId):"/api/projects";
    const j=await api(path);
    $("projectList").innerHTML="";
    if(j.quota){
      setText("projectQuota",j.quota.unlimited?`${j.quota.used} proje • Pro: sınırsız`:`${j.quota.used} / ${j.quota.limit} deneme hakkı • ${j.quota.remaining} yeni proje hakkı`);
      setText("homePlan",j.quota.unlimited?"PRO":"FREE");
    }
    setText("homeProjectCount",String(j.projects.length));
    for(const p of j.projects){
      const d=document.createElement("div");d.className="card";
      d.innerHTML=`<strong>${esc(p.name)}</strong><div class="muted">${esc(p.package_name)}</div><div class="muted">${esc(p.config?.sourceTechnologyLabel||p.config?.sourceTechnology||"")}</div><div class="row"><button class="openBtn">Aç</button><button class="secondary buildBtn">Build</button><button class="danger ghost trashBtn">Sil</button></div>`;
      d.querySelector(".openBtn").onclick=()=>openProject(p);
      d.querySelector(".buildBtn").onclick=async()=>{await openProject(p);showPanel("builderPanel");};
      d.querySelector(".trashBtn").onclick=()=>moveProjectToTrash(p);
      $("projectList").appendChild(d);
    }
    return j;
  }catch(e){setText("projectQuota",e.message)}
}
async function openProject(p){
  activeProjectId=p.id;activeProject=p;
  $("editorName").value=p.name;$("editorPackage").value=p.package_name;
  const j=await api(`/api/projects/${p.id}/files`);
  const loaded={};
  for(const meta of j.files){
    const one=await api(`/api/projects/${p.id}/files?path=${encodeURIComponent(meta.path)}`);
    loaded[meta.path]=one.file.content;
  }
  files=Object.keys(loaded).length?loaded:starterFiles("blank",p.name);
  activeFile=files["index.html"]!=null?"index.html":Object.keys(files)[0];
  if(editor)switchFile(activeFile);else renderTree();
  dirty=false;
  fillBuilder({...defaultConfig(),...(p.config||{}),appName:p.name,packageName:p.package_name});
  setText("autosaveStatus","Proje açıldı");$("autosaveStatus").className="status green";
  updateProjectBadges();preview();updateHome();
}
function updateProjectBadges(){
  const label=activeProject?`${activeProject.name} • ${activeProject.package_name}`:"Proje seçilmedi";
  setText("activeProjectBadge",label);
  setText("homeActiveProject",activeProject?`${activeProject.name}\n${activeProject.package_name}`:"Henüz proje seçilmedi.");
  setText("aiProjectLabel","Analiz edilen proje: "+(activeProject?.name||"seçilmedi"));
}
function updateHome(){
  setText("homeActiveProject",activeProject?`${activeProject.name} • ${activeProject.package_name}`:"Henüz proje seçilmedi.");
  if(lastBuilds.length){
    setText("homeBuildCount",String(lastBuilds.length));
    const ok=lastBuilds.filter(b=>b.status==="success").length;
    setText("homeSuccessRate",Math.round(ok/lastBuilds.length*100)+"%");
  }
}
window.createRevisionPrompt=async function createRevisionPrompt(){
  if(!activeProjectId){alert("Önce projeyi kaydet.");return}
  try{
    await autosaveForce();
    const message=prompt("Revision açıklaması:","Manuel kayıt")||"";
    await api(`/api/projects/${activeProjectId}/revisions`,{method:"POST",body:{kind:"manual",message}});
    await loadRevisions();
  }catch(e){alert(e.message)}
};
async function loadRevisions(){
  if(!activeProjectId){$("revisionList").innerHTML="<span class='muted'>Önce proje aç.</span>";return}
  try{
    const j=await api(`/api/projects/${activeProjectId}/revisions`);
    $("revisionSelect").innerHTML=j.revisions.map(r=>`<option value="${r.id}">#${r.id} — ${esc(r.message||r.revision_kind)} — ${new Date(r.created_at).toLocaleString()}</option>`).join("");
    $("revisionList").innerHTML="";
    for(const r of j.revisions){
      const d=document.createElement("div");d.className="card";
      d.innerHTML=`#${r.id} <b>${esc(r.revision_kind)}</b> — ${esc(r.message)} <span class="muted">${new Date(r.created_at).toLocaleString()}</span> <button class="secondary restoreBtn">Geri Yükle</button>`;
      d.querySelector(".restoreBtn").onclick=()=>restoreRevision(r.id);
      $("revisionList").appendChild(d);
    }
    renderTree();
  }catch(e){$("revisionList").textContent=e.message}
}
window.restoreRevision=async function restoreRevision(id){
  if(!activeProjectId||!confirm(`#${id} revision geri yüklensin mi?`))return;
  try{
    await api(`/api/projects/${activeProjectId}/revisions/${id}/restore`,{method:"POST",body:{}});
    await openProject(activeProject);
    await loadRevisions();
  }catch(e){alert(e.message)}
};
window.showDiff=async function showDiff(){
  if(!activeProjectId||!val("revisionSelect"))return;
  try{
    const j=await api(`/api/projects/${activeProjectId}/diff?path=${encodeURIComponent(val("diffFileSelect"))}&fromRevision=${encodeURIComponent(val("revisionSelect"))}&toRevision=current`);
    $("diffBox").innerHTML=j.diff.map(x=>`<div class="diffline ${x.type}">${x.type==="add"?"+":x.type==="remove"?"-":" "}${esc(x.text)}</div>`).join("");
  }catch(e){$("diffBox").textContent=e.message}
};

async function ensureFflate(){
  if(window.fflate)return window.fflate;
  await new Promise((resolve,reject)=>{
    const s=document.createElement("script");s.src=window.APPFORGE_FFLATE_URL;s.async=true;s.onload=resolve;s.onerror=()=>reject(new Error("ZIP motoru yüklenemedi."));document.head.appendChild(s);
  });
  if(!window.fflate)throw new Error("ZIP motoru başlatılamadı.");
  return window.fflate;
}
async function readZip(file){
  const f=await ensureFflate();
  if(file.size>550*1024*1024)throw new Error("ZIP/APK dosyası 550 MB sınırını aşıyor.");
  const bytes=new Uint8Array(await file.arrayBuffer());
  return f.unzipSync(bytes);
}
function decodeMaybe(bytes,max=5*1024*1024){
  if(!bytes||bytes.length>max)return null;
  try{
    const sample=bytes.subarray(0,Math.min(bytes.length,2048));
    if(sample.some(b=>b===0))return null;
    return textDecoder.decode(bytes);
  }catch{return null}
}
function detectTechnology(entries){
  const names=Object.keys(entries).map(x=>x.replaceAll("\\","/"));
  const lower=names.map(x=>x.toLowerCase());
  const has=s=>lower.some(x=>x.endsWith(s)||x.includes("/"+s));
  const texts=[];
  let budget=2*1024*1024;
  for(const [name,bytes] of Object.entries(entries)){
    if(budget<=0)break;
    const t=decodeMaybe(bytes,256*1024);
    if(t){texts.push(`${name}\n${t}`);budget-=t.length}
  }
  const all=texts.join("\n").toLowerCase();
  if(has("projectsettings/projectversion.txt"))return {id:"unity",label:"Unity",engine:"unity-android"};
  if(has("pubspec.yaml"))return {id:"flutter",label:"Flutter / Dart",engine:"flutter-android"};
  if(lower.some(x=>x.endsWith(".csproj")) && /net\d+\.\d+-android|usemaui/.test(all))return {id:"dotnet-android",label:".NET Android / MAUI",engine:"dotnet-android"};
  if(has("androidmanifest.xml") && (has("build.gradle")||has("build.gradle.kts")))return {id:"android-gradle",label:"Android Kotlin / Java Gradle",engine:"android-gradle"};
  if(has("package.json") && /react-native|expo/.test(all))return {id:"react-native",label:"React Native / Expo",engine:"react-native"};
  if(has("requirements.txt")||has("pyproject.toml")||lower.some(x=>x.endsWith(".py")))return {id:"python",label:"Python / Flask / Django",engine:"python-android"};
  if(has("cmakelists.txt")||lower.some(x=>/\.(cpp|cc|cxx|c)$/.test(x)))return {id:"cpp",label:"C / C++",engine:"cpp-android"};
  if(has("package.json") && /next|nuxt|vite|webpack/.test(all))return {id:"node-web",label:"Node / Web Framework",engine:"node-web"};
  if(has("index.html"))return {id:"web-static",label:"HTML / CSS / JavaScript",engine:"webview-static"};
  return {id:"unknown",label:"Bilinmeyen / Generic",engine:"auto"};
}
function detectPermissionsFromText(text){
  const t=text.toLowerCase();
  const p={camera:false,microphone:false,location:false,notifications:false,networkState:false,wakeLock:false,nfc:false,additionalPermissions:[]};
  if(/android\.permission\.camera|getusermedia|capture=["']?camera|navigator\.media/.test(t))p.camera=true;
  if(/android\.permission\.record_audio|audio\/|microphone|getusermedia/.test(t))p.microphone=true;
  if(/access_fine_location|access_coarse_location|navigator\.geolocation/.test(t))p.location=true;
  if(/post_notifications|notification/.test(t))p.notifications=true;
  if(/access_network_state|navigator\.connection/.test(t))p.networkState=true;
  if(/wake_lock|navigator\.wakelock/.test(t))p.wakeLock=true;
  if(/android\.permission\.nfc|ndefreader/.test(t))p.nfc=true;
  const map=[
    ["BLUETOOTH_SCAN",/bluetooth_scan|navigator\.bluetooth/],
    ["USE_BIOMETRIC",/use_biometric|webauthn|credentials\.create/],
    ["READ_CALENDAR",/read_calendar/],
    ["READ_CONTACTS",/read_contacts/],
    ["ACCESS_BACKGROUND_LOCATION",/access_background_location/],
    ["SCHEDULE_EXACT_ALARM",/schedule_exact_alarm/],
    ["READ_MEDIA_IMAGES",/read_media_images/],
    ["READ_MEDIA_VIDEO",/read_media_video/],
    ["ACTIVITY_RECOGNITION",/activity_recognition/]
  ];
  for(const [name,rx] of map)if(rx.test(t))p.additionalPermissions.push(name);
  return p;
}
function applyDetectedPermissions(p){
  const m={camera:"pCamera",microphone:"pMicrophone",location:"pLocation",notifications:"pNotifications",networkState:"pNetworkState",wakeLock:"pWakeLock",nfc:"pNfc"};
  for(const [k,id] of Object.entries(m))if(p[k])$(id).checked=true;
  const extra={BLUETOOTH_SCAN:"pBluetooth",USE_BIOMETRIC:"pBiometric",READ_CALENDAR:"pCalendar",READ_CONTACTS:"pContacts",ACCESS_BACKGROUND_LOCATION:"pBackgroundLocation",SCHEDULE_EXACT_ALARM:"pExactAlarm",READ_MEDIA_IMAGES:"pMediaImages",READ_MEDIA_VIDEO:"pMediaVideo",ACTIVITY_RECOGNITION:"pActivityRecognition"};
  for(const x of p.additionalPermissions||[])if(extra[x])$(extra[x]).checked=true;
}
async function analyzeZipFile(file){
  const entries=await readZip(file);
  const tech=detectTechnology(entries);
  let text="",textBytes=0,textFiles=0,binaryFiles=0;
  const editable={};
  for(const [name,bytes] of Object.entries(entries)){
    const clean=name.replaceAll("\\","/").replace(/^\/+/,"");
    if(!clean||clean.includes("../"))continue;
    const decoded=decodeMaybe(bytes);
    if(decoded!=null && textBytes+decoded.length<25*1024*1024){
      editable[clean]=decoded;text+=decoded+"\n";textBytes+=decoded.length;textFiles++;
    }else binaryFiles++;
  }
  const permissions=detectPermissionsFromText(text);
  sourceAnalysis={technology:tech,permissions,fileCount:Object.keys(entries).length,textFiles,binaryFiles,textBytes,pwa:/manifest\.json|service-worker|sw\.js/i.test(Object.keys(entries).join("\n"))};
  analyzedSourceFiles=editable;
  pendingProjectFile=file;
  applyDetectedPermissions(permissions);
  $("bTechnology").value=tech.id;
  $("sourceAnalysisBox").textContent=pretty(sourceAnalysis);
  return sourceAnalysis;
}
window.analyzeSelectedSource=async function analyzeSelectedSource(){
  try{
    const zip=$("sourceZipInput").files?.[0];
    const folder=[...($("sourceFolderInput").files||[])];
    if(zip){await analyzeZipFile(zip);return}
    if(folder.length){
      const f=await ensureFflate();
      const entries={};
      for(const file of folder){
        if(file.size>25*1024*1024)continue;
        entries[file.webkitRelativePath||file.name]=new Uint8Array(await file.arrayBuffer());
      }
      const zipped=f.zipSync(entries,{level:1});
      const name=(folder[0]?.webkitRelativePath||"source").split("/")[0]+".zip";
      const file=new File([zipped],name,{type:"application/zip"});
      await analyzeZipFile(file);
      return;
    }
    throw new Error("ZIP veya klasör seç.");
  }catch(e){$("sourceAnalysisBox").textContent=e.message}
};
window.importAnalyzedSourceToIde=function importAnalyzedSourceToIde(){
  if(!analyzedSourceFiles||!Object.keys(analyzedSourceFiles).length){alert("Önce kaynak analizi yap.");return}
  files=structuredClone(analyzedSourceFiles);
  activeFile=files["index.html"]!=null?"index.html":Object.keys(files)[0];
  if(editor)switchFile(activeFile);else renderTree();
  dirty=true;preview();showPanel("idePanel");
};

function u64be(view,offset){
  const hi=BigInt(view.getUint32(offset,false)),lo=BigInt(view.getUint32(offset+4,false));
  return (hi<<32n)|lo;
}
async function hydrateConversion(manifest,siteEntries,target){
  const name=manifest.appName||"Dönüştürülen Uygulama";
  const packageName=manifest.appId||slugPackage(name);
  const cfg={...defaultConfig(),
    appName:name,packageName,
    sourceMode:String(manifest.sourceMode||"LOCAL").toUpperCase()==="URL"?"URL":"LOCAL",
    webUrl:manifest.webUrl||"",
    versionName:manifest.versionName||"1.0.0",
    versionCode:Number(manifest.versionCode||1),
    buildOutput:target,
    ...(manifest.webView||{}),
    mediaPlayerBridge:Boolean(manifest.mediaPlayerBridge||manifest.nativeBridge?.mediaPlayer)
  };
  if(siteEntries&&Object.keys(siteEntries).length){
    files={};
    for(const [path,bytes] of Object.entries(siteEntries)){
      const t=decodeMaybe(bytes,10*1024*1024);
      if(t!=null)files[path]=t;
    }
  }else if(cfg.sourceMode==="LOCAL")throw new Error("Dönüşüm içinde web proje dosyaları bulunamadı.");
  await createProjectRecord(name,packageName,cfg,true);
  fillBuilder(cfg);
  conversionTarget=target;
  showPanel("builderPanel");
  builderStep=10;renderBuilderStep();
  return cfg;
}
window.extractApkConversion=async function extractApkConversion(){
  try{
    const file=$("apkConvertInput").files?.[0];if(!file)throw new Error("APK seç.");
    const z=await readZip(file);
    const manifestBytes=z["assets/appforge-project.json"];if(!manifestBytes)throw new Error("Bu APK AppForge dönüşüm manifesti içermiyor.");
    const manifest=JSON.parse(textDecoder.decode(manifestBytes));
    if(manifest.format!=="appforge-project"||Number(manifest.formatVersion)!==1||manifest.platform!=="android")throw new Error("Geçersiz AppForge Android dönüşüm manifesti.");
    if(!manifest.conversion?.apkToExe)throw new Error("Bu APK, APK → EXE dönüşümünü desteklemiyor.");
    const site={};
    for(const [name,bytes] of Object.entries(z))if(name.startsWith("assets/site/")&&name.length>"assets/site/".length)site[name.slice("assets/site/".length)]=bytes;
    await hydrateConversion(manifest,site,"exe");
    setText("apkConvertResult",`${manifest.appName} çıkarıldı. Builder EXE moduna hazır.`);
  }catch(e){setText("apkConvertResult",e.message)}
};
window.extractExeConversion=async function extractExeConversion(){
  try{
    const file=$("exeConvertInput").files?.[0];if(!file)throw new Error("EXE seç.");
    if(file.size>1024*1024*1024)throw new Error("EXE 1 GB sınırını aşıyor.");
    const bytes=new Uint8Array(await file.arrayBuffer());
    if(bytes[0]!==0x4d||bytes[1]!==0x5a)throw new Error("Geçerli Windows EXE değil.");
    const magic=textDecoder.decode(bytes.subarray(bytes.length-16));
    if(magic!=="APPFORGE-EXE-V1!")throw new Error("Bu EXE AppForge dönüşüm payload'ı içermiyor.");
    const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
    const payloadLength=Number(u64be(view,bytes.length-24));
    if(!Number.isSafeInteger(payloadLength)||payloadLength<=0||payloadLength>536870912)throw new Error("EXE dönüşüm payload boyutu geçersiz.");
    const payloadOffset=bytes.length-24-payloadLength;
    if(payloadOffset<2)throw new Error("EXE payload offset geçersiz.");
    if(textDecoder.decode(bytes.subarray(payloadOffset,payloadOffset+8))!=="AFEXEP01")throw new Error("EXE payload başlığı geçersiz.");
    const manifestLength=view.getUint32(payloadOffset+8,false);
    if(manifestLength<=0||manifestLength>256*1024)throw new Error("EXE manifest boyutu geçersiz.");
    const manifestStart=payloadOffset+12,manifestEnd=manifestStart+manifestLength;
    const manifest=JSON.parse(textDecoder.decode(bytes.subarray(manifestStart,manifestEnd)));
    if(manifest.format!=="appforge-project"||Number(manifest.formatVersion)!==1||manifest.platform!=="windows")throw new Error("Geçersiz AppForge Windows dönüşüm manifesti.");
    if(!manifest.conversion?.exeToApk)throw new Error("Bu EXE, EXE → APK dönüşümünü desteklemiyor.");
    let site={};
    const projectBytes=bytes.subarray(manifestEnd,payloadOffset+payloadLength);
    if(projectBytes.length){
      const f=await ensureFflate();
      const z=f.unzipSync(projectBytes);
      for(const [name,data] of Object.entries(z)){
        const clean=name.replaceAll("\\","/").replace(/^site\//,"");
        if(clean&& !clean.endsWith("/"))site[clean]=data;
      }
    }
    await hydrateConversion(manifest,site,"apk");
    setText("exeConvertResult",`${manifest.appName} çıkarıldı. Builder APK moduna hazır.`);
  }catch(e){setText("exeConvertResult",e.message)}
};

function collectAdditionalPermissions(){
  const out=[];
  const map={pBluetooth:"BLUETOOTH_SCAN",pBiometric:"USE_BIOMETRIC",pCalendar:"READ_CALENDAR",pContacts:"READ_CONTACTS",pBackgroundLocation:"ACCESS_BACKGROUND_LOCATION",pExactAlarm:"SCHEDULE_EXACT_ALARM",pMediaImages:"READ_MEDIA_IMAGES",pMediaVideo:"READ_MEDIA_VIDEO",pActivityRecognition:"ACTIVITY_RECOGNITION"};
  for(const [id,name] of Object.entries(map))if(bool(id))out.push(name);
  return out;
}
function collectBuilderConfig(){
  const c={
    ...defaultConfig(),
    appName:val("bAppName")||val("editorName")||activeProject?.name||"AppForge App",
    packageName:val("bPackageName")||val("editorPackage")||activeProject?.package_name||"com.example.myapp",
    sourceMode:val("bSourceMode")||"LOCAL",
    sourceTechnology:val("bTechnology")||"web-static",
    webUrl:val("bWebUrl").trim(),
    versionName:val("bVersionName")||"1.0.0",
    versionCode:Math.max(1,intVal("bVersionCode",1)),
    autoVersionCode:bool("bAutoVersion"),
    buildOutput:val("bBuildOutput")||"both",
    orientation:val("bOrientation")||"unspecified",
    appCategory:val("bAppCategory")||"auto",
    primaryColor:val("bPrimaryColor")||"#6B7CFF",
    backgroundColor:val("bBackgroundColor")||"#07101F",
    statusBarColor:val("bStatusBarColor")||"#07101F",
    navigationBarColor:val("bNavigationBarColor")||"#07101F",
    splashEnabled:bool("bSplashEnabled"),splashText:val("bSplashText"),
    signingMode:val("sMode")||"DEBUG",keyAlias:val("sAlias"),storePassword:val("sStorePassword"),keyPassword:val("sKeyPassword"),
    fileUpload:bool("pFileUpload"),downloads:bool("pDownloads"),fullscreen:bool("wFullscreen"),
    notifications:bool("pNotifications"),camera:bool("pCamera"),microphone:bool("pMicrophone"),location:bool("pLocation"),
    networkState:bool("pNetworkState"),wakeLock:bool("pWakeLock"),nfc:bool("pNfc"),additionalPermissions:collectAdditionalPermissions(),offlineCache:bool("wOfflineCache"),
    webJavaScriptEnabled:bool("wJavaScript"),webDomStorageEnabled:bool("wDomStorage"),webZoomEnabled:bool("wZoom"),webWideViewPortEnabled:bool("wWideViewport"),webOverviewModeEnabled:bool("wOverview"),webMediaAutoplayEnabled:bool("wAutoplay"),webMixedContentAllowed:bool("wMixedContent"),
    deepLinkEnabled:bool("dEnabled"),deepLinkScheme:val("dScheme")||"https",deepLinkHost:val("dHost"),deepLinkPathPrefix:val("dPath")||"/",
    javascriptBridge:bool("nJavascriptBridge"),remoteBridgeAllowed:bool("nRemoteBridge"),shareBridge:bool("nShare"),clipboardBridge:bool("nClipboard"),vibrationBridge:bool("nVibration"),mediaPlayerBridge:bool("nMediaPlayer"),qrScanner:bool("nQrScanner"),
    admobEnabled:bool("mAdmobEnabled"),admobAppId:val("mAdmobAppId"),admobBannerUnitId:val("mBanner"),admobInterstitialUnitId:val("mInterstitial"),admobRewardedUnitId:val("mRewarded"),umpConsentEnabled:bool("mUmp"),
    billingEnabled:bool("mBillingEnabled"),billingProductIds:val("mProducts"),billingSubscriptionIds:val("mSubs"),consumableProductIds:val("mConsumables"),removeAdsProductId:val("mRemoveAds"),purchaseVerificationUrl:val("mVerifyUrl"),
    firebaseAnalyticsEnabled:bool("fAnalytics"),firebaseCrashlyticsEnabled:bool("fCrashlytics"),firebaseMessagingEnabled:bool("fMessaging")
  };
  if(sourceAnalysis?.technology){
    c.sourceTechnology=sourceAnalysis.technology.id;
    c.sourceTechnologyLabel=sourceAnalysis.technology.label;
    c.sourceBuildEngine=sourceAnalysis.technology.engine;
    c.sourceBuildReady=sourceAnalysis.technology.id!=="unknown";
  }
  return c;
}
function fillBuilder(c){
  c={...defaultConfig(),...(c||{})};
  const set=(id,v)=>{if($(id))$(id).value=v??""};
  const check=(id,v)=>{if($(id))$(id).checked=Boolean(v)};
  set("bAppName",c.appName||activeProject?.name||"");set("bPackageName",c.packageName||activeProject?.package_name||"");
  set("bSourceMode",c.sourceMode);set("bTechnology",c.sourceTechnology);set("bWebUrl",c.webUrl);set("bVersionName",c.versionName);set("bVersionCode",c.versionCode);check("bAutoVersion",c.autoVersionCode);set("bBuildOutput",c.buildOutput);
  set("bOrientation",c.orientation);set("bAppCategory",c.appCategory);set("bPrimaryColor",c.primaryColor);set("bBackgroundColor",c.backgroundColor);set("bStatusBarColor",c.statusBarColor);set("bNavigationBarColor",c.navigationBarColor);check("bSplashEnabled",c.splashEnabled);set("bSplashText",c.splashText);
  check("pFileUpload",c.fileUpload);check("pDownloads",c.downloads);check("pNotifications",c.notifications);check("pCamera",c.camera);check("pMicrophone",c.microphone);check("pLocation",c.location);check("pNetworkState",c.networkState);check("pWakeLock",c.wakeLock);check("pNfc",c.nfc);
  const extras=new Set(c.additionalPermissions||[]);const em={pBluetooth:"BLUETOOTH_SCAN",pBiometric:"USE_BIOMETRIC",pCalendar:"READ_CALENDAR",pContacts:"READ_CONTACTS",pBackgroundLocation:"ACCESS_BACKGROUND_LOCATION",pExactAlarm:"SCHEDULE_EXACT_ALARM",pMediaImages:"READ_MEDIA_IMAGES",pMediaVideo:"READ_MEDIA_VIDEO",pActivityRecognition:"ACTIVITY_RECOGNITION"};for(const [id,n] of Object.entries(em))check(id,extras.has(n));
  check("wJavaScript",c.webJavaScriptEnabled);check("wDomStorage",c.webDomStorageEnabled);check("wZoom",c.webZoomEnabled);check("wWideViewport",c.webWideViewPortEnabled);check("wOverview",c.webOverviewModeEnabled);check("wAutoplay",c.webMediaAutoplayEnabled);check("wMixedContent",c.webMixedContentAllowed);check("wOfflineCache",c.offlineCache);check("wFullscreen",c.fullscreen);
  check("nJavascriptBridge",c.javascriptBridge);check("nRemoteBridge",c.remoteBridgeAllowed);check("nShare",c.shareBridge);check("nClipboard",c.clipboardBridge);check("nVibration",c.vibrationBridge);check("nMediaPlayer",c.mediaPlayerBridge);check("nQrScanner",c.qrScanner);
  check("dEnabled",c.deepLinkEnabled);set("dScheme",c.deepLinkScheme);set("dHost",c.deepLinkHost);set("dPath",c.deepLinkPathPrefix);
  check("fAnalytics",c.firebaseAnalyticsEnabled);check("fCrashlytics",c.firebaseCrashlyticsEnabled);check("fMessaging",c.firebaseMessagingEnabled);
  check("mAdmobEnabled",c.admobEnabled);set("mAdmobAppId",c.admobAppId);set("mBanner",c.admobBannerUnitId);set("mInterstitial",c.admobInterstitialUnitId);set("mRewarded",c.admobRewardedUnitId);check("mUmp",c.umpConsentEnabled);
  check("mBillingEnabled",c.billingEnabled);set("mProducts",c.billingProductIds);set("mSubs",c.billingSubscriptionIds);set("mConsumables",c.consumableProductIds);set("mRemoveAds",c.removeAdsProductId);set("mVerifyUrl",c.purchaseVerificationUrl);
  set("sMode",c.signingMode||"DEBUG");set("sAlias",c.keyAlias||"");
  updateBuilderSummary();
}
window.builderPrev=function builderPrev(){builderStep=Math.max(1,builderStep-1);renderBuilderStep()};
window.builderNext=function builderNext(){builderStep=Math.min(10,builderStep+1);renderBuilderStep()};
function renderBuilderStep(){
  qsa(".builder-step").forEach(x=>x.classList.toggle("hidden",Number(x.dataset.step)!==builderStep));
  const labels=["Kaynak","Uygulama","İzinler","Görünüm","WebView Pro","Native Bridge","Firebase","Monetization","Signing","Build"];
  setText("builderStepLabel",`Adım ${builderStep}/10 • ${labels[builderStep-1]}`);
  updateBuilderSummary();
}
function updateBuilderSummary(){
  if(!$("builderSummary"))return;
  const c=collectBuilderConfig();
  const safe=safeConfigForProject(c);
  $("builderSummary").textContent=pretty({app:safe.appName,package:safe.packageName,source:safe.sourceMode,technology:safe.sourceTechnology,output:safe.buildOutput,version:`${safe.versionName} (${safe.versionCode})`,permissions:{camera:safe.camera,microphone:safe.microphone,location:safe.location,notifications:safe.notifications,nfc:safe.nfc,additional:safe.additionalPermissions},firebase:{analytics:safe.firebaseAnalyticsEnabled,crashlytics:safe.firebaseCrashlyticsEnabled,messaging:safe.firebaseMessagingEnabled},signing:safe.signingMode});
}
async function workspaceZipFile(){
  const f=await ensureFflate();
  if(editor)files[activeFile]=editor.getValue();
  const entries={};
  for(const [path,content] of Object.entries(files))entries[path]=textEncoder.encode(content);
  const zipped=f.zipSync(entries,{level:1});
  return new File([zipped],`${safeFileName(val("bAppName")||"project")}.zip`,{type:"application/zip"});
}
window.saveBuilderProject=async function saveBuilderProject(){
  try{
    const c=collectBuilderConfig();
    const project=await createProjectRecord(c.appName,c.packageName,safeConfigForProject(c),true);
    activeProject=project;
    alert("Builder ayarları kaydedildi.");
  }catch(e){alert(e.message)}
};
window.startAdvancedBuild=async function startAdvancedBuild(){
  try{
    let c=collectBuilderConfig();
    if(c.autoVersionCode){c.versionCode=Math.max(c.versionCode+1,Math.floor(Date.now()/1000));$("bVersionCode").value=c.versionCode}
    if(bool("bSaveBeforeBuild")){
      const p=await createProjectRecord(c.appName,c.packageName,safeConfigForProject(c),true);
      activeProject=p;
    }
    let projectFile=$("bProjectFile").files?.[0] || pendingProjectFile;
    const iconFile=processedIconFile || $("bIconFile").files?.[0] || null;
    let firebaseFile=$("bFirebaseFile").files?.[0] || null;
    let keystoreFile=$("bKeystoreFile").files?.[0] || cachedKeystoreFile || null;
    if(c.sourceMode==="LOCAL" && !projectFile)projectFile=await workspaceZipFile();
    if(c.signingMode==="CUSTOM"&&!keystoreFile)throw new Error("CUSTOM signing için keystore gerekli.");
    if((c.firebaseAnalyticsEnabled||c.firebaseCrashlyticsEnabled||c.firebaseMessagingEnabled)&&!firebaseFile)throw new Error("Firebase açıkken google-services.json gerekli.");
    const form=new FormData();
    form.append("config",JSON.stringify(c));
    if(projectFile)form.append("project",projectFile,projectFile.name);
    if(iconFile)form.append("icon",iconFile,iconFile.name||"icon.png");
    if(firebaseFile)form.append("firebaseConfig",firebaseFile,firebaseFile.name||"google-services.json");
    if(keystoreFile)form.append("keystore",keystoreFile,keystoreFile.name||"release.jks");
    const idem=`v4-${c.packageName}-${Date.now()}-${crypto.getRandomValues(new Uint32Array(1))[0]}`;
    const j=await apiForm("/api/builds",form,{headers:{"Idempotency-Key":idem}});
    await trackBackgroundBuild(j.buildId);
    alert(`Build ${j.status}: ${j.buildId}${j.cacheHit?" (CACHE HIT)":""}`);
    showPanel("buildsPanel");
    await loadBuilds();
    if(j.buildId)startLiveLogs(j.buildId);
    if(conversionTarget){conversionTarget=null}
  }catch(e){alert(e.message)}
};
window.workspaceBuild=async function workspaceBuild(output="both"){
  if(!activeProjectId){alert("Önce projeyi kaydet.");return}
  try{
    await autosaveForce();
    const c=collectBuilderConfig();
    const idem=`workspace-${activeProjectId}-${Date.now()}`;
    const j=await api(`/api/projects/${activeProjectId}/builds`,{method:"POST",headers:{"Idempotency-Key":idem},body:{buildOutput:output,priority:intVal("bPriority",100),configOverride:safeConfigForProject({...c,buildOutput:output})}});
    await trackBackgroundBuild(j.buildId);
    showPanel("buildsPanel");await loadBuilds();startLiveLogs(j.buildId);
  }catch(e){alert(e.message)}
};

window.inspectFirebase=async function inspectFirebase(){
  try{
    const file=$("bFirebaseFile").files?.[0];if(!file)throw new Error("google-services.json seç.");
    if(file.size>2*1024*1024)throw new Error("Firebase config 2 MB sınırını aşıyor.");
    const j=JSON.parse(await file.text());
    const packageName=val("bPackageName").trim();
    const clients=Array.isArray(j.client)?j.client:[];
    const packages=clients.map(c=>c?.client_info?.android_client_info?.package_name).filter(Boolean);
    const match=!packageName||packages.includes(packageName);
    $("firebaseInfo").textContent=pretty({projectId:j.project_info?.project_id||null,projectNumber:j.project_info?.project_number||null,packages,packageMatches:match});
    if(packageName&&!match)throw new Error(`Firebase paket adı uyuşmuyor. Beklenen: ${packageName}`);
  }catch(e){$("firebaseInfo").textContent=e.message}
};

async function processIconFile(file){
  if(!file)return null;
  const bitmap=await createImageBitmap(file,{imageOrientation:"from-image"});
  const canvas=document.createElement("canvas");canvas.width=1024;canvas.height=1024;
  const ctx=canvas.getContext("2d");ctx.clearRect(0,0,1024,1024);
  const safe=640,scale=Math.min(safe/bitmap.width,safe/bitmap.height);
  const w=bitmap.width*scale,h=bitmap.height*scale;
  ctx.drawImage(bitmap,(1024-w)/2,(1024-h)/2,w,h);
  const blob=await new Promise(r=>canvas.toBlob(r,"image/png",1));
  return new File([blob],safeFileName(file.name.replace(/\.[^.]+$/,""))+".png",{type:"image/png"});
}
$("bIconFile").addEventListener("change",async()=>{
  try{
    processedIconFile=await processIconFile($("bIconFile").files?.[0]);
    setText("iconInfo",processedIconFile?`1024×1024 adaptive-safe PNG hazır • ${(processedIconFile.size/1024).toFixed(1)} KB`:"");
  }catch(e){setText("iconInfo",e.message)}
});

async function fileHashes(file){
  const bytes=await file.arrayBuffer();
  const [sha1,sha256]=await Promise.all([crypto.subtle.digest("SHA-1",bytes),crypto.subtle.digest("SHA-256",bytes)]);
  const hex=b=>[...new Uint8Array(b)].map(x=>x.toString(16).padStart(2,"0")).join(":").toUpperCase();
  return {sha1:hex(sha1),sha256:hex(sha256)};
}
window.saveKeystoreVault=async function saveKeystoreVault(){
  try{
    let file=$("bKeystoreFile").files?.[0] || cachedKeystoreFile;
    if(!file)throw new Error("Keystore seç.");
    if(file.size>4*1024*1024)throw new Error("Keystore 4 MB sınırını aşıyor.");
    const hashes=await fileHashes(file);
    setText("keystoreInfo",`Dosya SHA-1 ${hashes.sha1}\nDosya SHA-256 ${hashes.sha256}`);
    if(!bool("sRemember"))return;
    if(!desktopKeystore?.save)throw new Error("Güvenli keystore kasası yalnız Windows Setup sürümünde kullanılabilir.");
    const bytes=new Uint8Array(await file.arrayBuffer());
    let binary="";for(let i=0;i<bytes.length;i+=0x8000)binary+=String.fromCharCode(...bytes.subarray(i,i+0x8000));
    await desktopKeystore.save({name:file.name,base64:btoa(binary),alias:val("sAlias"),storePassword:val("sStorePassword"),keyPassword:val("sKeyPassword")});
    setText("keystoreInfo",`Windows safeStorage kasasına kaydedildi.\nDosya SHA-1 ${hashes.sha1}\nDosya SHA-256 ${hashes.sha256}`);
  }catch(e){setText("keystoreInfo",e.message)}
};
window.loadKeystoreVault=async function loadKeystoreVault(){
  try{
    if(!desktopKeystore?.get)throw new Error("Bu özellik Windows Setup sürümünde kullanılabilir.");
    const k=await desktopKeystore.get();if(!k)throw new Error("Kasada keystore yok.");
    const binary=atob(k.base64);const bytes=new Uint8Array(binary.length);for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);
    cachedKeystoreFile=new File([bytes],k.name||"release.jks",{type:"application/octet-stream"});
    $("sAlias").value=k.alias||"";$("sStorePassword").value=k.storePassword||"";$("sKeyPassword").value=k.keyPassword||"";
    setText("keystoreInfo",`Kasadan yüklendi: ${k.name}\nSHA-1 ${k.sha1||"-"}\nSHA-256 ${k.sha256||"-"}`);
  }catch(e){setText("keystoreInfo",e.message)}
};
window.clearKeystoreVault=async function clearKeystoreVault(){
  cachedKeystoreFile=null;$("sStorePassword").value="";$("sKeyPassword").value="";
  if(desktopKeystore?.clear)await desktopKeystore.clear();
  setText("keystoreInfo","Kasa temizlendi.");
};

async function loadTemplates(){
  if(!token)return;
  try{
    const j=await api("/api/templates");
    templateCache=j.templates||[];
  }catch{
    templateCache=[];
  }
  const fallback=[
    ["task-manager","Görev Yöneticisi","Verimlilik"],["inventory-panel","Stok Paneli","İşletme"],["booking-form","Rezervasyon Formu","İşletme"],
    ["restaurant-menu","Restoran Menü","E-ticaret ve Menü"],["event-invitation","Etkinlik Daveti","Etkinlik"],["visual-designer","Visual Designer","Panel"],
    ["personnel-tracker","Personel Takip","İşletme"],["qr-menu","QR Menü","E-ticaret ve Menü"],["education-quiz","Eğitim Quiz","Eğitim"],["firebase-login","Firebase Login","Başlangıçlar"]
  ];
  const known=new Set(templateCache.map(x=>x.slug));
  for(const [slug,name,category] of fallback)if(!known.has(slug))templateCache.push({slug,name,category,description:"AppForge V4 yerel başlangıç şablonu",config:{}});
  renderTemplates();
}
window.loadTemplates=loadTemplates;
window.renderTemplates=function renderTemplates(){
  const search=val("templateSearch").trim().toLowerCase();
  const categories=["Tümü",...new Set(templateCache.map(t=>t.category||"Diğer"))];
  $("templateCategories").innerHTML=categories.map(c=>`<button class="ghost ${c===templateCategory?"active":""}" data-cat="${esc(c)}">${esc(c)}</button>`).join("");
  qsa("#templateCategories button").forEach(b=>b.onclick=()=>{templateCategory=b.dataset.cat;renderTemplates()});
  const filtered=templateCache.filter(t=>(templateCategory==="Tümü"||t.category===templateCategory)&&(!search||`${t.name} ${t.description} ${t.slug} ${t.category}`.toLowerCase().includes(search)));
  $("templateList").innerHTML=filtered.map(t=>`<div class="card"><span class="pill">${esc(t.category||"Diğer")}</span><h3>${esc(t.name)}</h3><p class="muted">${esc(t.description||"")}</p><code>${esc(t.slug)}</code><div><button data-slug="${esc(t.slug)}">ŞABLONU UYGULA</button></div></div>`).join("") || "<div class='card muted'>Arama eşleşmesi yok.</div>";
  qsa("#templateList button[data-slug]").forEach(b=>b.onclick=()=>applyTemplate(b.dataset.slug));
};
async function applyTemplate(slug){
  const t=templateCache.find(x=>x.slug===slug)||{name:slug,config:{}};
  const name=prompt("Proje adı:",t.name)||t.name;
  const packageName=slugPackage(name);
  files=starterFiles(slug,name);
  const config={...defaultConfig(),...(t.config||{}),appName:name,packageName};
  await createProjectRecord(name,packageName,config,true);
  fillBuilder(config);showPanel("idePanel");
}

function buildPreviewDocument(){
  const html=files["index.html"]||"";
  const css=files["style.css"]||"";
  const js=files["app.js"]||"";
  const instrumentation=`<script>
  (()=>{const send=(type,data)=>parent.postMessage({__appforgePreview:true,type,data},'*');
  const oldLog=console.log,oldErr=console.error,oldWarn=console.warn;
  console.log=(...a)=>{oldLog(...a);send('console',{level:'log',text:a.map(String).join(' ')})};
  console.error=(...a)=>{oldErr(...a);send('console',{level:'error',text:a.map(String).join(' ')})};
  console.warn=(...a)=>{oldWarn(...a);send('console',{level:'warn',text:a.map(String).join(' ')})};
  addEventListener('error',e=>send('console',{level:'error',text:e.message||'error'}));
  const of=window.fetch;if(of)window.fetch=async(...a)=>{const t=performance.now();try{const r=await of(...a);send('network',{url:String(a[0]),status:r.status,ms:Math.round(performance.now()-t)});return r}catch(e){send('network',{url:String(a[0]),error:String(e),ms:Math.round(performance.now()-t)});throw e}};
  addEventListener('load',()=>setTimeout(()=>send('performance',performance.getEntriesByType('navigation').map(x=>({dom:x.domContentLoadedEventEnd,duration:x.duration,transferSize:x.transferSize}))),0));
  })();<\/script>`;
  let out=html.replace(/<link[^>]*href=["']style\.css["'][^>]*>/i,`<style>${css}</style>`);
  out=out.replace(/<script[^>]*src=["']app\.js["'][^>]*><\/script>/i,`<script>${js}<\/script>`);
  if(/<\/head>/i.test(out))out=out.replace(/<\/head>/i,instrumentation+"</head>");else out=instrumentation+out;
  return out;
}
function preview(){
  const doc=buildPreviewDocument();
  $("previewFrame").srcdoc=doc;
  renderLabPreview(doc);
}
function renderLabPreview(doc=buildPreviewDocument()){
  if($("labPreviewFrame"))$("labPreviewFrame").srcdoc=doc;
  renderSecurityInspector();
}
window.setPreviewDevice=function setPreviewDevice(device){
  const cls=device==="phone"?"compact":device==="tablet"?"tablet":"desktop";
  for(const id of ["ideDeviceWrap","labDeviceWrap"]){
    const el=$(id);if(el)el.className="device-wrap "+cls;
  }
};
window.inspectorTab=function inspectorTab(name,btn){
  for(const id of ["console","network","performance","security"])$(`inspector${id[0].toUpperCase()+id.slice(1)}`).classList.toggle("hidden",id!==name);
  qsa(".inspector .segmented button").forEach(b=>b.classList.toggle("active",b===btn));
};
function renderInspector(){
  $("inspectorConsole").textContent=previewConsole.map(x=>`[${x.level}] ${x.text}`).join("\n");
  $("inspectorNetwork").textContent=previewNetwork.map(x=>`${x.status||"ERR"} ${x.url} ${x.ms||0}ms ${x.error||""}`).join("\n");
  $("inspectorPerformance").textContent=pretty(previewPerformance);
}
function renderSecurityInspector(){
  const c=collectBuilderConfig();
  const issues=[];
  if(c.webMixedContentAllowed)issues.push("Mixed Content açık.");
  if(c.remoteBridgeAllowed)issues.push("Remote Native Bridge açık.");
  if(c.sourceMode==="URL"&&!/^https:\/\//i.test(c.webUrl))issues.push("Remote URL HTTPS değil.");
  if(c.javascriptBridge&&c.sourceMode==="URL"&&!c.remoteBridgeAllowed)issues.push("Remote URL modunda Native Bridge güvenli varsayımla kapalı kalmalıdır.");
  $("inspectorSecurity").textContent=issues.length?issues.map(x=>"⚠ "+x).join("\n"):"✓ Bilinen kritik WebView güvenlik riski seçilmedi.";
}
window.addEventListener("message",e=>{
  if(!e.data?.__appforgePreview)return;
  if(e.source!==$("previewFrame")?.contentWindow && e.source!==$("labPreviewFrame")?.contentWindow)return;
  if(e.data.type==="console"){previewConsole.push(e.data.data);previewConsole=previewConsole.slice(-200)}
  if(e.data.type==="network"){previewNetwork.push(e.data.data);previewNetwork=previewNetwork.slice(-200)}
  if(e.data.type==="performance"){previewPerformance=e.data.data||[]}
  renderInspector();
});

async function loadBuilds(){
  if(!token)return;
  try{
    const path=activeTeamId?"/api/builds?teamId="+encodeURIComponent(activeTeamId):"/api/builds";
    const j=await api(path);lastBuilds=j.builds||[];
    let changed=false;
    for(const build of lastBuilds){
      const previousStatus=backgroundBuilds[build.buildId]?.status;
      if(previousStatus){
        notifyBackgroundBuild(build,previousStatus);
        backgroundBuilds[build.buildId]={status:build.status};
        changed=true;
      }
    }
    if(changed)pSet("backgroundBuilds",backgroundBuilds);
    updateBackgroundBuildStatus();
    $("buildRows").innerHTML="";
    for(const b of lastBuilds){
      const tr=document.createElement("tr");
      const canControl=["queued","building"].includes(b.status);
      tr.innerHTML=`<td>${esc(b.buildId)}</td><td>${esc(b.appName)}</td><td>${esc(b.outputType||"-")}</td><td>${esc(b.status)}${b.cancelRequested?" / iptal bekliyor":""}</td><td>${b.cacheHit?"HIT":"-"}</td><td><input type="number" min="1" max="1000" value="${b.priority||100}" class="prio" style="width:80px"></td><td>${b.progress}%</td><td><button class="secondary liveBtn">Canlı</button> <button class="secondary testBtn">Test</button> <button class="secondary priorityBtn" ${b.status!=="queued"?"disabled":""}>Öncelik</button> <button class="danger cancelBtn" ${!canControl?"disabled":""}>İptal</button></td>`;
      tr.querySelector(".liveBtn").onclick=()=>startLiveLogs(b.buildId);
      tr.querySelector(".testBtn").onclick=()=>{showPanel("testLabPanel");$("testBuild").value=b.buildId;runTestLab()};
      tr.querySelector(".priorityBtn").onclick=async()=>{try{await api(`/api/builds/${b.buildId}/priority`,{method:"PATCH",body:{priority:Number(tr.querySelector(".prio").value)}});loadBuilds()}catch(e){alert(e.message)}};
      tr.querySelector(".cancelBtn").onclick=async()=>{if(confirm("Build iptal edilsin mi?"))try{await api(`/api/builds/${b.buildId}/cancel`,{method:"POST",body:{}});loadBuilds()}catch(e){alert(e.message)}};
      $("buildRows").appendChild(tr);
    }
    populateBuildSelectors();
    updateHome();
    return j;
  }catch(e){$("buildRows").innerHTML=`<tr><td colspan="8">${esc(e.message)}</td></tr>`}
}
window.loadBuilds=loadBuilds;
window.stopLiveLogs=function stopLiveLogs(){
  if(liveLogAbort){liveLogAbort.abort();liveLogAbort=null}
  setText("liveBuildStatus","Akış durduruldu.");
};
async function startLiveLogs(buildId){
  stopLiveLogs();$("liveBuildLog").textContent="";lastLiveLogId=0;setText("liveBuildStatus",`${buildId} bağlanıyor...`);liveLogAbort=new AbortController();
  try{
    const response=await fetch(`${base()}/api/builds/${encodeURIComponent(buildId)}/events?after=0`,{headers:{Authorization:"Bearer "+token,Accept:"text/event-stream"},signal:liveLogAbort.signal});
    if(!response.ok)throw new Error(await response.text());
    const reader=response.body.getReader(),decoder=new TextDecoder();let buffer="",eventName="message",eventId=null,dataLines=[];
    const flush=()=>{
      if(!dataLines.length)return;
      const raw=dataLines.join("\n");let data;try{data=JSON.parse(raw)}catch{data={raw}};
      if(eventName==="log"){$("liveBuildLog").textContent+=`[${data.createdAt||""}] ${data.line||""}\n`;$("liveBuildLog").scrollTop=$("liveBuildLog").scrollHeight}
      else if(eventName==="status")setText("liveBuildStatus",`${buildId}: ${data.status} %${data.progress}`);
      else if(eventName==="done")setText("liveBuildStatus",`${buildId}: ${data.status}`);
      else if(eventName==="error")$("liveBuildLog").textContent+=`HATA: ${data.message||raw}\n`;
      eventName="message";eventId=null;dataLines=[];
    };
    while(true){
      const {value,done}=await reader.read();if(done)break;buffer+=decoder.decode(value,{stream:true});
      const lines=buffer.split("\n");buffer=lines.pop()||"";
      for(const raw of lines){const line=raw.replace(/\r$/,"");if(line===""){flush();continue}if(line.startsWith("event:"))eventName=line.slice(6).trim();else if(line.startsWith("id:"))eventId=line.slice(3).trim();else if(line.startsWith("data:"))dataLines.push(line.slice(5).trimStart())}
    }
    flush();
  }catch(e){if(e.name!=="AbortError"){setText("liveBuildStatus","Canlı log hatası.");$("liveBuildLog").textContent+="\n"+e.message}}
  finally{liveLogAbort=null}
}
window.startLiveLogs=startLiveLogs;

function populateBuildSelectors(){
  const options=lastBuilds.map(b=>`<option value="${esc(b.buildId)}">${esc(b.appName)} • ${esc(b.status)} • ${esc(b.buildId.slice(0,8))}</option>`).join("");
  for(const id of ["testBuild","compareLeft","compareRight"])if($(id))$(id).innerHTML=options;
  if($("publishBuild"))$("publishBuild").innerHTML=lastBuilds.filter(b=>b.status==="success").map(b=>`<option value="${esc(b.buildId)}">${esc(b.appName)} • ${esc(b.buildId.slice(0,8))}</option>`).join("");
}
window.runTestLab=async function runTestLab(){
  try{$("testLabOutput").textContent=pretty(await api(`/api/builds/${encodeURIComponent(val("testBuild"))}/test-lab`))}catch(e){$("testLabOutput").textContent=e.message}
};
window.loadArtifacts=async function loadArtifacts(){
  try{$("testLabOutput").textContent=pretty(await api(`/api/builds/${encodeURIComponent(val("testBuild"))}/artifacts`))}catch(e){$("testLabOutput").textContent=e.message}
};
window.compareBuildsUi=async function compareBuildsUi(){
  try{$("testLabOutput").textContent=pretty(await api(`/api/builds/compare?left=${encodeURIComponent(val("compareLeft"))}&right=${encodeURIComponent(val("compareRight"))}`))}catch(e){$("testLabOutput").textContent=e.message}
};
window.releaseNotesUi=async function releaseNotesUi(){
  try{$("testLabOutput").textContent=pretty(await api(`/api/builds/${encodeURIComponent(val("testBuild"))}/release-notes`))}catch(e){$("testLabOutput").textContent=e.message}
};

async function loadProduction(){
  await loadBuilds();
  try{
    const j=await api("/api/publish-drafts");
    $("publishDraftList").innerHTML=(j.drafts||[]).map(d=>`<div class="card"><b>${esc(d.release_name||d.build_id)}</b><div class="muted">${esc(d.track)} • ${esc(d.status)} • ${new Date(d.created_at).toLocaleString()}</div></div>`).join("")||"<span class='muted'>Taslak yok.</span>";
  }catch(e){$("publishDraftList").textContent=e.message}
}
window.createPublishDraftUi=async function createPublishDraftUi(){
  try{
    const notes=val("publishNotes").split("\n").filter(Boolean);
    await api("/api/publish-drafts",{method:"POST",body:{buildId:val("publishBuild"),track:val("publishTrack"),releaseName:val("publishName"),releaseNotes:{tr:notes}}});
    await loadProduction();
  }catch(e){alert(e.message)}
};

async function snapshotProject(p){
  const fileMeta=await api(`/api/projects/${p.id}/files`);
  const snapshotFiles={};
  for(const m of fileMeta.files){const one=await api(`/api/projects/${p.id}/files?path=${encodeURIComponent(m.path)}`);snapshotFiles[m.path]=one.file.content}
  let localizations=[];try{localizations=(await api(`/api/projects/${p.id}/localizations`)).localizations||[]}catch{}
  return {deletedAt:Date.now(),name:p.name,packageName:p.package_name,config:safeConfigForProject(p.config||{}),files:snapshotFiles,localizations};
}
async function moveProjectToTrash(p){
  if(!confirm(`${p.name} silinsin mi? 30 gün boyunca bu cihazdaki AppForge V4 yedeğinden geri yüklenebilir.`))return;
  try{
    const snap=await snapshotProject(p);
    trashCache=(await pGet("trash",[])).filter(x=>x.packageName!==snap.packageName);
    trashCache.unshift(snap);
    await pSet("trash",trashCache);
    await api(`/api/projects/${p.id}`,{method:"DELETE"});
    if(activeProjectId===p.id){activeProjectId=null;activeProject=null;updateProjectBadges()}
    await loadProjects();await loadTrash();
  }catch(e){alert(e.message)}
}
async function loadTrash(){
  const now=Date.now(),ttl=30*24*60*60*1000;
  trashCache=(await pGet("trash",[])).filter(x=>now-Number(x.deletedAt||0)<ttl);
  await pSet("trash",trashCache);
  if(!$("trashList"))return;
  $("trashList").innerHTML=trashCache.map((x,i)=>`<div class="card"><strong>${esc(x.name)}</strong><div class="muted">${esc(x.packageName)}</div><div class="muted">${Math.max(0,30-Math.floor((now-x.deletedAt)/86400000))} gün içinde otomatik silinecek</div><div class="row"><button data-restore="${i}">Geri yükle</button><button class="danger ghost" data-purge="${i}">Kalıcı Sil</button></div></div>`).join("")||"<div class='card muted'>Çöp kutusu boş.</div>";
  qsa("[data-restore]",$("trashList")).forEach(b=>b.onclick=()=>restoreTrash(Number(b.dataset.restore)));
  qsa("[data-purge]",$("trashList")).forEach(b=>b.onclick=()=>purgeTrash(Number(b.dataset.purge)));
}
async function restoreTrash(index){
  try{
    const x=trashCache[index];if(!x)return;
    const p=await createProjectRecord(x.name,x.packageName,x.config,false);
    activeProjectId=p.id;activeProject=p;files=x.files||starterFiles("blank",x.name);await autosaveForce();
    for(const loc of x.localizations||[])await api(`/api/projects/${p.id}/localizations/${encodeURIComponent(loc.locale)}`,{method:"PUT",body:{strings:loc.strings||{}}});
    trashCache.splice(index,1);await pSet("trash",trashCache);await loadTrash();await loadProjects();showPanel("idePanel");
  }catch(e){alert(e.message)}
}
async function purgeTrash(index){if(!confirm("Bu yerel yedek kalıcı silinsin mi?"))return;trashCache.splice(index,1);await pSet("trash",trashCache);loadTrash()}
window.loadTrash=loadTrash;

window.exportProjectBackup=async function exportProjectBackup(){
  try{
    if(!activeProject)throw new Error("Önce proje seç.");
    const f=await ensureFflate();
    if(editor)files[activeFile]=editor.getValue();
    let localizations=[];try{localizations=(await api(`/api/projects/${activeProjectId}/localizations`)).localizations||[]}catch{}
    const manifest={format:"appforge-backup",version:4,createdAt:new Date().toISOString(),name:activeProject.name,packageName:activeProject.package_name,config:safeConfigForProject(collectBuilderConfig()),localizations};
    const entries={"manifest.json":textEncoder.encode(pretty(manifest))};
    for(const [path,content] of Object.entries(files))entries["files/"+path]=textEncoder.encode(content);
    const zipped=f.zipSync(entries,{level:1});
    downloadBlob(new Blob([zipped],{type:"application/zip"}),`${safeFileName(activeProject.name)}.appforge.zip`);
  }catch(e){alert(e.message)}
};
$("backupInput").addEventListener("change",async()=>{
  try{
    const file=$("backupInput").files?.[0];if(!file)return;
    const z=await readZip(file);if(!z["manifest.json"])throw new Error("AppForge backup manifesti yok.");
    const m=JSON.parse(textDecoder.decode(z["manifest.json"]));if(m.format!=="appforge-backup")throw new Error("Geçersiz backup.");
    files={};for(const [name,bytes] of Object.entries(z))if(name.startsWith("files/")){const t=decodeMaybe(bytes,20*1024*1024);if(t!=null)files[name.slice(6)]=t}
    const p=await createProjectRecord(m.name,m.packageName,m.config||{},true);
    for(const loc of m.localizations||[])await api(`/api/projects/${p.id}/localizations/${encodeURIComponent(loc.locale)}`,{method:"PUT",body:{strings:loc.strings||{}}});
    fillBuilder(m.config||{});showPanel("idePanel");
  }catch(e){alert(e.message)}finally{$("backupInput").value=""}
});

window.githubImport=async function githubImport(){
  if(!activeProjectId){setText("githubResult","Önce bir proje aç/kaydet.");return}
  setText("githubResult","İçe aktarılıyor...");
  try{
    const j=await api(`/api/projects/${activeProjectId}/github/import`,{method:"POST",body:{repoUrl:val("githubRepo"),ref:val("githubRef"),token:val("githubToken")}});
    $("githubToken").value="";
    setText("githubResult",`${j.owner}/${j.repo}@${j.ref}: ${j.importedFiles} metin dosyası aktarıldı, ${j.skippedBinary} binary atlandı.`);
    await openProject(activeProject);
  }catch(e){setText("githubResult",e.message)}
};

window.createTeam=async function createTeam(){try{await api("/api/teams",{method:"POST",body:{name:val("teamName")}});$("teamName").value="";loadTeams()}catch(e){alert(e.message)}};
async function loadTeams(){
  if(!token)return;
  try{
    const j=await api("/api/teams");$("teamList").innerHTML="";
    for(const t of j.teams){
      const d=document.createElement("div");d.className="card";
      d.innerHTML=`<strong>${esc(t.name)}</strong><div class="muted">${esc(t.slug)}</div><span class="pill">${esc(t.role)}</span><div><button>Bu Takımda Çalış</button></div>`;
      d.querySelector("button").onclick=async()=>{activeTeamId=t.id;setText("activeTeamLabel",`Aktif takım: ${t.name}`);await loadTeamMembers();await loadProjects();await loadBuilds()};
      $("teamList").appendChild(d);
    }
  }catch(e){$("teamList").textContent=e.message}
}
window.loadTeams=loadTeams;
async function loadTeamMembers(){
  if(!activeTeamId){setText("teamMembers","Takım seçilmedi.");return}
  try{
    const j=await api(`/api/teams/${activeTeamId}/members`);
    $("teamMembers").innerHTML=(j.members||[]).map(m=>`<div class="card"><b>${esc(m.email||m.display_name||m.user_id)}</b><div class="muted">${esc(m.role||"member")}</div></div>`).join("");
  }catch(e){setText("teamMembers",e.message)}
}
window.inviteMember=async function inviteMember(){
  if(!activeTeamId){alert("Önce takım seç.");return}
  try{
    const j=await api(`/api/teams/${activeTeamId}/invites`,{method:"POST",body:{email:val("inviteEmail"),role:val("inviteRole")}});
    alert("Davet oluşturuldu."+ (j.token?`\nToken: ${j.token}`:""));
  }catch(e){alert(e.message)}
};

async function loadLocalizationsUi(){
  if(!activeProjectId){$("localizationList").innerHTML="<span class='muted'>Önce proje seç.</span>";return}
  try{
    const j=await api(`/api/projects/${activeProjectId}/localizations`);
    $("localizationList").innerHTML=(j.localizations||[]).map(x=>`<button class="ghost full locBtn" data-locale="${esc(x.locale)}">${esc(x.locale)} • ${new Date(x.updated_at).toLocaleString()}</button>`).join("")||"<span class='muted'>Dil kaydı yok.</span>";
    qsa(".locBtn",$("localizationList")).forEach(b=>b.onclick=()=>{
      const x=j.localizations.find(v=>v.locale===b.dataset.locale);$("localizationLocale").value=x.locale;$("localizationJson").value=pretty(x.strings||{});
    });
  }catch(e){$("localizationList").textContent=e.message}
}
window.loadLocalizationsUi=loadLocalizationsUi;
window.saveLocalizationUi=async function saveLocalizationUi(){
  if(!activeProjectId){alert("Önce proje seç.");return}
  try{
    const strings=JSON.parse(val("localizationJson")||"{}");
    await api(`/api/projects/${activeProjectId}/localizations/${encodeURIComponent(val("localizationLocale"))}`,{method:"PUT",body:{strings}});
    await loadLocalizationsUi();
  }catch(e){alert(e.message)}
};

async function loadMe(){
  if(!token)return;
  try{
    const j=await api("/api/auth/me");currentUser=j.user;
    setText("emailState",j.user.emailVerified?"E-posta doğrulandı.":"E-posta doğrulanmadı.");
    setText("accountInfo",`${j.user.displayName||""}\n${j.user.email}\nRol: ${j.user.role}\n2FA: ${j.user.twoFactorEnabled?"Açık":"Kapalı"}`);
  }catch{}
}
window.resendVerification=async function resendVerification(){try{const j=await api("/api/auth/resend-verification",{method:"POST",body:{}});alert(j.alreadyVerified?"E-posta zaten doğrulanmış.":"Doğrulama e-postası gönderildi.")}catch(e){alert(e.message)}};
window.setup2fa=async function setup2fa(){try{const j=await api("/api/auth/2fa/setup",{method:"POST",body:{}});$("qrWrap").innerHTML=`<img src="${j.qrDataUrl}" style="width:260px;max-width:100%;background:#fff;padding:8px;border-radius:12px"><div class="muted">${esc(j.secret)}</div>`}catch(e){setText("qrWrap",e.message)}};
window.confirm2fa=async function confirm2fa(){try{await api("/api/auth/2fa/confirm",{method:"POST",body:{code:val("confirm2faCode")}});alert("2FA etkin.");loadMe()}catch(e){alert(e.message)}};
window.disable2faUi=async function disable2faUi(){
  const code=prompt("2FA kapatmak için mevcut authenticator kodunu yaz:");
  if(!code)return;
  try{await api("/api/auth/2fa",{method:"DELETE",body:{code}});alert("2FA kapatıldı.");loadMe()}catch(e){alert(e.message)}
};
async function loadProStatus(){
  if(!token)return;
  try{
    const j=await api("/api/pro/status");
    setText("proStatus",j.active?`PRO aktif${j.expiresAt?` • ${new Date(j.expiresAt).toLocaleString()}`:""}`:"Free plan");
    setText("homePlan",j.active?"PRO":"FREE");
  }catch(e){setText("proStatus",`Web/Windows doğrulama: ${e.message}`)}
}
window.loadProStatus=loadProStatus;
async function loadApiTokens(){
  if(!token)return;
  try{
    const j=await api("/api/auth/api-tokens");
    $("apiTokenList").innerHTML=(j.tokens||[]).map(t=>`<div class="card"><b>${esc(t.name)}</b><div class="muted">${esc((t.scopes||[]).join(", "))}</div><button class="danger ghost revokeToken" data-id="${esc(t.id)}">Sil</button></div>`).join("");
    qsa(".revokeToken",$("apiTokenList")).forEach(b=>b.onclick=async()=>{await api(`/api/auth/api-tokens/${encodeURIComponent(b.dataset.id)}`,{method:"DELETE"});loadApiTokens()});
  }catch(e){setText("apiTokenList",e.message)}
}
window.createApiTokenUi=async function createApiTokenUi(){
  try{
    const j=await api("/api/auth/api-tokens",{method:"POST",body:{name:val("apiTokenName")||"Studio V4 Token",scopes:["build:read","build:write"]}});
    setText("newApiToken",j.token?`Bu tokenı şimdi kaydet; tekrar gösterilmeyebilir:\n${j.token}`:"Token oluşturuldu.");
    await loadApiTokens();
  }catch(e){setText("newApiToken",e.message)}
};

async function loadSettings(){
  studioSettings={...studioSettings,...await pGet("settings",{})};
  $("settingTheme").value=studioSettings.theme;$("settingLanguage").value=studioSettings.language;$("settingAccent").value=studioSettings.accent;
  applySettings();
}
window.saveSettings=async function saveSettings(){
  studioSettings={theme:val("settingTheme"),language:val("settingLanguage"),accent:val("settingAccent")};
  await pSet("settings",studioSettings);applySettings();alert("Ayarlar kaydedildi.");
};
function applySettings(){
  let light=studioSettings.theme==="light";
  if(studioSettings.theme==="system")light=matchMedia("(prefers-color-scheme: light)").matches;
  document.body.classList.toggle("light",light);
  document.documentElement.style.setProperty("--accent",studioSettings.accent||"#6172ff");
  document.documentElement.dir=studioSettings.language==="ar"?"rtl":"ltr";
  if(editor)monaco.editor.setTheme(light?"vs":"vs-dark");
}

function redactText(text){
  return String(text||"")
    .replace(/(password|storePassword|keyPassword|token|apiKey|secret)\s*[:=]\s*[^\s,;]+/gi,"$1=[REDACTED]")
    .slice(0,16000);
}
function projectContext(){
  const c=safeConfigForProject(collectBuilderConfig());
  const log=redactText($("liveBuildLog").textContent).slice(-6000);
  return {projectId:activeProjectId,name:activeProject?.name||c.appName,packageName:activeProject?.package_name||c.packageName,sourceTechnology:c.sourceTechnology,buildOutput:c.buildOutput,permissions:{camera:c.camera,microphone:c.microphone,location:c.location,notifications:c.notifications,nfc:c.nfc,additional:c.additionalPermissions},firebase:{analytics:c.firebaseAnalyticsEnabled,crashlytics:c.firebaseCrashlyticsEnabled,messaging:c.firebaseMessagingEnabled},buildLogTail:log};
}
function offlineAdvice(prompt){
  const p=prompt.toLowerCase();
  const ctx=projectContext();
  const lines=[];
  if(/build|hata|gradle|worker|failed/.test(p)){
    lines.push("Build kontrolü: Build Kontrol ekranındaki canlı logun son hata satırını ve Test Lab sonucunu karşılaştır.");
    if(ctx.buildLogTail)lines.push("Son log özeti:\n"+ctx.buildLogTail.slice(-1600));
  }
  if(/firebase|fcm|bildirim/.test(p)){
    lines.push("Firebase: google-services.json içindeki package_name, Builder paket adıyla aynı olmalı. Messaging için Firebase Messaging seçeneğini aç.");
  }
  if(/izin|permission|kamera|mikrofon|nfc|konum/.test(p)){
    lines.push("İzinler: yalnız kullanılan izinleri aç. Kaynak ZIP analizi kamera, mikrofon, konum, bildirim, ağ, wake lock ve NFC işaretlerini otomatik bulabilir.");
  }
  if(/apk|aab|exe|dönüş|convert/.test(p)){
    lines.push("Çıktı: Play Store için AAB, cihaz testi için APK, Windows için EXE seç. APK↔EXE dönüşüm yalnız AppForge dönüşüm manifesti/payload'ı içeren çıktılarda çalışır.");
  }
  if(/güven|security|bridge|mixed/.test(p)){
    lines.push("Güvenlik: Mixed Content ve Remote Bridge varsayılan kapalı kalsın. Gizli signing parolaları proje ayarlarına veya backup'a yazılmaz.");
  }
  if(/pro|satın|billing/.test(p)){
    lines.push("Pro satın alma Google Play Billing üzerinden Android uygulamasında yapılır; Windows/Web sunucudaki entitlement durumunu görüntüler.");
  }
  if(!lines.length)lines.push(`Aktif proje: ${ctx.name||"seçilmedi"} (${ctx.sourceTechnology||"unknown"}). Builder, Preview, Build Kontrol ve Test Lab verilerini birlikte kontrol et. Sorunu veya hedefi daha spesifik yazarsan ilgili ekranı önerebilirim.`);
  return lines.join("\n\n");
}
function renderAi(){
  $("aiMessages").innerHTML=aiHistory.map(m=>`<div class="msg ${m.role==="user"?"user":"assistant"}">${esc(m.content)}</div>`).join("");
  $("aiMessages").scrollTop=$("aiMessages").scrollHeight;
}
function updateAiProjectLabel(){setText("aiProjectLabel","Analiz edilen proje: "+(activeProject?.name||"seçilmedi"))}
window.askAiPreset=function askAiPreset(p){$("aiPrompt").value=p;sendAi()};
window.sendAi=async function sendAi(){
  const prompt=val("aiPrompt").trim();if(!prompt)return;
  $("aiPrompt").value="";
  aiHistory.push({role:"user",content:prompt});renderAi();
  let answer="";
  if(desktopLocalAi?.chat){
    try{
      const system=`Sen AppForge Studio yerel proje asistanısın. Kısa, teknik ve güvenli cevap ver. Gizli bilgi isteme. Proje bağlamı: ${bool("aiProjectContext")?redactText(pretty(projectContext())):"kullanılmıyor"}`;
      const messages=[{role:"system",content:system},...aiHistory.slice(-10).map(x=>({role:x.role,content:x.content}))];
      const j=await desktopLocalAi.chat({model:val("aiModel"),messages});
      answer=j.text||"";
    }catch(e){answer=`Yerel model kullanılamadı (${e.message}).\n\n${offlineAdvice(prompt)}`}
  }else answer=offlineAdvice(prompt);
  aiHistory.push({role:"assistant",content:answer});renderAi();
  await pSet("aiHistory",aiHistory.slice(-40));
};

async function updateDesktopSecurityState(){
  if(!desktop?.security?.getState){
    setText("desktopSecurityState","Web modu: yalnız tarayıcı depolaması kullanılır; signing parolaları kalıcı saklanmaz.");
    return;
  }
  try{
    const state=await desktop.security.getState();
    setText(
      "desktopSecurityState",
      state?.safeStorage
        ? "Windows safeStorage aktif. Oturum ve isteğe bağlı kasa verileri şifreli saklanır."
        : "Windows güvenli kasası şu anda kullanılamıyor. Oturum bu pencere açıkken çalışır; yeniden açınca tekrar giriş gerekebilir."
    );
  }catch(e){
    console.warn("Desktop security state unavailable",e);
    setText("desktopSecurityState","Windows güvenli kasa durumu okunamadı.");
  }
}

async function loadTrashAndAi(){
  aiHistory=await pGet("aiHistory",[]);
  renderAi();
}

document.addEventListener("input",e=>{
  if(e.target.closest("#builderPanel"))updateBuilderSummary();
});
$("workspaceSearch").addEventListener("keydown",e=>{if(e.key==="Enter")searchWorkspace()});
$("sourceZipInput").addEventListener("change",()=>{pendingProjectFile=$("sourceZipInput").files?.[0]||null});
$("bProjectFile").addEventListener("change",async()=>{const f=$("bProjectFile").files?.[0];if(f)try{await analyzeZipFile(f)}catch{}});
$("backupInput").value="";

wireNavigation();
addFeatureHelp();
renderBuilderStep();
loadMonacoLoader()
  .then(()=>initMonaco())
  .catch(error=>{
    console.error(error);
    setText("autosaveStatus","Editör yüklenemedi");
    $("autosaveStatus").className="status red";
    renderTree();preview();
  });

resumeSession().then(loadTrashAndAi);
})();
