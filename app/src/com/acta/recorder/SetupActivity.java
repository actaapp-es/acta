package com.acta.recorder;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Asistente paso a paso para dejar lista la grabación de línea completa con Shizuku.
 * Detecta en vivo qué pasos están hechos y lleva con un toque a cada pantalla.
 * No puede activar los interruptores protegidos (eso lo impide Android/Xiaomi),
 * pero elimina el tener que buscar por los menús.
 */
public class SetupActivity extends Activity {

    private LinearLayout content;
    private int step;

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        content = findViewById(R.id.content);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { ShizukuManager.ensureService(); } catch (Throwable ignored) { }
        build();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        build();
    }

    private boolean devOptions() {
        try {
            return Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        } catch (Throwable t) { return false; }
    }

    private boolean wirelessDebug() {
        try {
            return Settings.Global.getInt(getContentResolver(), "adb_wifi_enabled", 0) == 1;
        } catch (Throwable t) { return false; }
    }

    private void build() {
        content.removeAllViews();
        step = 0;
        boolean xiaomi = Util.isXiaomi();

        // Cabecera + estado global
        TextView title = new TextView(this, null, 0, R.style.Text_Title);
        title.setText("Configurar grabación");
        int p = dp(24);
        title.setPadding(p, dp(8), p, dp(4));
        content.addView(title);

        boolean done = ShizukuManager.ready() && Util.isDefaultDialer(this);
        TextView banner = new TextView(this, null, 0, R.style.Text_Body);
        banner.setText(done ? "Todo listo: Acta grabará la llamada completa."
                : "Sigue los pasos pendientes. Los que ya están hechos aparecen en verde.");
        banner.setTextColor(getColor(done ? R.color.ok : R.color.ink_2));
        banner.setPadding(p, 0, p, dp(8));
        content.addView(banner);

        // Paso 1: opciones de desarrollador
        boolean dev = devOptions();
        stepRow("Activar Opciones de desarrollador", dev,
                dev ? "Hecho." : "Ajustes › Acerca del teléfono › toca 7 veces en «Versión de HyperOS/MIUI».",
                dev ? "Hecho" : "Abrir", new Runnable() {
                    @Override public void run() {
                        if (!open(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)))
                            open(new Intent(Settings.ACTION_SETTINGS));
                    }
                });

        // Paso 2: instalar Shizuku
        boolean inst = ShizukuManager.installed(this);
        stepRow("Instalar Shizuku", inst, inst ? "Instalada." : "App gratuita. Toca para instalarla desde Play Store.",
                inst ? "Hecha" : "Instalar", new Runnable() {
                    @Override public void run() { openShizukuStore(); }
                });

        // Paso 3: depuración inalámbrica
        boolean wd = wirelessDebug();
        stepRow("Activar Depuración inalámbrica", wd,
                "En Opciones de desarrollador › «Depuración inalámbrica». Déjala activada.",
                "Abrir", new Runnable() {
                    @Override public void run() {
                        if (!open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)))
                            open(new Intent(Settings.ACTION_SETTINGS));
                    }
                });

        // Paso 4: arrancar Shizuku
        boolean run = ShizukuManager.running();
        stepRow("Arrancar Shizuku", run,
                run ? "En marcha." : "En Shizuku: «Iniciar vía Depuración inalámbrica» › Emparejamiento, y escribe el código que da el móvil.",
                run ? "Hecho" : "Abrir Shizuku", new Runnable() {
                    @Override public void run() { launchShizuku(); }
                });

        // Paso 5 (solo Xiaomi): ajustes de seguridad
        if (xiaomi) {
            stepRow("Depuración USB (ajustes de seguridad)", false,
                    "Solo Xiaomi. En Opciones de desarrollador, con la SIM puesta y tu cuenta Xiaomi iniciada. Si no, Shizuku avisa en rojo.",
                    "Abrir", new Runnable() {
                        @Override public void run() {
                            if (!open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)))
                                open(new Intent(Settings.ACTION_SETTINGS));
                        }
                    });
        }

        // Paso 6: permiso de Shizuku a Acta
        boolean granted = ShizukuManager.granted();
        stepRow("Permitir Acta en Shizuku", granted,
                granted ? "Concedido." : "Concede a Acta el permiso de Shizuku.",
                granted ? "Hecho" : "Permitir", new Runnable() {
                    @Override public void run() {
                        if (!ShizukuManager.running()) { launchShizuku(); return; }
                        ShizukuManager.requestPermission();
                    }
                });

        // Paso 7: app de teléfono predeterminada
        boolean dialer = Util.isDefaultDialer(this);
        stepRow("Poner Acta como app de teléfono", dialer,
                dialer ? "Acta gestiona las llamadas." : "Necesario para que Acta grabe cada llamada.",
                dialer ? "Hecho" : "Establecer", new Runnable() {
                    @Override public void run() {
                        if (Util.isDefaultDialer(SetupActivity.this)) return;
                        Intent i = Util.requestDialerRole(SetupActivity.this);
                        if (i != null) startActivityForResult(i, 1);
                    }
                });

        // Activar el motor Shizuku en Acta
        if (!Util.shizukuEnabled(this)) {
            stepRow("Activar la grabación con Shizuku en Acta", false,
                    "Enciende el motor de grabación completa.", "Activar", new Runnable() {
                        @Override public void run() {
                            Util.prefs(SetupActivity.this).edit().putBoolean("shizuku", true).apply();
                            ShizukuManager.ensureService();
                            build();
                        }
                    });
        }

        // Nota sobre reinicio
        TextView note = new TextView(this, null, 0, R.style.Text_Meta);
        note.setText("Tras reiniciar el móvil, Xiaomi desactiva la depuración y Shizuku se detiene. "
                + "Tendrás que volver a activar la depuración inalámbrica y arrancar Shizuku. "
                + "Es una limitación de Android y de Xiaomi, no de Acta.");
        note.setLineSpacing(0, 1.3f);
        note.setPadding(p, dp(24), p, dp(24));
        content.addView(note);
    }

    private void stepRow(String titleText, boolean okState, String sub, String action, final Runnable onTap) {
        step++;
        View v = LayoutInflater.from(this).inflate(R.layout.row_setting, content, false);
        ((TextView) v.findViewById(R.id.title)).setText(step + ".  " + titleText);
        ((TextView) v.findViewById(R.id.sub)).setText(sub);
        TextView val = v.findViewById(R.id.value);
        val.setText(action);
        val.setTextColor(getColor(okState ? R.color.ok : R.color.accent));
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { onTap.run(); }
        });
        content.addView(v);
    }

    private boolean open(Intent i) {
        try { startActivity(i); return true; } catch (Exception e) { return false; }
    }

    private void openShizukuStore() {
        if (!open(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + ShizukuManager.PKG))))
            open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")));
    }

    private void launchShizuku() {
        Intent i = getPackageManager().getLaunchIntentForPackage(ShizukuManager.PKG);
        if (i == null) openShizukuStore();
        else open(i);
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        build();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
