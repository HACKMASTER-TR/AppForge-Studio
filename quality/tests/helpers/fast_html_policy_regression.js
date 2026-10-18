import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

// Compile production Kotlin, with only the generated Android BuildConfig boundary stubbed.
// Missing compilers/runtimes fail visibly; no skip or source-text substitute.
export function assertFastHtmlEngineIsolation(root) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 't21-policy-'));
  try {
    fs.writeFileSync(path.join(dir, 'BuildConfig.kt'),
      'package com.appforge.studio\nobject BuildConfig { const val DEBUG = true }');
    fs.writeFileSync(path.join(dir, 'Check.kt'), `package com.appforge.studio.build
import com.appforge.studio.model.*
fun main() {
 val base = ProjectDraft(appName="Routing", sourceTechnology="html", importedFolder="/fixture",
  startPage="index.html", fileUpload=false, clipboardBridge=false, vibrationBridge=false,
  networkState=false, splashEnabled=false)
 val windows = listOf("exe", "windows-exe", "portable-exe", "windows-portable-exe",
  "native-exe", "windows-native-exe", "all", "apk+aab+exe")
 for (mode in listOf(SourceMode.LOCAL, SourceMode.URL)) {
  for (output in listOf("apk", "aab", "both", "apk+aab") + windows) {
   val draft = base.copy(sourceMode=mode, webUrl="https://example.com", buildOutput=output)
   val original = draft.copy()
   val quick = output !in windows && (mode == SourceMode.LOCAL || output == "apk")
   check(FastHtmlApkPolicy.isCandidate(draft) == quick) { "route: $mode/$output" }
   check((WizardReadinessEvaluator.evaluate(draft).capability == WizardCapabilityClass.QUICK_SUPPORTED) == quick)
   for (engine in listOf("node-web", "expo", "android-gradle", "windows-native",
     "windows-portable", "react-native", "python-android", "unknown", " EXPO ")) {
    val foreign = draft.copy(sourceBuildEngine=engine)
    check(!FastHtmlApkPolicy.isCandidate(foreign)) { "foreign engine: $engine/$mode/$output" }
    check(runCatching { FastHtmlApkPolicy.requireSupported(foreign) }.isFailure)
    val result = WizardReadinessEvaluator.evaluate(foreign)
    check(result.capability != WizardCapabilityClass.QUICK_SUPPORTED)
    check(result.normalRouteReasons.isEmpty())
   }
   if (output in windows) {
    val selected = draft.copy(fileUpload=true, microphone=true)
    check(!FastHtmlApkPolicy.isCandidate(selected))
    check(FastHtmlApkPolicy.normalRouteReasons(selected).isEmpty())
    check(WizardReadinessEvaluator.evaluate(selected).normalRouteReasons.isEmpty())
   } else {
    check(WizardReadinessEvaluator.evaluate(draft).ready == (mode==SourceMode.LOCAL || output=="apk"))
    if(mode==SourceMode.LOCAL) {
     val selected=draft.copy(camera=true);val result=WizardReadinessEvaluator.evaluate(selected)
     check(!result.ready && result.capability == WizardCapabilityClass.UNSUPPORTED_BLOCKED)
     check(result.normalRouteReasons.isNotEmpty() && !FastHtmlApkPolicy.isCandidate(selected))
    }
    val denied = draft.copy(additionalPermissions=setOf("INVENTED_PERMISSION"))
    check(!WizardReadinessEvaluator.evaluate(denied).ready)
    check(runCatching { WebviewProjectSettings.validate(denied) }.isFailure)
    if (mode == SourceMode.URL) {
     check(!WizardReadinessEvaluator.evaluate(draft.copy(webUrl="http://example.com")).ready)
     check(!WizardReadinessEvaluator.evaluate(draft.copy(webUrl="https://user:pass@example.com")).ready)
    }
   }
   for(selected in listOf(draft.copy(camera=true),draft.copy(microphone=true),draft.copy(location=true))) {
    check(FastHtmlApkPolicy.isCandidate(selected) == (output !in windows && (mode==SourceMode.URL && output=="apk" || mode==SourceMode.LOCAL && selected.location)))
   }
   check(draft == original) { "routing mutated source/output selections" }
  }
 }
 FastHtmlApkPolicy.requireSupported(base)
 FastHtmlApkPolicy.requireSupported(base.copy(sourceMode=SourceMode.URL, webUrl="https://example.com"))
 check(!FastHtmlApkPolicy.isCandidate(base.copy(signingMode=SigningMode.CUSTOM)))
 check(runCatching { FastHtmlApkPolicy.requireSupported(base.copy(signingMode=SigningMode.CUSTOM)) }.isFailure)
 println("T21 executable engine isolation passed")
}`);
    const build = 'android-app/app/src/main/java/com/appforge/studio/build/';
    execFileSync('kotlinc', [
      path.join(root, 'android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt'),
      ...['FastHtmlApkPolicy.kt', 'DeviceBuildCapabilities.kt', 'FeatureSupportMatrix.kt','WebviewProjectSettings.kt']
        .map(file => path.join(root, build, file)),
      path.join(dir, 'BuildConfig.kt'), path.join(dir, 'Check.kt'),
      '-include-runtime', '-d', path.join(dir, 'check.jar')
    ], { timeout: 120000, stdio: 'pipe' });
    assert.match(execFileSync('java', ['-jar', path.join(dir, 'check.jar')],
      { encoding: 'utf8', timeout: 30000 }), /T21 executable engine isolation passed/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}
