package com.appforge.htmlstub;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Foreground connectivity observation. Never treats a missing permission/service as a successful connection. */
final class AppForgeConnectivity implements AutoCloseable {
    interface Sink { void changed(boolean online, boolean restored); default void unavailable() {} }
    private final Activity activity;
    private final ConnectivityManager manager;
    private final Sink sink;
    private ConnectivityManager.NetworkCallback callback;
    private boolean started, known, last;
    private int generation;
    AppForgeConnectivity(Activity activity, Sink sink) {
        this.activity=activity; this.sink=sink;
        manager=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager==null)throw new IllegalStateException("Connectivity service unavailable");
    }
    boolean online() {
        Network network=manager.getActiveNetwork();
        NetworkCapabilities capabilities=network==null?null:manager.getNetworkCapabilities(network);
        return capabilities!=null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    private void publish(int expected) {
        activity.runOnUiThread(()->{
            if(!started || generation!=expected)return;
            final boolean current;
            try { current=online(); }
            catch(RuntimeException failure) { close(); sink.unavailable(); return; }
            if(known && current==last)return;
            boolean restored=known && !last && current;
            last=current; known=true;
            sink.changed(current,restored);
        });
    }
    void start() {
        if(started)return;
        final int current=++generation; started=true;
        callback=new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network){publish(current);}
            @Override public void onLost(Network network){publish(current);}
            @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){publish(current);}
        };
        try {manager.registerDefaultNetworkCallback(callback); publish(current);}
        catch(RuntimeException failure){close();throw failure;}
    }
    @Override public void close() {
        started=false; generation++;
        if(callback!=null) {
            try {manager.unregisterNetworkCallback(callback);} catch(IllegalArgumentException | SecurityException ignored){}
            callback=null;
        }
    }
}
