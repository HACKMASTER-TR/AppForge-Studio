import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(import.meta.url)
  );

const repo =
  path.resolve(
    here,
    "..",
    ".."
  );

const read = relative =>
  fs.readFileSync(
    path.join(
      repo,
      relative
    ),
    "utf8"
  );

const main =
  read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

const advisor =
  read(
    "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
  );

test(
  "device build failure exposes real local logs",
  () => {
    assert.match(
      main,
      /DEVICE_BUILD_VISIBLE_LOGS_V1/
    );

    assert.match(
      main,
      /CİHAZ BUILD LOGLARI/
    );

    assert.match(
      main,
      /İlk kritik hata/
    );

    assert.match(
      main,
      /Hassas log satırı gizlendi/
    );
  }
);

test(
  "five build stress test matches two-thread device engine",
  () => {
    assert.match(
      main,
      /5 Build Testi • Maks\. 2 Paralel/
    );

    assert.match(
      main,
      /Semaphore[\s\S]{0,300}?permits\s*=\s*2/
    );

    assert.doesNotMatch(
        main,
        /5 Build Testi • Maks\. 3 Paralel/
    );
  }
);

test(
  "builder UI no longer advertises retired autoscale",
  () => {
    assert.match(
      main,
      /YÖNETİCİ SİSTEM DURUMU/
    );

    assert.doesNotMatch(
      main,
      /YÖNETİCİ SİSTEM DURUMU \/ AUTOSCALE/
    );

    assert.doesNotMatch(
      main,
      /Uygun Source Worker toolchain planı/
    );
  }
);

test(
  "build error fallback points to local device logs",
  () => {
    assert.doesNotMatch(
      advisor,
      /Build Service\/Worker logunu/
    );

    assert.doesNotMatch(
      advisor,
      /Build Worker disk alanı/
    );

    assert.doesNotMatch(
      advisor,
      /Source Worker toolchain desteklenmiyor/
    );

    assert.match(
      advisor,
      /Cihaz Build Logları/
    );
  }
);
