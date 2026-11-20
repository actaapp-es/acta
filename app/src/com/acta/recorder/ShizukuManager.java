package com.acta.recorder;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;

import rikka.shizuku.Shizuku;

/**
 * Puente con Shizuku. Shizuku concede a Acta los permisos del usuario "shell"
 * (ADB) sin root; con ellos, un pequeño servicio arrancado por Shizuku puede
 * grabar la línea de voz completa (ambas partes) con AudioSource.VOICE_CALL.
 */
final class ShizukuManager {

    static final int PERM_CODE = 4210;
    static final String PKG = "moe.shizuku.privileged.api";

    private static volatile IRecorderService svc;
    private static boolean binding;
    private static boolean inited;

    private ShizukuManager() { }

    static void init() {
        if (inited) return;
        inited = true;
        try {
            Shizuku.addBinderReceivedListenerSticky(new Shizuku.OnBinderReceivedListener() {
                @Override public void onBinderReceived() { ensureService(); }
            });
            Shizuku.addBinderDeadListener(new Shizuku.OnBinderDeadListener() {
                @Override public void onBinderDead() { svc = null; binding = false; }
            });
            Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
                @Override public void onRequestPermissionResult(int code, int grantResult) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) ensureService();
                }
            });
        } catch (Throwable ignored) { }
    }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean running() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    static boolean granted() {
        if (!running()) return false;
        try {
            if (Shizuku.isPreV11()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean shouldRequest() {
        return running() && !granted();
    }

    static void requestPermission() {
        try { Shizuku.requestPermission(PERM_CODE); } catch (Throwable ignored) { }
    }

    /** Listo para grabar: permiso concedido y servicio remoto enlazado. */
    static boolean ready() {
        return granted() && svc != null;
    }

    static IRecorderService service() {
        return svc;
    }

    static void ensureService() {
        if (svc != null || binding || !granted()) return;
        binding = true;
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(PKG_SELF, RecorderUserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("rec")
                    .debuggable(false)
                    .version(1);
            Shizuku.bindUserService(args, CONN);
        } catch (Throwable t) {
            binding = false;
        }
    }

    private static final String PKG_SELF = "com.acta.recorder";

    private static final ServiceConnection CONN = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            binding = false;
            svc = (binder != null && binder.pingBinder()) ? IRecorderService.Stub.asInterface(binder) : null;
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            svc = null;
            binding = false;
        }
    };
}
