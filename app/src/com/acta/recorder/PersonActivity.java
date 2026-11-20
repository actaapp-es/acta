package com.acta.recorder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Ficha de una persona: sus llamadas grabadas, resúmenes, tareas pendientes y llamadas programadas. */
public class PersonActivity extends Activity {

    private String number, name;
    private LinearLayout content;
    private View root;
    private int themeSeen;

    private final BroadcastReceiver updated = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { load(); }
    };

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        themeSeen = Ui.themeGen;
        number = getIntent().getStringExtra("number");
        name = getIntent().getStringExtra("name");
        if (TextUtils.isEmpty(number)) { finish(); return; }
        if (TextUtils.isEmpty(name)) name = Util.contactName(this, number);
        if (AppLock.enabled(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(getColor(R.color.bg));
        root = r;
        LinearLayout top = new LinearLayout(this);
        top.setPadding(dp(6), dp(8), dp(6), 0);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(getColor(R.color.ink)));
        back.setBackgroundResource(R.drawable.bg_icon_btn);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("Volver");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        r.addView(top);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), dp(28));
        scroll.addView(content);
        r.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(r);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSeen != Ui.themeGen) { recreate(); return; }
        IntentFilter f = new IntentFilter(Util.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updated, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updated, f);
        if (AppLock.needed(this)) {
            root.setVisibility(View.INVISIBLE);
            AppLock.require(this, new Runnable() {
                @Override public void run() { root.setVisibility(View.VISIBLE); }
            }, new Runnable() {
                @Override public void run() { finish(); }
            });
        } else {
            root.setVisibility(View.VISIBLE);
        }
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(updated); } catch (Exception ignored) { }
    }

    private void load() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Db.Call> calls = Db.get(PersonActivity.this).recentFor(number, 500);
                final List<Tasks.Item> tasks = Tasks.pending(PersonActivity.this, number);
                runOnUiThread(new Runnable() {
                    @Override public void run() { render(calls, tasks); }
                });
            }
        }).start();
    }

    private void render(List<Db.Call> calls, List<Tasks.Item> tasks) {
        content.removeAllViews();
        String formatted = Util.number(this, number);
        boolean named = !TextUtils.isEmpty(name);

        // Cabecera
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(0, dp(4), 0, dp(18));
        FrameLayout avf = new FrameLayout(this);
        TextView av = Ui.text(this, R.style.Text, "", 25, true);
        av.setGravity(Gravity.CENTER);
        av.setBackgroundResource(R.drawable.bg_avatar);
        avf.addView(av, new FrameLayout.LayoutParams(dp(76), dp(76)));
        ImageView avIcon = Ui.iconView(this, R.drawable.ic_tab_contacts, R.color.ink_2, 34);
        avf.addView(avIcon, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
        Ui.avatar(av, avIcon, named ? name : null);
        hero.addView(avf, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView n = Ui.text(this, R.style.Text, named ? name : formatted, 25, true);
        n.setLetterSpacing(-0.02f);
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, dp(12), 0, 0);
        hero.addView(n);
        if (named) {
            TextView num = Ui.text(this, R.style.Text_Meta, formatted);
            num.setTextSize(14);
            num.setPadding(0, dp(4), 0, 0);
            hero.addView(num);
        }
        int total = 0;
        for (Db.Call c : calls) total += Math.max(c.durationSec, 0);
        TextView stats = Ui.text(this, R.style.Text_Meta, calls.isEmpty() ? "Sin llamadas grabadas"
                : calls.size() + (calls.size() == 1 ? " llamada grabada" : " llamadas grabadas") + " · " + Util.duration(total)
                + " en total");
        stats.setTextSize(14);
        stats.setPadding(0, dp(2), 0, 0);
        hero.addView(stats);
        content.addView(hero);

        // Acciones
        LinearLayout actions = new LinearLayout(this);
        View callAction = action(R.drawable.ic_call_small, "Llamar", new View.OnClickListener() {
            @Override public void onClick(View v) { SimInfo.call(PersonActivity.this, number); }
        });
        callAction.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { return SimInfo.chooseOnce(PersonActivity.this, number); }
        });
        actions.addView(callAction, weight(false));
        actions.addView(action(R.drawable.ic_msg, "WhatsApp", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!QuickActions.openWhatsApp(PersonActivity.this, number, ""))
                    Toast.makeText(PersonActivity.this, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show();
            }
        }), weight(true));
        actions.addView(action(R.drawable.ic_alarm, "Programar", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ScheduleCall.show(PersonActivity.this, number, name, new Runnable() {
                    @Override public void run() { load(); }
                });
            }
        }), weight(true));
        content.addView(actions);

        // Llamadas programadas con esta persona
        for (final QuickActions.Reminder rem : QuickActions.pending(this)) {
            if (rem.number == null || !android.telephony.PhoneNumberUtils.compare(rem.number, number)) continue;
            LinearLayout row = Ui.card(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_note);
            row.setPadding(dp(16), dp(10), dp(6), dp(10));
            row.addView(Ui.iconView(this, R.drawable.ic_alarm, R.color.accent, 20));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = Ui.text(this, R.style.Text, rem.planned ? "Llamada programada" : "Llamada pospuesta", 13, true);
            t1.setTextColor(getColor(R.color.accent));
            col.addView(t1);
            col.addView(Ui.text(this, R.style.Text, ScheduleCall.dayLabel(rem.at) + ", " + Util.time(rem.at), 15, true));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.setMarginStart(dp(12));
            row.addView(col, cl);
            TextView cancel = Ui.textButton(this, "Cancelar");
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { QuickActions.cancel(PersonActivity.this, rem.id); load(); }
            });
            row.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
            content.addView(row, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
        }

        // Para la próxima llamada (de la última llamada analizada)
        for (Db.Call c : calls) {
            Summarizer.Result r = Summarizer.parse(c.summary);
            if (r == null) continue;
            if (!r.note.isEmpty()) {
                LinearLayout note = new LinearLayout(this);
                note.setBackgroundResource(R.drawable.bg_note);
                note.setPadding(dp(16), dp(16), dp(18), dp(16));
                note.addView(Ui.iconView(this, R.drawable.ic_hist, R.color.accent, 20));
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView nt = Ui.text(this, R.style.Text, "Para la próxima llamada · " + CallContext.ago(c.startMs), 13, true);
                nt.setTextColor(getColor(R.color.accent));
                col.addView(nt);
                TextView nb = Ui.text(this, R.style.Text, Character.toUpperCase(r.note.charAt(0)) + r.note.substring(1));
                nb.setTextSize(15);
                nb.setLineSpacing(dp(3), 1f);
                nb.setPadding(0, dp(4), 0, 0);
                col.addView(nb);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cl.setMarginStart(dp(12));
                note.addView(col, cl);
                final long noteCall = c.id;
                ImageView del = new ImageView(this);
                del.setImageResource(R.drawable.ic_x);
                del.setImageTintList(ColorStateList.valueOf(getColor(R.color.accent)));
                del.setBackgroundResource(R.drawable.bg_icon_btn);
                del.setScaleType(ImageView.ScaleType.CENTER);
                del.setContentDescription("Quitar esta nota");
                del.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        new android.app.AlertDialog.Builder(PersonActivity.this)
                                .setMessage("¿Quitar la nota «Para la próxima llamada»?")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Quitar", new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(android.content.DialogInterface d, int w) { removeNote(noteCall); }
                                }).show();
                    }
                });
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(36), dp(36));
                dl.topMargin = -dp(8);
                dl.setMarginEnd(-dp(8));
                note.addView(del, dl);
                content.addView(note, Ui.lpTop(this, Ui.MATCH, Ui.WRAP, 10));
            }
            break;
        }

        // Tareas pendientes
        content.addView(Ui.section(this, tasks.isEmpty() ? "Tareas pendientes" : "Tareas pendientes · " + tasks.size()));
        if (tasks.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.setPadding(dp(18), dp(16), dp(18), dp(16));
            TextView e = Ui.text(this, R.style.Text_Meta, "No tienes tareas pendientes con esta persona.");
            e.setTextSize(14.5f);
            empty.addView(e);
            content.addView(empty);
        } else {
            LinearLayout group = Ui.card(this);
            for (final Tasks.Item it : tasks) {
                if (group.getChildCount() > 0) group.addView(Ui.separator(this, 56));
                group.addView(taskRow(it));
            }
            content.addView(group);
        }

        // Llamadas
        content.addView(Ui.section(this, "Llamadas"));
        if (calls.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.setPadding(dp(18), dp(16), dp(18), dp(16));
            TextView e = Ui.text(this, R.style.Text_Meta, "Aún no hay llamadas grabadas con esta persona.");
            e.setTextSize(14.5f);
            empty.addView(e);
            content.addView(empty);
        }
        for (final Db.Call c : calls) content.addView(callCard(c));
    }

    private void removeNote(final long callId) {
        new Thread(new Runnable() {
            @Override public void run() {
                Db.Call call = Db.get(PersonActivity.this).byId(callId);
                Summarizer.Result r = call == null ? null : Summarizer.parse(call.summary);
                if (r == null) return;
                r.note = "";
                Db.get(PersonActivity.this).updateSummary(callId, r.toJson());
                sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(getPackageName()));
            }
        }).start();
    }

    private LinearLayout.LayoutParams weight(boolean gap) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(72), 1f);
        if (gap) l.setMarginStart(dp(10));
        return l;
    }

    private View action(int icon, String label, View.OnClickListener l) {
        TextView t = Ui.text(this, R.style.Text, label, 13, true);
        t.setTextColor(getColor(R.color.accent));
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_card_click);
        android.graphics.drawable.Drawable d = getDrawable(icon).mutate();
        d.setTint(getColor(R.color.accent));
        d.setBounds(0, 0, dp(20), dp(20));
        t.setCompoundDrawablesRelative(null, d, null, null);
        t.setCompoundDrawablePadding(dp(6));
        t.setPadding(0, dp(12), 0, 0);
        t.setOnClickListener(l);
        return t;
    }

    private View taskRow(final Tasks.Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackgroundResource(R.drawable.bg_tbtn);
        ImageView cb = new ImageView(this);
        cb.setBackgroundResource(R.drawable.bg_cbx);
        cb.setImageResource(R.drawable.ic_check);
        cb.setImageTintList(ColorStateList.valueOf(getColor(R.color.on_accent)));
        cb.setImageAlpha(0);
        cb.setPadding(dp(4), dp(4), dp(4), dp(4));
        cb.setContentDescription("Marcar como hecha");
        cb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.setActivated(true);
                ((ImageView) v).setImageAlpha(255);
                new Thread(new Runnable() {
                    @Override public void run() { Tasks.setDone(PersonActivity.this, it.callId, it.index, true); }
                }).start();
            }
        });
        LinearLayout.LayoutParams cbl = new LinearLayout.LayoutParams(dp(26), dp(26));
        cbl.topMargin = dp(1);
        row.addView(cb, cbl);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, R.style.Text, it.task.title, 15, true);
        t.setLineSpacing(dp(2), 1f);
        col.addView(t);
        StringBuilder m = new StringBuilder();
        if (!it.task.owner.isEmpty()) m.append(it.task.owner).append(" · ");
        m.append(Tasks.when(it.task)).append(" · llamada del ").append(Util.dayHeader(it.callMs).toLowerCase(Util.ES));
        TextView meta = Ui.text(this, R.style.Text_Meta, m.toString());
        meta.setPadding(0, dp(3), 0, 0);
        col.addView(meta);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMarginStart(dp(14));
        row.addView(col, l);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openCall(it.callId); }
        });
        return row;
    }

    private View callCard(final Db.Call c) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_click);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.iconView(this, c.direction == 1 ? R.drawable.ic_in : R.drawable.ic_out, R.color.ink_2, 14));
        TextView when = Ui.text(this, R.style.Text, Util.dayHeader(c.startMs) + ", " + Util.time(c.startMs), 15, true);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wl.setMarginStart(dp(8));
        head.addView(when, wl);
        TextView dur = Ui.text(this, R.style.Text_Meta, Util.clock(c.durationSec));
        dur.setTypeface(Ui.bold());
        dur.setTextColor(getColor(R.color.ink));
        head.addView(dur);
        card.addView(head);
        Summarizer.Result r = Summarizer.parse(c.summary);
        String summary = r != null ? r.summary : (c.summary != null ? c.summary : "");
        if (!TextUtils.isEmpty(summary)) {
            TextView s = Ui.text(this, R.style.Text, summary);
            s.setTextSize(14.5f);
            s.setLineSpacing(dp(3), 1f);
            s.setMaxLines(3);
            s.setEllipsize(TextUtils.TruncateAt.END);
            s.setPadding(0, dp(10), 0, 0);
            card.addView(s);
        } else {
            TextView s = Ui.text(this, R.style.Text_Meta, AutoAnalyzer.isRunning(c.id) ? "Analizando…" : "Sin analizar");
            s.setPadding(0, dp(8), 0, 0);
            card.addView(s);
        }
        int pend = Summarizer.pendingTasks(c.summary);
        if (pend > 0 || !TextUtils.isEmpty(c.marks)) {
            LinearLayout chips = new LinearLayout(this);
            chips.setPadding(0, dp(10), 0, 0);
            if (pend > 0) {
                TextView ch = Ui.chip(this, pend + (pend == 1 ? " tarea pendiente" : " tareas pendientes"), true);
                Ui.icon(ch, R.drawable.ic_check, R.color.accent, 14);
                chips.addView(ch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
            }
            if (!TextUtils.isEmpty(c.marks)) {
                int nm = c.marks.split(",").length;
                TextView ch = Ui.chip(this, nm + (nm == 1 ? " momento marcado" : " momentos marcados"), false);
                Ui.icon(ch, R.drawable.ic_bookmark, R.color.ink_2, 13);
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
                if (chips.getChildCount() > 0) l.setMarginStart(dp(6));
                chips.addView(ch, l);
            }
            card.addView(chips);
        }
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openCall(c.id); }
        });
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.bottomMargin = dp(10);
        card.setLayoutParams(l);
        return card;
    }

    private void openCall(long id) {
        startActivity(new Intent(this, DetailActivity.class).putExtra("id", id));
    }
}
