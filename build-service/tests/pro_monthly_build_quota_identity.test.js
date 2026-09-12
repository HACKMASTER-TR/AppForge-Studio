import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const server =
  fs.readFileSync(
    new URL(
      "../server.js",
      import.meta.url
    ),
    "utf8"
  );

const workspaceBuild =
  fs.readFileSync(
    new URL(
      "../src/workspaceBuild.js",
      import.meta.url
    ),
    "utf8"
  );

const workspace =
  fs.readFileSync(
    new URL(
      "../src/workspace.js",
      import.meta.url
    ),
    "utf8"
  );

const projectQuota =
  fs.readFileSync(
    new URL(
      "../src/projectQuotaV2.js",
      import.meta.url
    ),
    "utf8"
  );

const projects =
  fs.readFileSync(
    new URL(
      "../src/projects.js",
      import.meta.url
    ),
    "utf8"
  );


test(
  "direct builds persist server project id",
  () => {
    assert.match(
      server,
      /const project =[\s\S]*?await upsertProject/
    );

    assert.match(
      server,
      /project_id/
    );

    assert.match(
      server,
      /project\.id/
    );
  }
);


test(
  "direct cache hit consumes monthly successful-build quota",
  () => {
    assert.match(
      server,
      /if \(cached\)[\s\S]*?reserveMonthlyBuildQuota/
    );

    assert.match(
      server,
      /if \(cached\)[\s\S]*?recordSuccessfulBuild/
    );
  }
);


test(
  "workspace cache hit consumes monthly successful-build quota",
  () => {
    assert.match(
      workspaceBuild,
      /if \(cached\)[\s\S]*?reserveMonthlyBuildQuota/
    );

    assert.match(
      workspaceBuild,
      /if \(cached\)[\s\S]*?recordSuccessfulBuild/
    );
  }
);


test(
  "workspace package identity locks after first success",
  () => {
    assert.match(
      workspaceBuild,
      /package_locked_at/
    );

    assert.match(
      workspaceBuild,
      /PROJECT_PACKAGE_LOCKED/
    );

    assert.match(
      workspace,
      /package_locked_at/
    );
  }
);


test(
  "queue rejection releases both quota reservations",
  () => {
    assert.match(
      workspaceBuild,
      /enqueueJob[\s\S]*?releaseBuildQuotaReservation/
    );
  }
);


test(
  "package lock state is exposed by project APIs",
  () => {
    assert.match(
      projects,
      /package_locked_at/
    );

    assert.match(
      projectQuota,
      /UPDATE appforge_projects[\s\S]*?package_locked_at/
    );

    assert.doesNotMatch(
      projectQuota,
      /WHERE id = \$1\s+AND user_id = \$2\s+AND package_name/
    );
  }
);
