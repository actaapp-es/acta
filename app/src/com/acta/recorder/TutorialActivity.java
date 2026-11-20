package com.acta.recorder;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Tutorial interactivo para configurar la grabación de las dos voces desde cero:
 * app de teléfono, herramientas en el ordenador, opciones de desarrollador, depuración USB,
 * cable + «adb tcpip 5555», conectar Acta y prueba. Cada paso se detecta solo.
 */
public class TutorialActivity extends Activity {

    static final String EXTRA_RECONNECT = "reconnect";

    // Primero lo que se hace en el móvil, luego lo del ordenador.
    private static final int[] FULL = {1, 3, 4, 2, 5, 6, 7};
    private static final int[] RECONNECT = {5, 6};

    private static final String URL_WIN = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip";
    private static final String URL_MAC = "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip";
    private static final String URL_LINUX = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip";
    private static final String URL_DRIVER = "https://developer.android.com/studio/run/win-usb";

    private int step;
    private boolean reconnect;
    private int os; // 0 Windows, 1 Mac, 2 Linux
    private boolean manual; // mostrar los comandos a mano en Windows
    private boolean help;

    // Detección
    private boolean dDialer, dPerms, dDev, dUsb, dCable, dPort;
    private volatile boolean portChecking;
    private String sig = "";

    // Conexión y prueba
    private int conn; // 0 nada, 1 conectando, 2 ok, 3 error
    private String connErr;
    private int test; // 0 nada, 1 probando, 2 ok, 3 fallo
    private String testOut;
    private long testStart;

    private ScrollView scroll;
    private LinearLayout content, progress;
    private TextView count, primary, secondary, skip;
    private final Handler h = new Handler(Looper.getMainLooper());

    static boolean shouldAutoShow(Context c) {
        if (Util.prefs(c).getBoolean("tut_shown", false)) return false;
        if (Util.fullLine(c) || Util.shizukuEnabled(c)) return false;
        return NativeAdbManager.savedPort(c) == 0;
    }

    /** El tutorial de instalación está terminado (o la app ya venía configurada de antes). */
    static boolean done(Context c) {
        return Util.prefs(c).getBoolean("tut_done", false) || Util.fullLine(c) || Util.shizukuEnabled(c)
                || NativeAdbManager.savedPort(c) != 0;
    }

    static void markShown(Context c) {
        Util.prefs(c).edit().putBoolean("tut_shown", true).apply();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        markShown(this);
        reconnect = getIntent().getBooleanExtra(EXTRA_RECONNECT, false);
        step = b != null ? b.getInt("step", reconnect ? 5 : 0) : (reconnect ? 5 : 0);
        os = b != null ? b.getInt("os", 0) : Util.prefs(this).getInt("tut_os", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(10), dp(18), dp(6));
        ImageView close = new ImageView(this);
        close.setImageResource(R.drawable.ic_x);
        close.setImageTintList(ColorStateList.valueOf(getColor(R.color.ink)));
        close.setBackgroundResource(R.drawable.bg_icon_btn);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setContentDescription("Cerrar tutorial");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        progress = new LinearLayout(this);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, dp(4), 1f);
        pl.setMarginStart(dp(8));
        top.addView(progress, pl);
        count = Ui.text(this, R.style.Text_Meta, "");
        count.setTypeface(Ui.bold());
        count.setGravity(Gravity.END);
        top.addView(count, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(top);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(24), dp(10), dp(24), dp(20));
        primary = Ui.button(this, "", true);
        bottom.addView(primary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout second = new LinearLayout(this);
        second.setGravity(Gravity.CENTER_VERTICAL);
        secondary = Ui.textButton(this, "");
        skip = Ui.textButton(this, "Saltar este paso");
        skip.setTextColor(getColor(R.color.ink_2));
        second.addView(secondary, new LinearLayout.LayoutParams(0, dp(46), 1f));
        second.addView(skip, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(6);
        bottom.addView(second, sl);
        root.addView(bottom);

        setContentView(root);
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onPrimary(); }
        });
        secondary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onSecondary(); }
        });
        skip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { next(); }
        });
        View.OnClickListener overview = new View.OnClickListener() {
            @Override public void onClick(View v) { if (step > 0 && step < 8 && !reconnect) go(0); }
        };
        count.setOnClickListener(overview);
        progress.setOnClickListener(overview);
        count.setContentDescription("Ver todos los pasos");
        detect();
        // Empieza donde te quedaste: si ya hiciste pasos, salta al primero que falta.
        if (b == null && !reconnect && (dDialer || dPerms || dDev || dUsb || dPort)) step = firstPending();
        render();
        registerBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("step", step);
        out.putInt("os", os);
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(poll);
    }

    @Override
    public void onBackPressed() {
        if (!stepBack()) super.onBackPressed();
    }

    /** Atrás: vuelve al paso anterior. Devuelve false si hay que cerrar el tutorial. */
    private boolean stepBack() {
        int[] o = order();
        int i = indexOf(o, step);
        if (step == 0 || step == 8 || i <= 0) return false;
        go(o[i - 1]);
        return true;
    }

    private Object backCallback;

    /** Android 13+: gesto atrás (en Android 16 ya no se llama a onBackPressed). */
    private void registerBack() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        android.window.OnBackInvokedCallback cb = new android.window.OnBackInvokedCallback() {
            @Override public void onBackInvoked() { if (!stepBack()) finish(); }
        };
        backCallback = cb;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
    }

    @Override
    protected void onDestroy() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                        (android.window.OnBackInvokedCallback) backCallback);
            } catch (Throwable ignored) { }
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) { detect(); render(); }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        if (rc == 4) Util.prefs(this).edit().putBoolean("tut_restricted", true).apply();
        if (rc == 1 && res != RESULT_OK && !Util.isDefaultDialer(this)) Util.dialerSettings(this);
        detect(); render();
    }

    // ------------------------------------------------------------------ Detección

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            int stepBefore = step;
            boolean okBefore = stepOk(step);
            detect();
            String now = signature();
            if (!now.equals(sig)) {
                render();
                // En cuanto se detecta que el paso está hecho, pasa al siguiente solo.
                if (step == stepBefore && !okBefore && stepOk(step) && autoAdvance(step)) advanceSoon(step);
            }
            if (step == 5 && !dPort && !portChecking) checkPort();
            if (test == 1) updateTestProgress();
            h.postDelayed(this, 1000);
        }
    };

    private boolean autoAdvance(int s) { return s == 1 || s == 3 || s == 4 || s == 5; }

    /** ¿Está hecho este paso? Lo que Acta no puede ver (el ordenador) se da por hecho si ya hay conexión. */
    private boolean stepOk(int s) {
        switch (s) {
            case 1: return dDialer && dPerms;
            case 2: return dPort || conn == 2 || test == 2;
            case 3: return dDev;
            case 4: return dUsb;
            case 5: return dPort || conn == 2;
            case 6: return conn == 2 || NativeAdbManager.connected(this);
            case 7: return test == 2 || Util.prefs(this).getBoolean("tut_test_ok", false);
            default: return true;
        }
    }

    private int firstPending() {
        for (int s : order()) if (!stepOk(s)) return s;
        return 8;
    }

    private void advanceSoon(final int from) {
        Toast.makeText(this, "Detectado", Toast.LENGTH_SHORT).show();
        h.postDelayed(new Runnable() {
            @Override public void run() { if (step == from) next(); }
        }, 900);
    }

    private void detect() {
        dDialer = Util.isDefaultDialer(this);
        dPerms = Util.coreGranted(this);
        try {
            dDev = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
            dUsb = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        } catch (Exception e) {
            dDev = false;
            dUsb = false;
        }
        try {
            Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            dCable = bat != null && bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == BatteryManager.BATTERY_PLUGGED_USB;
        } catch (Exception e) {
            dCable = false;
        }
        if (NativeAdbManager.connected(this)) dPort = true;
    }

    private void checkPort() {
        portChecking = true;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean open = NativeAdbManager.portOpen("127.0.0.1", 5555, 800);
                if (!open) {
                    String ip = NativeAdbManager.deviceIp();
                    if (ip != null) open = NativeAdbManager.portOpen(ip, 5555, 800);
                }
                final boolean o = open;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        portChecking = false;
                        if (o && !dPort) {
                            dPort = true;
                            Toast.makeText(TutorialActivity.this, "Puerto 5555 detectado", Toast.LENGTH_SHORT).show();
                            render();
                        }
                    }
                });
            }
        }).start();
    }

    private String signature() {
        return step + "|" + dDialer + dPerms + dDev + dUsb + dCable + dPort + "|" + conn + test + os + help;
    }

    // ------------------------------------------------------------------ Navegación

    private int[] order() { return reconnect ? RECONNECT : FULL; }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    private void go(int s) {
        step = s;
        help = false;
        render();
        scroll.scrollTo(0, 0);
    }

    private void next() {
        int[] o = order();
        int i = indexOf(o, step);
        if (i == o.length - 1) {
            if (reconnect) {
                Toast.makeText(this, "Reconectado. Se vuelven a grabar las dos voces.", Toast.LENGTH_LONG).show();
                finish();
            } else {
                go(8);
            }
        } else {
            go(o[i + 1]);
        }
    }

    private boolean canNext() {
        switch (step) {
            case 1: return dDialer && dPerms;
            case 2: return true;
            case 3: return dDev;
            case 4: return dUsb;
            case 5: return dPort;
            case 6: return conn == 2;
            case 7: return test == 2;
            default: return true;
        }
    }

    private void onPrimary() {
        if (step == 0) { reconnect = false; go(firstPending()); return; }
        if (step == 8) { finish(); return; }
        switch (step) {
            case 3:
                if (!dDev) { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS); return; }
                break;
            case 4:
                if (!dUsb) {
                    if (!dDev) go(3);
                    else openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    return;
                }
                break;
            case 6:
                if (conn == 0 || conn == 3) { doConnect(); return; }
                break;
            case 7:
                if (test == 0 || test == 3) { doTest(); return; }
                break;
            default:
        }
        if (canNext()) next();
    }

    private void onSecondary() {
        if (step == 0) { reconnect = true; go(5); return; }
        if (step == 8) return;
        int[] o = order();
        int i = indexOf(o, step);
        if (i <= 0) { if (reconnect) finish(); else go(0); }
        else go(o[i - 1]);
    }

    // ------------------------------------------------------------------ Pintado

    private void render() {
        sig = signature();
        final int y = scroll.getScrollY();
        content.removeAllViews();
        int[] o = order();
        int idx = indexOf(o, step);
        boolean showProg = step > 0 && step < 8;
        progress.removeAllViews();
        progress.setVisibility(showProg ? View.VISIBLE : View.INVISIBLE);
        count.setVisibility(showProg ? View.VISIBLE : View.INVISIBLE);
        if (showProg) {
            for (int i = 0; i < o.length; i++) {
                View bar = new View(this);
                bar.setBackgroundResource(i <= idx ? R.drawable.bg_prog_on : R.drawable.bg_prog);
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(4), 1f);
                if (i > 0) l.setMarginStart(dp(5));
                progress.addView(bar, l);
            }
            count.setText((idx + 1) + " / " + o.length);
        }

        if (step == 8) Util.prefs(this).edit().putBoolean("tut_done", true).apply();
        switch (step) {
            case 0: stepWelcome(); break;
            case 1: stepDialer(); break;
            case 2: stepTools(); break;
            case 3: stepDev(); break;
            case 4: stepUsb(); break;
            case 5: stepCable(); break;
            case 6: stepConnect(); break;
            case 7: stepTest(); break;
            default: stepDone();
        }
        if (step > 0 && step < 8) addHelp();

        // Botones inferiores
        skip.setVisibility(View.GONE);
        boolean actionBtn = false;
        if (step == 0) {
            boolean some = dDialer || dPerms || dDev || dUsb || dPort;
            primary.setText(some ? "Continuar donde lo dejé" : "Empezar");
            secondary.setText("Ya lo hice antes: solo reconectar");
            secondary.setTextColor(getColor(R.color.accent));
            secondary.setVisibility(View.VISIBLE);
        } else if (step == 8) {
            primary.setText("Empezar a usar Acta");
            secondary.setVisibility(View.GONE);
        } else {
            boolean last = idx == o.length - 1;
            primary.setText(step == 2 ? "Ya lo tengo" : last ? (reconnect ? "Terminar" : "Finalizar") : "Siguiente");
            if (step == 3 && !dDev) { primary.setText("Abrir «Sobre el teléfono»"); actionBtn = true; }
            else if (step == 4 && !dUsb) { primary.setText(dDev ? "Abrir opciones de desarrollador" : "Primero: modo desarrollador"); actionBtn = true; }
            else if (step == 6 && conn != 2) {
                primary.setText(conn == 1 ? "Conectando…" : conn == 3 ? "Reintentar" : "Conectar");
                actionBtn = conn != 1;
            } else if (step == 7 && test != 2) {
                primary.setText(test == 1 ? "Probando…" : test == 3 ? "Repetir la prueba" : "Probar grabación");
                actionBtn = test != 1;
            }
            secondary.setText(idx == 0 && reconnect ? "Cancelar" : "Atrás");
            secondary.setTextColor(getColor(R.color.ink_2));
            secondary.setVisibility(View.VISIBLE);
            // Nunca atascado: cualquier paso se puede saltar y retomar después.
            skip.setVisibility(canNext() ? View.GONE : View.VISIBLE);
        }
        boolean enabled = step == 0 || step == 8 || canNext() || actionBtn;
        primary.setEnabled(enabled);
        primary.setAlpha(enabled ? 1f : 0.4f);
        scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, y); } });
    }

    private void stepWelcome() {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackgroundResource(R.drawable.bg_note);
        hero.addView(Ui.iconView(this, R.drawable.ic_wave, R.color.accent, 34),
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(72), dp(72));
        hl.topMargin = dp(18);
        content.addView(hero, hl);
        title("Graba las dos voces de cada llamada", 29, 24);
        para("Te guío paso a paso y Acta detecta solo lo que ya tienes hecho. Necesitarás un ordenador con Windows (también sirve Mac o Linux, a mano) y el cable USB del móvil. Puedes saltar cualquier paso y volver cuando quieras.", 15.5f, 12);
        LinearLayout card = cardBox(22);
        card.setPadding(0, dp(6), 0, dp(6));
        int[] list = FULL;
        String[] names = {"App de teléfono y permisos", "Opciones de desarrollador", "Depuración USB",
                "Acta Conexión en el ordenador", "Cable y activación", "Conectar Acta", "Prueba de grabación"};
        for (int i = 0; i < list.length; i++) {
            if (i > 0) card.addView(Ui.separator(this, 18));
            card.addView(progressRow(list[i], names[i]));
        }
        TextView law = Ui.text(this, R.style.Text_Meta, "Recuerda: según tu país, grabar una llamada puede requerir avisar a la otra persona o "
                + "tener su consentimiento. Tú eres responsable de cumplir la ley.");
        law.setTextSize(13);
        law.setLineSpacing(dp(2), 1f);
        law.setPadding(dp(4), dp(16), dp(4), 0);
        content.addView(law);
    }

    private View progressRow(final int st, String label) {
        boolean ok = stepOk(st);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(18), dp(8), dp(14), dp(8));
        row.setBackgroundResource(R.drawable.bg_tbtn);
        row.addView(Ui.text(this, R.style.Text, label, 15, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = Ui.text(this, R.style.Text, ok ? "Hecho" : "Pendiente", 13.5f, true);
        state.setTextColor(getColor(ok ? R.color.ok : R.color.ink_3));
        if (ok) { Ui.icon(state, R.drawable.ic_check, R.color.ok, 16); state.setCompoundDrawablePadding(dp(6)); }
        row.addView(state);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { reconnect = false; go(st); }
        });
        return row;
    }

    private View needRow(int icon, String t, String s) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(16), dp(18), dp(16));
        row.addView(Ui.iconView(this, icon, R.color.accent, 24));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, R.style.Text, t, 15.5f, true));
        TextView sv = Ui.text(this, R.style.Text_Meta, s);
        sv.setPadding(0, dp(2), 0, 0);
        col.addView(sv);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMarginStart(dp(14));
        row.addView(col, l);
        return row;
    }

    private void stepDialer() {
        kicker("EN EL MÓVIL");
        title("Deja que Acta gestione tus llamadas", 27, 8);
        para("Acta tiene que ser tu app de teléfono para poder grabar. Pulsa cada botón y acepta.", 15.5f, 10);
        LinearLayout card = cardBox(22);
        card.setPadding(0, 0, 0, 0);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            final boolean rdone = Util.prefs(this).getBoolean("tut_restricted", false) || dDialer;
            card.addView(checkRow("1 · Permitir ajustes restringidos", "Al instalar desde fuera de la tienda, Android bloquea el permiso de app de teléfono hasta que lo permitas", rdone, "Abrir",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            try {
                                startActivityForResult(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:" + getPackageName())), 4);
                            } catch (Throwable e) { }
                        }
                    }));
            if (!rdone) {
                TextView how = Ui.text(this, R.style.Text_Meta, "En «Información de la app» pulsa ⋮ (arriba a la derecha) › «Permitir ajustes restringidos» y confirma con tu huella o PIN. Al volver, sigue con el paso 2. Si no ves ese menú, pulsa antes «Establecer» una vez y vuelve a probar.");
                how.setPadding(dp(18), 0, dp(18), dp(14));
                card.addView(how);
            }
            card.addView(Ui.separator(this, 18));
        }
        card.addView(checkRow((android.os.Build.VERSION.SDK_INT >= 33 ? "2 · " : "") + "App de teléfono predeterminada", "Para grabar cada llamada", dDialer, "Establecer",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Util.askDialer(TutorialActivity.this, 1);
                    }
                }));
        card.addView(Ui.separator(this, 18));
        card.addView(checkRow((android.os.Build.VERSION.SDK_INT >= 33 ? "3 · " : "") + "Permisos", "Teléfono, contactos, micrófono y avisos", dPerms, "Permitir",
                new View.OnClickListener() {
                    @Override public void onClick(View v) { Util.askPermissions(TutorialActivity.this, 2); }
                }));
    }

    private View checkRow(String t, String s, boolean done, String btn, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(18), dp(14), dp(12), dp(14));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, R.style.Text, t, 15.5f, true));
        TextView sv = Ui.text(this, R.style.Text_Meta, s);
        sv.setPadding(0, dp(2), 0, 0);
        col.addView(sv);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (done) {
            TextView ok = Ui.text(this, R.style.Text, "Hecho", 14, true);
            ok.setTextColor(getColor(R.color.ok));
            Ui.icon(ok, R.drawable.ic_check, R.color.ok, 18);
            ok.setCompoundDrawablePadding(dp(6));
            ok.setPadding(0, 0, dp(6), 0);
            row.addView(ok);
        } else {
            TextView b = Ui.textButton(this, btn);
            b.setBackgroundResource(R.drawable.bg_tbtn_soft);
            b.setOnClickListener(l);
            row.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        }
        return row;
    }

    private void stepTools() {
        if (os == 0) { stepToolsPc(); return; }
        kicker("EN EL ORDENADOR");
        title("Descarga las herramientas de Android", 27, 8);
        para("Son gratis y oficiales de Google. Elige tu ordenador:", 15.5f, 10);
        content.addView(osTabs(42), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 18));
        LinearLayout card = cardBox(14);
        LinearLayout one = numbered(1, "Abre este enlace en el ordenador");
        one.addView(urlBox(url()), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        TextView z = Ui.text(this, R.style.Text_Meta, "Se descarga un archivo ZIP de unos 7 MB.");
        z.setPadding(0, dp(8), 0, 0);
        one.addView(z);
        card.addView(numberedRow(1, one));
        card.addView(divider());
        LinearLayout two = numbered(2, os == 0 ? "Clic derecho en el ZIP › Extraer todo" : "Haz doble clic en el ZIP para descomprimirlo");
        TextView t2 = Ui.text(this, R.style.Text_Meta, rich("Dentro tendrás una carpeta llamada ", "platform-tools", "."));
        t2.setPadding(0, dp(4), 0, 0);
        two.addView(t2);
        card.addView(numberedRow(2, two));
        TextView send = Ui.button(this, "Enviarme los enlaces y comandos", false);
        Ui.icon(send, R.drawable.ic_send, R.color.accent, 18);
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareInstructions(); }
        });
        content.addView(send, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 12));
        TextView hint = Ui.text(this, R.style.Text_Meta, "Por WhatsApp o correo, para abrirlos en el ordenador.");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(10), dp(8), dp(10), 0);
        content.addView(hint);
    }

    private void stepDev() {
        kicker("EN EL MÓVIL");
        title("Activa las opciones de desarrollador", 27, 8);
        para("Es un menú oculto de Android. Se activa así:", 15.5f, 10);
        LinearLayout card = cardBox(18);
        card.addView(path("Ajustes", "Sobre el teléfono"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView x7 = Ui.text(this, R.style.Text, "×7", 15, true);
        x7.setTextColor(getColor(R.color.accent));
        x7.setGravity(Gravity.CENTER);
        x7.setBackgroundResource(R.drawable.bg_circle_soft);
        row.addView(x7, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView tx = Ui.text(this, R.style.Text, rich("Toca ", "7 veces seguidas", " en ", "Versión de HyperOS", ". Si te pide el PIN, ponlo."));
        tx.setTextSize(15);
        tx.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(dp(12));
        row.addView(tx, tl);
        card.addView(row, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 16));
        if (dDev) status(true, "Opciones de desarrollador activadas");
        else waiting("Pulsa el botón de abajo: abre «Sobre el teléfono». Al volver, Acta lo detecta solo.");
    }

    private void stepUsb() {
        kicker("EN EL MÓVIL");
        title("Activa la depuración USB", 27, 8);
        para("Permite que el ordenador hable con el móvil por el cable.", 15.5f, 10);
        LinearLayout card = cardBox(18);
        card.addView(path("Ajustes", "Ajustes adicionales", "Opciones de desarrollador"));
        LinearLayout sw = new LinearLayout(this);
        sw.setGravity(Gravity.CENTER_VERTICAL);
        sw.setBackgroundResource(R.drawable.bg_url);
        sw.setPadding(dp(14), dp(12), dp(14), dp(12));
        sw.addView(Ui.text(this, R.style.Text, "Depuración USB", 15, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch s = new android.widget.Switch(this);
        s.setChecked(true);
        s.setClickable(false);
        s.setFocusable(false);
        s.setThumbTintList(ColorStateList.valueOf(getColor(R.color.on_accent)));
        s.setTrackTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        sw.addView(s);
        card.addView(sw, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 16));
        TextView n = Ui.text(this, R.style.Text_Meta, "Xiaomi enseña varios avisos con cuenta atrás. Espera y acéptalos todos.");
        n.setLineSpacing(dp(2), 1f);
        n.setPadding(0, dp(12), 0, 0);
        card.addView(n);
        if (dUsb) status(true, "Depuración USB activada");
        else if (!dDev) waiting("Antes hay que activar el modo desarrollador (paso anterior).");
        else waiting("Pulsa el botón de abajo: abre las opciones de desarrollador. Al volver, Acta lo detecta solo.");
    }

    private void stepToolsPc() {
        kicker("EN EL ORDENADOR");
        title("Instala Acta Conexión", 27, 8);
        para("Es un programa gratuito y de código abierto que hace todo el trabajo técnico por ti, y vuelve a activar la grabación solo cuando reinicies el móvil.", 15.5f, 10);
        content.addView(osTabs(42), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 18));
        LinearLayout card = cardBox(14);
        LinearLayout one = numbered(1, "Abre este enlace en el ordenador");
        one.addView(urlBox(Project.URL_PC), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        TextView z = Ui.text(this, R.style.Text_Meta, "Toca el enlace para abrirlo aquí, o cópialo y ábrelo en el ordenador. Descarga «ActaConexion-Windows.zip» (unos 3 MB).");
        z.setPadding(0, dp(8), 0, 0);
        one.addView(z);
        card.addView(numberedRow(1, one));
        card.addView(divider());
        LinearLayout two = numbered(2, "Clic derecho en el ZIP › Extraer todo");
        TextView t2 = Ui.text(this, R.style.Text_Meta, rich("Dentro está ", "ActaConexion.exe", ". No necesita instalación ni permisos de administrador."));
        t2.setPadding(0, dp(4), 0, 0);
        two.addView(t2);
        card.addView(numberedRow(2, two));
        card.addView(divider());
        LinearLayout three = numbered(3, "Ábrelo con doble clic");
        TextView t3 = Ui.text(this, R.style.Text_Meta, "Si Windows muestra «Windows protegió su PC», pulsa Más información › Ejecutar de todas formas. Aparece porque es un programa nuevo; puedes revisar su código en GitHub.");
        t3.setPadding(0, dp(4), 0, 0);
        three.addView(t3);
        card.addView(numberedRow(3, three));
        TextView send = Ui.button(this, "Enviarme el enlace", false);
        Ui.icon(send, R.drawable.ic_send, R.color.accent, 18);
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareInstructions(); }
        });
        content.addView(send, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 12));
        TextView hint = Ui.text(this, R.style.Text_Meta, "Por WhatsApp o correo, para abrirlo en el ordenador.");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(10), dp(8), dp(10), 0);
        content.addView(hint);
    }

    private void stepCablePc() {
        kicker(reconnect ? "TRAS REINICIAR EL MÓVIL" : "EN EL ORDENADOR");
        title("Conecta el cable y deja actuar a Acta Conexión", 26, 8);
        if (reconnect) para("Al reiniciar, Android cierra la conexión. Con Acta Conexión abierto, solo tienes que conectar el cable (1 minuto).", 15.5f, 10);
        LinearLayout card = cardBox(18);
        LinearLayout one = numbered(1, "Conecta el móvil al ordenador");
        TextView d1 = Ui.text(this, R.style.Text_Meta, rich("En el móvil saldrá este aviso. Marca la casilla y pulsa ", "Aceptar", "."));
        d1.setLineSpacing(dp(2), 1f);
        d1.setPadding(0, dp(4), 0, 0);
        one.addView(d1);
        one.addView(miniDialog(), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        TextView cable = Ui.text(this, R.style.Text, dCable ? "Cable conectado" : "Esperando el cable…", 13, true);
        cable.setTextColor(getColor(dCable ? R.color.ok : R.color.ink_2));
        if (dCable) { Ui.icon(cable, R.drawable.ic_check, R.color.ok, 16); cable.setCompoundDrawablePadding(dp(6)); }
        cable.setPadding(0, dp(10), 0, 0);
        one.addView(cable);
        card.addView(numberedRow(1, one));
        card.addView(divider());
        LinearLayout two = numbered(2, "Abre Acta Conexión y sigue su pantalla");
        TextView t2 = Ui.text(this, R.style.Text_Meta, "Detecta tu móvil y lo activa solo. Cuando se ponga todo en verde, puedes desconectar el cable.");
        t2.setPadding(0, dp(4), 0, 0);
        two.addView(t2);
        card.addView(numberedRow(2, two));
        card.addView(divider());
        LinearLayout three = numbered(3, "Para que reconecte siempre sola");
        TextView t3 = Ui.text(this, R.style.Text_Meta, rich("En el programa, activa ", "Iniciar con Windows", " y ", "Seguir vigilando al cerrar la ventana", "."));
        t3.setPadding(0, dp(4), 0, 0);
        three.addView(t3);
        card.addView(numberedRow(3, three));
        if (dPort) status(true, "¡Listo! Ya puedes desconectar el cable.");
        else waiting("Esperando a que Acta Conexión active el móvil…");
        TextView alt = Ui.text(this, R.style.Text, manual ? "Ocultar los comandos manuales" : "Prefiero hacerlo a mano", 14, true);
        alt.setTextColor(getColor(R.color.accent));
        alt.setGravity(Gravity.CENTER);
        alt.setPadding(0, dp(18), 0, dp(6));
        alt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { manual = !manual; render(); }
        });
        content.addView(alt);
        if (manual) {
            para("Abre una consola dentro de la carpeta platform-tools y escribe, uno a uno:", 14f, 6);
            content.addView(code("adb devices", "Debe salir:  a1b2c3d4    device"), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
            content.addView(code("adb tcpip 5555", "Debe salir:  restarting in TCP mode port: 5555"), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        }
    }

    private void stepCable() {
        if (os == 0) { stepCablePc(); return; }
        kicker(reconnect ? "TRAS REINICIAR EL MÓVIL" : "EN EL ORDENADOR");
        title("Conecta el cable y escribe dos comandos", 27, 8);
        if (reconnect) para("Al reiniciar, Android cierra la conexión. Repite solo esto (1 minuto).", 15.5f, 10);
        LinearLayout card = cardBox(18);

        LinearLayout one = numbered(1, "Conecta el móvil al ordenador");
        TextView d1 = Ui.text(this, R.style.Text_Meta, rich("En el móvil saldrá este aviso. Marca la casilla y pulsa ", "Aceptar", "."));
        d1.setLineSpacing(dp(2), 1f);
        d1.setPadding(0, dp(4), 0, 0);
        one.addView(d1);
        one.addView(miniDialog(), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        TextView cable = Ui.text(this, R.style.Text, dCable ? "Cable conectado" : "Esperando el cable…", 13, true);
        cable.setTextColor(getColor(dCable ? R.color.ok : R.color.ink_2));
        if (dCable) { Ui.icon(cable, R.drawable.ic_check, R.color.ok, 16); cable.setCompoundDrawablePadding(dp(6)); }
        cable.setPadding(0, dp(10), 0, 0);
        one.addView(cable);
        card.addView(numberedRow(1, one));
        card.addView(divider());

        LinearLayout two = numbered(2, "Abre la consola");
        two.addView(osTabs(36), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        TextView ct = Ui.text(this, R.style.Text, os == 0
                ? rich("Abre la carpeta ", "platform-tools", ". Haz clic en la barra de direcciones de arriba, escribe ", "cmd", " y pulsa Intro.")
                : os == 1 ? rich("Abre la app ", "Terminal", " (búscala con Cmd + Espacio) y escribe los comandos de abajo.")
                : rich("Abre una ", "terminal", " y escribe los comandos de abajo."));
        ct.setTextSize(14);
        ct.setLineSpacing(dp(3), 1f);
        ct.setPadding(0, dp(10), 0, 0);
        two.addView(ct);
        card.addView(numberedRow(2, two));
        card.addView(divider());

        LinearLayout three = numbered(3, "Copia y pega, uno a uno");
        String pre = os == 0 ? "" : "./";
        if (os != 0) three.addView(code("cd ~/Downloads/platform-tools", "entra en la carpeta"), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        three.addView(code(pre + "adb devices", "Debe salir:  a1b2c3d4    device"), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        three.addView(code(pre + "adb tcpip 5555", "Debe salir:  restarting in TCP mode port: 5555"), Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        card.addView(numberedRow(3, three));

        if (dPort) status(true, "¡Listo! Ya puedes desconectar el cable.");
        else waiting("Esperando a que ejecutes los comandos…");
    }

    private void stepConnect() {
        kicker("EN EL MÓVIL");
        title("Conecta Acta", 27, 8);
        para(rich("Pulsa Conectar. Android te preguntará si permites la depuración: marca ", "Permitir siempre", " y acepta."), 15.5f, 10);
        if (conn == 0 || conn == 3) {
            if (conn == 3) {
                LinearLayout err = statusBox(R.drawable.bg_status_warn);
                TextView et = Ui.text(this, R.style.Text, connErr);
                et.setTextColor(getColor(R.color.warn));
                et.setTextSize(14);
                et.setLineSpacing(dp(3), 1f);
                err.addView(et);
                content.addView(err, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 12));
            }
        } else if (conn == 1) {
            LinearLayout box = statusBox(R.drawable.bg_status_n);
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
            pb.setIndeterminateTintList(ColorStateList.valueOf(getColor(R.color.accent)));
            box.addView(pb, new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView t = Ui.text(this, R.style.Text, "Conectando… acepta el aviso del móvil si aparece.");
            t.setTextColor(getColor(R.color.ink_2));
            t.setTextSize(14);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            l.setMarginStart(dp(10));
            box.addView(t, l);
            content.addView(box, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 18));
        } else {
            status(true, "Conectado · " + NativeAdbManager.savedHost(this) + ":" + NativeAdbManager.savedPort(this));
        }
    }

    private void stepTest() {
        kicker("ÚLTIMO PASO");
        title("Haz una prueba", 27, 8);
        para("Acta graba 5 segundos y corta la conexión a mitad para comprobar que aguanta.", 15.5f, 10);
        if (test == 0 || test == 3) {
            if (test == 3) {
                LinearLayout err = statusBox(R.drawable.bg_status_warn);
                TextView et = Ui.text(this, R.style.Text, "La prueba no salió bien. Vuelve al paso del cable y repite los dos comandos.\n\n" + testOut);
                et.setTextColor(getColor(R.color.warn));
                et.setTextSize(13);
                et.setLineSpacing(dp(3), 1f);
                err.addView(et);
                content.addView(err, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 12));
            }
        } else if (test == 1) {
            LinearLayout card = cardBox(18);
            LinearLayout row = new LinearLayout(this);
            TextView a = Ui.text(this, R.style.Text, "Grabando prueba…", 14, true);
            row.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView secs = Ui.text(this, R.style.Text, "0 s", 14, true);
            secs.setTag("secs");
            row.addView(secs);
            card.addView(row);
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setTag("bar");
            pb.setMax(100);
            pb.setProgressTintList(ColorStateList.valueOf(getColor(R.color.accent)));
            pb.setProgressBackgroundTintList(ColorStateList.valueOf(getColor(R.color.track)));
            card.addView(pb, Ui.lpTop(this, Ui.MATCH, dp(8), 12));
            updateTestProgress();
        } else {
            status(true, "Graba las dos voces correctamente");
        }
    }

    private void stepDone() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, dp(30), 0, 0);
        FrameLayout c = new FrameLayout(this);
        c.setBackgroundResource(R.drawable.bg_circle_oksoft);
        c.addView(Ui.iconView(this, R.drawable.ic_check, R.color.ok, 42), new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        col.addView(c, new LinearLayout.LayoutParams(dp(88), dp(88)));
        TextView t = Ui.text(this, R.style.Text, "Todo listo", 29, true);
        t.setPadding(0, dp(24), 0, 0);
        col.addView(t);
        TextView s = Ui.text(this, R.style.Text_Meta, "Acta grabará las dos voces de todas tus llamadas.");
        s.setTextSize(16);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(10), 0, 0);
        col.addView(s);
        content.addView(col);
        LinearLayout card = cardBox(28);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(Ui.iconView(this, R.drawable.ic_refresh, R.color.accent, 20));
        TextView n = Ui.text(this, R.style.Text, "Si reinicias el móvil, Android cierra la conexión. Acta te avisará y solo tendrás que repetir el paso del cable.");
        n.setTextSize(14.5f);
        n.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMarginStart(dp(12));
        card.addView(n, l);
    }

    // ------------------------------------------------------------------ Ayuda

    private void addHelp() {
        String[][] items = helpItems();
        if (items.length == 0) return;
        LinearLayout card = Ui.card(this);
        TextView head = Ui.text(this, R.style.Text, "¿Algo no va?", 14.5f, true);
        head.setTextColor(getColor(R.color.ink_2));
        Ui.icon(head, R.drawable.ic_help, R.color.ink_2, 19);
        head.setCompoundDrawablePadding(dp(12));
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinHeight(dp(52));
        head.setPadding(dp(16), 0, dp(16), 0);
        head.setBackgroundResource(R.drawable.bg_tbtn);
        head.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { help = !help; render(); }
        });
        card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (help) {
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(18), 0, dp(18), dp(16));
            for (int i = 0; i < items.length; i++) {
                TextView q = Ui.text(this, R.style.Text, items[i][0], 14, true);
                if (i > 0) q.setPadding(0, dp(12), 0, 0);
                body.addView(q);
                TextView a = Ui.text(this, R.style.Text_Meta, items[i][1]);
                a.setTextSize(14);
                a.setLineSpacing(dp(3), 1f);
                a.setPadding(0, dp(3), 0, 0);
                a.setTextIsSelectable(true);
                body.addView(a);
            }
            if (step == 2 && os == 0) {
                TextView link = Ui.textButton(this, "Copiar enlace del driver");
                link.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { copy(URL_DRIVER, "Enlace copiado"); }
                });
                body.addView(link, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
            }
            if (step == 6) {
                TextView manual = Ui.textButton(this, "Conexión manual (IP y puerto)");
                manual.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { startActivity(new Intent(TutorialActivity.this, NativePairActivity.class)); }
                });
                body.addView(manual, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
            }
            card.addView(body);
        }
        content.addView(card, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 14));
    }

    private String[][] helpItems() {
        switch (step) {
            case 1: return new String[][]{{"No aparece el aviso", "Abre Ajustes › Aplicaciones › Apps predeterminadas › Teléfono y elige Acta."}};
            case 2: return os == 0
                    ? new String[][]{{"¿Dónde está el ZIP?", "En la carpeta Descargas. Clic derecho › Extraer todo."},
                    {"Windows no me deja abrirlo", "Pulsa Más información › Ejecutar de todas formas. Si el archivo viene bloqueado: clic derecho › Propiedades › Desbloquear."},
                    {"El ordenador no reconoce el móvil", "Instala el driver USB de Google: " + URL_DRIVER}}
                    : new String[][]{{"¿Dónde está el ZIP?", "En Descargas. Haz doble clic para descomprimirlo."},
                    {"El sistema bloquea adb", os == 1 ? "Ajustes del Sistema › Privacidad y seguridad › «Abrir igualmente»."
                            : "Dale permiso de ejecución: chmod +x adb"}};
            case 3: return new String[][]{{"No encuentro «Versión de HyperOS»", "Está arriba del todo en Sobre el teléfono. Al tocar 7 veces sale «Ya eres desarrollador»."}};
            case 4: return new String[][]{{"No veo Opciones de desarrollador", "Están en Ajustes › Ajustes adicionales, casi al final. Si no aparecen, repite el paso anterior."}};
            case 5: return os == 0 && !manual ? new String[][]{
                    {"Acta Conexión no ve el móvil", "Prueba otro cable o puerto USB. En la notificación USB del móvil elige «Transferencia de archivos» y revisa que la depuración USB esté activada."},
                    {"Sale «unauthorized»", "Desbloquea el móvil y acepta el aviso «Permitir depuración USB»."}}
                    : new String[][]{
                    {"adb devices no muestra nada", "Prueba otro cable o puerto USB. En la notificación USB del móvil elige «Transferencia de archivos»."},
                    {"Sale «unauthorized»", "Desbloquea el móvil y acepta el aviso. Si no sale, desconecta y vuelve a conectar el cable."},
                    os == 0 ? new String[]{"«adb» no se reconoce", "Abre la consola dentro de la carpeta platform-tools. En PowerShell escribe .\\adb en vez de adb."}
                            : new String[]{"command not found", "Comprueba que estás en la carpeta platform-tools (comando cd) y escribe ./adb"}};
            case 6: return new String[][]{{"No sale el aviso de permitir", "Baja la cortina de notificaciones. Si dice «Connection refused», repite adb tcpip 5555 en el ordenador."}};
            case 7: return new String[][]{{"La prueba falla", "Vuelve al paso del cable y repite los dos comandos."}};
            default: return new String[0][];
        }
    }

    // ------------------------------------------------------------------ Acciones

    private void doConnect() {
        conn = 1;
        render();
        new Thread(new Runnable() {
            @Override public void run() {
                String used = "127.0.0.1";
                boolean ok = NativeAdbManager.connect(TutorialActivity.this, used, 5555);
                if (!ok) {
                    String ip = NativeAdbManager.deviceIp();
                    if (ip != null) {
                        ok = NativeAdbManager.connect(TutorialActivity.this, ip, 5555);
                        if (ok) used = ip;
                    }
                }
                if (ok) {
                    NativeAdbManager.saveEndpoint(TutorialActivity.this, used, 5555);
                    Util.prefs(TutorialActivity.this).edit().putBoolean("native_adb", true).apply();
                    NativeAdbManager.startKeepAlive(TutorialActivity.this);
                }
                final boolean fok = ok;
                final String err = NativeAdbManager.lastError();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        conn = fok ? 2 : 3;
                        connErr = "No se pudo conectar" + (err != null ? ": " + err : ".")
                                + "\n\nComprueba que «adb tcpip 5555» dijo «restarting in TCP mode». Si el móvil se reinició, repite el paso del cable.";
                        render();
                    }
                });
            }
        }).start();
    }

    private void doTest() {
        test = 1;
        testStart = System.currentTimeMillis();
        render();
        new Thread(new Runnable() {
            @Override public void run() {
                final String out = NativeAdbManager.diagnose(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        testOut = out;
                        test = out != null && out.contains("✓") ? 2 : 3;
                        if (test == 2) Util.prefs(TutorialActivity.this).edit().putBoolean("tut_test_ok", true).apply();
                        render();
                    }
                });
            }
        }).start();
    }

    private void updateTestProgress() {
        View secs = content.findViewWithTag("secs");
        View bar = content.findViewWithTag("bar");
        if (secs == null || bar == null) return;
        long el = Math.min(7000, System.currentTimeMillis() - testStart);
        ((TextView) secs).setText(Math.min(5, el / 1000) + " s");
        ((ProgressBar) bar).setProgress((int) Math.min(100, el * 100 / 6000));
    }

    /** Abre justo la pantalla pedida; si el móvil no la tiene, cae a la más cercana y avisa de dónde buscar. */
    private void openSettings(String action) {
        String[] chain = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS.equals(action)
                ? new String[]{action, "com.android.settings.APPLICATION_DEVELOPMENT_SETTINGS", Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS}
                : new String[]{action, Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS};
        for (int i = 0; i < chain.length; i++) {
            try {
                startActivity(new Intent(chain[i]));
                if (i > 0) {
                    Toast.makeText(this, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS.equals(action)
                            ? "Busca: Ajustes adicionales › Opciones de desarrollador" : "Busca: Sobre el teléfono", Toast.LENGTH_LONG).show();
                }
                return;
            } catch (Exception ignored) { }
        }
    }

    private void copy(String text, String toast) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Acta", text));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private void shareInstructions() {
        String txt = "Acta · configurar la grabación de las dos voces\n\n"
                + "OPCIÓN FÁCIL (Windows): descarga Acta Conexión, extrae el ZIP y abre ActaConexion.exe:\n"
                + Project.URL_PC + "\n\n"
                + "OPCIÓN A MANO (Windows, Mac o Linux)\n"
                + "1) Descarga las herramientas de Android:\n"
                + "Windows: " + URL_WIN + "\n"
                + "Mac: " + URL_MAC + "\n"
                + "Linux: " + URL_LINUX + "\n\n"
                + "2) Descomprime el ZIP (carpeta platform-tools).\n\n"
                + "3) Conecta el móvil con el cable y acepta «Permitir depuración USB».\n\n"
                + "4) Abre la consola dentro de platform-tools y escribe:\n"
                + "Windows (cmd):\n   adb devices\n   adb tcpip 5555\n"
                + "Mac / Linux:\n   cd ~/Downloads/platform-tools\n   ./adb devices\n   ./adb tcpip 5555\n\n"
                + "Driver USB para Windows (si no reconoce el móvil): " + URL_DRIVER;
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Configurar Acta en el ordenador")
                .putExtra(Intent.EXTRA_TEXT, txt);
        try { startActivity(Intent.createChooser(i, "Enviar al ordenador")); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ Piezas

    private String url() { return os == 0 ? URL_WIN : os == 1 ? URL_MAC : URL_LINUX; }

    private void kicker(String s) {
        TextView t = Ui.text(this, R.style.Text, s, 13, true);
        t.setTextColor(getColor(R.color.accent));
        t.setLetterSpacing(0.04f);
        t.setPadding(0, dp(4), 0, 0);
        content.addView(t);
    }

    private void title(String s, float sp, float top) {
        TextView t = Ui.text(this, R.style.Text, s, sp, true);
        t.setLetterSpacing(-0.02f);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(0, dp(top), 0, 0);
        content.addView(t);
    }

    private void para(CharSequence s, float sp, float top) {
        TextView t = Ui.text(this, R.style.Text_Meta, s);
        t.setTextSize(sp);
        t.setLineSpacing(dp(5), 1f);
        t.setPadding(0, dp(top), 0, 0);
        content.addView(t);
    }

    private LinearLayout cardBox(float top) {
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        content.addView(card, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, top));
        return card;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(getColor(R.color.line));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        l.topMargin = dp(16);
        l.bottomMargin = dp(16);
        v.setLayoutParams(l);
        return v;
    }

    /** Columna derecha de un paso numerado, con su título. */
    private LinearLayout numbered(int n, String title) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, R.style.Text, title, 15.5f, true);
        t.setLineSpacing(dp(3), 1f);
        col.addView(t);
        return col;
    }

    private View numberedRow(int n, LinearLayout col) {
        LinearLayout row = new LinearLayout(this);
        TextView num = Ui.text(this, R.style.Text, String.valueOf(n), 13, true);
        num.setTextColor(getColor(R.color.on_accent));
        num.setGravity(Gravity.CENTER);
        num.setBackgroundResource(R.drawable.bg_circle_accent);
        row.addView(num, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMarginStart(dp(12));
        row.addView(col, l);
        return row;
    }

    private View osTabs(float height) {
        LinearLayout seg = new LinearLayout(this);
        seg.setBackgroundResource(R.drawable.bg_seg);
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        String[] labels = {"Windows", "Mac", "Linux"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView b = Ui.text(this, R.style.Text, labels[i], height > 38 ? 14 : 13, true);
            b.setGravity(Gravity.CENTER);
            b.setTextColor(getColor(os == i ? R.color.ink : R.color.ink_2));
            if (os == i) { b.setBackgroundResource(R.drawable.bg_seg_on); b.setElevation(dp(1)); }
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    os = k;
                    Util.prefs(TutorialActivity.this).edit().putInt("tut_os", k).apply();
                    render();
                }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(height - 6), 1f);
            if (i > 0) l.setMarginStart(dp(3));
            seg.addView(b, l);
        }
        return seg;
    }

    private View urlBox(final String u) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.bg_url);
        box.setPadding(dp(14), dp(10), dp(4), dp(10));
        TextView t = Ui.text(this, R.style.Text, u);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(12.5f);
        t.setLineSpacing(dp(2), 1f);
        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))); }
                catch (Throwable e) { copy(u, "Enlace copiado"); }
            }
        });
        box.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(copyButton(R.color.ink, new View.OnClickListener() {
            @Override public void onClick(View v) { copy(u, "Enlace copiado"); }
        }));
        return box;
    }

    private View code(final String cmd, String out) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_code);
        box.setPadding(dp(14), dp(8), dp(4), dp(10));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView c = Ui.text(this, R.style.Text, cmd);
        c.setTypeface(Typeface.MONOSPACE);
        c.setTextSize(14);
        c.setTextColor(getColor(R.color.code_ink));
        c.setTextIsSelectable(true);
        row.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(copyButton(R.color.av1_bg, new View.OnClickListener() {
            @Override public void onClick(View v) { copy(cmd, "Copiado: " + cmd); }
        }));
        box.addView(row);
        TextView o = Ui.text(this, R.style.Text, out);
        o.setTypeface(Typeface.MONOSPACE);
        o.setTextSize(11.5f);
        o.setTextColor(getColor(R.color.code_dim));
        o.setPadding(0, 0, dp(10), 0);
        box.addView(o);
        return box;
    }

    private View copyButton(int tint, View.OnClickListener l) {
        ImageView b = new ImageView(this);
        b.setImageResource(R.drawable.ic_copy);
        b.setImageTintList(ColorStateList.valueOf(getColor(tint == R.color.av1_bg ? R.color.code_ink : tint)));
        b.setBackgroundResource(R.drawable.bg_icon_btn);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription("Copiar");
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return b;
    }

    private View path(String... parts) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        wrap.addView(row);
        int used = 0;
        int max = getResources().getDisplayMetrics().widthPixels - dp(110);
        for (int i = 0; i < parts.length; i++) {
            TextView p = Ui.text(this, R.style.Text, parts[i], 13, true);
            p.setBackgroundResource(R.drawable.bg_path);
            p.setGravity(Gravity.CENTER);
            p.setPadding(dp(10), 0, dp(10), 0);
            p.measure(0, 0);
            int w = p.getMeasuredWidth() + dp(26);
            if (used > 0 && used + w > max) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                wrap.addView(row, Ui.lpTop(this, Ui.WRAP, Ui.WRAP, 6));
                used = 0;
            }
            if (used > 0 || i > 0) {
                ImageView ch = Ui.iconView(this, R.drawable.ic_chevron, R.color.ink_3, 14);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(14), dp(14));
                cl.setMarginStart(dp(4));
                cl.setMarginEnd(dp(4));
                row.addView(ch, cl);
            }
            row.addView(p, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
            used += w;
        }
        return wrap;
    }

    private View miniDialog() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundResource(R.drawable.bg_url);
        d.setPadding(dp(14), dp(12), dp(14), dp(10));
        d.addView(Ui.text(this, R.style.Text, "¿Permitir depuración USB?", 14, true));
        TextView cb = Ui.text(this, R.style.Text, "Permitir siempre desde este ordenador");
        cb.setTextSize(13);
        Ui.icon(cb, R.drawable.ic_check, R.color.accent, 16);
        cb.setCompoundDrawablePadding(dp(8));
        cb.setPadding(0, dp(8), 0, 0);
        d.addView(cb);
        TextView bt = Ui.text(this, R.style.Text, "Cancelar      Aceptar", 13, true);
        bt.setTextColor(getColor(R.color.accent));
        bt.setGravity(Gravity.END);
        bt.setPadding(0, dp(8), 0, 0);
        d.addView(bt);
        return d;
    }

    private LinearLayout statusBox(int bg) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(bg);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        return box;
    }

    private void status(boolean ok, String text) {
        LinearLayout box = statusBox(ok ? R.drawable.bg_status_ok : R.drawable.bg_status_warn);
        box.addView(Ui.iconView(this, ok ? R.drawable.ic_check : R.drawable.ic_alert, ok ? R.color.ok : R.color.warn, 20));
        TextView t = Ui.text(this, R.style.Text, text, 14.5f, true);
        t.setTextColor(getColor(ok ? R.color.ok : R.color.warn));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMarginStart(dp(10));
        box.addView(t, l);
        content.addView(box, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 12));
    }

    private void waiting(String text) {
        LinearLayout box = statusBox(R.drawable.bg_status_n);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        pb.setIndeterminateTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        box.addView(pb, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView t = Ui.text(this, R.style.Text, text);
        t.setTextColor(getColor(R.color.ink_2));
        t.setTextSize(14);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMarginStart(dp(10));
        box.addView(t, l);
        content.addView(box, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 12));
    }

    private void openButton(String label, final String action) {
        TextView b = Ui.button(this, label, false);
        Ui.icon(b, R.drawable.ic_ext, R.color.accent, 18);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSettings(action); }
        });
        content.addView(b, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 12));
    }

    /** Texto con partes en negrita: alterna normal, negrita, normal… */
    private static CharSequence rich(String... parts) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < parts.length; i++) {
            int start = sb.length();
            sb.append(parts[i]);
            if (i % 2 == 1) sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }
}
