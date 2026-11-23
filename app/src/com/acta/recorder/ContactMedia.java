package com.acta.recorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Foto de perfil y fondo de la pantalla de llamada de cada contacto.
 * Se guardan solo en el almacenamiento privado de Acta; no se tocan los contactos del teléfono.
 */
final class ContactMedia {
    private ContactMedia() { }

    static final int PHOTO_PX = 384, BG_PX = 1280;

    private static final android.util.LruCache<String, Bitmap> CACHE = new android.util.LruCache<String, Bitmap>(24) { };

    /** Clave estable del número: los 9 últimos dígitos (mismo número con o sin prefijo). */
    static String key(String number) {
        if (TextUtils.isEmpty(number)) return null;
        String d = PhoneNumberUtils.normalizeNumber(number).replaceAll("[^0-9]", "");
        if (d.isEmpty()) return null;
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    private static File dir(Context c) {
        File f = new File(c.getFilesDir(), "contact_media");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    private static File file(Context c, String number, boolean photo) {
        String k = key(number);
        return k == null ? null : new File(dir(c), (photo ? "p_" : "b_") + k + ".jpg");
    }

    static boolean has(Context c, String number, boolean photo) {
        File f = file(c, number, photo);
        return f != null && f.exists();
    }

    /** Foto o fondo ya decodificado, o null. */
    static Bitmap get(Context c, String number, boolean photo) {
        File f = file(c, number, photo);
        if (f == null || !f.exists()) return null;
        String ck = f.getPath() + "|" + f.lastModified();
        Bitmap b = CACHE.get(ck);
        if (b != null) return b;
        try {
            b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) CACHE.put(ck, b);
        } catch (Throwable ignored) { b = null; }
        return b;
    }

    static void remove(Context c, String number, boolean photo) {
        File f = file(c, number, photo);
        if (f != null) f.delete();
    }

    /** Copia una imagen elegida por la persona, la recorta/reduce y la guarda. Llamar fuera del hilo principal. */
    static boolean save(Context c, Uri src, String number, boolean photo) {
        File out = file(c, number, photo);
        if (out == null || src == null) return false;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(src);
            if (in == null) return false;
            try { BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            int target = photo ? PHOTO_PX : BG_PX;
            int sample = 1;
            while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap bmp;
            in = c.getContentResolver().openInputStream(src);
            if (in == null) return false;
            try { bmp = BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
            if (bmp == null) return false;
            bmp = rotate(c, src, bmp);
            Bitmap res = photo ? squareCrop(bmp, PHOTO_PX) : fit(bmp, BG_PX);
            FileOutputStream fo = new FileOutputStream(out);
            try { res.compress(Bitmap.CompressFormat.JPEG, photo ? 90 : 85, fo); } finally { fo.close(); }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Bitmap rotate(Context c, Uri src, Bitmap b) {
        try {
            InputStream in = c.getContentResolver().openInputStream(src);
            if (in == null) return b;
            int orient;
            try { orient = new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1); }
            finally { in.close(); }
            Matrix m = new Matrix();
            switch (orient) {
                case 3: m.postRotate(180); break;
                case 6: m.postRotate(90); break;
                case 8: m.postRotate(270); break;
                case 2: m.postScale(-1, 1); break;
                case 4: m.postScale(1, -1); break;
                default: return b;
            }
            return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        } catch (Throwable t) { return b; }
    }

    private static Bitmap squareCrop(Bitmap b, int px) {
        int s = Math.min(b.getWidth(), b.getHeight());
        Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, s);
        return s > px ? Bitmap.createScaledBitmap(sq, px, px, true) : sq;
    }

    private static Bitmap fit(Bitmap b, int max) {
        int big = Math.max(b.getWidth(), b.getHeight());
        if (big <= max) return b;
        float f = max / (float) big;
        return Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * f), Math.round(b.getHeight() * f), true);
    }
}
