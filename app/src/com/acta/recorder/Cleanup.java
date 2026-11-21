package com.acta.recorder;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.util.List;

/**
 * Borrado de audios. Solo borra el archivo de sonido: la llamada, su transcripción,
 * el resumen y las tareas se quedan en Acta. Las favoritas nunca se tocan.
 */
public class Cleanup extends JobService {

    static final String PREF_DAYS = "audio_keep_days";
    private static final String PREF_LAST = "audio_clean_last";
    private static final int JOB_ID = 7_301;

    /** Días que se guardan los audios; 0 = nunca se borran solos. */
    static int days(Context c) { return Util.prefs(c).getInt(PREF_DAYS, 0); }

    static void setDays(Context c, int d) {
        Util.prefs(c).edit().putInt(PREF_DAYS, Math.max(0, d)).apply();
        schedule(c, true);
    }

    static boolean hasAudio(Context c, Db.Call call) {
        return call != null && call.file != null && new File(Util.dir(c), call.file).exists();
    }

    /** Borra el audio (y su perfil de voces) de una llamada. Devuelve true si había audio. */
    static boolean deleteAudio(Context c, Db.Call call) {
        if (call == null || call.file == null) return false;
        File f = new File(Util.dir(c), call.file);
        boolean had = f.exists();
        if (had && !f.delete()) return false;
        //noinspection ResultOfMethodCallIgnored
        SpeakerSplit.vadFor(c, call.file).delete();
        //noinspection ResultOfMethodCallIgnored
        AudioPack.nrgFor(c, call.file).delete();
        return had;
    }

    /** Audios que el borrado automático quitaría ahora con ese número de días. */
    static int countEligible(Context c, int days) {
        if (days <= 0) return 0;
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        int n = 0;
        for (Db.Call call : Db.get(c).all())
            if (eligible(c, call, cutoff)) n++;
        return n;
    }

    private static boolean eligible(Context c, Db.Call call, long cutoff) {
        return call.startMs < cutoff && !call.fav && !AutoAnalyzer.isRunning(call.id) && hasAudio(c, call);
    }

    /** Ejecuta el borrado automático. Devuelve cuántos audios ha borrado. */
    static synchronized int run(Context c) {
        int d = days(c);
        Util.prefs(c).edit().putLong(PREF_LAST, System.currentTimeMillis()).apply();
        if (d <= 0) return 0;
        long cutoff = System.currentTimeMillis() - d * 86_400_000L;
        List<Db.Call> all = Db.get(c).all();
        int n = 0;
        for (Db.Call call : all)
            if (eligible(c, call, cutoff) && deleteAudio(c, call)) n++;
        if (n > 0) c.sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(c.getPackageName()));
        return n;
    }

    /** Al abrir la app o al colgar: si hace más de 6 horas de la última pasada, borra en segundo plano. */
    static void maybeRun(Context c) {
        final Context app = c.getApplicationContext();
        if (days(app) <= 0) return;
        long last = Util.prefs(app).getLong(PREF_LAST, 0);
        if (System.currentTimeMillis() - last < 6 * 3_600_000L) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try { Cleanup.run(app); } catch (Throwable ignored) { }
            }
        }, "acta-cleanup").start();
    }

    /** Trabajo diario del sistema para que el borrado ocurra aunque no abras Acta. */
    static void schedule(Context c) { schedule(c, false); }

    private static void schedule(Context c, boolean force) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        if (days(c) <= 0) { js.cancel(JOB_ID); return; }
        if (!force && js.getPendingJob(JOB_ID) != null) return; // ya programado: no reinicia su reloj
        try {
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, Cleanup.class))
                    .setPeriodic(24 * 3_600_000L, 6 * 3_600_000L)
                    .setPersisted(true)
                    .build());
        } catch (Throwable ignored) { }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try { Cleanup.run(app); } catch (Throwable ignored) { }
                jobFinished(params, false);
            }
        }, "acta-cleanup-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
