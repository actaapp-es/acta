package com.acta.recorder;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * Detección de la isla flotante. En HyperOS 3 (Super Island) y Android 16 la isla muestra las
 * notificaciones "Live Update" (promovidas); Acta publica sus llamadas así (CallStyle).
 */
final class Island {

    private Island() { }

    /** Versión de HyperOS ("OS3.0.4.0…") o null si no es HyperOS. */
    static String hyperOs() {
        String inc = Build.VERSION.INCREMENTAL;
        if (inc != null && inc.startsWith("OS")) return inc;
        String p = prop("ro.mi.os.version.name");
        return TextUtils.isEmpty(p) ? null : p;
    }

    static int hyperOsMajor() {
        String v = hyperOs();
        if (v == null) return 0;
        try {
            String n = v.replaceAll("^\\D*", "");
            int dot = n.indexOf('.');
            return Integer.parseInt(dot > 0 ? n.substring(0, dot) : n);
        } catch (Exception e) {
            return 0;
        }
    }

    /** El sistema tiene isla / chips de Live Update (Android 16+ o HyperOS 3+). */
    static boolean supported() {
        return Build.VERSION.SDK_INT >= 36 || hyperOsMajor() >= 3;
    }

    static String systemName() {
        if (hyperOsMajor() >= 3) return "HyperOS " + hyperOsMajor() + " · Super Island";
        if (hyperOs() != null) return "HyperOS " + Math.max(1, hyperOsMajor());
        return "Android " + Build.VERSION.RELEASE;
    }

    /** true/false si el sistema informa del permiso de Live Updates para Acta; null si no lo sabe. */
    static Boolean allowed(Context c) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            Object r = NotificationManager.class.getMethod("canPostPromotedNotifications").invoke(nm);
            return r instanceof Boolean ? (Boolean) r : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Abre el ajuste para permitir a Acta aparecer en la isla (o, si no existe, sus notificaciones). */
    static void openSettings(Activity a) {
        try {
            a.startActivity(new Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
            return;
        } catch (Throwable ignored) { }
        try {
            a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
        } catch (Throwable ignored) { }
    }

    private static String prop(String key) {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
            return v == null ? null : v.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
