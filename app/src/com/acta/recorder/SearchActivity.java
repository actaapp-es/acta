package com.acta.recorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Buscador de conversaciones: encuentra lo que se dijo y salta al momento exacto. */
public class SearchActivity extends Activity {

    private EditText field;
    private LinearLayout results;
    private View root;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile List<Db.Call> calls;
    private int seq;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (AppLock.enabled(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(getColor(R.color.bg));
        root = r;

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(10), dp(16), dp(8));
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

        field = new EditText(this);
        field.setHint("Buscar en lo que se habló");
        field.setTextColor(getColor(R.color.ink));
        field.setHintTextColor(getColor(R.color.ink_2));
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setBackgroundResource(R.drawable.bg_field);
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setCompoundDrawablePadding(dp(10));
        android.graphics.drawable.Drawable ic = getDrawable(R.drawable.ic_search).mutate();
        ic.setTint(getColor(R.color.ink_2));
        ic.setBounds(0, 0, dp(20), dp(20));
        field.setCompoundDrawablesRelative(ic, null, null, null);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, dp(48), 1f);
        fl.setMarginStart(dp(4));
        top.addView(field, fl);
        r.addView(top);

        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(dp(16), dp(4), dp(16), dp(28));
        scroll.addView(results);
        r.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(r);

        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { schedule(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        String q0 = getIntent().getStringExtra("q");
        if (!TextUtils.isEmpty(q0)) field.setText(q0);
        showHint();
        new Thread(new Runnable() {
            @Override public void run() {
                calls = Db.get(SearchActivity.this).all();
                runOnUiThread(new Runnable() { @Override public void run() { schedule(); } });
            }
        }).start();
        field.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
    }

    private void schedule() {
        h.removeCallbacks(run);
        h.postDelayed(run, 220);
    }

    private final Runnable run = new Runnable() {
        @Override public void run() {
            final String q = field.getText().toString().trim();
            final List<Db.Call> data = calls;
            if (q.length() < 2) { showHint(); return; }
            if (data == null) return;
            final int my = ++seq;
            new Thread(new Runnable() {
                @Override public void run() {
                    final List<ConvSearch.Group> res = ConvSearch.search(data, q);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { if (my == seq) show(q, res); }
                    });
                }
            }).start();
        }
    };

    private void showHint() {
        results.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(56), dp(24), 0);
        FrameLayout c = new FrameLayout(this);
        c.setBackgroundResource(R.drawable.bg_circle_soft);
        c.addView(Ui.iconView(this, R.drawable.ic_search, R.color.accent, 30), new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        col.addView(c, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView t = Ui.text(this, R.style.Text, "Busca en tus conversaciones", 19, true);
        t.setPadding(0, dp(20), 0, 0);
        col.addView(t);
        TextView s = Ui.text(this, R.style.Text_Meta, "Escribe una palabra que se dijo en la llamada, como «presupuesto» o «viernes», y salta al momento exacto.");
        s.setTextSize(14.5f);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(dp(4), 1f);
        s.setPadding(0, dp(8), 0, 0);
        col.addView(s);
        results.addView(col);
    }

    private void show(String q, List<ConvSearch.Group> res) {
        results.removeAllViews();
        int hits = 0;
        for (ConvSearch.Group g : res) hits += Math.max(g.total, 1);
        TextView count = Ui.text(this, R.style.Text_Section, res.isEmpty() ? "Sin resultados para «" + q + "»"
                : hits + (hits == 1 ? " resultado" : " resultados") + " en " + res.size()
                + (res.size() == 1 ? " llamada" : " llamadas"));
        count.setPadding(dp(10), dp(12), dp(10), dp(8));
        results.addView(count);
        if (res.isEmpty()) {
            TextView s = Ui.text(this, R.style.Text_Meta, "Solo se puede buscar en las llamadas que ya están analizadas con IA.");
            s.setPadding(dp(10), 0, dp(10), 0);
            results.addView(s);
            return;
        }
        for (ConvSearch.Group g : res) results.addView(card(q, g));
    }

    private View card(final String q, final ConvSearch.Group g) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.bottomMargin = dp(10);
        card.setLayoutParams(cl);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(14), dp(16), dp(10));
        head.setBackgroundResource(R.drawable.bg_tbtn);
        FrameLayout avf = new FrameLayout(this);
        TextView av = Ui.text(this, R.style.Text, "", 13, true);
        av.setGravity(Gravity.CENTER);
        av.setBackgroundResource(R.drawable.bg_avatar);
        avf.addView(av, new FrameLayout.LayoutParams(dp(36), dp(36)));
        ImageView avIcon = Ui.iconView(this, R.drawable.ic_tab_contacts, R.color.ink_2, 18);
        avf.addView(avIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        Ui.avatar(av, avIcon, g.name);
        head.addView(avf, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        String who = !TextUtils.isEmpty(g.name) ? g.name : TextUtils.isEmpty(g.number) ? "Número oculto" : Util.number(this, g.number);
        col.addView(Ui.text(this, R.style.Text, highlight(who, q), 15.5f, true));
        TextView when = Ui.text(this, R.style.Text_Meta, Util.dayHeader(g.startMs) + ", " + Util.time(g.startMs) + " · " + Util.clock(g.durationSec));
        when.setPadding(0, dp(2), 0, 0);
        col.addView(when);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMarginStart(dp(12));
        head.addView(col, l);
        head.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(g.callId, -1, q); }
        });
        card.addView(head);

        for (final ConvSearch.Hit hit : g.hits) {
            card.addView(Ui.separator(this, 16));
            LinearLayout row = new LinearLayout(this);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setBackgroundResource(R.drawable.bg_tbtn);
            TextView chip = Ui.chip(this, hit.t >= 0 ? (hit.approx ? "≈ " : "") + ConvSearch.clock(hit.t) : hit.where, hit.t >= 0);
            if (hit.t >= 0) Ui.icon(chip, R.drawable.ic_play, R.color.accent, 11);
            row.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
            TextView sn = Ui.text(this, R.style.Text, snippet(hit));
            sn.setTextSize(14.5f);
            sn.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            sl.setMarginStart(dp(12));
            sl.topMargin = dp(3);
            row.addView(sn, sl);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { open(g.callId, hit.t, q); }
            });
            card.addView(row);
        }
        int more = g.total - g.hits.size();
        if (more > 0) {
            TextView m = Ui.text(this, R.style.Text_Meta, "+" + more + " más en esta llamada");
            m.setPadding(dp(16), 0, dp(16), dp(12));
            card.addView(m);
        }
        return card;
    }

    private CharSequence snippet(ConvSearch.Hit hit) {
        SpannableString sp = new SpannableString(hit.snippet);
        if (hit.start >= 0 && hit.len > 0 && hit.start + hit.len <= hit.snippet.length()) mark(sp, hit.start, hit.len);
        return sp;
    }

    private CharSequence highlight(String text, String q) {
        SpannableString sp = new SpannableString(text);
        int i = ConvSearch.fold(text).indexOf(ConvSearch.fold(q).trim());
        if (i >= 0) mark(sp, i, q.trim().length());
        return sp;
    }

    private void mark(SpannableString sp, int start, int len) {
        int end = Math.min(sp.length(), start + len);
        sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new ForegroundColorSpan(getColor(R.color.accent)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new BackgroundColorSpan(getColor(R.color.accent_soft)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void open(long id, double t, String q) {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        Intent i = new Intent(this, DetailActivity.class).putExtra("id", id).putExtra("q", q);
        if (t >= 0) i.putExtra("seek", t);
        startActivity(i);
    }

}
