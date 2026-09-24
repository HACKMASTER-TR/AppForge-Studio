import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const adapterUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt",
  import.meta.url
);

const terminalViewUrl = new URL(
  "../../android-app/termux-terminal-view/src/main/java/com/termux/view/TerminalView.java",
  import.meta.url
);

test("AppForge initializes Termux renderer before attaching the session", async () => {
  const source = await readFile(adapterUrl, "utf8");

  const constructor = source.indexOf("TerminalView(\n            context,");
  const setTextSize = source.indexOf("view.setTextSize(");
  const attachSession = source.indexOf("view.attachSession(");

  assert.ok(constructor >= 0, "TerminalView construction missing");
  assert.ok(setTextSize > constructor, "renderer initialization must follow construction");
  assert.ok(
    attachSession > setTextSize,
    "renderer must be initialized before attachSession/updateSize"
  );
  assert.match(source, /DEFAULT_TERMUX_TERMINAL_TEXT_SIZE_SP\s*=\s*14f/);
  assert.match(source, /displayMetrics\.scaledDensity/);
  assert.match(source, /\.roundToInt\(\)/);
});

test("vendored Termux updateSize requires mRenderer after a session is attached", async () => {
  const source = await readFile(terminalViewUrl, "utf8");

  assert.match(source, /public TerminalRenderer mRenderer;/);
  assert.match(
    source,
    /public void setTextSize\(int textSize\)[\s\S]{0,300}mRenderer = new TerminalRenderer/
  );
  assert.match(
    source,
    /public void updateSize\(\)[\s\S]{0,500}mRenderer\.mFontWidth/
  );
});
