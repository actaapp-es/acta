package com.acta.recorder;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Expone en solo lectura los archivos de la carpeta de grabaciones para compartirlos. */
public class RecordingProvider extends ContentProvider {

    @Override
    public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) throw new FileNotFoundException();
        File f = new File(Util.dir(getContext()), name);
        if (!f.exists()) throw new FileNotFoundException(name);
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (Crypto.isEncrypted(f)) {
            // Al compartir, la otra app recibe el audio descifrado (copia temporal en la caché privada).
            File dir = new File(getContext().getCacheDir(), "share");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, f.getName());
            try {
                Crypto.decryptTo(getContext(), f, out);
            } catch (Exception e) {
                throw new FileNotFoundException("No se pudo descifrar la grabación");
            }
            f = out;
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        if (n == null) return "audio/*";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".amr")) return "audio/amr";
        if (n.endsWith(".ogg") || n.endsWith(".opus")) return "audio/ogg";
        if (n.endsWith(".wav")) return "audio/wav";
        return "audio/mp4";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
        File f;
        try { f = resolve(uri); } catch (FileNotFoundException e) { return null; }
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), Crypto.plainLength(f)});
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
