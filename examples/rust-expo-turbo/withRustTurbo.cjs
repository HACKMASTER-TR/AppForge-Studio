// Authored opt-in new-architecture C++ TurboModule registration; no legacy
// JS runtime pointer, remote script injection, or template core modification.
const {withDangerousMod,withAppBuildGradle}=require('expo/config-plugins');
const fs=require('fs'),path=require('path');
module.exports=config=>{
 config=withDangerousMod(config,['android',async c=>{
  for(const name of ['RustTurboModule.h','RustTurboModule.cpp','OnLoad.cpp','CMakeLists.txt']) {
   const dest=path.join(c.modRequest.platformProjectRoot,'app/src/main/jni',name);
   const bytes=fs.readFileSync(path.join(c.modRequest.projectRoot,'native',name));
   fs.mkdirSync(path.dirname(dest),{recursive:true});
   if(fs.existsSync(dest)&&!fs.readFileSync(dest).equals(bytes))throw new Error('Foreign TurboModule binding preserved: '+name);
   fs.writeFileSync(dest,bytes);
  }return c;
 }]);
 return withAppBuildGradle(config,c=>{
  if(c.modResults.language!=='groovy')throw new Error('Reviewed Groovy app Gradle required');
  if(!c.modResults.contents.includes('// APPFORGE_AUTHORED_RUST_TURBO'))c.modResults.contents+='\n// APPFORGE_AUTHORED_RUST_TURBO\nandroid {\n buildFeatures { prefab true }\n defaultConfig { minSdkVersion 26; ndk { abiFilters "arm64-v8a" }; externalNativeBuild { cmake { arguments "-DANDROID_STL=c++_shared" } } }\n externalNativeBuild { cmake { path "src/main/jni/CMakeLists.txt" } }\n packagingOptions { jniLibs { keepDebugSymbols += ["**/libappforge_jsi.so"] } }\n}\n';return c;
 });
};
