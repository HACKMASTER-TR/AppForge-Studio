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
 val base = ProjectDraft(fileUpload=false, clipboardBridge=false, vibrationBridge=false, networkState=false, splashEnabled=false, appName="Test", sourceTechnology="html", importedFolder="/test", startPage="index.html")
 check(FastHtmlApkPolicy.isCandidate(base))
 FastHtmlApkPolicy.requireSupported(base)
 check(!WizardReadinessEvaluator.evaluate(base.copy(packageName="com.appforge.htmlstub")).ready)
 check(!WizardReadinessEvaluator.evaluate(base.copy(packageName="com."+"x".repeat(256))).ready)
 check(FastHtmlApkPolicy.isCandidate(base.copy(sourceMode=SourceMode.URL,webUrl="https://example.com")))
 val advanced = listOf(base.copy(camera=true),base.copy(microphone=true))
 advanced.forEach { check(!FastHtmlApkPolicy.isCandidate(it)); WebviewProjectSettings.validate(it) }
 listOf("apk","aab","both","apk+aab","android","").forEach { check(FastHtmlApkPolicy.isCandidate(base.copy(buildOutput=it))) }
 listOf("exe","all","apk+aab+exe","native-exe").forEach { check(!FastHtmlApkPolicy.isCandidate(base.copy(buildOutput=it))) }
 listOf("node-web","react-native","expo","python-android","android-gradle").forEach { check(!FastHtmlApkPolicy.isCandidate(base.copy(sourceBuildEngine=it))) }
 check(WebviewProjectSettings.additionalPermissions(base.copy(additionalPermissions=setOf("android.permission.READ_CONTACTS","READ_CONTACTS"))) == listOf("READ_CONTACTS"))
 check(runCatching { WebviewProjectSettings.validate(base.copy(sourceMode=SourceMode.URL,webUrl="http://example.com",microphone=true)) }.isFailure)
 check(runCatching { WebviewProjectSettings.validate(base.copy(additionalPermissions=setOf("INVENTED_PERMISSION"))) }.exceptionOrNull()?.message?.contains("Desteklenmeyen Android izinleri") == true)
 listOf(base.copy(deepLinkEnabled=true,deepLinkScheme="javascript",deepLinkHost="example.com"),base.copy(deepLinkEnabled=true,deepLinkHost="example.com@evil.com"),base.copy(deepLinkEnabled=true,deepLinkHost="example.com",deepLinkPathPrefix="bad")).forEach { check(runCatching { WebviewProjectSettings.validate(it) }.isFailure) }

 listOf(base.copy(admobEnabled=true),base.copy(billingEnabled=true),
  base.copy(firebaseAnalyticsEnabled=true),base.copy(firebaseCrashlyticsEnabled=true),base.copy(firebaseMessagingEnabled=true),
  base.copy(umpConsentEnabled=true),base.copy(appCategory="business")).forEach {
  check(runCatching { WebviewProjectSettings.validate(it) }.isFailure)
 }
 // Supported WebView switches remain quick; each is consumed by runtime config and Tiny.
 listOf(base.copy(location=true),base.copy(mediaPlayerBridge=true),base.copy(deepLinkEnabled=true,deepLinkHost="example.com"),base.copy(nfc=true),base.copy(additionalPermissions=setOf("READ_CONTACTS")),base.copy(wakeLock=true),base.copy(iconUri="content://icons/selected"),base.copy(webJavaScriptEnabled=false),base.copy(webDomStorageEnabled=false),base.copy(webZoomEnabled=false),
  base.copy(webWideViewPortEnabled=false),base.copy(webOverviewModeEnabled=false),base.copy(webMediaAutoplayEnabled=false),
  base.copy(offlineCache=false),base.copy(webMixedContentAllowed=true),base.copy(downloads=false),base.copy(javascriptBridge=false),base.copy(shareBridge=false)).forEach {
  check(FastHtmlApkPolicy.isCandidate(it));FastHtmlApkPolicy.requireSupported(it)
 }
 // Exercise the production Kotlin validator, including output aliases and foreign engines.
 fun rejected(draft: ProjectDraft, message: String? = null) {
  val error = runCatching { WebviewProjectSettings.validate(draft) }.exceptionOrNull()
  check(error is IllegalArgumentException) { "Expected settings rejection" }
  if (message != null) check(error.message?.contains(message) == true)
 }
 val urlBase = base.copy(sourceMode=SourceMode.URL, webUrl="https://example.com/path")
 val urlAdvanced = listOf(urlBase.copy(camera=true),urlBase.copy(microphone=true),urlBase.copy(location=true),urlBase.copy(deepLinkEnabled=true,deepLinkHost="example.com"))
 listOf("apk", "aab", "both", "apk+aab", "android", "all", "apk+aab+exe").forEach { output ->
  urlAdvanced.forEach { advancedUrl ->
   val draft = advancedUrl.copy(buildOutput=output)
   check(FastHtmlApkPolicy.isCandidate(draft) == (output=="apk"))
   WebviewProjectSettings.validate(draft)
   listOf("http://example.com", "https://user:pass@example.com", "https://@example.com",
    "https:///missing-host", "https://bad_host", "https://example.com/invalid space",
    "https://example.com/" + "a".repeat(2048)).forEach { unsafe ->
    rejected(draft.copy(webUrl=unsafe))
   }
  }
  listOf("webview-static", "node-web").forEach { engine ->
   val draft = base.copy(buildOutput=output, sourceBuildEngine=engine)
   rejected(draft.copy(additionalPermissions=setOf("INVENTED_PERMISSION")), "Desteklenmeyen Android izinleri")
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
 // T21 aggregate diagnostics execute the production evaluator, never a mirrored JS policy.
 val complete = base.copy()
 check(WizardReadinessEvaluator.evaluate(complete).ready)
 check(WizardReadinessEvaluator.evaluate(complete).capability == WizardCapabilityClass.QUICK_SUPPORTED)
 check(WizardReadinessEvaluator.evaluate(complete.copy(fileUpload=true)).capability == WizardCapabilityClass.QUICK_SUPPORTED)
 val defaults = WizardReadinessEvaluator.evaluate(ProjectDraft(appName="Default", importedFolder="/test"))
 check(defaults.ready && defaults.capability == WizardCapabilityClass.QUICK_SUPPORTED)
 check(defaults.normalRouteReasons.isEmpty())
 check(defaults.normalRouteReasons.all { !it.contains("requires normal") })
 check(WizardReadinessEvaluator.evaluate(complete.copy(fileUpload=true)).normalRouteReasons.isEmpty())
 // V2: supported advanced combinations preserve all selections and share APK/AAB eligibility.
 val v2 = complete.copy(fileUpload=true,clipboardBridge=true,vibrationBridge=true,networkState=true,
  splashEnabled=true,splashText="Starting",fullscreen=true,orientation="portrait",backgroundColor="#123456")
 for(output in listOf("apk","aab","both","apk+aab")) {
  val selected=v2.copy(buildOutput=output); val before=selected.copy()
  check(WizardReadinessEvaluator.evaluate(selected).capability == WizardCapabilityClass.QUICK_SUPPORTED)
  check(selected == before)
  check(FastHtmlApkPolicy.templatePermissions(selected) == setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.VIBRATE"))
 }
 check(FastHtmlApkPolicy.isCandidate(v2.copy(notifications=true)))
 check(!FastHtmlApkPolicy.isCandidate(v2.copy(notifications=true,javascriptBridge=false)))
 check(!FastHtmlApkPolicy.isCandidate(v2.copy(notifications=true,webJavaScriptEnabled=false)))
 check(!FastHtmlApkPolicy.isCandidate(v2.copy(notifications=true,sourceMode=SourceMode.URL,webUrl="https://example.com")))
 check(FastHtmlApkPolicy.isCandidate(v2.copy(notifications=true,sourceMode=SourceMode.URL,webUrl="https://example.com",remoteBridgeAllowed=true)))
 check(FastHtmlApkPolicy.templatePermissions(v2.copy(notifications=true)).contains("android.permission.POST_NOTIFICATIONS"))
 check(FastHtmlApkPolicy.templatePermissions(v2.copy(javascriptBridge=false,networkState=false)) == setOf("android.permission.INTERNET"))

 // Routing regression matrix: Android reasons must never leak into another engine/target.
 for (mode in listOf(SourceMode.LOCAL, SourceMode.URL)) {
  for (output in listOf("apk", "aab", "both")) {
   val selected = complete.copy(sourceMode=mode, webUrl="https://example.com", buildOutput=output)
   val plain = WizardReadinessEvaluator.evaluate(selected)
   check(plain.normalRouteReasons.isNotEmpty() == (mode == SourceMode.URL && output != "apk"))
   advanced.forEach { toggled ->
    val fastWeb = mode==SourceMode.URL && output=="apk" && (toggled.camera || toggled.microphone || toggled.location)
    check(WizardReadinessEvaluator.evaluate(toggled.copy(sourceMode=mode,webUrl="https://example.com",buildOutput=output)).normalRouteReasons.isNotEmpty() != fastWeb)
   }
   check(WizardReadinessEvaluator.evaluate(ProjectDraft(appName="Defaults",importedFolder="/test",sourceMode=mode,webUrl="https://example.com",buildOutput=output)).normalRouteReasons.isNotEmpty() == (mode == SourceMode.URL && output != "apk"))
   for (engine in listOf("node-web", "expo", "android-gradle")) {
    for (upload in listOf(false,true)) {
     val other = WizardReadinessEvaluator.evaluate(selected.copy(sourceBuildEngine=engine,fileUpload=upload))
     check(other.normalRouteReasons.isEmpty())
     check(other.diagnostics.any { it.field == "toolchain" })
    }
   }
  }
 }
 for (output in listOf("exe", "all", "apk+aab+exe")) {
  for (upload in listOf(false,true)) {
   val selected = complete.copy(buildOutput=output,fileUpload=upload)
   check(FastHtmlApkPolicy.normalRouteReasons(selected).isEmpty())
   val result = WizardReadinessEvaluator.evaluate(selected)
   check(result.normalRouteReasons.isEmpty())
   check(result.diagnostics.any { it.message.contains("Windows Portable") })
  }
 }
 val nativeRouting = WizardReadinessEvaluator.evaluate(complete.copy(sourceBuildEngine="windows-native",buildOutput="native-exe",fileUpload=true))
 check(nativeRouting.ready && nativeRouting.normalRouteReasons.isEmpty())
 check(nativeRouting.diagnostics.any { it.message.contains("MinGW-w64") && it.message.contains("deneysel") })
 check(WizardReadinessEvaluator.evaluate(complete.copy(sourceTechnology="kotlin",startPage="Main.kt",fileUpload=true)).normalRouteReasons.isEmpty())
 check(WizardReadinessEvaluator.evaluate(complete).diagnostics.any { it.field == "runtimeChecks" && !it.blocking })

 val mixed = complete.copy(appCategory="game",primaryColor="#3F51B5",qrScanner=true,mediaPlayerBridge=true,
  admobEnabled=true,umpConsentEnabled=true,billingEnabled=true,firebaseAnalyticsEnabled=true,
  firebaseCrashlyticsEnabled=true,firebaseMessagingEnabled=true,deepLinkEnabled=true,
  deepLinkScheme="javascript",deepLinkHost="bad@host",deepLinkPathPrefix="bad",orientation="invalid",
  additionalPermissions=setOf("secret-do-not-echo"),minSdk=40,targetSdk=20,versionCode=0,
  packageName="bad",signingMode=SigningMode.CUSTOM)
 val report = WizardReadinessEvaluator.evaluate(mixed)
 check(!report.ready && report.affectedSteps == setOf(1,2,4,5,6,7,8,9))
 check(report.diagnostics.filter { it.blocking }.size >= 16)
 check(report.diagnostics.none { it.turkish().contains("secret-do-not-echo") })
 check(runCatching { report.requireReady() }.exceptionOrNull()?.message?.contains("firebaseMessagingEnabled") == true)
 check(mixed.appCategory == "game" && mixed.primaryColor == "#3F51B5")
 listOf("auto","game","none","invalid").forEach { category ->
  val result = WizardReadinessEvaluator.evaluate(complete.copy(appCategory=category))
  check(result.ready == (category != "invalid"))
 }
 listOf("#3F51B5","#8CC9F6","#ffffff","invalid").forEach { color ->
  check(WizardReadinessEvaluator.evaluate(complete.copy(primaryColor=color)).ready == (color != "invalid"))
 }
 for (engine in listOf("webview-static","node-web")) {
  for (output in listOf("apk","aab","both")) {
   for (category in listOf("auto","game","none")) {
    for (primary in listOf("#6B7CFF","#3F51B5","#8CC9F6","#123456")) {
     val appearance = complete.copy(sourceBuildEngine=engine,buildOutput=output,appCategory=category,primaryColor=primary)
     val result = WizardReadinessEvaluator.evaluate(appearance)
     check(result.diagnostics.none { it.field=="appCategory" && it.blocking })
     check(result.diagnostics.none { it.field=="primaryColor" && it.blocking })
     check(appearance.appCategory==category && appearance.primaryColor==primary)
    }
   }
  }
 }
 listOf("#07101F","#FFFFFF","#000000","#80112233").forEach { color ->
  check(WizardReadinessEvaluator.evaluate(complete.copy(backgroundColor=color,statusBarColor=color,navigationBarColor=color)).ready)
 }
 check(!WizardReadinessEvaluator.evaluate(complete.copy(backgroundColor="red")).ready)
 check(!WizardReadinessEvaluator.evaluate(complete.copy(buildOutput="invalid")).ready)
 check(!WizardReadinessEvaluator.evaluate(complete.copy(sourceMode=SourceMode.URL,webUrl="https://example.com:99999")).ready)
 listOf("aab","both","apk+aab","android","").forEach { output ->
  check(!FastHtmlApkPolicy.isCandidate(complete.copy(buildOutput=output,sourceMode=SourceMode.URL,webUrl="https://example.com")))
  check(!FastHtmlApkPolicy.isCandidate(complete.copy(buildOutput=output,minSdk=27)))
 }
 check(!WizardReadinessEvaluator.evaluate(complete.copy(sourceBuildEngine="expo",sourceBuildReady=false)).ready)
 for (engine in listOf("android-gradle","windows-native")) {
  val selected = complete.copy(sourceBuildEngine=engine,buildOutput=if(engine=="windows-native") "native-exe" else "both",admobEnabled=true,firebaseMessagingEnabled=true)
  val blocked = WizardReadinessEvaluator.evaluate(selected)
  check(!blocked.ready && blocked.diagnostics.count { it.blocking && it.step==6 } == 2)
 }
 // Integration identifiers/config do not make unsupported automatic injection real.
 for (id in listOf("", "invalid", "ca-app-pub-3940256099942544~3347511713")) {
  val selected = complete.copy(admobEnabled=true,admobAppId=id,admobBannerUnitId=id,admobInterstitialUnitId=id,admobRewardedUnitId=id)
  check(!WizardReadinessEvaluator.evaluate(selected).ready)
  check(WizardReadinessEvaluator.evaluate(selected.copy(admobEnabled=false)).ready)
 }
 for (config in listOf(null,"content://missing","content://configured")) {
  check(!WizardReadinessEvaluator.evaluate(complete.copy(firebaseConfigUri=config,firebaseAnalyticsEnabled=true)).ready)
  check(!WizardReadinessEvaluator.evaluate(complete.copy(firebaseConfigUri=config,firebaseCrashlyticsEnabled=true)).ready)
  check(!WizardReadinessEvaluator.evaluate(complete.copy(firebaseConfigUri=config,firebaseMessagingEnabled=true)).ready)
  check(WizardReadinessEvaluator.evaluate(complete.copy(firebaseConfigUri=config)).ready)
 }
 for (ids in listOf("", "invalid", "product_one")) {
  check(!WizardReadinessEvaluator.evaluate(complete.copy(billingEnabled=true,billingProductIds=ids,billingSubscriptionIds=ids)).ready)
  check(WizardReadinessEvaluator.evaluate(complete.copy(billingProductIds=ids,billingSubscriptionIds=ids)).ready)
 }
 val signed = complete.copy(signingMode=SigningMode.CUSTOM,buildOutput="aab",keystoreUri="content://key",keyAlias="alias",storePassword="sentinel-one",keyPassword="sentinel-two")
 check(WizardReadinessEvaluator.evaluate(signed).ready)
 check(WizardReadinessEvaluator.evaluate(signed).diagnostics.none { it.turkish().contains("sentinel") })
 // Capability disclosure is independent of output eligibility and runtime I/O readiness.
 val native = complete.copy(sourceBuildEngine="windows-native",buildOutput="native-exe")
 val nativeReport = WizardReadinessEvaluator.evaluate(native)
 check(nativeReport.ready && nativeReport.capability == WizardCapabilityClass.NORMAL_SUPPORTED)
 check(nativeReport.diagnostics.any { !it.blocking && it.message.contains("Deneysel") })
 check(DeviceBuildCapabilities.forEngine("windows-native")!!.support == DeviceBuildSupport.EXPERIMENTAL)
 listOf("apk","aab","both","all","exe").forEach {
  check(!WizardReadinessEvaluator.evaluate(native.copy(buildOutput=it)).ready)
 }
 listOf("exe","all","apk+aab+exe").forEach { output ->
  val portable = WizardReadinessEvaluator.evaluate(complete.copy(buildOutput=output))
  check(portable.ready && portable.capability == WizardCapabilityClass.NORMAL_SUPPORTED)
 }
 check(!WizardReadinessEvaluator.evaluate(complete.copy(buildOutput="native-exe")).ready)
 listOf("apk","aab","both").forEach { output ->
  check(WizardReadinessEvaluator.evaluate(complete.copy(sourceBuildEngine="expo",buildOutput=output)).ready)
 }
 listOf("exe","all","native-exe").forEach { output ->
  check(!WizardReadinessEvaluator.evaluate(complete.copy(sourceBuildEngine="expo",buildOutput=output)).ready)
 }
 listOf("flutter","dotnet-android","dotnet-maui","windows-web","unity","unknown","android-ndk","react-native").forEach { engine ->
  listOf("apk","both","native-exe","all").forEach { output ->
   check(!WizardReadinessEvaluator.evaluate(complete.copy(sourceBuildEngine=engine,buildOutput=output)).ready)
  }
 }
 check(!WizardReadinessEvaluator.evaluate(ProjectDraft(sourceBuildEngine="windows-native")).ready)
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

test('production file picker honors OFF, camera consent and Android 8 fallback',()=>{
 const source=read('android-app/app/src/main/assets/device-build/FastActivity.java');
 const start=source.indexOf('                public boolean onShowFileChooser(');
 const end=source.indexOf('\n            }\n        );',start);
 assert.ok(start>0 && end>start);
 const method=source.slice(start,end);
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-picker-'));
 try{
 fs.writeFileSync(path.join(dir,'PickerCheck.java'),`public class PickerCheck {
 static class WebView {} static class Uri {}
 interface ValueCallback<T>{void onReceiveValue(T value);}
 static class Config {boolean upload,camera;boolean optBoolean(String key,boolean fallback){return key.equals("fileUpload")?upload:key.equals("camera")?camera:fallback;}}
 static class FileChooserParams {boolean capture,fail;String[] types={"image/*"}; Intent createIntent(){if(fail)throw new IllegalStateException();return new Intent("picker");} String[] getAcceptTypes(){return types;}boolean isCaptureEnabled(){return capture;}}
 static class Intent {static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_GRANT_WRITE_URI_PERMISSION=2;static final String EXTRA_INITIAL_INTENTS="initial";String action;int flags;Intent(String action){this.action=action;}void putExtra(String k,Object v){}void addFlags(int flags){this.flags=flags;}static Intent createChooser(Intent i,String title){return i;}}
 static class Build {static class VERSION {static int SDK_INT=37;}static class VERSION_CODES {static final int Q=29;}}
 static class Manifest {static class permission {static final String CAMERA="camera";}}
 static class PackageManager {static final int PERMISSION_GRANTED=0;}
 static class ContentValues {void put(String key,String value){}}
 static class MediaStore {static final String ACTION_IMAGE_CAPTURE="camera",EXTRA_OUTPUT="output";static class Images {static class Media {static final String DISPLAY_NAME="name",MIME_TYPE="mime",RELATIVE_PATH="path";static final Uri EXTERNAL_CONTENT_URI=new Uri();}}}
 static class Environment {static final String DIRECTORY_PICTURES="Pictures";}
 static class Resolver {int inserts;Uri insert(Uri uri,ContentValues values){inserts++;return new Uri();}}
 Config config=new Config();Resolver resolver=new Resolver();boolean granted,cameraCaptureRequestedByPage,cleaned;Uri cameraCaptureUri;ValueCallback<Uri[]> fileChooserCallback;Intent launched;static final int FILE_CHOOSER_REQUEST=1;
 int checkSelfPermission(String p){return granted?0:-1;}Resolver getContentResolver(){return resolver;}void cleanupCameraCapture(){cleaned=true;}void startActivityForResult(Intent i,int code){launched=i;}
 ${method}
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  PickerCheck p=new PickerCheck();FileChooserParams f=new FileChooserParams();ValueCallback<Uri[]> callback=value->{};
  check(!p.onShowFileChooser(new WebView(),callback,f));check(p.launched==null && p.resolver.inserts==0);
  p.config.upload=true;check(p.onShowFileChooser(new WebView(),callback,f));check(p.launched.action.equals("picker") && p.resolver.inserts==0);
  p.config.camera=true;f.capture=true;p.granted=false;check(p.onShowFileChooser(new WebView(),callback,f));check(p.launched.action.equals("picker") && p.resolver.inserts==0);
  p.granted=true;check(p.onShowFileChooser(new WebView(),callback,f));check(p.launched.action.equals("camera"));check(p.launched.flags==3 && p.resolver.inserts==1);
  Build.VERSION.SDK_INT=26;p.launched=null;check(p.onShowFileChooser(new WebView(),callback,f));check(p.launched.action.equals("picker") && p.resolver.inserts==1);
  f.fail=true;check(!p.onShowFileChooser(new WebView(),callback,f));check(p.fileChooserCallback==null && p.cleaned);
  System.out.println("picker checks PASS");
 }
}`);
 execFileSync('javac',[path.join(dir,'PickerCheck.java')],{stdio:'pipe'});
 assert.match(execFileSync('java',['-cp',dir,'PickerCheck'],{encoding:'utf8'}),/checks PASS/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});


test('wizard exposes normal routing reasons before build without changing selections', () => {
 const ui = read('android-app/app/src/main/java/com/appforge/studio/MainActivity.kt');
 assert.match(ui, /HTML\/HTTPS Android için normal motor gerekli:/);
 assert.match(ui, /wizardReadiness\.normalRouteReasons\.joinToString/);
});

// Structural wiring evidence only; this does not establish Compose compilation or visual acceptance.
test('wizard warning panel has separate bounded height and every blocking step has navigation', () => {
 const ui = read('android-app/app/src/main/java/com/appforge/studio/MainActivity.kt');
 const start = ui.indexOf('val wizardReadiness = com.appforge.studio.build.WizardReadinessEvaluator.evaluate(draft)');
 const panel = ui.slice(start, ui.indexOf('1 -> SourceStep', start));
 assert.match(panel, /fillMaxWidth\(\)\.heightIn\(max = 160\.dp\)/);
 assert.match(panel, /if \(blockingDiagnostics.isNotEmpty\(\) \|\| showWizardHelp\)/);
 assert.match(panel, /var showWizardHelp by remember \{ mutableStateOf\(false\) \}/);
 assert.match(panel, /blockingDiagnostics.joinToString/);
 assert.match(panel, /affectedSteps.sorted\(\).forEach/);
 assert.match(panel, /TextButton\(onClick = \{ step = issueStep \}\)/);
 assert.match(panel, /Box\(modifier = Modifier.fillMaxWidth\(\).weight\(1f\)\)/);
});


test('actual DeviceBuildEngine capability guard accepts implemented quick QR/media and still rejects absent SDK/normal-engine injection',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-engine-guard-'));
 try {
  const engine=read(build+'DeviceBuildEngine.kt');
  const extract=name=>{const start=engine.indexOf('    private fun '+name+'(');assert.ok(start>=0);let end=engine.indexOf('    private ',start+20);if(end<0)end=engine.length;return engine.slice(start,end).replace('private fun '+name,'fun '+name);};
  fs.writeFileSync(path.join(dir,'BuildConfig.kt'),'package com.appforge.studio\nobject BuildConfig { const val DEBUG=true }');
  fs.writeFileSync(path.join(dir,'Engine.kt'),`package com.appforge.studio.build
import com.appforge.studio.model.*
object EngineGuard {
${extract('validateCapabilities')}
${extract('effectiveSourceEngine')}
}
fun main(){
 val base=ProjectDraft(appName="Engine",sourceTechnology="html",importedFolder="/test",startPage="index.html")
 for(output in listOf("apk","aab","both"))for(qr in listOf(false,true))for(media in listOf(false,true))for(billing in listOf(false,true)){
  val draft=base.copy(buildOutput=output,qrScanner=qr,mediaPlayerBridge=media,billingEnabled=billing,billingProductIds="coins",purchaseVerificationUrl="https://verify.example/receipt")
  check(FastHtmlApkPolicy.isCandidate(draft));EngineGuard.validateCapabilities(draft)
 }
 for(draft in listOf(base.copy(admobEnabled=true),base.copy(billingEnabled=true),base.copy(firebaseAnalyticsEnabled=true),
  base.copy(firebaseMessagingEnabled=true),base.copy(firebaseCrashlyticsEnabled=true),base.copy(mediaPlayerBridge=true,javascriptBridge=false),
  base.copy(qrScanner=true,sourceBuildEngine="node-web"),base.copy(mediaPlayerBridge=true,sourceBuildEngine="node-web"))) {
  check(runCatching{EngineGuard.validateCapabilities(draft)}.isFailure)
 }
 println("ENGINE GUARD PASS")
}`);
 execFileSync('kotlinc',[path.join(root,'android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt'),...['FastHtmlApkPolicy.kt','WebviewProjectSettings.kt','DeviceBuildCapabilities.kt'].map(f=>path.join(root,build,f)),path.join(dir,'BuildConfig.kt'),path.join(dir,'Engine.kt'),'-include-runtime','-d',path.join(dir,'engine.jar')],{timeout:120000,stdio:'pipe'});
 assert.match(execFileSync('java',['-jar',path.join(dir,'engine.jar')],{encoding:'utf8',timeout:30000}),/ENGINE GUARD PASS/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
