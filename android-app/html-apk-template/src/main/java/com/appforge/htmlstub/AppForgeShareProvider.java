package com.appforge.htmlstub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/** Only opaque, expiring app-created documents. Android enforces recipient URI read grants. */
public final class AppForgeShareProvider extends ContentProvider {
    static final long TTL = 60 * 60 * 1000L;
    private static File directory(Context context) throws java.io.IOException {
        File root = new File(context.getCacheDir(), "appforge-share-v1");
        if (!root.mkdirs() && !root.isDirectory()) throw new java.io.IOException("Share cache unavailable");
        return root;
    }
    static synchronized Uri create(Context context, byte[] bytes, String name, String mime) throws Exception {
        if (bytes.length < 1 || bytes.length > AppForgeDocuments.LIMIT ||
            !("application/pdf".equals(mime) ? AppForgeDocuments.isPdf(bytes) :
                ("text/csv".equals(mime) || "application/csv".equals(mime)) && AppForgeDocuments.isCsv(bytes)))
            throw new SecurityException("Invalid share document");
        File root = directory(context);
        long total = 0, now = System.currentTimeMillis();
        File[] files = root.listFiles();
        if (files == null) throw new java.io.IOException("Share cache unavailable");
        for (File file : files) {
            if (now - file.lastModified() >= TTL || file.lastModified() > now) file.delete();
            else total += file.length();
        }
        if (total + bytes.length > 64L * 1024 * 1024) throw new java.io.IOException("Share cache full; try later");
        String key = java.util.UUID.randomUUID().toString().replace("-", "") +
            ("application/pdf".equals(mime) ? ".pdf" : ".csv");
        File target = new File(root, key);
        boolean ready = false;
        try {
            if (!target.createNewFile()) throw new java.io.IOException("Share collision");
            try (FileOutputStream out = new FileOutputStream(target)) { out.write(bytes); out.getFD().sync(); }
            ready = true;
            // The filename is display metadata only; it is never resolved as a filesystem path.
            return Uri.parse("content://" + context.getPackageName() + ".appforge.documents/" + key + "/" +
                Uri.encode(AppForgeDocuments.safeDocumentName(name, mime)));
        } finally { if (!ready) target.delete(); }
    }
    private File resolve(Uri uri) throws FileNotFoundException {
        try {
            if (!"content".equals(uri.getScheme()) ||
                !(getContext().getPackageName() + ".appforge.documents").equals(uri.getAuthority()) ||
                uri.getQuery() != null || uri.getFragment() != null) throw new SecurityException();
            java.util.List<String> parts = uri.getPathSegments();
            if (parts.size() != 2 || !parts.get(0).matches("[a-f0-9]{32}\\.(pdf|csv)") ||
                !parts.get(1).equals(AppForgeDocuments.safeDocumentName(parts.get(1),
                    parts.get(0).endsWith(".pdf") ? "application/pdf" : "text/csv"))) throw new SecurityException();
            File root = directory(getContext());
            File file = new File(root, parts.get(0));
            if (!file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()) || !file.isFile() ||
                file.length() < 1 || file.length() > AppForgeDocuments.LIMIT) throw new SecurityException();
            long age = System.currentTimeMillis() - file.lastModified();
            if (age < 0 || age >= TTL) { file.delete(); throw new SecurityException(); }
            return file;
        } catch (Exception error) { throw new FileNotFoundException("Unavailable share document"); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only share");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) {
        try { return resolve(uri).getName().endsWith(".pdf") ? "application/pdf" : "text/csv"; }
        catch (FileNotFoundException e) { throw new IllegalArgumentException("Unavailable share document"); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null ? new String[]{"_display_name", "_size"} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i=0;i<columns.length;i++) {
                if ("_display_name".equals(columns[i])) row[i]=uri.getLastPathSegment();
                else if ("_size".equals(columns[i])) row[i]=file.length();
            }
            cursor.addRow(row); return cursor;
        } catch (FileNotFoundException e) { throw new IllegalArgumentException("Unavailable share document"); }
    }
    @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException("Read-only share"); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException("Read-only share"); }
    @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException("Read-only share"); }
}
