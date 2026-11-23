package com.acta.recorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Llamadas perdidas sin devolver: llamar, escribir por WhatsApp, programar o descartar. */
public class MissedCallsActivity extends Activity {

    private LinearLayout list;
    private TextView clear;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(24), dp(20), dp(12), dp(8));
        TextView title = new TextView(this, null, 0, R.style.Text_Title);
        title.setText("Llamadas perdidas");
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clear = new TextView(this, null, 0, R.style.Text_Meta);
        clear.setText("Descartar todas");
        clear.setTextColor(getColor(R.color.danger));
        clear.setPadding(dp(12), dp(12), dp(12), dp(12));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (MissedCalls.Item it : MissedCalls.pending(MissedCallsActivity.this)) MissedCalls.dismiss(MissedCallsActivity.this, it);
                render();
            }
        });
        header.addView(clear);
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(24));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        final List<MissedCalls.Item> items = MissedCalls.pending(this);
        list.removeAllViews();
        clear.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        if (items.isEmpty()) {
            TextView t = Ui.text(this, R.style.Text_Meta, "No tienes llamadas perdidas sin devolver. Aquí aparecerán las de los últimos 14 días hasta que las devuelvas.");
            t.setTextSize(15);
            t.setLineSpacing(dp(2), 1);
            t.setPadding(dp(8), dp(24), dp(8), 0);
            list.addView(t);
            return;
        }
        for (final MissedCalls.Item it : items) {
            LinearLayout card = Ui.card(this);
            card.setPadding(dp(16), dp(14), dp(16), dp(10));
            String who = !TextUtils.isEmpty(it.name) ? it.name : Util.number(this, it.number);
            TextView name = Ui.text(this, R.style.Text, who, 17, true);
            name.setTextColor(getColor(R.color.danger));
            card.addView(name);
            String sub = (it.count == 1 ? "1 llamada perdida" : it.count + " llamadas perdidas") + " · "
                    + Util.dateFull(it.last) + ", " + Util.time(it.last);
            TextView s = Ui.text(this, R.style.Text_Meta, sub);
            s.setTextSize(13.5f);
            s.setPadding(0, dp(2), 0, 0);
            card.addView(s);
            if (!TextUtils.isEmpty(it.name)) {
                TextView num = Ui.text(this, R.style.Text_Meta, Util.number(this, it.number));
                num.setTextSize(13.5f);
                card.addView(num);
            }
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(0, dp(8), 0, 0);
            actions.addView(button("Llamar", new View.OnClickListener() {
                @Override public void onClick(View v) { SimInfo.call(MissedCallsActivity.this, it.number); }
            }));
            actions.addView(button("WhatsApp", new View.OnClickListener() {
                @Override public void onClick(View v) { QuickActions.openWhatsApp(MissedCallsActivity.this, it.number, ""); }
            }));
            actions.addView(button("Programar", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ScheduleCall.show(MissedCallsActivity.this, it.number, it.name, new Runnable() {
                        @Override public void run() { MissedCalls.dismiss(MissedCallsActivity.this, it); render(); }
                    });
                }
            }));
            View sp = new View(this);
            actions.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
            TextView drop = Ui.textButton(this, "Descartar");
            drop.setTextColor(getColor(R.color.ink_2));
            drop.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { MissedCalls.dismiss(MissedCallsActivity.this, it); render(); }
            });
            actions.addView(drop);
            card.addView(actions);
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { startActivity(ContactMenuActivity.intent(MissedCallsActivity.this, it.number, it.name)); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            list.addView(card, lp);
        }
    }

    private TextView button(String label, View.OnClickListener l) {
        TextView t = Ui.textButton(this, label);
        t.setOnClickListener(l);
        return t;
    }
}
