import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const root =
  new URL(
    "../../",
    import.meta.url
  );

function read(relativePath) {
  return fs.readFileSync(
    new URL(
      relativePath,
      root
    ),
    "utf8"
  );
}

test(
  "custom FREE project limit uses persistent per-user override",
  () => {
    const source =
      read(
        "build-service/src/projects.js"
      );

    assert.match(
      source,
      /appforge_user_project_limits/
    );

    assert.match(
      source,
      /effectiveFreeLimit/
    );
  }
);

test(
  "admin can update per-user FREE project limit",
  () => {
    const source =
      read(
        "build-service/server.js"
      );

    assert.match(
      source,
      /\/api\/admin\/users\/:userId\/project-limit/
    );

    assert.match(
      source,
      /customFreeProjectLimit/
    );
  }
);

test(
  "heyomert initial 50 limit does not overwrite later admin changes",
  () => {
    const source =
      read(
        "build-service/sql/019_user_free_project_limits.sql"
      );

    assert.match(
      source,
      /heyomert@gmail\.com/
    );

    assert.match(
      source,
      /50/
    );

    assert.match(
      source,
      /ON CONFLICT\(user_id\)[\s\S]*DO NOTHING/
    );

    assert.doesNotMatch(
      source,
      /ON CONFLICT\(user_id\)[\s\S]*DO UPDATE/
    );
  }
);

test(
  "Android uses server quota and advisor recognizes quota errors",
  () => {
    const main =
      read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    const api =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
      );

    const advisor =
      read(
        "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
      );

    assert.match(
      api,
      /\/api\/projects\/quota/
    );

    assert.match(
      main,
      /effectiveFreeProjectLimit/
    );

    assert.match(
      advisor,
      /FREE proje hakkı doldu/
    );

    assert.match(
      advisor,
      /Aktif build sınırına ulaşıldı/
    );
  }
);
