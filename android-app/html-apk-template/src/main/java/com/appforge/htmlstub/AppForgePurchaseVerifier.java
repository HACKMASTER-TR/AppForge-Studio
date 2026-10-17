package com.appforge.htmlstub;

import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** App backend must verify against Google Play and bind its response to this exact receipt. */
final class AppForgePurchaseVerifier {
    static String hash(String value) throws Exception {
        StringBuilder out=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))) {
            out.append(Character.forDigit((b>>4)&15,16));out.append(Character.forDigit(b&15,16));
        }
        return out.toString();
    }
    static boolean verify(String endpoint,String packageName,String product,String token,int quantity) throws Exception {
        AppForgeBillingConfig.endpoint(endpoint);
        if(packageName==null || !packageName.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+") ||
            product==null || !product.matches("[a-z0-9][a-z0-9._-]{0,127}") || token==null || token.length()<1 || token.length()>8192 || quantity<1 || quantity>100)
            throw new IllegalArgumentException("Invalid purchase identity");
        byte[] body=new JSONObject().put("packageName",packageName).put("productId",product).put("purchaseToken",token).put("quantity",quantity).toString().getBytes(StandardCharsets.UTF_8);
        if(body.length>32768)throw new IllegalArgumentException("Verification payload exceeds budget");
        HttpsURLConnection connection=(HttpsURLConnection)new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(7000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Accept","application/json");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=connection.getOutputStream()){out.write(body);}
            if(connection.getResponseCode()!=200)return false;
            String type=connection.getContentType();
            if(type==null || !"application/json".equalsIgnoreCase(type.split(";",2)[0].trim()) || connection.getContentLengthLong()>16384)return false;
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(buffer.size()+n>16384)return false;buffer.write(b,0,n);}
            }
            String response=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(buffer.toByteArray())).toString();
            // Android JSONObject is intentionally lenient (duplicate escaped keys, trailing text,
            // single quotes). This protocol only needs five flat, ASCII receipt identity fields.
            String pair="\"[A-Za-z0-9]+\"\\s*:\\s*(?:true|false|[0-9]+|\"[A-Za-z0-9_.-]*\")";
            if(!response.matches("\\s*\\{\\s*"+pair+"(?:\\s*,\\s*"+pair+"){4}\\s*\\}\\s*"))return false;
            JSONObject answer=new JSONObject(response);
            if(answer.length()!=5)return false;
            for(String key:new String[]{"verified","packageName","productId","purchaseTokenSha256","quantity"}) {
                java.util.regex.Matcher fields=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote("\""+key+"\"")+"\\s*:").matcher(response);
                int count=0;while(fields.find())count++;if(count!=1)return false;
            }
            return Boolean.TRUE.equals(answer.opt("verified")) && packageName.equals(answer.opt("packageName")) &&
                product.equals(answer.opt("productId")) && hash(token).equals(answer.opt("purchaseTokenSha256")) && answer.opt("quantity") instanceof Number &&
                ((Number)answer.opt("quantity")).doubleValue()==quantity;
        }finally{connection.disconnect();}
    }
}
