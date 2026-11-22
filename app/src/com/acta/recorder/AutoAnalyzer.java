package com.acta.recorder;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.PowerManager;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Análisis con IA en la propia app, sin esperar al planificador del sistema (que en Xiaomi
 * puede retrasarse indefinidamente). Arranca al colgar, reintenta si no hay red y, si la app
 * se cerró antes de terminar, se reanuda al abrirla. Un análisis cada vez, en orden.
 */
final class AutoAnalyzer {

    private AutoAnalyzer() { }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Set<Long> RUNNING = Collections.synchronizedSet(new HashSet<Long>());

    static final int DONE = 0, RETRY = 1, FAILED = 2;

    static boolean isRunning(long id) { return RUNNING.contains(id); }

    /** Error del último intento automático (o null). */
    static String lastError(Context c, long id) {
        return Util.prefs(c).getString("ai_err_" + id, null);
    }

    /** Análisis automático al colgar: solo si está activado en Ajustes y hay clave. */
    static boolean startAuto(Context c, long id) {
        if (!AnalysisJob.enabled(c) || !Summarizer.hasKey(c) || !AiConsent.granted(c)) return false;
        return start(c, id, false);
    }

    /** Lanza (o encola) el análisis de una llamada. manual=true rehace aunque ya tenga uno. */
    static boolean start(Context c, final long id, final boolean manual) {
        if (id <= 0 || !Summarizer.hasKey(c) || !AiConsent.granted(c)) return false;
        if (!RUNNING.add(id)) return true;
        final Context app = c.getApplicationContext();
        Util.prefs(app).edit().remove("ai_err_" + id).apply();
        notifyChanged(app);
        EXEC.execute(new Runnable() {
            @Override public void run() {
                PowerManager.WakeLock wl = null;
                try {
                    PowerManager pm = app.getSystemService(PowerManager.class);
                    if (pm != null) {
                        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "acta:analysis");
                        wl.acquire(8 * 60_000L);
                    }
                    long[] waits = {0, 15_000, 45_000, 90_000};
                    for (int i = 0; i < waits.length; i++) {
                        if (waits[i] > 0) sleep(waits[i]);
                        if (i > 0) waitForNetwork(app, 60_000);
                        int r = analyzeOnce(app, id, manual);
                        if (r != RETRY) break;
                    }
                } catch (Throwable ignored) {
                } finally {
                    RUNNING.remove(id);
                    AnalysisJob.setBusy(app, id, false);
                    if (wl != null && wl.isHeld()) try { wl.release(); } catch (Throwable ignored) { }
                    notifyChanged(app);
                }
            }
        });
        return true;
    }

    /** Un intento. Guarda el resultado o el error. */
    static int analyzeOnce(Context c, long id, boolean manual) {
        Db.Call call = Db.get(c).byId(id);
        if (call == null) return FAILED;
        Summarizer.Result previous = Summarizer.parse(call.summary);
        if (previous != null && !manual) return DONE;
        File file = new File(Util.dir(c), call.file);
        if (!file.exists()) {
            setError(c, id, "El archivo de audio no está disponible.");
            return FAILED;
        }
        try {
            Summarizer.Output o = Summarizer.analyze(c, call, file);
            // Conserva las tareas ya marcadas como hechas si se repiten en el nuevo análisis.
            if (previous != null) for (Summarizer.Task nt : o.result.tasks)
                for (Summarizer.Task pt : previous.tasks)
                    if (pt.done && pt.title.equalsIgnoreCase(nt.title)) nt.done = true;
            Db.get(c).updateAnalysis(id, o.result.toJson(), o.transcript, o.segments);
            Util.prefs(c).edit().remove("ai_err_" + id).remove("ai_try_" + id).apply();
            Summarizer.clearCache(c, id);
            if (!manual) { AnalysisJob.suggestContact(c, call, o.result); AnalysisJob.notifyDone(c, call, o.result); }
            return DONE;
        } catch (Summarizer.RateLimit rl) {
            // Espera lo que pide Groq (hasta 15 min; si no lo dice, 1 min) y reintenta sin repetir la transcripción.
            int sec = rl.seconds > 0 ? rl.seconds : 60;
            if (sec > 900) { setError(c, id, rl.getMessage()); return FAILED; }
            setError(c, id, "Límite de Groq alcanzado. Reintento automático en " + Summarizer.waitText(sec) + ".");
            notifyChanged(c);
            sleep((sec + 2) * 1000L);
            return RETRY;
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : "No se pudo analizar la llamada.";
            setError(c, id, msg);
            String m = msg.toLowerCase(Util.ES);
            if (m.contains("clave") || m.contains("no hay audio") || m.contains("demasiado")
                    || m.contains("401") || m.contains("invalid api key")) return FAILED;
            return RETRY;
        }
    }

    /** Al abrir la app: retoma las llamadas recientes que se quedaron sin analizar. */
    static void resumePending(Context c) {
        if (!AnalysisJob.enabled(c) || !Summarizer.hasKey(c) || !AiConsent.granted(c)) return;
        long since = System.currentTimeMillis() - 3 * 86_400_000L;
        List<Db.Call> all = Db.get(c).all();
        for (Db.Call call : all) {
            if (call.startMs < since) break;
            if (call.recordedSec < 5 || (call.summary != null && !call.summary.isEmpty())) continue;
            if (isRunning(call.id) || lastError(c, call.id) != null) continue;
            if (!new File(Util.dir(c), call.file).exists()) continue;
            start(c, call.id, false);
        }
    }

    private static void setError(Context c, long id, String msg) {
        Util.prefs(c).edit().putString("ai_err_" + id, msg).apply();
    }

    private static void notifyChanged(Context c) {
        c.sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(c.getPackageName()));
    }

    private static void waitForNetwork(Context c, long maxMs) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        if (cm == null) return;
        long end = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < end) {
            Network n = cm.getActiveNetwork();
            if (n != null) return;
            sleep(3000);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
