package com.acta.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class DetailActivity extends Activity {

    private Db.Call call;
    private View rootView;

    private final android.content.BroadcastReceiver updated = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            if (aiBox == null) return;
            Db.Call fresh = Db.get(DetailActivity.this).byId(call.id);
            if (fresh == null) return;
            call.summary = fresh.summary;
            call.transcript = fresh.transcript;
            renderAi();
        }
    };
    private File file;
    private MediaPlayer player;
    private static final float[] SPEEDS = {1f, 1.5f, 2f};
    private int speedIdx;
    private TextView speedBtn;

    private void showSpeed() {
        float v = SPEEDS[speedIdx];
        String t = v == 1f ? "1×" : (v == 1.5f ? "1,5×" : "2×");
        speedBtn.setText(t);
        speedBtn.setContentDescription("Velocidad de reproducción: " + t + ". Toca para cambiarla.");
    }

    /** Cambia la velocidad sin alterar el tono de la voz. Solo con el audio sonando. */
    private void applySpeed() {
        try {
            player.setPlaybackParams(new android.media.PlaybackParams().setSpeed(SPEEDS[speedIdx]).setPitch(1f));
        } catch (Throwable ignored) { }
    }
    private ImageButton play;
    private SeekBar seek;
    private TextView pos, len;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean dragging;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (player != null && !dragging) {
                int p = player.getCurrentPosition();
                seek.setProgress(p);
                pos.setText(Util.clock(p / 1000));
            }
            h.postDelayed(this, 200);
        }
    };

    private int themeSeen;

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        themeSeen = Ui.themeGen;
        setContentView(R.layout.activity_detail);
        call = Db.get(this).byId(getIntent().getLongExtra("id", -1));
        if (call == null) { finish(); return; }
        rootView = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        // Con el bloqueo activado: sin capturas de pantalla ni vista previa en Recientes.
        if (AppLock.enabled(this)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        file = new File(Util.dir(this), call.file);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        String formatted = TextUtils.isEmpty(call.number) ? "Número oculto" : Util.number(this, call.number);
        boolean named = !TextUtils.isEmpty(call.name);
        Ui.avatar((TextView) findViewById(R.id.avatar), (android.widget.ImageView) findViewById(R.id.avatar_icon), named ? call.name : null, call.number);
        ((TextView) findViewById(R.id.number)).setText(named ? call.name : formatted);
        TextView name = findViewById(R.id.name);
        if (!named) name.setVisibility(View.GONE);
        else name.setText(formatted);
        ((TextView) findViewById(R.id.kind)).setText((call.direction == 1 ? "Entrante" : "Saliente") + " · "
                + Util.dayHeader(call.startMs) + ", " + Util.time(call.startMs) + " · " + Util.clock(call.durationSec));

        LinearLayout chips = findViewById(R.id.chips);
        String srcLabel = Ui.sourceLabel(call.source);
        if (srcLabel != null) {
            TextView c1 = Ui.chip(this, srcLabel, false);
            Ui.icon(c1, R.drawable.ic_wave, R.color.ink_2, 14);
            chips.addView(c1);
        }
        File af = new File(Util.dir(this), call.file);
        if (af.exists() && Crypto.isEncrypted(af)) {
            TextView c2 = Ui.chip(this, "Cifrada", false);
            Ui.icon(c2, R.drawable.ic_lock, R.color.ink_2, 13);
            LinearLayout.LayoutParams cl = Ui.lp(Ui.WRAP, Ui.WRAP);
            cl.setMarginStart(Ui.dp(this, 6));
            chips.addView(c2, cl);
        }
        if (af.exists()) {
            TextView c3 = Ui.chip(this, Util.size(Crypto.plainLength(af)), false);
            LinearLayout.LayoutParams cl = Ui.lp(Ui.WRAP, Ui.WRAP);
            cl.setMarginStart(Ui.dp(this, 6));
            chips.addView(c3, cl);
        }

        LinearLayout facts = findViewById(R.id.facts);
        fact(facts, "Número", TextUtils.isEmpty(call.number) ? "Oculto" : call.number);
        NumberInfo info = NumberInfo.of(call.number);
        if (info != null) {
            if (!TextUtils.isEmpty(info.lineType)) fact(facts, "Tipo de línea", info.lineType);
            if (!TextUtils.isEmpty(info.origin)) fact(facts, "Origen", info.origin);
        }
        fact(facts, "Fecha", Util.dateFull(call.startMs));
        fact(facts, "Hora", Util.timeFull(call.startMs));
        fact(facts, "Duración de la llamada", Util.duration(call.durationSec));
        fact(facts, "Duración grabada", Util.duration(call.recordedSec));
        fact(facts, "Archivo", file.exists() ? Util.size(Crypto.plainLength(file)) + "  ·  "
                + call.file.substring(call.file.lastIndexOf('.') + 1).toUpperCase(Util.ES)
                + (Crypto.isEncrypted(file) ? "  ·  cifrado" : "") : "Audio borrado");
        if (!TextUtils.isEmpty(call.source)) fact(facts, "Fuente de audio", call.source);
        if (!TextUtils.isEmpty(call.sim)) fact(facts, "Tarjeta SIM", call.sim);

        buildSummary((LinearLayout) findViewById(R.id.ai));

        play = findViewById(R.id.btn_play);
        seek = findViewById(R.id.seek);
        pos = findViewById(R.id.pos);
        len = findViewById(R.id.len);
        len.setText(Util.clock(call.recordedSec));

        speedBtn = findViewById(R.id.btn_speed);
        speedIdx = Math.max(0, Math.min(SPEEDS.length - 1, Util.prefs(this).getInt("play_speed", 0)));
        showSpeed();
        speedBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                speedIdx = (speedIdx + 1) % SPEEDS.length;
                Util.prefs(DetailActivity.this).edit().putInt("play_speed", speedIdx).apply();
                showSpeed();
                if (player != null && player.isPlaying()) applySpeed();
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggle(); }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) pos.setText(Util.clock(p / 1000));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                if (ensurePlayer()) player.seekTo(s.getProgress());
            }
        });

        View callBtn = findViewById(R.id.act_call);
        if (TextUtils.isEmpty(call.number)) callBtn.setEnabled(false);
        callBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + call.number)));
            }
        });
        findViewById(R.id.act_share).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { share(); }
        });
        View wa = findViewById(R.id.act_wa);
        if (TextUtils.isEmpty(call.number)) { wa.setEnabled(false); wa.setAlpha(0.4f); callBtn.setAlpha(0.4f); }
        wa.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!QuickActions.openWhatsApp(DetailActivity.this, call.number, ""))
                    Toast.makeText(DetailActivity.this, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.btn_more).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moreMenu(); }
        });
        findViewById(R.id.btn_fav).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                call.fav = !call.fav;
                Db.get(DetailActivity.this).setFav(call.id, call.fav);
                renderFav();
                Toast.makeText(DetailActivity.this, call.fav ? "Favorita: no se borrará en los borrados automáticos ni en lote"
                        : "Quitada de favoritas", Toast.LENGTH_SHORT).show();
            }
        });
        renderFav();
        renderAudioState();
        if (!TextUtils.isEmpty(call.number)) {
            View.OnClickListener person = new View.OnClickListener() {
                @Override public void onClick(View v) { openPerson(); }
            };
            findViewById(R.id.avatar).setOnClickListener(person);
            findViewById(R.id.number).setOnClickListener(person);
        }
        buildMarks();
        query = getIntent().getStringExtra("q");
        final double seekSec = getIntent().getDoubleExtra("seek", -1);
        if (seekSec >= 0) {
            showTranscript = true;
            renderAi();
            h.postDelayed(new Runnable() {
                @Override public void run() { playFrom(seekSec); }
            }, 300);
        }
    }

    private void renderFav() {
        android.widget.ImageButton b = findViewById(R.id.btn_fav);
        b.setImageResource(call.fav ? R.drawable.ic_star_on : R.drawable.ic_star);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(call.fav ? R.color.accent : R.color.ink)));
        b.setContentDescription(call.fav ? "Quitar de favoritas" : "Añadir a favoritas");
    }

    /** Sin audio (borrado): el reproductor da paso a un aviso y se desactiva compartir. */
    private void renderAudioState() {
        boolean has = file.exists();
        View playerRow = (View) play.getParent();
        playerRow.setVisibility(has ? View.VISIBLE : View.GONE);
        View share = findViewById(R.id.act_share);
        share.setEnabled(has);
        share.setAlpha(has ? 1f : 0.4f);
        if (!has && noAudioNote == null) {
            LinearLayout card = (LinearLayout) playerRow.getParent();
            noAudioNote = new LinearLayout(this);
            noAudioNote.setGravity(android.view.Gravity.CENTER_VERTICAL);
            noAudioNote.addView(Ui.iconView(this, R.drawable.ic_doc, R.color.ink_2, 20));
            TextView t = Ui.text(this, R.style.Text_Meta, TextUtils.isEmpty(call.transcript)
                    ? "El audio de esta llamada se borró."
                    : "El audio de esta llamada se borró. La transcripción y el resumen se conservan.");
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(dp(12));
            noAudioNote.addView(t, tl);
            card.addView(noAudioNote, 0);
        }
        if (noAudioNote != null) noAudioNote.setVisibility(has ? View.GONE : View.VISIBLE);
        if (marksBox != null && !has) marksBox.setVisibility(View.GONE);
    }

    private LinearLayout noAudioNote;

    private void openPerson() {
        startActivity(new Intent(this, PersonActivity.class).putExtra("number", call.number).putExtra("name", call.name));
    }

    /** Reproduce desde un segundo concreto (búsqueda, marcas, transcripción, respuestas). */
    private void playFrom(double sec) {
        if (!ensurePlayer()) return;
        int ms = (int) Math.max(0, Math.min(player.getDuration() - 500, sec * 1000 - 1500));
        player.seekTo(ms);
        seek.setProgress(ms);
        pos.setText(Util.clock(ms / 1000));
        if (!player.isPlaying()) toggle();
    }

    private LinearLayout marksBox;

    private void buildMarks() {
        if (marksBox == null) {
            LinearLayout chips = findViewById(R.id.chips);
            LinearLayout card = (LinearLayout) chips.getParent();
            marksBox = new LinearLayout(this);
            marksBox.setOrientation(LinearLayout.VERTICAL);
            card.addView(marksBox);
        }
        marksBox.removeAllViews();
        marksBox.setVisibility(file.exists() ? View.VISIBLE : View.GONE);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(10), 0, 0);
        boolean has = !TextUtils.isEmpty(call.marks);
        TextView label = Ui.text(this, R.style.Text_Label, has ? "Momentos marcados" : "Sin momentos marcados");
        label.setPadding(0, 0, 0, 0);
        head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = Ui.textButton(this, "Marcar aquí");
        Ui.icon(add, R.drawable.ic_bookmark, R.color.accent, 15);
        add.setCompoundDrawablePadding(dp(6));
        add.setContentDescription("Marcar el momento actual de la grabación");
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { addMarkHere(); }
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        al.setMarginEnd(-dp(12));
        head.addView(add, al);
        marksBox.addView(head);
        if (!has) return;
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        hs.addView(row);
        for (String m : call.marks.split(",")) {
            final int sec;
            try { sec = Integer.parseInt(m.trim()); } catch (Exception e) { continue; }
            TextView chip = Ui.chip(this, Util.clock(sec), true);
            Ui.icon(chip, R.drawable.ic_bookmark, R.color.accent, 13);
            chip.setMinHeight(dp(36));
            chip.setPadding(dp(12), 0, dp(14), 0);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { playFrom(sec + 1.5); }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    new AlertDialog.Builder(DetailActivity.this)
                            .setMessage("¿Quitar el marcador del " + Util.clock(sec) + "?")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Quitar", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) { removeMark(sec); }
                            }).show();
                    return true;
                }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            if (row.getChildCount() > 0) l.setMarginStart(dp(6));
            row.addView(chip, l);
        }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = dp(4);
        marksBox.addView(hs, hl);
        TextView tip = Ui.text(this, R.style.Text_Meta, "Mantén pulsado un marcador para quitarlo.");
        tip.setTextSize(12);
        tip.setPadding(0, dp(6), 0, 0);
        marksBox.addView(tip);
    }

    /** Añade un marcador en el segundo que se está escuchando. */
    private void addMarkHere() {
        int ms = player != null ? player.getCurrentPosition() : seek.getProgress();
        int sec = Math.max(0, ms / 1000);
        java.util.TreeSet<Integer> set = marksSet();
        for (int m : set) if (Math.abs(m - sec) < 2) {
            Toast.makeText(this, "Ya hay un marcador en el " + Util.clock(m), Toast.LENGTH_SHORT).show();
            return;
        }
        set.add(sec);
        saveMarks(set);
        Toast.makeText(this, "Marcador añadido en el " + Util.clock(sec), Toast.LENGTH_SHORT).show();
    }

    private void removeMark(int sec) {
        java.util.TreeSet<Integer> set = marksSet();
        set.remove(sec);
        saveMarks(set);
    }

    private java.util.TreeSet<Integer> marksSet() {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        if (!TextUtils.isEmpty(call.marks)) for (String m : call.marks.split(",")) {
            try { set.add(Integer.parseInt(m.trim())); } catch (Exception ignored) { }
        }
        return set;
    }

    private void saveMarks(java.util.TreeSet<Integer> set) {
        StringBuilder sb = new StringBuilder();
        for (int m : set) { if (sb.length() > 0) sb.append(','); sb.append(m); }
        call.marks = sb.length() == 0 ? null : sb.toString();
        Db.get(this).updateMarks(call.id, call.marks);
        sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(getPackageName()));
        buildMarks();
    }

    private void moreMenu() {
        final boolean hasNumber = !TextUtils.isEmpty(call.number);
        final boolean spam = hasNumber && Util.isSpam(this, call.number);
        final java.util.List<String> items = new java.util.ArrayList<>();
        if (hasNumber) {
            items.add("Ver ficha de la persona");
            items.add("Programar llamada");
            items.add("Copiar número");
            items.add(TextUtils.isEmpty(call.name) ? "Añadir a contactos" : "Ver contacto");
            items.add(spam ? "Quitar de spam" : "Marcar como spam");
        }
        items.add(call.fav ? "Quitar de favoritas" : "Añadir a favoritas");
        if (file.exists()) items.add("Borrar solo el audio");
        items.add("Eliminar grabación");
        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String it = items.get(w);
                        if (it.equals("Ver ficha de la persona")) {
                            openPerson();
                        } else if (it.equals("Programar llamada")) {
                            ScheduleCall.show(DetailActivity.this, call.number, call.name, null);
                        } else if (it.equals("Copiar número")) {
                            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Número", call.number));
                            Toast.makeText(DetailActivity.this, "Número copiado", Toast.LENGTH_SHORT).show();
                        } else if (it.equals("Añadir a contactos")) {
                            Intent i = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                            i.setType(android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                            i.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, call.number);
                            Summarizer.Result pr = Summarizer.parse(call.summary);
                            if (pr != null && !pr.person.isEmpty())
                                i.putExtra(android.provider.ContactsContract.Intents.Insert.NAME, pr.person);
                            safeStart(i);
                        } else if (it.equals("Ver contacto")) {
                            Uri u = Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(call.number));
                            Uri contact = null;
                            android.database.Cursor cur = null;
                            try {
                                cur = getContentResolver().query(u, new String[]{android.provider.ContactsContract.PhoneLookup._ID,
                                        android.provider.ContactsContract.PhoneLookup.LOOKUP_KEY}, null, null, null);
                                if (cur != null && cur.moveToFirst())
                                    contact = android.provider.ContactsContract.Contacts.getLookupUri(cur.getLong(0), cur.getString(1));
                            } catch (Exception ignored) {
                            } finally { if (cur != null) cur.close(); }
                            if (contact != null) safeStart(new Intent(Intent.ACTION_VIEW, contact));
                        } else if (it.equals("Marcar como spam")) {
                            Util.addSpam(DetailActivity.this, call.number);
                            Toast.makeText(DetailActivity.this, "Marcado como spam. Sus llamadas se rechazarán solas.", Toast.LENGTH_LONG).show();
                        } else if (it.equals("Quitar de spam")) {
                            Util.removeSpam(DetailActivity.this, call.number);
                            Toast.makeText(DetailActivity.this, "Quitado de spam", Toast.LENGTH_SHORT).show();
                        } else if (it.equals("Añadir a favoritas") || it.equals("Quitar de favoritas")) {
                            findViewById(R.id.btn_fav).performClick();
                        } else if (it.equals("Borrar solo el audio")) {
                            confirmDeleteAudio();
                        } else {
                            confirmDelete();
                        }
                    }
                }).show();
    }

    private void safeStart(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "No hay ninguna app para esta acción", Toast.LENGTH_SHORT).show(); }
    }

    // ================================================================== Análisis con IA

    private LinearLayout aiBox, askBox;
    private TextView aiBtn;
    private Summarizer.Result ai;
    private boolean showTranscript;
    private String query;
    private final java.util.List<String[]> qa = new java.util.ArrayList<>(); // {pregunta, respuesta, segundo}
    private boolean asking;
    private String draftQuestion = "";

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    private void buildSummary(LinearLayout parent) {
        askBox = new LinearLayout(this);
        askBox.setOrientation(LinearLayout.VERTICAL);
        parent.addView(askBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView h = Ui.section(this, "Análisis");
        h.setPadding(dp(10), dp(24), dp(10), dp(8));
        parent.addView(h);

        aiBox = new LinearLayout(this);
        aiBox.setOrientation(LinearLayout.VERTICAL);
        parent.addView(aiBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        aiBtn = Ui.button(this, "Analizar llamada con IA", true);
        parent.addView(aiBtn, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        aiBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { generateSummary(); }
        });
        renderAi();
    }

    private void styleAiBtn(boolean primary) {
        aiBtn.setBackgroundResource(primary ? R.drawable.bg_btn : R.drawable.bg_btn2);
        aiBtn.setTextColor(getColor(primary ? R.color.on_accent : R.color.accent));
        Ui.icon(aiBtn, primary ? R.drawable.ic_wave : R.drawable.ic_refresh, primary ? R.color.on_accent : R.color.accent, 18);
    }

    private View busyCard() {
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        row.addView(pb, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView t = body("Analizando la llamada… El resumen y las tareas aparecerán aquí solos en cuanto termine.", R.color.ink_2);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(dp(12));
        row.addView(t, tl);
        card.addView(row);
        return card;
    }

    private LinearLayout infoCard(String text, int color) {
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.addView(body(text, color));
        return card;
    }

    private void renderAi() {
        aiBox.removeAllViews();
        askBox.removeAllViews();
        if (!TextUtils.isEmpty(call.transcript) && !AutoAnalyzer.isRunning(call.id)) addAskCard();
        ai = Summarizer.parse(call.summary);
        if (AutoAnalyzer.isRunning(call.id)) {
            aiBox.addView(busyCard());
            aiBtn.setVisibility(View.GONE);
            return;
        }
        aiBtn.setVisibility(View.VISIBLE);
        if (!file.exists()) {
            // Sin audio no se puede analizar ni rehacer: se muestra lo que haya guardado.
            aiBtn.setVisibility(View.GONE);
            if (ai == null && TextUtils.isEmpty(call.summary)) {
                aiBox.addView(infoCard("Esta llamada no se analizó antes de borrar el audio, así que no tiene resumen ni transcripción.", R.color.ink_2));
                return;
            }
        }
        String err = AutoAnalyzer.lastError(this, call.id);
        if (ai == null && TextUtils.isEmpty(call.summary)) {
            styleAiBtn(true);
            // Análisis automático: si no está hecho ni en marcha, empieza ahora mismo.
            if (err == null && AutoAnalyzer.startAuto(this, call.id)) {
                aiBox.addView(busyCard());
                aiBtn.setVisibility(View.GONE);
                return;
            }
            if (err != null) {
                aiBox.addView(infoCard("No se pudo analizar: " + err, R.color.danger));
                aiBtn.setText("Reintentar");
                return;
            }
            aiBox.addView(infoCard("Obtén el contexto de la llamada, un resumen breve y las tareas acordadas, "
                    + "que podrás añadir a tu calendario. El audio se envía a la IA (Groq) solo al pulsar.", R.color.ink_2));
            aiBtn.setText("Analizar llamada con IA");
            return;
        }
        if (err != null) aiBox.addView(infoCard("No se pudo rehacer el análisis: " + err, R.color.danger));
        styleAiBtn(false);
        aiBtn.setText("Rehacer análisis");
        if (ai == null) { // resumen antiguo, en texto libre
            aiBox.addView(infoCard(call.summary, R.color.ink));
            addTranscript();
            return;
        }
        if (!ai.context.isEmpty() || !ai.summary.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.setPadding(dp(18), dp(18), dp(18), dp(18));
            if (!ai.context.isEmpty()) {
                card.addView(label("Contexto"));
                card.addView(body(ai.context, R.color.ink));
            }
            if (!ai.context.isEmpty() && !ai.summary.isEmpty()) {
                View div = new View(this);
                div.setBackgroundColor(getColor(R.color.line));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
                dl.topMargin = dp(16);
                dl.bottomMargin = dp(16);
                card.addView(div, dl);
            }
            if (!ai.summary.isEmpty()) {
                card.addView(label("Resumen"));
                card.addView(body(ai.summary, R.color.ink));
            }
            aiBox.addView(card);
        }
        if (!ai.note.isEmpty()) {
            LinearLayout note = new LinearLayout(this);
            note.setBackgroundResource(R.drawable.bg_note);
            note.setPadding(dp(16), dp(16), dp(18), dp(16));
            note.addView(Ui.iconView(this, R.drawable.ic_hist, R.color.accent, 20));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView nt = Ui.text(this, R.style.Text, "Para la próxima llamada", 13, true);
            nt.setTextColor(getColor(R.color.accent));
            col.addView(nt);
            TextView nb = body(Character.toUpperCase(ai.note.charAt(0)) + ai.note.substring(1), R.color.ink);
            nb.setPadding(0, dp(4), 0, 0);
            col.addView(nb);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.setMarginStart(dp(12));
            note.addView(col, cl);
            android.widget.ImageView del = new android.widget.ImageView(this);
            del.setImageResource(R.drawable.ic_x);
            del.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
            del.setBackgroundResource(R.drawable.bg_icon_btn);
            del.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            del.setContentDescription("Quitar esta nota");
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(DetailActivity.this)
                            .setMessage("¿Quitar la nota «Para la próxima llamada»? Ya no aparecerá al recibir llamadas de esta persona.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Quitar", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    ai.note = "";
                                    saveAi();
                                    renderAi();
                                }
                            }).show();
                }
            });
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(36), dp(36));
            dl.topMargin = -dp(8);
            dl.setMarginEnd(-dp(8));
            note.addView(del, dl);
            aiBox.addView(note, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        }
        TextView rep = Ui.textButton(this, "Informar de un problema con este análisis");
        rep.setTextColor(getColor(R.color.ink_2));
        rep.setTextSize(12.5f);
        rep.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                AiConsent.report(DetailActivity.this, "Análisis de llamada", ai.toJson());
            }
        });
        LinearLayout.LayoutParams rpl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        rpl.gravity = android.view.Gravity.END;
        aiBox.addView(rep, rpl);
        int n = ai.tasks.size();
        int pend = ai.pending();
        TextView th = Ui.section(this, n == 0 ? "Tareas" : "Tareas · " + (pend == 0 ? "todas hechas"
                : pend + (pend == 1 ? " pendiente" : " pendientes")));
        th.setPadding(dp(10), dp(24), dp(10), dp(8));
        aiBox.addView(th);
        if (n == 0) aiBox.addView(infoCard("No se acordó ninguna tarea en esta llamada.", R.color.ink_2));
        for (int i = 0; i < n; i++) aiBox.addView(taskCard(ai.tasks.get(i), i > 0));
        addTranscript();
    }

    private TextView label(String s) {
        TextView t = new TextView(this, null, 0, R.style.Text_Label);
        t.setText(s);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private TextView body(String s, int color) {
        TextView t = new TextView(this, null, 0, R.style.Text);
        t.setText(s);
        t.setTextSize(15.5f);
        t.setLineSpacing(dp(4), 1f);
        t.setTextColor(getColor(color));
        t.setTextIsSelectable(true);
        return t;
    }

    private View taskCard(final Summarizer.Task t, boolean gap) {
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(16), dp(16), dp(16), dp(8));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (gap) clp.topMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final android.widget.ImageView cb = new android.widget.ImageView(this);
        cb.setBackgroundResource(R.drawable.bg_cbx);
        cb.setImageResource(R.drawable.ic_check);
        cb.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.on_accent)));
        cb.setPadding(dp(4), dp(4), dp(4), dp(4));
        cb.setActivated(t.done);
        cb.setImageAlpha(t.done ? 255 : 0);
        cb.setContentDescription(t.done ? "Marcar como pendiente" : "Marcar como hecha");
        LinearLayout.LayoutParams cbl = new LinearLayout.LayoutParams(dp(26), dp(26));
        cbl.topMargin = dp(1);
        row.addView(cb, cbl);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, R.style.Text, t.title, 15.5f, true);
        title.setLineSpacing(dp(3), 1f);
        if (t.done) {
            title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(getColor(R.color.ink_2));
        }
        col.addView(title);
        if (!t.detail.isEmpty()) {
            TextView d = new TextView(this, null, 0, R.style.Text_Meta);
            d.setText(t.detail);
            d.setTextSize(14);
            d.setLineSpacing(dp(3), 1f);
            d.setPadding(0, dp(4), 0, 0);
            d.setTextIsSelectable(true);
            col.addView(d);
        }
        TextView meta = new TextView(this, null, 0, R.style.Text_Meta);
        StringBuilder m = new StringBuilder();
        if (!t.owner.isEmpty()) m.append("Responsable: ").append(t.owner).append(" · ");
        m.append(whenText(t));
        meta.setText(m.toString());
        meta.setPadding(0, dp(4), 0, 0);
        col.addView(meta);
        LinearLayout.LayoutParams coll = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        coll.setMarginStart(dp(14));
        row.addView(col, coll);
        card.addView(row);

        TextView cal = Ui.textButton(this, "Añadir al calendario");
        Ui.icon(cal, R.drawable.ic_cal, R.color.accent, 17);
        cal.setCompoundDrawablePadding(dp(6));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        alp.setMarginStart(dp(26));
        alp.topMargin = dp(4);
        card.addView(cal, alp);

        cb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                t.done = !t.done;
                saveAi();
                renderAi();
            }
        });
        cal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { addToCalendar(t); }
        });
        return card;
    }

    /** "Hoy · 17:00", "Mañana", "viernes 20 de noviembre · 10:00" o "Sin fecha". */
    private String whenText(Summarizer.Task t) {
        if (t.date == null) return t.time != null ? "Sin fecha · " + t.time : "Sin fecha";
        String day = t.date;
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", Util.ES);
            java.util.Date d = in.parse(t.date);
            java.util.Calendar c = java.util.Calendar.getInstance();
            java.util.Calendar today = java.util.Calendar.getInstance();
            c.setTime(d);
            int diff = (int) Math.round((startOfDay(c) - startOfDay(today)) / 86400000.0);
            if (diff == 0) day = "Hoy";
            else if (diff == 1) day = "Mañana";
            else if (diff == -1) day = "Ayer";
            else day = new java.text.SimpleDateFormat(
                    c.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
                            ? "EEEE d 'de' MMMM" : "d 'de' MMMM 'de' yyyy", Util.ES).format(d);
        } catch (Exception ignored) { }
        return t.time != null ? day + " · " + t.time : day;
    }

    private static long startOfDay(java.util.Calendar c) {
        java.util.Calendar x = (java.util.Calendar) c.clone();
        x.set(java.util.Calendar.HOUR_OF_DAY, 0);
        x.set(java.util.Calendar.MINUTE, 0);
        x.set(java.util.Calendar.SECOND, 0);
        x.set(java.util.Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }

    /** Abre el calendario del móvil con el evento ya relleno (el usuario lo confirma). */
    private void addToCalendar(Summarizer.Task t) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        boolean allDay = false;
        boolean guessed = false;
        int y = 0, mo = 0, d = 0;
        if (t.date != null) {
            String[] p = t.date.split("-");
            y = Integer.parseInt(p[0]); mo = Integer.parseInt(p[1]) - 1; d = Integer.parseInt(p[2]);
            cal.set(y, mo, d, 9, 0, 0);
            if (t.time != null) {
                String[] q = t.time.split(":");
                cal.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(q[0]));
                cal.set(java.util.Calendar.MINUTE, Integer.parseInt(q[1]));
            } else {
                allDay = true;
            }
        } else {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            if (t.time != null) {
                String[] q = t.time.split(":");
                cal.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(q[0]));
                cal.set(java.util.Calendar.MINUTE, Integer.parseInt(q[1]));
            } else {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 9);
                cal.set(java.util.Calendar.MINUTE, 0);
            }
            guessed = true;
        }
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long begin, end;
        if (allDay) {
            java.util.Calendar u = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            u.clear();
            u.set(y, mo, d);
            begin = u.getTimeInMillis();
            end = begin + 86400000L;
        } else {
            begin = cal.getTimeInMillis();
            end = begin + 30 * 60000L;
        }
        String who = !TextUtils.isEmpty(call.name) ? call.name : Util.number(this, call.number);
        StringBuilder desc = new StringBuilder();
        if (!t.detail.isEmpty()) desc.append(t.detail).append("\n\n");
        if (!t.owner.isEmpty()) desc.append("Responsable: ").append(t.owner).append('\n');
        desc.append("De la llamada con ").append(who).append(" · ")
            .append(Util.dateFull(call.startMs)).append(", ").append(Util.time(call.startMs))
            .append("\n(Acta)");
        Intent i = new Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra(android.provider.CalendarContract.Events.TITLE, t.title)
                .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc.toString())
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, end)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, allDay);
        try {
            startActivity(i);
            if (guessed) Toast.makeText(this, "En la llamada no se dijo fecha: la he puesto mañana. Cámbiala si quieres.",
                    Toast.LENGTH_LONG).show();
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No hay ninguna app de calendario en el móvil.", Toast.LENGTH_LONG).show();
        }
    }

    private void addTranscript() {
        if (TextUtils.isEmpty(call.transcript)) return;
        LinearLayout card = Ui.card(this);
        TextView toggle = Ui.text(this, R.style.Text, showTranscript ? "Ocultar transcripción" : "Ver transcripción", 15, true);
        Ui.icon(toggle, R.drawable.ic_doc, R.color.ink, 20);
        toggle.setCompoundDrawablePadding(dp(12));
        toggle.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toggle.setMinHeight(dp(56));
        toggle.setPadding(dp(16), 0, dp(16), 0);
        toggle.setBackgroundResource(R.drawable.bg_tbtn);
        card.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (showTranscript) {
            java.util.List<Summarizer.Seg> segs = Summarizer.parseSegments(call.segments);
            if (segs.isEmpty()) {
                TextView tr = body("", R.color.ink_2);
                tr.setText(highlight(call.transcript));
                tr.setTextSize(14.5f);
                tr.setPadding(dp(18), 0, dp(18), dp(18));
                card.addView(tr);
            } else {
                LinearLayout list = new LinearLayout(this);
                list.setOrientation(LinearLayout.VERTICAL);
                list.setPadding(dp(8), 0, dp(12), dp(10));
                char prevSpk = 0;
                for (final Summarizer.Seg sg : segs) {
                    LinearLayout row = new LinearLayout(this);
                    row.setPadding(dp(8), dp(7), dp(6), dp(7));
                    row.setBackgroundResource(R.drawable.bg_tbtn);
                    TextView tm = Ui.text(this, R.style.Text, ConvSearch.clock(sg.t), 12.5f, true);
                    tm.setTextColor(getColor(R.color.accent));
                    tm.setMinWidth(dp(44));
                    row.addView(tm);
                    TextView tx = Ui.text(this, R.style.Text, highlight(sg.s));
                    tx.setTextSize(14.5f);
                    tx.setLineSpacing(dp(3), 1f);
                    String who = sg.spk != prevSpk ? SpeakerSplit.label(sg.spk, call) : "";
                    prevSpk = sg.spk;
                    if (!who.isEmpty()) {
                        // Quién habla: «Tú» en el color de la app, la otra persona en gris.
                        LinearLayout col = new LinearLayout(this);
                        col.setOrientation(LinearLayout.VERTICAL);
                        TextView sp = Ui.text(this, R.style.Text, who, 12f, true);
                        sp.setTextColor(getColor(sg.spk == SpeakerSplit.OWNER ? R.color.accent : R.color.ink_3));
                        sp.setPadding(0, 0, 0, dp(1));
                        col.addView(sp);
                        col.addView(tx);
                        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    } else {
                        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    }
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { playFrom(sg.t + 1.5); }
                    });
                    list.addView(row);
                }
                card.addView(list);
            }
        }
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTranscript = !showTranscript; renderAi(); }
        });
        aiBox.addView(card, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
    }

    private CharSequence highlight(String text) {
        android.text.SpannableString sp = new android.text.SpannableString(text);
        if (TextUtils.isEmpty(query)) return sp;
        String f = ConvSearch.fold(text), q = ConvSearch.fold(query).trim();
        if (q.length() < 2) return sp;
        int from = 0;
        while (true) {
            int i = f.indexOf(q, from);
            if (i < 0) break;
            int e = Math.min(sp.length(), i + q.length());
            sp.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), i, e, 0);
            sp.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.accent)), i, e, 0);
            sp.setSpan(new android.text.style.BackgroundColorSpan(getColor(R.color.accent_soft)), i, e, 0);
            from = e;
        }
        return sp;
    }

    /** «Pregunta a Acta»: la IA responde con lo que se habló en esta llamada. */
    private void addAskCard() {
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(16), dp(14), dp(14), dp(14));
        TextView t = Ui.text(this, R.style.Text, "Pregunta a Acta", 15, true);
        t.setPadding(dp(2), 0, 0, 0);
        card.addView(t);

        for (final String[] e : qa) {
            TextView q = Ui.text(this, R.style.Text, e[0], 14.5f, true);
            q.setPadding(0, dp(16), 0, 0);
            card.addView(q);
            TextView a = body(e[1], e[2] != null && e[2].equals("err") ? R.color.danger : R.color.ink);
            a.setPadding(0, dp(4), 0, 0);
            card.addView(a);
            if (e[2] == null || !e[2].equals("err")) {
                TextView rep = Ui.textButton(this, "Informar");
                rep.setTextColor(getColor(R.color.ink_2));
                rep.setTextSize(12.5f);
                rep.setContentDescription("Informar de esta respuesta de la IA");
                rep.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        AiConsent.report(DetailActivity.this, "Respuesta a «" + e[0] + "»", e[1]);
                    }
                });
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
                rl.setMarginStart(-dp(14));
                card.addView(rep, rl);
            }
            if (e[2] != null && !e[2].equals("err")) {
                final double sec = Double.parseDouble(e[2]);
                TextView go = Ui.textButton(this, "Escuchar en el " + ConvSearch.clock(sec));
                Ui.icon(go, R.drawable.ic_play, R.color.accent, 13);
                go.setCompoundDrawablePadding(dp(6));
                go.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { playFrom(sec); }
                });
                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
                gl.setMarginStart(-dp(14));
                card.addView(go, gl);
            }
        }

        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setHint(asking ? "Pensando…" : "Escribe tu pregunta");
        in.setText(draftQuestion);
        in.setEnabled(!asking);
        in.setTextColor(getColor(R.color.ink));
        in.setHintTextColor(getColor(R.color.ink_2));
        in.setTextSize(15);
        in.setBackgroundResource(R.drawable.bg_url);
        in.setPadding(dp(14), dp(12), dp(14), dp(12));
        in.setMaxLines(3);
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        row.addView(in, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View sendView;
        if (asking) {
            android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
            pb.setPadding(dp(13), dp(13), dp(13), dp(13));
            sendView = pb;
        } else {
            android.widget.ImageView send = new android.widget.ImageView(this);
            send.setImageResource(R.drawable.ic_send);
            send.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.on_accent)));
            send.setBackgroundResource(R.drawable.bg_circle_accent);
            send.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            send.setContentDescription("Preguntar");
            send.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { ask(in.getText().toString()); }
            });
            sendView = send;
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(46), dp(46));
        sl.setMarginStart(dp(8));
        row.addView(sendView, sl);
        in.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence x, int a, int b, int c) { if (!asking) draftQuestion = x.toString(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, android.view.KeyEvent ev) {
                ask(in.getText().toString());
                return true;
            }
        });
        card.addView(row, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 12));
        askBox.addView(card, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 24));
    }

    private void ask(final String question) {
        final String q = question.trim();
        if (q.isEmpty() || asking) return;
        if (!AiConsent.granted(this)) {
            AiConsent.require(this, new Runnable() { @Override public void run() { ask(q); } });
            return;
        }
        if (!Summarizer.hasKey(this)) { Toast.makeText(this, "Falta la clave de IA (Ajustes › Análisis con IA).", Toast.LENGTH_LONG).show(); return; }
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        asking = true;
        draftQuestion = q;
        renderAi();
        new Thread(new Runnable() {
            @Override public void run() {
                String[] entry;
                try {
                    Summarizer.Answer a = Summarizer.ask(DetailActivity.this, call, q);
                    entry = new String[]{q, a.text, a.t >= 0 ? String.valueOf(a.t) : null};
                } catch (Throwable t) {
                    entry = new String[]{q, t.getMessage() != null ? t.getMessage() : "No se pudo responder.", "err"};
                }
                final String[] e = entry;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        asking = false;
                        draftQuestion = e[2] != null && e[2].equals("err") ? q : "";
                        qa.add(e);
                        renderAi();
                    }
                });
            }
        }).start();
    }

    private void saveAi() {
        if (ai == null) return;
        call.summary = ai.toJson();
        Db.get(this).updateAnalysis(call.id, call.summary, call.transcript);
        sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(getPackageName()));
    }

    private void generateSummary() {
        if (!Summarizer.hasKey(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Falta la clave de IA")
                    .setMessage("Para analizar llamadas necesitas una clave gratuita de Groq.\n\n"
                            + "1. Entra en console.groq.com y crea una cuenta gratis.\n"
                            + "2. En «API Keys», crea una clave.\n"
                            + "3. Pégala en Acta → Ajustes → Análisis con IA.")
                    .setPositiveButton("Ir a Ajustes", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            startActivity(new Intent(DetailActivity.this, SettingsActivity.class));
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        AiConsent.require(this, new Runnable() {
            @Override public void run() {
                AutoAnalyzer.start(DetailActivity.this, call.id, true);
                renderAi();
            }
        });
    }

    private void fact(LinearLayout parent, String k, String v) {
        View row = getLayoutInflater().inflate(R.layout.row_fact, parent, false);
        row.findViewById(R.id.sep).setVisibility(parent.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        ((TextView) row.findViewById(R.id.k)).setText(k);
        ((TextView) row.findViewById(R.id.v)).setText(v);
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private boolean ensurePlayer() {
        if (player != null) return true;
        if (!file.exists()) {
            Toast.makeText(this, "El audio de esta llamada se borró", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            if (Crypto.isEncrypted(file)) player.setDataSource(new Crypto.Source(this, file));
            else player.setDataSource(file.getAbsolutePath());
            player.prepare();
            seek.setMax(player.getDuration());
            len.setText(Util.clock(player.getDuration() / 1000));
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    play.setImageResource(R.drawable.ic_play);
                    seek.setProgress(0);
                    pos.setText("0:00");
                    mp.seekTo(0);
                }
            });
            h.post(tick);
            return true;
        } catch (Exception e) {
            player = null;
            Toast.makeText(this, "No se pudo abrir la grabación", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void toggle() {
        if (!ensurePlayer()) return;
        if (player.isPlaying()) {
            player.pause();
            play.setImageResource(R.drawable.ic_play);
            play.setContentDescription("Reproducir");
        } else {
            player.start();
            applySpeed();
            play.setImageResource(R.drawable.ic_pause);
            play.setContentDescription("Pausar");
        }
    }

    private void share() {
        if (!file.exists()) return;
        Uri u = Util.shareUri(call.file);
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType(call.file.endsWith(".mp3") ? "audio/mpeg" : "audio/*")
                .putExtra(Intent.EXTRA_STREAM, u)
                .putExtra(Intent.EXTRA_SUBJECT, "Llamada " + Util.number(this, call.number)
                        + " — " + Util.dateFull(call.startMs) + ", " + Util.time(call.startMs))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("", u));
        startActivity(Intent.createChooser(i, "Compartir grabación"));
    }

    private void confirmDeleteAudio() {
        if (AutoAnalyzer.isRunning(call.id)) {
            Toast.makeText(this, "Espera a que termine el análisis", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Borrar solo el audio")
                .setMessage(TextUtils.isEmpty(call.transcript)
                        ? "Esta llamada aún no está analizada: sin el audio ya no podrá tener transcripción ni resumen. No se puede deshacer."
                        : "Se conservan la transcripción, el resumen y las tareas. El audio no se puede recuperar.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar audio", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        release();
                        Cleanup.deleteAudio(DetailActivity.this, call);
                        sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(getPackageName()));
                        renderAudioState();
                        renderAi();
                        Toast.makeText(DetailActivity.this, "Audio borrado", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar grabación")
                .setMessage("Se borrarán el audio, la transcripción, el resumen y su registro de Acta. Esta acción no se puede deshacer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        release();
                        file.delete();
                        //noinspection ResultOfMethodCallIgnored
                        SpeakerSplit.vadFor(DetailActivity.this, call.file).delete();
                        //noinspection ResultOfMethodCallIgnored
                        AudioPack.nrgFor(DetailActivity.this, call.file).delete();
                        Db.get(DetailActivity.this).delete(call.id);
                        finish();
                    }
                })
                .show();
    }

    private void release() {
        h.removeCallbacks(tick);
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSeen != Ui.themeGen) { recreate(); return; }
        if (AppLock.needed(this)) {
            rootView.setVisibility(View.INVISIBLE);
            if (player != null && player.isPlaying()) { player.pause(); play.setImageResource(R.drawable.ic_play); }
            AppLock.require(this, new Runnable() {
                @Override public void run() { rootView.setVisibility(View.VISIBLE); }
            }, new Runnable() {
                @Override public void run() { finish(); }
            });
        } else {
            rootView.setVisibility(View.VISIBLE);
        }
        android.content.IntentFilter f = new android.content.IntentFilter(Util.ACTION_UPDATED);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(updated, f, android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updated, f);
        // Por si terminó el análisis automático mientras no estábamos aquí.
        if (aiBox != null) {
            Db.Call fresh = Db.get(this).byId(call.id);
            if (fresh != null) {
                call.summary = fresh.summary;
                call.transcript = fresh.transcript;
            }
            renderAi();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(updated); } catch (Exception ignored) { }
        if (player != null && player.isPlaying()) {
            player.pause();
            play.setImageResource(R.drawable.ic_play);
        }
    }

    @Override
    protected void onDestroy() {
        release();
        super.onDestroy();
    }
}
