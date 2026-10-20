#!/usr/bin/env python3
"""Host integration of production packagers with real AGP templates and ephemeral test signing.
Only Context/AssetManager and generated BuildConfig are host boundaries. No device claims.
Requires :app:generateFastHtmlIntegrity from the unchanged source tree first.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import re
import struct
import zlib
from zipfile import ZipFile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--android-jar', required=True)
p.add_argument('--apksig-jar', required=True)
p.add_argument('--json-jar', required=True)
p.add_argument('--aapt2', help='Independent binary manifest/resource inspection')
p.add_argument('--assets-build', required=True, help='android-app/app/build containing generated assets')
args = p.parse_args()
root = Path(__file__).resolve().parents[2]
build = root / 'android-app/app/src/main/java/com/appforge/studio/build'
model = root / 'android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt'
assets_build = Path(args.assets_build).resolve()
for required in ['html-template/assets/html-apk-template.apk', 'html-aab-template/assets/html-aab-template.aab',
                 'html-integrity/assets/html-template-integrity.txt']:
    if not (assets_build / 'generated' / required).is_file():
        p.error('Missing generated AGP evidence: ' + required)
with tempfile.TemporaryDirectory(prefix='fast-html-v2-compiled-') as directory:
    work = Path(directory)
    (work / 'classes').mkdir()
    def png_chunk(name, data):
        return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data))
    icon = b'\x89PNG\r\n\x1a\n'+png_chunk(b'IHDR',struct.pack('>IIBBBBB',2,2,8,6,0,0,0))+png_chunk(b'IDAT',zlib.compress((b'\x00'+bytes([200,50,100,255])*2)*2))+png_chunk(b'IEND',b'')
    (work / 'selected-icon.png').write_bytes(icon)
    (work / 'AssetManager.java').write_text('''package android.content.res;
public class AssetManager {
 private final java.io.File root;
 public AssetManager(java.io.File root){this.root=root;}
 public java.io.InputStream open(String name)throws java.io.IOException {
  String folder=name.equals("html-apk-template.apk")?"html-template":name.equals("html-aab-template.aab")?"html-aab-template":"html-integrity";
  return new java.io.FileInputStream(new java.io.File(root,"generated/"+folder+"/assets/"+name));
 }
}''')
    (work / 'Context.java').write_text('''package android.content;
public class Context {private final android.content.res.AssetManager assets;
public Context(java.io.File root){assets=new android.content.res.AssetManager(root);}
public android.content.res.AssetManager getAssets(){return assets;}
public ContentResolver getContentResolver(){return new ContentResolver();}}
''')
    (work / 'ContentResolver.java').write_text('''package android.content;
public class ContentResolver {public java.io.InputStream openInputStream(android.net.Uri uri)throws java.io.IOException {return new java.io.FileInputStream(System.getProperty("appforge.test.icon"));}}
''')
    (work / 'Uri.java').write_text('''package android.net;
public class Uri {private java.net.URI value;public static Uri parse(String s){Uri u=new Uri();u.value=java.net.URI.create(s);return u;}public String getScheme(){return value.getScheme();}public String getAuthority(){return value.getAuthority();}public String getFragment(){return value.getFragment();}}
''')
    subprocess.run(['javac', '-cp', args.android_jar, '-d', str(work / 'classes'), str(work / 'AssetManager.java'), str(work / 'Context.java'), str(work / 'ContentResolver.java'), str(work / 'Uri.java')], check=True)
    # Test-only identity, confined to this temporary directory and removed on completion.
    password = 'fast-html-v2-ephemeral-test-only'
    store = work / 'test-signing.p12'
    subprocess.run(['keytool', '-genkeypair', '-noprompt', '-alias', 'test', '-keyalg', 'RSA', '-keysize', '2048',
                    '-validity', '2', '-dname', 'CN=Fast HTML V2 Host Test Only', '-storetype', 'PKCS12',
                    '-keystore', str(store), '-storepass', password, '-keypass', password],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    (work / 'BuildConfig.kt').write_text('package com.appforge.studio\nobject BuildConfig { const val DEBUG=true }')
    (work / 'Smoke.kt').write_text(r'''package com.appforge.studio.build
import com.appforge.studio.model.*
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import com.android.apksig.*
fun main(args:Array<String>) {
 // Production packagers must not open sockets or start SDK/JDK/Gradle/Node processes.
 @Suppress("DEPRECATION", "removal")
 val offline = object: SecurityManager() {
  override fun checkPermission(permission:java.security.Permission?) {}
  override fun checkConnect(host:String?,port:Int) { throw SecurityException("Network forbidden in offline packager smoke") }
  override fun checkExec(command:String?) { throw SecurityException("Toolchain process forbidden in packager smoke") }
 }
 @Suppress("DEPRECATION", "removal")
 System.setSecurityManager(offline)
 val root=File(args[0]);val context=android.content.Context(File(args[1]))
 val store=KeyStore.getInstance("PKCS12").apply{File(root,"test-signing.p12").inputStream().use{load(it,args[2].toCharArray())}}
 val key=store.getKey("test",args[2].toCharArray()) as java.security.PrivateKey
 val certificate=store.getCertificate("test") as X509Certificate
 val signer=ApkSigner.SignerConfig.Builder("test",key,listOf(certificate)).build()
 val site=File(root,"site").apply{mkdir()}
 File(site,"index.html").writeText("<html><input type='file' multiple><video src='clip.mp4'></video></html>")
 val media=byteArrayOf(0,1,2,127,-1);File(site,"clip.mp4").writeBytes(media)
 for (enabled in listOf(false,true)) {
  val workspace=File(root,"work-$enabled").apply{mkdir()}
  val draft=ProjectDraft(appName="V2 Test",packageName="com.example.v2test${if(enabled) "enabled" else "disabled"}",
   sourceTechnology="html",importedFolder=site.path,startPage=File(site,"index.html").path,buildOutput="both",
   appCategory=if(enabled) "none" else "game",primaryColor=if(enabled) "#BC9CF6" else "#6B7CFF",
   deepLinkEnabled=enabled,deepLinkScheme="sample",deepLinkHost="example.com",deepLinkPathPrefix="/open",
   billingEnabled=enabled,billingProductIds=if(enabled)"one" else "",purchaseVerificationUrl=if(enabled)"https://verify.example/purchase" else "",mediaPlayerBridge=enabled,qrScanner=enabled,nfc=enabled,iconUri=if(enabled)"content://icons/selected" else null,additionalPermissions=if(enabled)setOf("CONTACTS","BLUETOOTH") else emptySet(),wakeLock=enabled,notifications=enabled,networkState=enabled,vibrationBridge=enabled,clipboardBridge=enabled,
   fullscreen=enabled,orientation=if(enabled)"portrait" else "unspecified",splashEnabled=enabled)
  TemplateIntegrity.requireBundled(context,setOf("html-apk-template.apk","html-aab-template.aab"))
  val apk=FastHtmlApkPackager.build(context,draft.copy(buildOutput="apk"),workspace,signer)
  val apkVerification=ApkVerifier.Builder(apk).build().verify()
  check(apkVerification.isVerified && apkVerification.isVerifiedUsingV2Scheme && apkVerification.isVerifiedUsingV3Scheme)
  check(apkVerification.signerCertificates.single().encoded.contentEquals(certificate.encoded))
  val unsigned=FastHtmlAabUnsignedPackager.buildUnsignedCandidate(context,draft,workspace)
  val aab=FastHtmlAabJarSigner.signWithIdentity(unsigned,workspace,key,certificate)
  FastHtmlAabVerification.requireSignedPayload(aab,certificate)
  ZipFile(apk).use { a -> ZipFile(aab).use { b ->
   fun read(zip:ZipFile,name:String)=zip.getInputStream(zip.getEntry(name)).use{it.readBytes()}
   check(read(a,"assets/appforge-html-config.json").contentEquals(read(b,"base/assets/appforge-html-config.json")))
   if (enabled) {
    val icon=File(System.getProperty("appforge.test.icon")).readBytes()
    check(read(a,FastHtmlResourceColor.binaryIconPath(read(a,"resources.arsc"))).contentEquals(icon))
    check(read(b,FastHtmlIcon.AAB_PATH).contentEquals(icon))
   }
   check(read(a,"assets/site/clip.mp4").contentEquals(media))
   check(read(b,"base/assets/site/clip.mp4").contentEquals(media))
   val apkManifest=read(a,"AndroidManifest.xml");val aabManifest=read(b,"base/manifest/AndroidManifest.xml")
   check(apkManifest.isNotEmpty() && aabManifest.toString(Charsets.UTF_8).contains(draft.packageName+".appforge.documents"))
   if (enabled) check(aabManifest.toString(Charsets.UTF_8).contains("android.permission.POST_NOTIFICATIONS"))
   else check(!aabManifest.toString(Charsets.UTF_8).contains("android.permission.POST_NOTIFICATIONS"))
  }}
  val sourceBinary=java.util.zip.ZipFile(File(args[1],"generated/html-template/assets/html-apk-template.apk")).use {it.getInputStream(it.getEntry("resources.arsc")).readBytes()}
  check(runCatching{FastHtmlResourceColor.binary(sourceBinary,"invalid",draft.packageName)}.isFailure)
  check(runCatching{FastHtmlResourceColor.binary(sourceBinary.copyOf(30),"#123456",draft.packageName)}.isFailure)
  val patched=FastHtmlResourceColor.binary(sourceBinary,draft.primaryColor,draft.packageName)
  check(runCatching{FastHtmlResourceColor.binary(patched,"#FFFFFF",draft.packageName)}.isFailure)
  // Wrong certificate, digest mismatch and unsigned AAB remain denied.
  check(runCatching{FastHtmlAabVerification.requireCertificateDigest(ByteArray(32),java.security.MessageDigest.getInstance("SHA-256").digest(certificate.encoded))}.isFailure)
  check(runCatching{FastHtmlAabVerification.requireSignedPayload(unsigned,certificate)}.isFailure)
  check(runCatching{TemplateIntegrity.requireDigest(byteArrayOf(1,2,3),"0".repeat(64))}.isFailure)
  println("PASS production APK+AAB packagers/signers/config/media parity enabled=$enabled (network/exec denied; host asset boundary; ephemeral test identity)")
 }
 for((index,address)in listOf("http://example.com:8080/path?q=1", "http://192.168.1.8:8080/", "http://localhost:8123/", "https://example.com/").withIndex()) {
  val draft=ProjectDraft(appName="URL Smoke",packageName="com.example.urltest$index",sourceMode=com.appforge.studio.model.SourceMode.URL,sourceTechnology="html",webUrl=address)
  val workspace=File(root,"url-$index").apply{mkdir()}
  val apk=FastHtmlApkPackager.build(context,draft,workspace,signer)
  check(ApkVerifier.Builder(apk).build().verify().isVerified)
  ZipFile(apk).use{z->check(org.json.JSONObject(z.getInputStream(z.getEntry("assets/appforge-html-config.json")).readBytes().toString(Charsets.UTF_8)).getString("url")==address)}
  println("PASS production URL APK original URL preserved and signed $index")
 }
}
''')
    jars = [str(work / 'classes'), args.json_jar, args.apksig_jar, args.android_jar]
    classpath = os.pathsep.join(jars)
    sources = ['DeviceBuildCapabilities.kt', 'WebviewProjectSettings.kt', 'FeatureSupportMatrix.kt', 'FastHtmlApkPolicy.kt',
               'FastHtmlApkPackager.kt', 'FastHtmlResourceColor.kt', 'FastHtmlIcon.kt', 'HtmlBinaryManifest.kt', 'TemplateIntegrity.kt',
               'FastHtmlAabProtoManifest.kt', 'FastHtmlAabUnsignedPackager.kt',
               'FastHtmlAabJarSigner.kt', 'FastHtmlAabVerification.kt']
    subprocess.run(['kotlinc', str(root / 'android-app/app/src/main/java/com/appforge/studio/io/HtmlCapabilityFindings.kt'), str(model), *(str(build / s) for s in sources), str(work / 'BuildConfig.kt'),
                    str(work / 'Smoke.kt'), '-cp', classpath, '-include-runtime', '-d', str(work / 'smoke.jar')], check=True)
    subprocess.run(['java', '-Dappforge.test.icon='+str(work / 'selected-icon.png'), '-Djava.security.manager=allow', '-cp', str(work / 'smoke.jar') + os.pathsep + classpath,
                    'com.appforge.studio.build.SmokeKt', str(work), str(assets_build), password], check=True)

    if args.aapt2:
        for index, host in enumerate(['example.com', '192.168.1.8', 'localhost', None]):
            apk = work / ('url-' + str(index)) / 'html-output.apk'
            policy = subprocess.check_output([args.aapt2, 'dump', 'xmltree', str(apk), '--file', 'res/xml/zz_appforge_network_security.xml'], text=True)
            values = re.findall(r'cleartextTrafficPermitted[^\n]*', policy)
            assert len(values) == 2 and re.search(r'0x0(?:\s|$)', values[0]), policy
            assert ('0xffffffff' in values[1]) == (host is not None), policy
            assert (host in policy) if host else ('appforge-cleartext.invalid' in policy)
            manifest = subprocess.check_output([args.aapt2, 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'], text=True)
            assert 'networkSecurityConfig' in manifest
            assert ('0xffffffff' in next(line for line in manifest.splitlines() if 'usesCleartextTraffic' in line)) == (host is not None)
            print('PASS independent AAPT2 default-deny exact-host cleartext policy', index)
        for enabled in ['false', 'true']:
            apk = work / ('work-' + enabled) / 'html-output.apk'
            resources = subprocess.check_output([args.aapt2, 'dump', 'resources', str(apk)], text=True)
            expected = 'ffbc9cf6' if enabled == 'true' else 'ff6b7cff'
            assert re.search(r'color/appforge_primary\s+\(\) #' + expected, resources.lower())
            assert 'Package name=com.example.v2test' in resources
            manifest = subprocess.check_output([args.aapt2, 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'], text=True)
            category = '(?:0xffffffff|-1)' if enabled == 'true' else '0'
            assert re.search(r'appCategory[^\n]*=' + category + r'(?:\s|$)', manifest)
            assert ('ProxyBillingActivity' in manifest) == (enabled == 'true')
            assert ('E: service' in manifest) == (enabled == 'true')
            assert ('scheme' in manifest) == (enabled == 'true')
            if enabled == 'true':
                for marker in ['sample', 'example.com', '/open', 'android.permission.NFC', 'android.permission.WAKE_LOCK', 'android.permission.READ_CONTACTS']:
                    assert marker in manifest
            print('PASS independent AAPT2 primary/category/package/deep-link/permission identity', enabled)

            def fields(data):
                at = 0
                def number():
                    nonlocal at
                    value = shift = 0
                    while True:
                        b = data[at]; at += 1
                        value |= (b & 127) << shift
                        if b < 128: return value
                        shift += 7
                        assert shift < 70
                result = []
                while at < len(data):
                    tag = number(); wire = tag & 7
                    if wire == 0: value = number()
                    elif wire == 2:
                        size = number(); value = data[at:at+size]; at += size
                    elif wire in (1, 5):
                        size = 8 if wire == 1 else 4; value = data[at:at+size]; at += size
                    else: raise AssertionError('wire')
                    result.append((tag >> 3, value))
                return result
            def one(items, tag):
                matches = [value for key, value in items if key == tag]
                assert len(matches) == 1
                return matches[0]
            def strings(items, tag): return one(items, tag).decode()
            bundles = []
            for candidate in (work / ('work-' + enabled)).glob('*.aab'):
                with ZipFile(candidate) as bundle:
                    if 'META-INF/MANIFEST.MF' in bundle.namelist(): bundles.append(candidate)
            assert len(bundles) == 1
            with ZipFile(bundles[0]) as bundle:
                package = fields(one(fields(bundle.read('base/resources.pb')), 2))
                assert strings(package, 2) == 'com.example.v2test' + ('enabled' if enabled == 'true' else 'disabled')
                color_type = [fields(v) for k, v in package if k == 3 and strings(fields(v), 2) == 'color']
                assert len(color_type) == 1
                entries = [fields(v) for k, v in color_type[0] if k == 3 and strings(fields(v), 2) == 'appforge_primary']
                assert len(entries) == 1
                config = fields(one(entries[0], 6)); value = fields(one(config, 2))
                item = fields(one(value, 4)); primitive = fields(one(item, 7))
                assert one(primitive, 9) == int(expected, 16)
                print('PASS independent AAB primary resource and namespace', enabled)
