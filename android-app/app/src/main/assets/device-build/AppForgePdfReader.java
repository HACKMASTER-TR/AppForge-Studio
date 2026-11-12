package com.appforge.runtime;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Local Android renderer. No network, JavaScript, external viewer or writable source URI. */
final class AppForgePdfReader {
    private final Activity activity;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ImageView image;
    private final TextView status;
    private final EditText pageInput;
    private final Button previous,next,jump,smaller,larger;
    private volatile boolean closed;
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private Bitmap displayed;
    private String identity;
    private int page,pages;
    private float zoom=1;
    private final boolean zoomEnabled;
    private File temporary;

    AppForgePdfReader(Activity activity,JSONObject config) {
        this.activity=activity;zoomEnabled=config.optBoolean("webZoomEnabled",true);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        try { root.setBackgroundColor(Color.parseColor(config.optString("backgroundColor","#ffffff"))); }
        catch(IllegalArgumentException ignored){root.setBackgroundColor(Color.WHITE);}
        status=new TextView(activity);status.setText("PDF açılıyor…");root.addView(status);
        LinearLayout controls=new LinearLayout(activity);
        previous=button(controls,"‹",()->render(page-1));next=button(controls,"›",()->render(page+1));
        pageInput=new EditText(activity);pageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        pageInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        pageInput.setContentDescription("Sayfa numarası");controls.addView(pageInput,new LinearLayout.LayoutParams(0,-2,1));
        jump=button(controls,"Git",()->{
            try {int requested=Integer.parseInt(pageInput.getText().toString());
                if(requested<1 || requested>pages)throw new NumberFormatException();render(requested-1);
            } catch(NumberFormatException error){status.setText("1–"+pages+" arasında bir sayfa seçin.");}
        });
        smaller=button(controls,"−",()->{zoom=Math.max(.5f,zoom-.25f);render(page);});
        larger=button(controls,"+",()->{zoom=Math.min(3,zoom+.25f);render(page);});
        root.addView(controls);
        image=new ImageView(activity);image.setAdjustViewBounds(true);image.setContentDescription("PDF sayfası");
        ScrollView vertical=new ScrollView(activity);HorizontalScrollView horizontal=new HorizontalScrollView(activity);
        horizontal.addView(image);vertical.addView(horizontal);root.addView(vertical,new LinearLayout.LayoutParams(-1,0,1));
        activity.setContentView(root);enabled(false);
        worker.execute(this::open);
    }
    private Button button(LinearLayout parent,String text,Runnable action) {
        Button b=new Button(activity);b.setText(text);b.setOnClickListener(v->action.run());parent.addView(b);return b;
    }
    private void enabled(boolean value) {
        previous.setEnabled(value && page>0);next.setEnabled(value && page+1<pages);jump.setEnabled(value);
        smaller.setEnabled(value && zoomEnabled && zoom>.5f);larger.setEnabled(value && zoomEnabled && zoom<3);
    }
    private void open() {
        try {
            File cache=new File(activity.getCacheDir(),"appforge-pdf");
            if(!cache.mkdirs() && !cache.isDirectory())throw new IOException();
            long total=0;File[] existing=cache.listFiles();if(existing==null)throw new IOException();
            for(File file:existing){
                if(System.currentTimeMillis()-file.lastModified()>24L*60*60*1000 && file.delete())continue;
                total+=file.length();
            }
            if(total>32L*1024*1024)throw new IOException();
            temporary=File.createTempFile("document-",".pdf",cache);
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=activity.getAssets().open("site/appforge-document.pdf");OutputStream out=new FileOutputStream(temporary)) {
                byte[] buffer=new byte[8192];int count;long size=0;
                while((count=in.read(buffer))!=-1){size+=count;if(size>32L*1024*1024)throw new IOException();sha.update(buffer,0,count);out.write(buffer,0,count);}
                if(size<5)throw new IOException();
            }
            StringBuilder hex=new StringBuilder();for(byte b:sha.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));identity=hex.toString();
            try(InputStream marker=activity.getAssets().open("site/appforge-pdf.sha256")) {
                byte[] expected=new byte[65];int offset=0,count;
                while(offset<expected.length && (count=marker.read(expected,offset,expected.length-offset))>0)offset+=count;
                if(offset!=64 || !MessageDigest.isEqual(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.util.Arrays.copyOf(expected,64)))throw new IOException();
            }
            descriptor=ParcelFileDescriptor.open(temporary,ParcelFileDescriptor.MODE_READ_ONLY);
            renderer=new PdfRenderer(descriptor);pages=renderer.getPageCount();
            if(pages<1 || pages>1000)throw new IOException();
            int restored=activity.getSharedPreferences("appforge-pdf-pages",0).getInt(identity,0);
            activity.runOnUiThread(()->{if(!closed)render(Math.max(0,Math.min(pages-1,restored)));});
        } catch(SecurityException error){pages=0;error("Parola korumalı PDF açılamadı. Kilidi kaldırılmış bir belgeyle yeniden derleyin.");}
        catch(Exception error){pages=0;error("PDF açılamadı. Belge bozuk, desteklenmiyor veya depolama alanı yetersiz.");}
    }
    private void render(int requested) {
        if(closed || requested<0 || requested>=pages)return;
        enabled(false);status.setText("Sayfa hazırlanıyor…");
        final float scale=zoom;final int width=Math.max(320,activity.getResources().getDisplayMetrics().widthPixels);
        worker.execute(()->{
            Bitmap bitmap=null;
            try(PdfRenderer.Page document=renderer.openPage(requested)) {
                double factor=Math.min(width*scale/document.getWidth(),8192.0/Math.max(document.getWidth(),document.getHeight()));
                double pixels=(double)document.getWidth()*document.getHeight()*factor*factor;
                if(pixels>4*1024*1024)factor*=Math.sqrt(4*1024*1024/pixels);
                bitmap=Bitmap.createBitmap(Math.max(1,(int)(document.getWidth()*factor)),Math.max(1,(int)(document.getHeight()*factor)),Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);document.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                final Bitmap result=bitmap;
                activity.runOnUiThread(()->{
                    if(closed){result.recycle();return;}
                    Bitmap old=displayed;displayed=result;image.setImageBitmap(result);if(old!=null)old.recycle();
                    page=requested;pageInput.setText(String.valueOf(page+1));status.setText("Sayfa "+(page+1)+" / "+pages);enabled(true);
                    activity.getSharedPreferences("appforge-pdf-pages",0).edit().putInt(identity,page).apply();
                });
            } catch(OutOfMemoryError failure){if(bitmap!=null)bitmap.recycle();error("Sayfa için bellek yetersiz. Yakınlaştırmayı azaltıp yeniden deneyin.");}
            catch(Exception error){if(bitmap!=null)bitmap.recycle();error("Bu PDF sayfası işlenemedi. Başka bir sayfa deneyin.");}
        });
    }
    private void error(String message){activity.runOnUiThread(()->{if(!closed){status.setText(message);enabled(renderer!=null && pages>0);}});}
    void close() {
        closed=true;image.setImageDrawable(null);if(displayed!=null){displayed.recycle();displayed=null;}
        worker.execute(()->{
            if(renderer!=null)renderer.close();
            try {if(descriptor!=null)descriptor.close();}catch(IOException ignored){}
            if(temporary!=null)temporary.delete();
        });worker.shutdown();
    }
}
