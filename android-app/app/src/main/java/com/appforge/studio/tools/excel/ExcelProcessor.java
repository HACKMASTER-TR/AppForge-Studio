package com.appforge.studio.tools.excel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ExcelProcessor {
    public static final long MAX_INPUT_BYTES = 80L * 1024L * 1024L;
    private static final long MAX_UNCOMPRESSED_BYTES = 300L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 25_000;

    private static final Pattern SHEET_PROTECTION = Pattern.compile(
            "<(?:[A-Za-z_][\\w.-]*:)?sheetProtection\\b[^>]*(?:/\\s*>|>[\\s\\S]*?</(?:[A-Za-z_][\\w.-]*:)?sheetProtection\\s*>)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WORKBOOK_PROTECTION = Pattern.compile(
            "<(?:[A-Za-z_][\\w.-]*:)?workbookProtection\\b[^>]*(?:/\\s*>|>[\\s\\S]*?</(?:[A-Za-z_][\\w.-]*:)?workbookProtection\\s*>)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_SHARING = Pattern.compile(
            "<(?:[A-Za-z_][\\w.-]*:)?fileSharing\\b[^>]*(?:/\\s*>|>[\\s\\S]*?</(?:[A-Za-z_][\\w.-]*:)?fileSharing\\s*>)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNATURE_RELATIONSHIP = Pattern.compile(
            "<Relationship\\b[^>]*(?:digital-signature|_xmlsignatures)[^>]*/\\s*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNATURE_CONTENT_TYPE = Pattern.compile(
            "<(?:Override|Default)\\b[^>]*(?:_xmlsignatures|digital-signature|\\.sigs)[^>]*/\\s*>",
            Pattern.CASE_INSENSITIVE);

    private ExcelProcessor() {
    }

    public static Result process(InputStream input, String fileName) throws IOException {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        byte[] source = readLimited(input, MAX_INPUT_BYTES);

        if (lowerName.endsWith(".csv")) {
            return new Result(source, 0, true);
        }
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xlsm")) {
            throw new IOException("Yalnızca XLSX, XLSM ve CSV dosyaları desteklenir.");
        }
        if (source.length < 4 || source[0] != 'P' || source[1] != 'K') {
            throw new IOException("Dosya geçerli bir Excel Open XML paketi değil. Açılış parolasıyla şifrelenmiş olabilir.");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(source.length, 16 * 1024));
        int changedParts = 0;
        int entryCount = 0;
        long totalUncompressed = 0;

        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(source));
             ZipOutputStream zipOut = new ZipOutputStream(output)) {
            ZipEntry entry;
            byte[] buffer = new byte[16 * 1024];

            while ((entry = zipIn.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("Excel paketinde olağan dışı sayıda dosya bölümü var.");
                }

                String entryName = entry.getName();
                String lowerEntry = entryName.toLowerCase(Locale.ROOT);

                if (lowerEntry.startsWith("_xmlsignatures/")) {
                    changedParts++;
                    zipIn.closeEntry();
                    continue;
                }

                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
                int read;
                while ((read = zipIn.read(buffer)) != -1) {
                    totalUncompressed += read;
                    if (totalUncompressed > MAX_UNCOMPRESSED_BYTES) {
                        throw new IOException("Excel paketinin açılmış boyutu güvenli sınırı aşıyor.");
                    }
                    entryBuffer.write(buffer, 0, read);
                }
                zipIn.closeEntry();

                byte[] entryBytes = entryBuffer.toByteArray();
                if (shouldInspect(entryName)) {
                    String xml = new String(entryBytes, StandardCharsets.UTF_8);
                    String updated = stripProtection(xml, entryName);
                    if (!updated.equals(xml)) {
                        entryBytes = updated.getBytes(StandardCharsets.UTF_8);
                        changedParts++;
                    }
                }

                ZipEntry outEntry = new ZipEntry(entryName);
                if (entry.getTime() >= 0) {
                    outEntry.setTime(entry.getTime());
                }
                if (entry.getComment() != null) {
                    outEntry.setComment(entry.getComment());
                }
                zipOut.putNextEntry(outEntry);
                zipOut.write(entryBytes);
                zipOut.closeEntry();
            }
        }

        if (entryCount == 0) {
            throw new IOException("Excel paketinin içeriği okunamadı.");
        }
        return new Result(output.toByteArray(), changedParts, false);
    }

    private static boolean shouldInspect(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("xl/workbook.xml")
                || lower.matches("xl/(worksheets|chartsheets|dialogsheets)/.*\\.xml")
                || lower.endsWith(".rels")
                || lower.equals("[content_types].xml");
    }

    private static String stripProtection(String xml, String entryName) {
        String updated = SHEET_PROTECTION.matcher(xml).replaceAll("");
        updated = WORKBOOK_PROTECTION.matcher(updated).replaceAll("");
        updated = FILE_SHARING.matcher(updated).replaceAll("");

        String lower = entryName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".rels")) {
            updated = SIGNATURE_RELATIONSHIP.matcher(updated).replaceAll("");
        }
        if (lower.equals("[content_types].xml")) {
            updated = SIGNATURE_CONTENT_TYPE.matcher(updated).replaceAll("");
        }
        return updated;
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("Dosya 80 MB sınırını aşıyor.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static final class Result {
        public final byte[] data;
        public final int changedParts;
        public final boolean csvCopy;

        Result(byte[] data, int changedParts, boolean csvCopy) {
            this.data = data;
            this.changedParts = changedParts;
            this.csvCopy = csvCopy;
        }
    }
}
