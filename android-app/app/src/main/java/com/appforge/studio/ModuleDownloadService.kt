package com.appforge.studio

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/** User-started data transfer only. No boot/background auto-start or OS survival guarantee. */
class ModuleDownloadService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Derleme paketi indirmeleri",NotificationManager.IMPORTANCE_LOW))
        fun notification():Notification {
            val events=ModuleInstallOperations.updates.value.values
            val e=events.lastOrNull{it.phase !in setOf(com.appforge.studio.build.ModulePhase.READY,com.appforge.studio.build.ModulePhase.ERROR)}
            val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("AppForge • modüler kurulum").setContentText(e?.detail?.take(160)?:"Kurulum hazırlanıyor")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setProgress(100,e?.percent?:0,e?.percent==null).build()
        }
        try { ServiceCompat.startForeground(this,ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
        catch(e:RuntimeException){ModuleInstallOperations.cancel();stopSelf();return START_NOT_STICKY}
        scope.launch {
            while(ModuleInstallOperations.busy.value) {
                // Notification permission may be denied; the foreground task and in-app state remain valid.
                runCatching{manager.notify(ID,notification())}
                delay(1000)
            }
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId)
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int) {
        ModuleInstallOperations.pause();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId)
    }
    override fun onDestroy(){if(ModuleInstallOperations.busy.value)ModuleInstallOperations.pause();scope.cancel();super.onDestroy()}
    companion object {
        private const val CHANNEL="appforge-module-download-v3"
        private const val ID=7314
        fun start(context:Context){ContextCompat.startForegroundService(context,Intent(context,ModuleDownloadService::class.java))}
    }
}
