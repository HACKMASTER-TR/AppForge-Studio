fixtures = {
'ComposeRuntime.kt': '''package androidx.compose.runtime
import kotlin.reflect.KProperty
import kotlinx.coroutines.*
@Target(AnnotationTarget.FUNCTION,AnnotationTarget.TYPE) annotation class Composable
class MutableState<T>(var value:T)
fun <T> mutableStateOf(value:T)=MutableState(value)
operator fun <T> MutableState<T>.getValue(thisRef:Any?,property:KProperty<*>)=value
operator fun <T> MutableState<T>.setValue(thisRef:Any?,property:KProperty<*>,v:T){value=v}
fun <T> remember(vararg keys:Any?,calculation:()->T)=calculation()
fun rememberCoroutineScope()=CoroutineScope(Dispatchers.Default)
fun LaunchedEffect(vararg keys:Any?,block:suspend CoroutineScope.()->Unit){}
class DisposableEffectResult
class DisposableEffectScope { fun onDispose(block:()->Unit)=DisposableEffectResult() }
fun DisposableEffect(vararg keys:Any?,effect:DisposableEffectScope.()->DisposableEffectResult){}
''',
'Modifier.kt': 'package androidx.compose.ui\ninterface Modifier { companion object : Modifier }',
'Dp.kt': 'package androidx.compose.ui.unit\nclass Dp\nval Int.dp get()=Dp()',
'Layout.kt': '''package androidx.compose.foundation.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
object Arrangement { val SpaceBetween=Any(); fun spacedBy(value:Dp)=Any() }
fun Column(modifier:Modifier=Modifier,verticalArrangement:Any?=null,content:()->Unit){}
fun Row(modifier:Modifier=Modifier,horizontalArrangement:Any?=null,content:()->Unit){}
fun Modifier.fillMaxSize()=this
fun Modifier.fillMaxWidth()=this
fun Modifier.navigationBarsPadding()=this
fun Modifier.statusBarsPadding()=this
fun Modifier.padding(value:Dp)=this
''',
'Foundation.kt': '''package androidx.compose.foundation
import androidx.compose.ui.Modifier
class ScrollState
fun rememberScrollState()=ScrollState()
fun Modifier.horizontalScroll(state:ScrollState)=this
''',
'Lazy.kt': '''package androidx.compose.foundation.lazy
import androidx.compose.ui.Modifier
class LazyListScope { fun item(content:()->Unit){} }
fun LazyColumn(modifier:Modifier=Modifier,verticalArrangement:Any?=null,content:LazyListScope.()->Unit){}
''',
'Material.kt': '''package androidx.compose.material3
import androidx.compose.ui.Modifier
class Typography { val headlineSmall=Any(); val bodySmall=Any(); val titleMedium=Any() }
class ColorScheme { val error=Any() }
object MaterialTheme { val typography=Typography(); val colorScheme=ColorScheme() }
fun Text(text:String,modifier:Modifier=Modifier,style:Any?=null,color:Any?=null){}
fun Card(modifier:Modifier=Modifier,content:()->Unit){}
fun Button(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:()->Unit){}
fun OutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:()->Unit){}
fun OutlinedTextField(modifier:Modifier=Modifier,value:String,enabled:Boolean=true,minLines:Int=1,maxLines:Int=1,label:()->Unit,onValueChange:(String)->Unit){}
''',
'LocalContext.kt': 'package androidx.compose.ui.platform\nimport android.content.Context\nimport java.io.File\nobject LocalContext { val current=Context(File(".")) }',
'Library.kt': 'package com.appforge.studio.io\nimport android.content.Context\nclass SavedProject(val id:String,val name:String)\nobject ProjectLibrary { fun load(context:Context)=emptyList<SavedProject>() }',
}
