import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
const read=p=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const engine=read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
const fast=read('android-app/app/src/main/assets/device-build/FastActivity.java');
const tiny=read('android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java');
const packager=read('android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPackager.kt');
const doc=read('android-app/app/src/main/assets/device-build/AppForgeDocuments.java');
test('normal wrapper serializes actual draft/runtime contract without invented flags',()=>{
 for(const [key,value] of Object.entries({nativeBridge:'javascriptBridge',nativeBridgeAllowRemote:'remoteBridgeAllowed',shareBridge:'shareBridge',clipboardBridge:'clipboardBridge',vibrationBridge:'vibrationBridge',qrScanner:'qrScanner',versionName:'versionName'})){
  assert.ok(engine.includes(`.put("${key}", draft.${value})`),key);assert.ok(fast.includes(`"${key}"`),key);
 }
 assert.ok(engine.includes('.put("hasCustomIcon", !draft.iconUri.isNullOrBlank())'));
 assert.doesNotMatch(engine,/\.put\("watermark", draft\./); // ProjectDraft has no watermark field.
 for(const old of ['share','clipboard','vibration'])assert.ok(!engine.includes(`.put("${old}", draft.`));
 assert.match(engine,/AppForgeDocuments\.java.*copyTo/);
});
test('both routes configure document downloads/share and fail-closed remote opt-in',()=>{
 for(const key of ['downloads','nativeBridge','shareBridge','nativeBridgeAllowRemote'])assert.ok(packager.includes(`.put("${key}", draft.`));
 for(const activity of [fast,tiny]){
  assert.match(activity,/config\.optBoolean\("nativeBridgeAllowRemote", false\)/);
  assert.match(activity,/AppForgeDocuments\.httpsOrigin/);
  assert.match(activity,/documents\.pageStarted\(\)/);
  assert.match(activity,/documents\.pageFinished\(url\)/);
  assert.match(activity,/documents\.activityResult/);
  assert.match(activity,/documents\.pause\(\)/);
  assert.match(activity,/documents\.close\(\)/);
  if(activity===tiny){
   assert.match(activity,/addJavascriptInterface\(new LocalDocuments\(\), "AppForgeLocalDocuments"\)/);
   assert.match(activity,/current\.receiveLocal\(token,raw\)/);
   assert.match(activity,/current\.matchesCompatToken\(token\)/);
  }
 }
 assert.match(fast,/addJavascriptInterface\(new NativeCompat\(\), "AppForgeNativeCompat"\)/);
 assert.match(fast,/current\.matchesCompatToken\(token\)/);
 assert.match(fast,/current\.allowsCompat\(token, gesture\)/);
 assert.match(doc,/MessageDigest\.isEqual/);
 assert.match(doc,/postWebMessage\(new WebMessage\(token/);
 assert.match(doc,/Uri\.parse\(origin\)/);
 assert.match(doc,/uri\.getUserInfo\(\)!=null/);
 assert.match(doc,/source==port/);
 assert.match(doc,/current==generation/);
 assert.doesNotMatch(doc,/Uri\.parse\("\*"\)/);
});
test('both wrapper helpers remain byte-identical apart from package',()=>{
 for(const name of ['AppForgeDocuments.java','AppForgeLocalAssets.java','AppForgeShareProvider.java']){
  const a=read('android-app/app/src/main/assets/device-build/'+name).replace(/package [^;]+;/,'');
  const b=read('android-app/html-apk-template/src/main/java/com/appforge/htmlstub/'+name).replace(/package [^;]+;/,'');
  assert.equal(a,b,name);
 }
});
test('SAF export is permission-free and shares actual content with read grant',()=>{
 assert.match(doc,/Intent\.ACTION_CREATE_DOCUMENT/);
 assert.match(doc,/"content"\.equalsIgnoreCase\(uri\.getScheme\(\)\)/);
 assert.match(doc,/openOutputStream\(uri,"wt"\)/);
 assert.match(doc,/Intent\.EXTRA_STREAM,uri/);
 assert.match(doc,/send\.setClipData\(ClipData\.newRawUri\(filename,uri\)\)/);
 assert.match(doc,/Intent\.FLAG_GRANT_READ_URI_PERMISSION/);
 assert.doesNotMatch(doc,/WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|file:\/\//);
 assert.match(tiny,/setAllowFileAccess\(false\)/);
 assert.match(tiny,/new Uri\.Builder\(\)\.scheme\("file"\)\.path\(localPath\)/);
 assert.match(tiny,/String localPath = "\/android_asset\/site\/" \+ entry/);
 assert.match(doc,/CookieManager\.getInstance\(\)\.getCookie\(url\)/);
 assert.match(doc,/setDestinationInExternalFilesDir/);
});
test('debug workflow explicitly compiles both generated wrapper sources',()=>{
 const workflow=read('.github/workflows/android-debug.yml');
 assert.match(workflow,/Compile generated Android wrappers/);
 assert.match(workflow,/javac/);
 assert.match(workflow,/app\/src\/main\/assets\/device-build\/\*\.java/);
 assert.match(workflow,/html-apk-template\/src\/main\/java\/com\/appforge\/htmlstub\/\*\.java/);
 assert.match(workflow,/html_document_bridge_runtime\.test\.js/);
});

test('updates retain both original storage namespaces and never clear business data',()=>{
 const base='44614aa3c77925934b1eaf43ffafdf2dcbfe9fba';
 const previous=p=>execFileSync('git',['show',base+':'+p],{encoding:'utf8'});
 assert.match(engine,/\.put\("assetHttpsEnabled", \(nodeWebAssets \|\| draft\.microphone\) && draft\.sourceMode == SourceMode\.LOCAL\)/);
 assert.doesNotMatch(engine,/localDocumentOrigin/);
 assert.match(fast,/config\.optBoolean\("assetHttpsEnabled", false\) \? "https:\/\/appassets.androidplatform.net" : "legacy-file"/);
 const tinyPath='android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java';
 const assetPath='android-app/app/src/main/assets/device-build/AppForgeLocalAssets.java';
 const load=s=>s.match(/String localPath = "([^"\n]+)" \+ entry;\s*web.loadUrl\((new Uri.Builder\(\).*?)\);/s)?.slice(1);
 assert.ok(load(previous(tinyPath)));
 assert.deepEqual(load(tiny),load(previous(tinyPath))); // Includes file scheme, full path and escaping builder.
 const start=s=>s.match(/START_URL\s*=\s*"([^"\n]+)"/)[1];
 assert.equal(start(read(assetPath)),start(previous(assetPath)));
 assert.equal(start(read(assetPath)),'https://appassets.androidplatform.net/assets/site/index.html');
 for(const text of [fast,tiny,doc])assert.doesNotMatch(text,/clearCache\(|clearHistory\(|deleteDatabase\(|WebStorage.*delete|localStorage\.clear|indexedDB\.deleteDatabase/);
});
