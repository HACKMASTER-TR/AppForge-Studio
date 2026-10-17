import test from 'node:test';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repoRoot = fileURLToPath(new URL('../../', import.meta.url));

test('production Kotlin Android publication rolls back faults and recovers without exposing partial sets', () => {
  const dir = fs.mkdtempSync(path.join(repoRoot, '.t21-publication-'));
  try {
    fs.writeFileSync(path.join(dir, 'Check.kt'), `package com.appforge.studio.build
import java.io.File
import java.util.zip.*
fun main(args: Array<String>) {
 val base = File(args[0]); val sources = File(base,"sources").apply { mkdir() }; val root = File(base,"private")
 fun fixture(kind: String): File = File(sources,"source.$kind").also { file ->
  ZipOutputStream(file.outputStream()).use { zip ->
   for (name in if(kind=="apk") listOf("AndroidManifest.xml") else listOf("BundleConfig.pb","base/manifest/AndroidManifest.xml")) {
    zip.putNextEntry(ZipEntry(name)); zip.write(byteArrayOf(1,2,3)); zip.closeEntry()
   }
  }
 }
 val apk=fixture("apk"); val aab=fixture("aab"); val both=linkedMapOf("apk" to apk,"aab" to aab)
 var visible: Map<String,File> = emptyMap()
 fun failure(sourcesMap: Map<String,File?> = both, cancel: () -> Unit = {}, verify: (String,File)->Unit = { _,_-> },
  copy: (File,File)->Unit = { s,t -> s.copyTo(t); Unit }, finalize: (File,File)->Unit = { s,t -> check(s.renameTo(t)) }) {
  visible=emptyMap()
  check(runCatching { visible=AndroidArtifactPublication.collect(root,"local-test",sources,sourcesMap,cancel,verify,copy,finalize) }.isFailure)
  check(visible.isEmpty()); check(root.listFiles().orEmpty().isEmpty())
 }
 failure(linkedMapOf("apk" to apk,"aab" to null))
 failure(copy={ s,t -> if(s.extension=="aab") { t.writeText("partial"); error("copy fault") }; s.copyTo(t); Unit })
 failure(verify={ kind,_ -> if(kind=="aab") error("signature fault") })
 val bad=File(sources,"bad.aab").apply { writeText("not a zip") }; failure(linkedMapOf("apk" to apk,"aab" to bad))
 failure(cancel={ error("cancel before publication") })
 var checks=0; failure(cancel={ if(++checks==4) error("cancel during staging") })
 var moves=0; var cancelled=false
 failure(cancel={ check(!cancelled) },finalize={ s,t -> check(s.renameTo(t)); if(++moves==1) cancelled=true })
 moves=0; failure(finalize={ s,t -> if(++moves==2) error("second rename fault"); check(s.renameTo(t)) })
 // Simulated interrupted process: same private job identity is cleaned on recovery.
 File(root,"local-test").mkdir(); File(root,"local-test/partial.apk").writeText("stale")
 File(root,"local-test-pending").mkdir(); File(root,"local-test-pending/partial.aab").writeText("stale")
 for (requested in listOf(mapOf("apk" to apk),mapOf("aab" to aab),both)) {
  visible=AndroidArtifactPublication.collect(root,"local-test",sources,requested,{}, { _,_-> })
  check(visible.keys==requested.keys); check(visible.values.all { it.isFile }); check(!File(root,"local-test-pending").exists())
 }
 val outside=File(base,"outside.apk").apply { writeBytes(apk.readBytes()) }
 failure(mapOf("apk" to outside))
 println("publication faults, APK/AAB/BOTH, bounds and recovery passed")
}`);
    execFileSync('kotlinc', [path.join(repoRoot, 'android-app/app/src/main/java/com/appforge/studio/build/AndroidArtifactPublication.kt'), path.join(dir, 'Check.kt'), '-include-runtime', '-d', path.join(dir, 'check.jar')], { timeout: 120000, stdio: 'pipe' });
    execFileSync('java', ['-jar', path.join(dir, 'check.jar'), dir], { timeout: 30000, stdio: 'pipe' });
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('Gradle selected-source fingerprint executes deterministically and detects source/config drift', () => {
  const dir = fs.mkdtempSync(path.join(repoRoot, '.t21-provenance-'));
  try {
    const script = fs.readFileSync(path.join(repoRoot, 'android-app/app/build.gradle.kts'), 'utf8');
    const source = script.slice(script.indexOf('fun fastHtmlSourceFingerprint()'), script.indexOf('val fastHtmlSourceRecord ='));
    fs.writeFileSync(path.join(dir, 'Check.kt'), `import java.io.File
import java.security.MessageDigest
object rootProject {
 val projectDir = File(System.getProperty("fixture"))
 fun file(path: String) = File(projectDir,path)
}
${source}
fun main() {
 val root = rootProject.projectDir
 val java = File(root,"html-apk-template/src/main/java/Child.java").apply { parentFile.mkdirs(); writeText("class Child {}") }
 val manifest = File(root,"html-apk-template/src/main/AndroidManifest.xml").apply { writeText("<manifest/>") }
 val gradle = File(root,"html-apk-template/build.gradle.kts").apply { writeText("plugins {}") }
 val original=fastHtmlSourceFingerprint(); check(original==fastHtmlSourceFingerprint())
 java.setLastModified(100000); check(original==fastHtmlSourceFingerprint())
 File(root,"local.properties").writeText("fixture excluded"); check(original==fastHtmlSourceFingerprint())
 File(root,"html-apk-template/build").mkdir(); File(root,"html-apk-template/build/output.apk").writeText("generated"); check(original==fastHtmlSourceFingerprint())
 java.appendText("// changed"); check(original!=fastHtmlSourceFingerprint()); val changed=fastHtmlSourceFingerprint()
 manifest.appendText("<!-- changed -->"); check(changed!=fastHtmlSourceFingerprint()); val manifestChanged=fastHtmlSourceFingerprint()
 gradle.appendText("// changed"); check(manifestChanged!=fastHtmlSourceFingerprint())
}`);
    execFileSync('kotlinc', [path.join(dir, 'Check.kt'), '-include-runtime', '-d', path.join(dir, 'check.jar')], { timeout: 120000, stdio: 'pipe' });
    execFileSync('java', [`-Dfixture=${dir}`, '-jar', path.join(dir, 'check.jar')], { timeout: 30000, stdio: 'pipe' });
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
