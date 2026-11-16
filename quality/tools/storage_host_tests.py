#!/usr/bin/env python3
"""Compile actual storage/transport primitives and their JVM tests with explicit Android seams.

Requires caller-supplied compiler/dependency jars. No downloads, signer keys, Android
runtime state or network-policy acceptance. Android Gradle/emulator remain separate gates.
"""
from pathlib import Path
import argparse
import subprocess
import tempfile

parser=argparse.ArgumentParser()
parser.add_argument('--dependencies',type=Path,required=True,help='Directory containing compiler, stdlib, junit, json, coroutines and compiler dependencies')
parser.add_argument('--native-library-dir',type=Path)
args=parser.parse_args()
r=Path(__file__).resolve().parents[2]
b=r/'android-app/app/src/main/java/com/appforge/studio/build'
t=r/'android-app/app/src/test/java/com/appforge/studio/build'
prod=['DownloadPartialCheckpoint','RuntimeGenerationStore','ModuleOwnershipStore','BaseRuntimeOwnership','RecoveryPathAnchor','RuntimeStorageBudget','RuntimeFileClone','RuntimeMutationLock','VerifiedArtifactCache','VerifiedModuleDownload','MeasuredDownloadConnection','ModuleArtifactBroker','V9R2ObjectTransfer']
tests=['RuntimeFileCloneTest','VerifiedCacheTelemetryTest','VerifiedModuleDownloadTest','RuntimeStorageBudgetTest','RuntimeGenerationStoreTest','GenerationPreparationAuditTest','StorageAndCacheFixTest','ModuleArtifactBrokerTest','V9R2ObjectTransferTest','ModuleOwnershipStoreTest']
cp=':'.join(str(p.resolve()) for p in args.dependencies.glob('*.jar') if p.name not in ('tests.jar','storage-tests.jar'))
with tempfile.TemporaryDirectory(prefix='appforge-storage-host-') as temporary:
    h=Path(temporary)
    s=(b/'OfflineModulePlan.kt').read_text()
    models='package com.appforge.studio.build\n'+s[s.index('internal enum class OfflineModuleId'):s.index('    fun refreshExistingInstallation')]+ '    fun spec(id:OfflineModuleId)=all.single{it.id==id}\n}\n'+s[s.index('internal enum class ModulePhase'):]
    (h/'Models.kt').write_text(models)
    (h/'AndroidBoundaries.kt').write_text('''package com.appforge.studio.build
import java.io.File
import java.net.*
internal enum class DownloadNetworkPolicy { WIFI_ONLY, ALLOW_ANY }
internal object DownloadNetwork {
 class PolicyBlocked:java.util.concurrent.CancellationException()
 val policy=DownloadNetworkPolicy.ALLOW_ANY
 fun prepareJournal(){}
 fun check(p:DownloadNetworkPolicy=policy){}
 fun open(u:URL)=u.openConnection() as HttpURLConnection
 fun release(c:HttpURLConnection){}
 fun <T> withPolicy(p:DownloadNetworkPolicy,block:()->T):T=block()
}
internal object DownloadCoordinator {
 enum class ResumeCapability { VERIFIED_HTTP_RANGE }
 data class Transfer(val artifactId:String,val sha:String,val sourceIdentity:String,val progress:ModuleProgress,val capability:ResumeCapability,val verificationStatus:String="")
 private val transfers=java.util.concurrent.CopyOnWriteArrayList<Transfer>()
 fun claim(key:String){}
 fun release(key:String){}
 fun bind(op:String,sha:String,file:File)=0
 fun observe(t:Transfer){transfers.add(t)}
 fun snapshot(op:String)=transfers.filter{it.progress.operationId==op}
 fun retry(op:String,sha:String,attempt:Int){}
 fun prefixCheckpoint(op:String,sha:String,bytes:Long){}
}
internal object V9SignedR2CandidateDownload {
 const val CHUNK_BYTES=8L*1024*1024
 fun urlForSha(sha:String):String {require(sha.matches(Regex("[a-f0-9]{64}")));return "https://appforge-v9-packages-readonly.28550040284a.workers.dev/v9/objects/sha256/${sha.take(2)}/$sha"}
}
''')
    jar=h/'storage-tests.jar'
    sources=[b/(n+'.kt') for n in prod]+[t/(n+'.kt') for n in tests]+[h/'Models.kt',h/'AndroidBoundaries.kt',r/'android-app/app/src/main/java/com/appforge/studio/terminal/RootfsActivationTransaction.kt']
    subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-d',str(jar),*map(str,sources)],check=True)
    vm=['java']
    if args.native_library_dir:
        vm.append('-Djava.library.path='+str(args.native_library_dir.resolve()))
    subprocess.run([*vm,'-cp',str(jar)+':'+cp,'org.junit.runner.JUnitCore',*['com.appforge.studio.build.'+n for n in tests]],check=True)
