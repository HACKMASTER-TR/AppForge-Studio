import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import cp from 'node:child_process';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
function read(p){return fs.readFileSync(path.join(root,p),'utf8');}
test('actual two-engine LAN gate executes OS/target/grant/deny/retry/revoke/recovery without permission loops (HOST simulation)',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-lan-gate-'));
 try{
 const stubs={
 'android/os/Build.java':'package android.os;public class Build{public static class VERSION{public static int SDK_INT=37;}}',
 'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager{public static final int PERMISSION_GRANTED=0;}',
 'android/content/pm/ApplicationInfo.java':'package android.content.pm;public class ApplicationInfo{public int targetSdkVersion=37;}',
 'android/view/View.java':'package android.view;public class View{public static final int GONE=8,VISIBLE=0;public interface Listener{void onClick(View v);}public void setVisibility(int i){} }',
 'android/view/Gravity.java':'package android.view;public class Gravity{public static final int BOTTOM=80;}',
 'android/widget/FrameLayout.java':'package android.widget;public class FrameLayout{public static class LayoutParams{public LayoutParams(int w,int h,int g){}}}',
 'android/widget/Button.java':'package android.widget;public class Button extends android.view.View{public Button(Object a){}public void setText(String s){}public void setOnClickListener(Listener l){} }',
 'android/app/AlertDialog.java':'package android.app;public class AlertDialog{public interface Click{void onClick(Object d,int which);}public static class Builder{public Builder(Object a){}public Builder setTitle(String s){return this;}public Builder setMessage(String s){return this;}public Builder setPositiveButton(String s,Click c){return this;}public Builder setNegativeButton(String s,Click c){return this;}public void show(){}}}'
 };
 for(const [n,s]of Object.entries(stubs)){const p=path.join(dir,n);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,s);}
 for(const [index,file]of ['android-app/app/src/main/assets/device-build/FastActivity.java','android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java'].entries()){
 const src=read(file);const a=src.indexOf('    private static final String LAN_PERMISSION');const b=src.indexOf('    @Override',a);assert.ok(a>=0&&b>a);
 const code=`public class Gate${index}{
 static class Config{boolean required=true;boolean optBoolean(String k,boolean fallback){return required;}}
 static class Web{int denied,stopped;void stopLoading(){stopped++;}void loadDataWithBaseURL(String a,String b,String c,String d,String e){denied++;}}
 Config config=new Config(); Web webView=new Web(),web=webView; int requests,loads,permission=-1;
 android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo();
 android.content.pm.ApplicationInfo getApplicationInfo(){return info;}int checkSelfPermission(String p){return permission;}
 void requestPermissions(String[] p,int id){if(p.length!=1||!p[0].equals("android.permission.ACCESS_LOCAL_NETWORK")||id!=7391)throw new AssertionError();requests++;}
 void addContentView(Object a,Object b){}void loadStartPageUnchecked(){loads++;}void loadProjectPage(){loads++;}
 ${src.slice(a,b)}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 Gate${index} g=new Gate${index}();g.startAfterLanPermission(false);check(g.requests==1&&g.loads==0);g.startAfterLanPermission(false);check(g.requests==1);
 g.lanRequestInFlight=false;g.showLanDenied();g.resumeLanPermission();check(g.loads==0&&g.requests==1);
 g.startAfterLanPermission(true);check(g.requests==2);g.permission=0;g.lanRequestInFlight=false;g.startAfterLanPermission(false);check(g.loads==1);
 g.permission=-1;g.resumeLanPermission();check(g.lanBlocked&&g.requests==2&&g.web.denied>0);g.permission=0;g.resumeLanPermission();check(g.loads==2);
 Gate${index} restored=new Gate${index}();restored.lanAttempted=true;restored.startAfterLanPermission(false);check(restored.requests==0&&restored.loads==0&&restored.web.denied==1);
 Gate${index} lower=new Gate${index}();lower.info.targetSdkVersion=36;lower.startAfterLanPermission(false);check(lower.requests==0&&lower.loads==1);
 Gate${index} oldOs=new Gate${index}();android.os.Build.VERSION.SDK_INT=36;oldOs.startAfterLanPermission(false);check(oldOs.requests==0&&oldOs.loads==1);android.os.Build.VERSION.SDK_INT=37;
 Gate${index} publicUrl=new Gate${index}();publicUrl.config.required=false;publicUrl.startAfterLanPermission(false);check(publicUrl.requests==0&&publicUrl.loads==1);
 Gate${index} granted=new Gate${index}();granted.permission=0;granted.startAfterLanPermission(false);check(granted.requests==0&&granted.loads==1);
 System.out.println("HOST_LAN_GATE_PASS; ANDROID_PERMISSION_DIALOG_NOT_RUN");}}
 `;
 fs.writeFileSync(path.join(dir,`Gate${index}.java`),code);
 }
 cp.execFileSync('javac',Object.keys(stubs).concat(['Gate0.java','Gate1.java']),{cwd:dir,stdio:'pipe'});
 for(const n of ['Gate0','Gate1'])assert.match(cp.execFileSync('java',['-cp',dir,n],{encoding:'utf8'}),/HOST_LAN_GATE_PASS/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('LAN permission is a coordinated template/inventory/config slot, separate from scoped HTTP and trusted bridge',()=>{
 const b='android-app/app/src/main/java/com/appforge/studio/build/';
 for(const f of ['FastHtmlApkPolicy.kt','HtmlBinaryManifest.kt','FastHtmlAabProtoManifest.kt'])assert.match(read(b+f),/ACCESS_LOCAL_NETWORK/);
 assert.match(read('android-app/html-apk-template/src/main/AndroidManifest.xml'),/ACCESS_LOCAL_NETWORK/);
 for(const f of ['FastHtmlApkPackager.kt','DeviceBuildEngine.kt'])assert.match(read(b+f),/localNetworkRequired.*WebviewProjectSettings.needsLocalNetwork/);
 const s=read(b+'WebviewProjectSettings.kt');assert.match(s,/draft.targetSdk>=37/);assert.match(s,/includeSubdomains="false"/);
 assert.doesNotMatch(read('android-app/app/src/main/AndroidManifest.xml'),/ACCESS_LOCAL_NETWORK/);
 for(const f of ['android-app/app/src/main/assets/device-build/FastActivity.java','android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java']){
 const t=read(f);assert.match(t,/request(?:Code)? == LAN_REQUEST/);assert.match(t,/appforge.lanAttempted/);assert.match(t,/resumeLanPermission\(\)/);assert.match(t,/lanRetryButton.setOnClickListener/);
 }
});
