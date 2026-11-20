package com.acta.recorder;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import java.io.File;

public class ActaApp extends Application {

    private int started;

    @Override
    public void onCreate() {
        super.onCreate();
        try { if (Build.VERSION.SDK_INT >= 28 && Application.getProcessName().endsWith(":crash")) return; } catch (Throwable ignored) { }
        try { CrashActivity.install(this); } catch (Throwable ignored) { }
        try { ShizukuManager.init(); } catch (Throwable ignored) { }
        // Mantiene viva la conexión ADB para que las llamadas (incluso cortas) la encuentren lista.
        try { NativeAdbManager.startKeepAlive(this); } catch (Throwable ignored) { }

        // Bloqueo: se vuelve a bloquear al apagar la pantalla o tras 30 s fuera de la app.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) {
                if (started++ == 0 && AppLock.backgroundAt > 0
                        && System.currentTimeMillis() - AppLock.backgroundAt > AppLock.GRACE_MS) {
                    AppLock.lock();
                }
            }
            @Override public void onActivityStopped(Activity a) {
                if (--started <= 0) { started = 0; AppLock.backgroundAt = System.currentTimeMillis(); }
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityPostCreated(Activity a, Bundle b) {
                try { Ui.applyInsets(a); } catch (Throwable ignored) { }
            }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
        BroadcastReceiver screenOff = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { AppLock.lock(); }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, f);

        // Cifra las grabaciones antiguas que sigan en claro y limpia temporales descifrados.
        final Context app = this;
        new Thread(new Runnable() {
            @Override public void run() {
                cleanTemp(new File(getCacheDir(), "share"));
                cleanTemp(new File(getCacheDir(), "ai"));
                try { Crypto.encryptAll(app); } catch (Throwable ignored) { }
                try { Cleanup.maybeRun(app); Cleanup.schedule(app); } catch (Throwable ignored) { }
            }
        }, "acta-crypto-migrate").start();
    }

    private static void cleanTemp(File dir) {
        File[] fs = dir.listFiles();
        long old = System.currentTimeMillis() - 30 * 60_000L;
        // Solo los antiguos: un análisis en marcha puede estar usando los recientes.
        if (fs != null) for (File f : fs) if (f.lastModified() < old) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
