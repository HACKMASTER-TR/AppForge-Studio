import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const home =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/StudioHomeScreen.kt",
    import.meta.url
  );

const library =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt",
    import.meta.url
  );

test(
  "project menu exposes clone action",
  async () => {
    const text =
      await readFile(
        home,
        "utf8"
      );

    assert.match(
      text,
      /Text\("Klonla"\)/
    );

    assert.match(
      text,
      /onClone/
    );
  }
);

test(
  "project cloning creates an independent saved project",
  async () => {
    const text =
      await readFile(
        library,
        "utf8"
      );

    assert.match(
      text,
      /fun cloneProject\(/
    );

    assert.match(
      text,
      /copyRecursively/
    );

    assert.match(
      text,
      /Kopya/
    );
  }
);
