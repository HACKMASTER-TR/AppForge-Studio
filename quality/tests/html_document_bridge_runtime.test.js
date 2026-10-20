import test from 'node:test';
import {extendV2AndroidStubs, extendConnectivityStubs, extendAwakeStubs, extendWebPermissionStubs, nfcStubs, scannerBoundaryStub, mediaBoundaryStub, locationBoundaryStub, billingBoundaryStub} from './helpers/fast_html_v2_android_stubs.js';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import os from 'node:os';
import {execFileSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'../..');
const variants=['android-app/app/src/main/assets/device-build','android-app/html-apk-template/src/main/java/com/appforge/htmlstub'];
const source=dir=>fs.readFileSync(path.join(root,dir,'AppForgeDocuments.java'),'utf8');
const shipped=dir=>JSON.parse(source(dir).match(/static final String SCRIPT = (".*");/)[1]);
function browser(dir,{sharing=true,downloads=true,subframe=false,connect=true,legacy=false,local=false,localLocation=false}={}){
 const events={}, clicks={}, messages=[], transferred=[],compatMessages=[];
 let bytes=[], mode, finish;
 const port={start(){},postMessage(raw){const m=JSON.parse(raw);messages.push(m);
  if(m.op==='begin'){bytes=[];mode=m.mode;}
  if(m.op==='chunk')bytes.push(Buffer.from(m.data,'base64'));
  if(m.op==='finish')finish=Buffer.concat(bytes);
  queueMicrotask(()=>{const data=JSON.stringify({id:m.id,ok:true,result:m.op==='finish'?(mode==='share'?'chooser-launched':'saved'):'ready'});if(local)context.__appforgeDocumentReply(data);else port.onmessage({data});});
 }};
 const context={Blob,File,DOMException,Uint8Array,Map,Promise,Object,Number,String,JSON,TypeError,RangeError,Error,
  fetch,URL,btoa,console,setTimeout,clearTimeout,navigator:{},
  CustomEvent:class{constructor(type,args){this.type=type;this.detail=args.detail;}},
  document:{addEventListener:(type,fn)=>clicks[type]=fn},
  addEventListener:(type,fn)=>events[type]=fn,dispatchEvent:e=>transferred.push(e)};
 if(local)context.AppForgeLocalDocuments={send(token,raw){assert.equal(token,'secret-token');port.postMessage(raw);}};
 context.window=context;context.top=subframe?{}:context;
 if(legacy)context.AppForgeNativeCompat={call(token,raw){assert.equal(token,'secret-token');const m=JSON.parse(raw);compatMessages.push(m);return m.method==='getLocation'?'PENDING':m.method==='download'?'OK|1|report.pdf':'OK';}};
 vm.runInNewContext(shipped(dir).replaceAll('__SHARE__',String(sharing)).replaceAll('__DOWNLOAD__',String(downloads)).replaceAll('__LEGACY__',String(legacy)).replaceAll('__LOCAL_LOCATION__',String(localLocation)).replaceAll('__NATIVE__',String(legacy)).replaceAll('__PORT_TOKEN__','secret-token'),context);
 if(connect&&!local&&events.message)events.message({data:'secret-token',ports:[port]});
 return {context,events,clicks,messages,compatMessages,port,transferred,get bytes(){return finish;}};
}
for(const dir of variants){
 test(`${dir}: opaque file transport saves and shares exact bytes; subframes get no capability`,async()=>{
  const b=browser(dir,{local:true});
  const bytes=Buffer.from('%PDF-legacy-data');
  assert.equal(await b.context.AppForgeDocuments.save(new Blob([bytes],{type:'application/pdf'})),'saved');
  assert.deepEqual(b.bytes,bytes);
  assert.equal(await b.context.navigator.share({files:[new File([bytes],'legacy.pdf',{type:'application/pdf'})]}),'chooser-launched');
  assert.deepEqual(b.bytes,bytes);
  assert.equal(browser(dir,{local:true,subframe:true}).context.AppForgeDocuments,undefined);
 });
 test(`${dir}: real Blob, chunk boundaries and PDF File sharing`,async()=>{
  const b=browser(dir);const bytes=Buffer.concat([Buffer.from('%PDF-1.7\n'),Buffer.from(Array.from({length:70000},(_,i)=>i%256))]);
  assert.equal(await b.context.AppForgeDocuments.save(new Blob([bytes],{type:'application/pdf'}),'report.pdf'),'saved');
  assert.deepEqual(b.bytes,bytes);
  assert.deepEqual(b.messages.filter(m=>m.op==='chunk').map(m=>m.offset),[0,24576,49152]);
  assert.equal(await b.context.navigator.share({files:[new File([bytes],'report.pdf',{type:'application/pdf'})]}),'chooser-launched');
  assert.deepEqual(b.bytes,bytes);assert.equal(b.messages.findLast(m=>m.op==='begin').mode,'share');
 });
 test(`${dir}: UTF-8 CSV uses separate exact bytes, escaped fields and save/share modes`,async()=>{
  const b=browser(dir);
  const blob=b.context.AppForgeDocuments.csv([['name','value'],['İstanbul, Kayseri','a"b\nc']]);
  const bytes=Buffer.from(await blob.arrayBuffer());
  assert.equal(bytes.toString(),'"name","value"\r\n"İstanbul, Kayseri","a""b\nc"\r\n');
  assert.equal(await b.context.AppForgeDocuments.save(blob,'records.csv'),'saved');assert.deepEqual(b.bytes,bytes);
  assert.equal(b.messages.findLast(m=>m.op==='begin').mime,'text/csv');
  assert.equal(await b.context.navigator.share({files:[new File([bytes],'records.csv',{type:'application/csv'})]}),'chooser-launched');
  assert.equal(b.messages.findLast(m=>m.op==='begin').mode,'share');assert.deepEqual(b.bytes,bytes);
  await b.context.AppForgeDocuments.download('data:text/csv;charset=utf-8;base64,'+bytes.toString('base64'),'records.csv');
  assert.deepEqual(b.bytes,bytes);
 });
 test(`${dir}: actual data and blob URLs preserve bytes`,async()=>{
  const b=browser(dir), bytes=Buffer.from('%PDF-1.4\n\x00\xff%%EOF','latin1');
  await b.context.AppForgeDocuments.download('data:application/pdf;base64,'+bytes.toString('base64'),'data.pdf');
  assert.deepEqual(b.bytes,bytes);
  const url=URL.createObjectURL(new Blob([bytes],{type:'application/pdf'}));
  try{await b.context.AppForgeDocuments.download(url,'blob.pdf');assert.deepEqual(b.bytes,bytes);}finally{URL.revokeObjectURL(url);}
 });
 test(`${dir}: JS anchor click honors download name; HTTP fallback is separate`,async()=>{
  const b=browser(dir);let stopped=false;
  b.clicks.click({target:{tagName:'A',href:'data:application/pdf;base64,JVBERi0=',download:'name.pdf'},preventDefault(){},stopImmediatePropagation(){stopped=true;}});
  await new Promise(r=>setImmediate(r));assert.ok(stopped);assert.equal(b.messages[0].name,'name.pdf');assert.equal(b.bytes.toString(),'%PDF-');
  b.clicks.click({target:{tagName:'A',href:'https://example.test/report.pdf',hasAttribute:()=>false},preventDefault(){},stopImmediatePropagation(){}});
  await new Promise(r=>setImmediate(r));assert.equal(b.messages.at(-1).op,'http');
 });
 test(`${dir}: MIME/signature/size/scheme, duplicates, subframes and share opt-out`,async()=>{
  const b=browser(dir,{sharing:false});assert.equal(b.context.navigator.share,undefined);
  assert.equal(browser(dir,{subframe:true}).context.AppForgeDocuments,undefined);
  await assert.rejects(b.context.AppForgeDocuments.save(new Blob(['%PDF-'],{type:'text/plain'})),/Expected PDF/);
  await assert.rejects(b.context.AppForgeDocuments.save(new Blob(['wrong'],{type:'application/pdf'})),/signature/);
  await assert.rejects(b.context.AppForgeDocuments.save(new Blob([new Uint8Array(16*1024*1024+1)],{type:'application/pdf'})),/Expected PDF/);
  for(const url of ['file:///secret.pdf','content://secret/pdf','https://example.test/a.pdf','javascript:alert(1)'])await assert.rejects(b.context.AppForgeDocuments.download(url),/generated PDF/);
  const first=b.context.AppForgeDocuments.save(new Blob(['%PDF-'],{type:'application/pdf'}));
  await assert.rejects(b.context.AppForgeDocuments.save(new Blob(['%PDF-'],{type:'application/pdf'})),/already active/);await first;
 });
 test(`${dir}: legacy native strings stay synchronous and use private capability`,()=>{
  const b=browser(dir,{legacy:true});
  assert.equal(b.context.AppForgeFastNative.share('title','text'),'OK');
  assert.equal(b.context.AppForgeFastNative.copy('text'),'OK');
  assert.equal(b.context.AppForgeFastNative.vibrate(100),'OK');
  assert.equal(b.context.AppForgeFastNative.scanCode(),'OK');
  assert.equal(b.context.AppForgeDownloads.download('https://example.test/report.pdf'),'OK|1|report.pdf');
  assert.equal(b.context.AppForgeCaptureHint.markCapture(),'OK');
 });
 test(`${dir}: forged iframe handshake cannot steal transfer`,async()=>{
  const b=browser(dir,{connect:false});let stolen=false;
  b.events.message({data:'appforge-document-port-v1',ports:[{start(){stolen=true;},postMessage(){stolen=true;}}]});
  assert.equal(stolen,false);
  b.events.message({data:'secret-token',ports:[b.port]});
  await b.context.AppForgeDocuments.save(new Blob(['%PDF-'],{type:'application/pdf'}));assert.equal(stolen,false);
 });
}
// Compile and execute production Java transfer/intent code against small Android endpoints.
// This proves host behavior, not a device WebView/SAF implementation.
const stubs={
 'android/content/ContentValues.java':`package android.content; public class ContentValues {}`,
 'android/content/ContentProvider.java':`package android.content; public abstract class ContentProvider { public Context context; public Context getContext(){return context;} public abstract boolean onCreate(); public abstract android.os.ParcelFileDescriptor openFile(android.net.Uri u,String m) throws java.io.FileNotFoundException; public abstract String getType(android.net.Uri u); public abstract android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o); public abstract android.net.Uri insert(android.net.Uri u,ContentValues v); public abstract int update(android.net.Uri u,ContentValues v,String s,String[] a); public abstract int delete(android.net.Uri u,String s,String[] a); }`,
 'android/database/Cursor.java':`package android.database; public interface Cursor {}`,
 'android/database/MatrixCursor.java':`package android.database; public class MatrixCursor implements Cursor { public Object[] row; public MatrixCursor(String[] c){} public void addRow(Object[] r){row=r;} }`,
 'android/os/ParcelFileDescriptor.java':`package android.os; public class ParcelFileDescriptor {public static final int MODE_READ_ONLY=1; public java.io.File file; public static ParcelFileDescriptor open(java.io.File f,int mode) throws java.io.FileNotFoundException {if(mode!=MODE_READ_ONLY)throw new AssertionError();if(!f.isFile())throw new java.io.FileNotFoundException();ParcelFileDescriptor p=new ParcelFileDescriptor();p.file=f;return p;} }`,
 'android/os/Bundle.java':`package android.os; public class Bundle {}`,
 'android/graphics/Color.java':`package android.graphics; public class Color {public static int parseColor(String s){return 0;} }`,
 'android/view/Window.java':`package android.view; public class Window {public void setStatusBarColor(int c){} public void setNavigationBarColor(int c){} }`,
 'android/graphics/Bitmap.java':`package android.graphics; public class Bitmap {}`,
 'android/webkit/JavascriptInterface.java':`package android.webkit; public @interface JavascriptInterface {}`,
 'android/webkit/DownloadListener.java':`package android.webkit; public interface DownloadListener {void onDownloadStart(String u,String a,String d,String m,long l);}`,
 'android/webkit/WebViewClient.java':`package android.webkit; public class WebViewClient {public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return null;} public void onPageStarted(WebView w,String u,android.graphics.Bitmap b){} public void onPageFinished(WebView w,String u){} public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return false;} public boolean shouldOverrideUrlLoading(WebView w,String u){return false;}}`,
 'android/content/res/AssetManager.java':`package android.content.res; public class AssetManager {public static byte[] config; public java.io.InputStream open(String p) throws java.io.IOException {if(config!=null)return new java.io.ByteArrayInputStream(config);throw new java.io.IOException();}}`,
 'android/webkit/WebResourceRequest.java':`package android.webkit; public interface WebResourceRequest {android.net.Uri getUrl();String getMethod();boolean isForMainFrame();}`,
 'android/webkit/WebResourceResponse.java':`package android.webkit; public class WebResourceResponse {public WebResourceResponse(String a,String b,java.io.InputStream c){} public WebResourceResponse(String a,String b,int c,String d,java.util.Map<String,String> e,java.io.InputStream f){}}`,
 'android/content/Context.java':`package android.content; public class Context { public String getPackageName(){return pkg;} public String pkg="test.child"; public java.io.File getCacheDir(){return cache;} public java.io.File cache=new java.io.File(System.getProperty("java.io.tmpdir"),java.util.UUID.randomUUID().toString()); public static final String DOWNLOAD_SERVICE="download"; public Object getSystemService(String n){return new android.app.DownloadManager();} }`,
 'android/app/Activity.java':`package android.app; public class Activity extends android.content.Context { public static final int RESULT_OK=-1; public void onCreate(android.os.Bundle b){} protected void onActivityResult(int r,int c,android.content.Intent i){} protected void onResume(){} protected void onPause(){} protected void onDestroy(){} public void onBackPressed(){} public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();} public android.view.Window getWindow(){return new android.view.Window();} public void setContentView(android.view.View v){} public boolean failLaunch; public android.content.Intent picker, launched; public final android.content.ContentResolver resolver=new android.content.ContentResolver(); public void startActivityForResult(android.content.Intent i,int r){picker=i;} public void startActivity(android.content.Intent i){if(failLaunch)throw new IllegalStateException("No recipient");launched=i;} public android.content.ContentResolver getContentResolver(){return resolver;} public void runOnUiThread(Runnable r){r.run();} }`,
 'android/content/ContentResolver.java':`package android.content; public class ContentResolver { public java.io.ByteArrayOutputStream bytes; public boolean fail,corrupt; public java.io.InputStream openInputStream(android.net.Uri u){byte[] b=bytes.toByteArray();if(corrupt)b[0]^=1;return new java.io.ByteArrayInputStream(b);} public java.io.OutputStream openOutputStream(android.net.Uri u,String mode) throws java.io.IOException { if(fail)throw new java.io.IOException("failed"); if(!mode.equals("wt"))throw new AssertionError(); return bytes=new java.io.ByteArrayOutputStream(); } }`,
 'android/content/Intent.java':`package android.content; public class Intent { public static final String ACTION_CREATE_DOCUMENT="create",ACTION_SEND="send",CATEGORY_OPENABLE="open",EXTRA_TITLE="title",EXTRA_STREAM="stream"; public static final int FLAG_GRANT_READ_URI_PERMISSION=1; public String action,type; public int flags; public android.net.Uri uri; public ClipData clip; public java.util.Map<String,Object> extras=new java.util.HashMap<>(); public Intent(String a){action=a;} public Intent addCategory(String s){return this;} public Intent setType(String s){type=s;return this;} public Intent putExtra(String s,Object o){extras.put(s,o);return this;} public Intent addFlags(int f){flags|=f;return this;} public void setClipData(ClipData c){clip=c;} public android.net.Uri getData(){return uri;} public static Intent createChooser(Intent i,String t){return i;} }`,
 'android/content/ClipData.java':`package android.content; public class ClipData { public android.net.Uri uri; public static ClipData newRawUri(String s,android.net.Uri u){ClipData c=new ClipData();c.uri=u;return c;} }`,
 'android/net/Uri.java':`package android.net; public class Uri { java.net.URI value; public static class Builder {String scheme,path; public Builder scheme(String s){scheme=s;return this;} public Builder path(String p){path=p;return this;} public Uri build(){try{return Uri.parse(new java.net.URI(scheme,"",path,null,null).toString());}catch(Exception e){throw new RuntimeException(e);}}} public static Uri parse(String s){Uri u=new Uri();u.value=java.net.URI.create(s);return u;} public String getAuthority(){return value.getAuthority();} public String getQuery(){return value.getQuery();} public String getFragment(){return value.getFragment();} public java.util.List<String> getPathSegments(){java.util.List<String> out=new java.util.ArrayList<>();for(String part:value.getRawPath().split("/"))if(!part.isEmpty())out.add(decode(part));return out;} public String getLastPathSegment(){java.util.List<String> p=getPathSegments();return p.get(p.size()-1);} public static String encode(String s){try{return java.net.URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(Exception e){throw new RuntimeException(e);}} public String getEncodedPath(){return value.getRawPath();} public static String decode(String s){try{return java.net.URLDecoder.decode(s,"UTF-8");}catch(Exception e){throw new RuntimeException(e);}} public String getScheme(){return value.getScheme();} public String getHost(){return value.getHost();} public String getUserInfo(){return value.getUserInfo();} public int getPort(){return value.getPort();} public String toString(){return value.toString();} }`,
 'android/os/SystemClock.java':`package android.os; public class SystemClock { public static long now=1000; public static long elapsedRealtime(){return now;} }`,
 'android/os/Build.java':`package android.os; public class Build { public static class VERSION { public static int SDK_INT=36; } }`,
 'android/os/Environment.java':`package android.os; public class Environment { public static final String DIRECTORY_DOWNLOADS="Downloads"; }`,
 'android/view/MotionEvent.java':`package android.view; public class MotionEvent { public static final int ACTION_UP=1; public int getActionMasked(){return ACTION_UP;} }`,
 'android/view/View.java':`package android.view; public class View { public interface OnTouchListener { boolean onTouch(View v, MotionEvent e); } }`,
 'android/webkit/WebSettings.java':`package android.webkit; public class WebSettings { public static final int MIXED_CONTENT_NEVER_ALLOW=1,LOAD_DEFAULT=-1,LOAD_NO_CACHE=2; public void setBuiltInZoomControls(boolean b){} public void setDisplayZoomControls(boolean b){} public void setSupportZoom(boolean b){zoom=b;} public void setUseWideViewPort(boolean b){wide=b;} public void setLoadWithOverviewMode(boolean b){overview=b;} public void setMediaPlaybackRequiresUserGesture(boolean b){gesture=b;} public void setCacheMode(int m){cache=m;} public boolean js,zoom,wide,overview,gesture,dom,file,universal,fileCross; public int cache; public void setJavaScriptEnabled(boolean b){js=b;} public void setDomStorageEnabled(boolean b){dom=b;} public void setAllowFileAccess(boolean b){file=b;} public void setAllowContentAccess(boolean b){} public void setJavaScriptCanOpenWindowsAutomatically(boolean b){} public void setMixedContentMode(int i){} public void setAllowFileAccessFromFileURLs(boolean b){fileCross=b;} public void setAllowUniversalAccessFromFileURLs(boolean b){universal=b;} public String getUserAgentString(){return "agent";} }`,
 'android/webkit/WebView.java':`package android.webkit; public class WebView extends android.view.View { public WebView(){} public WebView(android.content.Context c){} public WebViewClient client; public Object bridge; public WebSettings settings=new WebSettings(); public void setBackgroundColor(int c){} public void setWebViewClient(WebViewClient c){client=c;} public void addJavascriptInterface(Object o,String n){bridge=o;} public void setDownloadListener(DownloadListener d){} public void loadUrl(String u){url=u;} public void loadDataWithBaseURL(String b,String d,String m,String e,String h){url="error";} public boolean canGoBack(){return false;} public void goBack(){} public void destroy(){} public android.view.View.OnTouchListener touch; public String url="https://trusted.test/index.html", script,target; public WebMessagePort[] pair; public void setOnTouchListener(android.view.View.OnTouchListener t){touch=t;} public String getUrl(){return url;} public WebSettings getSettings(){return settings;} public interface Callback {void run(String v);} public void evaluateJavascript(String s,Callback c){script=s;c.run("");} public WebMessagePort[] createWebMessageChannel(){return pair=new WebMessagePort[]{new WebMessagePort(),new WebMessagePort()};} public void postWebMessage(WebMessage m,android.net.Uri u){target=u.toString();} public boolean postDelayed(Runnable r,long n){return true;} }`,
 'android/webkit/WebMessage.java':`package android.webkit; public class WebMessage { String data; public WebMessage(String s){data=s;} public WebMessage(String s,WebMessagePort[] p){data=s;} public String getData(){return data;} }`,
 'android/webkit/WebMessagePort.java':`package android.webkit; public class WebMessagePort { public volatile WebMessage last; public WebMessageCallback callback; public boolean closed; public static class WebMessageCallback {public void onMessage(WebMessagePort p,WebMessage m){}} public void setWebMessageCallback(WebMessageCallback c){callback=c;} public void postMessage(WebMessage m){last=m;} public void close(){closed=true;} }`,
 'android/webkit/CookieManager.java':`package android.webkit; public class CookieManager {public static CookieManager getInstance(){return new CookieManager();} public String getCookie(String s){return "session=test";} }`,
 'android/webkit/URLUtil.java':`package android.webkit; public class URLUtil { public static String guessFileName(String u,String d,String m){return "report.pdf";} }`,
 'android/app/DownloadManager.java':`package android.app; public class DownloadManager { public static Request last; public long enqueue(Request r){last=r;return 1;} public static class Request { public static final int VISIBILITY_VISIBLE_NOTIFY_COMPLETED=1; public String destination; public java.util.Map<String,String> headers=new java.util.HashMap<>(); public Request(android.net.Uri u){} public void setTitle(String s){} public void setNotificationVisibility(int i){} public void setMimeType(String s){} public void addRequestHeader(String k,String v){headers.put(k,v);} public void setDestinationInExternalPublicDir(String d,String f){destination="public";} public void setDestinationInExternalFilesDir(android.content.Context c,String d,String f){destination="private";} } }`,
 'android/util/Base64.java':`package android.util; public class Base64 {public static final int NO_WRAP=2; public static byte[] decode(String s,int f){return java.util.Base64.getDecoder().decode(s);} public static String encodeToString(byte[] b,int f){return java.util.Base64.getEncoder().encodeToString(b);} }`,
 'android/widget/Toast.java':`package android.widget; public class Toast {public static final int LENGTH_SHORT=0,LENGTH_LONG=1; public static Toast makeText(android.content.Context c,String s,int i){return new Toast();} public void show(){} }`,
 'org/json/JSONObject.java':`package org.json; public class JSONObject { static java.util.Map<String,JSONObject> registry=new java.util.HashMap<>(); java.util.Map<String,Object> map=new java.util.HashMap<>(); public static String quote(String s){return String.valueOf((char)34)+s+(char)34;} public JSONObject(){} public JSONObject(String key){if(!registry.containsKey(key))throw new IllegalArgumentException();map=registry.get(key).map;} public JSONObject put(String k,Object v){map.put(k,v);return this;} public String getString(String k){return (String)map.get(k);} public boolean optBoolean(String k,boolean fallback){return map.containsKey(k)?Boolean.TRUE.equals(map.get(k)):fallback;} public String optString(String k,String fallback){return map.containsKey(k)?optString(k):fallback;} public String optString(String k){return map.get(k)==null?"":map.get(k).toString();} public int getInt(String k){return ((Number)map.get(k)).intValue();} public boolean yes(String k){return Boolean.TRUE.equals(map.get(k));} public String toString(){String k="object"+registry.size();registry.put(k,this);return k;} }`
};
extendV2AndroidStubs(stubs);
extendConnectivityStubs(stubs);
extendAwakeStubs(stubs);
extendWebPermissionStubs(stubs);
Object.assign(stubs,nfcStubs);
stubs["com/appforge/htmlstub/AppForgeQrScanner.java"]=scannerBoundaryStub;
stubs["com/appforge/htmlstub/AppForgeMediaService.java"]=mediaBoundaryStub;
stubs["com/appforge/htmlstub/AppForgeLocation.java"]=locationBoundaryStub;
stubs["com/appforge/htmlstub/AppForgeBilling.java"]=billingBoundaryStub;
const tinyHarness=`package com.appforge.htmlstub;
import android.webkit.*; import org.json.JSONObject;
public class TinyHarness {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args) throws Exception {
  for(String entry:new String[]{"index.html","nested/service records.html"}) {
   android.content.res.AssetManager.config=new JSONObject().put("mode","LOCAL").put("entry",entry).put("downloads",true).put("nativeBridge",true).put("shareBridge",true).toString().getBytes("UTF-8");
   TinyHtmlActivity app=new TinyHtmlActivity();app.onCreate(null);
   java.lang.reflect.Field f=TinyHtmlActivity.class.getDeclaredField("web");f.setAccessible(true);WebView web=(WebView)f.get(app);
   // Exactly the legacy file namespace and entry path: no HTTPS redirect or storage migration.
   check(web.url.equals(new android.net.Uri.Builder().scheme("file").path("/android_asset/site/"+entry).build().toString()));
   check(web.settings.dom && !web.settings.file && !web.settings.fileCross && !web.settings.universal);
   check(web.bridge instanceof TinyHtmlActivity.LocalDocuments);
   web.client.onPageStarted(web,web.url,null);web.client.onPageFinished(web,web.url);check(web.script.contains("AppForgeDocuments"));
   String before=web.script;((TinyHtmlActivity.LocalDocuments)web.bridge).send("subframe-forgery","{}");check(before.equals(web.script));
  }
  for(boolean enabled:new boolean[]{false,true}) {
   android.content.res.AssetManager.config=new JSONObject().put("mode","LOCAL").put("entry","index.html")
    .put("webJavaScriptEnabled",enabled).put("webDomStorageEnabled",enabled).put("webZoomEnabled",enabled)
    .put("webWideViewPortEnabled",enabled).put("webOverviewModeEnabled",enabled)
    .put("webMixedContentAllowed",enabled).put("webMediaAutoplayEnabled",enabled).put("offlineCache",enabled).toString().getBytes("UTF-8");
   TinyHtmlActivity app=new TinyHtmlActivity();app.onCreate(null);
   java.lang.reflect.Field f=TinyHtmlActivity.class.getDeclaredField("web");f.setAccessible(true);WebView web=(WebView)f.get(app);
   check(web.settings.js==enabled && web.settings.dom==enabled && web.settings.zoom==enabled);
   check(web.settings.wide==enabled && web.settings.overview==enabled && web.settings.gesture!=enabled);
   check(web.settings.cache==(enabled?WebSettings.LOAD_DEFAULT:WebSettings.LOAD_NO_CACHE));
   check(web.settings.mixed==(enabled?WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE:WebSettings.MIXED_CONTENT_NEVER_ALLOW));
   check(!web.settings.file && !web.settings.fileCross && !web.settings.universal);
  }
  java.lang.reflect.Method mime=AppForgeLocalAssets.class.getDeclaredMethod("mimeType",String.class);mime.setAccessible(true);
  String[][] media={{"song.mp3","audio/mpeg"},{"clip.mp4","video/mp4"},{"clip.webm","video/webm"},{"audio.ogg","audio/ogg"},{"audio.wav","audio/wav"},{"audio.m4a","audio/mp4"},{"report.pdf","application/pdf"},{"data.csv","text/csv"}};
  for(String[] fixture:media)check(mime.invoke(null,fixture[0]).equals(fixture[1]));
  // Execute the complete V2 Activity with host endpoints, including lifecycle and picker results.
  for(int sdk:new int[]{26,30,37}) {
   android.os.Build.VERSION.SDK_INT=sdk;
   android.content.res.AssetManager.config=new JSONObject().put("mode","LOCAL").put("entry","index.html")
    .put("fileUpload",true).put("fullscreen",true).put("orientation","landscape")
    .put("splashEnabled",true).put("splashText","Starting").put("backgroundColor","#123456")
    .put("statusBarColor","#234567").put("navigationBarColor","#345678").toString().getBytes("UTF-8");
   TinyHtmlActivity app=new TinyHtmlActivity();app.onCreate(null);app.onResume();
   java.lang.reflect.Field field=TinyHtmlActivity.class.getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(app);
   check(app.orientation==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
   check(app.window.status==0x234567 && app.window.navigation==0x345678);
   check(sdk<30?app.window.decor.visibility==7:app.window.controller.hidden==3);
   check(app.root.children.size()==2);android.widget.TextView splash=(android.widget.TextView)app.root.children.get(1);
   check(splash.text.equals("Starting")&&splash.background==0x123456);
   splash.delayed.run();check(app.root.children.size()==1);splash.delayed.run();check(app.root.children.size()==1);
   web.client.onPageFinished(web,web.url);web.touch.onTouch(web,new android.view.MotionEvent());
   WebChromeClient.FileChooserParams params=new WebChromeClient.FileChooserParams();params.mode=1;params.types=new String[]{"image/*,text/csv","file:///private"};
   java.util.List<android.net.Uri[]> calls=new java.util.ArrayList<>();
   check(web.chrome.onShowFileChooser(web,calls::add,params));check(calls.isEmpty());
   check(app.picker.action.equals(android.content.Intent.ACTION_OPEN_DOCUMENT));
   check(app.picker.flags==android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
   check(Boolean.TRUE.equals(app.picker.extras.get(android.content.Intent.EXTRA_ALLOW_MULTIPLE)));
   check(java.util.Arrays.equals((String[])app.picker.extras.get(android.content.Intent.EXTRA_MIME_TYPES),new String[]{"image/*","text/csv"}));
   android.content.Intent result=new android.content.Intent("result");result.clip=new android.content.ClipData();
   result.clip.items.add(android.net.Uri.parse("content://documents/a"));result.clip.items.add(android.net.Uri.parse("content://documents/b"));
   app.onActivityResult(7201,android.app.Activity.RESULT_OK,result);check(calls.size()==1&&calls.get(0).length==2);
   web.chrome.onShowFileChooser(web,calls::add,params);app.onActivityResult(7201,0,null);check(calls.get(1)==null);
   web.chrome.onShowFileChooser(web,calls::add,params);result.clip=null;result.uri=android.net.Uri.parse("file:///private/key");
   app.onActivityResult(7201,-1,result);check(calls.get(2)==null);
   web.chrome.onShowFileChooser(web,calls::add,params);result.uri=android.net.Uri.parse("content://test.child.appforge.documents/private");
   app.onActivityResult(7201,-1,result);check(calls.get(3)==null);
   web.chrome.onShowFileChooser(web,calls::add,params);result.clip=new android.content.ClipData();
   for(int i=0;i<33;i++)result.clip.items.add(android.net.Uri.parse("content://documents/"+i));
   app.onActivityResult(7201,-1,result);check(calls.get(4)==null);
   web.chrome.onShowFileChooser(web,calls::add,params);web.client.onPageStarted(web,web.url,null);check(calls.get(5)==null);
   web.client.onPageFinished(web,web.url);web.touch.onTouch(web,new android.view.MotionEvent());
   web.chrome.onShowFileChooser(web,calls::add,params);check(calls.get(6)==null); // Old picker still in flight.
   app.onActivityResult(7201,-1,result);check(calls.size()==7); // Stale result cannot reach another callback.
   app.onPause();check(web.paused&&web.script.contains("media.pause()"));web.chrome.onShowFileChooser(web,calls::add,params);check(calls.get(7)==null);
   app.onResume();check(!web.paused);web.chrome.onShowFileChooser(web,calls::add,params);check(calls.get(8)==null); // Fresh gesture required.
   web.touch.onTouch(web,new android.view.MotionEvent());web.chrome.onShowFileChooser(web,calls::add,params);check(calls.size()==9);
   android.os.Bundle state=new android.os.Bundle();app.onSaveInstanceState(state);check(state.getBoolean("appforge.pickerOutstanding",false));
   app.onDestroy();check(calls.get(9)==null);
  }
  // Remote upload is explicitly user-mediated and does not grant remote native bridge access.
  for(boolean enabled:new boolean[]{false,true}) {
   android.content.res.AssetManager.config=new JSONObject().put("mode","URL").put("url","https://trusted.test/app")
    .put("fileUpload",enabled).put("nativeBridgeAllowRemote",false).toString().getBytes("UTF-8");
   TinyHtmlActivity app=new TinyHtmlActivity();app.onCreate(null);app.onResume();
   java.lang.reflect.Field field=TinyHtmlActivity.class.getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(app);
   web.client.onPageFinished(web,web.url);web.touch.onTouch(web,new android.view.MotionEvent());
   WebChromeClient.FileChooserParams params=new WebChromeClient.FileChooserParams();params.mode=0;
   java.util.List<android.net.Uri[]> calls=new java.util.ArrayList<>();web.chrome.onShowFileChooser(web,calls::add,params);
   if(enabled) {
    check(calls.isEmpty()&&app.picker!=null);android.content.Intent result=new android.content.Intent("result");result.clip=new android.content.ClipData();
    result.clip.items.add(android.net.Uri.parse("content://docs/one"));result.clip.items.add(android.net.Uri.parse("content://docs/two"));
    app.onActivityResult(7201,-1,result);check(calls.get(0)==null); // Single-selection mode rejects multi-file results.
    web.url="https://foreign.test/app";web.touch.onTouch(web,new android.view.MotionEvent());web.chrome.onShowFileChooser(web,calls::add,params);check(calls.get(1)==null);
   } else check(calls.get(0)==null&&app.picker==null);
   app.onDestroy();
  }
  android.content.res.AssetManager.config=new JSONObject().put("mode","URL").put("url","https://trusted.test/app")
   .put("networkState",true).toString().getBytes("UTF-8");
  TinyHtmlActivity connected=new TinyHtmlActivity();connected.onCreate(null);connected.onResume();
  check(android.widget.Toast.last.contains("İnternet yok"));check(connected.networkManager.registrations==1);
  java.lang.reflect.Field wf=TinyHtmlActivity.class.getDeclaredField("web");wf.setAccessible(true);WebView cw=(WebView)wf.get(connected);
  connected.networkManager.network=new android.net.Network();connected.networkManager.caps=new android.net.NetworkCapabilities();
  connected.networkManager.caps.internet=true;connected.networkManager.caps.validated=true;
  connected.networkManager.callback.onAvailable(connected.networkManager.network);check(cw.reloads==1);
  connected.networkManager.callback.onCapabilitiesChanged(connected.networkManager.network,connected.networkManager.caps);check(cw.reloads==1);
  android.net.ConnectivityManager.NetworkCallback old=connected.networkManager.callback;connected.onPause();
  check(connected.networkManager.removals==1);old.onLost(null);check(cw.reloads==1);connected.onDestroy();
  System.out.println("legacy Tiny origin harness PASS");
 }
}`;
const harness=`package PACKAGE;
import android.app.*; import android.webkit.*; import android.content.*; import android.net.Uri; import org.json.JSONObject;
public class Harness {
 static Activity a; static WebView w; static AppForgeDocuments d; static WebMessagePort port; static int seq;
 static void check(boolean b){if(!b)throw new AssertionError();}
 static JSONObject call(JSONObject m){m.put("id",String.valueOf(++seq));port.callback.onMessage(port,new WebMessage(m.toString()));return new JSONObject(port.last.getData());}
 static JSONObject op(String s){return new JSONObject().put("op",s);}
 static void init(boolean share){a=new Activity();w=new WebView();d=new AppForgeDocuments(a,w,"https://trusted.test",true,share);d.pageFinished(w.url);port=w.pair[0];check(w.target.equals("https://trusted.test"));w.touch.onTouch(w,new android.view.MotionEvent());}
 static String token() throws Exception {java.lang.reflect.Field f=AppForgeDocuments.class.getDeclaredField("token");f.setAccessible(true);return (String)f.get(d);}
 static JSONObject localCall(JSONObject message) throws Exception {
  d.receiveLocal(token(),message.put("id",String.valueOf(++seq)).toString());
  String script=w.script;return new JSONObject(script.substring(script.indexOf('"')+1,script.lastIndexOf('"')));
 }
 static void localInit(boolean downloads,boolean sharing){
  a=new Activity();w=new WebView();w.url="file:///android_asset/site/index.html";
  d=new AppForgeDocuments(a,w,"legacy-file",downloads,sharing);d.pageStarted();d.pageFinished(w.url);w.touch.onTouch(w,new android.view.MotionEvent());
 }
 static JSONObject begin(String mode,int size){return call(op("begin").put("mode",mode).put("mime","application/pdf").put("name","../../report.pdf").put("size",size));}

 static void ready(String mode){check(begin(mode,7).yes("ok"));check(call(op("chunk").put("offset",0).put("data","JVBERi0A/w==")).yes("ok"));JSONObject m=op("finish").put("id",String.valueOf(++seq));port.callback.onMessage(port,new WebMessage(m.toString()));if(mode.equals("save")){check(a.picker.action.equals(Intent.ACTION_CREATE_DOCUMENT));check(a.picker.type.equals("application/pdf"));}else{for(int i=0;i<200 && a.launched==null;i++)try{Thread.sleep(5);}catch(Exception e){} check(a.picker==null);check(a.launched.action.equals(Intent.ACTION_SEND));}}
 static Intent destination(String uri){Intent i=new Intent("result");i.uri=Uri.parse(uri);return i;}
 static void result(int code,Intent intent){String before=port.last.getData();d.activityResult(AppForgeDocuments.REQUEST,code,intent);for(int i=0;i<200 && port.last.getData().equals(before);i++){try{Thread.sleep(5);}catch(Exception e){}}}

 public static void main(String[] args) throws Exception {
  java.lang.reflect.Method inject=AppForgeLocalAssets.class.getDeclaredMethod("withBootstrap",java.io.InputStream.class,String.class);inject.setAccessible(true);
  byte[] html="<!DOCTYPE html><html><head><script>window.startup=navigator.canShare;</script></head><body>original</body></html>".getBytes("UTF-8");
  java.io.InputStream injected=(java.io.InputStream)inject.invoke(null,new java.io.ByteArrayInputStream(html),"window.before=true;");
  java.io.ByteArrayOutputStream rendered=new java.io.ByteArrayOutputStream();int next;while((next=injected.read())!=-1)rendered.write(next);injected.close();
  String response=new String(rendered.toByteArray(),"UTF-8");check(response.startsWith("<!DOCTYPE html><script>window.before=true;</script>"));check(response.replace("<script>window.before=true;</script>", "").equals(new String(html,"UTF-8")));
  init(true);check(!d.matchesCompatToken("untrusted-subframe"));
  java.lang.reflect.Field secret=AppForgeDocuments.class.getDeclaredField("token");secret.setAccessible(true);String token=(String)secret.get(d);
  check(d.allowsCompat(token,true));d.pageStarted();check(!d.allowsCompat(token,false));

  check(AppForgeDocuments.httpsOrigin("https://user@trusted.test")==null);
  check(AppForgeDocuments.httpsOrigin("http://trusted.test")==null);
  check(AppForgeDocuments.httpsOrigin("https://TRUSTED.test:443/path").equals("https://trusted.test"));
  check(!AppForgeDocuments.safeName("../../bad\\\\path.pdf").contains("/"));
  for(String bad:new String[]{"A===","AA=A","!!!!","Zg==\\n","Zh==","",new String(new char[32772]).replace('\\0','A')}){try{AppForgeDocuments.decodeChunk(bad);throw new AssertionError(bad);}catch(IllegalArgumentException expected){}}
  check(AppForgeDocuments.decodeChunk(java.util.Base64.getEncoder().encodeToString(new byte[AppForgeDocuments.CHUNK])).length==AppForgeDocuments.CHUNK);
  init(true);ready("save");result(Activity.RESULT_OK,destination("content://documents/1"));check(java.util.Arrays.equals(a.resolver.bytes.toByteArray(),new byte[]{37,80,68,70,45,0,-1}));check(new JSONObject(port.last.getData()).getString("result").equals("saved"));check(a.launched==null);
  init(true);ready("share");check(a.resolver.bytes==null);check(a.launched.type.equals("application/pdf"));check(a.launched.flags==Intent.FLAG_GRANT_READ_URI_PERMISSION);
  Uri shared=(Uri)a.launched.extras.get(Intent.EXTRA_STREAM);check(shared.toString().startsWith("content://test.child.appforge.documents/"));check(a.launched.clip.uri==shared);
  check(new JSONObject(port.last.getData()).getString("result").equals("chooser-launched"));
  AppForgeShareProvider provider=new AppForgeShareProvider();provider.context=a;
  check(provider.getType(shared).equals("application/pdf"));
  java.io.File file=provider.openFile(shared,"r").file;
  check(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(file.toPath()),new byte[]{37,80,68,70,45,0,-1}));
  check(((android.database.MatrixCursor)provider.query(shared,null,null,null,null)).row[0].toString().endsWith(".pdf"));
  for(String badMode:new String[]{"w","rw","rwt","wa"})try{provider.openFile(shared,badMode);throw new AssertionError();}catch(java.io.FileNotFoundException expected){}
  for(String bad:new String[]{shared.toString().replace("test.child","other.child"),shared.toString()+"?path=/secret",shared.toString().replace(file.getName(),"../secret"),"content://test.child.appforge.documents/00000000000000000000000000000000.pdf/report.pdf"})try{provider.openFile(Uri.parse(bad),"r");throw new AssertionError();}catch(java.io.FileNotFoundException expected){}
  // A separately packaged provider never resolves another child's granted URI.
  AppForgeShareProvider other=new AppForgeShareProvider();other.context=new Activity();other.context.pkg="other.child";
  try{other.openFile(shared,"r");throw new AssertionError();}catch(java.io.FileNotFoundException expected){}
  // Navigation/background must not revoke bytes before a delayed recipient opens them.
  d.pause();d.close();check(provider.openFile(shared,"r").file.exists());
  file.setLastModified(System.currentTimeMillis()-AppForgeShareProvider.TTL-1);
  try{provider.openFile(shared,"r");throw new AssertionError();}catch(java.io.FileNotFoundException expected){}
  check(!file.exists());
  byte[] csv="name,value\\r\\n\\\"a,b\\\",\\\"c\\\"\\\"d\\\"\\r\\n".getBytes("UTF-8");
  check(AppForgeDocuments.isCsv(csv));check(!AppForgeDocuments.isCsv(new byte[]{(byte)0xc3,0x28}));check(!AppForgeDocuments.isCsv(new byte[]{0}));check(!AppForgeDocuments.isCsv("bad\\"quote".getBytes("UTF-8")));
  Uri csvUri=AppForgeShareProvider.create(a,csv,"../report.csv","text/csv");check(provider.getType(csvUri).equals("text/csv"));check(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(provider.openFile(csvUri,"r").file.toPath()),csv));
  for(String csvMode:new String[]{"save","share"}) {
   init(true);check(call(op("begin").put("mode",csvMode).put("mime","application/csv").put("size",csv.length).put("name","records.csv")).yes("ok"));
   check(call(op("chunk").put("offset",0).put("data",java.util.Base64.getEncoder().encodeToString(csv))).yes("ok"));call(op("finish"));
   if(csvMode.equals("save")){check(a.picker.type.equals("text/csv"));result(Activity.RESULT_OK,destination("content://documents/csv"));check(java.util.Arrays.equals(a.resolver.bytes.toByteArray(),csv));}
   else {for(int i=0;i<200 && a.launched==null;i++)Thread.sleep(5);check(a.picker==null);check(a.resolver.bytes==null);check(a.launched.type.equals("text/csv"));}
  }
  // Hold the real provider creation lock so background/navigation happen before completion.
  init(true);check(begin("share",7).yes("ok"));check(call(op("chunk").put("offset",0).put("data","JVBERi0A/w==")).yes("ok"));
  synchronized(AppForgeShareProvider.class){call(op("finish"));d.pause();}
  java.lang.reflect.Field pending=AppForgeDocuments.class.getDeclaredField("pendingCompletion");pending.setAccessible(true);
  for(int i=0;i<200 && pending.get(d)==null;i++)Thread.sleep(5);
  check(a.launched==null);check(pending.get(d)!=null);d.resume();check(a.launched!=null);check(a.picker==null);
  init(true);check(begin("share",7).yes("ok"));check(call(op("chunk").put("offset",0).put("data","JVBERi0A/w==")).yes("ok"));
  synchronized(AppForgeShareProvider.class){call(op("finish"));d.close();}
  Thread.sleep(50);check(a.launched==null);check(a.picker==null);
  init(true);a.failLaunch=true;check(begin("share",7).yes("ok"));check(call(op("chunk").put("offset",0).put("data","JVBERi0A/w==")).yes("ok"));
  String old=port.last.getData();call(op("finish"));for(int i=0;i<200 && port.last.getData().equals(old);i++)Thread.sleep(5);
  check(!new JSONObject(port.last.getData()).yes("ok"));check(a.picker==null);check(a.resolver.bytes==null);
  init(true);ready("save");d.activityResult(AppForgeDocuments.REQUEST,0,null);check(new JSONObject(port.last.getData()).yes("cancelled"));check(a.resolver.bytes==null);
  init(true);ready("save");a.resolver.fail=true;result(Activity.RESULT_OK,destination("content://documents/fail"));check(!new JSONObject(port.last.getData()).yes("ok"));check(a.launched==null);
  init(true);ready("save");a.resolver.corrupt=true;result(Activity.RESULT_OK,destination("content://documents/corrupt"));check(!new JSONObject(port.last.getData()).yes("ok"));check(a.launched==null);
  init(true);ready("save");result(Activity.RESULT_OK,destination("file:///unsafe"));check(a.resolver.bytes==null);check(!new JSONObject(port.last.getData()).yes("ok"));
  init(false);check(!begin("share",7).yes("ok"));
  init(true);d.pause();check(!begin("save",7).yes("ok"));
  init(true);check(!begin("save",AppForgeDocuments.LIMIT+1).yes("ok"));
  init(true);check(begin("save",7).yes("ok"));check(!call(op("chunk").put("offset",1).put("data","JVBERi0A/w==")).yes("ok"));
  init(true);check(begin("save",7).yes("ok"));check(!call(op("chunk").put("offset",0).put("data","AAAA")).yes("ok") || !call(op("finish")).yes("ok"));
  init(true);check(begin("save",7).yes("ok"));d.pageStarted();check(port.closed);
  init(true);byte[] large=new byte[AppForgeDocuments.CHUNK+137];System.arraycopy(new byte[]{37,80,68,70,45},0,large,0,5);for(int i=5;i<large.length;i++)large[i]=(byte)(i%256);
  check(begin("save",large.length).yes("ok"));
  for(int at=0;at<large.length;at+=AppForgeDocuments.CHUNK){byte[] part=java.util.Arrays.copyOfRange(large,at,Math.min(large.length,at+AppForgeDocuments.CHUNK));check(call(op("chunk").put("offset",at).put("data",java.util.Base64.getEncoder().encodeToString(part))).yes("ok"));}
  JSONObject finish=op("finish").put("id",String.valueOf(++seq));port.callback.onMessage(port,new WebMessage(finish.toString()));result(Activity.RESULT_OK,destination("content://documents/large"));check(java.util.Arrays.equals(large,a.resolver.bytes.toByteArray()));
  init(true);d.enqueueHttp("https://trusted.test/report.pdf","agent",null,"application/pdf");check(DownloadManager.last.headers.get("Cookie").equals("session=test"));check(DownloadManager.last.destination.equals("public"));android.os.Build.VERSION.SDK_INT=28;d.enqueueHttp("https://trusted.test/report.pdf","agent",null,"application/pdf");check(DownloadManager.last.destination.equals("private"));
  init(true);w.url="https://attacker.test";try{d.enqueueHttp("https://trusted.test/report.pdf","agent",null,"application/pdf");throw new AssertionError();}catch(SecurityException expected){}
  // Replay, stale origin, user gesture and disabled settings are executed at native boundary.
  init(true);w.touch=null;d.pause();d.resume();check(!begin("save",7).yes("ok"));
  init(true);JSONObject duplicate=op("begin").put("id",String.valueOf(++seq)).put("mode","save").put("size",7).put("mime","application/pdf");
  port.callback.onMessage(port,new WebMessage(duplicate.toString()));check(new JSONObject(port.last.getData()).yes("ok"));
  port.callback.onMessage(port,new WebMessage(duplicate.toString()));check(!new JSONObject(port.last.getData()).yes("ok"));
  init(true);check(begin("save",7).yes("ok"));check(!begin("save",7).yes("ok"));
  // A competing begin/replay must leave the original transfer able to save exact bytes.
  check(call(op("chunk").put("offset",0).put("data","JVBERi0A/w==")).yes("ok"));
  call(op("finish"));result(Activity.RESULT_OK,destination("content://documents/concurrent"));
  check(java.util.Arrays.equals(a.resolver.bytes.toByteArray(),new byte[]{37,80,68,70,45,0,(byte)255}));

  init(true);w.url="https://attacker.test";check(!begin("save",7).yes("ok"));
  init(true);check(!call(op("begin").put("mode","save").put("size",7).put("mime","text/plain")).yes("ok"));
  a=new Activity();w=new WebView();d=new AppForgeDocuments(a,w,"https://trusted.test",false,false);d.pageFinished(w.url);port=w.pair[0];w.touch.onTouch(w,new android.view.MotionEvent());check(!begin("save",7).yes("ok"));
  a=new Activity();w=new WebView();d=new AppForgeDocuments(a,w,null,true,true);d.pageFinished(w.url);check(w.pair==null);
  for(String bad:new String[]{"file:///data/private/report.html","file:///android_asset/site/../private.html","file:///android_asset/site/%2e%2e/private.html","file://attacker/android_asset/site/index.html","https://attacker.test/index.html","content://private/index.html"})check(!AppForgeDocuments.legacyLocalPage(bad));
  localInit(true,true);String before=w.script;d.receiveLocal("iframe-guessed-token",op("begin").toString());check(w.script.equals(before));
  check(localCall(op("begin").put("mode","save").put("mime","application/pdf").put("size",7)).yes("ok"));
  check(localCall(op("chunk").put("offset",0).put("data","JVBERi0A/w==")).yes("ok"));
  localCall(op("finish"));check(a.picker.type.equals("application/pdf"));
  d.activityResult(AppForgeDocuments.REQUEST,Activity.RESULT_OK,destination("content://documents/local"));
  for(int i=0;i<200 && (a.resolver.bytes==null || a.resolver.bytes.size()!=7);i++)Thread.sleep(5);
  check(java.util.Arrays.equals(a.resolver.bytes.toByteArray(),new byte[]{37,80,68,70,45,0,-1}));
  localInit(true,true);check(d.allowsCompat(token(),true));
  check(localCall(op("begin").put("mode","share").put("mime","application/pdf").put("size",7)).yes("ok"));
  check(localCall(op("chunk").put("offset",0).put("data","JVBERi0A/w==")).yes("ok"));localCall(op("finish"));
  d.activityResult(AppForgeDocuments.REQUEST,Activity.RESULT_OK,destination("content://documents/local-share"));
  for(int i=0;i<200 && a.launched==null;i++)Thread.sleep(5);
  check(a.launched.action.equals(Intent.ACTION_SEND));check(a.launched.type.equals("application/pdf"));check(a.picker==null);check(a.resolver.bytes==null);check(a.launched.extras.get(Intent.EXTRA_STREAM).toString().startsWith("content://test.child.appforge.documents/"));
  d.pause();check(!d.allowsCompat(token(),true));
  localInit(false,false);check(!localCall(op("begin").put("mode","save").put("mime","application/pdf").put("size",7)).yes("ok"));
  localInit(true,false);check(!localCall(op("begin").put("mode","share").put("mime","application/pdf").put("size",7)).yes("ok"));
  localInit(true,true);String oldToken=token();d.pageStarted();d.pageFinished(w.url);before=w.script;
  d.receiveLocal(oldToken,op("begin").toString());check(w.script.equals(before));
  w.url="https://attacker.test";d.receiveLocal(token(),op("begin").toString());check(w.script.equals(before));
  // V2 native actions share the same main-frame capability, origin, gesture and lifecycle boundary.
  localInit(true,true);final int[] invoked={0};d.setLegacy(m->{invoked[0]++;return "OK";},true);
  check(localCall(op("legacy").put("method","copy")).yes("ok"));check(invoked[0]==1);
  before=w.script;d.receiveLocal("forged",op("legacy").put("id","9999").toString());check(w.script.equals(before)&&invoked[0]==1);
  d.pause();d.receiveLocal(token(),op("legacy").put("id","9998").toString());check(invoked[0]==1);
  d.resume();check(!localCall(op("legacy").put("method","copy")).yes("ok"));check(invoked[0]==1);
  w.touch.onTouch(w,new android.view.MotionEvent());android.os.SystemClock.now+=30001;
  check(!localCall(op("legacy").put("method","copy")).yes("ok"));check(invoked[0]==1);
  w.touch.onTouch(w,new android.view.MotionEvent());w.url="https://attacker.test";
  d.receiveLocal(token(),op("legacy").put("id","9997").toString());check(invoked[0]==1);
  System.out.println("native document harness PASS");
 }
}`;
for(const dir of variants)test(`${dir}: execute native bytes, SAF, intents, failure and security regressions`,()=>{
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'document-runtime-'));
 try{
  const files=[];
  for(const [name,value] of Object.entries(stubs)){const file=path.join(temp,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,value);files.push(file);}
  const pkg=source(dir).match(/package ([^;]+);/)[1];
  const file=path.join(temp,'Harness.java');fs.writeFileSync(file,harness.replaceAll('PACKAGE',pkg));files.push(file,path.join(root,dir,'AppForgeDocuments.java'),path.join(root,dir,'AppForgeLocalAssets.java'),path.join(root,dir,'AppForgeShareProvider.java'));
  if(pkg==='com.appforge.htmlstub'){
   const tinyFile=path.join(temp,'TinyHarness.java');fs.writeFileSync(tinyFile,tinyHarness);files.push(tinyFile,path.join(root,dir,'TinyHtmlActivity.java'),path.join(root,dir,'AppForgeSelection.java'),path.join(root,dir,'AppForgeConnectivity.java'),path.join(root,dir,'AppForgeAwake.java'),path.join(root,dir,'AppForgeWebPermissions.java'),path.join(root,dir,'AppForgeNfc.java'),path.join(root,dir,'AppForgeDeepLink.java'));
  }
  execFileSync('javac',['--release','8','-Xlint:-options','-d',temp,...files],{stdio:'pipe'});
  assert.match(execFileSync('java',['-cp',temp,pkg+'.Harness'],{encoding:'utf8'}),/harness PASS/);
  if(pkg==='com.appforge.htmlstub')assert.match(execFileSync('java',['-cp',temp,pkg+'.TinyHarness'],{encoding:'utf8'}),/origin harness PASS/);
 }finally{fs.rmSync(temp,{recursive:true,force:true});}
});

for(const dir of variants)test(`${dir}: local location shim returns only correlated actual fixes/errors and keeps disabled origins unchanged`,async()=>{
 const off=browser(dir,{legacy:true,local:true});assert.equal(off.context.AppForgeLocation,undefined);
 const b=browser(dir,{legacy:true,local:true,localLocation:true});let position,error,calls=0;
 assert.throws(()=>b.context.AppForgeLocation.getCurrentPosition(null),/callback required/);
 assert.equal(b.context.navigator.geolocation.getCurrentPosition(v=>{position=v;calls++;},e=>{error=e;}),undefined);
 await new Promise(r=>setImmediate(r));
 const id=b.compatMessages.at(-1).args[0];assert.equal(b.compatMessages.at(-1).method,'getLocation');
 b.events['appforge-location']({detail:{id:'forged-unmatched',ok:true,position:{coords:{latitude:0}}}});assert.equal(position,undefined);
 const actual={coords:{latitude:41.01,longitude:28.97,accuracy:15},timestamp:1700000000000};
 b.events['appforge-location']({detail:{id,ok:true,position:actual}});assert.equal(position,actual);assert.equal(error,undefined);
 b.events['appforge-location']({detail:{id,ok:true,position:actual}});assert.equal(calls,1);
 b.context.AppForgeLocation.getCurrentPosition(()=>assert.fail('Denied request produced coordinates'),e=>{error=e;});
 await new Promise(r=>setImmediate(r));const deniedId=b.compatMessages.at(-1).args[0];
 b.events['appforge-location']({detail:{id:deniedId,ok:false,error:{code:1,message:'Denied'}}});assert.equal(error.code,1);
});
