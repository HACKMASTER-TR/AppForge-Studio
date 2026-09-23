import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ProPurchasesActivity.kt",
    import.meta.url
  );

test(
  "Pro purchases screen respects the Android status bar",
  async () => {
    const text = await readFile(source, "utf8");

    assert.match(
      text,
      /import androidx\.compose\.foundation\.layout\.statusBarsPadding/
    );

    assert.match(
      text,
      /\.fillMaxSize\(\)\s*\.statusBarsPadding\(\)\s*\.verticalScroll/
    );
  }
);

test(
  "Pro purchases only reports internet failure for connection errors",
  async () => {
    const text = await readFile(source, "utf8");

    assert.match(
      text,
      /UnknownHostException/
    );

    assert.match(
      text,
      /ConnectException/
    );

    assert.match(
      text,
      /SocketTimeoutException/
    );

    assert.match(
      text,
      /İnternet bağlantısı kurulamadı/
    );

    assert.match(
      text,
      /İşlem tamamlanamadı\. Lütfen tekrar dene\./
    );

    assert.doesNotMatch(
      text,
      /İşlem tamamlanamadı\. İnternet bağlantını kontrol edip tekrar deneyebilirsin\./
    );
  }
);
