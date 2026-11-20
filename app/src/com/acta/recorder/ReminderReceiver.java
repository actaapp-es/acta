package com.acta.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recordatorios de llamadas pospuestas: aviso a su hora y "otros 15 min". */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (a == null) return;
        Context c = context.getApplicationContext();
        if (QuickActions.ACTION_FIRE.equals(a)) {
            QuickActions.fire(c, intent.getIntExtra("id", -1));
        } else if (QuickActions.ACTION_SNOOZE.equals(a)) {
            QuickActions.snooze(c, intent.getStringExtra("number"), intent.getStringExtra("name"),
                    intent.getIntExtra("notif", 0), 15);
        }
    }
}
