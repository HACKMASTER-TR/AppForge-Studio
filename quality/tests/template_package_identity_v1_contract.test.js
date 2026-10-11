import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const source = readFileSync(
  'android-app/app/src/main/java/com/appforge/studio/MainActivity.kt',
  'utf8'
);

test('materialized local template gets template-specific app and package identities', () => {
  const start = source.indexOf('AppScreen.TEMPLATES -> TemplatesScreen(');
  const end = source.indexOf('AppScreen.SETTINGS ->', start);
  assert.ok(start >= 0 && end > start);
  const handler = source.slice(start, end);
  assert.match(handler, /TemplateProjectFactory\s*\.materialize/);
  assert.match(handler, /if\s*\(\s*generated != null\s*\)/);
  assert.match(handler, /appName\s*=\s*template\.name/);
  assert.match(handler, /packageName\s*=\s*uniqueTemplatePackageName\(\s*template\.name/);
  assert.match(handler, /ProjectLibrary\.load\(context\)/);
  assert.doesNotMatch(handler, /configuredDraft\s*\.packageName\s*==\s*"com\.example\.myapp"/);
});

test('duplicate saved package identities get a unique suffix', () => {
  const start = source.indexOf('private fun uniqueTemplatePackageName(');
  const end = source.indexOf('private fun createQuickDraft(', start);
  assert.ok(start >= 0 && end > start);
  const helper = source.slice(start, end);
  assert.match(helper, /quickPackageName\(templateName\)/);
  assert.match(helper, /while\s*\(proposed in existingPackages\)/);
  assert.match(helper, /proposed = "\$base\$suffix"/);
  assert.match(helper, /suffix\+\+/);
});

test('Quick Create package name manual override remains untouched', () => {
  assert.match(source, /var quickPackageManualOverride by rememberSaveable/);
  assert.match(source, /val shouldSyncPackage = !quickPackageManualOverride/);
  assert.match(source, /quickPackageManualOverride = true/);
});
