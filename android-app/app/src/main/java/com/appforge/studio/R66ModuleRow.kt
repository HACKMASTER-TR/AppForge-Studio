package com.appforge.studio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appforge.studio.build.*

/** R65 observed sequential net app-data deltas, NOT independent module sizes. */
internal object R66ModuleMeasurements {
    fun area(id:OfflineModuleId):String = when(id) {
        OfflineModuleId.RUST_ANDROID_NDK -> "10,14 GB"
        OfflineModuleId.RUST -> "2,02 GB"
        OfflineModuleId.FAST_HTML -> "Gömülü"
        OfflineModuleId.LINUX_RUNTIME -> "0,14 GB"
        OfflineModuleId.ANDROID_CORE -> "4,34 GB"
        OfflineModuleId.NODE_WEB -> "0,95 GB"
        OfflineModuleId.PYTHON_ANDROID -> "4,52 GB"
        OfflineModuleId.EXPO_REACT_NATIVE -> "9,42 GB"
        OfflineModuleId.WINDOWS_PORTABLE -> "0,38 GB"
        OfflineModuleId.WINDOWS_NATIVE -> "1,55 GB"
    }
}

/** Stage landmarks are a UI scale, not measured total installation percentages.
 * Individual transfer percentages are displayed only when byte totals exist.
 * No wall-clock progress, and only verified READY can fill the entire track.
 * A later dependency download is represented honestly as a download event.
 */
internal object R66ModuleProgress {
    data class State(val fraction:Float,val label:String,val color:Long)
    fun state(event:ModuleProgress?,installed:Boolean,running:Boolean,scanning:Boolean,blocked:Boolean):State {
        if(installed)return State(1f,"HAZIR ✓",0xFF16C65A)
        if(scanning)return State(0f,"Kontrol ediliyor",0xFF45CFEE)
        if(blocked)return State(0f,"Kurulum engellendi",0xFFEF3340)
        if(!running) return when(event?.phase) {
            ModulePhase.ERROR -> State(0f,"Hata · Tekrar dene",0xFFEF3340)
            ModulePhase.PAUSED,ModulePhase.WAITING_CONNECTION,ModulePhase.CANCELED -> State(0f,"Devam et",0xFF45CFEE)
            else -> State(0f,"İndir ve Kur",0xFF45CFEE)
        }
        val percent=event?.percent
        // R67: a source checksum is acquisition, not final module validation.
        if(event?.acquisitionPending==true) {
            val label=when(event.phase) {
                ModulePhase.DOWNLOADING -> percent?.let{"İndiriliyor · dosya %$it"} ?: "İndiriliyor"
                ModulePhase.VERIFYING -> "İndirilen paket doğrulanıyor"
                ModulePhase.WAITING_CONNECTION,ModulePhase.RECONNECTING -> "Bağlantı bekleniyor"
                else -> "İndirme hazırlanıyor"
            }
            return State((percent?:0)/100f*.50f,label,0xFFEF3340)
        }
        val total=event?.installUnitsTotal
        val done=event?.installUnitsCompleted
        if(event!=null && total!=null && done!=null && total>0 && done in 0..total) {
            val fraction=.50f+.35f*done/total
            val state=when(event.phase) {
                // Some guest dependencies still download during installation.
                // Report them explicitly without treating file % as whole-module %.
                ModulePhase.DOWNLOADING -> State(fraction,percent?.let{"Ek paket indiriliyor · dosya %$it"} ?: "Ek paket indiriliyor",0xFFEF3340)
                ModulePhase.ERROR -> State(fraction,"Hata oluştu",0xFFEF3340)
                ModulePhase.AWAITING_CONSENT -> State(fraction,"Onay bekleniyor",0xFFFF8A24)
                ModulePhase.WAITING_CONNECTION,ModulePhase.RECONNECTING -> State(fraction,"Bağlantı bekleniyor",0xFFEF3340)
                ModulePhase.OFFLINE_TEST,ModulePhase.VERIFYING,ModulePhase.READY -> State(fraction,
                    if(done==total)"Doğrulanıyor" else "Kuruluyor · ara kontrol",if(done==total)0xFFFFD42A else 0xFFFF8A24)
                else -> State(fraction,"Kuruluyor · $done/$total",0xFFFF8A24)
            }
            return state
        }
        return when(event?.phase) {
            ModulePhase.DOWNLOADING -> State((percent?:0)/100f*0.50f,
                percent?.let{"İndiriliyor · dosya %$it"} ?: "İndiriliyor",0xFFEF3340)
            ModulePhase.INSTALLING,ModulePhase.EXTRACTING -> State(.50f,"Kuruluyor",0xFFFF8A24)
            ModulePhase.OFFLINE_TEST,ModulePhase.VERIFYING,ModulePhase.READY ->
                State(.85f,"Doğrulanıyor",0xFFFFD42A)
            ModulePhase.AWAITING_CONSENT -> State(0f,"Onay bekleniyor",0xFFEF3340)
            ModulePhase.WAITING_CONNECTION,ModulePhase.RECONNECTING -> State(0f,"Bağlantı bekleniyor",0xFFEF3340)
            ModulePhase.ERROR -> State(0f,"Hata oluştu",0xFFEF3340)
            else -> State(0f,"Hazırlanıyor",0xFFEF3340)
        }
    }
}

@Composable internal fun R66ModuleRow(module:OfflineModuleSpec,installed:Boolean,running:Boolean,
    scanning:Boolean,blocked:Boolean,event:ModuleProgress?,enabled:Boolean,
    onClick:()->Unit,onError:()->Unit) {
    val state=R66ModuleProgress.state(event,installed,running,scanning,blocked)
    val fraction by animateFloatAsState(state.fraction,animationSpec=tween(350),label="module-fill")
    val tint by animateColorAsState(Color(state.color),animationSpec=tween(550),label="module-stage")
    val shape=RoundedCornerShape(24.dp)
    val cardShape=RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().border(1.dp,Color(0xFF328FA9),cardShape)
        .background(Color.Black,cardShape).padding(horizontal=12.dp,vertical=10.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(module.title,style=MaterialTheme.typography.titleSmall,
            color=Color(0xFF48CFEE),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        Text(R66ModuleMeasurements.area(module.id),style=MaterialTheme.typography.bodySmall,
            color=Color(0xFFBFC4C9),textAlign=TextAlign.Center)
        Box(Modifier.fillMaxWidth().heightIn(min=40.dp).clip(shape)
            .background(Color(0xFF191E23)).border(1.dp,Color(0xFF328FA9),shape)
            .semantics {
                role=Role.Button
                stateDescription=state.label
            }.clickable(enabled=enabled,onClick=onClick),
            contentAlignment=Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val w=size.width*fraction.coerceIn(0f,1f)
                if(w>0f)drawRoundRect(tint,size=Size(w,size.height),
                    cornerRadius=CornerRadius(minOf(size.height/2,w/2)))
            }
            Text(state.label,modifier=Modifier.padding(horizontal=8.dp,vertical=7.dp),
                style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Medium,
                color=if(fraction>.83f && !installed)Color.Black else Color.White,
                textAlign=TextAlign.Center)
        }
        if(!running && !installed && event?.phase==ModulePhase.ERROR)
            Text("Hata ayrıntısı",color=Color(0xFFEF3340),style=MaterialTheme.typography.labelSmall,
                modifier=Modifier.clickable(onClick=onError))
    }
}
