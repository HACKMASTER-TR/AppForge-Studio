import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {execFileSync} from 'node:child_process';
const root = path.resolve(import.meta.dirname, '../..');

test('T21 provider actual resolver rejects expired, oversized, foreign and escaping files (stubbed Android boundary)', () => {
 const variants = ['android-app/app/src/main/assets/device-build', 'android-app/html-apk-template/src/main/java/com/appforge/htmlstub'];
 const sources = variants.map(dir => fs.readFileSync(path.join(root, dir, 'AppForgeShareProvider.java'), 'utf8'));
 assert.equal(sources[0].replace(/^package .*;/, ''), sources[1].replace(/^package .*;/, ''));
 const source = sources[0];
 const resolver = source.slice(source.indexOf('    private File resolve('), source.indexOf('    @Override public boolean onCreate'));
 const directory = source.slice(source.indexOf('    private static File directory('), source.indexOf('    static synchronized Uri create'));
 const documents = fs.readFileSync(path.join(root, variants[0], 'AppForgeDocuments.java'), 'utf8');
 const nameStart = documents.indexOf('    static String safeDocumentName(');
 assert.ok(nameStart > 0);
 // Compile the actual sanitizer, including any private helpers preceding the next method.
 const nameEnd = documents.indexOf('    boolean activityResult(', nameStart);
 const sanitizer = documents.slice(nameStart, nameEnd);
 const dir = fs.mkdtempSync(path.join(os.tmpdir(), 't21-provider-'));
 try {
 fs.writeFileSync(path.join(dir, 'Check.java'), `import java.io.*; import java.util.*;
public class Check {
 static final long TTL=3600000L;
 static class Context {File cache; Context(File f){cache=f;} File getCacheDir(){return cache;} String getPackageName(){return "com.test.child";}}
 static class Uri {String scheme="content", authority="com.test.child.appforge.documents", query, fragment; List<String> parts;
 Uri(String key){parts=Arrays.asList(key,"report.pdf");} String getScheme(){return scheme;} String getAuthority(){return authority;}
 String getQuery(){return query;} String getFragment(){return fragment;} List<String> getPathSegments(){return parts;}}
 static class AppForgeDocuments {static final int LIMIT=16*1024*1024;
 ${sanitizer}
 }
 Context context; Context getContext(){return context;}
 ${directory}
 ${resolver}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 void denied(Uri u){try{resolve(u);throw new AssertionError("unexpected access");}catch(FileNotFoundException expected){}}
 public static void main(String[] args)throws Exception {
 Check c=new Check();c.context=new Context(new File(args[0]));File root=directory(c.context);
 String key="a".repeat(32)+".pdf";File file=new File(root,key);try(FileOutputStream out=new FileOutputStream(file)){out.write(1);}
 Uri uri=new Uri(key);check(c.resolve(uri).equals(file));
 uri.authority="foreign.appforge.documents";c.denied(uri);uri.authority="com.test.child.appforge.documents";
 uri.scheme="file";c.denied(uri);uri.scheme="content";
 uri.query="x";c.denied(uri);uri.query=null;uri.fragment="x";c.denied(uri);uri.fragment=null;
 c.denied(new Uri("../report.pdf"));c.denied(new Uri("A".repeat(32)+".pdf"));
 uri.parts=Arrays.asList(key);c.denied(uri);uri=new Uri(key);
 try(RandomAccessFile out=new RandomAccessFile(file,"rw")){out.setLength(AppForgeDocuments.LIMIT+1L);}c.denied(uri);
 try(RandomAccessFile out=new RandomAccessFile(file,"rw")){out.setLength(0);}c.denied(uri);
 try(FileOutputStream out=new FileOutputStream(file)){out.write(1);}
 check(file.setLastModified(System.currentTimeMillis()-TTL-2000));c.denied(uri);check(!file.exists());
 try(FileOutputStream out=new FileOutputStream(file)){out.write(1);}
 check(file.setLastModified(System.currentTimeMillis()+60000));c.denied(uri);check(!file.exists());
 File outside=new File(c.context.cache,"outside.pdf");try(FileOutputStream out=new FileOutputStream(outside)){out.write(1);}
 java.nio.file.Files.createSymbolicLink(file.toPath(),outside.toPath());c.denied(uri);check(outside.exists());
 System.out.println("provider assertions passed");
 }
}`);
 execFileSync('javac', [path.join(dir, 'Check.java')], {stdio:'pipe'});
 assert.match(execFileSync('java', ['-cp',dir,'Check',dir], {encoding:'utf8'}), /assertions passed/);
 } finally { fs.rmSync(dir, {recursive:true, force:true}); }
});
