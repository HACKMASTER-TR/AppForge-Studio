package com.appforge.studio.build

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.asContextElement

internal enum class DownloadCapability { BYTE_RESUME, CHECKPOINT_RESUME, SAFE_RESTART, BLOCKED }
internal data class DownloadNetworkFacts(val validated:Boolean,val wifi:Boolean,val cellular:Boolean,val vpn:Boolean,val metered:Boolean)
internal object DownloadNetworkRules {
    fun choose(request:DownloadNetworkPolicy,current:DownloadNetworkPolicy,active:Int?,candidates:List<DownloadNetworkFacts>):Int? {
        if(active!=null && candidates[active].vpn && (request!=DownloadNetworkPolicy.ALLOW_ANY || current!=DownloadNetworkPolicy.ALLOW_ANY))return null
        val order=(listOfNotNull(active)+candidates.indices).distinct()
        return order.firstOrNull{permits(request,candidates[it]) && permits(current,candidates[it])}
    }
    fun externalCapability(policy:DownloadNetworkPolicy)=if(policy==DownloadNetworkPolicy.ALLOW_ANY)DownloadCapability.CHECKPOINT_RESUME else DownloadCapability.BLOCKED
    fun permits(policy:DownloadNetworkPolicy,f:DownloadNetworkFacts)=f.validated && when(policy) {
        DownloadNetworkPolicy.ALLOW_ANY->true
        else->f.wifi && !f.cellular && !f.vpn && !f.metered
    }
}
/** Restrictive requests are bound to a validated, unmetered Wi-Fi Network, never process-wide routing. */
internal object DownloadNetwork {
    class PolicyBlocked:java.util.concurrent.CancellationException("Ağ politikası bağlantıyı engelledi; Wi-Fi veya açık mobil veri onayı bekleniyor.")
    @Volatile private var context:Context?=null
    @Volatile private var preference=DownloadNetworkPolicy.ALLOW_ANY
    /** New installs accept validated Wi-Fi or cellular; valid saved choices win.
     * Corrupt persisted policy fails closed to the previous restricted default. */
    internal fun initialPolicy(saved:String?):DownloadNetworkPolicy =
        if(saved==null)DownloadNetworkPolicy.ALLOW_ANY
        else runCatching{DownloadNetworkPolicy.valueOf(saved)}.getOrDefault(DownloadNetworkPolicy.WIFI_PREFERRED)
    private class Consent { @Volatile var active=true }
    private val consent=ThreadLocal<Consent?>()
    private val pendingConsent=java.util.concurrent.atomic.AtomicBoolean(false)
    val policy:DownloadNetworkPolicy get()=if(consent.get()?.active==true)DownloadNetworkPolicy.ALLOW_ANY else preference
    /** Claimed by the initiating module driver only, inherited by its coroutine children. */
    fun takeOperationConsent():kotlin.coroutines.CoroutineContext=consent.asContextElement(if(pendingConsent.getAndSet(false))Consent() else null)
    private fun <T> withConsent(value:Consent?,action:()->T):T {
        val old=consent.get();consent.set(value)
        try{return action()}finally{if(old==null)consent.remove()else consent.set(old)}
    }
    val status=kotlinx.coroutines.flow.MutableStateFlow("Bağlantı henüz doğrulanmadı")
    private val requested=ThreadLocal<DownloadNetworkPolicy>()
    private data class Binding(val request:DownloadNetworkPolicy,val network:android.net.Network,val consent:Consent?)
    private val connections=java.util.concurrent.ConcurrentHashMap<HttpURLConnection,Binding>()
    fun <T> withPolicy(request:DownloadNetworkPolicy,action:()->T):T {
        val old=requested.get();requested.set(request)
        try{return action()}finally{if(old==null)requested.remove()else requested.set(old)}
    }
    fun release(connection:HttpURLConnection){connections.remove(connection)}
    private var callback:ConnectivityManager.NetworkCallback?=null
    @Synchronized fun configure(app:Context){if(context!=null)return;context=app.applicationContext;preference=initialPolicy(app.getSharedPreferences("download-v5",0).getString("policy",null))
        val cm=app.getSystemService(ConnectivityManager::class.java)
        if(cm!=null) {
            val observer=object:ConnectivityManager.NetworkCallback(){
                override fun onAvailable(network:android.net.Network){refresh()}
                override fun onLost(network:android.net.Network){
                    connections.entries.filter{it.value.network==network}.forEach{(connection,_)->connection.disconnect();connections.remove(connection)}
                    refresh()
                }
                override fun onCapabilitiesChanged(network:android.net.Network,caps:NetworkCapabilities){refresh()}
            }
            cm.registerNetworkCallback(android.net.NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),observer);callback=observer;refresh()
        }
    }
    private fun refresh(){
        val cm=context?.getSystemService(ConnectivityManager::class.java)
        connections.entries.forEach{(connection,binding)->
            withConsent(binding.consent) {
                val caps=cm?.getNetworkCapabilities(binding.network)
                if(caps==null || !allowed(binding.request) || !DownloadNetworkRules.permits(binding.request,facts(caps)) || !DownloadNetworkRules.permits(policy,facts(caps))){connection.disconnect();connections.remove(connection)}
            }
        }
        status.value=if(allowed())"Doğrulanmış ağ politikaya uygun" else "Bağlantı/ağ onayı bekleniyor; yeni aktarım engellendi"
    }
    fun allowMobileOnce(){check(pendingConsent.compareAndSet(false,true)){"Önceki ağ onayı henüz işleme bağlanmadı."}}
    fun restorePreference(){pendingConsent.set(false);consent.get()?.active=false;refresh()}
    fun prepareJournal() {
        val app=context?:return
        val roots=mapOf("private" to app.noBackupFilesDir,"files" to app.filesDir,"cache" to app.cacheDir).mapValues { (_,path)->
            RecoveryPathAnchor.androidPlatformPath(path).also { RecoveryPathAnchor.check(it) }
        }
        DownloadCoordinator.configure(PersistentDownloads(java.io.File(roots.getValue("private"),"downloads-v5"),roots))
    }
    fun select(value:DownloadNetworkPolicy){preference=value;context?.getSharedPreferences("download-v5",0)?.edit()?.putString("policy",value.name)?.apply();refresh()}
    private fun facts(c:NetworkCapabilities)=DownloadNetworkFacts(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),c.hasTransport(NetworkCapabilities.TRANSPORT_VPN),!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    private fun selected(cm:ConnectivityManager,request:DownloadNetworkPolicy):android.net.Network? {
        val active=cm.activeNetwork
        val networks=(listOfNotNull(active)+cm.allNetworks.toList()).distinct()
        val candidates=networks.map{cm.getNetworkCapabilities(it)?.let(::facts)?:DownloadNetworkFacts(false,false,false,false,true)}
        return DownloadNetworkRules.choose(request,policy,networks.indexOf(active).takeIf{it>=0},candidates)?.let{networks[it]}
    }
    fun allowed(request:DownloadNetworkPolicy=policy):Boolean {
        val app=context?:return true // Host harness only; Application initializes the production context.
        val cm=app.getSystemService(ConnectivityManager::class.java)?:return false
        return selected(cm,request)!=null
    }
    fun check(request:DownloadNetworkPolicy=policy){if(!allowed(request))throw PolicyBlocked()}
    fun open(url:URL):HttpURLConnection {
        val app=context?:return url.openConnection() as HttpURLConnection
        val cm=app.getSystemService(ConnectivityManager::class.java)?:throw PolicyBlocked()
        val request=requested.get()?:policy
        val network=selected(cm,request)?:throw PolicyBlocked()
        return (network.openConnection(url) as HttpURLConnection).also{connection->
            connections[connection]=Binding(request,network,consent.get())
            val caps=cm.getNetworkCapabilities(network)
            if(caps==null || !DownloadNetworkRules.permits(request,facts(caps)) || !DownloadNetworkRules.permits(policy,facts(caps))){
                connection.disconnect();connections.remove(connection);throw PolicyBlocked()
            }
        }
    }
    /** Shell package managers cannot be safely rebound across network changes by this service. */
    fun requireExternalConsent(){require(context==null || DownloadNetworkRules.externalCapability(policy)!=DownloadCapability.BLOCKED){"Bu paket yöneticisinin Wi-Fi kısıtı ağ geçişinde garanti edilemiyor. İndirme engellendi; yalnız açık Wi-Fi ve mobil veri izniyle çalışabilir."}}
}
