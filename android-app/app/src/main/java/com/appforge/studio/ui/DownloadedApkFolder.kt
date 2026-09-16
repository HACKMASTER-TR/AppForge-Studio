@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.appforge.studio.ui

import android.content.ContentUris
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal data class DownloadedApkEntry(val name:String,val uri:Uri,val modifiedAtMillis:Long,val sizeBytes:Long)

internal fun loadDownloadedAppForgeApks(context:Context):List<DownloadedApkEntry>{
    val out=mutableListOf<DownloadedApkEntry>()
    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
        val c=MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val p=arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.RELATIVE_PATH)
        val prefix="${Environment.DIRECTORY_DOWNLOADS}/AppForge Studio/"
        context.contentResolver.query(c,p,"${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",arrayOf("$prefix%"),"${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use{q->
            val ii=q.getColumnIndexOrThrow(MediaStore.MediaColumns._ID); val ni=q.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val di=q.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED); val si=q.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pi=q.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while(q.moveToNext()){
                val n=q.getString(ni).orEmpty(); val rp=q.getString(pi).orEmpty()
                if(!rp.startsWith(prefix,true)||!n.endsWith(".apk",true)) continue
                out+=DownloadedApkEntry(n,ContentUris.withAppendedId(c,q.getLong(ii)),q.getLong(di)*1000L,q.getLong(si))
            }
        }
        return out
    }
    @Suppress("DEPRECATION") val d=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"AppForge Studio")
    d.listFiles().orEmpty().filter{it.isFile&&it.name.endsWith(".apk",true)}.forEach{out+=DownloadedApkEntry(it.name,Uri.fromFile(it),it.lastModified(),it.length())}
    return out
}

private fun sizeText(
    value: Long
): String =
    when {
        value <= 0L ->
            "—"

        value < 1024L * 1024L ->
            "${
                (
                    (
                        value +
                            1023L
                    ) /
                        1024L
                ).coerceAtLeast(
                    1L
                )
            } KB"

        else ->
            String.format(
                Locale.ROOT,
                "%.1f MB",
                value /
                    1048576.0
            )
    }


private fun shareDownloadedApk(
    context: Context,
    entry: DownloadedApkEntry
) {
    runCatching {

        val sendIntent =
            Intent(
                Intent.ACTION_SEND
            ).apply {

                type =
                    "application/vnd.android.package-archive"

                putExtra(
                    Intent.EXTRA_STREAM,
                    entry.uri
                )

                clipData =
                    ClipData.newRawUri(
                        entry.name,
                        entry.uri
                    )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        val chooser =
            Intent.createChooser(
                sendIntent,
                "APK'yı paylaş"
            ).apply {

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                if (
                    context !is android.app.Activity
                ) {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            }

        context.startActivity(
            chooser
        )

    }.onFailure {

        Toast.makeText(
            context,
            "APK paylaşılamadı: ${
                it.message
                    ?: it.javaClass.simpleName
            }",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable internal fun DownloadedApkFolderScreen(onBack:()->Unit,onInstall:(Uri,String)->Unit){
    val ctx=LocalContext.current; val cfg=LocalConfiguration.current; val compact=cfg.screenWidthDp<390
    var data by remember{mutableStateOf<List<DownloadedApkEntry>>(emptyList())}; var loading by remember{mutableStateOf(true)}
    var err by remember{mutableStateOf("")}; var token by remember{mutableIntStateOf(0)}
    var search by rememberSaveable{mutableStateOf("")}; var newest by rememberSaveable{mutableStateOf(true)}
    LaunchedEffect(token){ loading=true; err=""; runCatching{withContext(Dispatchers.IO){loadDownloadedAppForgeApks(ctx)}}.onSuccess{data=it}.onFailure{err=it.message?:"Okunamadı"}; loading=false }
    val q=search.trim().lowercase(Locale.ROOT)
    val shown=data.filter{q.isBlank()||it.name.lowercase(Locale.ROOT).contains(q)}.let{if(newest)it.sortedByDescending{x->x.modifiedAtMillis}else it.sortedBy{x->x.modifiedAtMillis}}
    Scaffold(topBar={TopAppBar(title={Column{Text("Başarılı APK'lar",fontWeight=FontWeight.Black);Text("${data.size} indirilen APK",fontSize=11.sp)}},navigationIcon={TextButton(onClick=onBack){Text("← Geri")}},actions={TextButton(onClick={token++}){Text("Yenile")}})}){pad->
        LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(if(compact)12.dp else 18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("APK ara")})}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(newest) Button({newest=true},Modifier.weight(1f)){Text("Yeni → Eski")} else OutlinedButton({newest=true},Modifier.weight(1f)){Text("Yeni → Eski")}
                if(!newest) Button({newest=false},Modifier.weight(1f)){Text("Eski → Yeni")} else OutlinedButton({newest=false},Modifier.weight(1f)){Text("Eski → Yeni")}
            }}
            when{ loading->item{Text("APK klasörü okunuyor...")} ; err.isNotBlank()->item{Text("APK klasörü okunamadı: $err",color=MaterialTheme.colorScheme.error)} ; shown.isEmpty()->item{Text("Henüz indirilen APK yok")} ; else->items(shown,key={it.uri.toString()}){e->
                val dt=if(e.modifiedAtMillis>0) DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(e.modifiedAtMillis)) else "—"
                Card(onClick={onInstall(e.uri,e.name)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("APK",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(e.name,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("$dt • ${sizeText(e.sizeBytes)}",fontSize=11.sp)};Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){
                        TextButton(
                            onClick={onInstall(e.uri,e.name)}
                        ){Text("Kur")}
                        TextButton(
                            onClick={shareDownloadedApk(ctx,e)}
                        ){Text("Paylaş")}
                    }}
                }
            }}
        }
    }
}
