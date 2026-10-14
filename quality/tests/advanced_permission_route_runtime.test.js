import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
const root = path.resolve(import.meta.dirname, '../..');
const build = 'android-app/app/src/main/java/com/appforge/studio/build/';
const read = p => fs.readFileSync(path.join(root,p),'utf8');

test('all permission aliases execute normalization, restored toggles and actual manifest generation', () => {
 const dir = fs.mkdtempSync(path.join(root, 'quality/.permission-runtime-'));
 try {
 const engine = read(build+'DeviceBuildEngine.kt');
 const ui = read('android-app/app/src/main/java/com/appforge/studio/MainActivity.kt');
 const stage = ui.slice(ui.indexOf('    val stageLabel ='), ui.indexOf('    val currentProjectKey =', ui.indexOf('    val stageLabel =')));
 const manifest = engine.slice(engine.indexOf('    private fun webManifest('), engine.indexOf('    private fun ',engine.indexOf('    private fun webManifest(')+10));
 fs.writeFileSync(path.join(dir,'BuildConfig.kt'),'package com.appforge.studio\nobject BuildConfig { const val DEBUG = true }');
 fs.writeFileSync(path.join(dir,'Check.kt'), `package com.appforge.studio.build
import com.appforge.studio.model.*
object Wrapper {
 fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace("\\\"", "&quot;")
 ${manifest.replace('private fun webManifest', 'fun webManifest')}
}
fun stage(normalizedStatus: String, effectiveBuildBusy: Boolean, buildId: String?, preflight: List<String>, safeProgress: Int, reentryPending: Boolean = false): String {
 ${stage}
 return stageLabel
}
fun main() {
 check(stage("invalid settings",false,null,listOf("BUILD_BLOCKED=YES"),0) == "Derleme engellendi")
 check(stage("queued",true,"job",listOf("BUILD_BLOCKED=YES"),0) == "Bekleniyor")
 check(stage("running",true,"job",emptyList(),40) == "Kaynaklar derleniyor")
 check(stage("success",false,"job",emptyList(),100) == "Tamamlandı")
 val base = ProjectDraft(sourceTechnology="html", importedFolder="/test", startPage="index.html")
 val aliases = linkedMapOf(
 "BLUETOOTH" to listOf("BLUETOOTH_SCAN","BLUETOOTH_CONNECT"),
 "BIOMETRIC" to listOf("USE_BIOMETRIC"), "CALENDAR" to listOf("READ_CALENDAR","WRITE_CALENDAR"),
 "CONTACTS" to listOf("READ_CONTACTS","WRITE_CONTACTS"), "BACKGROUND_LOCATION" to listOf("ACCESS_BACKGROUND_LOCATION"),
 "EXACT_ALARM" to listOf("SCHEDULE_EXACT_ALARM"), "MEDIA_IMAGES" to listOf("READ_MEDIA_IMAGES"),
 "MEDIA_VIDEO" to listOf("READ_MEDIA_VIDEO"), "ACTIVITY_RECOGNITION" to listOf("ACTIVITY_RECOGNITION"))
 aliases.forEach { (alias, expected) ->
  val variants = listOf(setOf(alias), expected.toSet(), expected.map { "android.permission.$it" }.toSet(),
   setOf(alias) + expected + expected.map { "android.permission.$it" })
  variants.forEach { values ->
   check(WebviewProjectSettings.additionalPermissions(base.copy(additionalPermissions=values)) == expected.sorted())
   check(WebviewProjectSettings.isAdditionalPermissionEnabled(values, alias))
   val off = WebviewProjectSettings.toggleAdditionalPermission(values + "UNRECOGNIZED", alias, false)
   check(off == setOf("UNRECOGNIZED"))
   check(WebviewProjectSettings.toggleAdditionalPermission(off, alias, true) == expected.toSet()+"UNRECOGNIZED")
   listOf("apk","aab","both").forEach { output ->
    val draft = base.copy(buildOutput=output, iconUri="content://icon", additionalPermissions=values)
    check(!FastHtmlApkPolicy.isCandidate(draft)); WebviewProjectSettings.validate(draft)
    val text = Wrapper.webManifest(draft)
    val names = Regex("<uses-permission android:name=\\"([^\\"]+)\\"").findAll(text).map { it.groupValues[1] }.toList()
    check(names.toSet().size == names.size)
    check(names.filter { it != "android.permission.INTERNET" && it != "android.permission.ACCESS_NETWORK_STATE" }.toSet() == expected.map { "android.permission.$it" }.toSet())
   }
  }
  expected.forEach { canonical ->
   check(WebviewProjectSettings.isAdditionalPermissionEnabled(setOf(canonical),alias))
   check(WebviewProjectSettings.toggleAdditionalPermission(setOf(canonical),alias,false).isEmpty())
  }
 }
 val mixed = aliases.keys + aliases.values.flatten() + aliases.values.flatten().map { "android.permission.$it" }
 check(WebviewProjectSettings.additionalPermissions(base.copy(additionalPermissions=mixed.toSet())) == aliases.values.flatten().distinct().sorted())
 listOf("", " ", " MEDIA_IMAGES", "read_media_images", "android.permission.MEDIA_IMAGES", "CAMERA", "MANAGE_EXTERNAL_STORAGE", "READ_SMS", "READ_CONTACTS\\" />", "android.permission.android.permission.READ_CONTACTS").forEach { bad ->
  check(runCatching { WebviewProjectSettings.additionalPermissions(base.copy(additionalPermissions=setOf(bad))) }.isFailure)
 }
 val unrelated = setOf("READ_CONTACTS", "READ_MEDIA_VIDEO", "invalid")
 check(WebviewProjectSettings.toggleAdditionalPermission(unrelated,"MEDIA_IMAGES",false) == unrelated)
 listOf("http://example.com", "https://user:pass@example.com", "https:///missing").forEach { url ->
  check(runCatching { WebviewProjectSettings.validate(base.copy(sourceMode=SourceMode.URL,webUrl=url,iconUri="content://icon",additionalPermissions=setOf("MEDIA_IMAGES"))) }.isFailure)
 }
 println("permission assertions passed")
}`);
 execFileSync('kotlinc',[path.join(root,'android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt'), ...['FastHtmlApkPolicy.kt','DeviceBuildCapabilities.kt','WebviewProjectSettings.kt'].map(p=>path.join(root,build,p)),path.join(dir,'BuildConfig.kt'),path.join(dir,'Check.kt'),'-include-runtime','-d',path.join(dir,'check.jar')], {timeout:120000});
 assert.match(execFileSync('java',['-jar',path.join(dir,'check.jar')],{encoding:'utf8'}),/assertions passed/);
 } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});

test('Builder restores equivalent permissions and preserves blocked preflight route and truthful wording', () => {
 const ui=read('android-app/app/src/main/java/com/appforge/studio/MainActivity.kt');
 assert.match(ui,/checked = WebviewProjectSettings.isAdditionalPermissionEnabled\(d.additionalPermissions, spec.key\)/);
 assert.match(ui,/WebviewProjectSettings.toggleAdditionalPermission\(\s*d.additionalPermissions, spec.key, enabled/);
 assert.match(ui,/!effectiveBuildBusy && buildId == null && "BUILD_BLOCKED=YES" in preflight ->\s*"Derleme engellendi"/);
 assert.match(ui,/step =\s*10\s*if \(preflightFailure == "dependency-missing"\) \{\s*openOfflinePack\(\)/);
 assert.match(ui,/geniş galeri izni gerektirmeyen Android sistem seçicisini/);
 assert.match(ui,/add\(array.optString\(index\)\)/);
});
