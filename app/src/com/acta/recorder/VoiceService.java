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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
    private SpeechRecognizer rec;
    private TextToSpeech tts;
    private boolean ttsReady;
    private volatile boolean busy;      // procesando una orden
    private boolean stopped;
    private int errors;
    private final java.util.Map<String, Runnable> speakDone = new java.util.HashMap<>();

    static boolean enabled(Context c) { return Util.prefs(c).getBoolean("voice_on", false); }

    static void setEnabled(Context c, boolean on) {
        Util.prefs(c).edit().putBoolean("voice_on", on).apply();
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
            Util.prefs(this).edit().putBoolean("voice_on", false).apply();
            stopped = true;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!startFg("Di «Hola Acta» y pide algo: llamar, guardar un contacto o programar una llamada.")) { stopSelf(); return START_NOT_STICKY; }
        if (!Util.has(this, android.Manifest.permission.RECORD_AUDIO)) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && ACTION_LISTEN.equals(intent.getAction())) {
            main.post(new Runnable() { @Override public void run() { if (!busy) { destroyRec(); beepAndCapture(true); } } });
        } else if (rec == null && !busy) {
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

    // ------------------------------------------------------------------ Escucha de «Hola Acta»

    private boolean inCall() { return !CallManager.calls.isEmpty(); }

    private void startWake() {
        if (stopped || busy) return;
        if (inCall()) { main.postDelayed(new Runnable() { @Override public void run() { startWake(); } }, 3000); return; }
        try {
            if (rec == null) {
                if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
                    rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                else if (SpeechRecognizer.isRecognitionAvailable(this))
                    rec = SpeechRecognizer.createSpeechRecognizer(this);
                else {
                    // Sin reconocimiento de voz: solo funciona el botón «Hablar ahora» del aviso.
                    startFg("Tu móvil no tiene reconocimiento de voz instalado. Usa «Hablar ahora».");
                    return;
                }
                rec.setRecognitionListener(listener);
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            rec.startListening(i);
        } catch (Throwable t) {
            destroyRec();
            retryLater(2000);
        }
    }

    private void retryLater(long ms) {
        if (stopped) return;
        main.postDelayed(new Runnable() { @Override public void run() { startWake(); } }, ms);
    }

    private void destroyRec() {
        if (rec != null) {
            try { rec.cancel(); } catch (Throwable ignored) { }
            try { rec.destroy(); } catch (Throwable ignored) { }
            rec = null;
        }
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle b) { errors = 0; }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float v) { }
        @Override public void onBufferReceived(byte[] b) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int t, Bundle b) { }

        @Override public void onPartialResults(Bundle b) { }

        @Override
        public void onResults(Bundle b) {
            if (busy) return;
            // Solo vale la frase COMPLETA, al principio de lo que se dijo, y solo la mejor transcripción.
            ArrayList<String> all = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (all == null || all.isEmpty()) { retryLater(250); return; }
            Matcher m = WAKE.matcher(all.get(0).trim());
            if (!m.find()) { retryLater(250); return; }
            float[] conf = b.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (conf != null && conf.length > 0 && conf[0] >= 0 && conf[0] < MIN_CONFIDENCE) { retryLater(250); return; }
            onWakeWord(m.group(1).trim());
        }

        @Override
        public void onError(int code) {
            if (busy) return;
            if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { stopSelf(); return; }
            // Silencio o sin coincidencia: lo normal. Otros errores: recrea el reconocedor con espera creciente.
            if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { retryLater(200); return; }
            destroyRec();
            errors++;
            retryLater(Math.min(30000, 1000L * (1L << Math.min(errors, 5))));
        }
    };

    private static String best(Bundle b) {
        ArrayList<String> l = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    // ------------------------------------------------------------------ Orden

    /** Se oyó «Hola Acta». Si la orden venía en la misma frase se usa; si no, se graba y transcribe con Groq. */
    private void onWakeWord(String rest) {
        if (busy) return;
        busy = true;
        destroyRec();
        if (rest.trim().split("\\s+").length >= 2 && !rest.trim().isEmpty()) {
            final String said = rest.trim();
            work(said, null, false);
        } else {
            beepAndCapture(false);
        }
    }

    private void beepAndCapture(final boolean manual) {
        busy = true;
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
                        text = VoiceAgent.transcribe(VoiceService.this, wav);
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
                Runnable done = new Runnable() { @Override public void run() { busy = false; retryLater(400); } };
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
