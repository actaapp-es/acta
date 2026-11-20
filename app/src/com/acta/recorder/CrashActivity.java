package com.acta.recorder;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Pantalla de error: si Acta se cierra por un fallo, aquí se ve el motivo exacto para poder
 * copiarlo y enviarlo. El texto se guarda solo en el teléfono; nunca se envía solo.
 * Se ejecuta en un proceso aparte (:crash) para que funcione aunque el principal haya caído.
 */
public class CrashActivity extends Activity {

    static final String EXTRA_TRACE = "trace";

    /** Instala el capturador de errores en el proceso principal. */
    static void install(final Context app) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String trace = "Acta " + version(app) + " · Android " + android.os.Build.VERSION.RELEASE
                            + " (API " + android.os.Build.VERSION.SDK_INT + ") · " + android.os.Build.MODEL
                            + "\nHilo: " + t.getName() + "\n\n" + sw;
                    if (trace.length() > 12000) trace = trace.substring(0, 12000);
                    try {
                        java.io.FileOutputStream o = new java.io.FileOutputStream(new File(app.getFilesDir(), "crash.txt"));
                        o.write(trace.getBytes("UTF-8")); o.close();
                    } catch (Throwable ignored) { }
                    android.content.Intent i = new android.content.Intent(app, CrashActivity.class)
                            .putExtra(EXTRA_TRACE, trace)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    app.startActivity(i);
                } catch (Throwable ignored) { }
                if (prev != null) prev.uncaughtException(t, e);
                else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
            }
        });
    }

    private static String version(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Throwable e) { return "?"; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String t = getIntent().getStringExtra(EXTRA_TRACE);
        if (t == null) {
            try {
                File f = new File(getFilesDir(), "crash.txt");
                byte[] buf = new byte[(int) Math.min(f.length(), 12000)];
                FileInputStream in = new FileInputStream(f); in.read(buf); in.close();
                t = new String(buf, "UTF-8");
            } catch (Throwable e) { t = "(sin datos)"; }
        }
        final String trace = t;
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(pad, pad * 2, pad, pad);
        TextView h = new TextView(this);
        h.setText("Acta ha tenido un error");
        h.setTextSize(22); h.setTextColor(Color.BLACK);
        root.addView(h);
        TextView s = new TextView(this);
        s.setText("Copia este texto y envíalo para que se pueda corregir. Se guarda solo en tu teléfono.");
        s.setTextColor(Color.DKGRAY); s.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(s);
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(trace); tv.setTextSize(11); tv.setTextColor(Color.BLACK);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button copy = new Button(this);
        copy.setText("Copiar error");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("Error de Acta", trace));
                Toast.makeText(CrashActivity.this, "Copiado", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(copy);
        Button close = new Button(this);
        close.setText("Cerrar");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishAndRemoveTask(); }
        });
        root.addView(close);
        setContentView(root);
    }
}
