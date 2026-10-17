import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {execFileSync} from 'node:child_process';
import {mediaStubs} from './helpers/fast_html_media_stubs.js';
const root=path.resolve(import.meta.dirname,'../..');
const shell=path.join(root,'android-app/html-apk-template/src/main/java/com/appforge/htmlstub');

test('actual private media service enforces local assets, preparation/focus, controls, denial, cancellation and cleanup',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-media-service-'));
 try {
 const sources=Object.entries(mediaStubs).map(([name,source])=>{const file=path.join(dir,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,source);return file;});
 const harness=path.join(dir,'MediaCheck.java');
 fs.writeFileSync(harness,`package com.appforge.htmlstub;
import java.io.*;import java.nio.file.*;import java.util.*;import android.app.*;import android.content.*;import android.media.*;import android.os.*;import android.media.session.*;import org.json.JSONObject;
public class MediaCheck {
 static File root;static AppForgeMediaService current;
 static void check(boolean b){if(!b)throw new AssertionError(AppForgeMediaService.state());}
 interface Action {void run()throws Exception;}
 static void denied(Action a)throws Exception{try{a.run();throw new AssertionError("Expected denial");}catch(IllegalArgumentException|IllegalStateException expected){}}
 static void config(boolean enabled,String mode)throws Exception{Files.writeString(new File(root,"appforge-html-config.json").toPath(),new JSONObject().put("mode",mode).put("nativeBridge",true).put("webJavaScriptEnabled",true).put("mediaPlayerBridge",enabled).toString());}
 static AppForgeMediaService service()throws Exception {
  if(current!=null)current.onDestroy();current=new AppForgeMediaService();current.assets.root=root;current.cache=new File(root,"cache");current.cache.mkdirs();current.onCreate();MediaPlayer.last=null;return current;
 }
 static void command(String action){current.onStartCommand(new Intent(current,AppForgeMediaService.class).setAction(action).putExtra("path","site/audio/song.mp3").putExtra("title","Track"),0,1);}
 static MediaPlayer awaitPlayer()throws Exception{for(int i=0;i<400&&MediaPlayer.last==null;i++){Handler.drain();Thread.sleep(5);}Handler.drain();check(MediaPlayer.last!=null);return MediaPlayer.last;}
 static void awaitError()throws Exception{for(int i=0;i<400&&!AppForgeMediaService.state().contains("ERROR");i++){Handler.drain();Thread.sleep(5);}check(AppForgeMediaService.state().contains("ERROR"));}
 public static void main(String[] args)throws Exception {
 root=new File(args[0]);new File(root,"audio").mkdirs();new File(root,"site/audio").mkdirs();Files.write(new File(root,"site/audio/song.mp3").toPath(),new byte[]{1,2,3});
 for(String bad:new String[]{"../private.mp3","/absolute.mp3","https://evil/a.mp3","content://provider/a.mp3","file:///private/a.mp3","a//b.mp3","a/%2e%2e/x.mp3","a/./b.mp3","x.mp3?q=secret","x.mp3#fragment","a\\\\b.mp3","track.exe","x.mp3\\n"})denied(()->AppForgeMediaPaths.asset(bad));
 check(AppForgeMediaPaths.asset("audio/song.mp3").equals("site/audio/song.mp3"));denied(()->AppForgeMediaPaths.title("x".repeat(101)));denied(()->AppForgeMediaPaths.title("bad\\n"));
 Activity activity=new Activity();check(AppForgeMediaService.send(activity,"stop","","").equals("STOPPED"));denied(()->AppForgeMediaService.send(activity,"resume","",""));denied(()->AppForgeMediaService.send(activity,"unknown","",""));
 check(AppForgeMediaService.send(activity,"play","audio/song.mp3","Track").equals("STARTING"));check(activity.started.getStringExtra("path").equals("site/audio/song.mp3"));
 config(true,"LOCAL");AppForgeMediaService s=service();List<String> events=new ArrayList<>();java.util.function.Consumer<String> listener=events::add;AppForgeMediaService.observe(listener);
 command("play");check(s.fg&&AppForgeMediaService.state().contains("PREPARING"));MediaPlayer p=awaitPlayer();check(!p.playing);p.prepared.onPrepared(p);check(p.playing&&AppForgeMediaService.state().contains("PLAYING")&&((Context)s).audio.request!=null);
 MediaSession.last.callback.onPause();check(!p.playing&&AppForgeMediaService.state().contains("PAUSED"));MediaSession.last.callback.onPlay();check(p.playing);
 ((Context)s).audio.request.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);check(!p.playing);((Context)s).audio.request.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);check(p.playing);
 s.receiver.onReceive(s,new Intent(s,AppForgeMediaService.class));check(!p.playing);((Context)s).audio.request.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);check(!p.playing);
 command("resume");check(p.playing);MediaSession.last.callback.onSeekTo(2000);MediaSession.last.callback.onStop();check(s.stopped&&!s.fg&&p.released&&AppForgeMediaService.state().contains("STOPPED"));check(s.cache.list().length==0);
 AppForgeMediaService.observe(null);s.onDestroy();check(s.receiver==null&&MediaSession.last.released);
 config(false,"LOCAL");s=service();command("play");check(s.stopped&&!s.fg&&AppForgeMediaService.state().contains("ERROR"));
 config(true,"URL");s=service();command("play");check(s.stopped&&!s.fg);
 config(true,"LOCAL");s=service();s.permission=-1;command("play");check(s.stopped&&!s.fg&&MediaPlayer.last==null);
 s=service();((Context)s).notifications.blocked=true;command("play");check(s.stopped&&!s.fg);
 s=service();((Context)s).audio.deny=true;command("play");p=awaitPlayer();p.prepared.onPrepared(p);check(p.released&&!p.playing&&AppForgeMediaService.state().contains("AUDIO_FOCUS_DENIED"));
 s=service();command("play");p=awaitPlayer();command("stop");p.prepared.onPrepared(p);check(p.released&&!p.playing&&AppForgeMediaService.state().contains("STOPPED"));
 s=service();command("play");p=awaitPlayer();p.error.onError(p,1,2);check(p.released&&AppForgeMediaService.state().contains("DECODE_FAILED"));
 s=service();command("play");p=awaitPlayer();p.prepared.onPrepared(p);p.completed.onCompletion(p);check(p.released&&s.stopped);
 s=service();command("play");p=awaitPlayer();p.prepared.onPrepared(p);((Context)s).audio.request.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);check(p.released&&s.stopped);
 s=service();command("play");p=awaitPlayer();for(Runnable r:new ArrayList<>(Handler.delayed))r.run();check(p.released&&AppForgeMediaService.state().contains("TIMEOUT"));
 s=service();command("play");p=awaitPlayer();p.prepared.onPrepared(p);command("pause");for(Runnable r:new ArrayList<>(Handler.delayed))r.run();check(s.stopped&&p.released);
 s=service();current.onStartCommand(new Intent(s,AppForgeMediaService.class).setAction("play").putExtra("path","site/no.mp3").putExtra("title","Missing"),0,1);awaitError();check(s.stopped);
 s=service();current.onStartCommand(new Intent(s,AppForgeMediaService.class).setAction("play").putExtra("path","site/../private.mp3").putExtra("title","Bad"),0,1);check(s.stopped&&!s.fg);
 s.onDestroy();AppForgeMediaService.observe(null);System.out.println("MEDIA SERVICE PASS");
 }
}`);
 execFileSync('javac',['-d',dir,...sources,harness,path.join(shell,'AppForgeMediaPaths.java'),path.join(shell,'AppForgeMediaService.java')],{timeout:60000,stdio:'pipe'});
 assert.match(execFileSync('java',['-cp',dir,'com.appforge.htmlstub.MediaCheck',dir],{encoding:'utf8',timeout:30000}),/MEDIA SERVICE PASS/);
 } finally {fs.rmSync(dir,{recursive:true,force:true});}
});
