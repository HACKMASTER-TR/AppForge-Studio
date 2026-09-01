import test from "node:test";
import assert from "node:assert/strict";

import {
  explainAppForgeProblem,
  appForgeProblemEnvelope
} from "../src/problemExplainer.js";

test(
  "invalid package name returns one exact diagnosis",
  () => {
    const problem =
      explainAppForgeProblem({
        message:
          "Package name is not valid: com.test app",
        status:
          400
      });

    assert.equal(
      problem.code,
      "INVALID_PACKAGE_NAME"
    );

    assert.equal(
      problem.confidence,
      99
    );
  }
);

test(
  "Gradle invalid directory beats Java guesses",
  () => {
    const problem =
      explainAppForgeProblem({
        message:
          "Error resolving plugin. Configuring project with invalid directory. The configured projectDirectory does not exist, can't be written to or is not a directory.",
        status:
          400
      });

    assert.equal(
      problem.code,
      "GRADLE_PROJECT_DIRECTORY"
    );

    assert.equal(
      problem.confidence,
      100
    );
  }
);

test(
  "HTTP 401 gets authentication explanation",
  () => {
    const problem =
      explainAppForgeProblem({
        message:
          "Unauthorized",
        status:
          401
      });

    assert.equal(
      problem.code,
      "AUTH_FAILED"
    );
  }
);

test(
  "HTTP 413 gets upload size explanation",
  () => {
    const problem =
      explainAppForgeProblem({
        message:
          "Payload Too Large",
        status:
          413
      });

    assert.equal(
      problem.code,
      "UPLOAD_TOO_LARGE"
    );
  }
);

test(
  "HTTP 429 gets rate limit explanation",
  () => {
    const problem =
      explainAppForgeProblem({
        message:
          "Too many requests",
        status:
          429
      });

    assert.equal(
      problem.code,
      "RATE_LIMITED"
    );
  }
);

test(
  "API error envelope keeps structured problem and one user message",
  () => {
    const result =
      appForgeProblemEnvelope(
        {
          error:
            "No matching client found for package name"
        },
        {
          status:
            400,
          path:
            "/api/builds"
        }
      );

    assert.equal(
      result.problem.code,
      "FIREBASE_CONFIG_ERROR"
    );

    assert.match(
      result.error,
      /Sorun:/
    );

    assert.match(
      result.error,
      /Neden:/
    );

    assert.match(
      result.error,
      /Çözüm:/
    );
  }
);

test(
  "secrets are redacted from evidence",
  () => {
    const problem =
      explainAppForgeProblem({
        message:
          "Build failed token=abc123secret",
        status:
          500
      });

    assert.equal(
      problem.evidence.includes(
        "abc123secret"
      ),
      false
    );
  }
);
