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
