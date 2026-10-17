package com.appforge.htmlstub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import org.json.JSONObject;
import java.util.function.BooleanSupplier;

/** One-shot foreground location for the existing local file origin, via the gated document channel. */
final class AppForgeLocation implements AutoCloseable {
    static final int REQUEST=7206;
    interface Sink {void result(JSONObject value);}
    private final Activity activity;
    private final BooleanSupplier trusted;
    private final Sink sink;
    private final LocationManager manager;
    private AlertDialog dialog;
    private LocationListener listener;
    private String pending;
    private boolean systemOutstanding;
    private int generation;
    AppForgeLocation(Activity activity,BooleanSupplier trusted,Sink sink,boolean outstanding) {
        this.activity=activity;this.trusted=trusted;this.sink=sink;systemOutstanding=outstanding;
        manager=(LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
    }
    boolean outstanding(){return systemOutstanding;}
    String request(String id) {
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid location request ID");
        if(!trusted.getAsBoolean())throw new SecurityException("Trusted foreground required");
        if(pending!=null || systemOutstanding)throw new IllegalStateException("Location request already active");
        if(manager==null)throw new IllegalStateException("Location service unavailable");
        pending=id;final int current=++generation;
        try {
            dialog=new AlertDialog.Builder(activity).setTitle("Konum isteği")
                .setMessage("Bu yerel sayfa için tek seferlik konum alınsın mı? Android yaklaşık veya hassas konum onayını ayrıca ister. Arka planda izleme yapılmaz.")
                .setPositiveButton("İzin ver",(d,w)->consent(current))
                .setNegativeButton("Reddet",(d,w)->error(1,"Konum onayı reddedildi."))
                .setOnCancelListener(d->error(1,"Konum isteği iptal edildi.")).create();
            dialog.show();
            return "PENDING";
        }catch(RuntimeException failure){close();throw failure;}
    }
    private boolean granted(String permission){return activity.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private void consent(int current) {
        if(pending==null || current!=generation)return;dialog=null;
        if(!trusted.getAsBoolean()){error(1,"Sayfa artık önplanda değil.");return;}
        if(!granted(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
            systemOutstanding=true;
            try {activity.requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION},REQUEST);}
            catch(RuntimeException denied){systemOutstanding=false;error(1,"Android konum izni istenemedi.");}
        } else locate(current);
    }
    boolean permissionsResult(int request) {
        if(request!=REQUEST)return false;
        if(!systemOutstanding)return true;systemOutstanding=false;
        if(pending!=null) {
            if(!granted(android.Manifest.permission.ACCESS_COARSE_LOCATION))error(1,"Android konum izni reddedildi.");
            else locate(generation);
        }
        return true;
    }
    private void locate(int current) {
        if(pending==null || !trusted.getAsBoolean()){error(1,"Konum isteği iptal edildi.");return;}
        try {
            String provider;
            if(granted(android.Manifest.permission.ACCESS_FINE_LOCATION) && manager.isProviderEnabled(LocationManager.GPS_PROVIDER))provider=LocationManager.GPS_PROVIDER;
            else if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))provider=LocationManager.NETWORK_PROVIDER;
            else {error(2,"Konum sağlayıcısı kapalı veya kullanılamıyor.");return;}
            listener=new LocationListener(){
                @Override public void onLocationChanged(Location value){if(current==generation)received(value);}
                @Override public void onProviderDisabled(String name){if(current==generation)error(2,"Konum sağlayıcısı kapatıldı.");}
                @Override public void onProviderEnabled(String name){}
                @Override public void onStatusChanged(String name,int status,Bundle extras){}
            };
            manager.requestLocationUpdates(provider,0,0,listener,Looper.getMainLooper());
            activity.getWindow().getDecorView().postDelayed(()->{if(current==generation)error(3,"Konum zaman aşımı; cihaz konumunu ve sağlayıcı bağlantısını kontrol edin.");},30000);
        }catch(SecurityException denied){error(1,"Android konum izni yok.");}
        catch(RuntimeException unavailable){error(2,"Konum sağlayıcısı kullanılamıyor.");}
    }
    private void received(Location value) {
        if(pending==null || !trusted.getAsBoolean()){close();return;}
        if(value==null)return;
        long age=SystemClock.elapsedRealtimeNanos()-value.getElapsedRealtimeNanos();
        double latitude=value.getLatitude(),longitude=value.getLongitude(),accuracy=value.getAccuracy();
        if(age<0 || age>30000000000L || value.getTime()<=0 || !Double.isFinite(latitude) || Math.abs(latitude)>90 ||
            !Double.isFinite(longitude) || Math.abs(longitude)>180 || !value.hasAccuracy() || !Double.isFinite(accuracy) || accuracy<0)return;
        try {
            JSONObject coords=new JSONObject().put("latitude",latitude).put("longitude",longitude).put("accuracy",accuracy)
                .put("altitude",value.hasAltitude() && Double.isFinite(value.getAltitude())?value.getAltitude():JSONObject.NULL)
                .put("altitudeAccuracy",JSONObject.NULL).put("heading",JSONObject.NULL)
                .put("speed",value.hasSpeed() && Float.isFinite(value.getSpeed()) && value.getSpeed()>=0?value.getSpeed():JSONObject.NULL);
            JSONObject result=new JSONObject().put("id",pending).put("ok",true)
                .put("position",new JSONObject().put("coords",coords).put("timestamp",value.getTime()));
            close();sink.result(result);
        }catch(Exception invalid){error(2,"Konum sonucu okunamadı.");}
    }
    private void error(int code,String message) {
        if(pending==null)return;
        try {
            JSONObject result=new JSONObject().put("id",pending).put("ok",false)
                .put("error",new JSONObject().put("code",code).put("message",message));
            close();sink.result(result);
        }catch(Exception ignored){close();}
    }
    @Override public void close() {
        pending=null;generation++;
        if(dialog!=null){AlertDialog previous=dialog;dialog=null;previous.dismiss();}
        if(listener!=null){try{manager.removeUpdates(listener);}catch(RuntimeException ignored){}listener=null;}
    }
    void cancel(){error(1,"Konum isteği sayfa veya yaşam döngüsü değişikliğiyle iptal edildi.");}
}
