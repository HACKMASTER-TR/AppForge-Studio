package com.appforge.htmlstub;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.os.SystemClock;
import android.webkit.WebMessage;
import android.webkit.WebMessagePort;
import android.webkit.WebView;
import android.view.MotionEvent;
import android.util.Base64;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Main-frame document capability; HTTPS ports or token-checked file transport, no URI reads. */
final class AppForgeDocuments {
    static final int REQUEST = 7198;
    static final int LIMIT = 16 * 1024 * 1024;
    static final int CHUNK = 24576;
    private final Activity activity;
    private final WebView web;
    private final boolean downloads, sharing;
    private final String origin;
    private WebMessagePort port;
    private boolean localReady;
    private long lastRequest;
    private long gestureAt, started;
    private ByteArrayOutputStream bytes;
    private int expected;
    private String name, mode, finishId, mime;
    private byte[] document;
    private volatile int generation;
    private volatile int transferVersion;
    private boolean writing;
    private boolean foreground=true;
    private volatile Runnable pendingCompletion;
    private volatile String token = java.util.UUID.randomUUID().toString();
    interface Legacy { String call(JSONObject message) throws Exception; }
    private Legacy legacy;
    private boolean legacyNativeEnabled;
    void setLegacy(Legacy legacy, boolean enabled) { this.legacy=legacy; this.legacyNativeEnabled=enabled; }

    AppForgeDocuments(Activity activity, WebView web, String origin, boolean downloads, boolean sharing) {
        this.activity=activity; this.web=web; this.origin=origin;
        this.downloads=downloads; this.sharing=sharing;
        web.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_UP) gestureAt=SystemClock.elapsedRealtime();
            return false;
        });
    }

    static String httpsOrigin(String value) {
        try {
            java.net.URI uri=new java.net.URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null ||
                uri.getUserInfo()!=null || uri.getHost().isEmpty()) return null;
            int port=uri.getPort();
            if(port < -1 || port > 65535) return null;
            return "https://"+uri.getHost().toLowerCase(Locale.ROOT)+(port==-1||port==443?"":":"+port);
        } catch(Exception e){return null;}
    }

    static boolean legacyLocalPage(String value) {
        try {
            java.net.URI uri=new java.net.URI(value);
            String path=uri.getRawPath();
            if(!"file".equals(uri.getScheme()) || uri.getRawAuthority()!=null || path==null ||
                !path.startsWith("/android_asset/site/"))return false;
            String relative=uri.getPath().substring("/android_asset/site/".length());
            if(relative.indexOf('\\')>=0 || relative.indexOf('\0')>=0)return false;
            for(String part:relative.split("/",-1))if(part.isEmpty() || part.equals(".") || part.equals(".."))return false;
            return relative.endsWith(".html") || relative.endsWith(".htm");
        } catch(Exception error){return false;}
    }
    private boolean trustedPage(String url) {
        return "legacy-file".equals(origin) ? legacyLocalPage(url) : origin!=null && origin.equals(httpsOrigin(url));
    }
    void pageStarted() { close(); token=java.util.UUID.randomUUID().toString(); }

    // Exposed only by TinyHtmlActivity. File pages cannot receive an origin-addressed port.
    // The random capability is injected solely into the trusted main frame, never subframes.
    void receiveLocal(String supplied, String raw) {
        if(!localReady || !foreground || !matchesCompatToken(supplied) || !trustedPage(web.getUrl()))return;
        receive(raw);
    }

    void pageFinished(String url) {
        if(!trustedPage(url) || !url.equals(web.getUrl()))return;
        if("legacy-file".equals(origin)) {
            if(localReady)return;
            localReady=true;
            web.evaluateJavascript(bootstrap(),ignored->{});
            return;
        }
        if (port != null) return;
        close();
        final int current=generation;
        web.evaluateJavascript(bootstrap(),ignored->{
            if(current!=generation || !url.equals(web.getUrl()))return;
            WebMessagePort[] pair=web.createWebMessageChannel();
            port=pair[0];
            port.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){
                @Override public void onMessage(WebMessagePort source,WebMessage message){
                    if(current==generation && source==port) receive(message.getData());
                }
            });
            web.postWebMessage(new WebMessage(token,new WebMessagePort[]{pair[1]}),Uri.parse(origin));
        });
    }

    boolean matchesCompatToken(String supplied) {
        return supplied != null && supplied.length() == token.length() && java.security.MessageDigest.isEqual(
            token.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            supplied.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    boolean allowsCompat(String supplied, boolean needsGesture) {
        return matchesCompatToken(supplied) && (port != null || localReady) && origin != null &&
            trustedPage(web.getUrl()) && foreground && (!needsGesture ||
                gestureAt != 0 && SystemClock.elapsedRealtime()-gestureAt<=30000);
    }

    String bootstrap() {
        return script(sharing, legacy != null).replace("__NATIVE__", Boolean.toString(legacyNativeEnabled)).replace("__DOWNLOAD__", Boolean.toString(downloads)).replace("__PORT_TOKEN__", token);
    }

    private void reply(String id,boolean ok,String result,boolean cancelled) {
        try {
            String response=new JSONObject().put("id",id).put("ok",ok)
                .put(ok?"result":"error",result).put("cancelled",cancelled).toString();
            if(port!=null)port.postMessage(new WebMessage(response));
            else if(localReady && trustedPage(web.getUrl()))web.evaluateJavascript(
                "window.__appforgeDocumentReply("+JSONObject.quote(response)+")",ignored->{});
        } catch(Exception ignored){}
    }

    private void receive(String raw) {
        String id="";
        try {
            if(!foreground || !trustedPage(web.getUrl()))throw new SecurityException("Untrusted document page");
            if(raw==null || raw.length()>34000)throw new IllegalArgumentException("Message too large");
            JSONObject message=new JSONObject(raw);id=message.getString("id");
            if(!id.matches("[0-9]{1,12}"))throw new IllegalArgumentException("Invalid request");
            long request=Long.parseLong(id);
            if(request<=lastRequest)throw new SecurityException("Duplicate or stale request");
            lastRequest=request;
            String op=message.getString("op");
            if("http".equals(op)) {
                enqueueHttp(message.getString("url"), web.getSettings().getUserAgentString(),
                    message.optString("disposition"), message.optString("mime"));
                reply(id,true,"download-enqueued",false);return;
            }
            if("legacy".equals(op)) {
                if(legacy==null || gestureAt==0 || SystemClock.elapsedRealtime()-gestureAt>30000)
                    throw new SecurityException("Native action requires a tap and project opt-in");
                reply(id,true,legacy.call(message),false);return;
            }
            if("cancel".equals(op)){ reset(); reply(id,true,"cancelled",true);return; }
            if("begin".equals(op)){
                if(bytes!=null || document!=null || writing)throw new IllegalStateException("Document request already active");
                long now=SystemClock.elapsedRealtime();
                if(gestureAt==0 || now-gestureAt>30000)throw new SecurityException("Tap required to export a document");
                gestureAt=0;
                String requested=message.getString("mode");
                if(!("save".equals(requested)&&downloads || "share".equals(requested)&&sharing))
                    throw new SecurityException("Document feature disabled");
                mime=message.getString("mime");
                if(!("application/pdf".equals(mime) || "text/csv".equals(mime) || "application/csv".equals(mime)))throw new SecurityException("PDF or CSV required");
                int size=message.getInt("size");
                if(size<("application/pdf".equals(mime)?5:1) || size>LIMIT)throw new IllegalArgumentException("Document exceeds limit");
                name=safeDocumentName(message.optString("name"),mime);mode=requested;expected=size;started=now;
                bytes=new ByteArrayOutputStream(Math.min(size,65536));
                final int current=generation;
                web.postDelayed(()->{if(current==generation && bytes!=null && SystemClock.elapsedRealtime()-started>=120000) reset();},120000);
                reply(id,true,"ready",false);return;
            }
            if(bytes==null || SystemClock.elapsedRealtime()-started>120000)throw new IllegalStateException("No active document transfer");
            if("chunk".equals(op)){
                if(message.getInt("offset")!=bytes.size())throw new IllegalArgumentException("Chunk out of order");
                byte[] part=decodeChunk(message.getString("data"));
                if(bytes.size()+part.length>expected)throw new IllegalArgumentException("Document overflow");
                bytes.write(part,0,part.length);reply(id,true,"chunk",false);return;
            }
            if(!"finish".equals(op))throw new IllegalArgumentException("Unknown document operation");
            document=bytes.toByteArray();
            if(document.length!=expected || !("application/pdf".equals(mime)?isPdf(document):isCsv(document)))throw new IllegalArgumentException("Invalid PDF or UTF-8 CSV bytes");
            bytes=null;finishId=id;
            if ("share".equals(mode)) {
                writing=true;
                final byte[] payload=document;
                final String filename=name, contentType=mime, requestId=id;
                final int current=generation, transfer=transferVersion;
                new Thread(()->{
                    Uri shared=null; String failure=null;
                    try { shared=AppForgeShareProvider.create(activity,payload,filename,contentType); }
                    catch(Exception error){failure="Share cache unavailable";}
                    final Uri uri=shared; final String shareFailure=failure;
                    activity.runOnUiThread(()->{
                        if(current!=generation || transfer!=transferVersion){writing=false;return;}
                        Runnable complete=()->{
                            writing=false;
                            if(current!=generation || transfer!=transferVersion)return;
                            try {
                                if(shareFailure!=null)throw new java.io.IOException(shareFailure);
                                Intent send=new Intent(Intent.ACTION_SEND).setType("application/csv".equals(contentType)?"text/csv":contentType)
                                    .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                send.setClipData(ClipData.newRawUri(filename,uri));
                                activity.startActivity(Intent.createChooser(send,"Paylaş"));
                                reply(requestId,true,"chooser-launched",false);
                            } catch(Exception error){reply(requestId,false,"Share chooser unavailable",false);}
                            finally {reset();}
                        };
                        if(foreground)complete.run();else pendingCompletion=complete;
                    });
                },"AppForge-document-share").start();
                return;
            }
            // User selects a writable content destination. Only a system document destination is accepted.
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime).putExtra(Intent.EXTRA_TITLE,name);
            activity.startActivityForResult(intent,REQUEST);
        } catch(Exception error){reply(id,false,error.getMessage()==null?"Document export failed":error.getMessage(),false);reset();}
    }

    void enqueueHttp(String url, String userAgent, String disposition, String mime) {
        if(!allowHttpDownload())throw new SecurityException("Download requires trusted page and tap");
        Uri uri=Uri.parse(url);
        if(!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) ||
            uri.getHost()==null || uri.getUserInfo()!=null)throw new SecurityException("Invalid download URL");
        android.app.DownloadManager.Request request=new android.app.DownloadManager.Request(uri);
        String filename=android.webkit.URLUtil.guessFileName(url, disposition, mime);
        filename=safeDownloadName(filename);
        request.setTitle(filename);
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        if(mime!=null && !mime.isEmpty())request.setMimeType(mime);
        String cookies=android.webkit.CookieManager.getInstance().getCookie(url);
        if(cookies!=null && !cookies.isEmpty())request.addRequestHeader("Cookie",cookies);
        if(userAgent!=null && !userAgent.isEmpty())request.addRequestHeader("User-Agent",userAgent);
        if(android.os.Build.VERSION.SDK_INT>=29)request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,filename);
        else request.setDestinationInExternalFilesDir(activity,android.os.Environment.DIRECTORY_DOWNLOADS,filename);
        android.app.DownloadManager manager=(android.app.DownloadManager)activity.getSystemService(android.content.Context.DOWNLOAD_SERVICE);
        if(manager==null)throw new IllegalStateException("Download service unavailable");
        manager.enqueue(request);
    }

    static byte[] decodeChunk(String text) {
        if(text==null || text.length()==0 || text.length()>CHUNK*4/3 || text.length()%4!=0 ||
            !text.matches("[A-Za-z0-9+/]+={0,2}"))
            throw new IllegalArgumentException("Invalid base64 chunk");
        byte[] value=Base64.decode(text,Base64.NO_WRAP);
        if(value.length>CHUNK || !Base64.encodeToString(value,Base64.NO_WRAP).equals(text))
            throw new IllegalArgumentException("Noncanonical base64 chunk");
        return value;
    }

    static boolean isPdf(byte[] value) {
        return value.length>=5 && value[0]=='%' && value[1]=='P' && value[2]=='D' && value[3]=='F' && value[4]=='-';
    }

    static boolean isCsv(byte[] value) {
        if(value.length<1 || value.length>LIMIT)return false;
        try {
            String text=java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(value)).toString();
            for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);
                if((c<32 && c!='\r' && c!='\n' && c!='\t') || c==127)return false;
            }
            // RFC4180 quoting: quotes start a field, doubled quotes escape a quote.
            boolean quoted=false, start=true, closed=false;
            for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);
                if(quoted) {
                    if(c=='"') { if(i+1<text.length() && text.charAt(i+1)=='"')i++; else {quoted=false;closed=true;} }
                } else if(c==',' || c=='\r' || c=='\n') {start=true;closed=false;}
                else if(c=='"') {if(!start || closed)return false;quoted=true;start=false;}
                else {if(closed)return false;start=false;}
            }
            return !quoted;
        } catch(Exception error) {return false;}
    }
    static String safeDocumentName(String value,String mime) {
        if("application/pdf".equals(mime))return safeName(value);
        String name=safeDownloadName(value);
        if(name.length()>100)name=name.substring(0,100);
        if(!name.toLowerCase(Locale.ROOT).endsWith(".csv"))name+=".csv";
        return name;
    }

    static String safeDownloadName(String value) {
        String name=value==null?"":value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]","_").trim();
        name=name.replaceAll("^[. ]+","");
        if(name.length()>150)name=name.substring(0,150);
        return name.isEmpty()?"download":name;
    }

    static String safeName(String value) {
        String name=safeDownloadName(value);
        if(name.length()>100)name=name.substring(0,100);
        if(name.isEmpty())name="report";
        if(!name.toLowerCase(Locale.ROOT).endsWith(".pdf"))name+=".pdf";
        return name;
    }

    boolean activityResult(int request,int result,Intent data) {
        if(request!=REQUEST)return false;
        if(document==null || writing)return true;
        if(result!=Activity.RESULT_OK || data==null || data.getData()==null){
            reply(finishId,false,"Document selection cancelled",true);reset();return true;
        }
        Uri uri=data.getData();
        if(!"content".equalsIgnoreCase(uri.getScheme())){
            reply(finishId,false,"Unsafe document destination",false);reset();return true;
        }
        writing=true;
        final byte[] payload=document;
        final String requestId=finishId;
        final int current=generation, transfer=transferVersion;
        new Thread(()->{
            String failure=null;
            try {
                // SAF providers may perform slow I/O. Never block the WebView/UI thread.
                try(OutputStream output=activity.getContentResolver().openOutputStream(uri,"wt")){
                    if(output==null)throw new java.io.IOException("Destination unavailable");
                    for(int offset=0;offset<payload.length;offset+=CHUNK){
                        if(current!=generation || transfer!=transferVersion)throw new java.io.IOException("Document request cancelled");
                        output.write(payload,offset,Math.min(CHUNK,payload.length-offset));
                    }
                    output.flush();
                }
                // Some document providers defer persistence until close. Read back bounded bytes
                // before reporting success; a write-only/unverifiable destination fails clearly.
                try(java.io.InputStream input=activity.getContentResolver().openInputStream(uri)) {
                    if(input==null)throw new java.io.IOException("Destination cannot be verified");
                    byte[] buffer=new byte[8192];int offset=0, count;
                    while((count=input.read(buffer))!=-1) {
                        if(current!=generation || transfer!=transferVersion || count==0 || offset+count>payload.length)
                            throw new java.io.IOException("Destination verification failed");
                        for(int i=0;i<count;i++)if(buffer[i]!=payload[offset+i])
                            throw new java.io.IOException("Destination bytes differ");
                        offset+=count;
                    }
                    if(offset!=payload.length)throw new java.io.IOException("Destination truncated");
                }
            } catch(Exception error){failure="Document write or read-back verification failed";}
            final String writeFailure=failure;
            activity.runOnUiThread(()->{
                if(current!=generation || transfer!=transferVersion){writing=false;return;}
                Runnable complete=()->{
                writing=false;
                if(current!=generation || transfer!=transferVersion)return;
                try {
                    if(writeFailure!=null)throw new java.io.IOException(writeFailure);
                    reply(requestId,true,"saved",false);
                    Toast.makeText(activity,"Document saved.",Toast.LENGTH_SHORT).show();
                } catch(Exception error){
                    reply(requestId,false,writeFailure!=null?writeFailure:"Document save failed",false);
                    Toast.makeText(activity,"Document export failed.",Toast.LENGTH_LONG).show();
                } finally{reset();}
                };
                if(foreground)complete.run();else pendingCompletion=complete;
            });
        },"AppForge-document-write").start();
        return true;
    }

    private void reset(){
        transferVersion++;bytes=null;document=null;finishId=null;expected=0;
        if(pendingCompletion!=null){pendingCompletion=null;writing=false;}
    }
    boolean allowHttpDownload(){
        return foreground && downloads && trustedPage(web.getUrl()) &&
            gestureAt != 0 && SystemClock.elapsedRealtime()-gestureAt<=30000;
    }
    void resume(){
        foreground=true;
        Runnable complete=pendingCompletion;pendingCompletion=null;
        if(complete!=null)complete.run();
    }
    void pause(){
        foreground=false;
        gestureAt=0;
        if(bytes!=null){
            try {
                String event=new JSONObject().put("event","cancelled").toString();
                if(port!=null)port.postMessage(new WebMessage(event));
                else if(localReady && trustedPage(web.getUrl()))web.evaluateJavascript(
                    "window.__appforgeDocumentReply("+JSONObject.quote(event)+")",ignored->{});
            }
            catch(Exception ignored){}
            reset();
        }
    }
    void close(){
        if(finishId!=null)reply(finishId,false,"Document request cancelled by navigation or lifecycle",true);
        generation++;gestureAt=0;localReady=false;lastRequest=0;reset();
        if(pendingCompletion!=null){pendingCompletion=null;writing=false;}
        if(port!=null){port.close();port=null;}
    }
    private static String script(boolean share, boolean nativeApi) { return SCRIPT.replace("__SHARE__",Boolean.toString(share)).replace("__LEGACY__",Boolean.toString(nativeApi)); }
    // Kept as one literal so the executable regression runs the exact shipped shim.
    static final String SCRIPT = "(function(){\n'use strict';\nif(window!==window.top||window.__appforgeDocuments)return;\nwindow.__appforgeDocuments=true;\nconst LIMIT=16*1024*1024, CHUNK=24576;\nlet port, sequence=0, active=false;\nconst waiting=[];\nconst pending=new Map();\nasync function request(op,fields={}) {\n if(!port)await new Promise((resolve,reject)=>{\n  const timer=setTimeout(()=>reject(new Error('Document bridge unavailable')),10000);\n  waiting.push(()=>{clearTimeout(timer);resolve();});\n });\n return new Promise((resolve,reject)=>{\n  if(!port){reject(new Error('Document bridge unavailable'));return;}\n  const id=String(++sequence);\n  const timer=setTimeout(()=>{pending.delete(id);reject(new Error('Document request timed out'));},120000);\n  pending.set(id,{resolve,reject,timer});\n  port.postMessage(JSON.stringify(Object.assign({id,op},fields)));\n });\n}\nwindow.addEventListener('message',function receive(e){\n if(e.data!=='__PORT_TOKEN__'||!e.ports||e.ports.length!==1||port)return;\n port=e.ports[0];\n port.onmessage=deliver;\n port.start();\n waiting.splice(0).forEach(ready=>ready());\n});\nfunction deliver(e){\n  let r;try{r=JSON.parse(e.data);}catch(_){return;}\n  if(r.event==='cancelled'){\n   for(const p of pending.values()){clearTimeout(p.timer);p.reject(new DOMException('Document request cancelled','AbortError'));}\n   pending.clear();return;\n  }\n  const p=pending.get(r.id);if(!p)return;\n  pending.delete(r.id);clearTimeout(p.timer);\n  if(r.ok)p.resolve(r.result);else p.reject(new DOMException(r.error||'Export failed',r.cancelled?'AbortError':'NotAllowedError'));\n }\nif(window.AppForgeLocalDocuments){\n window.__appforgeDocumentReply=raw=>deliver({data:raw});\n port={postMessage:raw=>window.AppForgeLocalDocuments.send('__PORT_TOKEN__',raw)};\n}\nasync function exportPdf(file,mode,name){\n if(active)throw new Error('A document request is already active');\n const mime=file instanceof Blob?file.type.toLowerCase().split(';')[0]:'';\n if(!(file instanceof Blob)||!['application/pdf','text/csv','application/csv'].includes(mime)||file.size<1||file.size>LIMIT)throw new TypeError('Expected PDF or UTF-8 CSV up to 16 MiB');\n active=true;\n try {\n  const buffer=file.arrayBuffer?await file.arrayBuffer():await new Promise((resolve,reject)=>{\n   const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(reader.error);reader.readAsArrayBuffer(file);\n  });\n  const bytes=new Uint8Array(buffer);\n  if(mime==='application/pdf'&&String.fromCharCode(...bytes.subarray(0,5))!=='%PDF-')throw new TypeError('Invalid PDF signature');\n  await request('begin',{mode,name:name||file.name||(mime==='application/pdf'?'report.pdf':'report.csv'),mime,size:bytes.length});\n  for(let at=0;at<bytes.length;at+=CHUNK){\n   const part=bytes.subarray(at,at+CHUNK);let binary='';\n   for(let i=0;i<part.length;i++)binary+=String.fromCharCode(part[i]);\n   await request('chunk',{offset:at,data:btoa(binary)});\n  }\n  return await request('finish');\n }catch(error){request('cancel').catch(()=>{});throw error;}\n finally{active=false;}\n}\nasync function fromUrl(url,name){\n const value=String(url);\n if(!value.startsWith('blob:')&&!/^data:(?:application\\/pdf|(?:text|application)\\/csv)(?:;charset=utf-8)?(?:;base64)?,/i.test(value))throw new TypeError('Expected generated PDF or CSV URL');\n if(value.length>Math.ceil(LIMIT*4/3)+100)throw new RangeError('Document URL too large');\n const response=await fetch(value);const blob=await response.blob();\n return exportPdf(blob,'save',name);\n}\nif(__LEGACY__){\n const nativeCall=(method,args)=>window.AppForgeNativeCompat\n  ? window.AppForgeNativeCompat.call('__PORT_TOKEN__',JSON.stringify({method,args}))\n  : request('legacy',{method,args});\n window.AppForgeFastNative=Object.freeze({isEnabled:()=>__NATIVE__,\n  share:(title,text)=>nativeCall('share',[title,text]),copy:text=>nativeCall('copy',[text]),\n  vibrate:ms=>nativeCall('vibrate',[ms]),scanCode:()=>nativeCall('scanCode',[])});\n window.AppForgeDownloads=Object.freeze({isEnabled:()=>__DOWNLOAD__,download:url=>nativeCall('download',[url]),status:id=>nativeCall('status',[id])});\n window.AppForgeCaptureHint=Object.freeze({markCapture:()=>nativeCall('markCapture',[]),clearCapture:()=>nativeCall('clearCapture',[])});\n}\nfunction csv(rows){\n if(!Array.isArray(rows)||rows.length>100000)throw new TypeError('Expected bounded CSV rows');\n let text='';\n for(const row of rows){\n  if(!Array.isArray(row)||row.length>10000)throw new TypeError('Expected CSV cells');\n  const line=row.map(cell=>'\"'+String(cell==null?'':cell).replace(/\"/g,'\"\"')+'\"').join(',')+'\\r\\n';\n  text+=line;if(text.length>LIMIT)throw new RangeError('CSV too large');\n }\n const blob=new Blob([text],{type:'text/csv'});if(blob.size>LIMIT)throw new RangeError('CSV too large');return blob;\n}\nwindow.AppForgeDocuments=Object.freeze({csv,save:(file,name)=>exportPdf(file,'save',name),share:(file,name)=>exportPdf(file,'share',name),download:fromUrl});\nfunction report(error){window.dispatchEvent(new CustomEvent('appforge-document-error',{detail:{name:error.name,message:error.message}}));}\ndocument.addEventListener('click',function(e){\n let a=e.target;while(a&&a.tagName!=='A')a=a.parentElement;\n if(a&&a.href&&__DOWNLOAD__&&/^https?:/i.test(a.href)&&\n    (a.hasAttribute('download')||/\\.(pdf|zip|apk|aab|rar|7z|doc|docx|xls|xlsx|ppt|pptx|csv|txt)(?:[?#]|$)/i.test(a.href))){\n  e.preventDefault();e.stopImmediatePropagation();request('http',{url:a.href}).catch(report);return;\n }\n if(!a||!a.href||(!a.href.startsWith('blob:')&&!/^data:(?:application\\/pdf|(?:text|application)\\/csv)[;,]/i.test(a.href)))return;\n e.preventDefault();e.stopImmediatePropagation();fromUrl(a.href,a.download).catch(report);\n},true);\nconst originalShare=navigator.share&&navigator.share.bind(navigator);\nconst originalCanShare=navigator.canShare&&navigator.canShare.bind(navigator);\nif(__SHARE__){\n try{Object.defineProperty(navigator,'share',{configurable:true,value:function(data){\n  if(data&&data.files){if(data.files.length!==1)return Promise.reject(new TypeError('One PDF or CSV required'));return exportPdf(data.files[0],'share',data.files[0].name);}\n  return originalShare?originalShare(data):Promise.reject(new TypeError('File share requires a PDF or CSV'));\n }});\n Object.defineProperty(navigator,'canShare',{configurable:true,value:function(data){\n  if(data&&data.files)return data.files.length===1&&data.files[0] instanceof Blob&&['application/pdf','text/csv','application/csv'].includes(data.files[0].type.toLowerCase().split(';')[0])&&data.files[0].size<=LIMIT;\n  return originalCanShare?originalCanShare(data):false;\n }});}catch(_){}\n}\n})();";
}
