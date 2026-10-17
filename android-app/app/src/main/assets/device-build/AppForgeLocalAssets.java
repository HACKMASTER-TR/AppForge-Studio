package com.appforge.runtime;

import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;

final class AppForgeLocalAssets {

    static final String START_URL =
        "https://appassets.androidplatform.net/assets/site/index.html";

    private static final String PREFIX =
        "/assets/site/";

    private final AssetManager assets;
    private final String documentBootstrap;

    AppForgeLocalAssets(AssetManager assets) { this(assets, null); }
    AppForgeLocalAssets(AssetManager assets, String documentBootstrap) {
        this.assets = assets;
        this.documentBootstrap = documentBootstrap;
    }

    private static boolean isHost(Uri uri) {
        return uri != null
            && "https".equalsIgnoreCase(uri.getScheme())
            && "appassets.androidplatform.net"
                .equalsIgnoreCase(uri.getHost())
            && uri.getPort() == -1
            && uri.getUserInfo() == null;
    }

    static boolean isLocalOrigin(String origin) {
        try {
            return isHost(Uri.parse(origin));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isLocalPage(String url) {
        try {
            Uri uri = Uri.parse(url);

            return isHost(uri)
                && uri.getEncodedPath() != null
                && uri.getEncodedPath().startsWith(PREFIX);

        } catch (RuntimeException ignored) {
            return false;
        }
    }

    WebResourceResponse intercept(
        WebResourceRequest request
    ) {
        if (
            request == null ||
            !isHost(request.getUrl())
        ) {
            return null;
        }

        if (!"GET".equalsIgnoreCase(
            request.getMethod()
        )) {
            return notFound();
        }

        String encoded =
            request.getUrl().getEncodedPath();

        if (
            encoded == null ||
            !encoded.startsWith(PREFIX)
        ) {
            return notFound();
        }

        String lower =
            encoded.toLowerCase(Locale.ROOT);

        if (
            lower.contains("%2f") ||
            lower.contains("%5c")
        ) {
            return notFound();
        }

        String relative =
            Uri.decode(
                encoded.substring(PREFIX.length())
            );

        if (
            relative.isEmpty() ||
            relative.length() > 2048 ||
            relative.indexOf('\\') >= 0 ||
            relative.indexOf('\0') >= 0
        ) {
            return notFound();
        }

        for (
            String segment :
            relative.split("/", -1)
        ) {
            if (
                segment.isEmpty() ||
                ".".equals(segment) ||
                "..".equals(segment)
            ) {
                return notFound();
            }
        }

        try {
            InputStream body =
                assets.open("site/" + relative);

            String mime =
                mimeType(relative);

            if (request.isForMainFrame() && "text/html".equals(mime) && documentBootstrap != null) {
                body = withBootstrap(body, documentBootstrap);
            }

            String encoding =
                mime.startsWith("text/") ||
                mime.contains("javascript") ||
                mime.contains("json") ||
                mime.contains("svg")
                    ? "UTF-8"
                    : null;

            return new WebResourceResponse(
                mime,
                encoding,
                body
            );

        } catch (IOException ignored) {
            return notFound();
        }
    }

    // Insert ASCII script before application code without re-encoding project HTML or
    // changing its doctype. The project on disk is never modified.
    private static InputStream withBootstrap(InputStream body, String javascript) throws IOException {
        byte[] prefix = new byte[1024];
        int used=0, count;
        try {
            while (used<prefix.length && (count=body.read(prefix,used,prefix.length-used))>0) used+=count;
            String text=new String(prefix,0,used,java.nio.charset.StandardCharsets.ISO_8859_1);
            java.util.regex.Matcher doctype=java.util.regex.Pattern.compile("(?is)^(?:\\xEF\\xBB\\xBF)?\\s*<!doctype[^>]*>").matcher(text);
            int at=doctype.find()?doctype.end():0;
            java.io.ByteArrayOutputStream first=new java.io.ByteArrayOutputStream();
            first.write(prefix,0,at);
            first.write(("<script>"+javascript.replace("</script", "<\\/script")+"</script>").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            first.write(prefix,at,used-at);
            return new java.io.SequenceInputStream(new ByteArrayInputStream(first.toByteArray()),body);
        } catch(IOException error){body.close();throw error;}
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse(
            "text/plain",
            "UTF-8",
            404,
            "Not Found",
            Collections.emptyMap(),
            new ByteArrayInputStream(new byte[0])
        );
    }

    private static String mimeType(String file) {
        String name =
            file.toLowerCase(Locale.ROOT);

        if (name.endsWith(".html") ||
            name.endsWith(".htm"))
            return "text/html";

        if (name.endsWith(".js") ||
            name.endsWith(".mjs"))
            return "text/javascript";

        if (name.endsWith(".css"))
            return "text/css";

        if (name.endsWith(".json") ||
            name.endsWith(".map"))
            return "application/json";

        if (name.endsWith(".svg"))
            return "image/svg+xml";

        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".m4a")) return "audio/mp4";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".csv")) return "text/csv";

        if (name.endsWith(".wasm"))
            return "application/wasm";

        if (name.endsWith(".png"))
            return "image/png";

        if (name.endsWith(".jpg") ||
            name.endsWith(".jpeg"))
            return "image/jpeg";

        if (name.endsWith(".gif"))
            return "image/gif";

        if (name.endsWith(".webp"))
            return "image/webp";

        if (name.endsWith(".ico"))
            return "image/x-icon";

        if (name.endsWith(".woff2"))
            return "font/woff2";

        if (name.endsWith(".woff"))
            return "font/woff";

        if (name.endsWith(".ttf"))
            return "font/ttf";

        if (name.endsWith(".otf"))
            return "font/otf";

        if (name.endsWith(".txt"))
            return "text/plain";

        return "application/octet-stream";
    }
}
