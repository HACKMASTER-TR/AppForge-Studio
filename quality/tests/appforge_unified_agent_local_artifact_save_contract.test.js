import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read = path => fs.readFileSync(new URL('../../' + path, import.meta.url), 'utf8');
const client = read('android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentArtifactClient.kt');
const route = read('android-app/app/src/main/java/com/appforge/studio/UnifiedAgentStudioRoute.kt');
test('Unified Agent local APK AAB and EXE tickets save to public Downloads, not HTTP manager', () => {
  assert.match(client, /client\.createDownloadTicket\(/);
  assert.match(client, /uri\.scheme\.equals\("file"/);
  assert.match(client, /device-build\/artifacts/);
  assert.match(client, /source\.path\.startsWith\(root\.path \+ File\.separator\)/);
  assert.match(client, /savePublicArtifact\(source, fileName, safeKind\)/);
  assert.match(client, /MediaStore\.Downloads\.EXTERNAL_CONTENT_URI/);
  assert.match(client, /Environment\.DIRECTORY_DOWNLOADS}\/.?AppForgeStudio/);
  assert.match(client, /MediaStore\.MediaColumns\.IS_PENDING, 1/);
  assert.match(client, /input\.copyTo\(output, 1024 \* 1024\)/);
  assert.match(client, /count == source\.length\(\)/);
  assert.match(client, /MediaStore\.MediaColumns\.IS_PENDING, 0/);
  assert.match(client, /resolver\.delete\(target, null, null\)/);
  assert.match(client, /uri\.scheme\.equals\("https"/);
  assert.match(client, /DownloadManager\.Request\(uri\)/);
});
test('Unified Agent reports saved local artifact instead of fictitious queued download', () => {
  assert.match(client, /data class AppForgeAgentArtifactSaveResult/);
  assert.match(client, /downloadId = null/);
  assert.match(client, /kaydedildi: \$savedPath/);
  assert.match(route, /lastDownloadId = saved\.downloadId/);
  assert.match(route, /message = saved\.message/);
});
