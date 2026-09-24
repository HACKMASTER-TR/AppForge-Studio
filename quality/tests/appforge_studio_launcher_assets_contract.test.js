import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import crypto from "node:crypto";

const root = new URL("../../", import.meta.url);
const read = file => fs.readFileSync(new URL(file, root));
const resources = [
  ["mdpi", 48], ["hdpi", 72], ["xhdpi", 96],
  ["xxhdpi", 144], ["xxxhdpi", 192]
];
function dimensions(file) {
  const data = read(file);
  assert.equal(data.subarray(0,8).toString("hex"), "89504e470d0a1a0a", file);
  assert.equal(data.toString("ascii",12,16), "IHDR", file);
  return [data.readUInt32BE(16), data.readUInt32BE(20)];
}

test("AppForge Studio itself uses full-artwork density launcher assets", () => {
  const manifest = read("android-app/app/src/main/AndroidManifest.xml").toString();
  assert.match(manifest, /android:icon="@mipmap\/ic_launcher"/);
  assert.match(manifest, /android:roundIcon="@mipmap\/ic_launcher_round"/);
  assert.deepEqual(dimensions("android-app/app/src/main/assets/branding/appforge_studio_launcher_master.png"), [1024,1024]);
  assert.deepEqual(dimensions("android-app/app/src/main/assets/branding/appforge_studio_launcher_source.png"), [1536,1536]);
  for (const [density, size] of resources) {
    const base = `android-app/app/src/main/res/mipmap-${density}/`;
    assert.deepEqual(dimensions(base + "ic_launcher.png"), [size,size]);
    assert.deepEqual(dimensions(base + "ic_launcher_round.png"), [size,size]);
    assert.equal(
      crypto.createHash("sha256").update(read(base + "ic_launcher.png")).digest("hex"),
      crypto.createHash("sha256").update(read(base + "ic_launcher_round.png")).digest("hex"),
      "Both regular and round resources must use the same approved artwork"
    );
  }
});

test("Home Pro card remains hidden while approved Pro indicator and settings stay", () => {
  const home = read("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt").toString();
  const dashboard = read("android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeDashboard.kt").toString();
  assert.doesNotMatch(home, /ModernProCard\s*\(/);
  assert.match(home, /ModernHomeHero\(/);
  assert.match(dashboard, /"PRO"/);
  assert.match(home, /Text\("Ayarlar"\)/);
});
