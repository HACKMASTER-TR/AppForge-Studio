package com.appforge.htmlstub;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import java.util.ArrayList;
import java.util.function.Predicate;

/** Same-origin HTTPS WebView requests: selection + recent tap + explicit consent + Android grant. */
final class AppForgeWebPermissions {
    static final int REQUEST=7203;
    private final Activity activity;
    private final Predicate<String> trusted;
    private final boolean camera,microphone,location;
    private Pending pending;
    private AlertDialog dialog;
    private boolean systemOutstanding;
    private static final class Pending {
        final Object identity; final String origin; final String[] permissions;
        final Runnable grant,deny;
        Pending(Object identity,String origin,String[] permissions,Runnable grant,Runnable deny) {
            this.identity=identity;this.origin=origin;this.permissions=permissions;this.grant=grant;this.deny=deny;
        }
    }
    AppForgeWebPermissions(Activity activity,Predicate<String> trusted,boolean camera,boolean microphone,boolean location,boolean outstanding) {
        this.activity=activity;this.trusted=trusted;this.camera=camera;this.microphone=microphone;this.location=location;
        systemOutstanding=outstanding;
    }
    boolean outstanding(){return systemOutstanding;}
    void media(PermissionRequest request) {
        if(request==null)return;
        String origin=request.getOrigin()==null?null:request.getOrigin().toString();
        String[] resources=request.getResources();
        ArrayList<String> permissions=new ArrayList<>();
        if(resources==null || resources.length<1 || resources.length>2){request.deny();return;}
        for(String resource:resources) {
            String permission;
            if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)&&camera)permission=Manifest.permission.CAMERA;
            else if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)&&microphone)permission=Manifest.permission.RECORD_AUDIO;
            else {request.deny();return;}
            if(permissions.contains(permission)){request.deny();return;}permissions.add(permission);
        }
        final String[] granted=resources.clone();
        begin(new Pending(request,origin,permissions.toArray(new String[0]),()->request.grant(granted),request::deny));
    }
    void location(String origin,GeolocationPermissions.Callback callback) {
        if(callback==null)return;
        if(!location){callback.invoke(origin,false,false);return;}
        // Approximate foreground location is sufficient; no background/fine request.
        begin(new Pending(callback,origin,new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
            ()->callback.invoke(origin,true,false),()->callback.invoke(origin,false,false)));
    }
    private void begin(Pending candidate) {
        if(systemOutstanding || pending!=null || !trusted.test(candidate.origin)){candidate.deny.run();return;}
        pending=candidate;
        try {
            dialog=new AlertDialog.Builder(activity).setTitle("Web sayfası izin istiyor")
                .setMessage(candidate.origin+"\n"+String.join(", ",candidate.permissions)+"\nYalnız bu önplan isteğine izin verilsin mi?")
                .setPositiveButton("İzin ver",(d,w)->consent(candidate))
                .setNegativeButton("Reddet",(d,w)->finish(candidate,false))
                .setOnCancelListener(d->finish(candidate,false)).create();
            dialog.show();
        } catch(RuntimeException failure){finish(candidate,false);}
    }
    private void consent(Pending candidate) {
        if(pending!=candidate)return;
        dialog=null;
        if(!trusted.test(candidate.origin)){finish(candidate,false);return;}
        ArrayList<String> missing=new ArrayList<>();
        for(String permission:candidate.permissions)if(activity.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)missing.add(permission);
        if(missing.isEmpty()){finish(candidate,true);return;}
        systemOutstanding=true;
        try {activity.requestPermissions(missing.toArray(new String[0]),REQUEST);}
        catch(RuntimeException failure){systemOutstanding=false;finish(candidate,false);}
    }
    boolean result(int request) {
        if(request!=REQUEST)return false;
        if(!systemOutstanding)return true;
        systemOutstanding=false;
        Pending current=pending;
        if(current!=null)finish(current,true);
        return true;
    }
    private void finish(Pending candidate,boolean allowed) {
        if(pending!=candidate)return;
        pending=null;
        AlertDialog previous=dialog;dialog=null;if(previous!=null)previous.dismiss();
        if(allowed) {
            allowed=trusted.test(candidate.origin);
            for(String permission:candidate.permissions)allowed &= activity.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;
        }
        if(allowed)candidate.grant.run();else candidate.deny.run();
    }
    void canceled(PermissionRequest request) {
        if(pending!=null && pending.identity==request) {
            pending=null; AlertDialog previous=dialog;dialog=null;if(previous!=null)previous.dismiss();
        }
    }
    void cancel() {if(pending!=null)finish(pending,false);}
}
