package com.acta.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.text.TextUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Servicio que Android usa cuando Acta es la app de teléfono predeterminada:
 * recibe todas las llamadas, muestra la pantalla de llamada y graba.
 */
public class ActaInCallService extends InCallService {

    static final String CH_INCOMING = "incoming";
    static final String CH_ONGOING = "ongoing";
    static final int NOTIF_ID = 11;

    private final Recorder recorder = new Recorder();
    private final java.util.List<Integer> marks = new java.util.ArrayList<>();

    /** Marca el segundo actual de la grabación en curso. Devuelve el segundo o -1. */
    int markNow() {
        if (CallManager.recordingCall == null || recorder.startedAt <= 0) return -1;
        int sec = (int) Math.max(0, (System.currentTimeMillis() - recorder.startedAt) / 1000);
        synchronized (marks) {
            if (!marks.isEmpty() && Math.abs(marks.get(marks.size() - 1) - sec) < 2) return sec;
            marks.add(sec);
        }
        return sec;
    }

    int markCount() { synchronized (marks) { return marks.size(); } }
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean foreground, fgsMic;
    private Notification lastNotification;

    /** Con la pantalla de llamada visible, Android permite ampliar el servicio al micrófono. */
    void ensureMicForeground() {
        if (fgsMic || lastNotification == null || CallManager.calls.isEmpty()) return;
        if (!Util.has(this, android.Manifest.permission.RECORD_AUDIO)) return;
        try {
            startForeground(NOTIF_ID, lastNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            foreground = true;
            fgsMic = true;
        } catch (Exception ignored) { }
    }

    private final Call.Callback callback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING) {
                try { SimInfo.learn(ActaInCallService.this, CallManager.number(call), call.getDetails().getAccountHandle()); }
                catch (Throwable ignored) { }
            }
            if (state == Call.STATE_SELECT_PHONE_ACCOUNT) autoSelectSim(call);
            if (state == Call.STATE_ACTIVE) maybeStartRecording(call);
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) finishRecording(call);
            updateNotification();
            CallManager.changed();
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            CallManager.changed();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        CallManager.service = this;
        createChannels();
        try {
            ShizukuManager.init();
            if (Util.shizukuEnabled(this)) ShizukuManager.ensureService();
            if (Util.nativeAdbEnabled(this)) {
                new Thread(new Runnable() { public void run() { NativeAdbManager.ensureConnected(getApplicationContext()); } }).start();
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public void onDestroy() {
        if (CallManager.recordingCall != null) finishRecording(CallManager.recordingCall);
        if (CallManager.service == this) CallManager.service = null;
        super.onDestroy();
    }

    @Override
    public void onCallAdded(Call call) {
        // Papelera de spam: rechaza en silencio los números marcados como spam
        if (CallManager.incoming(call) && call.getState() == Call.STATE_RINGING) {
            String num = CallManager.number(call);
            if (Util.isSpam(this, num)) {
                try {
                    String name = Util.contactName(this, num);
                    Db.get(this).insertSpam(num, name, System.currentTimeMillis());
                    sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(getPackageName()));
                } catch (Exception ignored) { }
                try { call.reject(false, null); } catch (Exception ignored) { }
                return;
            }
        }
        CallManager.calls.add(call);
        call.registerCallback(callback);
        if (call.getState() == Call.STATE_SELECT_PHONE_ACCOUNT) autoSelectSim(call);
        // Pre-conexión insistente durante el tono: deja el ADB listo antes de descolgar,
        // para que al contestar la grabación nativa (ambas voces) arranque al instante.
        if (Util.nativeAdbEnabled(this)) {
            new Thread(new Runnable() {
                public void run() {
                    // Reconexión EN LIMPIO durante el tono: deja una conexión fresca para que al
                    // contestar la grabación sea el PRIMER stream (el canal se degrada tras varios).
                    for (int i = 0; i < 6 && !CallManager.calls.isEmpty(); i++) {
                        if (NativeAdbManager.isBusy()) break; // no tocar la conexión si se está grabando/parando
                        if (NativeAdbManager.forceReconnect(getApplicationContext())) break;
                        try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                    }
                }
            }).start();
        }
        updateNotification();
        Intent i = new Intent(this, InCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
        if (call.getState() == Call.STATE_ACTIVE) maybeStartRecording(call);
        CallManager.changed();
    }

    @Override
    public void onCallRemoved(Call call) {
        finishRecording(call);
        call.unregisterCallback(callback);
        CallManager.calls.remove(call);
        if (CallManager.calls.isEmpty()) {
            AudioRoute.reset();
            if (foreground) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                foreground = false;
                fgsMic = false;
            }
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(NOTIF_ID);
        } else {
            updateNotification();
        }
        CallManager.changed();
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        CallManager.audio = state;
        AudioRoute.observe(state);
        updateNotification();
        CallManager.changed();
    }

    // ---------------------------------------------------------------- Grabación

    private volatile boolean starting;

    private void maybeStartRecording(final Call call) {
        if (CallManager.recordingCall != null || recorder.running() || starting) return;
        boolean in = CallManager.incoming(call);
        if (in && !Util.recordIncoming(this)) return;
        if (!in && !Util.recordOutgoing(this)) return;
        if (!Util.has(this, android.Manifest.permission.RECORD_AUDIO)) return;
        starting = true;
        // En segundo plano, pero SIN bloquear reconectando: graba ya (micro garantizado,
        // nativo si la conexión ya está lista por el keepalive). Nunca se pierde la llamada.
        new Thread(new Runnable() {
            @Override public void run() {
                synchronized (marks) { marks.clear(); }
                final boolean ok = recorder.start(ActaInCallService.this);
                starting = false;
                if (ok) CallManager.recordingCall = call;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (ok) { updateNotification(); CallManager.changed(); }
                    }
                });
            }
        }).start();
    }

    /** Llamada sin SIM elegida: usa la recordada para ese número; si no hay, la pantalla de llamada pregunta. */
    private void autoSelectSim(Call call) {
        try {
            SimInfo.Sim p = SimInfo.preferred(this, CallManager.number(call));
            if (p != null) call.phoneAccountSelected(p.handle, false);
        } catch (Throwable ignored) { }
    }

    private void finishRecording(Call call) {
        if (CallManager.recordingCall != call) return;
        CallManager.recordingCall = null;
        final String source = recorder.source;
        final long recStart = recorder.startedAt;
        final String markList;
        synchronized (marks) {
            StringBuilder mb = new StringBuilder();
            for (int m : marks) { if (mb.length() > 0) mb.append(','); mb.append(m); }
            markList = mb.length() > 0 ? mb.toString() : null;
            marks.clear();
        }
        // Metadatos rápidos en el hilo principal; parar y guardar van en segundo plano
        // porque el modo nativo usa la red al detener (no debe bloquear el hilo principal).
        long connectTmp;
        try { connectTmp = call.getDetails().getConnectTimeMillis(); } catch (Exception e) { connectTmp = 0; }
        final long connect = connectTmp;
        final String number = CallManager.number(call);
        final boolean incoming = CallManager.incoming(call);
        String simTmp = null;
        try {
            if (SimInfo.dual(this)) {
                SimInfo.Sim s = SimInfo.forHandle(this, call.getDetails().getAccountHandle());
                if (s != null) simTmp = s.label();
            }
        } catch (Throwable ignored) { }
        final String simLabel = simTmp;

        new Thread(new Runnable() {
            @Override
            public void run() {
                final File f = recorder.stop();
                if (f == null) return;
                Db.Call c = new Db.Call();
                c.startMs = connect > 0 ? connect : recStart;
                c.durationSec = (int) Math.max(1, (System.currentTimeMillis() - c.startMs) / 1000);
                c.recordedSec = (int) Math.max(1, (System.currentTimeMillis() - recStart) / 1000);
                c.number = number;
                c.direction = incoming ? 1 : 2;
                c.source = source;
                c.marks = markList;
                c.sim = simLabel;
                c.name = Util.contactName(ActaInCallService.this, c.number);
                String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Util.ES).format(new Date(c.startMs));
                String ext = f.getName().endsWith(".wav") ? ".wav" : ".m4a";
                String fname = stamp + "_" + (c.direction == 1 ? "entrante" : "saliente") + "_" + Util.safe(c.number) + ext;
                File target = new File(f.getParentFile(), fname);
                File saved = f.renameTo(target) ? target : f;
                // La grabación con ambas voces sale en WAV sin comprimir (muy pesado): se pasa a AAC mono ligero.
                if (saved.getName().toLowerCase(Locale.ROOT).endsWith(".wav")) {
                    try {
                        File small = new File(saved.getParentFile(), saved.getName().replaceAll("(?i)\\.wav$", ".m4a"));
                        int[] nrg = SpeakerSplit.wavEnergy(saved);
                        if (AudioPack.compress(saved, small)) {
                            AudioPack.saveEnergy(ActaInCallService.this, small.getName(), nrg);
                            //noinspection ResultOfMethodCallIgnored
                            saved.delete();
                            saved = small;
                        }
                    } catch (Throwable ignored) { }
                }
                c.file = saved.getName();
                // Pista de tu voz (para saber quién habla): se guarda aparte, en privado.
                File vad = new File(f.getPath() + ".vad");
                if (vad.exists() && !vad.renameTo(SpeakerSplit.vadFor(ActaInCallService.this, c.file)))
                    //noinspection ResultOfMethodCallIgnored
                    vad.delete();
                // Cifrado en reposo (AES-256-GCM, clave en el Keystore).
                Crypto.encryptFile(ActaInCallService.this, saved);
                long rowId = Db.get(ActaInCallService.this).insert(c);
                sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(getPackageName()));
                // Análisis automático al colgar (si está activado y hay clave).
                if (c.recordedSec >= 5) AnalysisJob.schedule(ActaInCallService.this, rowId);
                // Borrado automático de audios antiguos (si está programado en Ajustes).
                Cleanup.maybeRun(ActaInCallService.this);
                // Aviso si esta llamada no se pudo grabar con ambas voces.
                if (Util.nativeAdbEnabled(ActaInCallService.this) && NativeAdbManager.savedPort(ActaInCallService.this) != 0
                        && (source == null || !source.startsWith("Ambas voces"))) {
                    AdbWatch.lost(ActaInCallService.this, "La última llamada se grabó solo con tu voz.");
                }
            }
        }).start();
    }

    // ---------------------------------------------------------------- Notificación

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel in = new NotificationChannel(CH_INCOMING, "Llamadas entrantes", NotificationManager.IMPORTANCE_HIGH);
        in.setSound(null, (AudioAttributes) null); // el sistema ya reproduce el tono
        in.enableVibration(false);
        nm.createNotificationChannel(in);
        NotificationChannel on = new NotificationChannel(CH_ONGOING, "Llamada en curso", NotificationManager.IMPORTANCE_LOW);
        on.setShowBadge(false);
        nm.createNotificationChannel(on);
    }

    private void updateNotification() {
        Call call = CallManager.primary();
        if (call == null) return;
        String number = CallManager.number(call);
        String name = Util.contactName(this, number);
        String who = !TextUtils.isEmpty(name) ? name
                : TextUtils.isEmpty(number) ? "Número oculto" : Util.number(this, number);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, InCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        // CallStyle: es el formato de llamada que la isla flotante (HyperOS Super Island / Live Updates)
        // muestra con sus botones al minimizar la llamada.
        android.app.Person person = new android.app.Person.Builder().setName(who).setImportant(true).build();

        Notification.Builder b;
        if (call.getState() == Call.STATE_RINGING) {
            PendingIntent answer = PendingIntent.getActivity(this, 1,
                    new Intent(this, InCallActivity.class).setAction(InCallActivity.ACTION_ANSWER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent quick = PendingIntent.getActivity(this, 4,
                    QuickActionsActivity.intent(this, number, null),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            android.app.KeyguardManager km = getSystemService(android.app.KeyguardManager.class);
            boolean redact = AppLock.enabled(this) && km != null && km.isKeyguardLocked();
            String ctx = CallContext.line(this, number, redact);
            b = new Notification.Builder(this, CH_INCOMING)
                    .setContentTitle(who)
                    .setContentText(ctx != null ? ctx : "Llamada entrante")
                    .setFullScreenIntent(open, true)
                    .setStyle(Notification.CallStyle.forIncomingCall(person,
                            action(CallActionReceiver.REJECT, 2), answer));
            if (!TextUtils.isEmpty(number)) {
                b.addAction(new Notification.Action.Builder(null, "Mensaje o recordar", quick).build());
            }
        } else {
            String text = CallManager.recordingCall != null ? "En curso · Grabando" : "En curso";
            if (call.getState() == Call.STATE_DIALING || call.getState() == Call.STATE_CONNECTING) text = "Llamando…";
            if (call.getState() == Call.STATE_HOLDING) text = "En espera";
            boolean muted = CallManager.audio != null && CallManager.audio.isMuted();
            boolean speaker = CallManager.audio != null
                    && CallManager.audio.getRoute() == CallAudioState.ROUTE_SPEAKER;
            b = new Notification.Builder(this, CH_ONGOING)
                    .setContentTitle(who)
                    .setContentText(text)
                    .setStyle(Notification.CallStyle.forOngoingCall(person, action(CallActionReceiver.HANGUP, 3)))
                    .addAction(new Notification.Action.Builder(null, muted ? "Activar micro" : "Silenciar",
                            action(CallActionReceiver.MUTE, 5)).build())
                    .addAction(new Notification.Action.Builder(null, speaker ? (AudioRoute.bluetoothAvailable(CallManager.audio) ? "Bluetooth" : "Teléfono") : "Altavoz",
                            action(CallActionReceiver.SPEAKER, 6)).build());
            long connect = call.getDetails().getConnectTimeMillis();
            if (connect > 0) b.setUsesChronometer(true).setWhen(connect);
        }
        // Pide ser "Live Update" (Android 16): así aparece en la isla y en la barra de estado.
        android.os.Bundle promo = new android.os.Bundle();
        promo.putBoolean("android.requestPromotedOngoing", true);
        promo.putBoolean("android.REQUEST_PROMOTED_ONGOING", true);
        b.addExtras(promo);

        Notification n = b.setSmallIcon(R.drawable.ic_call)
                .setColor(getColor(R.color.accent))
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setContentIntent(open)
                .build();
        lastNotification = n;

        if (!foreground) {
            try {
                boolean mic = Util.has(this, android.Manifest.permission.RECORD_AUDIO);
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                        | (mic ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : 0));
                foreground = true;
                fgsMic = mic;
                return;
            } catch (Exception e) {
                try {
                    startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
                    foreground = true;
                    return;
                } catch (Exception ignored) { }
            }
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, n);
    }

    private PendingIntent action(String a, int rc) {
        return PendingIntent.getBroadcast(this, rc, new Intent(this, CallActionReceiver.class).setAction(a),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
