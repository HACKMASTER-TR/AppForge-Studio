package com.appforge.studio.build

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

internal data class DownloadNetworkFacts(val validated:Boolean,val wifi:Boolean,val cellular:Boolean,val vpn:Boolean,val metered:Boolean)
internal object DownloadNetworkRules {
    fun permits(policy:DownloadNetworkPolicy,f:DownloadNetworkFacts)=f.validated && when(policy) {
        DownloadNetworkPolicy.ALLOW_ANY->true
        else->f.wifi && !f.cellular && !f.vpn && !f.metered
    }
}
/** Restrictive requests are bound to a validated, unmetered Wi-Fi Network, never process-wide routing. */
internal object DownloadNetwork {
    class PolicyBlocked:java.util.concurrent.CancellationException("Ağ politikası bağlantıyı engelledi; Wi-Fi veya açık mobil veri onayı bekleniyor.")
    @Volatile private var context:Context?=null
    @Volatile var policy=DownloadNetworkPolicy.WIFI_PREFERRED
        private set
    val status=kotlinx.coroutines.flow.MutableStateFlow("Bağlantı henüz doğrulanmadı")
    private val requested=ThreadLocal<DownloadNetworkPolicy>()
    private val connections=java.util.concurrent.ConcurrentHashMap<HttpURLConnection,DownloadNetworkPolicy>()
    fun <T> withPolicy(request:DownloadNetworkPolicy,action:()->T):T {
        val old=requested.get();requested.set(request)
        try{return action()}finally{if(old==null)requested.remove()else requested.set(old)}
    }
    fun release(connection:HttpURLConnection){connections.remove(connection)}
    private var callback:ConnectivityManager.NetworkCallback?=null
    @Synchronized fun configure(app:Context){if(context!=null)return;context=app.applicationContext;policy=runCatching{DownloadNetworkPolicy.valueOf(app.getSharedPreferences("download-v5",0).getString("policy",DownloadNetworkPolicy.WIFI_PREFERRED.name)?:DownloadNetworkPolicy.WIFI_PREFERRED.name)}.getOrDefault(DownloadNetworkPolicy.WIFI_PREFERRED)
        val cm=app.getSystemService(ConnectivityManager::class.java)
        if(cm!=null) {
            val observer=object:ConnectivityManager.NetworkCallback(){
                override fun onAvailable(network:android.net.Network){refresh()}
                override fun onLost(network:android.net.Network){refresh()}
                override fun onCapabilitiesChanged(network:android.net.Network,caps:NetworkCapabilities){refresh()}
            }
            cm.registerDefaultNetworkCallback(observer);callback=observer;refresh()
        }
    }
    private fun refresh(){connections.entries.forEach{(connection,request)->if(!allowed(request)){connection.disconnect();connections.remove(connection)}};status.value=if(allowed())"Doğrulanmış ağ politikaya uygun" else "Bağlantı/ağ onayı bekleniyor; yeni aktarım engellendi"}
    fun allowMobileOnce(){policy=DownloadNetworkPolicy.ALLOW_ANY;refresh()}
    fun restorePreference(){val app=context?:return;policy=runCatching{DownloadNetworkPolicy.valueOf(app.getSharedPreferences("download-v5",0).getString("policy",DownloadNetworkPolicy.WIFI_PREFERRED.name)!!)}.getOrDefault(DownloadNetworkPolicy.WIFI_PREFERRED);refresh()}
    fun prepareJournal() {
        val app=context?:return
        DownloadCoordinator.configure(PersistentDownloads(java.io.File(app.noBackupFilesDir,"downloads-v5"),mapOf("private" to app.noBackupFilesDir,"files" to app.filesDir,"cache" to app.cacheDir)))
    }
    fun select(value:DownloadNetworkPolicy){policy=value;context?.getSharedPreferences("download-v5",0)?.edit()?.putString("policy",value.name)?.apply();refresh()}
    private fun facts(c:NetworkCapabilities)=DownloadNetworkFacts(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),c.hasTransport(NetworkCapabilities.TRANSPORT_VPN),!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    fun allowed(request:DownloadNetworkPolicy=policy):Boolean {
        val app=context?:return true // host/unit use has no Android network, app configures before starting work
        val cm=app.getSystemService(ConnectivityManager::class.java)?:return false
        val network=cm.activeNetwork?:return false
        return cm.getNetworkCapabilities(network)?.let{DownloadNetworkRules.permits(request,facts(it)) && DownloadNetworkRules.permits(policy,facts(it))}?:false
    }
    fun check(request:DownloadNetworkPolicy=policy){if(!allowed(request))throw PolicyBlocked()}
    fun open(url:URL):HttpURLConnection {
        val app=context?:return url.openConnection() as HttpURLConnection
        val cm=app.getSystemService(ConnectivityManager::class.java)?:throw PolicyBlocked()
        val network=cm.activeNetwork?:throw PolicyBlocked()
        val caps=cm.getNetworkCapabilities(network)?:throw PolicyBlocked()
        val request=requested.get()?:policy
        if(!DownloadNetworkRules.permits(request,facts(caps)) || !DownloadNetworkRules.permits(policy,facts(caps)))throw PolicyBlocked()
        return (network.openConnection(url) as HttpURLConnection).also{connections[it]=request}
    }
    /** Shell package managers cannot be safely rebound across network changes by this service. */
    fun requireExternalConsent(){require(context==null || policy==DownloadNetworkPolicy.ALLOW_ANY){"Bu paket yöneticisinin Wi-Fi kısıtı ağ geçişinde garanti edilemiyor. İndirme engellendi; yalnız açık Wi-Fi ve mobil veri izniyle çalışabilir."}}
}
