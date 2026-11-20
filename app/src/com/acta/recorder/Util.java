package com.acta.recorder;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class Util {

    static final Locale ES = new Locale("es", "ES");
    static final String ACTION_UPDATED = "com.acta.recorder.UPDATED";


    private Util() { }

    // ---------- Preferencias ----------

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("acta", Context.MODE_PRIVATE);
    }

    static boolean shizukuEnabled(Context c) { return prefs(c).getBoolean("shizuku", false); }
    static boolean nativeAdbEnabled(Context c) { return prefs(c).getBoolean("native_adb", false); }
    static boolean recordIncoming(Context c) { return prefs(c).getBoolean("incoming", true); }
    static boolean hideCallerId(Context c) { return prefs(c).getBoolean("hide_id", false); }

    // ---- Lista de spam (números bloqueados) ----
    static java.util.Set<String> spamSet(Context c) {
        return new java.util.HashSet<>(prefs(c).getStringSet("spam", new java.util.HashSet<String>()));
    }
    private static String tailDigits(String n) {
        String d = n == null ? "" : n.replaceAll("[^0-9]", "");
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }
    static boolean isSpam(Context c, String number) {
        if (number == null || number.isEmpty()) return false;
        String t = tailDigits(number);
        if (t.isEmpty()) return false;
        for (String s : spamSet(c)) if (tailDigits(s).equals(t)) return true;
        return false;
    }
    static void addSpam(Context c, String number) {
        if (TextUtils.isEmpty(number)) return;
        java.util.Set<String> set = spamSet(c);
        set.add(number);
        prefs(c).edit().putStringSet("spam", set).apply();
    }
    static void removeSpam(Context c, String number) {
        String t = tailDigits(number);
        java.util.Set<String> set = spamSet(c);
        java.util.Iterator<String> it = set.iterator();
        while (it.hasNext()) if (tailDigits(it.next()).equals(t)) it.remove();
        prefs(c).edit().putStringSet("spam", set).apply();
    }
    static boolean recordOutgoing(Context c) { return prefs(c).getBoolean("outgoing", true); }

    // ---------- Permisos y estado ----------

    static String[] runtimePermissions() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.RECORD_AUDIO);
        p.add(Manifest.permission.READ_PHONE_STATE);
        p.add(Manifest.permission.READ_CALL_LOG);
        p.add(Manifest.permission.READ_CONTACTS);
        p.add(Manifest.permission.CALL_PHONE);
        p.add(Manifest.permission.WRITE_CALL_LOG);
        p.add(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        return p.toArray(new String[0]);
    }

    static boolean has(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** Permisos imprescindibles para grabar y registrar. */
    static boolean coreGranted(Context c) {
        return has(c, Manifest.permission.RECORD_AUDIO)
                && has(c, Manifest.permission.READ_PHONE_STATE)
                && has(c, Manifest.permission.READ_CALL_LOG);
    }

    static boolean allGranted(Context c) {
        for (String p : runtimePermissions()) if (!has(c, p)) return false;
        return true;
    }

    /**
     * Pide los permisos que faltan. Si Android ya no va a mostrar el aviso (el usuario marcó
     * «no volver a preguntar»), abre directamente la pantalla de permisos de Acta para no dejarle atascado.
     */
    static void askPermissions(android.app.Activity a, int code) {
        List<String> miss = new ArrayList<>();
        boolean canAsk = false;
        for (String p : runtimePermissions()) {
            if (has(a, p)) continue;
            miss.add(p);
            if (a.shouldShowRequestPermissionRationale(p)) canAsk = true;
        }
        if (miss.isEmpty()) return;
        SharedPreferences sp = prefs(a);
        if (sp.getBoolean("perm_asked", false) && !canAsk) {
            try {
                a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
                android.widget.Toast.makeText(a, "Entra en Permisos y activa Teléfono, Contactos y Micrófono",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) { }
        }
        sp.edit().putBoolean("perm_asked", true).apply();
        a.requestPermissions(miss.toArray(new String[0]), code);
    }

    static boolean isDefaultDialer(Context c) {
        RoleManager rm = (RoleManager) c.getSystemService(Context.ROLE_SERVICE);
        return rm != null && rm.isRoleHeld(RoleManager.ROLE_DIALER);
    }

    static Intent requestDialerRole(Context c) {
        RoleManager rm = (RoleManager) c.getSystemService(Context.ROLE_SERVICE);
        return rm != null ? rm.createRequestRoleIntent(RoleManager.ROLE_DIALER) : null;
    }

    /** true cuando Acta está instalada como app del sistema y puede grabar la línea completa. */
    static boolean isXiaomi() {
        String m = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    /** Paquete de la app de llamadas de Xiaomi (la que tiene grabadora), si está instalada. */
    static String xiaomiDialer(Context c) {
        String[] pk = {"com.android.contacts", "com.android.incallui"};
        for (String p : pk) {
            try {
                c.getPackageManager().getPackageInfo(p, 0);
                return "com.android.contacts".equals(p) ? p : "com.android.contacts";
            } catch (Exception ignored) { }
        }
        return null;
    }

    static String defaultDialerPackage(Context c) {
        TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
        return tm != null ? tm.getDefaultDialerPackage() : null;
    }

    /** true si la app de llamadas predeterminada es la de Google (sin grabadora de Xiaomi). */
    static boolean usesGoogleDialer(Context c) {
        return "com.google.android.dialer".equals(defaultDialerPackage(c));
    }

    static boolean fullLine(Context c) {
        return has(c, "android.permission.CAPTURE_AUDIO_OUTPUT");
    }

    static void placeCall(Context c, String number) { placeCall(c, number, null); }

    /** Llama con una SIM concreta (account) o con la predeterminada del sistema si es null. */
    static void placeCall(Context c, String number, android.telecom.PhoneAccountHandle account) {
        if (TextUtils.isEmpty(number)) return;
        String dial = number;
        if (hideCallerId(c) && !dial.startsWith("#31#") && !dial.startsWith("*")) {
            dial = "#31#" + dial;
        }
        Uri u = Uri.fromParts("tel", dial, null);
        if (has(c, Manifest.permission.CALL_PHONE)) {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            try {
                Bundle extras = new Bundle();
                if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account);
                if (tm != null) { tm.placeCall(u, extras); return; }
            } catch (SecurityException ignored) { }
        }
        Intent i = new Intent(Intent.ACTION_CALL, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (account != null) i.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account);
        c.startActivity(i);
    }

    static boolean batteryUnrestricted(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    static boolean ready(Context c) {
        return coreGranted(c) && isDefaultDialer(c);
    }

    // ---------- Archivos ----------

    static File dir(Context c) {
        File d = c.getExternalFilesDir("Grabaciones");
        if (d == null) d = new File(c.getFilesDir(), "Grabaciones");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static Uri shareUri(String fileName) {
        return Uri.parse("content://com.acta.recorder.files/" + Uri.encode(fileName));
    }

    static String safe(String s) {
        if (s == null) return "desconocido";
        String r = s.replaceAll("[^0-9+]", "");
        return r.isEmpty() ? "desconocido" : r;
    }

    // ---------- Formato ----------

    static String country(Context c) {
        try {
            TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            String iso = tm != null ? tm.getNetworkCountryIso() : null;
            if (TextUtils.isEmpty(iso) && tm != null) iso = tm.getSimCountryIso();
            if (!TextUtils.isEmpty(iso)) return iso.toUpperCase(Locale.ROOT);
        } catch (Exception ignored) { }
        return "ES";
    }

    static String number(Context c, String raw) {
        if (TextUtils.isEmpty(raw)) return "Número oculto";
        String f = PhoneNumberUtils.formatNumber(raw, country(c));
        return f != null ? f : raw;
    }

    static String contactName(Context c, String number) {
        if (TextUtils.isEmpty(number) || !has(c, Manifest.permission.READ_CONTACTS)) return null;
        Cursor cur = null;
        try {
            Uri u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            cur = c.getContentResolver().query(u,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }

    /** 3:12 · 1:02:03 */
    static String clock(int sec) {
        if (sec < 0) sec = 0;
        int h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** 3 min 12 s */
    static String duration(int sec) {
        if (sec < 0) sec = 0;
        int h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return h + " h " + m + " min";
        if (m > 0) return m + " min " + s + " s";
        return s + " s";
    }

    static String time(long ms) {
        return new SimpleDateFormat("HH:mm", ES).format(new Date(ms));
    }

    static String timeFull(long ms) {
        return new SimpleDateFormat("HH:mm:ss", ES).format(new Date(ms));
    }

    static String dateFull(long ms) {
        String s = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", ES).format(new Date(ms));
        return s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }

    static String dayHeader(long ms) {
        Calendar now = Calendar.getInstance();
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(ms);
        if (sameDay(now, d)) return "Hoy";
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(now, d)) return "Ayer";
        boolean thisYear = d.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR);
        String p = thisYear ? "EEEE, d 'de' MMMM" : "d 'de' MMMM 'de' yyyy";
        String s = new SimpleDateFormat(p, ES).format(new Date(ms));
        return s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }

    static long dayKey(long ms) {
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(ms);
        return d.get(Calendar.YEAR) * 1000L + d.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) return String.format(ES, "%.0f KB", bytes / 1024.0);
        return String.format(ES, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
