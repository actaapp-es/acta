package com.acta.recorder;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recoge las grabaciones que hace la app de llamadas de Xiaomi (MIUI / HyperOS),
 * que graba la línea completa, y las incorpora a Acta con número, fecha, hora y duración.
 */
final class Importer {

    private static final Pattern STAMP = Pattern.compile("(20\\d{2})(\\d{2})(\\d{2})_?(\\d{2})(\\d{2})(\\d{2})");
    private static final Pattern PAREN = Pattern.compile("\\(([^)]*\\d[^)]*)\\)");
    private static final Pattern DIGITS = Pattern.compile("\\+?\\d[\\d ]{5,}\\d");
    private static final Object LOCK = new Object();

    private static final class Source {
        Uri uri;
        String name;
        long size, modified;
    }

    private Importer() { }

    static boolean canRead(Context c) {
        return Util.has(c, Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE) || folderUri(c) != null;
    }

    static Uri folderUri(Context c) {
        String s = Util.prefs(c).getString("folder", null);
        return s == null ? null : Uri.parse(s);
    }

    /** Busca grabaciones nuevas. Devuelve cuántas se han añadido. */
    static int run(Context c) {
        synchronized (LOCK) {
            List<Source> found = new ArrayList<>();
            scanMediaStore(c, found);
            Uri tree = folderUri(c);
            if (tree != null) scanTree(c, tree, found);

            SharedPreferences p = Util.prefs(c);
            Set<String> done = new HashSet<>(p.getStringSet("imported", new HashSet<String>()));
            int added = 0;
            for (Source s : found) {
                String key = s.name + "|" + s.size;
                if (done.contains(key)) continue;
                if (importOne(c, s)) added++;
                done.add(key);
            }
            p.edit().putStringSet("imported", done).apply();
            if (added > 0) c.sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(c.getPackageName()));
            return added;
        }
    }

    private static boolean looksLikeCallFolder(String path) {
        if (path == null) return false;
        String l = path.toLowerCase(Locale.ROOT);
        return l.contains("call_rec") || l.contains("callrec") || l.contains("call recording")
                || l.contains("recordings/call") || l.contains("sound_recorder/call")
                || l.contains("jemrec") || l.contains("shizucallrecorder");
    }

    private static void scanMediaStore(Context c, List<Source> out) {
        boolean perm = Util.has(c, Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE);
        if (!perm) return;
        Uri base = MediaStore.Files.getContentUri("external");
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(base,
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
                            MediaStore.MediaColumns.RELATIVE_PATH},
                    MediaStore.MediaColumns.MIME_TYPE + " LIKE 'audio/%'", null, null);
            while (cur != null && cur.moveToNext()) {
                String rel = cur.getString(4);
                if (!looksLikeCallFolder(rel)) continue;
                Source s = new Source();
                s.uri = Uri.withAppendedPath(base, String.valueOf(cur.getLong(0)));
                s.name = cur.getString(1);
                s.size = cur.getLong(2);
                s.modified = cur.getLong(3) * 1000L;
                if (s.name != null && s.size > 0) out.add(s);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
    }

    private static void scanTree(Context c, Uri tree, List<Source> out) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(tree);
            walk(c.getContentResolver(), tree, docId, out, 0);
        } catch (Exception ignored) { }
    }

    private static void walk(ContentResolver cr, Uri tree, String docId, List<Source> out, int depth) {
        if (depth > 3) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor cur = null;
        try {
            cur = cr.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null);
            while (cur != null && cur.moveToNext()) {
                String id = cur.getString(0), name = cur.getString(1), mime = cur.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(cr, tree, id, out, depth + 1);
                } else if ((mime != null && mime.startsWith("audio/")) || isAudioName(name)) {
                    Source s = new Source();
                    s.uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    s.name = name;
                    s.size = cur.getLong(3);
                    s.modified = cur.getLong(4);
                    if (s.size > 0) out.add(s);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
    }

    private static boolean isAudioName(String n) {
        if (n == null) return false;
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".mp3") || l.endsWith(".m4a") || l.endsWith(".aac") || l.endsWith(".amr")
                || l.endsWith(".ogg") || l.endsWith(".opus") || l.endsWith(".wav");
    }

    private static boolean importOne(Context c, Source s) {
        // 1. Datos del nombre del archivo (formato de Xiaomi: Nombre(número)_AAAAMMDDhhmmss.mp3)
        long when = s.modified;
        Matcher m = STAMP.matcher(s.name);
        String stampText = null;
        if (m.find()) {
            stampText = m.group();
            try {
                Date d = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT)
                        .parse(m.group(1) + m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6));
                if (d != null) when = d.getTime();
            } catch (Exception ignored) { }
        }
        String base = s.name.replaceFirst("\\.[^.]+$", "");
        if (stampText != null) base = base.replace(stampText, " ");
        String number = null;
        Matcher pm = PAREN.matcher(base);
        if (pm.find()) number = pm.group(1).replaceAll("[^0-9+]", "");
        if (TextUtils.isEmpty(number)) {
            Matcher dm = DIGITS.matcher(base);
            if (dm.find()) number = dm.group().replaceAll("[^0-9+]", "");
        }

        // 2. Duración real del audio
        int audioSec = 0;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(c, s.uri);
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) audioSec = (int) Math.round(Long.parseLong(d) / 1000.0);
        } catch (Exception ignored) {
        } finally {
            try { mmr.release(); } catch (Exception ignored) { }
        }

        Db.Call call = new Db.Call();
        call.number = number;
        call.startMs = when;
        call.durationSec = Math.max(audioSec, 1);
        call.recordedSec = Math.max(audioSec, 1);
        call.direction = 1;
        call.source = s.name.toLowerCase(Locale.ROOT).endsWith(".ogg") || s.name.toLowerCase(Locale.ROOT).endsWith(".opus")
                ? "JemRec (línea completa)" : "Grabadora externa (línea completa)";

        // 3. Cruce con el registro de llamadas para fijar número, sentido, hora y duración exactos
        matchCallLog(c, call, audioSec);
        call.name = Util.contactName(c, call.number);

        // 4. Copia en la carpeta de Acta
        String ext = s.name.contains(".") ? s.name.substring(s.name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : ".mp3";
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Util.ES).format(new Date(call.startMs));
        String fileName = stamp + "_" + (call.direction == 1 ? "entrante" : "saliente") + "_" + Util.safe(call.number) + ext;
        File target = new File(Util.dir(c), fileName);
        InputStream in = null;
        OutputStream out = null;
        try {
            in = c.getContentResolver().openInputStream(s.uri);
            if (in == null) return false;
            out = new FileOutputStream(target);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            target.delete();
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
        }
        call.file = fileName;
        Db.get(c).insert(call);
        return true;
    }

    private static void matchCallLog(Context c, Db.Call call, int audioSec) {
        if (!Util.has(c, Manifest.permission.READ_CALL_LOG)) return;
        Cursor cur = null;
        try {
            long from = call.startMs - 6 * 60 * 60 * 1000L, to = call.startMs + 10 * 60 * 1000L;
            cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    CallLog.Calls.DATE + " BETWEEN ? AND ? AND " + CallLog.Calls.DURATION + " > 0",
                    new String[]{String.valueOf(from), String.valueOf(to)}, CallLog.Calls.DATE + " DESC");
            long best = Long.MAX_VALUE;
            while (cur != null && cur.moveToNext()) {
                String n = cur.getString(0);
                int type = cur.getInt(1);
                long date = cur.getLong(2);
                int dur = cur.getInt(3);
                if (!TextUtils.isEmpty(call.number) && !tailEq(n, call.number)) continue;
                // El nombre del archivo suele llevar la hora de inicio o de fin de la llamada.
                long diff = Math.min(Math.abs(date - call.startMs), Math.abs(date + dur * 1000L - call.startMs));
                diff += Math.abs(dur - audioSec) * 200L;
                if (diff < best && diff < 15 * 60 * 1000L) {
                    best = diff;
                    if (!TextUtils.isEmpty(n)) call.number = n;
                    call.direction = type == CallLog.Calls.OUTGOING_TYPE ? 2 : 1;
                    call.startMs = date;
                    call.durationSec = dur;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
    }

    private static boolean tailEq(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.replaceAll("[^0-9]", ""), y = b.replaceAll("[^0-9]", "");
        int k = Math.min(9, Math.min(x.length(), y.length()));
        return k >= 6 && x.substring(x.length() - k).equals(y.substring(y.length() - k));
    }
}
