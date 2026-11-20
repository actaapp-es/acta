package com.acta.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Aviso cuando se pierde la conexión ADB (y con ella la grabación de ambas voces).
 * Lo lanzan el vigilante de conexión, el arranque del móvil y el final de una llamada
 * que no se pudo grabar con ambas voces. Se quita solo al recuperar la conexión.
 */
final class AdbWatch {

    private AdbWatch() { }

    static final String CH = "acta_status";
    static final int NOTIF = 7001;
    static final String ACTION_RETRY = "com.acta.recorder.ADB_RETRY";

    static void lost(Context c, String why) {
        if (!Util.nativeAdbEnabled(c)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH, "Estado de la grabación", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Aviso si la grabación de ambas voces deja de estar disponible");
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(c, 71,
                new Intent(c, TutorialActivity.class).putExtra(TutorialActivity.EXTRA_RECONNECT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent retry = PendingIntent.getBroadcast(c, 72,
                new Intent(c, Receiver.class).setAction(ACTION_RETRY),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = why + "\n\nMientras tanto se graba solo tu voz. Para recuperarla, conecta el móvil al PC, "
                + "ejecuta «adb tcpip 5555» y pulsa Reintentar.";
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_call)
                .setColor(c.getColor(R.color.warn))
                .setContentTitle("Grabación de ambas voces desactivada")
                .setContentText(why)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Reintentar", retry).build())
                .build();
        nm.notify(NOTIF, n);
        Util.prefs(c).edit().putBoolean("adb_warned", true).apply();
    }

    static void restored(Context c) {
        if (!Util.prefs(c).getBoolean("adb_warned", false)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF);
        Util.prefs(c).edit().putBoolean("adb_warned", false).apply();
    }

    /** Texto claro según el motivo técnico. */
    static String reason(String err) {
        if (err != null && err.contains("puerto cerrado"))
            return "El puerto ADB está cerrado (pasa siempre al reiniciar el móvil).";
        return "Se ha perdido la conexión ADB.";
    }

    /** Botón "Reintentar" de la notificación. */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_RETRY.equals(intent.getAction())) return;
            final Context c = context.getApplicationContext();
            final PendingResult pr = goAsync();
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        if (NativeAdbManager.forceReconnect(c)) restored(c);
                        else lost(c, "Sigue sin conexión: " + NativeAdbManager.lastError());
                    } finally {
                        pr.finish();
                    }
                }
            }).start();
        }
    }
}
