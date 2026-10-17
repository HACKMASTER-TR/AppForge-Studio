package com.appforge.htmlstub;

/** Validates exported entry data; the result is an app event, never a WebView navigation. */
final class AppForgeDeepLink {
    static String accept(String value,String scheme,String host,String prefix) {
        try {
            if(value==null || value.length()>2048 || prefix==null || !prefix.startsWith("/"))return null;
            java.net.URI uri=new java.net.URI(value);
            if(!uri.isAbsolute() || uri.isOpaque() || uri.getRawUserInfo()!=null || uri.getPort()!=-1 ||
                uri.getScheme()==null || !uri.getScheme().equalsIgnoreCase(scheme) ||
                uri.getHost()==null || !uri.getHost().equalsIgnoreCase(host))return null;
            if(java.util.Arrays.asList("file","javascript","data","content","intent").contains(uri.getScheme().toLowerCase(java.util.Locale.ROOT)))return null;
            String path=uri.getPath();
            if(path==null || !path.startsWith(prefix) || path.contains("\\") ||
                path.codePoints().anyMatch(c->Character.isISOControl(c)))return null;
            for(String part:path.split("/"))if(".".equals(part)||"..".equals(part))return null;
            return value;
        } catch(Exception invalid){return null;}
    }
}
