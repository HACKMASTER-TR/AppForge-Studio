// Authored, opt-in Expo plugin. Runs only as approved source build code in the
// private project copy. It never changes runtime tools, app consent or readiness.
const {withDangerousMod,withAppBuildGradle,withMainApplication}=require('expo/config-plugins');
const fs=require('fs'),path=require('path');
module.exports=config=>{
 config=withDangerousMod(config,['android',async c=>{
  const root=c.modRequest.projectRoot,app=path.join(c.modRequest.platformProjectRoot,'app/src/main');
  for(const [name,relative] of [['RustJsi.cpp','cpp/RustJsi.cpp'],['CMakeLists.txt','cpp/CMakeLists.txt'],['RustJsiModule.java','java/com/appforge/rustjsi/RustJsiModule.java'],['RustJsiPackage.java','java/com/appforge/rustjsi/RustJsiPackage.java']]){
   const dest=path.join(app,relative),bytes=fs.readFileSync(path.join(root,'native',name));
   fs.mkdirSync(path.dirname(dest),{recursive:true});
   if(fs.existsSync(dest)&&!fs.readFileSync(dest).equals(bytes))throw new Error('Foreign Rust JSI binding preserved: '+relative);
   fs.writeFileSync(dest,bytes);
  }return c;
 }]);
 config=withAppBuildGradle(config,c=>{
  if(c.modResults.language!=='groovy')throw new Error('Rust JSI example requires Groovy app Gradle');
  if(!c.modResults.contents.includes('// APPFORGE_AUTHORED_RUST_JSI'))c.modResults.contents+='\n// APPFORGE_AUTHORED_RUST_JSI\nandroid {\n buildFeatures { prefab true }\n defaultConfig { minSdkVersion 26; ndk { abiFilters "arm64-v8a" }; externalNativeBuild { cmake { arguments "-DANDROID_STL=c++_shared" } } }\n externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }\n packagingOptions { jniLibs { keepDebugSymbols += ["**/libappforge_jsi.so"] } }\n}\n';return c;
 });
 return withMainApplication(config,c=>{
  const anchor='PackageList(this).packages.apply {';
  if(!c.modResults.contents.includes('add(com.appforge.rustjsi.RustJsiPackage())')){
   if(!c.modResults.contents.includes(anchor))throw new Error('Expected reviewed RN0.81 registration anchor missing');
   c.modResults.contents=c.modResults.contents.replace(anchor,anchor+'\n              add(com.appforge.rustjsi.RustJsiPackage())');
  }return c;
 });
};
