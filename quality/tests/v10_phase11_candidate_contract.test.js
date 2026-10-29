import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const workflow=fs.readFileSync(new URL('../../.github/workflows/v10-phase11-native-candidate.yml',import.meta.url),'utf8');
const script=fs.readFileSync(new URL('../../scripts/v10/phase11-native-candidate.sh',import.meta.url),'utf8');
test('Phase11 native candidates stay branch guarded, read only and bounded',()=>{
 const job=workflow.slice(workflow.indexOf('  phase11-native-candidate:'));
 assert.match(job,/github.ref == 'refs\/heads\/feat\/v10-oss-unified-20261028'/);
 assert.match(workflow,/permissions:\n  contents: read/);
 assert.match(job,/timeout-minutes: 45/);
 assert.match(job,/timeout 2100/);
 assert.match(job,/persist-credentials: false/);
 assert.doesNotMatch(job,/upload-artifact|write|secrets\./);
 for(const m of job.matchAll(/uses: (.+)/g))assert.match(m[1],/@[a-f0-9]{40}$/);
});
test('candidate compile checks never stand in for runtime or change shipped manifests',()=>{
 assert.match(script,/mktemp -d/);assert.match(script,/sha256sum -c/);
 assert.match(script,/npm ci --ignore-scripts/);assert.match(script,/npm ci --offline/);
 assert.match(script,/:app:compileDebugJavaWithJavac :app:externalNativeBuildDebug/);
 assert.match(script,/HERMES_NATIVE_REGISTRATION_RUNTIME=NOT_RUN/);
 assert.doesNotMatch(script,/:app:(?:assemble|install|bundle|package)/);
 assert.equal(JSON.parse(fs.readFileSync(new URL('../../examples/rust-react-native-turbo/package.json',import.meta.url))).devDependencies['@react-native-community/cli'],'20.0.0');
 assert.equal(JSON.parse(fs.readFileSync(new URL('../../examples/rust-expo-jsi/app.json',import.meta.url))).expo.newArchEnabled,false);
});
