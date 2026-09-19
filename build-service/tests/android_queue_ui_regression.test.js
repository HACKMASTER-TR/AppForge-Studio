import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root =
  new URL(
    "../../",
    import.meta.url
  );

const read =
  path =>
    readFile(
      new URL(
        path,
        root
      ),
      "utf8"
    );

test(
  "normal Android build path does not consume remote queue metadata",
  async () => {
    const client =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
      );

    assert.doesNotMatch(
      client,
      /\/api\/builds\/[^"\n]*queue|queueEstimatedWaitSeconds\s*=\s*json|queuePosition\s*=\s*json/
    );
  }
);

test(
  "Android builder stores live queue state",
  async () => {
    const text =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.ok(
      text.includes(
        "queuePosition ="
      )
    );

    assert.ok(
      text.includes(
        "s.queuePosition"
      )
    );

    assert.ok(
      text.includes(
        "s.queueEstimatedWaitSeconds"
      )
    );
  }
);

test(
  "Android build UI displays queue position workers and ETA",
  async () => {
    const text =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.ok(
      text.includes(
        "Sırada $it. build"
      )
    );

    assert.ok(
      text.includes(
        "uygun build slotu aktif"
      )
    );

    assert.ok(
      text.includes(
        "Tahmini bekleme"
      )
    );

    assert.ok(
      text.includes(
        "Süre worker yüküne"
      )
    );
  }
);
