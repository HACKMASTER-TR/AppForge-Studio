import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const adapterUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt",
  import.meta.url
);

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

const workspaceUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt",
  import.meta.url
);

test(
  "Termux mirror controller survives Compose viewport disposal",
  async () => {
    const source =
      await readFile(
        adapterUrl,
        "utf8"
      );

    assert.ok(
      source.includes(
        "object TermuxTerminalMirrorControllerRegistry"
      )
    );

    assert.ok(
      source.includes(
        "fun detachView()"
      )
    );

    assert.ok(
      source.includes(
        ".ensureRegistered("
      )
    );

    assert.ok(
      source.includes(
        ".detachView("
      )
    );

    assert.ok(
      source.includes(
        "fun release("
      )
    );
  }
);

test(
  "copy mode overlays persistent native viewport",
  async () => {
    const source =
      await readFile(
        panelUrl,
        "utf8"
      );

    const surface =
      source.indexOf(
        "private fun LocalPtySurface("
      );

    const persistent =
      source.indexOf(
        "Persistent native viewport: copy mode is an overlay",
        surface
      );

    const copy =
      source.indexOf(
        "if (copyMode) {",
        persistent
      );

    assert.ok(surface >= 0);
    assert.ok(persistent > surface);
    assert.ok(copy > persistent);

    assert.ok(
      source.includes(
        "if (copyMode) 0f else 1f"
      )
    );

    assert.ok(
      source.includes(
        "TermuxTerminalMirrorControllerRegistry\n"
        + "            .release(id)"
      )
    );
  }
);

test(
  "workspace selector changes only through saved user selection",
  async () => {
    const source =
      await readFile(
        workspaceUrl,
        "utf8"
      );

    assert.ok(
      source.includes(
        "object TerminalWorkspaceSelectionPreferences"
      )
    );

    assert.ok(
      source.includes(
        "Workspace selection is user-owned state."
      )
    );

    assert.ok(
      source.includes(
        "TerminalWorkspaceSelectionPreferences\n"
        + "                    .load("
      )
    );

    assert.ok(
      source.includes(
        "TerminalWorkspaceSelectionPreferences\n"
        + "                        .save("
      )
    );

    assert.doesNotMatch(
      source,
      /selectedProjectId[\s\S]{0,400}projects\s*\.firstOrNull\(\)\s*\?\.id/
    );
  }
);


test(
  "PTY session switch recreates the native TerminalView for the new mirror controller",
  async () => {
    const source =
      await readFile(
        adapterUrl,
        "utf8"
      );

    const hostStart =
      source.indexOf(
        "@Composable\ninternal fun TermuxTerminalCoreHost("
      );

    const hostEnd =
      source.indexOf(
        "Termux-backed viewport for an AppForge-owned PTY session",
        hostStart
      );

    assert.ok(hostStart >= 0);
    assert.ok(hostEnd > hostStart);

    const host =
      source.slice(
        hostStart,
        hostEnd
      );

    assert.match(
      host,
      /key\(\s*controller,\s*mirrorSessionId,\s*\)\s*\{[\s\S]{0,600}?AndroidView\(/
    );

    const factory =
      host.indexOf(
        "factory = { context ->"
      );

    const createView =
      host.indexOf(
        ".createView(",
        factory
      );

    const register =
      host.indexOf(
        ".ensureRegistered(",
        createView
      );

    assert.ok(factory >= 0);
    assert.ok(createView > factory);
    assert.ok(register > createView);
  }
);
