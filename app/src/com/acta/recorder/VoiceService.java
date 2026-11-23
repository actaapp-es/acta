package com.acta.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Hola Acta»: escucha la frase de activación en el propio móvil (reconocimiento de voz del sistema, sin enviar nada)
 * y, solo después de oírla, graba la orden y se la pasa al agente de Groq.
 */
public class VoiceService extends Service {

    static final String ACTION_STOP = "com.acta.recorder.VOICE_STOP";
    static final String ACTION_LISTEN = "com.acta.recorder.VOICE_LISTEN";
    private static final String CH = "voice";
    private static final int NOTIF = 31;

    /** «Hola Acta» y las formas en que suele transcribirlo el reconocedor. */
    private static final Pattern WAKE = Pattern.compile(
            "^[\\s¡¿\"“«]*(?:h?ola)[\\s,.:;!]+h?acta\\b[\\s,.:;!¡¿?\"”»]*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** Confianza mínima del reconocedor (0-1) para dar por buena la frase de activación, si la informa. */
    private static final float MIN_CONFIDENCE = 0.55f;

    static volatile boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady;
    private volatile boolean busy;      // procesando una orden
    private boolean stopped, once;
    private final Runnable restart = new Runnable() { @Override public void run() { startWake(); } };
    private android.content.BroadcastReceiver screenRx;

    static boolean screenOnly(Context c) { return Util.prefs(c).getBoolean("voice_screen_only", true); }

    private boolean screenOk() {
        if (!screenOnly(this)) return true;
        android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
        return pm == null || pm.isInteractive();
    }
    private int errors;
    private final java.util.Map<String, Runnable> speakDone = new java.util.HashMap<>();

    static boolean enabled(Context c) { return Util.prefs(c).getBoolean("voice_wake", false); }

    static void setEnabled(Context c, boolean on) {
        Util.prefs(c).edit().putBoolean("voice_wake", on).apply();
        if (on) start(c); else c.stopService(new Intent(c, VoiceService.class));
    }

    /** Arranca el servicio (hay que llamar con la app visible o desde un aviso). */
    static void start(Context c) {
        try {
            Intent i = new Intent(c, VoiceService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) { }
    }

    /** Pide una orden ahora mismo, sin decir «Hola Acta». */
    static void listenNow(Context c) {
        try {
            Intent i = new Intent(c, VoiceService.class).setAction(ACTION_LISTEN);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) { }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        screenRx = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    if (screenOnly(c) && !busy) { main.removeCallbacks(restart); destroyRec(); } // micrófono libre con la pantalla apagada
                } else {
                    retryLater(300);
                }
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenRx, f);
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) return;
                try { tts.setLanguage(new Locale("es", "ES")); } catch (Throwable ignored) { }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { finishSpeak(id); }
                    @Override public void onError(String id) { finishSpeak(id); }
                });
                ttsReady = true;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Util.prefs(this).edit().putBoolean("voice_wake", false).apply();
            stopped = true;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!startFg("Di «Hola Acta» y pide algo: llamar, guardar un contacto o programar una llamada.")) { stopSelf(); return START_NOT_STICKY; }
        if (!Util.has(this, android.Manifest.permission.RECORD_AUDIO)) { stopSelf(); return START_NOT_STICKY; }
        boolean listen = intent != null && ACTION_LISTEN.equals(intent.getAction());
        if (listen) once = !enabled(this);
        if (!listen && !enabled(this)) { stopSelf(); return START_NOT_STICKY; } // sin escucha continua: solo bajo demanda
        if (listen) {
            main.post(new Runnable() { @Override public void run() { if (!busy) { destroyRec(); beepAndCapture(true); } } });
        } else if (det == null && !busy) {
            main.post(new Runnable() { @Override public void run() { startWake(); } });
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        stopped = true;
        main.removeCallbacksAndMessages(null);
        destroyRec();
        try { unregisterReceiver(screenRx); } catch (Throwable ignored) { }
        if (tts != null) { try { tts.shutdown(); } catch (Throwable ignored) { } }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Aviso en primer plano

    private boolean startFg(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH, "Agente de voz", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Acta escuchando «Hola Acta»");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, VoiceService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent talk = PendingIntent.getForegroundService(this, 2, new Intent(this, VoiceService.class).setAction(ACTION_LISTEN),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open = PendingIntent.getActivity(this, 3, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_call)
                .setColor(getColor(R.color.accent))
                .setContentTitle("Acta escucha «Hola Acta»")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Hablar ahora", talk).build())
                .addAction(new Notification.Action.Builder(null, "Desactivar", stop).build())
                .build();
        try {
            startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Escucha de «Hola Acta» (local, con tu voz)

    private boolean inCall() { return !CallManager.calls.isEmpty(); }

    private volatile Thread det;
    private volatile boolean detStop;

    private void startWake() {
        if (stopped || busy || det != null) return;
        if (!screenOk()) return; // se reanuda al encender la pantalla
        if (inCall()) { main.postDelayed(restart, 3000); return; }
        if (!WakeWord.enrolled(this)) {
            startFg("Falta grabar tu voz: Ajustes → Agente de voz → «Grabar mi voz». Mientras tanto, usa «Hablar ahora».");
            return;
        }
        detStop = false;
        Thread t = new Thread(new Runnable() { @Override public void run() { detectLoop(); } }, "acta-wake");
        det = t;
        t.start();
    }

    private void stopDetector() {
        detStop = true;
        Thread t = det;
        if (t != null && t != Thread.currentThread()) { try { t.join(1500); } catch (InterruptedException ignored) { } }
        if (det == t) det = null;
    }

    private void retryLater(long ms) {
        if (stopped) return;
        main.removeCallbacks(restart);
        main.postDelayed(restart, Math.max(ms, 300));
    }

    private void destroyRec() { stopDetector(); }

    /** Un único flujo de micrófono, estable. Detecta voz, compara con tu plantilla y, si encaja, recoge la frase entera. */
    private void detectLoop() {
        final int hop = WakeWord.HOP;
        int min = AudioRecord.getMinBufferSize(WakeWord.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord ar = null;
        try {
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, WakeWord.RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, WakeWord.RATE * 2));
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) { ar.release(); ar = null; det = null; retryLater(5000); return; }
            ar.startRecording();
            java.util.List<float[][]> templates = WakeWord.load(this);
            double thr = WakeWord.threshold(this);
            short[] buf = new short[hop];
            short[] pre = new short[hop * 20];      // 200 ms previos al inicio de la voz
            int preFill = 0;
            short[] seg = new short[WakeWord.RATE * 12];
            int segLen = 0;
            double floor = -1;
            int voiceRun = 0, silence = 0;
            int state = 0;                          // 0 reposo, 1 posible frase, 2 capturando orden, 3 descartando
            long lastTry = 0;
            while (!detStop && !stopped) {
                int n = ar.read(buf, 0, hop);
                if (n < hop) { if (n < 0) break; continue; }
                double r = WakeWord.rms(buf, 0, hop);
                if (floor < 0) floor = r;
                boolean voice = r > Math.max(550, floor * 2.8);
                if (state == 0) {
                    floor = floor * 0.97 + r * 0.03;
                    // anillo de pre-roll
                    System.arraycopy(pre, hop, pre, 0, pre.length - hop);
                    System.arraycopy(buf, 0, pre, pre.length - hop, hop);
                    preFill = Math.min(pre.length, preFill + hop);
                    voiceRun = voice ? voiceRun + 1 : 0;
                    if (voiceRun >= 2) {
                        segLen = 0;
                        System.arraycopy(pre, pre.length - preFill, seg, 0, preFill);
                        segLen = preFill;
                        state = 1; silence = 0; lastTry = 0;
                    }
                    continue;
                }
                if (segLen + hop <= seg.length) { System.arraycopy(buf, 0, seg, segLen, hop); segLen += hop; }
                silence = voice ? 0 : silence + 1;
                if (state == 1) {
                    int hops = segLen / hop;
                    boolean timeToTry = hops >= 45 && hops <= 230 && (hops - lastTry >= 8 || silence >= 30);
                    if (timeToTry) {
                        lastTry = hops;
                        float[][] f = WakeWord.features(seg, segLen);
                        if (WakeWord.score(templates, f) < thr) { state = 2; silence = voice ? 0 : silence; }
                    }
                    if (state == 1) {
                        if (hops > 230) state = 3;
                        else if (silence >= 30) { state = 0; preFill = 0; voiceRun = 0; }
                    }
                } else if (state == 3) {
                    if (silence >= 30) { state = 0; preFill = 0; voiceRun = 0; }
                }
                if (state == 2) {
                    // Frase entera: hasta ~0,9 s de silencio o 10 s en total
                    if (silence >= 90 || segLen >= WakeWord.RATE * 10) {
                        final short[] clip = java.util.Arrays.copyOf(seg, segLen);
                        ar.stop(); ar.release(); ar = null;
                        det = null;
                        onCandidate(clip);
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            // el micrófono lo tiene otra app, p. ej.: reintenta más tarde
            det = null;
            retryLater(5000);
        } finally {
            if (ar != null) { try { ar.stop(); } catch (Throwable ignored) { } try { ar.release(); } catch (Throwable ignored) { } }
            if (det == Thread.currentThread()) det = null;
        }
    }

    private long verifyWindowStart;
    private int verifyCount;

    /** Coincidencia local: Groq confirma que de verdad dice «Hola Acta» antes de hacer nada. */
    private void onCandidate(final short[] clip) {
        main.post(new Runnable() { @Override public void run() { busy = true; } });
        // Tope por hora de confirmaciones, para no gastar la cuota de Groq con falsas coincidencias.
        long now = System.currentTimeMillis();
        if (now - verifyWindowStart > 3_600_000L) { verifyWindowStart = now; verifyCount = 0; }
        if (++verifyCount > 40) { resumeQuiet(); return; }
        File wav = new File(getCacheDir(), "voice_wake.wav");
        try {
            writeWav(wav, clip);
            String text = VoiceAgent.transcribe(this, wav, "Conversación en español con un teléfono móvil.");
            //noinspection ResultOfMethodCallIgnored
            wav.delete();
            Matcher m = WAKE.matcher(text == null ? "" : text.trim());
            if (!m.find()) { resumeQuiet(); return; }          // falsa alarma: ni un ruido
            String rest = m.group(1).trim();
            if (rest.split("\\s+").length >= 2 && !rest.isEmpty()) {
                work(rest, null, false);
            } else {
                main.post(new Runnable() { @Override public void run() { beepAndCapture(false); } });
            }
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            wav.delete();
            resumeQuiet();
        }
    }

    private void resumeQuiet() {
        main.post(new Runnable() { @Override public void run() { busy = false; retryLater(1500); } });
    }

    private static void writeWav(File f, short[] pcm) throws Exception {
        try (FileOutputStream fo = new FileOutputStream(f)) {
            fo.write(new byte[44]);
            byte[] b = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) { b[2 * i] = (byte) pcm[i]; b[2 * i + 1] = (byte) (pcm[i] >> 8); }
            fo.write(b);
        }
        writeWavHeader(f, pcm.length * 2L, WakeWord.RATE);
    }

    // ------------------------------------------------------------------ Orden

    private void beepAndCapture(final boolean manual) {
        busy = true;
        stopDetector();
        beep(ToneGenerator.TONE_PROP_BEEP);
        startFg("Te escucho…");
        new Thread(new Runnable() {
            @Override public void run() {
                try { Thread.sleep(450); } catch (InterruptedException ignored) { }
                File wav = recordCommand();
                work(null, wav, manual);
            }
        }).start();
    }

    /** Procesa fuera del hilo principal: transcribe (si hay audio), pregunta a Groq, ejecuta y responde en voz alta. */
    private void work(final String said, final File wav, final boolean manual) {
        new Thread(new Runnable() {
            @Override public void run() {
                String reply;
                try {
                    String text = said;
                    if (text == null) {
                        if (wav == null) { speakThenResume(manual ? "No te he oído." : null); return; }
                        startFg("Entendiendo lo que has dicho…");
                        text = VoiceAgent.transcribe(VoiceService.this, wav, null);
                        //noinspection ResultOfMethodCallIgnored
                        wav.delete();
                    }
                    if (text == null || text.trim().length() < 3) reply = manual ? "No te he entendido." : null;
                    else reply = VoiceAgent.run(VoiceService.this, text.trim());
                } catch (Summarizer.RateLimit rl) {
                    reply = "Groq ha llegado a su límite. Prueba en un momento.";
                } catch (Exception e) {
                    reply = "No he podido entenderte. Revisa tu conexión y tu clave de Groq.";
                }
                if (!manual && reply != null && reply.startsWith("No he podido")) { quiet(reply); reply = null; }
                speakThenResume(reply);
            }
        }).start();
    }

    /** Avisa sin hablar (para no molestar si la activación fue un falso positivo). */
    private void quiet(final String msg) {
        main.post(new Runnable() { @Override public void run() { Toast.makeText(VoiceService.this, msg, Toast.LENGTH_LONG).show(); } });
    }

    private void speakThenResume(final String reply) {
        main.post(new Runnable() {
            @Override public void run() {
                if (reply != null) Toast.makeText(VoiceService.this, reply, Toast.LENGTH_LONG).show();
                startFg("Di «Hola Acta» y pide algo: llamar, guardar un contacto o programar una llamada.");
                final String id = "r" + System.currentTimeMillis();
                Runnable done = new Runnable() { @Override public void run() {
                    busy = false;
                    if (once && !enabled(VoiceService.this)) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); } else retryLater(400);
                } };
                if (reply == null || !ttsReady || inCall()) { done.run(); return; }
                speakDone.put(id, done);
                Bundle p = new Bundle();
                if (tts.speak(reply, TextToSpeech.QUEUE_FLUSH, p, id) != TextToSpeech.SUCCESS) { speakDone.remove(id); done.run(); return; }
                main.postDelayed(new Runnable() { // por si el sintetizador no avisa nunca
                    @Override public void run() { finishSpeak(id); }
                }, 12000);
            }
        });
    }

    private void finishSpeak(final String id) {
        main.post(new Runnable() {
            @Override public void run() {
                Runnable r = speakDone.remove(id);
                if (r != null) r.run();
            }
        });
    }

    private void beep(int tone) {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
            tg.startTone(tone, 160);
            main.postDelayed(new Runnable() { @Override public void run() { tg.release(); } }, 400);
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ Grabar la orden (WAV 16 kHz)

    /** Graba hasta que hay un silencio tras hablar (máx. 12 s). Devuelve null si no se oyó nada. */
    private File recordCommand() {
        final int rate = 16000;
        int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return null;
        AudioRecord ar = null;
        File out = new File(getCacheDir(), "voice_cmd.wav");
        try {
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, rate));
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) return null;
            FileOutputStream fo = new FileOutputStream(out);
            fo.write(new byte[44]);
            ar.startRecording();
            byte[] buf = new byte[rate / 5 * 2]; // 200 ms
            long start = System.currentTimeMillis();
            long lastVoice = 0;
            boolean spoke = false;
            long total = 0;
            double floor = -1;
            while (true) {
                int n = ar.read(buf, 0, buf.length);
                if (n <= 0) break;
                fo.write(buf, 0, n);
                total += n;
                double rms = rms(buf, n);
                if (floor < 0) floor = rms; else if (!spoke) floor = Math.min(floor * 0.9 + rms * 0.1, floor * 1.5 + 50);
                boolean voice = rms > Math.max(900, floor * 3);
                long now = System.currentTimeMillis();
                if (voice) { spoke = true; lastVoice = now; }
                if (spoke && now - lastVoice > 1100) break;
                if (!spoke && now - start > 6000) break;
                if (now - start > 12000) break;
            }
            ar.stop();
            fo.close();
            if (!spoke) { out.delete(); return null; }
            writeWavHeader(out, total, rate);
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            if (ar != null) { try { ar.release(); } catch (Throwable ignored) { } }
        }
    }

    private static double rms(byte[] b, int n) {
        double sum = 0;
        int samples = n / 2;
        for (int i = 0; i < samples; i++) {
            short s = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8));
            sum += (double) s * s;
        }
        return samples == 0 ? 0 : Math.sqrt(sum / samples);
    }

    private static void writeWavHeader(File f, long dataLen, int rate) throws Exception {
        byte[] h = new byte[44];
        long total = dataLen + 36;
        put(h, 0, "RIFF"); le32(h, 4, total); put(h, 8, "WAVE"); put(h, 12, "fmt ");
        le32(h, 16, 16); h[20] = 1; h[22] = 1; le32(h, 24, rate); le32(h, 28, rate * 2L); h[32] = 2; h[34] = 16;
        put(h, 36, "data"); le32(h, 40, dataLen);
        try (RandomAccessFile r = new RandomAccessFile(f, "rw")) { r.seek(0); r.write(h); }
    }

    private static void put(byte[] b, int o, String s) { for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i); }
    private static void le32(byte[] b, int o, long v) { for (int i = 0; i < 4; i++) b[o + i] = (byte) (v >> (8 * i)); }
}
