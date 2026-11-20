package com.acta.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;
import android.telecom.CallAudioState;

/** Acciones de la notificación / isla de llamada: rechazar, colgar, silenciar y altavoz. */
public class CallActionReceiver extends BroadcastReceiver {

    static final String REJECT = "com.acta.recorder.REJECT";
    static final String HANGUP = "com.acta.recorder.HANGUP";
    static final String MUTE = "com.acta.recorder.MUTE";
    static final String SPEAKER = "com.acta.recorder.SPEAKER";

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (MUTE.equals(a)) {
            if (CallManager.service != null) {
                boolean muted = CallManager.audio != null && CallManager.audio.isMuted();
                CallManager.service.setMuted(!muted);
            }
            return;
        }
        if (SPEAKER.equals(a)) {
            AudioRoute.toggle(CallManager.service, CallManager.audio);
            return;
        }
        Call c = CallManager.primary();
        if (c == null) return;
        if (REJECT.equals(a) && c.getState() == Call.STATE_RINGING) c.reject(false, null);
        else c.disconnect();
    }
}
