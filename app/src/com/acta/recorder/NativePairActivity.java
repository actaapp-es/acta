package com.acta.recorder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Conexión del ADB propio de Acta por el método de cable (adb tcpip 5555).
 * Simple: dirección, puerto y Conectar. Sin emparejamiento.
 */
public class NativePairActivity extends Activity {

    private EditText host, port;
    private TextView status;

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int p = dp(24);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, dp(16), p, dp(32));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(title("Conectar grabación completa"));
        root.addView(meta("Una vez hecho desde el ordenador el comando «adb tcpip 5555» con el cable, "
                + "pulsa Conectar. Normalmente no hay que cambiar nada.\n\n"
                + "El móvil te pedirá autorizar a Acta: acepta y marca «Permitir siempre»."));

        root.addView(label("Dirección"));
        host = field("IP del móvil", "127.0.0.1", InputType.TYPE_CLASS_TEXT);
        root.addView(host);
        new Thread(new Runnable() {
            @Override public void run() {
                final String ip = NativeAdbManager.deviceIp();
                if (ip != null) runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (host.getText().toString().trim().equals("127.0.0.1")) host.setText(ip);
                    }
                });
            }
        }).start();

        root.addView(label("Puerto"));
        port = field("5555", "5555", InputType.TYPE_CLASS_NUMBER);
        root.addView(port);

        Button connectBtn = button("Conectar", R.color.accent, R.color.on_accent);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doConnect(); }
        });
        root.addView(connectBtn);

        Button dialerBtn = button("Poner Acta como app de teléfono", R.color.surface, R.color.ink);
        dialerBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Util.isDefaultDialer(NativePairActivity.this)) {
                    set("Acta ya es tu app de teléfono.");
                    return;
                }
                Intent i = Util.requestDialerRole(NativePairActivity.this);
                if (i != null) startActivityForResult(i, 1);
            }
        });
        root.addView(dialerBtn);

        status = meta("");
        status.setPadding(0, dp(20), 0, 0);
        root.addView(status);
    }

    private void doConnect() {
        final String h = host.getText().toString().trim();
        final String pt = port.getText().toString().trim();
        if (h.isEmpty() || pt.isEmpty()) { set("Rellena dirección y puerto (por defecto 127.0.0.1 y 5555)."); return; }
        set("Conectando… acepta el aviso de autorización en el móvil si aparece.");
        new Thread(new Runnable() {
            @Override public void run() {
                int port = parse(pt);
                String used = h;
                boolean ok = NativeAdbManager.connect(NativePairActivity.this, h, port);
                String err1 = NativeAdbManager.lastError();
                if (!ok) {
                    // Reintento automático con la IP Wi-Fi del propio móvil
                    String ip = NativeAdbManager.deviceIp();
                    if (ip != null && !ip.equals(h)) {
                        set("127.0.0.1 no respondió. Probando con " + ip + "… acepta el aviso en el móvil si sale.");
                        ok = NativeAdbManager.connect(NativePairActivity.this, ip, port);
                        if (ok) used = ip;
                    }
                }
                if (ok) {
                    NativeAdbManager.saveEndpoint(NativePairActivity.this, used, port);
                    Util.prefs(NativePairActivity.this).edit().putBoolean("native_adb", true).apply();
                    NativeAdbManager.startKeepAlive(NativePairActivity.this);
                    set("Conectado (" + used + ":" + port + "). Acta grabará la llamada completa.\n\n"
                            + (Util.isDefaultDialer(NativePairActivity.this) ? ""
                            : "Ahora pulsa «Poner Acta como app de teléfono»."));
                } else {
                    String ip = NativeAdbManager.deviceIp();
                    set("No se pudo conectar.\n\nMotivo: " + NativeAdbManager.lastError()
                            + "\nIP del móvil detectada: " + (ip != null ? ip : "ninguna")
                            + "\n\nComprueba en el ordenador (con el cable): «adb tcpip 5555» debe decir «restarting in TCP mode». "
                            + "Si el móvil se reinició, el puerto se cerró: vuelve a hacerlo.");
                }
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        if (Util.isDefaultDialer(this)) set("Acta ya es tu app de teléfono.");
    }

    private void set(final String t) {
        runOnUiThread(new Runnable() {
            @Override public void run() { status.setText(t); }
        });
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ---- vistas ----
    private TextView title(String t) {
        TextView v = new TextView(this, null, 0, R.style.Text_Title);
        v.setText(t);
        v.setPadding(0, dp(8), 0, dp(8));
        return v;
    }

    private TextView meta(String t) {
        TextView v = new TextView(this, null, 0, R.style.Text_Meta);
        v.setText(t);
        v.setLineSpacing(0, 1.3f);
        return v;
    }

    private TextView label(String t) {
        TextView v = new TextView(this, null, 0, R.style.Text_Section);
        v.setText(t);
        v.setPadding(0, dp(18), 0, dp(6));
        return v;
    }

    private EditText field(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        e.setTextColor(getColor(R.color.ink));
        e.setHintTextColor(getColor(R.color.ink_3));
        e.setTextSize(18);
        e.setBackgroundResource(R.drawable.bg_field);
        e.setPadding(dp(18), dp(12), dp(18), dp(12));
        return e;
    }

    private Button button(String t, int bg, int fg) {
        Button btn = new Button(this, null, 0, R.style.Text);
        btn.setText(t);
        btn.setAllCaps(false);
        boolean primary = bg == R.color.accent;
        btn.setTextColor(getColor(primary ? R.color.on_accent : R.color.accent));
        btn.setBackgroundResource(primary ? R.drawable.bg_btn : R.drawable.bg_btn2);
        btn.setTypeface(Ui.bold());
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setTextSize(15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(14);
        btn.setLayoutParams(lp);
        return btn;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
