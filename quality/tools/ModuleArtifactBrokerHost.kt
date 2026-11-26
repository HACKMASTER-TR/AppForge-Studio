package com.appforge.studio.build

import java.io.File
import kotlinx.coroutines.*

/** Host proof entry point using the actual compiled app classes, not a transport mock.
 * Compile with the app classes.jar as -Xfriend-paths, plus its coroutine runtime.
 * Argument 0: isolated request workspace, 1: private verified cache, 2: stop file.
 */
fun main(args:Array<String>)=runBlocking {
    require(args.size==3)
    val broker=ModuleArtifactBroker(File(args[0]),File(args[1]),OfflineModuleId.ANDROID_CORE,"host-apt-proof",{e->
        println("${e.moduleId} ${e.phase} bytes=${e.bytesDownloaded} total=${e.bytesTotal} speed=${e.speedBytesPerSecond} elapsed=${e.elapsedMs} detail=${e.detail}")
    })
    val job=launch(Dispatchers.IO){broker.serve()}
    try {while(!File(args[2]).exists())delay(100)}finally{job.cancelAndJoin()}
}
