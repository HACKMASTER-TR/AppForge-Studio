package com.appforge.htmlstub;

import android.app.Activity;
import android.app.Dialog;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.LinearLayout;
import android.widget.Button;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** User-started foreground camera scanner with packaged ZXing. No external scanner/model/app required. */
@SuppressWarnings("deprecation")
final class AppForgeQrScanner implements AutoCloseable {
    private final Activity activity;
    private final BooleanSupplier trusted;
    private final Consumer<String> result;
    private final Consumer<String> failure;
    private Dialog dialog;
    private Camera camera;
    private java.util.concurrent.ExecutorService decoder;
    private boolean decoding;
    private int generation;
    AppForgeQrScanner(Activity activity,BooleanSupplier trusted,Consumer<String> result,Consumer<String> failure) {
        this.activity=activity;this.trusted=trusted;this.result=result;this.failure=failure;
    }
    String start() {
        if(!trusted.getAsBoolean())throw new SecurityException("Trusted foreground required");
        if(dialog!=null)throw new IllegalStateException("Scanner already active");
        if(activity.checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Camera permission required");
        final int current=++generation;
        try {
            decoder=java.util.concurrent.Executors.newSingleThreadExecutor();
            SurfaceView surface=new SurfaceView(activity);
            Button cancel=new Button(activity);cancel.setText("Taramayı kapat");cancel.setOnClickListener(v->close());
            LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(surface,new LinearLayout.LayoutParams(-1,0,1));layout.addView(cancel,new LinearLayout.LayoutParams(-1,-2));
            dialog=new Dialog(activity);dialog.setTitle("QR / Barkod");dialog.setContentView(layout);
            dialog.setOnDismissListener(d->close());
            surface.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override public void surfaceCreated(SurfaceHolder holder){open(current,holder);}
                @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
                @Override public void surfaceDestroyed(SurfaceHolder holder){close();}
            });
            dialog.show();
            dialog.getWindow().setLayout(-1,-1);
            activity.getWindow().getDecorView().postDelayed(()->{if(generation==current && dialog!=null){close();failure.accept("Tarama zaman aşımına uğradı.");}},120000);
            return "SCANNING";
        } catch(RuntimeException error){close();throw error;}
    }
    private void open(int current,SurfaceHolder holder) {
        if(generation!=current || dialog==null || !trusted.getAsBoolean()){close();return;}
        try {
            int selected=-1;
            for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){selected=i;break;}}
            if(selected<0)throw new IllegalStateException("Rear camera unavailable");
            camera=Camera.open(selected);
            Camera.Parameters parameters=camera.getParameters();
            Camera.Size chosen=null;
            for(Camera.Size size:parameters.getSupportedPreviewSizes())if(size.width<=1920&&size.height<=1920&&
                size.width>=16&&size.height>=16&&(long)size.width*size.height<=1920L*1080&&
                (chosen==null || (long)size.width*size.height>(long)chosen.width*chosen.height))chosen=size;
            if(chosen==null)throw new IllegalStateException("Bounded camera preview unavailable");
            parameters.setPreviewSize(chosen.width,chosen.height);
            parameters.setPreviewFormat(android.graphics.ImageFormat.NV21);
            if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);
            Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(selected,info);
            int degrees=activity.getWindowManager().getDefaultDisplay().getRotation()*90;
            camera.setDisplayOrientation((info.orientation-degrees+360)%360);
            camera.setPreviewDisplay(holder);
            final int width=chosen.width,height=chosen.height;
            camera.setPreviewCallback((data,owner)->{
                if(decoding || current!=generation || decoder==null || data==null || data.length<width*height || !trusted.getAsBoolean())return;
                decoding=true;final byte[] frame=java.util.Arrays.copyOf(data,width*height);
                try {decoder.execute(()->{
                    final String text=AppForgeBarcode.decode(frame,width,height);
                    activity.runOnUiThread(()->{
                        if(current!=generation)return;
                        decoding=false;
                        if(text!=null && trusted.getAsBoolean()){close();result.accept(text);}
                    });
                });}catch(java.util.concurrent.RejectedExecutionException stopped){decoding=false;}
            });
            camera.startPreview();
        } catch(Exception error){close();failure.accept("Kamera taraması başlatılamadı.");}
    }
    @Override public void close() {
        generation++;decoding=false;
        Camera previous=camera;camera=null;
        if(previous!=null){try{previous.setPreviewCallback(null);previous.stopPreview();}catch(RuntimeException ignored){}finally{try{previous.release();}catch(RuntimeException ignored){}}}
        java.util.concurrent.ExecutorService executor=decoder;decoder=null;if(executor!=null)executor.shutdownNow();
        Dialog previousDialog=dialog;dialog=null;if(previousDialog!=null)previousDialog.dismiss();
    }
}
