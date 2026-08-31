import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const enginePath =
  new URL(
    "../src/buildEngine.js",
    import.meta.url
  );

test("legacy JavascriptInterface bridge is removed", async () => {
  const text =
    await readFile(
      enginePath,
      "utf8"
    );

  assert.equal(
    text.includes(
      "addJavascriptInterface"
    ),
    false
  );

  assert.equal(
    text.includes(
      "@JavascriptInterface"
    ),
    false
  );
});

test("clipboard read method is removed", async () => {
  const text =
    await readFile(
      enginePath,
      "utf8"
    );

  assert.equal(
    text.includes(
      "readClipboard"
    ),
    false
  );
});

test("origin scoped WebMessage bridge exists", async () => {
  const text =
    await readFile(
      enginePath,
      "utf8"
    );

  assert.equal(
    text.includes(
      "WebViewCompat.addWebMessageListener"
    ),
    true
  );

  assert.equal(
    text.includes(
      "allowedBridgeOrigins"
    ),
    true
  );

  assert.equal(
    text.includes(
      "isMainFrame"
    ),
    true
  );
});

test("bridge fails closed without WebMessage feature", async () => {
  const text =
    await readFile(
      enginePath,
      "utf8"
    );

  assert.equal(
    text.includes(
      "WebViewFeature.WEB_MESSAGE_LISTENER"
    ),
    true
  );

  assert.equal(
    text.includes(
      "Bu WebView sürümü güvenli WebMessage Native Bridge'i desteklemiyor."
    ),
    true
  );
});
