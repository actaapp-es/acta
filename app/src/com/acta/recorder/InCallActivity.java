package com.acta.recorder;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class InCallActivity extends Activity implements CallManager.Listener {

    static final String ACTION_ANSWER = "com.acta.recorder.ANSWER";

    private TextView state, who, sub, timer, dtmfDigits, contextView;
    private String ctxKey, ctxText;
    private View rec, controls, ringing, end, dtmf, quick;
    private ImageButton mute, speaker, hold, keypad;
    private final Handler h = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock proximity;
    private boolean dtmfOpen;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Call c = CallManager.primary();
            if (c != null && c.getState() == Call.STATE_ACTIVE) {
                long connect = c.getDetails().getConnectTimeMillis();
                if (connect > 0) timer.setText(Util.clock((int) ((System.currentTimeMillis() - connect) / 1000)));
            }
            h.postDelayed(this, 500);
        }
    };

    private android.animation.AnimatorSet ringAnim;
    private android.animation.ObjectAnimator blinkAnim;
    private String avatarKey;

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_incall);

        state = findViewById(R.id.state);
        who = findViewById(R.id.who);
        sub = findViewById(R.id.sub);
        timer = findViewById(R.id.timer);
        contextView = findViewById(R.id.context);
        rec = findViewById(R.id.rec);
        controls = findViewById(R.id.controls);
        ringing = findViewById(R.id.ringing);
        quick = findViewById(R.id.quick);
        findViewById(R.id.btn_msg).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openQuick("msg"); }
        });
        findViewById(R.id.btn_later).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openQuick("later"); }
        });
        end = findViewById(R.id.btn_end);
        findViewById(R.id.btn_mark).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (CallManager.service == null) return;
                int sec = CallManager.service.markNow();
                if (sec < 0) return;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                android.widget.Toast.makeText(InCallActivity.this, "Momento marcado en el " + Util.clock(sec),
                        android.widget.Toast.LENGTH_SHORT).show();
                render();
            }
        });
        findViewById(R.id.btn_ctx).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showContext(); }
        });
        dtmf = findViewById(R.id.dtmf);
        dtmfDigits = findViewById(R.id.dtmf_digits);
        mute = findViewById(R.id.btn_mute);
        speaker = findViewById(R.id.btn_speaker);
        hold = findViewById(R.id.btn_hold);
        keypad = findViewById(R.id.btn_keypad);

        findViewById(R.id.btn_answer).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { answer(); }
        });
        findViewById(R.id.btn_reject).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Call c = CallManager.primary();
                if (c != null) c.reject(false, null);
            }
        });
        end.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Call c = CallManager.primary();
                if (c != null) c.disconnect();
            }
        });
        mute.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (CallManager.service == null) return;
                boolean muted = CallManager.audio != null && CallManager.audio.isMuted();
                CallManager.service.setMuted(!muted);
            }
        });
        speaker.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                AudioRoute.onButton(InCallActivity.this, CallManager.service, CallManager.audio);
            }
        });
        hold.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Call c = CallManager.primary();
                if (c == null) return;
                if (c.getState() == Call.STATE_HOLDING) c.unhold(); else c.hold();
            }
        });
        keypad.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dtmfOpen = !dtmfOpen;
                render();
            }
        });

        Keypad.build((LinearLayout) findViewById(R.id.dtmf_grid), true, new Keypad.Listener() {
            @Override public void onKey(char c) {
                Call call = CallManager.primary();
                if (call == null) return;
                call.playDtmfTone(c);
                call.stopDtmfTone();
                dtmfDigits.append(String.valueOf(c));
            }
            @Override public void onLongKey(char c) { onKey(c); }
        });

        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "acta:proximity");
        }
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i != null && ACTION_ANSWER.equals(i.getAction())) answer();
    }

    private void openQuick(String mode) {
        Call c = CallManager.primary();
        if (c == null) return;
        startActivity(QuickActionsActivity.intent(this, CallManager.number(c), mode));
    }

    /** Botón «Contexto»: pequeño resumen de quién es esta persona según las llamadas anteriores. */
    private void showContext() {
        Call c = CallManager.primary();
        if (c == null) return;
        String number = CallManager.number(c);
        String name = Util.contactName(this, number);
        android.app.KeyguardManager km = getSystemService(android.app.KeyguardManager.class);
        boolean redact = AppLock.enabled(this) && km != null && km.isKeyguardLocked();
        String text = CallContext.detail(this, number, redact);
        if (text == null) text = "Es la primera vez que hablas con esta persona, o aún no tienes llamadas guardadas con ella.";
        new android.app.AlertDialog.Builder(this)
                .setTitle(!TextUtils.isEmpty(name) ? name : Util.number(this, number))
                .setMessage(text)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void answer() {
        Call c = CallManager.primary();
        if (c != null && c.getState() == Call.STATE_RINGING) c.answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CallManager.addListener(this);
        if (CallManager.service != null) CallManager.service.ensureMicForeground();
        h.post(tick);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CallManager.removeListener(this);
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        if (ringAnim != null) ringAnim.cancel();
        if (blinkAnim != null) blinkAnim.cancel();
        if (proximity != null && proximity.isHeld()) proximity.release();
        super.onDestroy();
    }

    @Override
    public void onCallsChanged() {
        runOnUiThread(new Runnable() {
            @Override public void run() { render(); }
        });
    }

    private void render() {
        Call c = CallManager.primary();
        if (c == null) {
            if (proximity != null && proximity.isHeld()) proximity.release();
            finishAndRemoveTask();
            return;
        }
        int s = c.getState();
        String number = CallManager.number(c);
        String name = Util.contactName(this, number);
        String formatted = Util.number(this, number);
        who.setText(!TextUtils.isEmpty(name) ? name : formatted);
        if (!TextUtils.isEmpty(name)) {
            sub.setText(formatted);
            sub.setVisibility(View.VISIBLE);
        } else {
            NumberInfo info = NumberInfo.of(number);
            String extra = info != null ? (info.lineType
                    + (TextUtils.isEmpty(info.origin) || info.origin.equals("España") ? "" : "  ·  " + info.origin)) : "";
            sub.setText(extra);
            sub.setVisibility(TextUtils.isEmpty(extra) ? View.GONE : View.VISIBLE);
        }

        // Contexto: de qué hablasteis la última vez (si está activado en Ajustes).
        android.app.KeyguardManager km = getSystemService(android.app.KeyguardManager.class);
        boolean redact = AppLock.enabled(this) && km != null && km.isKeyguardLocked();
        String key = number + "|" + redact;
        if (!key.equals(ctxKey)) { ctxKey = key; ctxText = CallContext.line(this, number, redact); }
        contextView.setText(ctxText);
        findViewById(R.id.context_box).setVisibility(TextUtils.isEmpty(ctxText) || dtmfOpen ? View.GONE : View.VISIBLE);
        String ak = name + "|" + number;
        if (!ak.equals(avatarKey)) {
            avatarKey = ak;
            Ui.avatar((TextView) findViewById(R.id.avatar), (android.widget.ImageView) findViewById(R.id.avatar_icon), name);
        }

        switch (s) {
            case Call.STATE_RINGING: state.setText("Llamada entrante"); break;
            case Call.STATE_ACTIVE: state.setText("En llamada"); break;
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING: state.setText("Llamando…"); break;
            case Call.STATE_SELECT_PHONE_ACCOUNT: state.setText("Elige la tarjeta"); break;
            case Call.STATE_HOLDING: state.setText("En espera"); break;
            case Call.STATE_DISCONNECTED:
            case Call.STATE_DISCONNECTING: state.setText("Llamada finalizada"); break;
            default: state.setText(CallManager.incoming(c) ? "Llamada entrante" : "Llamando…");
        }
        if (s != Call.STATE_ACTIVE) timer.setText(s == Call.STATE_HOLDING ? timer.getText() : "");

        boolean isRinging = s == Call.STATE_RINGING;
        boolean selecting = s == Call.STATE_SELECT_PHONE_ACCOUNT;
        renderSimPanel(c, selecting, number);
        if (SimInfo.dual(this) && !selecting) {
            SimInfo.Sim sim = SimInfo.forHandle(this, c.getDetails().getAccountHandle());
            if (sim != null) {
                String cur = sub.getVisibility() == View.VISIBLE ? sub.getText().toString() : "";
                sub.setText(cur.isEmpty() ? sim.label() : cur + " · " + sim.shortLabel());
                sub.setVisibility(View.VISIBLE);
            }
        }
        ringing.setVisibility(isRinging ? View.VISIBLE : View.GONE);
        quick.setVisibility(isRinging ? View.VISIBLE : View.GONE);
        end.setVisibility(isRinging ? View.GONE : View.VISIBLE);
        controls.setVisibility(isRinging || selecting ? View.GONE : View.VISIBLE);
        dtmf.setVisibility(!isRinging && dtmfOpen ? View.VISIBLE : View.GONE);

        boolean recording = CallManager.recordingCall == c;
        rec.setVisibility(recording ? View.VISIBLE : View.INVISIBLE);
        TextView recText = findViewById(R.id.rec_text);
        recText.setText("Grabando");
        recText.setText(NativeAdbManager.isBusy() ? "Grabando · ambas voces" : "Grabando");
        animate(isRinging, recording);
        TextView mark = findViewById(R.id.btn_mark);
        mark.setVisibility(recording && !isRinging && !dtmfOpen && !selecting ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_ctx).setVisibility(
                (s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING) && !dtmfOpen && !selecting ? View.VISIBLE : View.GONE);
        int nm = CallManager.service != null ? CallManager.service.markCount() : 0;
        mark.setText(nm == 0 ? "Marcar momento" : "Marcar momento · " + nm);

        CallAudioState a = CallManager.audio;
        boolean muted = a != null && a.isMuted();
        boolean spk = AudioRoute.highlighted(a); // altavoz o Bluetooth: sin sensor de proximidad
        toggle(mute, muted);
        toggle(speaker, AudioRoute.highlighted(a));
        speaker.setImageResource(AudioRoute.icon(a));
        String route = AudioRoute.label(this, a);
        ((TextView) findViewById(R.id.speaker_label)).setText(route);
        speaker.setContentDescription(route);
        toggle(hold, s == Call.STATE_HOLDING);
        toggle(keypad, dtmfOpen);
        hold.setEnabled(c.getDetails().can(Call.Details.CAPABILITY_HOLD) || s == Call.STATE_HOLDING);
        hold.setAlpha(hold.isEnabled() ? 1f : 0.35f);

        if (proximity != null) {
            boolean want = (s == Call.STATE_ACTIVE || s == Call.STATE_DIALING) && !spk;
            if (want && !proximity.isHeld()) proximity.acquire(4 * 60 * 60 * 1000L);
            else if (!want && proximity.isHeld()) proximity.release();
        }
    }

    private boolean simPanelBuilt;

    /** Doble SIM sin tarjeta recordada: botones para elegir con cuál sale la llamada. */
    private void renderSimPanel(final Call c, boolean show, final String number) {
        LinearLayout panel = findViewById(R.id.sim_panel);
        if (!show) { panel.setVisibility(View.GONE); simPanelBuilt = false; return; }
        panel.setVisibility(View.VISIBLE);
        if (simPanelBuilt) return;
        simPanelBuilt = true;
        panel.removeAllViews();
        for (final SimInfo.Sim sim : SimInfo.list(this)) {
            TextView b = Ui.button(this, "Llamar con " + sim.label(), false);
            Ui.icon(b, R.drawable.ic_call_small, R.color.accent, 18);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SimInfo.remember(InCallActivity.this, number, sim.handle);
                    try { c.phoneAccountSelected(sim.handle, false); } catch (Throwable ignored) { }
                }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (panel.getChildCount() > 0) l.topMargin = Ui.dp(this, 10);
            panel.addView(b, l);
        }
        TextView hint = Ui.text(this, R.style.Text_Meta, "Se recordará para este número.");
        hint.setGravity(android.view.Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
        panel.addView(hint);
    }

    private void toggle(ImageButton b, boolean on) {
        b.setActivated(on);
        b.setImageTintList(ColorStateList.valueOf(getColor(on ? R.color.on_accent : R.color.ink)));
    }

    /** Ondas alrededor del avatar mientras suena y punto rojo parpadeante mientras graba. */
    private void animate(boolean ringingNow, boolean recording) {
        if (ringingNow && ringAnim == null) {
            ringAnim = new android.animation.AnimatorSet();
            java.util.List<android.animation.Animator> list = new java.util.ArrayList<>();
            View[] rings = {findViewById(R.id.ring1), findViewById(R.id.ring2)};
            for (int i = 0; i < rings.length; i++) {
                android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofPropertyValuesHolder(rings[i],
                        android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.55f),
                        android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.55f),
                        android.animation.PropertyValuesHolder.ofFloat("alpha", 0.45f, 0f));
                a.setDuration(2400);
                a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                a.setStartDelay(i * 1200L);
                list.add(a);
            }
            ringAnim.playTogether(list);
            ringAnim.start();
        } else if (!ringingNow && ringAnim != null) {
            ringAnim.cancel();
            ringAnim = null;
            findViewById(R.id.ring1).setAlpha(0f);
            findViewById(R.id.ring2).setAlpha(0f);
        }
        if (recording && blinkAnim == null) {
            blinkAnim = android.animation.ObjectAnimator.ofFloat(findViewById(R.id.rec_dot), "alpha", 1f, 0.25f);
            blinkAnim.setDuration(700);
            blinkAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            blinkAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            blinkAnim.start();
        } else if (!recording && blinkAnim != null) {
            blinkAnim.cancel();
            blinkAnim = null;
        }
    }
}
