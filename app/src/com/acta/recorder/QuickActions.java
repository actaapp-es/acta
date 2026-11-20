package com.acta.recorder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Acciones rápidas sobre una llamada entrante sin descolgar:
 *  - Responder por WhatsApp con un mensaje predefinido (abre el chat con el texto ya escrito).
 *  - Posponer: rechaza la llamada y avisa con una notificación para devolverla más tarde.
 */
final class QuickActions {

    private QuickActions() { }

    static final String[] DEFAULT_MESSAGES = {"Estoy ocupado.", "Luego te llamo.", "No puedo hablar."};
    static final int[] POSTPONE_MINUTES = {5, 15, 30, 60, 120};

    static String[] messages(Context c) {
        String[] out = new String[3];
        for (int i = 0; i < 3; i++) {
            String v = Util.prefs(c).getString("qm" + i, DEFAULT_MESSAGES[i]);
            out[i] = TextUtils.isEmpty(v) ? DEFAULT_MESSAGES[i] : v;
        }
        return out;
    }

    static void saveMessages(Context c, String[] m) {
        android.content.SharedPreferences.Editor e = Util.prefs(c).edit();
        for (int i = 0; i < 3; i++) e.putString("qm" + i, m[i] == null ? "" : m[i].trim());
        e.apply();
    }

    static String minutesLabel(int m) {
        if (m < 60) return m + " min";
        return m == 60 ? "1 hora" : (m / 60) + " horas";
    }

    /** Rechaza la llamada entrante de ese número si sigue sonando. */
    static void rejectRinging(String number) {
        Call c = CallManager.primary();
        if (c == null || c.getState() != Call.STATE_RINGING) return;
        String n = CallManager.number(c);
        if (TextUtils.isEmpty(number) || PhoneNumberUtils.compare(n, number)) {
            try { c.reject(false, null); } catch (Throwable ignored) { }
        }
    }

    // ================================================================== WhatsApp

    private static final String[] WA_PACKAGES = {"com.whatsapp", "com.whatsapp.w4b"};

    static String whatsappPackage(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String p : WA_PACKAGES) {
            try { pm.getPackageInfo(p, 0); return p; } catch (Throwable ignored) { }
        }
        return null;
    }

    /** Número en formato internacional sin "+" (lo que pide WhatsApp), o null. */
    static String internationalDigits(Context c, String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        String country = "ES";
        try {
            TelephonyManager tm = c.getSystemService(TelephonyManager.class);
            if (tm != null) {
                String iso = tm.getSimCountryIso();
                if (TextUtils.isEmpty(iso)) iso = tm.getNetworkCountryIso();
                if (!TextUtils.isEmpty(iso)) country = iso.toUpperCase(Locale.ROOT);
            }
        } catch (Throwable ignored) { }
        String e164 = null;
        try { e164 = PhoneNumberUtils.formatNumberToE164(raw, country); } catch (Throwable ignored) { }
        if (TextUtils.isEmpty(e164)) {
            String d = raw.replaceAll("[^0-9+]", "");
            if (d.startsWith("00")) d = "+" + d.substring(2);
            e164 = d.startsWith("+") ? d : null;
        }
        if (TextUtils.isEmpty(e164)) return null;
        String digits = e164.replaceAll("[^0-9]", "");
        return digits.length() >= 8 ? digits : null;
    }

    /** Abre el chat de WhatsApp con ese número y el texto ya escrito. */
    static boolean openWhatsApp(Context c, String number, String text) {
        String digits = internationalDigits(c, number);
        if (digits == null) return false;
        String url = "https://api.whatsapp.com/send?phone=" + digits;
        try {
            if (!TextUtils.isEmpty(text)) url += "&text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (Exception ignored) { }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String pkg = whatsappPackage(c);
        if (pkg != null) i.setPackage(pkg);
        try {
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            if (pkg != null) {
                try { c.startActivity(i.setPackage(null)); return true; } catch (Throwable ignored) { }
            }
            return false;
        }
    }

    // ================================================================== Posponer / recordatorios

    static final String CH_REMIND = "acta_remind";
    static final String ACTION_FIRE = "com.acta.recorder.REMIND_FIRE";
    static final String ACTION_SNOOZE = "com.acta.recorder.REMIND_SNOOZE";

    static final class Reminder {
        int id;
        String number, name;
        long at, postponedAt;
        boolean planned;   // llamada programada por el usuario (no una llamada pospuesta)
    }

    /** Programa una llamada para una fecha y hora: a esa hora avisa con un botón para llamar. */
    static Reminder scheduleCall(Context c, String number, String name, long at) {
        Reminder r = new Reminder();
        r.id = (int) (System.currentTimeMillis() % 100000000L) + 1000;
        r.number = number;
        r.name = name;
        r.postponedAt = System.currentTimeMillis();
        r.at = at;
        r.planned = true;
        List<Reminder> all = load(c);
        all.add(r);
        save(c, all);
        schedule(c, r);
        return r;
    }

    /** Llamadas programadas y pospuestas pendientes, de la más próxima a la más lejana. */
    static List<Reminder> pending(Context c) {
        List<Reminder> all = load(c);
        java.util.Collections.sort(all, new java.util.Comparator<Reminder>() {
            @Override public int compare(Reminder a, Reminder b) { return Long.compare(a.at, b.at); }
        });
        return all;
    }

    static void cancel(Context c, int id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(firePending(c, id));
        take(c, id);
    }

    /** Programa un recordatorio para devolver la llamada. Devuelve la hora del aviso. */
    static long postpone(Context c, String number, String name, int minutes) {
        Reminder r = new Reminder();
        r.id = (int) (System.currentTimeMillis() % 100000000L) + 1000;
        r.number = number;
        r.name = name;
        r.postponedAt = System.currentTimeMillis();
        r.at = r.postponedAt + minutes * 60_000L;
        List<Reminder> all = load(c);
        all.add(r);
        save(c, all);
        schedule(c, r);
        return r.at;
    }

    static void schedule(Context c, Reminder r) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = firePending(c, r.id);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi);
        }
    }

    static boolean exactAlarmsAllowed(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
    }

    private static PendingIntent firePending(Context c, int id) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION_FIRE).putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Tras reiniciar el móvil, vuelve a programar los recordatorios pendientes. */
    static void rescheduleAll(Context c) {
        long now = System.currentTimeMillis();
        for (Reminder r : load(c)) {
            if (r.at <= now) fire(c, r.id);   // vencido mientras estaba apagado: avisar una vez y borrar
            else schedule(c, r);
        }
    }

    static void fire(Context c, int id) {
        Reminder r = take(c, id);
        if (r != null) showReminder(c, r);
    }

    static void snooze(Context c, String number, String name, int notifId, int minutes) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(notifId);
        postpone(c, number, name, minutes);
    }

    private static void showReminder(Context c, Reminder r) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH_REMIND, "Llamadas pospuestas", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Aviso para devolver una llamada que pospusiste");
        nm.createNotificationChannel(ch);

        String who = !TextUtils.isEmpty(r.name) ? r.name : Util.number(c, r.number);
        String dial = r.number;
        if (Util.hideCallerId(c) && !dial.startsWith("#31#") && !dial.startsWith("*")) dial = "#31#" + dial;
        PendingIntent call = PendingIntent.getActivity(c, r.id,
                new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", dial, null)),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent snooze = PendingIntent.getBroadcast(c, r.id + 1,
                new Intent(c, ReminderReceiver.class).setAction(ACTION_SNOOZE)
                        .putExtra("number", r.number).putExtra("name", r.name).putExtra("notif", r.id),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open = PendingIntent.getActivity(c, r.id + 2,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(c, CH_REMIND)
                .setSmallIcon(R.drawable.ic_call)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle(r.planned ? "Llamar a " + who : "Devolver llamada a " + who)
                .setContentText(r.planned ? "Llamada programada para las " + Util.time(r.at)
                        : "La pospusiste a las " + Util.time(r.postponedAt))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setWhen(r.at)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Llamar ahora", call).build())
                .addAction(new Notification.Action.Builder(null, "Otros 15 min", snooze).build())
                .build();
        nm.notify(r.id, n);
    }

    // ---- almacenamiento (preferencias, JSON) ----

    private static synchronized List<Reminder> load(Context c) {
        List<Reminder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Util.prefs(c).getString("reminders", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Reminder r = new Reminder();
                r.id = o.getInt("id");
                r.number = o.optString("number", "");
                r.name = o.optString("name", "");
                r.at = o.getLong("at");
                r.postponedAt = o.optLong("from", r.at);
                r.planned = o.optBoolean("planned", false);
                out.add(r);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static synchronized void save(Context c, List<Reminder> list) {
        JSONArray a = new JSONArray();
        try {
            for (Reminder r : list) {
                a.put(new JSONObject().put("id", r.id).put("number", r.number).put("name", r.name)
                        .put("at", r.at).put("from", r.postponedAt).put("planned", r.planned));
            }
        } catch (Throwable ignored) { }
        Util.prefs(c).edit().putString("reminders", a.toString()).apply();
    }

    private static synchronized Reminder take(Context c, int id) {
        List<Reminder> all = load(c);
        Reminder found = null;
        for (Reminder r : all) if (r.id == id) found = r;
        if (found != null) { all.remove(found); save(c, all); }
        return found;
    }
}
