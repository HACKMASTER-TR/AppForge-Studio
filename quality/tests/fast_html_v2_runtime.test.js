import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {scannerStubs} from './helpers/fast_html_scanner_stubs.js';
import {webPermissionStubs,nfcStubs} from './helpers/fast_html_v2_android_stubs.js';
import {execFileSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'../..');
const shell=path.join(root,'android-app/html-apk-template/src/main/java/com/appforge/htmlstub');
const source=fs.readFileSync(path.join(shell,'TinyHtmlActivity.java'),'utf8');
function method(signature){const start=source.indexOf(signature);assert.ok(start>=0);let end=source.indexOf('{',start), depth=1;while(depth){end++;if(source[end]==='{')depth++;if(source[end]==='}')depth--;}return source.slice(start,end+1);}
function java(name,code,extra=[],stubs={}){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'fast-html-v2-'));try{const files=Object.entries(stubs).map(([p,s])=>{const f=path.join(dir,p);fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,s);return f;});fs.writeFileSync(path.join(dir,name+'.java'),code);execFileSync('javac',['-d',dir,path.join(dir,name+'.java'),...extra,...files],{stdio:'pipe',timeout:60000});assert.match(execFileSync('java',['-cp',dir,'com.appforge.htmlstub.'+name],{encoding:'utf8',timeout:30000}),/PASS/);}finally{fs.rmSync(dir,{recursive:true,force:true});}}

test('V2 production picker state drains stale results, bounds selection and rejects unsafe URI/MIME input',()=>{
 java('SelectionCheck',`package com.appforge.htmlstub;
import java.util.*;
public class SelectionCheck {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  AppForgeSelection<String> s=new AppForgeSelection<>();List<List<String>> calls=new ArrayList<>();
  check(s.begin(calls::add));check(!s.begin(calls::add));s.cancel();check(calls.size()==1&&calls.get(0)==null);
  check(!s.begin(calls::add));s.finish(List.of("stale"));check(calls.size()==1);
  check(s.begin(calls::add));s.finish(List.of("a","a","b"));check(calls.get(1).equals(List.of("a","b")));
  check(s.begin(calls::add));s.finish(null);check(calls.get(2)==null);
  check(s.begin(calls::add));s.finish(Collections.nCopies(33,"a"));check(calls.get(3)==null);
  check(s.begin(calls::add));s.finish(Arrays.asList("a",null));check(calls.get(4)==null);
  check(s.begin(calls::add));s.finish(Collections.emptyList());check(calls.get(5)==null);
  s.restoreOutstanding(true);check(!s.begin(calls::add));s.finish(null);check(s.begin(calls::add));s.cancel();s.cancel();check(calls.size()==7);
  for(String u:new String[]{"file:///private/key","https://example/a","content://user@docs/a","content://docs/a#fragment","content:/a","content://docs","content://docs/invalid space","content://docs:42/a","content://docs/"+"a".repeat(8192)})check(!AppForgeSelection.safeContentUri(u));
  check(AppForgeSelection.safeContentUri("content://com.android.providers.downloads.documents/document/42"));
  check(AppForgeSelection.safeContentUri("content://docs/document/a%20b"));
  check(Arrays.equals(AppForgeSelection.mimeTypes(new String[]{"image/*, text/csv","image/*",".pdf",null,"text/html\\r\\nX: bad"}),new String[]{"image/*","text/csv"}));
  check(AppForgeSelection.mimeTypes(null).length==0);
  System.out.println("selection PASS");
 }
}`, [path.join(shell,'AppForgeSelection.java')]);
});

test('V2 executes shipped native actions: opt-in, bounds, permission denial, channel blocking and actual notification dispatch',()=>{
 const body=method('private String nativeAction(').replaceAll('android.content.pm.','').replaceAll('android.content.','').replaceAll('android.os.','').replaceAll('android.app.','').replaceAll('android.Manifest.','Manifest.').replaceAll('android.R.','R.').replaceAll('org.json.','');
 java('NativeCheck',`package com.appforge.htmlstub;
import java.util.*;
public class NativeCheck {
 static class JSONObject {Map<String,Object> values=new HashMap<>();JSONObject put(String k,Object v){values.put(k,v);return this;}boolean optBoolean(String k,boolean fallback){return values.containsKey(k)?(boolean)values.get(k):fallback;}String optString(String k){return values.containsKey(k)?String.valueOf(values.get(k)):"";}String getString(String k){return (String)values.get(k);}JSONArray optJSONArray(String k){return (JSONArray)values.get(k);}}
 static class JSONArray {Object[] a;JSONArray(Object... a){this.a=a;}String optString(int i,String d){return i<a.length?String.valueOf(a[i]):d;}int optInt(int i,int d){return i<a.length?(int)a[i]:d;}}
 static class ClipData {String text;static ClipData newPlainText(String label,String text){ClipData c=new ClipData();c.text=text;return c;}}
 static class ClipboardManager {String text;void setPrimaryClip(ClipData c){text=c.text;}}
 static class Vibrator {boolean available=true;int ms;boolean hasVibrator(){return available;}void vibrate(int effect){ms=effect;}}
 static class VibrationEffect {static final int DEFAULT_AMPLITUDE=-1;static int createOneShot(int ms,int amplitude){return ms;}}
 static class Build {static class VERSION {static int SDK_INT=37;}}
 static class Manifest {static class permission {static String POST_NOTIFICATIONS="post",CAMERA="camera";}}
 static class PackageManager {static int PERMISSION_GRANTED=0;}
 static class R {static class drawable {static int ic_dialog_info=1;}}
 static class NotificationChannel {String id;int importance;NotificationChannel(String id,String title,int i){this.id=id;importance=i;}int getImportance(){return importance;}}
 static class Notification {String title,text;static class BigTextStyle {BigTextStyle bigText(String s){return this;}}static class Builder {Notification n=new Notification();Builder(Object ctx,String channel){}Builder setSmallIcon(int i){return this;}Builder setContentTitle(String s){n.title=s;return this;}Builder setContentText(String s){n.text=s;return this;}Builder setStyle(BigTextStyle s){return this;}Builder setAutoCancel(boolean v){return this;}Notification build(){return n;}}}
 static class NotificationManager {static final int IMPORTANCE_DEFAULT=3,IMPORTANCE_NONE=0;boolean enabled=true,blocked;int sent;Notification notification;NotificationChannel channel;boolean areNotificationsEnabled(){return enabled;}void createNotificationChannel(NotificationChannel c){if(channel==null)channel=c;}NotificationChannel getNotificationChannel(String s){if(blocked)channel.importance=0;return channel;}void notify(int id,Notification n){sent++;notification=n;}}
 static String CLIPBOARD_SERVICE="clipboard",VIBRATOR_SERVICE="vibrator",NOTIFICATION_SERVICE="notification";
 static class Connectivity {boolean enabled;boolean online(){return enabled;}}Connectivity connectivity=new Connectivity();
 static class BillingHelper {int calls,offline;String product,offer;void network(boolean v){if(!v)offline++;}String query(){calls++;return "REQUESTED";}String refresh(){calls++;return "REQUESTED";}String purchase(String id,String token){calls++;product=id;offer=token;return "FLOW_OPENED";}}BillingHelper billing=new BillingHelper();String billingSnapshot="OFFLINE";
 static class LocationHelper {String request(String id){return "PENDING";}}LocationHelper location=new LocationHelper();
 static class AppForgeMediaService {static int calls;static String send(Object a,String command,String path,String title){calls++;return command.equals("play")?"STARTING":command.equals("state")?"state-json":"REQUESTED";}}
 static class Scanner {String start(){return "SCANNING";}}Scanner scanner=new Scanner();
 static class Nfc {String start(){return "LISTENING";}}Nfc nfc=new Nfc();
 JSONObject config=new JSONObject();ClipboardManager clipboard=new ClipboardManager();Vibrator vibrator=new Vibrator();NotificationManager manager=new NotificationManager();boolean notificationPermissionRequested,scannerPermissionRequested;int permission=-1,requested;
 Object getSystemService(String name){return name.equals(CLIPBOARD_SERVICE)?clipboard:name.equals(VIBRATOR_SERVICE)?vibrator:manager;}
 int checkSelfPermission(String name){return permission;}void requestPermissions(String[] names,int code){requested++;}
 ${body}
 static void check(boolean value){if(!value)throw new AssertionError();}
 interface Action{void run()throws Exception;}
 static void denied(Action action)throws Exception{try{action.run();throw new AssertionError("Expected denial");}catch(SecurityException|IllegalArgumentException|IllegalStateException expected){}}
 JSONObject message(String op,Object... args){return new JSONObject().put("method",op).put("args",new JSONArray(args));}
 public static void main(String[] args)throws Exception{
  NativeCheck n=new NativeCheck();denied(()->n.nativeAction(n.message("copy","a")));n.config.put("nativeBridge",true);
  denied(()->n.nativeAction(n.message("copy","a")));n.config.put("clipboardBridge",true);
  check(n.nativeAction(n.message("copy","hello")).equals("OK"));check(n.clipboard.text.equals("hello"));
  denied(()->n.nativeAction(n.message("copy","x".repeat(65537))));check(n.clipboard.text.equals("hello"));
  n.clipboard=null;denied(()->n.nativeAction(n.message("copy","a")));
  denied(()->n.nativeAction(n.message("vibrate",100)));n.config.put("vibrationBridge",true);
  denied(()->n.nativeAction(n.message("vibrate",0)));denied(()->n.nativeAction(n.message("vibrate",1001)));
  check(n.nativeAction(n.message("vibrate",200)).equals("OK")&&n.vibrator.ms==200);n.vibrator.available=false;denied(()->n.nativeAction(n.message("vibrate",100)));
  denied(()->n.nativeAction(n.message("notify","Title","Body")));n.config.put("notifications",true);
  denied(()->n.nativeAction(n.message("notify","","Body")));denied(()->n.nativeAction(n.message("notify","Title","x".repeat(2001))));
  check(n.nativeAction(n.message("notify","Title","Body")).equals("PERMISSION_REQUESTED"));check(n.requested==1&&n.manager.sent==0);
  denied(()->n.nativeAction(n.message("notify","Title","Body")));check(n.requested==1&&n.manager.sent==0);
  n.permission=0;check(n.nativeAction(n.message("notify","Title","Body")).equals("SENT"));check(n.manager.sent==1&&n.manager.notification.text.equals("Body"));
  n.manager.enabled=false;denied(()->n.nativeAction(n.message("notify","Title","Body")));n.manager.enabled=true;n.manager.blocked=true;denied(()->n.nativeAction(n.message("notify","Title","Body")));check(n.manager.sent==1);
  n.manager=new NotificationManager();n.permission=-1;Build.VERSION.SDK_INT=26;check(n.nativeAction(n.message("notify","Offline","Local")).equals("SENT"));check(n.manager.sent==1);
  denied(()->n.nativeAction(n.message("networkState")));n.config.put("networkState",true);
  check(n.nativeAction(n.message("networkState")).equals("OFFLINE"));n.connectivity.enabled=true;
  check(n.nativeAction(n.message("networkState")).equals("ONLINE"));
  denied(()->n.nativeAction(n.message("scanNfc")));n.config.put("nfc",true);check(n.nativeAction(n.message("scanNfc")).equals("LISTENING"));n.nfc=null;denied(()->n.nativeAction(n.message("scanNfc")));
  denied(()->n.nativeAction(n.message("scanCode")));n.config.put("qrScanner",true);
  check(n.nativeAction(n.message("scanCode")).equals("PERMISSION_REQUESTED"));denied(()->n.nativeAction(n.message("scanCode")));
  n.permission=0;check(n.nativeAction(n.message("scanCode")).equals("SCANNING"));n.scanner=null;denied(()->n.nativeAction(n.message("scanCode")));
  denied(()->n.nativeAction(n.message("readClipboard")));
  NativeCheck m=new NativeCheck();m.config.put("nativeBridge",true);denied(()->m.nativeAction(m.message("mediaPlay","song.mp3","Track")));
  m.config.put("mediaPlayerBridge",true);Build.VERSION.SDK_INT=37;
  check(m.nativeAction(m.message("mediaPlay","song.mp3","Track")).equals("PERMISSION_REQUESTED")&&AppForgeMediaService.calls==0);
  denied(()->m.nativeAction(m.message("mediaPlay","song.mp3","Track")));m.permission=0;
  check(m.nativeAction(m.message("mediaPlay","song.mp3","Track")).equals("STARTING"));
  check(m.nativeAction(m.message("mediaPause")).equals("REQUESTED"));check(m.nativeAction(m.message("mediaResume")).equals("REQUESTED"));
  check(m.nativeAction(m.message("mediaStop")).equals("REQUESTED"));check(m.nativeAction(m.message("mediaState")).equals("state-json"));
  denied(()->m.nativeAction(m.message("mediaUnknown")));m.config.put("nativeBridge",false);denied(()->m.nativeAction(m.message("mediaPlay","song.mp3","Track")));
  n.config.put("nativeBridge",false);denied(()->n.nativeAction(n.message("notify","Title","Body")));check(n.manager.sent==1);
  NativeCheck b=new NativeCheck();b.config.put("nativeBridge",true);denied(()->b.nativeAction(b.message("billingProducts")));
  b.config.put("billingEnabled",true);denied(()->b.nativeAction(b.message("billingProducts")));check(b.billing.calls==0&&b.billing.offline==1);
  check(b.nativeAction(b.message("billingState")).equals("OFFLINE"));b.connectivity.enabled=true;
  check(b.nativeAction(b.message("billingProducts")).equals("REQUESTED"));check(b.nativeAction(b.message("billingRestore")).equals("REQUESTED"));
  check(b.nativeAction(b.message("billingPurchase","one","offer-token")).equals("FLOW_OPENED"));check(b.billing.calls==3&&b.billing.product.equals("one")&&b.billing.offer.equals("offer-token"));
  denied(()->b.nativeAction(b.message("billingUnknown")));b.config.put("nativeBridge",false);denied(()->b.nativeAction(b.message("billingPurchase","one","offer-token")));check(b.billing.calls==3);
  NativeCheck l=new NativeCheck();l.config.put("nativeBridge",true);denied(()->l.nativeAction(l.message("getLocation","id")));
  l.config.put("location",true);l.config.put("mode","URL");denied(()->l.nativeAction(l.message("getLocation","id")));l.config.put("mode","LOCAL");check(l.nativeAction(l.message("getLocation","id")).equals("PENDING"));
  l.location=null;denied(()->l.nativeAction(l.message("getLocation","id")));
  System.out.println("native actions PASS");
 }
}`);
});

test('V2 real connectivity observer tracks validated restoration and drops paused/stale callbacks',()=>{
 java('ConnectivityCheck',`package com.appforge.htmlstub;
import android.app.Activity;import android.net.*;import java.util.*;
public class ConnectivityCheck {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  Activity a=new Activity();List<String> events=new ArrayList<>();final int[] errors={0};AppForgeConnectivity c=new AppForgeConnectivity(a,new AppForgeConnectivity.Sink(){public void changed(boolean online,boolean restored){events.add(online+":"+restored);}public void unavailable(){errors[0]++;}});
  c.start();c.start();check(a.manager.registrations==1);a.drain();check(events.equals(List.of("false:false")));
  a.manager.network=new Network();a.manager.caps=new NetworkCapabilities();a.manager.caps.internet=true;
  a.manager.callback.onAvailable(a.manager.network);a.drain();check(events.size()==1);
  a.manager.caps.validated=true;a.manager.callback.onCapabilitiesChanged(a.manager.network,a.manager.caps);a.drain();check(events.get(1).equals("true:true"));
  a.manager.callback.onAvailable(a.manager.network);a.drain();check(events.size()==2);
  ConnectivityManager.NetworkCallback old=a.manager.callback;c.close();c.close();check(a.manager.removals==1);
  a.manager.network=null;old.onLost(null);a.drain();check(events.size()==2);
  c.start();c.close();a.drain();check(events.size()==2);
  c.start();a.drain();check(events.get(2).equals("false:false"));
  a.manager.network=new Network();a.manager.caps.internet=false;a.manager.caps.validated=true;
  a.manager.callback.onAvailable(a.manager.network);a.drain();check(!c.online()&&events.size()==3);
  c.close();a.manager.fail=true;try{c.start();throw new AssertionError();}catch(SecurityException expected){}
  a.manager.fail=false;c.start();a.drain();check(events.size()==3);
  a.manager.readFail=true;a.manager.callback.onAvailable(a.manager.network);a.drain();check(events.size()==3&&errors[0]==1);c.close();
  System.out.println("connectivity PASS");
 }
}`, [path.join(shell,'AppForgeConnectivity.java')], {
 'android/content/Context.java':`package android.content;public class Context {public static final String CONNECTIVITY_SERVICE="connectivity";}`,
 'android/app/Activity.java':`package android.app;public class Activity extends android.content.Context {public final android.net.ConnectivityManager manager=new android.net.ConnectivityManager();java.util.List<Runnable> queue=new java.util.ArrayList<>();public Object getSystemService(String n){return manager;}public void runOnUiThread(Runnable r){queue.add(r);}public void drain(){java.util.List<Runnable> copy=new java.util.ArrayList<>(queue);queue.clear();copy.forEach(Runnable::run);}}`,
 'android/net/Network.java':`package android.net;public class Network {}`,
 'android/net/NetworkCapabilities.java':`package android.net;public class NetworkCapabilities {public static final int NET_CAPABILITY_INTERNET=1,NET_CAPABILITY_VALIDATED=2;public boolean internet,validated;public boolean hasCapability(int n){return n==1?internet:validated;}}`,
 'android/net/ConnectivityManager.java':`package android.net;public class ConnectivityManager {public boolean fail,readFail;public int registrations,removals;public Network network;public NetworkCapabilities caps;public NetworkCallback callback;public Network getActiveNetwork(){if(readFail)throw new SecurityException();return network;}public NetworkCapabilities getNetworkCapabilities(Network n){return caps;}public void registerDefaultNetworkCallback(NetworkCallback cb){if(fail)throw new SecurityException();callback=cb;registrations++;}public void unregisterNetworkCallback(NetworkCallback cb){removals++;if(callback==cb)callback=null;}public static class NetworkCallback {public void onAvailable(Network n){}public void onLost(Network n){}public void onCapabilitiesChanged(Network n,NetworkCapabilities c){}}}`
 });
});

test('V2 actual foreground wake controller releases on pause, is idempotent and rolls back failed acquire',()=>{
 const stubs={
 'android/content/Context.java':`package android.content;public class Context {public static final String POWER_SERVICE="power";public android.os.PowerManager power=new android.os.PowerManager();public Object getSystemService(String n){return power;}}`,
 'android/app/Activity.java':`package android.app;public class Activity extends android.content.Context {public android.view.Window window=new android.view.Window();public android.view.Window getWindow(){return window;}}`,
 'android/view/Window.java':`package android.view;public class Window {public int flags;public void addFlags(int f){flags|=f;}public void clearFlags(int f){flags&=~f;}}`,
 'android/view/WindowManager.java':`package android.view;public interface WindowManager {class LayoutParams {public static final int FLAG_KEEP_SCREEN_ON=128;}}`,
 'android/os/PowerManager.java':`package android.os;public class PowerManager {public static final int PARTIAL_WAKE_LOCK=1;public int acquires,releases;public boolean fail;public WakeLock last;public WakeLock newWakeLock(int flags,String tag){if(flags!=1)throw new AssertionError();return last=new WakeLock();}public class WakeLock {public boolean held;public long timeout;public void setReferenceCounted(boolean b){if(b)throw new AssertionError();}public void acquire(long t){if(fail)throw new SecurityException();held=true;timeout=t;acquires++;}public boolean isHeld(){return held;}public void release(){held=false;releases++;}}}`
 };
 java('AwakeCheck',`package com.appforge.htmlstub;public class AwakeCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){android.app.Activity a=new android.app.Activity();AppForgeAwake c=new AppForgeAwake(a);
 c.start();c.start();check(a.power.acquires==1&&a.power.last.timeout==600000&&a.window.flags==128);
 c.close();c.close();check(a.power.releases==1&&a.window.flags==0&&!a.power.last.held);
 c.start();check(a.power.acquires==2);a.power.last.held=false;c.close();check(a.power.releases==1&&a.window.flags==0);
 a.power.fail=true;try{c.start();throw new AssertionError();}catch(SecurityException expected){}check(a.window.flags==0);
 a.power=null;try{c.start();throw new AssertionError();}catch(IllegalStateException expected){}check(a.window.flags==0);
 System.out.println("awake PASS");}}
 `,[path.join(shell,'AppForgeAwake.java')],stubs);
});


test('V2 real HTTPS media/location controller requires opt-in, trusted tap, explicit consent and actual Android grant; stale and concurrent requests deny',()=>{
 const stubs={...webPermissionStubs,
 'android/Manifest.java':`package android;public class Manifest {public static class permission {public static final String CAMERA="camera",RECORD_AUDIO="audio",ACCESS_COARSE_LOCATION="location";}}`,
 'android/content/pm/PackageManager.java':`package android.content.pm;public class PackageManager {public static final int PERMISSION_GRANTED=0;}`,
 'android/app/Activity.java':`package android.app;public class Activity {public java.util.Set<String> granted=new java.util.HashSet<>();public String[] requested;public boolean fail;public int checkSelfPermission(String p){return granted.contains(p)?0:-1;}public void requestPermissions(String[] p,int r){if(fail)throw new SecurityException();requested=p;}}`,
 'android/net/Uri.java':`package android.net;public class Uri {String s;public static Uri parse(String s){Uri u=new Uri();u.s=s;return u;}public String toString(){return s;}}`
 };
 java('WebPermissionCheck',`package com.appforge.htmlstub;
import android.webkit.*;import android.app.*;import java.util.*;
public class WebPermissionCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static PermissionRequest request(String origin,String... resources){PermissionRequest p=new PermissionRequest();p.origin=android.net.Uri.parse(origin);p.resources=resources;return p;}
 static void yes(){AlertDialog.last.yes.onClick(AlertDialog.last,1);}
 public static void main(String[] args){
 Activity a=new Activity();boolean[] trusted={true};
 AppForgeWebPermissions c=new AppForgeWebPermissions(a,o->trusted[0]&&"https://good.example".equals(o),true,true,true,false);
 PermissionRequest foreign=request("https://foreign.example","video");c.media(foreign);check(foreign.denials==1);
 for(String[] resources:new String[][]{new String[]{},new String[]{"unknown"},new String[]{"video","video"},new String[]{"video","audio","unknown"}}){PermissionRequest r=request("https://good.example",resources);c.media(r);check(r.denials==1);}
 PermissionRequest p=request("https://good.example","video","audio");c.media(p);check(p.grants==0&&AlertDialog.last.shown);
 PermissionRequest parallel=request("https://good.example","audio");c.media(parallel);check(parallel.denials==1);
 yes();check(c.outstanding()&&Arrays.equals(a.requested,new String[]{"camera","audio"})&&p.grants==0);
 a.granted.add("camera");c.result(AppForgeWebPermissions.REQUEST);check(p.denials==1&&p.grants==0);
 a.granted.add("audio");p=request("https://good.example","video","audio");c.media(p);check(p.grants==0);yes();check(p.grants==1&&p.granted.length==2);
 p=request("https://good.example","audio");c.media(p);AlertDialog.last.no.onClick(AlertDialog.last,0);check(p.denials==1);
 p=request("https://good.example","audio");c.media(p);AlertDialog.last.cancel.onCancel(AlertDialog.last);check(p.denials==1);
 p=request("https://good.example","audio");c.media(p);trusted[0]=false;yes();check(p.denials==1);trusted[0]=true;
 a.granted.clear();p=request("https://good.example","video");c.media(p);yes();check(c.outstanding());c.cancel();check(p.denials==1&&c.outstanding());
 PermissionRequest stale=request("https://good.example","video");c.media(stale);check(stale.denials==1);a.granted.add("camera");c.result(AppForgeWebPermissions.REQUEST);check(p.grants==0&&!c.outstanding());
 p=request("https://good.example","video");c.media(p);c.canceled(p);check(p.grants==0);yes();check(p.grants==0);
 int[] geo={0,0};GeolocationPermissions.Callback callback=(o,allow,retain)->{check(!retain);geo[allow?0:1]++;};
 c.location("https://foreign.example",callback);check(geo[1]==1);
 c.location("https://good.example",callback);yes();check(c.outstanding());a.granted.add("location");c.result(AppForgeWebPermissions.REQUEST);check(geo[0]==1);
 c.location("https://good.example",callback);c.cancel();check(geo[1]==2);
 AppForgeWebPermissions off=new AppForgeWebPermissions(a,o->true,false,false,false,false);
 p=request("https://good.example","video");off.media(p);check(p.denials==1);off.location("https://good.example",callback);check(geo[1]==3);
 AppForgeWebPermissions restored=new AppForgeWebPermissions(a,o->true,true,true,true,true);p=request("https://good.example","video");restored.media(p);check(p.denials==1);restored.result(AppForgeWebPermissions.REQUEST);restored.media(p);yes();check(p.grants==1);
 a.granted.clear();a.fail=true;p=request("https://good.example","audio");c.media(p);yes();check(p.denials==1&&!c.outstanding());
 check(!c.result(999));System.out.println("web permissions PASS");}}
 `,[path.join(shell,'AppForgeWebPermissions.java')],stubs);
});

test('V2 deep-link receiver validates bounded scheme/host/path data and never accepts injected or traversal URLs',()=>{
 java('DeepLinkCheck',`package com.appforge.htmlstub;public class DeepLinkCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 check(AppForgeDeepLink.accept("sample://example.com/open/item?q=a%20b","sample","example.com","/open").contains("q="));
 check(AppForgeDeepLink.accept("https://EXAMPLE.com/open","https","example.com","/")!=null);
 for(String value:new String[]{"sample://evil.com/open","sample://example.com/other","sample://u:p@example.com/open","sample://example.com:443/open","sample://example.com/open/../secret","sample://example.com/open/%2e%2e/secret","sample://example.com/open/%00","sample://example.com/open/%5cpath","javascript:alert(1)","file:///open","content://example.com/open","sample://example.com/open invalid","sample://example.com/open/"+"a".repeat(2048)})check(AppForgeDeepLink.accept(value,"sample","example.com","/open")==null);
 check(AppForgeDeepLink.accept(null,"sample","example.com","/")==null);System.out.println("deep link PASS");}}
 `,[path.join(shell,'AppForgeDeepLink.java')]);
});


test('V2 actual NFC reader only delivers one bounded foreground ID; hardware failures, timeout, pause and stale results deny',()=>{
 const stubs={...nfcStubs,
 'android/os/Bundle.java':`package android.os;public class Bundle {}`,
 'android/app/Activity.java':`package android.app;public class Activity {public java.util.List<Runnable> queued=new java.util.ArrayList<>();public android.view.Window window=new android.view.Window();public void runOnUiThread(Runnable r){queued.add(r);}public void drain(){for(Runnable r:new java.util.ArrayList<>(queued))r.run();queued.clear();}public android.view.Window getWindow(){return window;}}`,
 'android/view/Window.java':`package android.view;public class Window {public View view=new View();public View getDecorView(){return view;}}`,
 'android/view/View.java':`package android.view;public class View {public Runnable timeout;public boolean postDelayed(Runnable r,long n){if(n!=30000)throw new AssertionError();timeout=r;return true;}}`
 };
 java('NfcCheck',`package com.appforge.htmlstub;import android.nfc.*;import java.util.*;
 public class NfcCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static Tag tag(byte[] bytes){Tag t=new Tag();t.id=bytes;return t;}
 public static void main(String[] args){android.app.Activity a=new android.app.Activity();boolean[] trusted={true};List<String> ids=new ArrayList<>();AppForgeNfc c=new AppForgeNfc(a,()->trusted[0],ids::add);
 check(c.start().equals("LISTENING"));NfcAdapter n=NfcAdapter.available;NfcAdapter.ReaderCallback old=n.callback;
 try{c.start();throw new AssertionError();}catch(IllegalStateException expected){}
 old.onTagDiscovered(tag(new byte[]{0,15,-1}));a.drain();check(ids.equals(List.of("000fff"))&&n.callback==null);old.onTagDiscovered(tag(new byte[]{2}));a.drain();check(ids.size()==1);
 c.start();old=n.callback;old.onTagDiscovered(tag(new byte[]{1}));c.close();c.start();a.drain();check(ids.size()==1);a.window.view.timeout.run();check(n.callback==null);
 c.start();old=n.callback;trusted[0]=false;old.onTagDiscovered(tag(new byte[]{1}));a.drain();check(ids.size()==1);try{c.start();throw new AssertionError();}catch(SecurityException expected){}trusted[0]=true;
 for(byte[] bad:new byte[][]{null,new byte[]{},new byte[33]}){c.start();n.callback.onTagDiscovered(tag(bad));a.drain();check(ids.size()==1);}
 n.enabled=false;try{c.start();throw new AssertionError();}catch(IllegalStateException expected){}n.enabled=true;n.fail=true;try{c.start();throw new AssertionError();}catch(SecurityException expected){}check(n.callback==null);
 NfcAdapter.available=null;try{c.start();throw new AssertionError();}catch(IllegalStateException expected){}c.close();System.out.println("NFC PASS");}}
 `,[path.join(shell,'AppForgeNfc.java')],stubs);
});


test('V2 real foreground scanner handles camera denial, reentry, decoded result, timeout, surface failure and cleanup',()=>{
 java('ScannerCheck',`package com.appforge.htmlstub;import java.util.*;import android.hardware.Camera;
 public class ScannerCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void open(){android.view.SurfaceView.holder.callback.surfaceCreated(android.view.SurfaceView.holder);}
 public static void main(String[] args)throws Exception {
 android.app.Activity a=new android.app.Activity();boolean[] trusted={true};List<String> results=Collections.synchronizedList(new ArrayList<>());List<String> errors=new ArrayList<>();
 AppForgeQrScanner c=new AppForgeQrScanner(a,()->trusted[0],results::add,errors::add);
 a.permission=-1;try{c.start();throw new AssertionError();}catch(SecurityException expected){}a.permission=0;
 trusted[0]=false;try{c.start();throw new AssertionError();}catch(SecurityException expected){}trusted[0]=true;
 check(c.start().equals("SCANNING"));try{c.start();throw new AssertionError();}catch(IllegalStateException expected){}open();Camera camera=Camera.last;check(camera.started);
 byte[] frame=new byte[1024];frame[0]=1;camera.callback.onPreviewFrame(frame,camera);
 check(AppForgeBarcode.decoded.await(2,java.util.concurrent.TimeUnit.SECONDS));for(int i=0;i<200&&results.isEmpty();i++)Thread.sleep(5);
 check(results.equals(List.of("decoded-test"))&&camera.released&&!camera.started&&android.app.Dialog.last.dismissed);
 c.close();c.start();open();camera=Camera.last;a.window.view.timeout.run();check(camera.released&&errors.size()==1);
 c.start();open();camera=Camera.last;android.view.SurfaceView.holder.callback.surfaceDestroyed(android.view.SurfaceView.holder);check(camera.released);
 Camera.fail=true;c.start();open();check(errors.size()==2&&android.app.Dialog.last.dismissed);Camera.fail=false;
 c.start();trusted[0]=false;open();check(android.app.Dialog.last.dismissed&&results.size()==1);trusted[0]=true;
 c.close();System.out.println("scanner PASS");}}
 `,[path.join(shell,'AppForgeQrScanner.java')],scannerStubs);
});
