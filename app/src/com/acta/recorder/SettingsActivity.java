package com.acta.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SettingsActivity extends Activity {

    private LinearLayout content;
    private View rowPerms, rowDialer, rowLine, rowBattery, rowIncoming, rowOutgoing, rowStorage, rowExport, rowXDialer, rowXAuto, rowXAccess, rowXFolder, rowXScan, rowShzToggle, rowShzStatus, rowShzHelp, rowNvToggle, rowNvPair, rowNvStatus, rowHideId, rowSpam, rowAiKey, rowIsland, rowQuickMsg, rowExact, rowAiAuto, rowAiCtx, rowOwner, rowSplit, rowLock, rowCrypto;
    private boolean xiaomiMode;

    private LinearLayout group;
    private View heroCard;
    private int themeSeen;

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        themeSeen = Ui.themeGen;
        setContentView(R.layout.activity_settings);
        content = findViewById(R.id.content);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        buildHero();
        buildNativeSection();
        xiaomiMode = false;
        section("Estado");
        rowPerms = row("Permisos", "Teléfono, contactos, registro de llamadas, micrófono y notificaciones.", new Click() {
            @Override void on() {
                if (Util.allGranted(SettingsActivity.this)) openAppInfo();
                else requestPermissions(Util.runtimePermissions(), 2);
            }
        });
        if (!xiaomiMode) {
        rowDialer = row("App de teléfono predeterminada",
                    "Acta gestiona todas las llamadas: marcar, contestar, contactos e historial.", new Click() {
                        @Override void on() {
                            if (Util.isDefaultDialer(SettingsActivity.this)) {
                                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                            } else {
                                Intent i = Util.requestDialerRole(SettingsActivity.this);
                                if (i != null) startActivityForResult(i, 3);
                            }
                        }
                    });
        }
        rowBattery = row("Uso de batería", "Sin restricciones, para que el sistema no interrumpa las llamadas ni la grabación.", new Click() {
            @Override void on() {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            }
        });

        section("Grabación");
        rowIncoming = row("Grabar llamadas recibidas", "Automáticamente, al contestar.", new Click() {
            @Override void on() { flip("incoming"); }
        });
        rowOutgoing = row("Grabar llamadas realizadas", "Automáticamente, cuando la otra persona contesta.", new Click() {
            @Override void on() { flip("outgoing"); }
        });

        buildAiSection();
        buildPrivacySection();
        buildQuickSection();
        buildCallsSection();
        if (SimInfo.dual(this)) buildSimSection();
        buildAppearanceSection();

        section("Almacenamiento");
        rowStorage = row("Carpeta", "Android/data/com.acta.recorder/files/Grabaciones", null);
        rowAutoDel = row("Borrar audios automáticamente",
                "Solo el audio. Se conservan las transcripciones, los resúmenes y las grabaciones favoritas.", new Click() {
            @Override void on() { chooseKeepDays(); }
        });
        rowExport = row("Exportar registro", "Archivo CSV con número, fecha, hora y duración de cada llamada.", new Click() {
            @Override void on() { exportCsv(); }
        });

        section("Información legal");
        row("Política de privacidad", "Qué datos usa Acta y cuándo salen del móvil.", new Click() {
            @Override void on() { startActivity(new Intent(SettingsActivity.this, LegalActivity.class).putExtra("doc", "privacy")); }
        });
        row("Aviso legal y exención de responsabilidad", "Condiciones de uso de Acta.", new Click() {
            @Override void on() { startActivity(new Intent(SettingsActivity.this, LegalActivity.class)); }
        });

        row("Código abierto", "Acta es software libre (GPL-3.0). Código fuente y descargas en GitHub.", new Click() {
            @Override void on() {
                try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Project.URL_REPO))); }
                catch (Exception e) { Toast.makeText(SettingsActivity.this, "No se pudo abrir el enlace", Toast.LENGTH_LONG).show(); }
            }
        });

        // Ayuda: canal de Telegram.
        TextView help = Ui.button(this, "Ayuda por Telegram", true);
        Ui.icon(help, R.drawable.ic_send, R.color.on_accent, 20);
        help.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/actaapp")));
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "No se pudo abrir el enlace: t.me/actaapp", Toast.LENGTH_LONG).show();
                }
            }
        });
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.setMargins(dp(16), dp(28), dp(16), 0);
        content.addView(help, hl);

        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        TextView foot = new TextView(this, null, 0, R.style.Text_Meta);
        foot.setText("Acta " + ver + "\nLas grabaciones se guardan únicamente en este teléfono.");
        foot.setLineSpacing(0, 1.3f);
        foot.setTextSize(12);
        foot.setGravity(android.view.Gravity.CENTER);
        int p = dp(24);
        foot.setPadding(p, dp(26), p, 0);
        content.addView(foot);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSeen != Ui.themeGen) { recreate(); return; }
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        refresh();
        if (!Util.allGranted(this)) {
            boolean canAsk = false;
            for (String perm : Util.runtimePermissions())
                if (!Util.has(this, perm) && shouldShowRequestPermissionRationale(perm)) canAsk = true;
            if (!canAsk) openAppInfo();
        }
    }

    private void refresh() {
        status(rowPerms, Util.allGranted(this) ? "Concedidos" : (Util.coreGranted(this) ? "Parcial" : "Conceder"),
                Util.allGranted(this));
        if (rowDialer != null) status(rowDialer, Util.isDefaultDialer(this) ? "Activa" : "Establecer", Util.isDefaultDialer(this));
        refreshNative();
        refreshCalls();
        refreshQuick();
        refreshAi();
        refreshPrivacy();
        refreshHero();
        if (rowSims != null) {
            int n = SimInfo.rememberedCount(this);
            TextView sv = rowSims.findViewById(R.id.value);
            sv.setText(n == 0 ? "Ninguna" : n + (n == 1 ? " número" : " números"));
            sv.setTextColor(getColor(R.color.ink_2));
        }
        if (rowPlanned != null) {
            int np = QuickActions.pending(this).size();
            TextView pv = rowPlanned.findViewById(R.id.value);
            pv.setText(np == 0 ? "Ninguna" : String.valueOf(np));
            pv.setTextColor(getColor(R.color.ink_2));
        }
        if (rowTheme != null) {
            TextView tv = rowTheme.findViewById(R.id.value);
            tv.setText(Ui.THEME_SHORT[Ui.theme(this)]);
            tv.setTextColor(getColor(R.color.ink_2));
        }

        status(rowBattery, Util.batteryUnrestricted(this) ? "Sin restricciones" : "Ajustar", Util.batteryUnrestricted(this));
        toggle(rowIncoming, Util.recordIncoming(this));
        toggle(rowOutgoing, Util.recordOutgoing(this));

        File[] files = Util.dir(this).listFiles();
        long total = 0;
        int count = 0;
        if (files != null) for (File f : files) if (f.getName().endsWith(".m4a") || f.getName().endsWith(".wav")) { total += f.length(); count++; }
        ((TextView) rowStorage.findViewById(R.id.value)).setText(count + (count == 1 ? " archivo" : " archivos") + " · " + Util.size(total));
        ((TextView) rowStorage.findViewById(R.id.value)).setTextColor(getColor(R.color.ink_2));
        refreshAutoDel();
    }

    // ------------------------------------------------------------------

    private abstract static class Click { abstract void on(); }

    // ---------------------------------------------------------------- Borrado automático de audios

    private View rowAutoDel;
    private static final int[] KEEP_DAYS = {0, 7, 15, 30, 60, 90, 180, 365, -1};
    private static final String[] KEEP_LABELS = {"Nunca", "Con más de 7 días", "Con más de 15 días",
            "Con más de 30 días", "Con más de 60 días", "Con más de 90 días", "Con más de 6 meses",
            "Con más de 1 año", "Otro número de días…"};

    private static String keepShort(int d) {
        if (d <= 0) return "Nunca";
        if (d == 180) return "6 meses";
        if (d == 365) return "1 año";
        return d + (d == 1 ? " día" : " días");
    }

    private void refreshAutoDel() {
        if (rowAutoDel == null) return;
        TextView v = rowAutoDel.findViewById(R.id.value);
        int d = Cleanup.days(this);
        v.setText(keepShort(d));
        v.setTextColor(getColor(d > 0 ? R.color.accent : R.color.ink_2));
    }

    private void chooseKeepDays() {
        int cur = Cleanup.days(this);
        int sel = KEEP_DAYS.length - 1;
        for (int i = 0; i < KEEP_DAYS.length - 1; i++) if (KEEP_DAYS[i] == cur) sel = i;
        new AlertDialog.Builder(this)
                .setTitle("Borrar los audios")
                .setSingleChoiceItems(KEEP_LABELS, sel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        if (KEEP_DAYS[w] < 0) askCustomDays();
                        else applyKeepDays(KEEP_DAYS[w]);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void askCustomDays() {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setHint("Número de días");
        in.setSingleLine(true);
        in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int cur = Cleanup.days(this);
        if (cur > 0) in.setText(String.valueOf(cur));
        in.setTextColor(getColor(R.color.ink));
        in.setHintTextColor(getColor(R.color.ink_3));
        in.setPadding(dp(20), dp(8), dp(20), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("Borrar audios con más de…")
                .setMessage("Escribe los días que quieres guardar cada audio (de 1 a 3650).")
                .setView(in)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int n;
                        try { n = Integer.parseInt(in.getText().toString().trim()); } catch (Exception e) { n = 0; }
                        if (n < 1 || n > 3650) {
                            Toast.makeText(SettingsActivity.this, "Escribe un número entre 1 y 3650", Toast.LENGTH_LONG).show();
                            return;
                        }
                        applyKeepDays(n);
                    }
                })
                .show();
    }

    private void applyKeepDays(final int days) {
        if (days <= 0) {
            Cleanup.setDays(this, 0);
            refresh();
            Toast.makeText(this, "Los audios ya no se borrarán solos", Toast.LENGTH_SHORT).show();
            return;
        }
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                final int n = Cleanup.countEligible(app, days);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (n == 0) {
                            Cleanup.setDays(SettingsActivity.this, days);
                            refresh();
                            Toast.makeText(SettingsActivity.this, "Activado: se borrarán los audios con más de "
                                    + keepShort(days), Toast.LENGTH_LONG).show();
                            return;
                        }
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("Borrar " + n + (n == 1 ? " audio" : " audios"))
                                .setMessage("Ahora mismo hay " + n + (n == 1 ? " audio" : " audios") + " de llamadas de hace más de "
                                        + keepShort(days) + ", que se borrarán al activarlo. Después se irán borrando solos cada día.\n\n"
                                        + "Se conservan las transcripciones, los resúmenes, las tareas y las grabaciones favoritas. "
                                        + "Los audios borrados no se pueden recuperar.")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Activar y borrar", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) { runCleanupNow(days); }
                                })
                                .show();
                    }
                });
            }
        }).start();
    }

    private void runCleanupNow(int days) {
        Cleanup.setDays(this, days);
        refresh();
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                final int n = Cleanup.run(app);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isFinishing()) refresh();
                        Toast.makeText(app, n == 1 ? "1 audio borrado" : n + " audios borrados", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void section(String title) {
        TextView t = (TextView) LayoutInflater.from(this).inflate(R.layout.row_header, content, false);
        t.setText(title);
        t.setPadding(dp(26), dp(22), dp(26), dp(8));
        content.addView(t);
        group = Ui.card(this);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.setMarginStart(dp(16));
        l.setMarginEnd(dp(16));
        content.addView(group, l);
    }

    private View row(String title, String sub, final Click click) {
        View v = LayoutInflater.from(this).inflate(R.layout.row_setting, content, false);
        ((TextView) v.findViewById(R.id.title)).setText(title);
        TextView s = v.findViewById(R.id.sub);
        if (TextUtils.isEmpty(sub)) s.setVisibility(View.GONE);
        else s.setText(sub);
        if (click != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { click.on(); }
            });
        } else {
            v.setBackground(null);
        }
        if (group == null) section("");
        if (group.getChildCount() > 0) group.addView(Ui.separator(this, 18));
        group.addView(v);
        return v;
    }

    private void status(View row, String label, boolean ok) {
        TextView t = row.findViewById(R.id.value);
        t.setText(label);
        t.setTextColor(getColor(ok ? R.color.ok : R.color.warn));
    }

    private void toggle(View row, boolean on) {
        Switch sw = row.findViewById(R.id.sw);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(on);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(getColor(on ? R.color.on_accent : R.color.thumb_off)));
        sw.setTrackTintList(android.content.res.ColorStateList.valueOf(getColor(on ? R.color.accent : R.color.track)));
    }

    private void flip(String key) {
        boolean v = Util.prefs(this).getBoolean(key, true);
        Util.prefs(this).edit().putBoolean(key, !v).apply();
        refresh();
    }

    // ---------------------------------------------------------------- Modo nativo (ADB propio)

    private void buildNativeSection() {
        section("Grabación de las dos voces");
        rowNvToggle = row("Grabar ambas voces",
                "Tú y la otra persona, sin altavoz, sin apps externas ni root.",
                new Click() {
                    @Override void on() {
                        boolean on = !Util.nativeAdbEnabled(SettingsActivity.this);
                        Util.prefs(SettingsActivity.this).edit().putBoolean("native_adb", on).apply();
                        refresh();
                    }
                });
        rowNvStatus = row("Conexión", "", new Click() {
            @Override void on() {
                new Thread(new Runnable() { public void run() {
                    final boolean ok = NativeAdbManager.ensureConnected(getApplicationContext());
                    runOnUiThread(new Runnable() { public void run() {
                        refresh();
                        if (ok) Toast.makeText(SettingsActivity.this, "Conectado", Toast.LENGTH_SHORT).show();
                        else startActivity(new Intent(SettingsActivity.this, TutorialActivity.class)
                                .putExtra(TutorialActivity.EXTRA_RECONNECT, NativeAdbManager.savedPort(SettingsActivity.this) != 0));
                    } });
                } }).start();
            }
        });
        rowNvPair = row("Tutorial de configuración", "Paso a paso, con los enlaces y comandos del ordenador.", new Click() {
            @Override void on() { startActivity(new Intent(SettingsActivity.this, TutorialActivity.class)); }
        });
        row("Probar grabación", "Prueba de 5 s con corte de conexión a mitad: confirma que graba entero.", new Click() {
            @Override void on() { runDiagnosis(); }
        });
    }

    private void runDiagnosis() {
        final android.app.ProgressDialog pd = android.app.ProgressDialog.show(this, null, "Probando grabación por ADB…", true);
        new Thread(new Runnable() {
            @Override public void run() {
                final String result = NativeAdbManager.diagnose(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try { pd.dismiss(); } catch (Exception ignored) { }
                        final android.widget.EditText tv = new android.widget.EditText(SettingsActivity.this);
                        tv.setText(result);
                        tv.setTextSize(12);
                        tv.setKeyListener(null);
                        tv.setTextColor(getColor(R.color.ink));
                        int pad = dp(16);
                        tv.setPadding(pad, pad, pad, pad);
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("Diagnóstico ADB")
                                .setView(tv)
                                .setPositiveButton("Cerrar", null)
                                .setNeutralButton("Copiar", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("diag", result));
                                        Toast.makeText(SettingsActivity.this, "Copiado", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .show();
                    }
                });
            }
        }).start();
    }

    private void refreshNative() {
        if (rowNvToggle == null) return;
        toggle(rowNvToggle, Util.nativeAdbEnabled(this));
        boolean conn = NativeAdbManager.connected(this);
        boolean configured = NativeAdbManager.savedPort(this) != 0;
        status(rowNvStatus, conn ? "Conectada" : (configured ? "Perdida" : "Sin configurar"), conn);
        TextView ns = rowNvStatus.findViewById(R.id.sub);
        ns.setVisibility(View.VISIBLE);
        ns.setText(conn
                ? "Acta grabará la llamada completa con su ADB propio."
                : configured ? "Se cierra al reiniciar el móvil. Toca para reconectar (1 minuto)."
                : "Toca para configurarlo con el tutorial.");
    }

    // ---------------------------------------------------------------- Resumen de estado

    private void buildHero() {
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(18), dp(12), dp(18));
        android.widget.FrameLayout circle = new android.widget.FrameLayout(this);
        circle.setId(R.id.hero_circle);
        android.widget.ImageView ic = new android.widget.ImageView(this);
        ic.setId(R.id.hero_icon);
        circle.addView(ic, new android.widget.FrameLayout.LayoutParams(dp(22), dp(22), android.view.Gravity.CENTER));
        card.addView(circle, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, R.style.Text, "", 16, true);
        t.setId(R.id.hero_title);
        col.addView(t);
        TextView st = Ui.text(this, R.style.Text_Meta, "");
        st.setId(R.id.hero_sub);
        st.setPadding(0, dp(2), 0, 0);
        col.addView(st);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.setMarginStart(dp(14));
        card.addView(col, cl);
        TextView act = Ui.textButton(this, "");
        act.setId(R.id.hero_action);
        card.addView(act, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.setMargins(dp(16), dp(12), dp(16), 0);
        content.addView(card, l);
        heroCard = card;
    }

    private void refreshHero() {
        if (heroCard == null) return;
        boolean full = Util.fullLine(this), shiz = Util.shizukuEnabled(this);
        boolean nativ = Util.nativeAdbEnabled(this), configured = NativeAdbManager.savedPort(this) != 0;
        boolean conn = NativeAdbManager.connected(this);
        String title, sub, act = null;
        boolean ok;
        View.OnClickListener click = null;
        if (!Util.isDefaultDialer(this) || !Util.coreGranted(this)) {
            ok = false;
            title = "Falta un paso";
            sub = !Util.isDefaultDialer(this) ? "Acta debe ser tu app de teléfono." : "Faltan permisos.";
            act = "Arreglar";
            click = new View.OnClickListener() { @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, TutorialActivity.class)); } };
        } else if (full || (shiz && ShizukuManager.ready()) || (nativ && conn)) {
            ok = true;
            title = "Todo listo";
            sub = "Se graban las dos voces en todas las llamadas.";
        } else if (nativ && configured) {
            ok = false;
            title = "Conexión perdida";
            sub = "Repite el paso del cable (1 minuto).";
            act = "Arreglar";
            click = new View.OnClickListener() { @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, TutorialActivity.class).putExtra(TutorialActivity.EXTRA_RECONNECT, true)); } };
        } else {
            ok = false;
            title = "Solo se graba tu voz";
            sub = "Configura la grabación de las dos voces.";
            act = "Empezar";
            click = new View.OnClickListener() { @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, TutorialActivity.class)); } };
        }
        heroCard.findViewById(R.id.hero_circle).setBackgroundResource(ok ? R.drawable.bg_circle_oksoft : R.drawable.bg_circle_warnsoft);
        android.widget.ImageView ic = heroCard.findViewById(R.id.hero_icon);
        ic.setImageResource(ok ? R.drawable.ic_check : R.drawable.ic_alert);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(ok ? R.color.ok : R.color.warn)));
        ((TextView) heroCard.findViewById(R.id.hero_title)).setText(title);
        ((TextView) heroCard.findViewById(R.id.hero_sub)).setText(sub);
        TextView a = heroCard.findViewById(R.id.hero_action);
        a.setText(act == null ? "" : act);
        a.setVisibility(act == null ? View.GONE : View.VISIBLE);
        a.setOnClickListener(click);
    }

    // ---------------------------------------------------------------- Doble SIM

    private View rowSims;

    private void buildSimSection() {
        section("Tarjetas SIM");
        rowSims = row("Tarjeta recordada por número",
                "Acta recuerda con qué SIM llamas a cada número. Mantén pulsado Llamar para usar otra en una llamada.",
                new Click() {
                    @Override void on() {
                        if (SimInfo.rememberedCount(SettingsActivity.this) == 0) return;
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("Olvidar tarjetas")
                                .setMessage("Acta volverá a preguntarte con qué SIM llamar a cada número.")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Olvidar", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        SimInfo.forgetAll(SettingsActivity.this);
                                        refresh();
                                    }
                                }).show();
                    }
                });
    }

    // ---------------------------------------------------------------- Apariencia

    private View rowTheme, rowPlanned;

    private void buildAppearanceSection() {
        section("Apariencia");
        rowTheme = row("Tema", "Claro, oscuro o como esté el móvil.", new Click() {
            @Override void on() {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Tema")
                        .setSingleChoiceItems(Ui.THEME_LABELS, Ui.theme(SettingsActivity.this), new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                d.dismiss();
                                if (w == Ui.theme(SettingsActivity.this)) return;
                                Ui.setTheme(SettingsActivity.this, w);
                                recreate();
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });
    }

    // ---------------------------------------------------------------- Llamadas y spam

    private void buildCallsSection() {
        section("Llamadas y spam");
        rowHideId = row("Ocultar mi número al llamar",
                "Marca cada llamada como oculta (antepone #31#). Solo oculta tu número a quien llamas, no a la operadora.",
                new Click() {
                    @Override void on() {
                        boolean v = !Util.hideCallerId(SettingsActivity.this);
                        Util.prefs(SettingsActivity.this).edit().putBoolean("hide_id", v).apply();
                        refresh();
                    }
                });
        rowSpam = row("Papelera de spam", "Números bloqueados y llamadas rechazadas.", new Click() {
            @Override void on() { startActivity(new Intent(SettingsActivity.this, SpamActivity.class)); }
        });
        rowPlanned = row("Llamadas programadas", "Avisos para llamar a alguien a una hora.", new Click() {
            @Override void on() {
                ScheduleCall.manage(SettingsActivity.this, new Runnable() { @Override public void run() { refresh(); } });
            }
        });
    }

    private void refreshCalls() {
        if (rowHideId == null) return;
        toggle(rowHideId, Util.hideCallerId(this));
        int blocked = Util.spamSet(this).size();
        int rejected = Db.get(this).spamCount();
        TextView v = rowSpam.findViewById(R.id.value);
        v.setText(blocked + (blocked == 1 ? " número" : " números"));
        v.setTextColor(getColor(R.color.ink_2));
        ((TextView) rowSpam.findViewById(R.id.sub)).setText(
                rejected == 0 ? "Marca un número como spam desde Recientes." : rejected + " llamadas rechazadas. Toca para ver la papelera.");
    }

    // ---------------------------------------------------------------- Isla flotante y acciones rápidas

    private void buildQuickSection() {
        section("Isla flotante y acciones rápidas");
        rowIsland = row("Isla flotante", "", new Click() {
            @Override void on() { Island.openSettings(SettingsActivity.this); }
        });
        rowQuickMsg = row("Mensajes rápidos de WhatsApp", "", new Click() {
            @Override void on() { editQuickMessages(); }
        });
        rowExact = row("Recordatorios a la hora exacta", "", new Click() {
            @Override void on() {
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    safe(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                }
            }
        });
    }

    private void refreshQuick() {
        if (rowIsland == null) return;
        TextView isub = rowIsland.findViewById(R.id.sub);
        if (!Island.supported()) {
            isub.setText(Island.systemName() + ": sin isla. Las acciones aparecen en la notificación de la llamada.");
            status(rowIsland, "No disponible", false);
        } else {
            Boolean ok = Island.allowed(this);
            isub.setText("Detectada: " + Island.systemName() + ". Al minimizar la llamada verás sus botones "
                    + "(contestar, rechazar, mensaje o recordar, colgar, silenciar, altavoz).");
            if (ok == null) status(rowIsland, "Detectada", true);
            else status(rowIsland, ok ? "Activada" : "Activar", ok);
        }
        String[] m = QuickActions.messages(this);
        ((TextView) rowQuickMsg.findViewById(R.id.sub)).setText("«" + m[0] + "» · «" + m[1] + "» · «" + m[2] + "»");
        status(rowQuickMsg, "Editar", true);
        boolean exact = QuickActions.exactAlarmsAllowed(this);
        ((TextView) rowExact.findViewById(R.id.sub)).setText(exact
                ? "Las llamadas pospuestas te avisan justo a la hora elegida."
                : "Sin este permiso el aviso puede llegar unos minutos tarde. Toca para permitirlo.");
        status(rowExact, exact ? "Activado" : "Permitir", exact);
    }

    private void editQuickMessages() {
        final String[] cur = QuickActions.messages(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final android.widget.EditText[] in = new android.widget.EditText[3];
        for (int i = 0; i < 3; i++) {
            in[i] = new android.widget.EditText(this);
            in[i].setText(cur[i]);
            in[i].setHint(QuickActions.DEFAULT_MESSAGES[i]);
            in[i].setTextColor(getColor(R.color.ink));
            in[i].setHintTextColor(getColor(R.color.ink_3));
            in[i].setSingleLine(true);
            box.addView(in[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Mensajes rápidos")
                .setMessage("Se envían por WhatsApp a quien te llama, sin descolgar.")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Restablecer", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        QuickActions.saveMessages(SettingsActivity.this, QuickActions.DEFAULT_MESSAGES);
                        refresh();
                    }
                })
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String[] out = new String[3];
                        for (int i = 0; i < 3; i++) out[i] = in[i].getText().toString();
                        QuickActions.saveMessages(SettingsActivity.this, out);
                        refresh();
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- Resumen con IA

    private void buildAiSection() {
        section("Análisis con IA");
        rowAiKey = row("Clave de Groq", "Gratuita en console.groq.com. Contexto, resumen y tareas de cada llamada.", new Click() {
            @Override void on() { editAiKey(); }
        });
        android.widget.ImageView help = new android.widget.ImageView(this);
        help.setImageResource(R.drawable.ic_help);
        help.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        help.setBackgroundResource(R.drawable.bg_icon_btn);
        help.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        help.setContentDescription("Cómo conseguir la clave de Groq");
        help.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { groqHelp(); }
        });
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(40), dp(40));
        hl.setMarginStart(dp(4));
        ((LinearLayout) rowAiKey).addView(help, 1, hl);
        rowAiAuto = row("Análisis automático al colgar",
                "Las tareas aparecen solas tras cada llamada, sin pulsar nada. Necesita la clave de Groq.", new Click() {
                    @Override void on() { flip("ai_auto"); }
                });
        rowAiCtx = row("Contexto al recibir llamada",
                "La pantalla de llamada muestra de qué hablasteis la última vez con esa persona.", new Click() {
                    @Override void on() { flip("ai_context"); }
                });
        rowOwner = row("Tu nombre",
                "Opcional. Ayuda a la IA a distinguir lo que dices tú de lo que dice la otra persona.", new Click() {
                    @Override void on() { editOwnerName(); }
                });
        rowSplit = row("Separar las voces",
                "Graba aparte el nivel de tu micrófono para saber quién habla en cada frase. Si tu móvil no lo permite, la IA lo deduce por el contenido.", new Click() {
                    @Override void on() {
                        SpeakerSplit.setEnabled(SettingsActivity.this, !SpeakerSplit.enabled(SettingsActivity.this));
                        refresh();
                    }
                });
    }

    private void editOwnerName() {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setHint("Tu nombre de pila");
        in.setSingleLine(true);
        in.setText(SpeakerSplit.ownerName(this));
        in.setTextColor(getColor(R.color.ink));
        in.setHintTextColor(getColor(R.color.ink_3));
        int pad = dp(20);
        in.setPadding(pad, dp(8), pad, dp(8));
        new AlertDialog.Builder(this)
                .setTitle("Tu nombre")
                .setMessage("Solo se guarda en este móvil y se incluye en el análisis para que la IA sepa quién eres tú en la conversación.")
                .setView(in)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Util.prefs(SettingsActivity.this).edit().putString("owner_name", in.getText().toString().trim()).apply();
                        refresh();
                    }
                })
                .show();
    }

    private void refreshAi() {
        if (rowAiKey == null) return;
        boolean has = Summarizer.hasKey(this);
        status(rowAiKey, has ? "Configurada" : "Añadir", has);
        toggle(rowAiAuto, AnalysisJob.enabled(this));
        toggle(rowAiCtx, CallContext.enabled(this));
        String on = SpeakerSplit.ownerName(this);
        status(rowOwner, on.isEmpty() ? "Añadir" : on, true);
        if (on.isEmpty()) ((TextView) rowOwner.findViewById(R.id.value)).setTextColor(getColor(R.color.ink_3));
        toggle(rowSplit, SpeakerSplit.enabled(this));
        TextView ss = rowSplit.findViewById(R.id.sub);
        ss.setText(SpeakerSplit.bad(this)
                ? "Tu móvil silenciaba la grabación al separar voces, así que se desactivó. La IA deduce quién habla por el contenido."
                : "Graba aparte el nivel de tu micrófono para saber quién habla en cada frase. Si tu móvil no lo permite, la IA lo deduce por el contenido.");
    }

    private void editAiKey() {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setHint("Pega aquí tu clave (gsk_...)");
        in.setText(Util.prefs(this).getString("ai_key", ""));
        in.setTextColor(getColor(R.color.ink));
        in.setHintTextColor(getColor(R.color.ink_3));
        int pad = dp(20);
        in.setPadding(pad, dp(8), pad, dp(8));
        new AlertDialog.Builder(this)
                .setTitle("Clave de Groq (IA)")
                .setMessage("Pega aquí tu clave. Empieza por gsk_.")
                .setView(in)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("¿Cómo la consigo?", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { groqHelp(); }
                })
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String k = in.getText().toString().trim();
                        Util.prefs(SettingsActivity.this).edit().putString("ai_key", k).apply();
                        if (k.isEmpty()) AiConsent.revoke(SettingsActivity.this);
                        else AiConsent.require(SettingsActivity.this, null);
                        refresh();
                    }
                })
                .show();
    }

    /** Pequeño tutorial: cómo conseguir y pegar la clave gratuita de Groq. */
    private void groqHelp() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(4), dp(24), dp(4));
        TextView intro = Ui.text(this, R.style.Text_Meta, "Es gratis y se hace en 2 minutos. Mejor desde el ordenador o el navegador del móvil.");
        intro.setTextSize(14.5f);
        intro.setLineSpacing(dp(3), 1f);
        box.addView(intro);
        String[][] steps = {
                {"Entra en console.groq.com", "Crea una cuenta gratis (con Google o con tu correo)."},
                {"Abre «API Keys»", "En el menú, pulsa «API Keys» y luego «Create API Key»."},
                {"Ponle un nombre y créala", "Por ejemplo «Acta». Pulsa «Submit»."},
                {"Copia la clave", "Empieza por gsk_. Cópiala ya: Groq solo la enseña una vez."},
                {"Pégala en Acta", "Ajustes › Clave de Groq › pega y pulsa Guardar."}};
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(14), 0, 0);
            TextView n = Ui.text(this, R.style.Text, String.valueOf(i + 1), 13, true);
            n.setTextColor(getColor(R.color.on_accent));
            n.setGravity(android.view.Gravity.CENTER);
            n.setBackgroundResource(R.drawable.bg_circle_accent);
            row.addView(n, new LinearLayout.LayoutParams(dp(24), dp(24)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(Ui.text(this, R.style.Text, steps[i][0], 15, true));
            TextView d = Ui.text(this, R.style.Text_Meta, steps[i][1]);
            d.setTextSize(14);
            d.setLineSpacing(dp(2), 1f);
            d.setPadding(0, dp(2), 0, 0);
            col.addView(d);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.setMarginStart(dp(12));
            row.addView(col, cl);
            box.addView(row);
        }
        TextView note = Ui.text(this, R.style.Text_Meta, "Al analizar, el audio de esa llamada se envía a Groq. El resto de tus datos siguen en el móvil.");
        note.setTextSize(13);
        note.setLineSpacing(dp(2), 1f);
        note.setPadding(0, dp(16), 0, 0);
        box.addView(note);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("Cómo conseguir la clave")
                .setView(sv)
                .setNegativeButton("Cerrar", null)
                .setNeutralButton("Abrir Groq", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) {
                        safe(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")));
                    }
                })
                .setPositiveButton("Pegar clave", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) { editAiKey(); }
                })
                .show();
    }

    // ---------------------------------------------------------------- Privacidad

    private void buildPrivacySection() {
        section("Privacidad");
        rowLock = row("Bloquear grabaciones con huella o PIN",
                "Pide tu huella o el PIN del móvil para abrir las grabaciones. Sin capturas de pantalla.", new Click() {
                    @Override void on() { toggleLock(); }
                });
        rowCrypto = row("Cifrado de grabaciones", "", new Click() {
            @Override void on() { cryptoInfo(); }
        });
    }

    private void refreshPrivacy() {
        if (rowLock == null) return;
        toggle(rowLock, AppLock.enabled(this));
        int[] st = Crypto.stats(this);
        ((TextView) rowCrypto.findViewById(R.id.sub)).setText(
                "Los audios se guardan cifrados (AES-256) con una clave del almacén seguro del móvil. "
                        + "Fuera de Acta no se pueden abrir.");
        boolean all = st[0] == st[1];
        status(rowCrypto, st[1] == 0 ? "Activado" : (all ? "Activado · " + st[0] : st[0] + " de " + st[1]), all);
    }

    private void toggleLock() {
        if (!AppLock.available(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Falta un bloqueo de pantalla")
                    .setMessage("Para proteger las grabaciones con huella o PIN, primero pon un bloqueo de pantalla "
                            + "(PIN, patrón o contraseña) en los ajustes del móvil.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Abrir ajustes", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            safe(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        }
                    })
                    .show();
            return;
        }
        final boolean turnOn = !AppLock.enabled(this);
        AppLock.authenticate(this, turnOn ? "Activar el bloqueo" : "Desactivar el bloqueo", new Runnable() {
            @Override public void run() {
                AppLock.setEnabled(SettingsActivity.this, turnOn);
                refresh();
            }
        }, null);
    }

    private void cryptoInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Cifrado de grabaciones")
                .setMessage("Cada audio se cifra con AES-256-GCM nada más terminar la llamada. La clave se genera en "
                        + "este móvil y queda protegida por su almacén seguro (Android Keystore): aunque alguien copie "
                        + "los archivos al ordenador, no podrá escucharlos.\n\nActa los descifra solo en memoria para "
                        + "reproducirlos. Al compartir una grabación, la otra app la recibe descifrada.")
                .setPositiveButton("Entendido", null)
                .setNeutralButton("Cifrar pendientes", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        new Thread(new Runnable() {
                            @Override public void run() {
                                Crypto.encryptAll(getApplicationContext());
                                runOnUiThread(new Runnable() { @Override public void run() { refresh(); } });
                            }
                        }).start();
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- Shizuku

    private void buildShizukuSection() {
        section("Línea completa sin root (Shizuku)");
        rowShzToggle = row("Grabar la llamada completa con Shizuku",
                "Graba a las dos personas sin root y sin altavoz.",
                new Click() {
                    @Override void on() { toggleShizuku(); }
                });
        rowShzHelp = row("Asistente de configuración",
                "Paso a paso, con detección automática. Te lleva directo a cada pantalla.", new Click() {
                    @Override void on() { startActivity(new Intent(SettingsActivity.this, SetupActivity.class)); }
                });
        rowShzStatus = row("Estado de Shizuku", "", new Click() {
            @Override void on() { shizukuStatusAction(); }
        });
    }

    private void refreshShizuku() {
        if (rowShzToggle == null) return;
        toggle(rowShzToggle, Util.shizukuEnabled(this));
        String label;
        boolean ok = false;
        if (!ShizukuManager.installed(this)) label = "Instalar";
        else if (!ShizukuManager.running()) label = "Iniciar";
        else if (!ShizukuManager.granted()) label = "Permitir";
        else if (!ShizukuManager.ready()) label = "Conectando…";
        else { label = "Listo"; ok = true; }
        status(rowShzStatus, label, ok);
        String sub;
        if (!ShizukuManager.installed(this)) sub = "La app Shizuku no está instalada. Toca para instalarla (es gratuita).";
        else if (!ShizukuManager.running()) sub = "Shizuku está instalada pero no en marcha. Toca para abrirla y arrancarla.";
        else if (!ShizukuManager.granted()) sub = "Concede a Acta el permiso de Shizuku. Toca para solicitarlo.";
        else if (!ShizukuManager.ready()) sub = "Enlazando el servicio de grabación…";
        else sub = "Acta grabará la línea completa con Shizuku.";
        ((TextView) rowShzStatus.findViewById(R.id.sub)).setText(sub);
    }

    private void toggleShizuku() {
        boolean on = !Util.shizukuEnabled(this);
        Util.prefs(this).edit().putBoolean("shizuku", on).apply();
        if (on) {
            if (!ShizukuManager.installed(this)) { openShizukuPlayStore(); }
            else if (!ShizukuManager.running()) { launchShizuku(); }
            else if (!ShizukuManager.granted()) { ShizukuManager.requestPermission(); }
            else { ShizukuManager.ensureService(); }
        }
        recreate();
    }

    private void shizukuStatusAction() {
        if (!ShizukuManager.installed(this)) openShizukuPlayStore();
        else if (!ShizukuManager.running()) launchShizuku();
        else if (!ShizukuManager.granted()) ShizukuManager.requestPermission();
        else { ShizukuManager.ensureService(); refresh(); }
    }

    private void openShizukuPlayStore() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + ShizukuManager.PKG)));
        } catch (Exception e) {
            safe(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")));
        }
    }

    private void launchShizuku() {
        Intent i = getPackageManager().getLaunchIntentForPackage(ShizukuManager.PKG);
        if (i != null) safe(i); else openShizukuPlayStore();
    }

    private void explainShizuku() {
        new AlertDialog.Builder(this)
                .setTitle("Preparar Shizuku")
                .setMessage("Shizuku da a Acta los permisos necesarios para grabar la llamada completa, sin root.\n\n"
                        + "1. Instala la app Shizuku desde Play Store (gratuita).\n"
                        + "2. Ábrela y arráncala con «Emparejamiento por depuración inalámbrica»: Shizuku te guía para activar las Opciones de desarrollador y la depuración inalámbrica, y escribir un código. No hace falta ordenador.\n"
                        + "3. Vuelve a Acta y concede el permiso de Shizuku cuando se te pida.\n"
                        + "4. Pon Acta como app de teléfono predeterminada.\n\n"
                        + "Tras reiniciar el móvil, abre Shizuku una vez para volver a arrancarlo.")
                .setPositiveButton("Entendido", null)
                .show();
    }

    // ---------------------------------------------------------------- Xiaomi

    private static final int RC_FOLDER = 21;

    private void buildXiaomiSection() {
        section("Grabación completa en Xiaomi");
        rowXDialer = row("1 · Pantalla de llamada de Xiaomi",
                "Elige «Teléfono» de Xiaomi como app de llamadas predeterminada. Tú sigues marcando y viendo todo desde Acta.",
                new Click() {
                    @Override void on() { safe(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }
                });
        rowXAuto = row("2 · Grabación automática",
                "En los ajustes de llamada de Xiaomi: Grabación de llamadas › Grabar llamadas automáticamente › Todos los números.",
                new Click() {
                    @Override void on() { openXiaomiCallSettings(); }
                });
        rowXAccess = row("3 · Acceso a las grabaciones",
                "Permite a Acta leer los audios para añadirlos con número, fecha, hora y duración.",
                new Click() {
                    @Override void on() {
                        if (!Importer.canRead(SettingsActivity.this))
                            requestPermissions(Util.runtimePermissions(), 2);
                        else openAppInfo();
                    }
                });
        rowXFolder = row("Carpeta de grabaciones",
                "Solo si las grabaciones no aparecen: elige la carpeta donde se guardan (por ejemplo Recordings › JemRec).",
                new Click() {
                    @Override void on() { pickFolder(); }
                });
        rowXScan = row("Buscar grabaciones ahora", "Normalmente se añaden solas unos segundos después de colgar.", new Click() {
            @Override void on() { scanNow(); }
        });
    }

    private void refreshXiaomi() {
        String def = Util.defaultDialerPackage(this);
        boolean xiaomiDefault = "com.android.contacts".equals(def) || "com.android.incallui".equals(def);
        if (Util.usesGoogleDialer(this)) {
            status(rowXDialer, "Teléfono de Google", false);
            ((TextView) rowXDialer.findViewById(R.id.sub)).setText(
                    "La app de Google no deja sus grabaciones accesibles. Si tu móvil tiene «Teléfono» de Xiaomi, elígela como predeterminada.");
        } else {
            status(rowXDialer, xiaomiDefault ? "Activa" : "Cambiar", xiaomiDefault);
        }
        TextView v = rowXAuto.findViewById(R.id.value);
        v.setText("Abrir");
        v.setTextColor(getColor(R.color.accent));
        status(rowXAccess, Importer.canRead(this) ? "Concedido" : "Conceder", Importer.canRead(this));
        Uri f = Importer.folderUri(this);
        TextView fv = rowXFolder.findViewById(R.id.value);
        fv.setText(f != null ? "Elegida" : "Opcional");
        fv.setTextColor(getColor(f != null ? R.color.ok : R.color.ink_3));
    }

    private void openXiaomiCallSettings() {
        Intent i = new Intent(android.telecom.TelecomManager.ACTION_SHOW_CALL_SETTINGS);
        try {
            startActivity(i);
        } catch (Exception e) {
            Intent l = getPackageManager().getLaunchIntentForPackage("com.android.contacts");
            if (l != null) safe(l);
        }
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(
                "content://com.android.externalstorage.documents/document/primary%3AMIUI%2Fsound_recorder%2Fcall_rec"));
        try { startActivityForResult(i, RC_FOLDER); } catch (Exception ignored) { }
    }

    private void scanNow() {
        Toast.makeText(this, "Buscando grabaciones…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final int n = Importer.run(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(SettingsActivity.this,
                                n == 0 ? "No hay grabaciones nuevas" : n == 1 ? "1 grabación añadida" : n + " grabaciones añadidas",
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void safe(Intent i) {
        try { startActivity(i); } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show();
        }
    }

    private void explainSystemInstall() {
        if (Util.fullLine(this)) return;
        new AlertDialog.Builder(this)
                .setTitle("Instalar como app del sistema")
                .setMessage("Android solo permite grabar la voz del interlocutor a las apps del sistema.\n\n"
                        + "1. El teléfono debe tener root con Magisk o KernelSU.\n"
                        + "2. En Magisk › Módulos › Instalar desde almacenamiento, elige Acta-modulo-sistema.zip.\n"
                        + "3. Reinicia el teléfono.\n\n"
                        + "Al volver, esta opción aparecerá como «Activa».")
                .setPositiveButton("Entendido", null)
                .show();
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        if (rc == RC_FOLDER && res == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Util.prefs(this).edit().putString("folder", tree.toString()).apply();
                scanNow();
            } catch (Exception ignored) { }
        }
        refresh();
    }

    private void openAppInfo() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void exportCsv() {
        List<Db.Call> calls = Db.get(this).all();
        if (calls.isEmpty()) {
            Toast.makeText(this, "Todavía no hay llamadas registradas", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = "registro_llamadas.csv";
        File out = new File(Util.dir(this), name);
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8);
            w.write('﻿'); // para que Excel reconozca UTF-8
            w.write("Tipo;Número;Contacto;Fecha;Hora;Duración (s);Duración;Archivo\r\n");
            for (Db.Call c : calls) {
                w.write((c.direction == 1 ? "Entrante" : "Saliente") + ";"
                        + csv(c.number) + ";" + csv(c.name) + ";"
                        + new java.text.SimpleDateFormat("dd/MM/yyyy", Util.ES).format(new java.util.Date(c.startMs)) + ";"
                        + Util.timeFull(c.startMs) + ";"
                        + c.durationSec + ";" + Util.clock(c.durationSec) + ";" + csv(c.file) + "\r\n");
            }
            w.close();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo exportar", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri u = Util.shareUri(name);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM, u)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(android.content.ClipData.newRawUri("", u));
        startActivity(Intent.createChooser(i, "Exportar registro"));
    }

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
