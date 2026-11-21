package com.acta.recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * Se ejecuta como usuario "shell" vía app_process, lanzado por el cliente ADB propio.
 * Motor AudioRecord (nativo): lee PCM directamente de AudioFlinger. Con permisos de
 * shell, VOICE_CALL captura ambas partes de la llamada.
 *
 * Modo robusto "arfile": graba a un archivo WAV hasta que aparece un "archivo de parada".
 * Así NO depende de que el canal ADB siga abierto: si el ADB parpadea a mitad de la
 * llamada, el proceso sigue grabando (se lanza con nohup) y la grabación no se corta.
 *
 * Modos (args[1]):
 *   "arfile" + args[2]=<archivoParada>   AudioRecord, escalera, para cuando existe ese archivo.
 *   "ar"                                 AudioRecord, escalera, para por stdin (modo antiguo).
 *   "ar<n>"                              AudioRecord SOLO fuente n, 3 s (diagnóstico).
 *   "diag" / "<n>"                       MediaRecorder (referencia/diagnóstico).
 */
public final class AdbRecorderMain {

    static void log(String s) { System.out.println(s); System.out.flush(); }

    static final int[] SRC = {
            MediaRecorder.AudioSource.VOICE_CALL,          // 4  ambas voces
            3,                                             // VOICE_DOWNLINK (voz del otro)
            2,                                             // VOICE_UPLINK (tu voz por la línea)
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // 7
            MediaRecorder.AudioSource.VOICE_RECOGNITION,   // 6  solo micrófono
            MediaRecorder.AudioSource.MIC                  // 1  solo micrófono
    };

    static String name(int s) {
        switch (s) {
            case 4: return "VOICE_CALL";
            case 7: return "VOICE_COMMUNICATION";
            case 3: return "VOICE_DOWNLINK";
            case 2: return "VOICE_UPLINK";
            case 6: return "VOICE_RECOGNITION";
            case 1: return "MIC";
            default: return "SRC" + s;
        }
    }

    /** Si es true, además de la llamada se capta tu voz por separado para saber quién habla. */
    static boolean split;

    static int rateFor(int s) { return (s == 4 || s == 3 || s == 2) ? 16000 : 44100; }

    public static void main(String[] args) {
        try { android.os.Looper.prepareMainLooper(); } catch (Throwable ignored) { }
        if (args.length < 1) { log("ERR no-output"); return; }
        File out = new File(args[0]);
        String mode = args.length > 1 ? args[1].trim() : "";

        if ("arfile".equals(mode)) {
            File stopFlag = new File(args.length > 2 ? args[2] : (args[0] + ".stop"));
            split = args.length > 3 && "split".equals(args[3].trim());
            runAudioRecord(SRC, out, -1, stopFlag);
            return;
        }
        boolean useAr = mode.startsWith("ar");
        boolean diag = "diag".equals(mode);
        int single = -1;
        String num = useAr ? mode.substring(2) : mode;
        if (!diag && !num.isEmpty()) {
            try { single = Integer.parseInt(num.trim()); } catch (Exception ignored) { }
        }
        boolean shortMode = diag || single >= 0;
        int[] sources = single >= 0 ? new int[]{single} : SRC;
        log("START engine=" + (useAr ? "AudioRecord" : "MediaRecorder")
                + (single >= 0 ? " single=" + name(single) : "") + (diag ? " diag" : ""));
        if (useAr) runAudioRecord(sources, out, shortMode ? 3000 : -1, null);
        else runMediaRecorder(sources, out, shortMode, single);
    }

    // ---- Motor AudioRecord (nativo) ----
    // durationMs>=0: graba ese tiempo. stopFlag!=null: graba hasta que exista ese archivo.
    // durationMs<0 && stopFlag==null: graba hasta señal por stdin.
    static void runAudioRecord(int[] sources, File out, int durationMs, File stopFlag) {
        for (int src : sources) {
            log("TRY " + name(src));
            int rate = rateFor(src);
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) min = rate * 2;
            int buf = Math.max(min * 2, rate);
            AudioRecord ar = null;
            try {
                ar = new AudioRecord(src, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf * 2);
                if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
                    log("FAIL " + name(src) + ": no inicializa (state=" + ar.getState() + ")");
                    ar.release(); continue;
                }
                ar.startRecording();
                if (ar.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    log("FAIL " + name(src) + ": no graba (state=" + ar.getRecordingState() + ")");
                    ar.stop(); ar.release(); continue;
                }
                log("REC " + name(src));
                Tracker tracker = (split && src == MediaRecorder.AudioSource.VOICE_CALL && stopFlag != null)
                        ? new Tracker(new File(out.getPath() + ".vad")) : null;
                long total;
                try {
                    total = writePcm(ar, out, rate, buf, durationMs, stopFlag, tracker);
                } finally {
                    if (tracker != null) tracker.finish();
                }
                try { ar.stop(); } catch (Throwable ignored) { }
                try { ar.release(); } catch (Throwable ignored) { }
                log("DONE " + name(src) + " bytes=" + total);
                if (stopFlag != null) try { stopFlag.delete(); } catch (Throwable ignored) { }
                System.exit(0);
                return;
            } catch (Throwable e) {
                log("FAIL " + name(src) + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
                if (ar != null) try { ar.release(); } catch (Throwable ignored) { }
            }
        }
        log("ALLFAIL");
    }

    static long writePcm(AudioRecord ar, File out, int rate, int buf, int durationMs, File stopFlag) throws Exception {
        return writePcm(ar, out, rate, buf, durationMs, stopFlag, null);
    }

    static long writePcm(AudioRecord ar, File out, int rate, int buf, int durationMs, File stopFlag, Tracker tracker) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(out, "rw");
        raf.setLength(0);
        writeWavHeader(raf, rate, 0);
        byte[] data = new byte[buf];
        long total = 0;
        long end = durationMs < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs;
        long maxEnd = System.currentTimeMillis() + 3L * 3600 * 1000; // tope de seguridad: 3 h
        final boolean[] stdinStop = {false};
        if (durationMs < 0 && stopFlag == null) {
            Thread t = new Thread(new Runnable() {
                public void run() { try { System.in.read(); } catch (Throwable ignored) { } stdinStop[0] = true; }
            });
            t.setDaemon(true); t.start();
        }
        long lastFlagCheck = 0;
        long lastBeat = System.currentTimeMillis();
        // Pista de tu voz: arranca a los 1,5 s. Protección: si al arrancarla la llamada se queda en
        // silencio digital (ceros exactos), se apaga y la grabación principal sigue como siempre.
        long recStart = System.nanoTime();
        boolean preSignal = false;
        double zeroRun = 0;
        while (System.currentTimeMillis() < end && System.currentTimeMillis() < maxEnd && !stdinStop[0]) {
            if (stopFlag != null) {
                long now = System.currentTimeMillis();
                if (now - lastFlagCheck > 150) { lastFlagCheck = now; if (stopFlag.exists()) break; }
            }
            // Latido solo en modo "atado" (sin flag): mantiene el canal con datos. En modo arfile NO,
            // porque nadie drena la salida y bloquearía la grabación.
            long now = System.currentTimeMillis();
            if (stopFlag == null && now - lastBeat > 700) { lastBeat = now; try { System.out.write('.'); System.out.flush(); } catch (Throwable ignored) { } }
            int n = ar.read(data, 0, data.length);
            if (n > 0 && tracker != null && !tracker.dead) {
                boolean allZero = true;
                for (int i = 0; i < n; i++) if (data[i] != 0) { allZero = false; break; }
                if (!tracker.started) {
                    if (!allZero) preSignal = true;
                    if ((System.nanoTime() - recStart) / 1_000_000L >= 1500) tracker.start(recStart);
                } else {
                    zeroRun = allZero ? zeroRun + (double) n / (rate * 2) : 0;
                    if (preSignal && zeroRun > 1.2) {
                        tracker.kill();
                        log("VAD_OFF silenced");
                    }
                }
            }
            if (n > 0) {
                raf.write(data, 0, n); total += n;
                // Refresca la cabecera de vez en cuando para que el WAV sea válido aunque se corte.
                if ((total % (rate * 4)) < data.length) patchWavHeader(raf, total);
            } else if (n < 0) { log("READERR " + n); break; }
        }
        patchWavHeader(raf, total);
        raf.close();
        return total;
    }

    static void writeWavHeader(RandomAccessFile raf, int rate, int dataLen) throws Exception {
        int ch = 1, bits = 16, byteRate = rate * ch * bits / 8;
        raf.seek(0);
        raf.writeBytes("RIFF"); writeIntLE(raf, 36 + dataLen); raf.writeBytes("WAVE");
        raf.writeBytes("fmt "); writeIntLE(raf, 16); writeShortLE(raf, 1); writeShortLE(raf, ch);
        writeIntLE(raf, rate); writeIntLE(raf, byteRate); writeShortLE(raf, ch * bits / 8); writeShortLE(raf, bits);
        raf.writeBytes("data"); writeIntLE(raf, dataLen);
    }

    static void patchWavHeader(RandomAccessFile raf, long dataLen) throws Exception {
        long pos = raf.getFilePointer();
        raf.seek(4);  writeIntLE(raf, (int) (36 + dataLen));
        raf.seek(40); writeIntLE(raf, (int) dataLen);
        raf.seek(pos);
    }

    static void writeIntLE(RandomAccessFile raf, int v) throws Exception {
        raf.write(v & 0xff); raf.write((v >> 8) & 0xff); raf.write((v >> 16) & 0xff); raf.write((v >> 24) & 0xff);
    }
    static void writeShortLE(RandomAccessFile raf, int v) throws Exception {
        raf.write(v & 0xff); raf.write((v >> 8) & 0xff);
    }

    // ---- Pista de tu voz (para saber quién habla) ----

    /**
     * Capta TU voz por separado (VOICE_UPLINK y, si no se puede, el micrófono) y guarda solo su
     * energía cada 100 ms en <wav>.vad: "AVAD", versión, fuente, retardo (ms) y un short por bloque.
     * Nunca guarda audio. Cualquier fallo se ignora: la grabación principal no depende de esto.
     */
    static final class Tracker implements Runnable {
        final File file;
        volatile boolean started, dead, stop;
        long recStart;
        Thread thread;

        Tracker(File f) { file = f; }

        void start(long primaryStart) {
            started = true;
            recStart = primaryStart;
            thread = new Thread(this, "acta-vad");
            thread.setDaemon(true);
            thread.start();
        }

        void kill() {
            dead = true;
            stop = true;
            try { if (thread != null) thread.join(800); } catch (Throwable ignored) { }
            try { file.delete(); } catch (Throwable ignored) { }
        }

        void finish() {
            stop = true;
            try { if (thread != null) thread.join(800); } catch (Throwable ignored) { }
            if (dead) try { file.delete(); } catch (Throwable ignored) { }
        }

        @Override public void run() {
            int[] srcs = {2, MediaRecorder.AudioSource.MIC};
            AudioRecord ar = null;
            int used = -1;
            final int rate = 16000;
            for (int s : srcs) {
                try {
                    int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    if (min <= 0) min = 6400;
                    AudioRecord a = new AudioRecord(s, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(min * 2, 12800));
                    if (a.getState() != AudioRecord.STATE_INITIALIZED) { a.release(); continue; }
                    a.startRecording();
                    if (a.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) { try { a.release(); } catch (Throwable ignored) { } continue; }
                    ar = a;
                    used = s;
                    break;
                } catch (Throwable ignored) { }
            }
            if (ar == null) { log("VAD none"); dead = true; return; }
            log("VAD " + name(used));
            java.io.BufferedOutputStream os = null;
            try {
                os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(file));
                short[] frame = new short[1600]; // 100 ms
                boolean header = false;
                int frames = 0;
                while (!stop && !dead) {
                    int got = 0;
                    while (got < frame.length && !stop && !dead) {
                        int r = ar.read(frame, got, frame.length - got);
                        if (r < 0) { stop = true; break; }
                        got += r;
                    }
                    if (got <= 0) break;
                    if (!header) {
                        long delay = (System.nanoTime() - recStart) / 1_000_000L - 100;
                        os.write(new byte[]{'A', 'V', 'A', 'D', 1, (byte) used});
                        int d = (int) Math.max(0, delay);
                        os.write(new byte[]{(byte) d, (byte) (d >> 8), (byte) (d >> 16), (byte) (d >> 24)});
                        header = true;
                    }
                    double sum = 0;
                    for (int i = 0; i < got; i++) sum += (double) frame[i] * frame[i];
                    int rms = (int) Math.min(32767, Math.sqrt(sum / got));
                    os.write(rms & 0xff);
                    os.write((rms >> 8) & 0xff);
                    if (++frames % 20 == 0) os.flush();
                }
            } catch (Throwable ignored) {
            } finally {
                try { if (os != null) os.close(); } catch (Throwable ignored) { }
                try { ar.stop(); } catch (Throwable ignored) { }
                try { ar.release(); } catch (Throwable ignored) { }
            }
        }
    }

    // ---- Motor MediaRecorder (referencia/diagnóstico) ----
    static void runMediaRecorder(int[] sources, File out, boolean shortMode, int single) {
        MediaRecorder recorder = null;
        int used = -1;
        for (int src : sources) {
            log("TRY " + name(src));
            MediaRecorder r = new MediaRecorder();
            try {
                r.setAudioSource(src);
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                r.setAudioChannels(1);
                r.setAudioSamplingRate(src == MediaRecorder.AudioSource.VOICE_CALL ? 16000 : 44100);
                r.setAudioEncodingBitRate(src == MediaRecorder.AudioSource.VOICE_CALL ? 32000 : 48000);
                r.setOutputFile(out.getAbsolutePath());
                r.prepare(); r.start();
                recorder = r; used = src; log("REC " + name(src)); break;
            } catch (Throwable e) {
                log("FAIL " + name(src) + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
                try { r.release(); } catch (Throwable ignored) { }
            }
        }
        if (recorder == null) { log("ALLFAIL"); return; }
        if (shortMode) {
            try { Thread.sleep(single >= 0 ? 3000 : 4000); } catch (Throwable ignored) { }
            try { recorder.stop(); } catch (Throwable e) { log("STOPERR " + e.getMessage()); }
            try { recorder.release(); } catch (Throwable ignored) { }
            log("DONE " + name(used) + " bytes=" + out.length()); System.exit(0); return;
        }
        try { System.in.read(); } catch (Throwable ignored) { }
        try { recorder.stop(); } catch (Throwable ignored) { }
        try { recorder.release(); } catch (Throwable ignored) { }
        log("DONE " + name(used) + " bytes=" + out.length()); System.exit(0);
    }
}
