import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const enginePath =
  new URL(
    "../src/buildEngine.js",
    import.meta.url
  );

async function engineText() {
  return readFile(
    enginePath,
    "utf8"
  );
}

test("Media3 required dependencies and service contract exist", async () => {
  const text = await engineText();

  const required = [
    'implementation("androidx.media3:media3-exoplayer:1.11.0")',
    'implementation("androidx.media3:media3-session:1.11.0")',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
    'android.permission.POST_NOTIFICATIONS',
    'android:foregroundServiceType="mediaPlayback"',
    'androidx.media3.session.MediaSessionService'
  ];

  for (const marker of required) {
    assert.equal(
      text.includes(marker),
      true,
      `Missing Media3 marker: ${marker}`
    );
  }
});

test("Media3 notification and registered session exist", async () => {
  const text = await engineText();

  const required = [
    "DefaultMediaNotificationProvider",
    "setMediaNotificationProvider(",
    "MediaSession",
    "addSession(",
    "ACTION_PLAY",
    "ACTION_PAUSE",
    "ACTION_NEXT",
    "ACTION_PREVIOUS"
  ];

  for (const marker of required) {
    assert.equal(
      text.includes(marker),
      true,
      `Missing Media3 session marker: ${marker}`
    );
  }
});

test("Media3 Native Bridge controls exist", async () => {
  const text = await engineText();

  const required = [
    '"mediaPlay"',
    '"mediaSetPlaylist"',
    '"mediaPause"',
    '"mediaResume"',
    '"mediaNext"',
    '"mediaPrevious"'
  ];

  for (const marker of required) {
    assert.equal(
      text.includes(marker),
      true,
      `Missing Media3 bridge action: ${marker}`
    );
  }
});
