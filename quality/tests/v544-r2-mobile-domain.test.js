import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const base = new URL('../../',import.meta.url);
const read = path => fs.readFileSync(new URL(path, base), 'utf8');
const source = read('android-app/app/src/main/java/com/appforge/studio/build/V9SignedR2CandidateDownload.kt');
const transfer = read('android-app/app/src/main/java/com/appforge/studio/build/V9R2ObjectTransfer.kt');
const gradle = read('android-app/app/build.gradle.kts');
const testSource = read('android-app/app/src/test/java/com/appforge/studio/build/V9SignedR2CandidateDownloadTest.kt');
const manifest = JSON.parse(read('android-app/app/src/debug/assets/v9-r2/release-v1.json'));
test('only approved custom HTTPS R2 hostname is used for signed content', () => {
 assert.match(source, /private const val ORIGIN = "https:\/\/packages\.appforgecloud\.com"/);
 assert.doesNotMatch(source, /workers\.dev/);
 assert.match(testSource,/https:\/\/packages\.appforgecloud\.com\/v9\/objects\/sha256/);
 assert.match(source,/require\(HASH\.matches\(sha256\)\)/);
 assert.match(source,/V9ReleaseCandidatePolicy\.verify\(manifest, signature, persistedFloor\)/);
});
test('range requests, size and final sha256 remain fail-closed', () => {
 assert.match(transfer, /setRequestProperty\("Range", "bytes=\$start-\$end"\)/);
 assert.match(transfer, /connection\.responseCode != 206/);
 assert.match(transfer, /Content-Range mismatch/);
 assert.match(transfer, /Content-Length mismatch/);
 assert.match(transfer, /VerifiedModuleDownload\.sha\(partial\) != sha256/);
 assert.match(transfer, /transientFailures >= 3/);
 assert.doesNotMatch(transfer, /setHostnameVerifier|trustAll|SSLContext\.getInstance\("SSL"\)/);
});
test('V544 uses explicit exclusive in-place update version', () => {
 assert.match(gradle, /appforgeV9R2Isolated3UpgradeV544/);
 assert.match(gradle, /output\.versionCode\.set\(544\)/);
 assert.match(gradle, /output\.versionName\.set\("5\.0\.44-isolated3-upgrade"\)/);
 const pinned = manifest.objects.find(o => o.sha256 === 'd141e94ed3b32ea6540f2e927a83b3d42cd4378256a1aad60cda583ffdbd78af');
 assert.equal(pinned?.size,9654);
});
