package com.appforge.htmlstub;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Foreground, one-tag ID reader. No NDEF parsing, tag writes, payments or background dispatch. */
final class AppForgeNfc implements AutoCloseable {
    private final Activity activity;
    private final BooleanSupplier trusted;
    private final Consumer<String> sink;
    private NfcAdapter adapter;
    private int generation;
    private boolean reading;
    AppForgeNfc(Activity activity,BooleanSupplier trusted,Consumer<String> sink) {
        this.activity=activity;this.trusted=trusted;this.sink=sink;
    }
    String start() {
        if(!trusted.getAsBoolean())throw new SecurityException("Trusted foreground tap required");
        if(reading)throw new IllegalStateException("NFC scan already active");
        NfcAdapter candidate=NfcAdapter.getDefaultAdapter(activity);
        if(candidate==null || !candidate.isEnabled())throw new IllegalStateException("NFC unavailable or disabled");
        final int current=++generation;
        adapter=candidate;reading=true;
        try {
            adapter.enableReaderMode(activity,tag->activity.runOnUiThread(()->received(current,tag)),
                NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|
                NfcAdapter.FLAG_READER_NFC_V|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,null);
            activity.getWindow().getDecorView().postDelayed(()->{if(generation==current)close();},30000);
            return "LISTENING"; // No tag read is claimed until the separate result event.
        } catch(RuntimeException failure){close();throw failure;}
    }
    private void received(int current,Tag tag) {
        if(!reading || current!=generation)return;
        boolean allowed=trusted.getAsBoolean();
        byte[] id=tag==null?null:tag.getId();
        close();
        if(!allowed || id==null || id.length<1 || id.length>32)return;
        StringBuilder hex=new StringBuilder();
        for(byte value:id){hex.append(Character.forDigit((value>>4)&15,16));hex.append(Character.forDigit(value&15,16));}
        sink.accept(hex.toString());
    }
    @Override public void close() {
        generation++;reading=false;
        if(adapter!=null) {
            NfcAdapter previous=adapter;adapter=null;
            try {previous.disableReaderMode(activity);} catch(IllegalStateException | SecurityException ignored) {}
        }
    }
}
