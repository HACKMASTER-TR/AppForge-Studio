import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const buildPath =
  new URL(
    "../src/buildEngine.js",
    import.meta.url
  );

test(
  "V3 native recorder activity is generated and returned through file chooser",
  async () => {
    const source =
      await readFile(
        buildPath,
        "utf8"
      );

    for (const marker of [
      "APPFORGE_NATIVE_AUDIO_RECORDER_V3",
      "generatedAudioRecorderActivity",
      "AppForgeAudioRecorderActivity",
      "MediaRecorder.AudioSource.MIC",
      "MediaRecorder.OutputFormat.MPEG_4",
      "MediaRecorder.AudioEncoder.AAC",
      '<cache-path name="audio" path="audio/"/>'
    ]) {
      assert.ok(
        source.includes(marker),
        `missing marker: ${marker}`
      );
    }

    assert.match(
      source,
      /captureEnabled\s*&&\s*wantsAudio[\s\S]*AppForgeAudioRecorderActivity::class\.java/
    );
  }
);

test(
  "V3 camera return accepts a non-empty EXTRA_OUTPUT file before vendor cancel code",
  async () => {
    const source =
      await readFile(
        buildPath,
        "utf8"
      );

    for (const marker of [
      "APPFORGE_CAMERA_FILE_RETURN_V3",
      "pendingCameraFilePath",
      "validCameraUri",
      "file.length() > 0L"
    ]) {
      assert.ok(
        source.includes(marker),
        `missing marker: ${marker}`
      );
    }

    assert.match(
      source,
      /pendingCameraCapture && validCameraUri != null -> arrayOf\(validCameraUri\)[\s\S]{0,500}result\.resultCode != Activity\.RESULT_OK/
    );
  }
);
