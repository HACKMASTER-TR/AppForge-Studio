package com.appforge.htmlstub;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.EnumMap;
import java.util.Arrays;

/** Bounded luminance decoder; accepts camera luma only, never opens URLs from decoded content. */
final class AppForgeBarcode {
    static String decode(byte[] frame,int width,int height) {
        if(frame==null || width<16 || height<16 || width>1920 || height>1920 ||
            (long)width*height>1920L*1080 || frame.length<(long)width*height)return null;
        EnumMap<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,Arrays.asList(BarcodeFormat.QR_CODE,BarcodeFormat.EAN_13,BarcodeFormat.EAN_8,
            BarcodeFormat.CODE_128,BarcodeFormat.CODE_39,BarcodeFormat.UPC_A,BarcodeFormat.UPC_E,BarcodeFormat.ITF,BarcodeFormat.DATA_MATRIX));
        hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
        MultiFormatReader reader=new MultiFormatReader();
        try {
            for(int rotation=0;rotation<2;rotation++) {
                byte[] data=frame;int w=width,h=height;
                if(rotation==1){data=new byte[width*height];w=height;h=width;for(int y=0;y<height;y++)for(int x=0;x<width;x++)data[x*height+(height-1-y)]=frame[y*width+x];}
                LuminanceSource source=new PlanarYUVLuminanceSource(data,w,h,0,0,w,h,false);
                try {
                    String text=reader.decode(new BinaryBitmap(new HybridBinarizer(source)),hints).getText();
                    if(text!=null && text.length()>0 && text.length()<=4096)return text;
                } catch(ReaderException absent) { } finally {reader.reset();}
            }
            return null;
        } catch(RuntimeException malformed){return null;}
    }
}
