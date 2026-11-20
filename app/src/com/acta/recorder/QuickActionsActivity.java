package com.acta.recorder;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Hoja de acciones rápidas para una llamada entrante, sin descolgar:
 * responder por WhatsApp con un mensaje predefinido o posponerla con recordatorio.
 * Se abre desde la isla/notificación de llamada o desde la pantalla de llamada.
 */
public class QuickActionsActivity extends Activity implements Ui.InsetsAware {

    private FrameLayout rootView;
    private LinearLayout sheetView;

    @Override public void onInsets(int left, int top, int right, int bottom) {
        if (rootView != null) rootView.setPadding(left, top, right, 0);
        if (sheetView != null) sheetView.setPadding(dp(16), dp(10), dp(16), dp(18) + bottom);
    }

    static final String EXTRA_NUMBER = "number";
    static final String EXTRA_MODE = "mode";   // "msg", "later" o null (ambas)

    private String number, name;

    static Intent intent(Context c, String number, String mode) {
        return new Intent(c, QuickActionsActivity.class)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        number = getIntent().getStringExtra(EXTRA_NUMBER);
        name = Util.contactName(this, number);
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        boolean showMsg = mode == null || "msg".equals(mode);
        boolean showLater = mode == null || "later".equals(mode);

        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        ScrollView scroll = new ScrollView(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundResource(R.drawable.bg_sheet);
        sheet.setPadding(dp(16), dp(10), dp(16), dp(18));
        rootView = root;
        sheetView = sheet;
        View handle = new View(this);
        handle.setBackgroundResource(R.drawable.bg_handle);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(36), dp(4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(14);
        sheet.addView(handle, hl);
        sheet.setClickable(true);
        scroll.addView(sheet);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        slp.setMargins(0, dp(40), 0, 0);
        root.addView(scroll, slp);

        String who = !TextUtils.isEmpty(name) ? name
                : TextUtils.isEmpty(number) ? "Número oculto" : Util.number(this, number);
        TextView title = new TextView(this, null, 0, R.style.Text);
        title.setText(who);
        title.setTextSize(19);
        title.setTypeface(Ui.bold());
        title.setPadding(dp(8), 0, dp(8), 0);
        sheet.addView(title);
        TextView sub = new TextView(this, null, 0, R.style.Text_Meta);
        sub.setText("Sin descolgar: la llamada se rechaza al elegir una opción");
        sub.setPadding(dp(8), dp(4), dp(8), dp(4));
        sheet.addView(sub);

        boolean hasNumber = !TextUtils.isEmpty(number);

        if (showMsg) {
            sheet.addView(section("Responder por WhatsApp"));
            if (!hasNumber || QuickActions.internationalDigits(this, number) == null) {
                sheet.addView(note("Este número no se puede usar en WhatsApp."));
            } else {
                if (QuickActions.whatsappPackage(this) == null)
                    sheet.addView(note("WhatsApp no parece instalado; se abrirá en el navegador."));
                LinearLayout card = Ui.card(this);
                for (final String m : QuickActions.messages(this)) {
                    if (card.getChildCount() > 0) card.addView(Ui.separator(this, 18));
                    card.addView(item(m, new View.OnClickListener() {
                        @Override public void onClick(View v) { sendWhatsApp(m); }
                    }));
                }
                card.addView(Ui.separator(this, 18));
                View other = item("Escribir otro mensaje…", new View.OnClickListener() {
                    @Override public void onClick(View v) { sendWhatsApp(""); }
                });
                ((TextView) other).setTextColor(getColor(R.color.accent));
                card.addView(other);
                sheet.addView(card);
            }
        }

        if (showLater) {
            sheet.addView(section("Recordarme llamar en"));
            if (!hasNumber) {
                sheet.addView(note("Con número oculto no se puede devolver la llamada."));
            } else {
                LinearLayout chips = new LinearLayout(this);
                chips.setOrientation(LinearLayout.HORIZONTAL);
                for (final int min : QuickActions.POSTPONE_MINUTES) {
                    TextView chip = new TextView(this, null, 0, R.style.Text);
                    chip.setText(QuickActions.minutesLabel(min));
                    chip.setTextSize(13.5f);
                    chip.setTypeface(Ui.bold());
                    chip.setSingleLine(true);
                    chip.setBackgroundResource(R.drawable.bg_chip);
                    chip.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
                    lp.setMargins(dp(3), dp(2), dp(3), dp(2));
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { postpone(min); }
                    });
                    chips.addView(chip, lp);
                }
                sheet.addView(chips);
            }
        }

        TextView cancel = Ui.button(this, "Cancelar", false);
        cancel.setTextColor(getColor(R.color.ink));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        clp.topMargin = dp(16);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        sheet.addView(cancel, clp);

        setContentView(root);
    }

    private TextView section(String s) {
        TextView t = new TextView(this, null, 0, R.style.Text_Section);
        t.setText(s);
        t.setPadding(dp(10), dp(20), dp(10), dp(8));
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this, null, 0, R.style.Text_Meta);
        t.setText(s);
        t.setPadding(dp(10), dp(2), dp(10), dp(6));
        return t;
    }

    private View item(String text, View.OnClickListener l) {
        TextView t = new TextView(this, null, 0, R.style.Text);
        t.setText(text);
        t.setTextSize(15.5f);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(54));
        t.setPadding(dp(18), 0, dp(16), 0);
        Ui.icon(t, R.drawable.ic_send, R.color.accent, 18);
        t.setCompoundDrawablesRelative(null, null, t.getCompoundDrawablesRelative()[0], null);
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
            t.setBackgroundResource(tv.resourceId);
        t.setOnClickListener(l);
        return t;
    }

    private void sendWhatsApp(final String text) {
        QuickActions.rejectRinging(number);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            // Pide desbloquear y, al hacerlo, abre WhatsApp.
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { openWa(text); }
                @Override public void onDismissCancelled() { finish(); }
                @Override public void onDismissError() { openWa(text); }
            });
        } else {
            openWa(text);
        }
    }

    private void openWa(String text) {
        if (!QuickActions.openWhatsApp(this, number, text))
            Toast.makeText(this, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show();
        finish();
    }

    private void postpone(int minutes) {
        QuickActions.rejectRinging(number);
        long at = QuickActions.postpone(this, number, name, minutes);
        String who = !TextUtils.isEmpty(name) ? name : Util.number(this, number);
        String when = Util.time(at) + (QuickActions.exactAlarmsAllowed(this) ? "" : " aprox.");
        Toast.makeText(this, "Te avisaré a las " + when + " para llamar a " + who, Toast.LENGTH_LONG).show();
        finish();
    }
}
