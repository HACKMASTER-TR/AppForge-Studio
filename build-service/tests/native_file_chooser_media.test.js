import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const buildPath =
  new URL(
    "../src/buildEngine.js",
    import.meta.url
  );

const fastPath =
  new URL(
    "../src/fastBuild.js",
    import.meta.url
  );

test(
  "generated WebView routes image/audio capture to native intents",
  async () => {
    const source =
      await readFile(
        buildPath,
        "utf8"
      );

    for (const marker of [
      "APPFORGE_NATIVE_CAPTURE_ROUTER_V1",
      "APPFORGE_WEB_MEDIA_PERMISSION_V2",
      "APPFORGE_CAMERA_RESULT_V2",
      "android.permission.RECORD_AUDIO",
      "PermissionRequest.RESOURCE_AUDIO_CAPTURE",
      "pendingWebPermissionRequest",
      "pendingCameraCapture",
      "ClipData.newRawUri",
      "allowContentAccess",
      "isCaptureEnabled",
      "wantsImage",
      "wantsAudio",
      "MediaStore.Audio.Media",
      "RECORD_SOUND_ACTION",
      "MediaStore.ACTION_IMAGE_CAPTURE",
      "cameraIntent",
      "audioRecorderIntent",
      ".resolveActivity(",
      ".resolveActivity("
    ]) {
      assert.ok(
        source.includes(marker),
        marker
      );
    }
  }
);

test(
  "FAST build falls back to FULL when native media chooser is needed",
  async () => {
    const source =
      await readFile(
        fastPath,
        "utf8"
      );

    assert.ok(
      source.includes(
        "Native dosya / kamera seçici"
      )
    );

    assert.ok(
      source.includes(
        "c.features?.fileUpload"
      )
    );

    assert.ok(
      source.includes(
        "c.features?.camera"
      )
    );
  }
);
