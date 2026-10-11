"use strict";

const test =
  require(
    "node:test"
  );

const assert =
  require(
    "node:assert/strict"
  );

const path =
  require(
    "node:path"
  );

const {
  normalizedAppId,
  persistentUserDataPath,
  runtimeDirectoryName
} =
  require(
    "../storage.cjs"
  );


test(
  "same app ID keeps the same persistent user data directory",
  () => {
    const base =
      path.resolve(
        "test-local-app-data"
      );

    const first =
      persistentUserDataPath(
        "com.appforge.example",
        base
      );

    const second =
      persistentUserDataPath(
        "COM.APPFORGE.EXAMPLE",
        base
      );

    assert.equal(
      first,
      second
    );

    assert.equal(
      first,
      path.resolve(
        base,
        "AppForgeStudio",
        "PortableApps",
        "com.appforge.example",
        "user-data"
      )
    );
  }
);


test(
  "different app IDs receive isolated profiles",
  () => {
    const base =
      path.resolve(
        "test-local-app-data"
      );

    assert.notEqual(
      persistentUserDataPath(
        "com.appforge.one",
        base
      ),
      persistentUserDataPath(
        "com.appforge.two",
        base
      )
    );

    assert.notEqual(
      runtimeDirectoryName(
        "com.appforge.one"
      ),
      runtimeDirectoryName(
        "com.appforge.two"
      )
    );
  }
);


test(
  "runtime directory stays stable across relaunches",
  () => {
    assert.equal(
      runtimeDirectoryName(
        "com.appforge.same"
      ),
      runtimeDirectoryName(
        "COM.APPFORGE.SAME"
      )
    );
  }
);


test(
  "invalid persistent app IDs fail closed",
  () => {
    assert.throws(
      () =>
        normalizedAppId(
          "../evil"
        ),
      /geçersiz/
    );

    assert.throws(
      () =>
        persistentUserDataPath(
          "com/app/evil",
          path.resolve(
            "test-local-app-data"
          )
        ),
      /geçersiz/
    );
  }
);
