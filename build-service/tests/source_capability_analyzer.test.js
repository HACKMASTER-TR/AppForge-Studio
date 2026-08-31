import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";


async function readSources() {

  const [
    analyzer,
    mainActivity
  ] =
    await Promise.all([
      fs.readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/io/SourceCapabilityAnalyzer.kt",
          import.meta.url
        ),
        "utf8"
      ),

      fs.readFile(
        new URL(
          "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
          import.meta.url
        ),
        "utf8"
      )
    ]);


  return {
    analyzer,
    mainActivity
  };
}


test(
  "Studio detects HTML ZIP source capabilities with reasons",
  async () => {

    const {
      analyzer,
      mainActivity
    } =
      await readSources();


    for (
      const marker of [
        "navigator.geolocation",
        "notification.requestpermission",
        "getUserMedia",
        "appforgedownloads",
        "appforgemedia",
        "appforge.scancode",
        "fileInputRegex",
        "cameraReason",
        "locationReason",
        "notificationsReason",
        "fileUploadReason",
        "downloadsReason",
        "mediaPlayerReason",
        "qrScannerReason"
      ]
    ) {
      assert.ok(
        analyzer
          .toLowerCase()
          .includes(
            marker.toLowerCase()
          ),
        `Analyzer missing ${marker}`
      );
    }


    for (
      const marker of [
        "SourceCapabilityAnalyzer",
        "SourceCapabilityAnalysis",
        "sourceAnalysis",
        "analysis.camera",
        "analysis.location",
        "analysis.notifications",
        "analysis.fileUpload",
        "analysis.downloads",
        "analysis.mediaPlayer",
        "analysis.qrScanner",
        "Otomatik algılandı",
        "detectedDetails"
      ]
    ) {
      assert.ok(
        mainActivity.includes(
          marker
        ),
        `MainActivity missing ${marker}`
      );
    }
  }
);


test(
  "Generated APK is published to AppForge Studio download folder",
  async () => {

    const {
      mainActivity
    } =
      await readSources();


    const start =
      mainActivity.indexOf(
        "private fun publishApkToDownloads"
      );

    const end =
      mainActivity.indexOf(
        "private fun installCachedApk",
        start
      );


    assert.ok(
      start >= 0 &&
      end > start,
      "publishApkToDownloads block missing"
    );


    const block =
      mainActivity.slice(
        start,
        end
      );


    assert.ok(
      block.includes(
        '"${Environment.DIRECTORY_DOWNLOADS}/$APPFORGE_DOWNLOAD_FOLDER"'
      ),
      "APK must be saved under Downloads/AppForge Studio"
    );
  }
);
