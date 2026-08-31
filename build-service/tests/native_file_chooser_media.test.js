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
  "generated WebView routes image/audio capture to AppForge native handlers",
  async () => {
    const source =
      await readFile(
        buildPath,
        "utf8"
      );

    for (const marker of [
      "APPFORGE_NATIVE_CAPTURE_ROUTER_V1",
      "APPFORGE_WEB_MEDIA_PERMISSION_V2",
      "APPFORGE_CAMERA_FILE_RETURN_V3",
      "APPFORGE_NATIVE_AUDIO_RECORDER_V3",
      "android.permission.RECORD_AUDIO",
      "PermissionRequest.RESOURCE_AUDIO_CAPTURE",
      "pendingWebPermissionRequest",
      "pendingCameraCapture",
      "pendingCameraFilePath",
      "ClipData.newRawUri",
      "allowContentAccess",
      "isCaptureEnabled",
      "wantsImage",
      "wantsAudio",
      "AppForgeAudioRecorderActivity",
      "MediaStore.ACTION_IMAGE_CAPTURE",
      "cameraIntent",
      "audioRecorderIntent",
      ".resolveActivity("
    ]) {
      assert.ok(
        source.includes(marker),
        marker
      );
    }

    assert.ok(
      !source.includes(
        "MediaStore.Audio.Media\\n                                    .RECORD_SOUND_ACTION"
      ),
      "external RECORD_SOUND_ACTION route must not be required"
    );
  }
);

test(
  "FAST build keeps file and camera chooser projects on the prebuilt runtime",
  async () => {
    const source =
      await readFile(
        fastPath,
        "utf8"
      );

    assert.ok(source.includes("FAST runtime artık onShowFileChooser"));
    assert.equal(source.includes('"Native dosya / kamera seçici"'), false);
  }
);
