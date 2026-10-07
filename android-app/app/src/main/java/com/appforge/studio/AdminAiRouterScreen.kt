package com.appforge.studio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.security.OwnerAccessPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class AdminAiResult(val provider:String,val model:String,val content:String,val fallbackUsed:Boolean)
private class AdminAiAuthorizationDenied(message:String):IllegalStateException(message)

@Composable
fun AdminAiRouterScreen(serverUrl:String,onAuthorizationLost:()->Unit,onBack:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val client=remember(serverUrl){AdminAiRouterApiClient(serverUrl)}
    var provider by remember{mutableStateOf("auto")}
    var prompt by remember{mutableStateOf("")}
    var result by remember{mutableStateOf<AdminAiResult?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var sending by remember{mutableStateOf(false)}
    val providers=listOf("auto" to "AUTO","groq" to "GROQ","gemini" to "GEMINI","workers_ai" to "WORKERS AI","openrouter" to "OPENROUTER")
    LazyColumn(modifier=Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("AppForge AI",style=MaterialTheme.typography.headlineSmall);Text("Yalnız doğrulanmış yönetici",style=MaterialTheme.typography.bodySmall)};OutlinedButton(onClick=onBack){Text("GERİ")}}}
        item{Card(modifier=Modifier.fillMaxWidth()){Column(modifier=Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("AI Router",style=MaterialTheme.typography.titleMedium)
            Text("AUTO modunda AppForge göreve göre sağlayıcı seçer ve gerektiğinde diğer sağlayıcılara geçer.",style=MaterialTheme.typography.bodySmall)
            Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){providers.forEach{choice->val id=choice.first;val label=choice.second;if(provider==id)Button(onClick={provider=id}){Text(label)} else OutlinedButton(onClick={provider=id}){Text(label)}}}
            OutlinedTextField(modifier=Modifier.fillMaxWidth(),value=prompt,enabled=!sending,minLines=5,maxLines=12,label={Text("Yönetici istemi")},onValueChange={if(it.length<=12000)prompt=it})
            Button(modifier=Modifier.fillMaxWidth(),enabled=!sending&&prompt.isNotBlank(),onClick={scope.launch{val token=OwnerAccessPolicy.currentGoogleIdToken();if(token==null){result=null;error="Google yönetici doğrulaması gerekli.";onAuthorizationLost();return@launch};sending=true;result=null;error=null;try{result=withContext(Dispatchers.IO){client.chat(token,prompt,provider)}}catch(_:AdminAiAuthorizationDenied){OwnerAccessPolicy.clearVerifiedGoogleAdmin(context);error="Yönetici oturumu sona erdi.";onAuthorizationLost()}catch(e:Exception){error=e.message?.take(300)?:"AppForge AI isteği başarısız."}finally{sending=false}}}){Text(if(sending)"YANIT BEKLENİYOR..." else "APPFORGE AI'YA SOR")}
        }}}
        if(!error.isNullOrBlank())item{Card(modifier=Modifier.fillMaxWidth()){Text(error.orEmpty(),modifier=Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}}
        result?.let{a->item{Card(modifier=Modifier.fillMaxWidth()){Column(modifier=Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Yanıt",style=MaterialTheme.typography.titleMedium);Text("Sağlayıcı: ${a.provider}");Text("Model: ${a.model}",style=MaterialTheme.typography.bodySmall);if(a.fallbackUsed)Text("Fallback kullanıldı",style=MaterialTheme.typography.bodySmall);Text(a.content)}}}}
    }
}

private class AdminAiRouterApiClient(private val baseUrl:String){
    fun chat(token:String,prompt:String,provider:String):AdminAiResult{
        require(token.length in 50..12000){"Google yönetici doğrulaması gerekli."}
        val p=prompt.trim();require(p.length in 1..12000){"AI istemi geçersiz."}
        val providerId=provider.trim().lowercase();require(providerId in setOf("auto","groq","gemini","workers_ai","openrouter")){"AI sağlayıcısı geçersiz."}
        val c=(URL(controlPlaneBaseUrl()+"/api/admin/ai/chat").openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=15000;readTimeout=45000;doOutput=true;setRequestProperty("Accept","application/json");setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("Authorization","Bearer $token")}
        try{
            val body=JSONObject().put("prompt",p).put("provider",providerId).toString();c.outputStream.bufferedWriter(Charsets.UTF_8).use{it.write(body)}
            val code=c.responseCode;val text=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}.orEmpty()
            if(code !in 200..299){val e=runCatching{JSONObject(text).optString("error")}.getOrDefault("").ifBlank{"ai_request_failed"};if(code==401||code==403)throw AdminAiAuthorizationDenied("HTTP $code • $e");throw IllegalStateException(when(e){"ai_providers_unavailable"->"AI sağlayıcılarının hiçbiri şu anda yanıt vermiyor.";"ai_provider_not_configured"->"Seçilen AI sağlayıcısı sunucuda yapılandırılmamış.";"invalid_ai_prompt"->"AI istemi geçersiz.";else->"AppForge AI kullanılamadı • $e"})}
            val r=JSONObject(text);check(r.optBoolean("ok")){"AppForge AI yanıtı doğrulanamadı."};val content=r.optString("content").trim();check(content.isNotBlank()){"AppForge AI boş yanıt döndürdü."};return AdminAiResult(r.optString("provider").ifBlank{"unknown"},r.optString("model").ifBlank{"unknown"},content,r.optBoolean("fallbackUsed"))
        }finally{c.disconnect()}
    }
    private fun controlPlaneBaseUrl():String{val v=baseUrl.trim().trimEnd('/');require(v.startsWith("https://",true)||v.startsWith("http://10.0.2.2",true)){"AppForge AI üretimde HTTPS Control Plane gerektirir."};return v}
}
