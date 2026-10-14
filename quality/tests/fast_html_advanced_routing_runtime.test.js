import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
const root = path.resolve(import.meta.dirname, '../..');
const build = 'android-app/app/src/main/java/com/appforge/studio/build/';
const read = p => fs.readFileSync(path.join(root,p),'utf8');

test('real Kotlin policy enforces output-aware settings and Android URL security', () => {
 const dir = fs.mkdtempSync(path.join(os.tmpdir(),'appforge-routing-'));
 try {
 fs.writeFileSync(path.join(dir,'BuildConfig.kt'),'package com.appforge.studio\nobject BuildConfig { const val DEBUG = true }');
 fs.writeFileSync(path.join(dir,'Check.kt'), `package com.appforge.studio.build
import com.appforge.studio.model.*
fun main() {
 val base = ProjectDraft(appName="Test", sourceTechnology="html", importedFolder="/test", startPage="index.html")
 check(FastHtmlApkPolicy.isCandidate(base))
 FastHtmlApkPolicy.requireSupported(base)
 check(FastHtmlApkPolicy.isCandidate(base.copy(sourceMode=SourceMode.URL,webUrl="https://example.com")))
 val advanced = listOf(base.copy(iconUri="content://icon"),base.copy(camera=true),base.copy(microphone=true),base.copy(notifications=true),base.copy(location=true),base.copy(nfc=true),base.copy(wakeLock=true),base.copy(deepLinkEnabled=true,deepLinkHost="example.com"),base.copy(additionalPermissions=setOf("READ_CONTACTS")))
 advanced.forEach { check(!FastHtmlApkPolicy.isCandidate(it)); WebviewProjectSettings.validate(it) }
 listOf("apk","aab","both","apk+aab").forEach { check(FastHtmlApkPolicy.isCandidate(base.copy(buildOutput=it))) }
 listOf("exe","all","apk+aab+exe","native-exe").forEach { check(!FastHtmlApkPolicy.isCandidate(base.copy(buildOutput=it))) }
 listOf("node-web","react-native","expo","python-android","android-gradle").forEach { check(!FastHtmlApkPolicy.isCandidate(base.copy(sourceBuildEngine=it))) }
 check(WebviewProjectSettings.additionalPermissions(base.copy(additionalPermissions=setOf("android.permission.READ_CONTACTS","READ_CONTACTS"))) == listOf("READ_CONTACTS"))
 check(runCatching { WebviewProjectSettings.validate(base.copy(sourceMode=SourceMode.URL,webUrl="http://example.com",microphone=true)) }.isFailure)
 check(runCatching { WebviewProjectSettings.validate(base.copy(additionalPermissions=setOf("INVENTED_PERMISSION"))) }.exceptionOrNull()?.message?.contains("INVENTED_PERMISSION") == true)
 listOf(base.copy(deepLinkEnabled=true,deepLinkScheme="javascript",deepLinkHost="example.com"),base.copy(deepLinkEnabled=true,deepLinkHost="example.com@evil.com"),base.copy(deepLinkEnabled=true,deepLinkHost="example.com",deepLinkPathPrefix="bad")).forEach { check(runCatching { WebviewProjectSettings.validate(it) }.isFailure) }

 // Exercise the production Kotlin validator, including output aliases and foreign engines.
 fun rejected(draft: ProjectDraft, message: String? = null) {
  val error = runCatching { WebviewProjectSettings.validate(draft) }.exceptionOrNull()
  check(error is IllegalArgumentException) { "Expected settings rejection" }
  if (message != null) check(error.message?.contains(message) == true)
 }
 val urlBase = base.copy(sourceMode=SourceMode.URL, webUrl="https://example.com/path")
 val urlAdvanced = listOf(urlBase.copy(iconUri="content://icon"), urlBase.copy(camera=true),
  urlBase.copy(location=true), urlBase.copy(microphone=true),
  urlBase.copy(deepLinkEnabled=true, deepLinkHost="example.com"),
  urlBase.copy(additionalPermissions=setOf("READ_CONTACTS")))
 listOf("apk", "aab", "both", "apk+aab", "android", "all", "apk+aab+exe").forEach { output ->
  urlAdvanced.forEach { advancedUrl ->
   val draft = advancedUrl.copy(buildOutput=output)
   check(!FastHtmlApkPolicy.isCandidate(draft))
   WebviewProjectSettings.validate(draft)
   listOf("http://example.com", "https://user:pass@example.com", "https://@example.com",
    "https:///missing-host", "https://bad_host", "https://example.com/invalid space",
    "https://example.com/" + "a".repeat(2048)).forEach { unsafe ->
    rejected(draft.copy(webUrl=unsafe))
   }
  }
  listOf("webview-static", "node-web").forEach { engine ->
   val draft = base.copy(buildOutput=output, sourceBuildEngine=engine)
   rejected(draft.copy(additionalPermissions=setOf("INVENTED_PERMISSION")), "INVENTED_PERMISSION")
   listOf(draft.copy(deepLinkEnabled=true, deepLinkScheme="javascript", deepLinkHost="example.com"),
    draft.copy(deepLinkEnabled=true, deepLinkHost="example.com@evil.com"),
    draft.copy(deepLinkEnabled=true, deepLinkHost="example.com", deepLinkPathPrefix="bad")).forEach { rejected(it) }
   WebviewProjectSettings.validate(draft.copy(deepLinkEnabled=true, deepLinkHost="example.com", deepLinkPathPrefix="/open"))
   rejected(draft.copy(sourceMode=SourceMode.URL, webUrl="http://example.com", microphone=true))
   WebviewProjectSettings.validate(draft.copy(sourceMode=SourceMode.URL, webUrl="https://example.com", microphone=true))
   rejected(draft.copy(sourceMode=SourceMode.URL, webUrl="https://user:pass@example.com", microphone=true))
  }
 }
 listOf("exe", "windows-exe", "portable-exe", "windows-portable-exe", "native-exe", "windows-native-exe").forEach { output ->
  listOf("webview-static", "node-web").forEach { engine ->
   WebviewProjectSettings.validate(urlBase.copy(buildOutput=output, sourceBuildEngine=engine,
    webUrl="http://user:pass@example.com", microphone=true, additionalPermissions=setOf("INVENTED_PERMISSION"),
    deepLinkEnabled=true, deepLinkScheme="javascript", deepLinkHost="invalid@host", deepLinkPathPrefix="bad"))
  }
 }
 listOf("react-native", "expo", "python-android", "android-gradle", "windows-native", "universal-cross-platform").forEach { engine ->
  WebviewProjectSettings.validate(urlBase.copy(sourceBuildEngine=engine, webUrl="http://user:pass@example.com",
   microphone=true, additionalPermissions=setOf("INVENTED_PERMISSION"), deepLinkEnabled=true, deepLinkScheme="javascript"))
 }
 WebviewProjectSettings.validate(urlBase.copy(sourceBuildEngine="node-web", webUrl="http://example.com", camera=true))
 WebviewProjectSettings.validate(urlBase.copy(sourceBuildEngine=" WEBVIEW-STATIC "))
 WebviewProjectSettings.validate(urlBase.copy(sourceBuildEngine=""))
 check(runCatching { FastHtmlApkPolicy.requireSupported(urlBase.copy(webUrl="http://example.com")) }.isFailure)
 check(runCatching { FastHtmlApkPolicy.requireSupported(urlBase.copy(webUrl="https://user:pass@example.com")) }.isFailure)
 listOf("aab", "both", "apk+aab").forEach { output ->
  check(FastHtmlApkPolicy.isCandidate(base.copy(buildOutput=output)))
  check(runCatching { FastHtmlApkPolicy.requireSupported(base.copy(buildOutput=output)) }.isFailure)
 }
 println("routing/settings assertions passed")
}`);
 execFileSync('kotlinc',[path.join(root,'android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt'), ...['FastHtmlApkPolicy.kt','DeviceBuildCapabilities.kt','WebviewProjectSettings.kt'].map(p=>path.join(root,build,p)),path.join(dir,'BuildConfig.kt'),path.join(dir,'Check.kt'),'-include-runtime','-d',path.join(dir,'check.jar')], {timeout:120000});
 assert.match(execFileSync('java',['-jar',path.join(dir,'check.jar')],{encoding:'utf8'}),/assertions passed/);
 } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});

test('normal wrapper carries settings to manifest and runtime; shared preflight fails closed', () => {
 const engine=read(build+'DeviceBuildEngine.kt'), offline=read(build+'OfflineBuildPackManager.kt');
 for (const key of ['microphone','deepLinkEnabled','deepLinkScheme','deepLinkHost','deepLinkPathPrefix']) assert.ok(engine.includes('.put("'+key+'", draft.'+key+')'));
 assert.match(engine,/android\.permission\.RECORD_AUDIO/);
 assert.match(engine,/android:pathPrefix="\$\{xml\(draft.deepLinkPathPrefix\)\}"/);
 assert.match(engine,/WebviewProjectSettings.additionalPermissions\(draft\)/);
 assert.ok(offline.indexOf('WebviewProjectSettings.validate(draft)') < offline.indexOf('if (FastHtmlApkPolicy.isCandidate(draft))'));
 const validation = engine.slice(engine.indexOf('private fun validateCapabilities('));
 assert.match(validation,/WebviewProjectSettings.validate\(draft\)/);
 assert.ok(engine.indexOf('validateCapabilities(draft)') < engine.indexOf('if (FastHtmlApkPolicy.isCandidate(draft))'));
 assert.match(engine,/if \(wantsAab\) \{[\s\S]*?FastHtmlAabUnsignedPackager.requireSource\(draft\)/);
 assert.match(offline,/APPFORGE_OFFLINE_BUILD_PACK_REQUIRED/);
 assert.match(engine,/DeviceProjectIcon.install\(context, draft, project\)/);
});

test('actual Java media handler denies disabled, denied and foreign requests and grants only selected resources', () => {
 const source=read('android-app/app/src/main/assets/device-build/FastActivity.java');
 const method=source.slice(source.indexOf('                public void onPermissionRequest('),source.indexOf('                @Override\n                public void onGeolocationPermissionsShowPrompt'));
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-media-'));
 try {
 fs.writeFileSync(path.join(dir,'MediaCheck.java'), `import java.util.*;
public class MediaCheck {
 static class Manifest { static class permission { static String CAMERA="camera", RECORD_AUDIO="audio"; } }
 static class PackageManager { static int PERMISSION_GRANTED=0; }
 static class Config { boolean camera, microphone; boolean optBoolean(String k, boolean d) { return k.equals("camera")?camera:k.equals("microphone")?microphone:d; } }
 static class PermissionRequest {
 static String RESOURCE_VIDEO_CAPTURE="video", RESOURCE_AUDIO_CAPTURE="audio";
 String origin="https://trusted.example"; String[] resources={"audio","video","unrelated"}; String[] grants; boolean denied;
 String getOrigin(){return origin;} String[] getResources(){return resources;}
 void deny(){denied=true;} void grant(String[] r){grants=r;}
 }
 Config config=new Config(); boolean audioAllowed, cameraAllowed;
 int checkSelfPermission(String p){return (p.equals("audio")?audioAllowed:cameraAllowed)?0:-1;}
 boolean isTrustedGeolocationOrigin(String o){return "https://trusted.example".equals(o);}
 void runOnUiThread(Runnable r){r.run();}
 ${method}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
 MediaCheck m=new MediaCheck(); PermissionRequest r=new PermissionRequest();
 m.audioAllowed=true; m.onPermissionRequest(r); check(r.denied);
 m.config.microphone=true; m.audioAllowed=false; r=new PermissionRequest(); m.onPermissionRequest(r); check(r.denied);
 m.audioAllowed=true; r=new PermissionRequest(); m.onPermissionRequest(r); check(Arrays.equals(r.grants,new String[]{"audio"}));
 m.config.camera=true; m.cameraAllowed=true; r=new PermissionRequest(); m.onPermissionRequest(r); check(Arrays.equals(r.grants,new String[]{"audio","video"}));
 r=new PermissionRequest(); r.origin="https://evil.example"; m.onPermissionRequest(r); check(r.denied);
 r=new PermissionRequest(); r.origin=null; m.onPermissionRequest(r); check(r.denied);
 r=new PermissionRequest(); r.resources=new String[]{"unrelated"}; m.onPermissionRequest(r); check(r.denied);
 System.out.println("media assertions passed");
 }
}`);
 execFileSync('javac',[path.join(dir,'MediaCheck.java')]);
 assert.match(execFileSync('java',['-cp',dir,'MediaCheck'],{encoding:'utf8'}),/media assertions passed/);
 assert.match(source,/config.optBoolean\("microphone", false\) &&\s*checkSelfPermission\(Manifest.permission.RECORD_AUDIO\)/);
 assert.match(source,/expected.getPort\(\) ==\s*actual.getPort\(\)/);
 assert.match(source,/actualHost == null/);
 assert.match(source,/isConfiguredDeepLink\(\s*uri\s*\)/);
 assert.match(source,/JSONObject.quote\(\s*link\s*\)/);
 } finally {fs.rmSync(dir,{recursive:true,force:true});}
});
