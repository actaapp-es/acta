package com.acta.recorder;

import android.telecom.Call;
import android.telecom.CallAudioState;

import java.util.ArrayList;
import java.util.List;

/** Estado compartido entre el servicio de llamadas y la pantalla de llamada. */
final class CallManager {

    interface Listener { void onCallsChanged(); }

    static final List<Call> calls = new ArrayList<>();
    static ActaInCallService service;
    static CallAudioState audio;
    static Call recordingCall;
    private static final List<Listener> listeners = new ArrayList<>();

    private CallManager() { }

    static void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    static void removeListener(Listener l) { listeners.remove(l); }

    static void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onCallsChanged();
    }

    /** Llamada que se muestra en pantalla: la que suena, si no la activa, si no la primera. */
    static Call primary() {
        Call fallback = null;
        for (Call c : calls) {
            int s = c.getState();
            if (s == Call.STATE_RINGING) return c;
            if (s == Call.STATE_ACTIVE || s == Call.STATE_DIALING || s == Call.STATE_CONNECTING) fallback = c;
        }
        if (fallback != null) return fallback;
        return calls.isEmpty() ? null : calls.get(0);
    }

    static String number(Call c) {
        if (c == null || c.getDetails() == null || c.getDetails().getHandle() == null) return null;
        return c.getDetails().getHandle().getSchemeSpecificPart();
    }

    static boolean incoming(Call c) {
        return c.getDetails().getCallDirection() == Call.Details.DIRECTION_INCOMING;
    }
}
