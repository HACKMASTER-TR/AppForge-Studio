package com.appforge.htmlstub;

import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;

/** Actual parser/transport contract with an in-process HTTPS endpoint boundary. No receipt leaves the JVM. */
public class AppForgePurchaseVerifierTest {
    private static byte[] response;
    private static int status=200;
    private static String type="application/json; charset=utf-8";
    private static long length=-1;
    private static Connection last;
    private static int opens;
    private static class Connection extends HttpsURLConnection {
        final ByteArrayOutputStream body=new ByteArrayOutputStream();boolean disconnected;
        Connection(URL url){super(url);}
        @Override public void connect(){}
        @Override public boolean usingProxy(){return false;}
        @Override public void disconnect(){disconnected=true;}
        @Override public String getCipherSuite(){return "test-boundary";}
        @Override public Certificate[] getLocalCertificates(){return null;}
        @Override public Certificate[] getServerCertificates(){return new Certificate[0];}
        @Override public OutputStream getOutputStream(){return body;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(response);}
        @Override public int getResponseCode(){return status;}
        @Override public String getContentType(){return type;}
        @Override public long getContentLengthLong(){return length;}
    }
    @BeforeClass public static void transportBoundary(){URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){@Override protected URLConnection openConnection(URL url){opens++;return last=new Connection(url);}}:null);}
    private JSONObject answer() throws Exception {return new JSONObject().put("verified",true).put("packageName","com.example.billing")
        .put("productId","coins").put("purchaseTokenSha256",AppForgePurchaseVerifier.hash("unit-token-only")).put("quantity",2);}
    private boolean verify() throws Exception {return AppForgePurchaseVerifier.verify("https://verify.example/receipt","com.example.billing","coins","unit-token-only",2);}
    private void response(JSONObject json){response=json.toString().getBytes(StandardCharsets.UTF_8);}
    @Before public void reset() throws Exception {status=200;type="application/json; charset=utf-8";length=-1;response(answer());}
    @Test public void exactReceiptAndQuantityMustBeVerifiedBeforeSdkCanAcknowledge() throws Exception {
        assertTrue(verify());assertTrue(last.disconnected);assertFalse(last.getInstanceFollowRedirects());assertEquals("POST",last.getRequestMethod());
        assertEquals(7000,last.getConnectTimeout());assertEquals(7000,last.getReadTimeout());
        JSONObject sent=new JSONObject(last.body.toString("UTF-8"));assertEquals("unit-token-only",sent.getString("purchaseToken"));assertEquals(2,sent.getInt("quantity"));
        for(String key:new String[]{"packageName","productId","purchaseTokenSha256"}){response(answer().put(key,"wrong"));assertFalse(key,verify());}
        response(answer().put("verified",false));assertFalse(verify());response(answer().put("verified","true"));assertFalse(verify());
        response(answer().put("quantity",1));assertFalse(verify());response(answer().put("quantity",2.1));assertFalse(verify());
        response(answer().put("extra","ignored-by-unsafe-clients"));assertFalse(verify());
    }
    @Test public void redirectsMalformedAndOversizedRepliesNeverVerify() throws Exception {
        status=302;assertFalse(verify());status=200;type="text/plain";assertFalse(verify());type="application/json";
        length=20000;assertFalse(verify());length=-1;response=new byte[16385];assertFalse(verify());
        for(byte[] bad:new byte[][]{"not-json".getBytes(StandardCharsets.UTF_8),new byte[]{(byte)0xff}}) {
            response=bad;try{assertFalse(verify());}catch(Exception expected){}assertTrue(last.disconnected);
        }
        for(String invalid:new String[]{answer().toString()+" trailing",answer().toString().replace("true","1"),
                answer().toString().replace("\"verified\"","'verified'"),
                answer().toString().replace("\"verified\"", "\""+"\\"+"u0076erified\""),
                answer().toString().replace("\"verified\":true","\"verified\":false,\""+"\\"+"u0076erified\":true")}) {
            response=invalid.getBytes(StandardCharsets.UTF_8);try{assertFalse(invalid,verify());}catch(Exception expected){}
        }
        String duplicate=answer().toString().replace("\"verified\":true","\"verified\":false,\"verified\":true");
        response=duplicate.getBytes(StandardCharsets.UTF_8);try{assertFalse(verify());}catch(Exception expected){}
    }
    @Test public void unsafeEndpointAndIdentityAreRejectedBeforeAnyConnection() throws Exception {
        int before=opens;
        for(String endpoint:new String[]{"http://verify.example/p","https://user:pass@verify.example/p","https://verify.example:8443/p","https://verify.example/p#fragment","https://verify.example/p?token=not-for-assets","file:///private"})
            assertThrows(Exception.class,()->AppForgePurchaseVerifier.verify(endpoint,"com.example.billing","coins","unit-token-only",2));
        assertThrows(Exception.class,()->AppForgePurchaseVerifier.verify("https://verify.example/p","bad package","coins","token",1));
        assertThrows(Exception.class,()->AppForgePurchaseVerifier.verify("https://verify.example/p","com.example.billing","BAD product","token",1));
        assertThrows(Exception.class,()->AppForgePurchaseVerifier.verify("https://verify.example/p","com.example.billing","coins","",1));
        assertThrows(Exception.class,()->AppForgePurchaseVerifier.verify("https://verify.example/p","com.example.billing","coins","token",0));
        assertEquals(before,opens);
    }
    @Test public void productConfigRejectsMissingDuplicateAndMisclassifiedProducts() throws Exception {
        JSONObject base=new JSONObject().put("billingProductIds","coins,premium").put("billingSubscriptionIds","monthly").put("consumableProductIds","coins")
            .put("removeAdsProductId","premium").put("purchaseVerificationUrl","https://verify.example/p");
        AppForgeBillingConfig config=new AppForgeBillingConfig(base);assertTrue(config.consumables.contains("coins"));assertTrue(config.subscriptions.contains("monthly"));
        assertThrows(IllegalArgumentException.class,()->AppForgeBillingConfig.ids("coins,coins"));assertThrows(IllegalArgumentException.class,()->AppForgeBillingConfig.ids("BAD product"));
        base.put("removeAdsProductId","coins");assertThrows(IllegalArgumentException.class,()->new AppForgeBillingConfig(base));
        base.put("removeAdsProductId","").put("consumableProductIds","monthly");assertThrows(IllegalArgumentException.class,()->new AppForgeBillingConfig(base));
        base.put("consumableProductIds","").put("billingSubscriptionIds","coins");assertThrows(IllegalArgumentException.class,()->new AppForgeBillingConfig(base));
    }
}
