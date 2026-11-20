package com.acta.recorder;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Elegir los contactos que aparecen en el widget. */
public class ContactsWidgetConfig extends Activity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final List<String[]> all = new ArrayList<>();          // {nombre, número}
    private final Map<String, String[]> chosen = new LinkedHashMap<>(); // número → {nombre, número}
    private LinearLayout list;
    private EditText search;
    private TextView save;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        for (String[] e : ContactsWidget.load(this, widgetId)) chosen.put(e[1], e);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        root.setPadding(0, dp(12), 0, 0);
        TextView title = Ui.text(this, R.style.Text_Title, "Contactos del widget");
        title.setTextSize(26);
        title.setPadding(dp(24), dp(8), dp(24), 0);
        root.addView(title);
        TextView sub = Ui.text(this, R.style.Text_Meta, "Elige hasta " + ContactsWidget.MAX + ". Un toque en el widget y llamas.");
        sub.setTextSize(14.5f);
        sub.setPadding(dp(24), dp(6), dp(24), dp(12));
        root.addView(sub);

        search = new EditText(this);
        search.setHint("Buscar contacto");
        search.setTextColor(getColor(R.color.ink));
        search.setHintTextColor(getColor(R.color.ink_2));
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setBackgroundResource(R.drawable.bg_field);
        search.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        sl.setMargins(dp(16), 0, dp(16), dp(6));
        root.addView(search, sl);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        save = Ui.button(this, "", true);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.setMargins(dp(24), dp(8), dp(24), dp(20));
        root.addView(save, bl);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { done(); }
        });
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { render(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        if (!Util.has(this, Manifest.permission.READ_CONTACTS)) {
            Toast.makeText(this, "Acta necesita permiso para leer tus contactos.", Toast.LENGTH_LONG).show();
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 1);
        } else {
            loadContacts();
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (Util.has(this, Manifest.permission.READ_CONTACTS)) loadContacts();
    }

    private void loadContacts() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String[]> out = new ArrayList<>();
                Cursor c = null;
                try {
                    c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID},
                            null, null, ContactsContract.CommonDataKinds.Phone.STARRED + " DESC, "
                                    + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC");
                    java.util.Set<Long> seen = new java.util.HashSet<>();
                    while (c != null && c.moveToNext()) {
                        String n = c.getString(0), num = c.getString(1);
                        if (TextUtils.isEmpty(num) || !seen.add(c.getLong(2))) continue;
                        out.add(new String[]{n == null ? "" : n, num});
                    }
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.close();
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() { all.clear(); all.addAll(out); render(); }
                });
            }
        }).start();
    }

    private void render() {
        list.removeAllViews();
        int n = chosen.size();
        save.setText(n == 0 ? "Guardar" : "Guardar (" + n + ")");
        String q = ConvSearch.fold(search.getText().toString().trim());
        LinearLayout card = Ui.card(this);
        int shown = 0;
        for (final String[] e : all) {
            if (!q.isEmpty() && !ConvSearch.fold(e[0]).contains(q) && !e[1].replaceAll("[^0-9]", "").contains(q.replaceAll("[^0-9]", "x"))) continue;
            if (shown++ >= 300) break;
            if (card.getChildCount() > 0) card.addView(Ui.separator(this, 70));
            final boolean on = chosen.containsKey(e[1]);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(16), dp(10));
            row.setMinimumHeight(dp(62));
            row.setBackgroundResource(R.drawable.bg_tbtn);
            FrameLayout avf = new FrameLayout(this);
            TextView av = Ui.text(this, R.style.Text, "", 15, true);
            av.setGravity(Gravity.CENTER);
            av.setBackgroundResource(R.drawable.bg_avatar);
            avf.addView(av, new FrameLayout.LayoutParams(dp(42), dp(42)));
            ImageView ic = Ui.iconView(this, R.drawable.ic_tab_contacts, R.color.ink_2, 20);
            avf.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            Ui.avatar(av, ic, e[0]);
            row.addView(avf, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(Ui.text(this, R.style.Text, TextUtils.isEmpty(e[0]) ? Util.number(this, e[1]) : e[0], 15.5f, true));
            col.addView(Ui.text(this, R.style.Text_Meta, Util.number(this, e[1])));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.setMarginStart(dp(14));
            row.addView(col, cl);
            ImageView cb = new ImageView(this);
            cb.setBackgroundResource(R.drawable.bg_cbx);
            cb.setImageResource(R.drawable.ic_check);
            cb.setImageTintList(ColorStateList.valueOf(getColor(R.color.on_accent)));
            cb.setPadding(dp(4), dp(4), dp(4), dp(4));
            cb.setActivated(on);
            cb.setImageAlpha(on ? 255 : 0);
            row.addView(cb, new LinearLayout.LayoutParams(dp(26), dp(26)));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (chosen.containsKey(e[1])) chosen.remove(e[1]);
                    else if (chosen.size() >= ContactsWidget.MAX) {
                        Toast.makeText(ContactsWidgetConfig.this, "Máximo " + ContactsWidget.MAX + " contactos.", Toast.LENGTH_SHORT).show();
                        return;
                    } else chosen.put(e[1], e);
                    render();
                }
            });
            card.addView(row);
        }
        if (card.getChildCount() == 0) {
            TextView t = Ui.text(this, R.style.Text_Meta, all.isEmpty() ? "No hay contactos con número." : "Sin resultados.");
            t.setPadding(dp(18), dp(18), dp(18), dp(18));
            card.addView(t);
        }
        list.addView(card);
    }

    private void done() {
        ContactsWidget.save(this, widgetId, new ArrayList<>(chosen.values()));
        ContactsWidget.update(this, AppWidgetManager.getInstance(this), widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }
}
