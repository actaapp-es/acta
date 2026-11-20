package com.acta.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Aviso de versiones nuevas (solo versión completa, que se instala fuera de la tienda).
 * Una vez al día pregunta a la API pública de GitHub cuál es la última versión publicada. No envía
 * ningún dato tuyo; GitHub solo ve la dirección IP, como con cualquier descarga. Si hay una más nueva,
 * muestra una notificación que abre la página oficial de esa versión. Se puede desactivar en Ajustes.
 */
public class UpdateCheck extends JobService {

    static final String PREF = "update_check";
    private static final String PREF_LAST = "update_last";
    private static final String PREF_NOTIFIED = "update_notified";
    private static final int JOB_ID = 7_302;
    private static final String CH = "updates";

    static boolean enabled(Context c) { return Util.prefs(c).getBoolean(PREF, true); }

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        if (!enabled(c)) { js.cancel(JOB_ID); return; }
        if (js.getPendingJob(JOB_ID) != null) return;
        try {
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateCheck.class))
                    .setPeriodic(24 * 3_600_000L, 6 * 3_600_000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build());
        } catch (Throwable ignored) { }
    }

    /** Al abrir la app: si hace más de 12 horas de la última consulta, la hace en segundo plano. */
    static void maybeRun(Context c) {
        final Context app = c.getApplicationContext();
        if (!enabled(app)) return;
        if (System.currentTimeMillis() - Util.prefs(app).getLong(PREF_LAST, 0) < 12 * 3_600_000L) return;
        new Thread(new Runnable() {
            @Override public void run() { try { check(app); } catch (Throwable ignored) { } }
        }, "acta-update").start();
    }

    static synchronized void check(Context c) throws Exception {
        if (!enabled(c)) return;
        Util.prefs(c).edit().putLong(PREF_LAST, System.currentTimeMillis()).apply();
        HttpURLConnection h = (HttpURLConnection) new URL("https://api.github.com/repos/" + Project.REPO + "/releases/latest").openConnection();
        h.setConnectTimeout(15_000);
        h.setReadTimeout(15_000);
        h.setRequestProperty("Accept", "application/vnd.github+json");
        h.setRequestProperty("User-Agent", "Acta");
        try {
            if (h.getResponseCode() != 200) return;
            InputStream in = h.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 512 * 1024) out.write(buf, 0, n);
            JSONObject j = new JSONObject(out.toString("UTF-8"));
            if (j.optBoolean("draft") || j.optBoolean("prerelease")) return;
            String tag = j.optString("tag_name", "").trim();
            String url = j.optString("html_url", "");
            // Solo se abre la página oficial de las versiones del proyecto.
            if (tag.isEmpty() || !url.startsWith(Project.URL_REPO + "/releases/")) return;
            String local = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            if (!newer(tag, local)) return;
            if (tag.equals(Util.prefs(c).getString(PREF_NOTIFIED, ""))) return;
            if (notifyNew(c, tag, url)) Util.prefs(c).edit().putString(PREF_NOTIFIED, tag).apply();
        } finally {
            h.disconnect();
        }
    }

    /** true si la versión remota (p. ej. «v5.1.7») es mayor que la instalada (p. ej. «5.1.6»). */
    static boolean newer(String remote, String local) {
        int[] a = parse(remote), b = parse(local);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    private static int[] parse(String v) {
        int[] r = new int[3];
        if (v == null) return r;
        String[] p = v.trim().replaceFirst("^[vV]", "").split("[^0-9]+");
        for (int i = 0, k = 0; i < p.length && k < 3; i++) {
            if (p[i].isEmpty()) continue;
            try { r[k++] = Integer.parseInt(p[i]); } catch (NumberFormatException e) { r[k++] = 0; }
        }
        return r;
    }

    private static boolean notifyNew(Context c, String tag, String url) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return false;
        NotificationChannel ch = new NotificationChannel(CH, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Aviso cuando hay una versión nueva de Acta");
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(c, 7302,
                new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = "Ya puedes descargar " + tag.replaceFirst("^[vV]", "") + ". Toca para ver las novedades y bajar el APK.";
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_call)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle("Hay una versión nueva de Acta")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(7302, n);
        return true;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try { check(app); } catch (Throwable ignored) { }
                jobFinished(params, false);
            }
        }, "acta-update-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
