package com.acta.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Solo eventos del sistema: reprograma recordatorios y avisa si la grabación de ambas voces quedó desactivada. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        final Context c = context.getApplicationContext();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(a)) {
            QuickActions.rescheduleAll(c);
            Cleanup.schedule(c);
            UpdateCheck.schedule(c);
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) && Util.nativeAdbEnabled(c) && NativeAdbManager.savedPort(c) != 0) {
            final PendingResult pr = goAsync();
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        if (!NativeAdbManager.ensureConnected(c))
                            AdbWatch.lost(c, "Tras reiniciar el móvil, el puerto ADB se cierra.");
                    } finally {
                        pr.finish();
                    }
                }
            }).start();
        }
    }
}
