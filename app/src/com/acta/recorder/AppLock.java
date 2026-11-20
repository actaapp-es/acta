package com.acta.recorder;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.Toast;

/**
 * Bloqueo de las grabaciones con la huella (o cara) o el PIN/patrón del propio móvil.
 * Una vez desbloqueado, sigue abierto hasta apagar la pantalla o dejar la app más de 30 s.
 */
final class AppLock {

    private AppLock() { }

    static volatile boolean unlocked;
    static volatile long backgroundAt;
    private static volatile boolean prompting;
    static final long GRACE_MS = 30_000;

    static boolean enabled(Context c) { return Util.prefs(c).getBoolean("lock_on", false); }

    static void setEnabled(Context c, boolean on) { Util.prefs(c).edit().putBoolean("lock_on", on).apply(); }

    /** Hace falta pedir huella/PIN ahora mismo. */
    static boolean needed(Context c) { return enabled(c) && !unlocked && available(c); }

    /** El móvil tiene un bloqueo de pantalla seguro (requisito para huella y PIN). */
    static boolean available(Context c) {
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return km != null && km.isDeviceSecure();
    }

    static void lock() { unlocked = false; }

    /** Si hace falta, pide huella/PIN; si no, ejecuta ok directamente. */
    static void require(Activity a, Runnable ok, Runnable cancel) {
        if (!needed(a)) { ok.run(); return; }
        authenticate(a, "Grabaciones protegidas", ok, cancel);
    }

    static void authenticate(final Activity a, String title, final Runnable ok, final Runnable cancel) {
        if (prompting) return;
        prompting = true;
        try {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(a)
                    .setTitle(title)
                    .setSubtitle("Usa tu huella o el PIN del móvil");
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else {
                //noinspection deprecation
                b.setDeviceCredentialAllowed(true);
            }
            b.build().authenticate(new CancellationSignal(), a.getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                            prompting = false;
                            unlocked = true;
                            if (ok != null) ok.run();
                        }

                        @Override public void onAuthenticationError(int code, CharSequence msg) {
                            prompting = false;
                            if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                                    && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                                    && msg != null) {
                                Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
                            }
                            if (cancel != null) cancel.run();
                        }
                    });
        } catch (Throwable t) {
            prompting = false;
            Toast.makeText(a, "No se pudo pedir la huella/PIN", Toast.LENGTH_SHORT).show();
            if (cancel != null) cancel.run();
        }
    }
}
