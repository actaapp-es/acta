package com.acta.recorder;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Graba una llamada. Por orden de preferencia:
 *  Versión Google Play: micrófono del teléfono (con altavoz se capta también a la otra persona).
 *  Versión completa: además, líneas de voz completas por ADB/Shizuku.
 */
final class Recorder {

    private MediaRecorder recorder;
    private ParcelFileDescriptor pfd;
    private boolean viaShizuku;
    private boolean viaNative;
    private Context ctx;
    File file;
    String source;
    long startedAt;

    boolean start(Context c) {
        startedAt = System.currentTimeMillis();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Util.ES).format(new Date(startedAt));
        ctx = c.getApplicationContext();

        // Vía nativa (AudioRecord vía ADB): produce WAV con ambas voces.
        // Se intenta SIEMPRE (reconectando si hace falta), acotada por un vigilante:
        // si no arranca a tiempo, se abandona y se graba al micro para no perder la llamada.
        if (Util.nativeAdbEnabled(c)) {
            final File wav = new File(Util.dir(c), "tmp_" + stamp + ".wav");
            file = wav;
            final boolean[] nativeOk = {false};
            final boolean[] abandoned = {false};
            final Object lock = new Object();
            final Context cc = c;
            Thread w = new Thread(new Runnable() {
                @Override public void run() {
                    boolean r = NativeAdbManager.startRecording(cc);
                    synchronized (lock) {
                        if (r && !abandoned[0]) nativeOk[0] = true;
                        else if (r) NativeAdbManager.abandon(cc); // arrancó tarde: deshacer
                    }
                }
            });
            w.start();
            try { w.join(10000); } catch (InterruptedException ignored) { }
            synchronized (lock) {
                if (nativeOk[0]) {
                    viaNative = true;
                    source = NativeAdbManager.lastSource();
                    return true;
                }
                abandoned[0] = true; // si arranca después, el hilo lo deshará
            }
            if (wav.exists()) wav.delete();
        }

        file = new File(Util.dir(c), "tmp_" + stamp + ".m4a");
        if (Util.shizukuEnabled(c) && ShizukuManager.ready()) {
            if (startShizuku(c)) return true;
        }
        if (startLocal(c)) return true;
        file = null;
        return false;
    }

    private boolean startShizuku(Context c) {
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
            int src = ShizukuManager.service().start(pfd, 0);
            try { pfd.close(); } catch (Exception ignored) { }
            pfd = null;
            if (src >= 0) {
                viaShizuku = true;
                source = src == MediaRecorder.AudioSource.VOICE_CALL
                        ? "Línea completa · Shizuku (ambas partes)"
                        : src == MediaRecorder.AudioSource.VOICE_RECOGNITION
                        ? "Micrófono · Shizuku (solo tu voz)" : "Micrófono · Shizuku";
                return true;
            }
        } catch (Throwable e) {
            if (pfd != null) try { pfd.close(); } catch (Exception ignored) { }
            pfd = null;
        }
        if (file.exists()) file.delete();
        return false;
    }

    private boolean startLocal(Context c) {
        int[] order = Util.fullLine(c)
                ? new int[]{MediaRecorder.AudioSource.VOICE_CALL,
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            MediaRecorder.AudioSource.MIC}
                : new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            MediaRecorder.AudioSource.MIC};
        for (int src : order) {
            MediaRecorder r = new MediaRecorder(c);
            try {
                r.setAudioSource(src);
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                r.setAudioChannels(1);
                r.setAudioSamplingRate(src == MediaRecorder.AudioSource.VOICE_CALL ? 16000 : 44100);
                r.setAudioEncodingBitRate(src == MediaRecorder.AudioSource.VOICE_CALL ? 64000 : 96000);
                r.setOutputFile(file.getAbsolutePath());
                r.prepare();
                r.start();
                recorder = r;
                source = src == MediaRecorder.AudioSource.VOICE_CALL ? "Línea completa (ambas partes)"
                        : src == MediaRecorder.AudioSource.VOICE_RECOGNITION ? "Micrófono (solo tu voz)" : "Micrófono";
                return true;
            } catch (Exception e) {
                try { r.release(); } catch (Exception ignored) { }
                if (file.exists()) file.delete();
            }
        }
        return false;
    }

    boolean running() {
        return recorder != null || viaShizuku || viaNative;
    }

    /** Detiene la grabación. Devuelve el archivo o null si no es válido. */
    File stop() {
        boolean ok = true;
        if (viaNative) {
            viaNative = false;
            File f = (ctx != null && file != null) ? NativeAdbManager.stopRecording(ctx, file) : null;
            file = null;
            return f;
        }
        if (viaShizuku) {
            viaShizuku = false;
            try {
                IRecorderService s = ShizukuManager.service();
                if (s != null) s.stop();
            } catch (Throwable e) {
                ok = false;
            }
        } else {
            if (recorder == null) return null;
            try { recorder.stop(); } catch (Exception e) { ok = false; }
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
        File f = file;
        file = null;
        if (!ok || f == null || !f.exists() || f.length() < 1024) {
            if (f != null) f.delete();
            return null;
        }
        return f;
    }
}
