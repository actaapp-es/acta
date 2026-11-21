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
import android.os.Build;
import android.os.PersistableBundle;
import android.text.TextUtils;


/**
 * Análisis automático al colgar. Es un trabajo del sistema: espera a tener internet,
 * reintenta si falla la red y avisa con una notificación cuando las tareas están listas.
 */
public class AnalysisJob extends JobService {

    static final String CH = "acta_ai";

    static boolean enabled(Context c) { return Util.prefs(c).getBoolean("ai_auto", true); }

    /**
     * Al colgar: arranca el análisis al momento en la app y deja un trabajo de respaldo del sistema
     * por si la app se cierra antes de terminar (se ejecuta en unos minutos, cuando haya red).
     */
    static void schedule(Context c, long callId) {
        if (!enabled(c) || !Summarizer.hasKey(c) || !AiConsent.granted(c) || callId <= 0) return;
        AutoAnalyzer.startAuto(c, callId);
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        PersistableBundle ex = new PersistableBundle();
        ex.putLong("id", callId);
        int jobId = 50_000 + (int) (callId % 100_000);
        try {
            js.schedule(new JobInfo.Builder(jobId, new ComponentName(c, AnalysisJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(4 * 60_000L)
                    .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .setPersisted(true)
                    .setExtras(ex)
                    .build());
        } catch (Throwable ignored) { }
    }

    // Marca "analizando" para que la pantalla de la llamada lo muestre.
    static void setBusy(Context c, long id, boolean busy) {
        if (busy) Util.prefs(c).edit().putLong("ai_busy_" + id, System.currentTimeMillis()).apply();
        else Util.prefs(c).edit().remove("ai_busy_" + id).apply();
    }

    static boolean isBusy(Context c, long id) { return AutoAnalyzer.isRunning(id); }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final long id = params.getExtras().getLong("id", -1);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                boolean retry = false;
                try {
                    Db.Call call = Db.get(app).byId(id);
                    boolean pending = call != null && TextUtils.isEmpty(call.summary)
                            && AutoAnalyzer.lastError(app, id) == null;
                    if (pending || AutoAnalyzer.isRunning(id)) {
                        AutoAnalyzer.start(app, id, false);
                        long end = System.currentTimeMillis() + 9 * 60_000L;
                        while (AutoAnalyzer.isRunning(id) && System.currentTimeMillis() < end) {
                            try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                        }
                        Db.Call after = Db.get(app).byId(id);
                        String err = AutoAnalyzer.lastError(app, id);
                        int tries = Util.prefs(app).getInt("ai_try_" + id, 0) + 1;
                        Util.prefs(app).edit().putInt("ai_try_" + id, tries).apply();
                        retry = after != null && TextUtils.isEmpty(after.summary) && tries < 4
                                && (err == null || !err.toLowerCase(Util.ES).contains("clave"));
                        // Se borra el error para que el reintento del sistema lo vuelva a intentar.
                        if (retry) Util.prefs(app).edit().remove("ai_err_" + id).apply();
                    }
                } catch (Throwable ignored) {
                } finally {
                    jobFinished(params, retry);
                }
            }
        }, "acta-ai-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }

    /**
     * Si el número no está en tus contactos y en la conversación la otra persona se presentó,
     * avisa para añadirla a contactos con el nombre y el número ya rellenados.
     */
    static void suggestContact(Context c, Db.Call call, Summarizer.Result r) {
        if (r == null || r.person.isEmpty() || TextUtils.isEmpty(call.number)) return;
        if (!TextUtils.isEmpty(Util.contactName(c, call.number))) return; // ya es un contacto
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        NotificationChannel ch = new NotificationChannel("new_contact", "Contactos nuevos", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Propone añadir a contactos a quien se presenta en una llamada");
        nm.createNotificationChannel(ch);
        Intent add = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        add.setType(android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        add.putExtra(android.provider.ContactsContract.Intents.Insert.NAME, r.person);
        add.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, call.number);
        add.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(c, 60_000 + (int) (call.id % 10_000), add,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = AppLock.enabled(c)
                ? "Toca para añadir este número a tus contactos."
                : "Se presentó como «" + r.person + "» (" + Util.number(c, call.number) + "). Toca para añadirlo a tus contactos.";
        Notification n = new Notification.Builder(c, "new_contact")
                .setSmallIcon(R.drawable.ic_call)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle("¿Añadir a contactos?")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build();
        nm.notify(60_000 + (int) (call.id % 10_000), n);
    }

    static void notifyDone(Context c, Db.Call call, Summarizer.Result r) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH, "Análisis de llamadas", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Aviso cuando el resumen y las tareas de una llamada están listos");
        nm.createNotificationChannel(ch);

        String who = !TextUtils.isEmpty(call.name) ? call.name : Util.number(c, call.number);
        int pending = r.pending();
        String text;
        if (AppLock.enabled(c)) {
            text = pending > 0 ? pending + (pending == 1 ? " tarea nueva" : " tareas nuevas") + ". Toca para verlas."
                    : "Toca para ver el resumen.";
        } else if (pending > 0) {
            text = pending + (pending == 1 ? " tarea: " : " tareas: ") + r.tasks.get(0).title;
        } else {
            text = !r.note.isEmpty() ? cap(r.note) : !r.summary.isEmpty() ? r.summary : r.context;
        }
        PendingIntent open = PendingIntent.getActivity(c, (int) (call.id % 100_000),
                new Intent(c, DetailActivity.class).putExtra("id", call.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_call)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle("Llamada con " + who + " analizada")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build();
        nm.notify(3000 + (int) (call.id % 100_000), n);
    }

    private static String cap(String s) {
        return TextUtils.isEmpty(s) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
