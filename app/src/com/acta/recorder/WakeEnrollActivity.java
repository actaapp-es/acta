package com.acta.recorder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Graba tu voz diciendo «Hola Acta» unas veces: es lo que el móvil usa para reconocer la frase, sin red. */
public class WakeEnrollActivity extends Activity {

    private static final int NEEDED = 5;
    private final List<short[]> samples = new ArrayList<>();
    private TextView status, count;
    private TextView record;
    private volatile boolean recording;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        setContentView(root);

        TextView title = new TextView(this, null, 0, R.style.Text_Title);
        title.setText("Graba tu voz");
        root.addView(title);
        TextView info = Ui.text(this, R.style.Text_Meta,
                "Di «Hola Acta» con tu voz de siempre cada vez que pulses el botón. Hacen falta " + NEEDED
                        + " grabaciones, mejor en un sitio tranquilo. Las grabaciones se quedan en tu móvil y solo sirven para reconocer la frase.");
        info.setTextSize(15);
        info.setLineSpacing(dp(2), 1);
        info.setPadding(0, dp(10), 0, dp(24));
        root.addView(info);

        count = Ui.text(this, R.style.Text, "", 44, true);
        count.setGravity(Gravity.CENTER);
        root.addView(count, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        status = Ui.text(this, R.style.Text_Meta, "");
        status.setGravity(Gravity.CENTER);
        status.setTextSize(15);
        status.setPadding(0, dp(6), 0, dp(24));
        root.addView(status);

        record = Ui.textButton(this, "Grabar");
        record.setTextSize(18);
        record.setGravity(Gravity.CENTER);
        record.setBackgroundResource(R.drawable.bg_circle_ok);
        record.setTextColor(0xFFFFFFFF);
        record.setPadding(dp(24), dp(16), dp(24), dp(16));
        record.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { go(); } });
        root.addView(record, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        if (WakeWord.enrolled(this)) {
            TextView del = Ui.textButton(this, "Borrar mi voz guardada");
            del.setTextColor(getColor(R.color.danger));
            del.setGravity(Gravity.CENTER);
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    WakeWord.clear(WakeEnrollActivity.this);
                    VoiceService.setEnabled(WakeEnrollActivity.this, false);
                    Toast.makeText(WakeEnrollActivity.this, "Voz borrada", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(24);
            root.addView(del, lp);
        }
        render("Pulsa el botón y di «Hola Acta».");
    }

    private void render(String msg) {
        count.setText(samples.size() + " / " + NEEDED);
        status.setText(msg);
        record.setText(recording ? "Escuchando…" : (samples.size() == 0 ? "Grabar" : "Grabar otra"));
        record.setEnabled(!recording);
    }

    private void go() {
        if (!Util.has(this, Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 93);
            return;
        }
        recording = true;
        render("Habla ahora…");
        new Thread(new Runnable() {
            @Override public void run() {
                final short[] s = sample();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        recording = false;
                        if (s == null) { render("No te he oído bien. Dilo otra vez, un poco más alto y con calma."); return; }
                        samples.add(s);
                        if (samples.size() >= NEEDED) { finish(true); return; }
                        render("Bien. Otra vez.");
                    }
                });
            }
        }).start();
    }

    private void finish(boolean ok) {
        if (ok && WakeWord.save(this, samples)) {
            Toast.makeText(this, "Voz guardada. Ya puedes activar «Hola Acta».", Toast.LENGTH_LONG).show();
            finish();
        } else {
            samples.clear();
            render("Las grabaciones no se parecían lo bastante. Empieza otra vez diciendo siempre lo mismo.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) { if (Util.has(this, Manifest.permission.RECORD_AUDIO)) go(); }

    /** Graba una vez la frase: espera voz (hasta 5 s) y corta tras un silencio. Devuelve solo la parte con voz. */
    private short[] sample() {
        int min = AudioRecord.getMinBufferSize(WakeWord.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord ar = null;
        try {
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, WakeWord.RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, WakeWord.RATE * 2));
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) return null;
            ar.startRecording();
            int hop = WakeWord.HOP;
            short[] buf = new short[hop];
            short[] all = new short[WakeWord.RATE * 7];
            int len = 0;
            double floor = -1;
            int first = -1, last = -1, silence = 0;
            long t0 = System.currentTimeMillis();
            while (System.currentTimeMillis() - t0 < 6500 && len + hop <= all.length) {
                int n = ar.read(buf, 0, hop);
                if (n < hop) { if (n < 0) break; continue; }
                System.arraycopy(buf, 0, all, len, hop);
                double r = WakeWord.rms(buf, 0, hop);
                if (floor < 0) floor = r; else if (first < 0) floor = floor * 0.9 + r * 0.1;
                boolean voice = r > Math.max(550, floor * 2.8);
                if (voice) { if (first < 0) first = len; last = len + hop; silence = 0; }
                else if (first >= 0) { silence++; if (silence >= 40) break; }
                len += hop;
            }
            if (first < 0) return null;
            int from = Math.max(0, first - hop * 5), to = Math.min(len, last + hop * 5);
            int size = to - from;
            if (size < WakeWord.MIN_SAMPLES_TEMPLATE || size > WakeWord.MAX_SAMPLES_TEMPLATE) return null;
            return java.util.Arrays.copyOfRange(all, from, to);
        } catch (Throwable t) {
            return null;
        } finally {
            if (ar != null) { try { ar.stop(); } catch (Throwable ignored) { } try { ar.release(); } catch (Throwable ignored) { } }
        }
    }
}
