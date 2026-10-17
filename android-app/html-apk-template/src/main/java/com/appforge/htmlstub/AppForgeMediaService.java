package com.appforge.htmlstub;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Private mediaPlayback FGS for explicitly selected packaged audio. No remote URLs or background auto-start. */
public final class AppForgeMediaService extends Service {
    private static final String CHANNEL="appforge.media.v2";
    private static final int NOTICE=42;
    private static volatile String snapshot="{\"state\":\"STOPPED\",\"positionMs\":0,\"durationMs\":0}";
    private static volatile boolean running;
    private static java.lang.ref.WeakReference<Consumer<String>> observer=new java.lang.ref.WeakReference<>(null);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile int generation;
    private int pauseTicket;
    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private NotificationManager notifications;
    private File mediaFile;
    private boolean ready,foreground,registered,resumeOnFocus,focusHeld;
    private String state="STOPPED",title="AppForge Audio";

    static void observe(Consumer<String> listener) {
        observer=new java.lang.ref.WeakReference<>(listener);
        if(listener!=null)listener.accept(snapshot);
    }
    static String state(){return snapshot;}
    static String send(Activity activity,String method,String path,String title) {
        if("state".equals(method))return snapshot;
        if(!java.util.Arrays.asList("play","pause","resume","stop").contains(method))throw new IllegalArgumentException("Unknown media action");
        if(!running && !"play".equals(method)) {
            if("stop".equals(method))return "STOPPED";
            throw new IllegalStateException("No active media session");
        }
        Intent intent=new Intent(activity,AppForgeMediaService.class).setAction(method);
        if("play".equals(method)) {
            intent.putExtra("path",AppForgeMediaPaths.asset(path)).putExtra("title",AppForgeMediaPaths.title(title));
            if(activity.startForegroundService(intent)==null)throw new IllegalStateException("Media service unavailable");
            return "STARTING"; // Preparation, focus and playback may still fail; observe the state event.
        }
        if(activity.startService(intent)==null)throw new IllegalStateException("Media service unavailable");
        return "REQUESTED";
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate() {
        super.onCreate();running=true;
        notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        session=new MediaSession(this,"AppForgeAudio");
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){resume();}
            @Override public void onPause(){pause(false);}
            @Override public void onStop(){stopPlayback();}
            @Override public void onSeekTo(long position){
                if(ready && player!=null)try{player.seekTo((int)Math.max(0,Math.min(position,player.getDuration())));publish(state,null);}catch(RuntimeException failure){fail("MEDIA_SEEK_FAILED");}
            }
        },main);
        session.setActive(true);
    }
    private boolean allowed() throws Exception {
        try(InputStream input=getAssets().open("appforge-html-config.json");ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(out.size()+n>65536)return false;out.write(b,0,n);}
            JSONObject config=new JSONObject(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
            return "LOCAL".equals(config.optString("mode")) && config.optBoolean("nativeBridge",false) &&
                config.optBoolean("webJavaScriptEnabled",false) && config.optBoolean("mediaPlayerBridge",false);
        }
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        try {
            if(!allowed() || intent==null){fail("MEDIA_DISABLED");return START_NOT_STICKY;}
            String action=intent.getAction();
            if("play".equals(action)) {
                String supplied=intent.getStringExtra("path");
                if(supplied==null || !supplied.startsWith("site/"))throw new SecurityException("Invalid asset path");
                String path=AppForgeMediaPaths.asset(supplied.substring(5));
                title=AppForgeMediaPaths.title(intent.getStringExtra("title"));
                prepare(path);
            } else if("pause".equals(action))pause(false);
            else if("resume".equals(action))resume();
            else if("stop".equals(action))stopPlayback();
            else fail("UNKNOWN_ACTION");
        } catch(Exception failure){fail("MEDIA_START_FAILED");}
        return START_NOT_STICKY;
    }
    private void ensureForeground() {
        if(notifications==null || !notifications.areNotificationsEnabled())throw new SecurityException("Media notifications disabled");
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"Audio playback",NotificationManager.IMPORTANCE_LOW));
        NotificationChannel channel=notifications.getNotificationChannel(CHANNEL);
        if(channel==null || channel.getImportance()==NotificationManager.IMPORTANCE_NONE)throw new SecurityException("Media channel blocked");
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Media notification consent required");
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notification(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTICE,notification());
        foreground=true;
    }
    private void prepare(String path) {
        releasePlayer();final int current=++generation;
        ready=false;resumeOnFocus=false;pauseTicket++;
        state="PREPARING";ensureForeground();publish(state,null);
        if(!registered) {
            IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,filter);
            registered=true;
        }
        io.execute(()->{
            File candidate=null;boolean delivered=false;
            try {
                candidate=File.createTempFile("AppForgeMediaV2-",".audio",getCacheDir());
                try(InputStream input=getAssets().open(path);OutputStream output=new FileOutputStream(candidate)) {
                    byte[] buffer=new byte[8192];long total=0;int n;
                    while((n=input.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted() || current!=generation)throw new InterruptedIOException();
                        total+=n;if(total>24L*1024*1024)throw new IOException("Media size budget");output.write(buffer,0,n);
                    }
                    if(total==0)throw new IOException("Empty media");
                }
                final File copied=candidate;
                delivered=main.post(()->{
                    if(current!=generation){copied.delete();return;}
                    mediaFile=copied;
                    try {
                        player=new MediaPlayer();final MediaPlayer created=player;
                        created.setAudioAttributes(attributes());created.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
                        created.setDataSource(copied.getAbsolutePath());
                        created.setOnPreparedListener(p->{
                            if(current!=generation || p!=player)return;
                            ready=true;session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title)
                                .putLong(MediaMetadata.METADATA_KEY_DURATION,p.getDuration()).build());resume();
                        });
                        created.setOnCompletionListener(p->{if(current==generation && p==player)stopPlayback();});
                        created.setOnErrorListener((p,what,extra)->{if(current==generation && p==player)fail("DECODE_FAILED");return true;});
                        created.prepareAsync();
                    } catch(Exception failure){fail("MEDIA_PREPARE_FAILED");}
                });
            } catch(Exception failure){main.post(()->{if(current==generation)fail("MEDIA_READ_FAILED");});}
            finally {if(!delivered && candidate!=null)candidate.delete();}
        });
        main.postDelayed(()->{if(current==generation && !ready)fail("MEDIA_PREPARE_TIMEOUT");},30000);
    }
    private AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
    private boolean focus() {
        if(audio==null)return false;
        if(focusHeld)return true;
        if(focusRequest==null)focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes())
            .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(this::focusChanged,main).build();
        focusHeld=audio.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focusHeld;
    }
    private void focusChanged(int change) {
        if(change==AudioManager.AUDIOFOCUS_LOSS){focusHeld=false;stopPlayback();}
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)pause(true);
        else if(change==AudioManager.AUDIOFOCUS_GAIN){focusHeld=true;if(resumeOnFocus)resume();}
    }
    private void resume() {
        if(!ready || player==null){if(!"PREPARING".equals(state))stopPlayback();return;}
        try {
            ensureForeground();
            if(!focus()){fail("AUDIO_FOCUS_DENIED");return;}
            player.start();resumeOnFocus=false;pauseTicket++;publish("PLAYING",null);
        } catch(RuntimeException failure){fail("MEDIA_PLAY_FAILED");}
    }
    private void pause(boolean fromFocus) {
        if(!ready || player==null){stopPlayback();return;}
        try {
            boolean playing=player.isPlaying();if(playing)player.pause();
            resumeOnFocus=fromFocus && (playing || resumeOnFocus);
            publish("PAUSED",null);final int ticket=++pauseTicket;
            main.postDelayed(()->{if(ticket==pauseTicket && "PAUSED".equals(state))stopPlayback();},10*60*1000L);
        } catch(RuntimeException failure){fail("MEDIA_PAUSE_FAILED");}
    }
    private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){pause(false);}};
    private PendingIntent command(String action,int code) {
        return PendingIntent.getService(this,code,new Intent(this,AppForgeMediaService.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        boolean playing="PLAYING".equals(state);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title)
            .setContentText(state).setDeleteIntent(command("stop",3)).setOnlyAlertOnce(true).setOngoing(playing || "PREPARING".equals(state))
            .addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,
                playing?"Pause":"Play",command(playing?"pause":"resume",1)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",command("stop",2)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1)).build();
    }
    private void publish(String next,String error) {
        state=next;
        try {
            int position=ready && player!=null?player.getCurrentPosition():0;
            int duration=ready && player!=null?player.getDuration():0;
            JSONObject data=new JSONObject().put("state",state).put("title",title).put("positionMs",position).put("durationMs",duration);
            if(error!=null)data.put("error",error);
            snapshot=data.toString();
            int platform="PLAYING".equals(state)?PlaybackState.STATE_PLAYING:"PAUSED".equals(state)?PlaybackState.STATE_PAUSED:
                "PREPARING".equals(state)?PlaybackState.STATE_BUFFERING:"ERROR".equals(state)?PlaybackState.STATE_ERROR:PlaybackState.STATE_STOPPED;
            session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SEEK_TO)
                .setState(platform,position,"PLAYING".equals(state)?1f:0f,SystemClock.elapsedRealtime()).build());
            if(foreground)notifications.notify(NOTICE,notification());
            Consumer<String> listener=observer.get();if(listener!=null)listener.accept(snapshot);
        } catch(Exception failure){/* Android teardown can retire UI/session; never claim another playback state. */}
    }
    private void releasePlayer() {
        generation++;ready=false;
        MediaPlayer previous=player;player=null;if(previous!=null){try{previous.release();}catch(RuntimeException ignored){}}
        File old=mediaFile;mediaFile=null;if(old!=null)old.delete();
        if(audio!=null && focusRequest!=null)audio.abandonAudioFocusRequest(focusRequest);
        focusHeld=false;resumeOnFocus=false;
    }
    private void stopPlayback(){releasePlayer();pauseTicket++;publish("STOPPED",null);finishService();}
    private void fail(String error){releasePlayer();pauseTicket++;publish("ERROR",error);finishService();}
    private void finishService(){if(foreground){foreground=false;stopForeground(STOP_FOREGROUND_REMOVE);}stopSelf();}
    @Override public void onDestroy() {
        releasePlayer();pauseTicket++;io.shutdownNow();main.removeCallbacksAndMessages(null);
        if(registered){try{unregisterReceiver(noisy);}catch(IllegalArgumentException ignored){}registered=false;}
        if(foreground){foreground=false;stopForeground(STOP_FOREGROUND_REMOVE);}
        if(!"ERROR".equals(state))publish("STOPPED",null);
        session.setActive(false);session.release();running=false;super.onDestroy();
    }
}
