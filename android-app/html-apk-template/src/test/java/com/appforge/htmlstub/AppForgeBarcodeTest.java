package com.appforge.htmlstub;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppForgeBarcodeTest {
    private byte[] frame(String value,BarcodeFormat format,int width,int height) throws Exception {
        BitMatrix matrix=new MultiFormatWriter().encode(value,format,width,height,java.util.Collections.singletonMap(EncodeHintType.CHARACTER_SET,"UTF-8"));
        byte[] bytes=new byte[width*height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)bytes[y*width+x]=(byte)(matrix.get(x,y)?0:255);
        return bytes;
    }
    @Test public void actualQrAndBarcodesDecodeWithoutNetworkOrExternalApp() throws Exception {
        assertEquals("offline QR — Türkçe",AppForgeBarcode.decode(frame("offline QR — Türkçe",BarcodeFormat.QR_CODE,240,240),240,240));
        assertEquals("5901234123457",AppForgeBarcode.decode(frame("5901234123457",BarcodeFormat.EAN_13,640,160),640,160));
        byte[] code=frame("APPFORGE-42",BarcodeFormat.CODE_128,480,120);
        assertEquals("APPFORGE-42",AppForgeBarcode.decode(code,480,120));
        byte[] rotated=new byte[code.length];for(int y=0;y<120;y++)for(int x=0;x<480;x++)rotated[x*120+119-y]=code[y*480+x];
        assertEquals("APPFORGE-42",AppForgeBarcode.decode(rotated,120,480));
    }
    @Test public void malformedAndBlankFramesNeverReportSuccess() {
        assertNull(AppForgeBarcode.decode(null,100,100));assertNull(AppForgeBarcode.decode(new byte[9],100,100));
        assertNull(AppForgeBarcode.decode(new byte[16*16],16,16));
        assertNull(AppForgeBarcode.decode(new byte[1],Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertNull(AppForgeBarcode.decode(new byte[1920*1920],1920,1920));
    }
}
