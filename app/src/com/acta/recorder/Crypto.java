package com.acta.recorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaDataSource;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado de las grabaciones (AES-256-GCM).
 *
 * Clave: una clave de datos aleatoria de 256 bits, guardada "envuelta" (cifrada) con una clave
 * maestra que vive en el Android Keystore y nunca sale de él. Fuera del móvil los audios no se
 * pueden abrir.
 *
 * Formato por bloques (permite reproducir con acceso aleatorio sin descifrar a disco):
 *   "ACTAENC1" | versión(1) | tamaño de bloque(4) | nonce(8) | longitud en claro(8)
 *   bloque i = AES-GCM(texto[i]) + etiqueta(16), IV = nonce || i, AAD = cabecera || i
 */
final class Crypto {

    private static final byte[] MAGIC = {'A', 'C', 'T', 'A', 'E', 'N', 'C', '1'};
    private static final int HEADER = 8 + 1 + 4 + 8 + 8;
    private static final int CHUNK = 256 * 1024;
    private static final int TAG = 16;
    private static final String MASTER = "acta_master_v1";
    private static final String[] AUDIO = {".wav", ".m4a", ".mp3", ".amr", ".ogg", ".opus", ".aac", ".3gp"};

    private static volatile SecretKey dataKey;

    private Crypto() { }

    // ------------------------------------------------------------------ Claves

    static synchronized SecretKey key(Context c) throws Exception {
        if (dataKey != null) return dataKey;
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(MASTER)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(MASTER,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        SecretKey master = (SecretKey) ks.getKey(MASTER, null);
        SharedPreferences p = Util.prefs(c);
        String wrapped = p.getString("rec_dek", null);
        if (wrapped == null) {
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.ENCRYPT_MODE, master);
            String w = b64(ci.getIV()) + ":" + b64(ci.doFinal(raw));
            if (!p.edit().putString("rec_dek", w).commit()) throw new IOException("no se pudo guardar la clave");
            dataKey = new SecretKeySpec(raw, "AES");
        } else {
            String[] parts = wrapped.split(":");
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, master, new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            dataKey = new SecretKeySpec(ci.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), "AES");
        }
        return dataKey;
    }

    /** Cifra un texto corto (p. ej. la clave de la IA) con la clave del Keystore. */
    static String sealText(Context ctx, String plain) throws Exception {
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, key(ctx));
        return "s1:" + b64(ci.getIV()) + ":" + b64(ci.doFinal(plain.getBytes("UTF-8")));
    }

    static String openText(Context ctx, String sealed) throws Exception {
        String[] p = sealed.split(":");
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, key(ctx), new GCMParameterSpec(128, Base64.decode(p[1], Base64.NO_WRAP)));
        return new String(ci.doFinal(Base64.decode(p[2], Base64.NO_WRAP)), "UTF-8");
    }

    private static String b64(byte[] b) { return Base64.encodeToString(b, Base64.NO_WRAP); }

    // ------------------------------------------------------------------ Utilidades de formato

    static boolean isAudio(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.startsWith("tmp_") || n.contains(".enc_tmp")) return false;
        for (String e : AUDIO) if (n.endsWith(e)) return true;
        return false;
    }

    static boolean isEncrypted(File f) {
        if (f == null || f.length() < HEADER) return false;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] m = new byte[8];
            if (in.read(m) != 8) return false;
            for (int i = 0; i < 8; i++) if (m[i] != MAGIC[i]) return false;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] iv(byte[] header, long idx) {
        byte[] iv = new byte[12];
        System.arraycopy(header, 13, iv, 0, 8);
        iv[8] = (byte) (idx >>> 24); iv[9] = (byte) (idx >>> 16); iv[10] = (byte) (idx >>> 8); iv[11] = (byte) idx;
        return iv;
    }

    private static byte[] idxBytes(long idx) {
        return new byte[]{(byte) (idx >>> 24), (byte) (idx >>> 16), (byte) (idx >>> 8), (byte) idx};
    }

    private static long plainLength(byte[] h) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (h[21 + i] & 0xffL);
        return v;
    }

    /** Tamaño real del audio (sin cifrar). */
    static long plainLength(File f) {
        if (!isEncrypted(f)) return f.length();
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[HEADER];
            raf.readFully(h);
            return plainLength(h);
        } catch (IOException e) {
            return f.length();
        }
    }

    // ------------------------------------------------------------------ Cifrar

    /** Cifra el archivo en su sitio (de forma atómica). true si queda cifrado. */
    static synchronized boolean encryptFile(Context c, File f) {
        if (f == null || !f.exists()) return false;
        if (isEncrypted(f)) return true;
        File tmp = new File(f.getParentFile(), f.getName() + ".enc_tmp");
        try {
            SecretKey k = key(c);
            long len = f.length();
            byte[] header = new byte[HEADER];
            System.arraycopy(MAGIC, 0, header, 0, 8);
            header[8] = 1;
            header[9] = (byte) (CHUNK >>> 24); header[10] = (byte) (CHUNK >>> 16);
            header[11] = (byte) (CHUNK >>> 8); header[12] = (byte) CHUNK;
            byte[] nonce = new byte[8];
            new SecureRandom().nextBytes(nonce);
            System.arraycopy(nonce, 0, header, 13, 8);
            for (int i = 0; i < 8; i++) header[21 + i] = (byte) (len >>> (56 - 8 * i));

            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            FileOutputStream fos = new FileOutputStream(tmp);
            try (InputStream in = new FileInputStream(f); OutputStream out = new BufferedOutputStream(fos, 1 << 16)) {
                out.write(header);
                byte[] buf = new byte[CHUNK];
                long idx = 0;
                int n;
                while ((n = readFully(in, buf)) > 0) {
                    ci.init(Cipher.ENCRYPT_MODE, k, new GCMParameterSpec(128, iv(header, idx)));
                    ci.updateAAD(header);
                    ci.updateAAD(idxBytes(idx));
                    out.write(ci.doFinal(buf, 0, n));
                    idx++;
                }
                out.flush();
                fos.getFD().sync();
            }
            // Comprueba que se puede leer antes de sustituir el original.
            if (plainLength(tmp) != len || !verifyFirstChunk(c, tmp)) throw new IOException("verificación fallida");
            if (!tmp.renameTo(f)) throw new IOException("no se pudo sustituir");
            return true;
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
    }

    private static boolean verifyFirstChunk(Context c, File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[HEADER];
            raf.readFully(h);
            if (plainLength(h) == 0) return true;
            decryptChunk(key(c), raf, h, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }

    /** Cifra todas las grabaciones que aún estén en claro (las de versiones anteriores). */
    static int encryptAll(Context c) {
        File[] files = Util.dir(c).listFiles();
        int done = 0;
        if (files == null) return 0;
        for (File f : files) {
            String n = f.getName();
            if (n.endsWith(".enc_tmp")) { // restos de un cifrado interrumpido
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                continue;
            }
            if (isAudio(f) && !isEncrypted(f) && encryptFile(c, f)) done++;
        }
        return done;
    }

    static int[] stats(Context c) {
        File[] files = Util.dir(c).listFiles();
        int enc = 0, total = 0;
        if (files != null) for (File f : files) {
            if (!isAudio(f)) continue;
            total++;
            if (isEncrypted(f)) enc++;
        }
        return new int[]{enc, total};
    }

    // ------------------------------------------------------------------ Descifrar

    private static byte[] decryptChunk(SecretKey k, RandomAccessFile raf, byte[] h, long idx) throws Exception {
        long plain = plainLength(h);
        long start = idx * (long) CHUNK;
        int len = (int) Math.min(CHUNK, plain - start);
        byte[] ct = new byte[len + TAG];
        raf.seek(HEADER + idx * (long) (CHUNK + TAG));
        raf.readFully(ct);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv(h, idx)));
        ci.updateAAD(h);
        ci.updateAAD(idxBytes(idx));
        return ci.doFinal(ct);
    }

    /** Descifra a un archivo (para compartir o enviar a la IA). Si no está cifrado, lo copia. */
    static void decryptTo(Context c, File src, File dst) throws Exception {
        if (!isEncrypted(src)) {
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] b = new byte[1 << 16];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            return;
        }
        SecretKey k = key(c);
        try (RandomAccessFile raf = new RandomAccessFile(src, "r");
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dst), 1 << 16)) {
            byte[] h = new byte[HEADER];
            raf.readFully(h);
            long plain = plainLength(h);
            long chunks = (plain + CHUNK - 1) / CHUNK;
            for (long i = 0; i < chunks; i++) out.write(decryptChunk(k, raf, h, i));
        }
    }

    /** Fuente para MediaPlayer: descifra bloque a bloque en memoria, con acceso aleatorio. */
    static final class Source extends MediaDataSource {
        private final RandomAccessFile raf;
        private final byte[] header = new byte[HEADER];
        private final long plain;
        private final SecretKey k;
        private long cachedIdx = -1;
        private byte[] cached;

        Source(Context c, File f) throws Exception {
            k = key(c);
            raf = new RandomAccessFile(f, "r");
            raf.readFully(header);
            plain = plainLength(header);
        }

        @Override
        public synchronized int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            if (position >= plain) return -1;
            int total = 0;
            try {
                while (size > 0 && position < plain) {
                    long idx = position / CHUNK;
                    if (idx != cachedIdx) {
                        cached = decryptChunk(k, raf, header, idx);
                        cachedIdx = idx;
                    }
                    int off = (int) (position - idx * CHUNK);
                    int n = Math.min(size, cached.length - off);
                    System.arraycopy(cached, off, buffer, offset, n);
                    position += n;
                    offset += n;
                    size -= n;
                    total += n;
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("audio dañado o clave no válida", e);
            }
            return total;
        }

        @Override
        public long getSize() { return plain; }

        @Override
        public synchronized void close() throws IOException { raf.close(); }
    }
}
