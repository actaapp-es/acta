package com.acta.recorder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Por dónde suena la llamada: teléfono (auricular), auriculares con cable, altavoz o Bluetooth. */
final class AudioRoute {

    private AudioRoute() {}

    static int route(CallAudioState a) {
        return a == null ? CallAudioState.ROUTE_EARPIECE : a.getRoute();
    }

    static boolean speaker(CallAudioState a) { return route(a) == CallAudioState.ROUTE_SPEAKER; }

    /** true si en esta llamada ha habido un dispositivo Bluetooth conectado (aunque ahora suene por el altavoz). */
    private static volatile boolean btSeen;

    /** Se llama en cada cambio de audio: recuerda si hay Bluetooth, para no perderlo al pasar a altavoz. */
    static void observe(CallAudioState a) {
        if (rawBluetooth(a)) btSeen = true;
    }

    /** Se llama al terminar todas las llamadas. */
    static void reset() { btSeen = false; }

    private static boolean rawBluetooth(CallAudioState a) {
        if (a == null) return false;
        if ((a.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0) return true;
        if (a.getRoute() == CallAudioState.ROUTE_BLUETOOTH) return true;
        try {
            Collection<BluetoothDevice> d = a.getSupportedBluetoothDevices();
            if (d != null && !d.isEmpty()) return true;
            return a.getActiveBluetoothDevice() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Hay Bluetooth conectado al teléfono (el sistema lo lista entre las salidas de audio). */
    private static boolean bluetoothOutputConnected() {
        try {
            Context c = CallManager.service;
            if (c == null) return false;
            android.media.AudioManager am = c.getSystemService(android.media.AudioManager.class);
            if (am == null) return false;
            for (android.media.AudioDeviceInfo d : am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || (Build.VERSION.SDK_INT >= 31 && t == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET))
                    return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static boolean bluetoothAvailable(CallAudioState a) {
        if (a == null) return false;
        if (rawBluetooth(a)) { btSeen = true; return true; }
        return btSeen || bluetoothOutputConnected();
    }

    /** El botón se ilumina cuando el sonido no sale por el propio teléfono. */
    static boolean highlighted(CallAudioState a) {
        int r = route(a);
        return r == CallAudioState.ROUTE_SPEAKER || r == CallAudioState.ROUTE_BLUETOOTH;
    }

    static int icon(CallAudioState a) {
        switch (route(a)) {
            case CallAudioState.ROUTE_SPEAKER: return R.drawable.ic_speaker;
            case CallAudioState.ROUTE_BLUETOOTH: return R.drawable.ic_bluetooth;
            case CallAudioState.ROUTE_WIRED_HEADSET: return R.drawable.ic_headset;
            default:
                // Sin Bluetooth el botón sigue siendo «Altavoz»; con Bluetooth muestra dónde suena ahora.
                return bluetoothAvailable(a) ? R.drawable.ic_earpiece : R.drawable.ic_speaker;
        }
    }

    static String label(Context c, CallAudioState a) {
        switch (route(a)) {
            case CallAudioState.ROUTE_SPEAKER: return "Altavoz";
            case CallAudioState.ROUTE_BLUETOOTH: {
                String n = a != null ? btName(c, a.getActiveBluetoothDevice()) : null;
                return !TextUtils.isEmpty(n) ? n : "Bluetooth";
            }
            case CallAudioState.ROUTE_WIRED_HEADSET: return "Auriculares";
            default: return bluetoothAvailable(a) ? "Teléfono" : "Altavoz";
        }
    }

    /** Nombre del dispositivo, solo si Android deja leerlo sin pedir permisos nuevos. */
    static String btName(Context c, BluetoothDevice d) {
        if (d == null) return null;
        if (Build.VERSION.SDK_INT >= 31
                && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return null;
        try {
            String n = d.getName();
            return n != null ? n.trim() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Notificación y botón sin Bluetooth: alterna el altavoz. */
    static void toggle(InCallService svc, CallAudioState a) {
        if (svc == null) return;
        if (speaker(a)) svc.setAudioRoute(bluetoothAvailable(a) ? CallAudioState.ROUTE_BLUETOOTH : CallAudioState.ROUTE_WIRED_OR_EARPIECE);
        else svc.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
    }

    /** Botón de la pantalla de llamada: con Bluetooth conectado deja elegir dónde suena, como el marcador nativo. */
    static void onButton(Activity act, InCallService svc, CallAudioState a) {
        if (svc == null) return;
        if (!bluetoothAvailable(a)) { toggle(svc, a); return; }
        choose(act, svc, a);
    }

    private static final class Option {
        String title; int icon; boolean current; int route; BluetoothDevice device;
    }

    private static void choose(Activity act, final InCallService svc, CallAudioState a) {
        int r = route(a);
        List<Option> opts = new ArrayList<>();
        boolean wired = (a.getSupportedRouteMask() & CallAudioState.ROUTE_WIRED_HEADSET) != 0;
        Option phone = new Option();
        phone.title = wired ? "Auriculares" : "Teléfono";
        phone.icon = wired ? R.drawable.ic_headset : R.drawable.ic_earpiece;
        phone.route = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
        phone.current = r == CallAudioState.ROUTE_EARPIECE || r == CallAudioState.ROUTE_WIRED_HEADSET;
        opts.add(phone);
        Option spk = new Option();
        spk.title = "Altavoz";
        spk.icon = R.drawable.ic_speaker;
        spk.route = CallAudioState.ROUTE_SPEAKER;
        spk.current = r == CallAudioState.ROUTE_SPEAKER;
        opts.add(spk);
        Collection<BluetoothDevice> devs = a.getSupportedBluetoothDevices();
        BluetoothDevice active = a.getActiveBluetoothDevice();
        if (devs != null && devs.size() > 1) {
            int i = 1;
            for (BluetoothDevice d : devs) {
                Option o = new Option();
                String n = btName(act, d);
                o.title = !TextUtils.isEmpty(n) ? n : "Bluetooth " + i;
                o.icon = R.drawable.ic_bluetooth;
                o.route = CallAudioState.ROUTE_BLUETOOTH;
                o.device = d;
                o.current = r == CallAudioState.ROUTE_BLUETOOTH && d.equals(active);
                opts.add(o);
                i++;
            }
        } else {
            Option o = new Option();
            BluetoothDevice d = devs != null && !devs.isEmpty() ? devs.iterator().next() : active;
            String n = btName(act, d);
            o.title = !TextUtils.isEmpty(n) ? n : "Bluetooth";
            o.icon = R.drawable.ic_bluetooth;
            o.route = CallAudioState.ROUTE_BLUETOOTH;
            o.current = r == CallAudioState.ROUTE_BLUETOOTH;
            opts.add(o);
        }

        int dp = Math.round(act.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(16 * dp, 4 * dp, 16 * dp, 8 * dp);
        final AlertDialog[] dlg = new AlertDialog[1];
        for (final Option o : opts) {
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(12 * dp, 8 * dp, 12 * dp, 8 * dp);
            row.setMinimumHeight(56 * dp);
            row.setBackgroundResource(R.drawable.bg_tbtn);
            row.addView(Ui.iconView(act, o.icon, o.current ? R.color.accent : R.color.ink, 22));
            TextView t = Ui.text(act, R.style.Text, o.title, 16, o.current);
            if (o.current) t.setTextColor(act.getColor(R.color.accent));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(16 * dp);
            row.addView(t, tl);
            if (o.current) row.addView(Ui.iconView(act, R.drawable.ic_check, R.color.accent, 20));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (dlg[0] != null) dlg[0].dismiss();
                    if (o.device != null) svc.requestBluetoothAudio(o.device);
                    else svc.setAudioRoute(o.route);
                }
            });
            box.addView(row);
        }
        dlg[0] = new AlertDialog.Builder(act)
                .setTitle("Escuchar la llamada por")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
