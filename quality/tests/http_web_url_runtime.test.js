import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {execFileSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
test('actual Tiny HTTP navigation method accepts selected host and ports without secure-origin or cross-host bypass',()=>{
 const src=read('android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java');
 const begin=src.indexOf('    private boolean isAllowedWebNavigation(');
 const method=src.slice(begin,src.indexOf('    @Override public void onCreate',begin));
 assert.ok(begin>0);
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-http-nav-'));
 try {
 fs.writeFileSync(path.join(dir,'HttpNav.java'),`public class HttpNav { static class Config { java.util.Map<String,String> data=new java.util.HashMap<>(); Config put(String k,String v){data.put(k,v);return this;} String optString(String k){return data.getOrDefault(k,"");} } Config config=new Config(); ${method}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args) {
 HttpNav p=new HttpNav();
 for(String source:new String[]{"http://example.com:8080/path","http://192.168.1.7:8080/","http://localhost:3000/","http://127.0.0.1:8123/","http://[::1]:8080/"}) {
  p.config=new Config().put("mode","URL").put("url",source);
  check(p.isAllowedWebNavigation(source));
  check(p.isAllowedWebNavigation("https://other.example/path"));
  check(!p.isAllowedWebNavigation("http://other.example/path"));
  check(!p.isAllowedWebNavigation("javascript:alert(1)"));
  check(!p.isAllowedWebNavigation("http://user:pass@localhost/"));
 }
 p.config=new Config().put("mode","URL").put("url","https://example.com");
 check(!p.isAllowedWebNavigation("http://example.com"));
 p.config=new Config().put("mode","LOCAL");check(!p.isAllowedWebNavigation("http://localhost"));
 System.out.println("HOST_NAVIGATION_POLICY_PASS; ANDROID_WEBVIEW_RENDER_NOT_RUN");
 }}
`);
 execFileSync('javac',['-d',dir,path.join(dir,'HttpNav.java')],{timeout:60000});
 assert.match(execFileSync('java',['-cp',dir,'HttpNav'],{encoding:'utf8',timeout:60000}),/HOST_NAVIGATION_POLICY_PASS/);
 } finally {fs.rmSync(dir,{recursive:true,force:true});}
});
test('HTTP host policy is generated-app only; saved URL is preserved and mixed-content/native HTTPS guards remain',()=>{
 const tiny=read('android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java');
 assert.match(tiny,/if \(!isAllowedWebNavigation\(address\)\)/);
 assert.match(tiny,/web\.loadUrl\(address\)/);
 assert.match(tiny,/HTTP host blocked/);
 assert.match(tiny,/MIXED_CONTENT_NEVER_ALLOW/);
 assert.doesNotMatch(tiny,/MIXED_CONTENT_ALWAYS_ALLOW/);
 const settings=read('android-app/app/src/main/java/com/appforge/studio/build/WebviewProjectSettings.kt');
 assert.match(settings,/base-config cleartextTrafficPermitted="false"/);
 assert.match(settings,/includeSubdomains="false"/);
 assert.match(settings,/Uzak Native Bridge yalnız HTTPS/);
 const library=read('android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt');
 assert.match(library,/put\("webUrl", d\.webUrl\)/);assert.match(library,/webUrl = obj\.optString\("webUrl"\)/);
 assert.doesNotMatch(read('android-app/app/src/main/AndroidManifest.xml'),/zz_appforge_network_security/);
});
