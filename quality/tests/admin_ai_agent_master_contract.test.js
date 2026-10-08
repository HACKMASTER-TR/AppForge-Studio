import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(new URL(`../../${p}`,import.meta.url),'utf8');
const base='android-app/app/src/main/java/com/appforge/studio/';
const screen=read(base+'AdminAiRouterScreen.kt'),main=read(base+'MainActivity.kt'),session=read(base+'ai/AdminAiAgentSession.kt'),runtime=read(base+'ai/AdminAiAgentRuntime.kt');
test('saved-project selector changes Studio state through restore and build navigation guard',()=>{
 assert.match(screen,/ProjectLibrary\.load\(context\)/);
 assert.match(screen,/projects\.forEach/);
 assert.match(main,/onSelectProject = aiProjectSelect@[\s\S]{0,350}prepareBuilderProjectNavigation/);
 assert.match(main,/onSelectProject = aiProjectSelect@[\s\S]{0,600}ProjectLibrary\.restore\(context, id\)[\s\S]{0,200}currentProjectId = id/);
 assert.match(screen,/remember\(projectId\) \{ mutableStateOf<List<AdminAiEvidence>>/);
 assert.match(screen,/LaunchedEffect\(projectId, workspacePath, contextRevision\)/);
 assert.match(screen,/DisposableEffect\(taskRuntime, client\)/);
});
test('local approval and cancellation own runtime authority',()=>{
 assert.match(screen,/CompletableDeferred<Boolean>/);
 assert.match(screen,/executor\.execute\(operation, allowed\)/);
 assert.match(screen,/taskRuntime\?\.stop\(\); client\.cancel\(\); approval\?\.cancel\(\); job\?\.cancel\(\)/);
 assert.match(session,/Cancelled\/failed session cannot accept late success/);
 assert.match(session,/Real build\/artifact evidence required/);
 assert.match(runtime,/DeviceBuildEngine\.artifact\(started\.buildId, kind\)/);
 assert.match(runtime,/device-build\/artifacts\/\$\{started\.buildId\}/);
 assert.match(runtime,/Publisher signing must remain outside AI authority/);
});
test('untrusted script execution fails closed without replacing protected runtime',()=>{
 assert.match(session,/no safe project verification executor available/);
 assert.match(runtime,/LinuxShellEngine\(context\)/);
 assert.match(runtime,/builds\.createBuild/);
 assert.doesNotMatch(runtime,/ProcessBuilder|Runtime\.getRuntime|\/api\/build/);
 assert.match(session,/currentHash\(target\) == op\.beforeHash/);
 assert.match(session,/AppForgeAgentStructuredPatch\.apply/);
 assert.match(session,/AppForgeAgentWorkspaceTransaction\.rollback\(cp, guards\)/);
});
