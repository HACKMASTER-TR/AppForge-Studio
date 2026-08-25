import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";


test(
  "Studio scans HTML ZIP source capabilities automatically",
  async () => {

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


    for (
      const marker of [
        "navigator.geolocation",
        "notification.requestpermission",
        "getUserMedia",
        "appforgedownloads",
        "appforgemedia",
        "appforge.scancode",
        "fileInputRegex"
      ]
    ) {
      assert.ok(
        analyzer.toLowerCase()
          .includes(
            marker.toLowerCase()
          ),
        `Analyzer missing ${marker}`
      );
    }


    assert.ok(
      mainActivity.includes(
        "SourceCapabilityAnalyzer"
      )
    );

    assert.ok(
      mainActivity.includes(
        "analysis.camera"
      )
    );

    assert.ok(
      mainActivity.includes(
        "analysis.location"
      )
    );

    assert.ok(
      mainActivity.includes(
        "analysis.notifications"
      )
    );

    assert.ok(
      mainActivity.includes(
        "analysis.fileUpload"
      )
    );

    assert.ok(
      mainActivity.includes(
        "analysis.downloads"
      )
    );

    assert.ok(
      mainActivity.includes(
        "analysis.mediaPlayer"
      )
    );

    assert.ok(
      mainActivity.includes(
        "analysis.qrScanner"
      )
    );
  }
);
