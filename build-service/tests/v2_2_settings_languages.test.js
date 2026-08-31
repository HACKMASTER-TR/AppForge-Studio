import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const mainActivity =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const i18nFile =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/i18n/StudioI18n.kt",
    import.meta.url
  );

const storeFile =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/AppSettingsStore.kt",
    import.meta.url
  );

const keystoreFile =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/KeystoreVault.kt",
    import.meta.url
  );

test("settings hub and support screens exist", async () => {
  const text = await readFile(mainActivity, "utf8");
  for (const token of [
    "AppScreen.SETTINGS",
    "AppScreen.LEGAL",
    "AppScreen.HELP",
    "AppScreen.PLAY_GUIDE",
    "AppScreen.PRO",
    "AppScreen.KEYSTORES",
    "AppScreen.LANGUAGE",
    "SettingsHubScreen",
    "LanguageSettingsScreen",
    "LegalCenterScreen",
    "HowToUseCenterScreen",
    "PlayPublishingGuideScreen",
    "ProUpgradeScreen",
    "KeystoreManagerScreen"
  ]) {
    assert.equal(text.includes(token), true, `Missing ${token}`);
  }
});

test("multi-language catalog exists", async () => {
  const text = await readFile(i18nFile, "utf8");
  for (const code of ["system", "tr", "en", "de", "ar"]) {
    assert.equal(text.includes(`"${code}"`), true, `Missing ${code}`);
  }
});

test("settings persistence and keystore vault exist", async () => {
  const settingsText = await readFile(storeFile, "utf8");
  const keystoreText = await readFile(keystoreFile, "utf8");

  assert.equal(settingsText.includes("StudioPreferences"), true);
  assert.equal(settingsText.includes("updateLanguage"), true);
  // Pro entitlement artık yerel preferences içinde tutulmaz.

  assert.equal(keystoreText.includes("ManagedKeystore"), true);
  assert.equal(keystoreText.includes("importFromUri"), true);
  assert.equal(keystoreText.includes("SHA-1"), true);
  assert.equal(keystoreText.includes("SHA-256"), true);
});
