package com.acta.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

/** Al colgar, espera a que Xiaomi termine de guardar la grabación y la incorpora. */
public class CallEndReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!TelephonyManager.EXTRA_STATE_IDLE.equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE))) return;
        if (Util.fullLine(context) && Util.isDefaultDialer(context)) return; // Acta ya graba por sí misma
        final PendingResult pr = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(4000);
                    Importer.run(app);
                } catch (Exception ignored) {
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }
}
