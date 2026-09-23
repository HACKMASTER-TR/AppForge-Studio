import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
const read = p => fs.readFileSync(new URL("../../" + p,import.meta.url),"utf8");
const engine = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");
const android = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceProjectIcon.kt");
const windows = read("android-app/app/src/main/java/com/appforge/studio/build/WindowsPeIconPatcher.kt");
const packager = read("android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt");
const host = read("android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableHostStore.kt");

test("selected icon is included in disposable Web, Python and imported Android project outputs",()=>{
  assert.match(engine,/DeviceProjectIcon\.install\(context, draft, project\)/);
  assert.equal((engine.match(/DeviceProjectIcon\.install\(context, draft, project\)/g)||[]).length,3);
  assert.match(android,/drawable-nodpi/);
  assert.match(android,/appforge_user_icon\.png/);
  assert.match(android,/android:icon/);
  assert.match(android,/android:roundIcon/);
  assert.match(android,/Seçilen uygulama ikonu okunamadı/);
});

test("Windows icon changes only a verified Host copy, before signed payload footer",()=>{
  assert.match(packager,/copyHost\(host, part\)/);
  assert.match(packager,/WindowsPeIconPatcher\.install\(appContext, draft\.iconUri!!, part\)/);
  assert.ok(packager.indexOf("copyHost(host, part)") < packager.indexOf("WindowsPeIconPatcher.install(appContext, draft.iconUri!!, part)"));
  assert.ok(packager.indexOf("WindowsPeIconPatcher.install(appContext, draft.iconUri!!, part)") < packager.indexOf("appendPayload(part, manifestBytes, projectZip, payloadLength)"));
  assert.match(windows,/resources\(3\)/);
  assert.match(windows,/resources\(14\)/);
  assert.match(windows,/pe\.length\(\) == originalLength/);
  assert.match(windows,/Host ikon yuvalarına seçilen görsel sığmadı/);
  assert.match(host,/HOST_SHA256/);
  assert.doesNotMatch(packager,/host\.outputStream/);
});


test("prepared Android launcher icon uses the actual graphic instead of the old 640px inset", () => {
  const processor = read("android-app/app/src/main/java/com/appforge/studio/io/AppIconProcessor.kt");
  assert.match(processor, /OUTPUT_SIZE = 1024/);
  assert.match(processor, /SAFE_CONTENT_SIZE = 960/);
  assert.doesNotMatch(processor, /SAFE_CONTENT_SIZE = 640/);
});

test("Windows PE slots adapt high-detail images without modifying verified host or layout", () => {
  assert.match(windows, /intArrayOf\(6, 5, 4, 3\)/);
  assert.match(windows, /Bitmap\.createScaledBitmap\(thumbnail, w, h, false\)/);
  assert.match(windows, /if \(limit >= longest\) continue/);
  assert.match(windows, /if \(scaled !== bitmap\) scaled\.recycle\(\)/);
  assert.match(windows, /return null \/\/ Fail closed/);
  assert.match(windows, /pe\.length\(\) == originalLength/);
});
