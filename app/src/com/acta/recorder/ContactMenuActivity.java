package com.acta.recorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Desplegable que sale al pulsar un contacto de un widget: llamar, WhatsApp o ver su historial. */
public class ContactMenuActivity extends Activity implements Ui.InsetsAware {

    static final String EXTRA_NUMBER = "number", EXTRA_NAME = "name";

    private FrameLayout rootView;
    private LinearLayout sheetView;
    private String number, name;

    static Intent intent(Context c, String number, String name) {
        return new Intent(c, ContactMenuActivity.class)
                .putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    @Override public void onInsets(int left, int top, int right, int bottom) {
        if (rootView != null) rootView.setPadding(left, top, right, 0);
        if (sheetView != null) sheetView.setPadding(dp(16), dp(10), dp(16), dp(18) + bottom);
    }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        number = getIntent().getStringExtra(EXTRA_NUMBER);
        name = getIntent().getStringExtra(EXTRA_NAME);
        if (TextUtils.isEmpty(number)) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundResource(R.drawable.bg_sheet);
        sheet.setPadding(dp(16), dp(10), dp(16), dp(18));
        sheet.setClickable(true);
        rootView = root;
        sheetView = sheet;
        View handle = new View(this);
        handle.setBackgroundResource(R.drawable.bg_handle);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(36), dp(4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(14);
        sheet.addView(handle, hl);

        String who = TextUtils.isEmpty(name) ? Util.number(this, number) : name;
        TextView title = new TextView(this, null, 0, R.style.Text);
        title.setText(who);
        title.setTextSize(19);
        title.setTypeface(Ui.bold());
        title.setPadding(dp(8), 0, dp(8), 0);
        sheet.addView(title);
        if (!TextUtils.isEmpty(name)) {
            TextView sub = new TextView(this, null, 0, R.style.Text_Meta);
            sub.setText(Util.number(this, number));
            sub.setPadding(dp(8), dp(2), dp(8), 0);
            sheet.addView(sub);
        }

        LinearLayout card = Ui.card(this);
        card.addView(item("Llamar", R.drawable.ic_call, new View.OnClickListener() {
            @Override public void onClick(View v) { call(); }
        }));
        card.addView(Ui.separator(this, 18));
        card.addView(item("Enviar WhatsApp", R.drawable.ic_msg, new View.OnClickListener() {
            @Override public void onClick(View v) { whatsapp(); }
        }));
        card.addView(Ui.separator(this, 18));
        card.addView(item("Ver historial", R.drawable.ic_hist, new View.OnClickListener() {
            @Override public void onClick(View v) { history(); }
        }));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = dp(14);
        sheet.addView(card, cl);

        root.addView(sheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        setContentView(root);
    }

    private View item(String text, int icon, View.OnClickListener l) {
        TextView t = new TextView(this, null, 0, R.style.Text);
        t.setText(text);
        t.setTextSize(15.5f);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(56));
        t.setPadding(dp(18), 0, dp(16), 0);
        t.setCompoundDrawablePadding(dp(16));
        Ui.icon(t, icon, R.color.accent, 22);
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
            t.setBackgroundResource(tv.resourceId);
        t.setOnClickListener(l);
        return t;
    }

    private void call() {
        try {
            SimInfo.Sim p = SimInfo.dual(this) ? SimInfo.preferred(this, number) : null;
            // Sin SIM recordada, la pantalla de llamada de Acta preguntará con cuál.
            Util.placeCall(this, number, p != null ? p.handle : null);
        } catch (Throwable ignored) { }
        finish();
    }

    private void whatsapp() {
        if (QuickActions.openWhatsApp(this, number, "")) finish();
        else Toast.makeText(this, "Este número no se puede usar en WhatsApp.", Toast.LENGTH_SHORT).show();
    }

    private void history() {
        startActivity(new Intent(this, PersonActivity.class).putExtra("number", number).putExtra("name", name));
        finish();
    }
}
