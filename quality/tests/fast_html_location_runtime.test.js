import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {execFileSync} from 'node:child_process';
import {mediaStubs} from './helpers/fast_html_media_stubs.js';
import {webPermissionStubs} from './helpers/fast_html_v2_android_stubs.js';
const root=path.resolve(import.meta.dirname,'../..');
test('real local location controller requires consent, actual permission and fresh provider fixes; rejects stale/denied/canceled requests',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-location-'));
 try {
 const stubs={
 'org/json/JSONObject.java':mediaStubs['org/json/JSONObject.java'],
 'android/content/DialogInterface.java':webPermissionStubs['android/content/DialogInterface.java'],
 'android/app/AlertDialog.java':webPermissionStubs['android/app/AlertDialog.java'],
 'android/Manifest.java':`package android;public class Manifest {public static class permission {public static final String ACCESS_COARSE_LOCATION="coarse",ACCESS_FINE_LOCATION="fine";}}`,
 'android/content/pm/PackageManager.java':`package android.content.pm;public class PackageManager {public static final int PERMISSION_GRANTED=0;}`,
 'android/content/Context.java':`package android.content;public class Context {public static final String LOCATION_SERVICE="location";}`,
 'android/app/Activity.java':`package android.app;public class Activity extends android.content.Context {public android.location.LocationManager manager=new android.location.LocationManager();public java.util.Set<String> permissions=new java.util.HashSet<>();public String[] asked;public android.view.Window window=new android.view.Window();public Object getSystemService(String s){return manager;}public int checkSelfPermission(String p){return permissions.contains(p)?0:-1;}public void requestPermissions(String[] p,int code){asked=p;if(code!=7206)throw new AssertionError();}public android.view.Window getWindow(){return window;}}`,
 'android/view/Window.java':`package android.view;public class Window {public View view=new View();public View getDecorView(){return view;}}`,
 'android/view/View.java':`package android.view;public class View {public Runnable timeout;public boolean postDelayed(Runnable r,long n){if(n!=30000)throw new AssertionError();timeout=r;return true;}}`,
 'android/os/Bundle.java':`package android.os;public class Bundle {}`,
 'android/os/Looper.java':`package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}`,
 'android/os/SystemClock.java':`package android.os;public class SystemClock {public static long elapsedRealtimeNanos(){return 100000000000L;}}`,
 'android/location/LocationListener.java':`package android.location;public interface LocationListener {void onLocationChanged(Location l);void onProviderDisabled(String p);void onProviderEnabled(String p);void onStatusChanged(String p,int status,android.os.Bundle b);}`,
 'android/location/LocationManager.java':`package android.location;public class LocationManager {public static final String GPS_PROVIDER="gps",NETWORK_PROVIDER="network";public boolean gps=true,network=true,fail;public String provider;public int registrations,removals;public LocationListener listener;public boolean isProviderEnabled(String p){return p.equals("gps")?gps:network;}public void requestLocationUpdates(String p,long time,float distance,LocationListener l,android.os.Looper looper){if(fail)throw new SecurityException();provider=p;listener=l;registrations++;}public void removeUpdates(LocationListener l){if(l==listener)listener=null;removals++;}}`,
 'android/location/Location.java':`package android.location;public class Location {public double latitude=41.01,longitude=28.97,altitude=10;public float accuracy=15,speed=1;public long elapsed=99000000000L,time=1700000000000L;public boolean hasAccuracy=true;public double getLatitude(){return latitude;}public double getLongitude(){return longitude;}public double getAltitude(){return altitude;}public boolean hasAltitude(){return true;}public boolean hasAccuracy(){return hasAccuracy;}public float getAccuracy(){return accuracy;}public boolean hasSpeed(){return true;}public float getSpeed(){return speed;}public long getElapsedRealtimeNanos(){return elapsed;}public long getTime(){return time;}}`
 };
 const files=Object.entries(stubs).map(([name,body])=>{const p=path.join(dir,name);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,body);return p;});
 const harness=path.join(dir,'LocationCheck.java');fs.writeFileSync(harness,`package com.appforge.htmlstub;import android.app.*;import android.location.*;import java.util.*;public class LocationCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 interface Action{void run();}static void denied(Action a){try{a.run();throw new AssertionError();}catch(IllegalArgumentException|IllegalStateException|SecurityException expected){}}
 static void yes(){AlertDialog.last.yes.onClick(AlertDialog.last,1);}
 public static void main(String[] args){Activity a=new Activity();boolean[] trusted={true};List<String> results=new ArrayList<>();AppForgeLocation c=new AppForgeLocation(a,()->trusted[0],v->results.add(v.toString()),false);
 for(String bad:new String[]{"","bad space","x".repeat(65)})denied(()->c.request(bad));trusted[0]=false;denied(()->c.request("a"));trusted[0]=true;
 check(c.request("first").equals("PENDING"));denied(()->c.request("parallel"));check(results.isEmpty());yes();check(c.outstanding()&&Arrays.equals(a.asked,new String[]{"coarse","fine"}));c.permissionsResult(AppForgeLocation.REQUEST);check(results.size()==1&&results.get(0).contains("false")&&a.manager.registrations==0);
 c.request("second");yes();a.permissions.add("coarse");c.permissionsResult(AppForgeLocation.REQUEST);check(a.manager.provider.equals("network"));LocationListener first=a.manager.listener;first.onLocationChanged(new Location());check(results.size()==2&&results.get(1).contains("41.01")&&results.get(1).contains("28.97")&&results.get(1).contains("true")&&a.manager.listener==null);first.onLocationChanged(new Location());check(results.size()==2);
 a.permissions.add("fine");c.request("gps");yes();check(a.manager.provider.equals("gps"));LocationListener live=a.manager.listener;Location stale=new Location();stale.elapsed=1;live.onLocationChanged(stale);Location future=new Location();future.elapsed=100000000001L;live.onLocationChanged(future);Location invalid=new Location();invalid.latitude=Double.NaN;live.onLocationChanged(invalid);invalid=new Location();invalid.longitude=181;live.onLocationChanged(invalid);invalid=new Location();invalid.accuracy=-1;live.onLocationChanged(invalid);check(results.size()==2);a.window.view.timeout.run();check(results.size()==3&&results.get(2).contains("3")&&a.manager.listener==null);
 c.request("cancel");yes();live=a.manager.listener;c.cancel();check(results.size()==4);live.onLocationChanged(new Location());check(results.size()==4);
 c.request("deny");AlertDialog.last.no.onClick(AlertDialog.last,0);check(results.size()==5);c.request("dismiss");AlertDialog.last.cancel.onCancel(AlertDialog.last);check(results.size()==6);
 a.permissions.clear();c.request("permission-pause");yes();check(c.outstanding());c.cancel();denied(()->c.request("too-early"));a.permissions.add("coarse");c.permissionsResult(AppForgeLocation.REQUEST);check(!c.outstanding());c.request("again");yes();live=a.manager.listener;live.onProviderDisabled("network");check(a.manager.listener==null);
 AppForgeLocation restored=new AppForgeLocation(a,()->true,v->{},true);denied(()->restored.request("new"));restored.permissionsResult(AppForgeLocation.REQUEST);check(!restored.outstanding());check(!restored.permissionsResult(1));
 a.manager.network=false;c.request("unavailable");yes();check(a.manager.listener==null);a.manager.network=true;a.manager.fail=true;c.request("revoked");yes();check(a.manager.listener==null);a.manager.fail=false;
 c.request("foreign");trusted[0]=false;yes();check(a.manager.listener==null);trusted[0]=true;
 c.request("pause-result");yes();live=a.manager.listener;trusted[0]=false;int before=results.size();live.onLocationChanged(new Location());check(results.size()==before&&a.manager.listener==null);
 Activity missing=new Activity();missing.manager=null;AppForgeLocation absent=new AppForgeLocation(missing,()->true,v->{},false);denied(()->absent.request("missing"));c.close();System.out.println("LOCATION PASS");
 }}
`);
 execFileSync('javac',['-d',dir,...files,harness,path.join(root,'android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeLocation.java')],{timeout:60000,stdio:'pipe'});
 assert.match(execFileSync('java',['-cp',dir,'com.appforge.htmlstub.LocationCheck'],{encoding:'utf8',timeout:30000}),/LOCATION PASS/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
