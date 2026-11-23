package com.acta.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Control de llamadas perdidas: qué números te han llamado sin que los devolvieras, aviso al perderlas
 * y recordatorio para devolverlas. Se calcula a partir del registro de llamadas del teléfono.
 */
final class MissedCalls {
    private MissedCalls() { }

    static final String CH = "missed";
    static final long WINDOW_MS = 14L * 24 * 3600 * 1000;

    static final class Item {
        String number, name;
        long last;
        int count;
    }

    static boolean notifyEnabled(Context c) { return Util.prefs(c).getBoolean("missed_notify", true); }

    private static String dismissKey(String number) { return "missed_dismiss_" + ContactMedia.key(number); }

    /**
     * Números con llamadas perdidas sin devolver: sin una llamada posterior tuya (saliente) ni una entrante
     * contestada, y que no hayas descartado. Del más reciente al más antiguo.
     */
    static List<Item> pending(Context c) {
        List<Item> out = new ArrayList<>();
        if (!Util.has(c, android.Manifest.permission.READ_CALL_LOG)) return out;
        Map<String, Item> map = new LinkedHashMap<>();
        Set<String> resolved = new HashSet<>();
        long since = System.currentTimeMillis() - WINDOW_MS;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI.buildUpon()
                            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "500").build(),
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                            CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    CallLog.Calls.DATE + " > ?", new String[]{String.valueOf(since)}, CallLog.Calls.DATE + " DESC");
            while (cur != null && cur.moveToNext()) {
                String num = cur.getString(0);
                String key = ContactMedia.key(num);
                if (key == null || resolved.contains(key)) continue;
                int type = cur.getInt(2);
                long date = cur.getLong(3);
                if (type == CallLog.Calls.MISSED_TYPE) {
                    Item it = map.get(key);
                    if (it == null) {
                        it = new Item();
                        it.number = num;
                        it.name = cur.getString(1);
                        it.last = date;
                        map.put(key, it);
                    }
                    it.count++;
                } else if (type == CallLog.Calls.OUTGOING_TYPE || type == CallLog.Calls.INCOMING_TYPE
                        || type == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE) {
                    resolved.add(key); // ya hablaste con esa persona después
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        for (Item it : map.values()) {
            if (it.last <= Util.prefs(c).getLong(dismissKey(it.number), 0)) continue;
            if (TextUtils.isEmpty(it.name)) it.name = Util.contactName(c, it.number);
            out.add(it);
        }
        return out;
    }

    static void dismiss(Context c, Item it) {
        Util.prefs(c).edit().putLong(dismissKey(it.number), System.currentTimeMillis()).apply();
        cancel(c, it.number);
    }

    // ------------------------------------------------------------------ Aviso

    private static int notifId(String number) {
        String k = ContactMedia.key(number);
        return 6000 + (k == null ? 0 : (k.hashCode() & 0xffff));
    }

    static void cancel(Context c, String number) {
        if (TextUtils.isEmpty(number)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(notifId(number));
    }

    /** Llamar al terminar una llamada entrante que no se contestó. Cuenta las veces seguidas sin devolver. */
    static void onMissed(final Context ctx, final String number, final long at) {
        if (TextUtils.isEmpty(number) || !notifyEnabled(ctx)) return;
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                // El registro de llamadas tarda un instante en anotar la perdida.
                try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
                int count = 1;
                for (Item it : pending(c)) {
                    if (ContactMedia.key(it.number) != null && ContactMedia.key(it.number).equals(ContactMedia.key(number))) {
                        count = Math.max(1, it.count);
                        break;
                    }
                }
                show(c, number, at, count);
            }
        }).start();
    }

    private static void show(Context c, String number, long at, int count) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH, "Llamadas perdidas", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Aviso de las llamadas que no contestaste, para devolverlas");
        nm.createNotificationChannel(ch);

        String name = Util.contactName(c, number);
        String who = !TextUtils.isEmpty(name) ? name : Util.number(c, number);
        int id = notifId(number);
        PendingIntent open = PendingIntent.getActivity(c, id,
                new Intent(c, MissedCallsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String dial = number;
        if (Util.hideCallerId(c) && !dial.startsWith("#31#") && !dial.startsWith("*")) dial = "#31#" + dial;
        PendingIntent call = PendingIntent.getActivity(c, id + 1,
                new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", dial, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent later = PendingIntent.getBroadcast(c, id + 2,
                new Intent(c, ReminderReceiver.class).setAction(QuickActions.ACTION_SNOOZE)
                        .putExtra("number", number).putExtra("name", name).putExtra("notif", id),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent menu = PendingIntent.getActivity(c, id + 3, ContactMenuActivity.intent(c, number, name),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_call)
                .setColor(c.getColor(R.color.danger))
                .setContentTitle(count > 1 ? count + " llamadas perdidas de " + who : "Llamada perdida de " + who)
                .setContentText("Última a las " + Util.time(at) + ". Toca para ver todas.")
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setWhen(at)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Llamar", call).build())
                .addAction(new Notification.Action.Builder(null, "En 15 min", later).build())
                .addAction(new Notification.Action.Builder(null, "Más", menu).build())
                .build();
        nm.notify(id, n);
    }
}
