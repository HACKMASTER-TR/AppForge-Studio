#!/usr/bin/env python3
"""Compile actual platform-independent agent sources and real shell execution code.
Android Context/runtime launcher and unrelated codegen DTOs are host fixtures;
this is deliberately not classified as an Android unit or device acceptance run.
"""
import pathlib, subprocess, tempfile, shutil, argparse
parser = argparse.ArgumentParser()
parser.add_argument('--junit-jar', type=pathlib.Path)
parser.add_argument('--hamcrest-jar', type=pathlib.Path)
parser.add_argument('--ui-json-jar', type=pathlib.Path)
parser.add_argument('--jgit-jar', type=pathlib.Path)
parser.add_argument('--slf4j-jar', type=pathlib.Path)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
base = root / 'android-app/app/src/main/java/com/appforge/studio'
kotlinc = shutil.which('kotlinc')
if not kotlinc:
    raise SystemExit('PENDING: kotlinc unavailable')
lib = pathlib.Path(kotlinc).resolve().parent.parent / 'lib/kotlinx-coroutines-core-jvm.jar'
if not lib.is_file():
    raise SystemExit('PENDING: Kotlin coroutines unavailable')
with tempfile.TemporaryDirectory(prefix='appforge-agent-test-') as tmp:
    tmp = pathlib.Path(tmp)
    fixtures = {
        'Context.kt': 'package android.content\nimport java.io.File\nclass Context(val filesDir: File) { val applicationContext get() = this }',
        'Launcher.kt': '''package com.appforge.studio.terminal
import android.content.Context
import java.io.File
class PackagedLinuxEngine(context: Context) { fun requireLauncher() = File("/bin/sh") }
object ProrootPinnedRuntime { fun buildShellArguments(rootfs: File,workspace: File,command: String) = listOf("-c",command) }
''',
        'RuntimeFixtures.kt': '''package com.appforge.studio.build
import android.content.Context
import com.appforge.studio.model.ProjectDraft
import java.io.File
class BuildApiClient(context: Context, base: String, key: String) {
 fun cancelBuild(id: String) {}
 fun createBuild(draft: ProjectDraft, zip: File?) = Start("local-"+"a".repeat(20),1L)
 fun getBuild(id: String) = Status(id,1L,"failed",0,emptyList())
}
class Start(val buildId: String,val buildNo: Long?)
class Status(val buildId: String,val buildNo: Long?,val status: String,val progress: Int,val logs: List<String>)
object DeviceBuildEngine { fun artifact(id: String,kind: String): File? = null }
object DeviceBuildRuntimeV3 { suspend fun ensureReady(context: Context,progress: (String)->Unit) = context.filesDir }
object WindowsPublisherSigningPolicy { fun signingRequested(context: Context) = false }
''',
        'Owner.kt': 'package com.appforge.studio.security\nimport android.content.Context\nobject OwnerAccessPolicy { fun isActiveOwner(context: Context) = true }',
        'Git.kt': 'package com.appforge.studio.terminal\nimport java.io.File\nobject GitWorkspaceService { suspend fun agentInspect(root: File,diff: Boolean,allowedPath: (String)->File) = "fixture" }',
        'Project.kt': '''package com.appforge.studio.model
data class ProjectDraft(val sourceMode: SourceMode = SourceMode.LOCAL, val signingMode: SigningMode = SigningMode.DEBUG, val buildOutput: String = "both", val importedFolder: String? = null, val appName: String = "demo", val packageName: String = "com.demo", val sourceTechnologyLabel: String = "web", val sourceBuildEngine: String = "webview-static")
enum class SourceMode { LOCAL }
enum class SigningMode { DEBUG }
''',
        'Generated.kt': (base/'ai/AppForgeAgentCodegen.kt').read_text().split('internal object AppForgeAgentCodegen')[0] + (base/'ai/AppForgeAgentBlueprint.kt').read_text().split('internal data class AppForgeAgentDesignTokens')[0].split('\n',2)[2],
    }
    extra_sources = []
    classpath = str(lib)
    if args.jgit_jar:
        if not args.slf4j_jar or not args.jgit_jar.is_file() or not args.slf4j_jar.is_file():
            raise SystemExit('JGit and SLF4J jar paths required')
        git_source = (base/'terminal/GitWorkspaceService.kt').read_text()
        method = git_source.split('    /** Bounded index-to-worktree inspection')[1].split('    suspend fun status(')[0]
        fixtures['Git.kt'] = 'package com.appforge.studio.terminal\nimport java.io.File\nimport org.eclipse.jgit.storage.file.FileRepositoryBuilder\nimport kotlinx.coroutines.*\nobject GitWorkspaceService {\n    /** Bounded index-to-worktree inspection' + method + '\n}\n'
        extra_sources.append(str(root/'quality/kotlin/AdminAiGitBehavior.kt'))
        fixtures['GitMain.kt'] = 'package com.appforge.studio.ai\nfun main() { gitBehavior() }'
        classpath += ':'+str(args.jgit_jar)+':'+str(args.slf4j_jar)
    if args.ui_json_jar:
        if not args.ui_json_jar.is_file(): raise SystemExit('JSON jar path required')
        # Compose signatures are compile-only fixtures, never Android acceptance.
        ui_fixture_path = root/'quality/kotlin/admin_ai_ui_fixtures.py'
        namespace = {}
        exec(ui_fixture_path.read_text(), namespace)
        fixtures.update(namespace['fixtures'])
        fixtures['Owner.kt'] += '\nfun unused() {}\n'
        fixtures['Owner.kt'] = fixtures['Owner.kt'].replace('fun isActiveOwner(context: Context) = true', 'fun isActiveOwner(context: Context) = true; fun currentGoogleIdToken():String?=null; fun clearVerifiedGoogleAdmin(context:Context){}')
        fixtures['Git.kt'] += '\nobject TerminalWorkspaceResolver { fun resolve(context:android.content.Context,id:String?,draft:com.appforge.studio.model.ProjectDraft,email:String)=context.filesDir }\n'
        extra_sources.append(str(base/'AdminAiRouterScreen.kt'))
        classpath += ':'+str(args.ui_json_jar)
    junit_classes = []
    if args.junit_jar:
        if not args.hamcrest_jar or not args.junit_jar.is_file() or not args.hamcrest_jar.is_file(): raise SystemExit('JUnit and Hamcrest jars required')
        classpath += ':'+str(args.junit_jar)+':'+str(args.hamcrest_jar)
        for package, name in [('ai','AdminAiAgentSessionTest'),('ai','AppForgeAgentStructuredPatchTest'),('ai','AppForgeAgentWorkspaceTransactionTest'),('ai','AppForgeAgentRepairLoopTest'),('ai','AiProjectContextCollectorTest'),('terminal','TerminalCommandPolicyTest')]:
            extra_sources.append(str(root/f'android-app/app/src/test/java/com/appforge/studio/{package}/{name}.kt'))
            junit_classes.append(f'com.appforge.studio.{package}.{name}')
    for name, content in fixtures.items(): (tmp/name).write_text(content)
    sources = [base / p for p in ['ai/AdminAiAgentRuntime.kt','ai/AdminAiAgentSession.kt','ai/AiProjectContextCollector.kt','ai/AppForgeAgentStructuredPatch.kt','ai/AppForgeAgentWorkspaceTransaction.kt','ai/AppForgeAgentRepairLoop.kt','terminal/TerminalCommandPolicy.kt','terminal/LinuxShellEngine.kt']]
    jar = tmp/'tests.jar'
    subprocess.run([kotlinc, *map(str,sources),*map(str,tmp.glob('*.kt')),str(root/'quality/kotlin/AdminAiAgentBehavior.kt'),*extra_sources,'-cp',classpath,'-include-runtime','-d',str(jar)],check=True,cwd=root)
    subprocess.run(['java','-cp',str(jar)+':'+classpath,'com.appforge.studio.ai.AdminAiAgentBehaviorKt'],check=True,cwd=root,timeout=60)
    if args.jgit_jar:
        subprocess.run(['java','-cp',str(jar)+':'+classpath,'com.appforge.studio.ai.GitMainKt'],check=True,cwd=root,timeout=60)
    if args.junit_jar:
        subprocess.run(['java','-cp',str(jar)+':'+classpath,'org.junit.runner.JUnitCore',*junit_classes],check=True,cwd=root,timeout=60)
