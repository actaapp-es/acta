package com.acta.recorder;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Widget con contactos elegidos para llamar con un toque. */
public class ContactsWidget extends AppWidgetProvider {

    static final int MAX = 8;
    private static final int[] AV_BG = {R.drawable.bg_av0, R.drawable.bg_av1, R.drawable.bg_av2,
            R.drawable.bg_av3, R.drawable.bg_av4, R.drawable.bg_av5};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        update(c, m, id);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Util.prefs(c).edit().remove("wc_" + id).apply();
    }

    static List<String[]> load(Context c, int id) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Util.prefs(c).getString("wc_" + id, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.optString("n", ""), o.optString("p", "")});
            }
        } catch (Throwable ignored) { }
        return out;
    }

    static void save(Context c, int id, List<String[]> list) {
        JSONArray a = new JSONArray();
        try {
            for (String[] e : list) a.put(new JSONObject().put("n", e[0]).put("p", e[1]));
        } catch (Throwable ignored) { }
        Util.prefs(c).edit().putString("wc_" + id, a.toString()).apply();
    }

    static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_contacts);
        List<String[]> list = load(c, id);
        v.removeAllViews(R.id.wc_rows);
        PendingIntent config = PendingIntent.getActivity(c, 7300 + id,
                new Intent(c, ContactsWidgetConfig.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (list.isEmpty()) {
            v.setViewVisibility(R.id.wc_empty, android.view.View.VISIBLE);
            v.setOnClickPendingIntent(R.id.wc_root, config);
            m.updateAppWidget(id, v);
            return;
        }
        v.setViewVisibility(R.id.wc_empty, android.view.View.GONE);

        // Tamaño real del widget (dp): ancho mínimo y alto máximo en vertical.
        int w = 0, h = 0;
        try {
            Bundle o = m.getAppWidgetOptions(id);
            w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        } catch (Throwable ignored) { }
        if (w <= 0) w = 250;
        if (h <= 0) h = 80;
        int availW = Math.max(60, w - 16), availH = Math.max(50, h - 12);
        int n = Math.min(list.size(), MAX);
        int maxCols = Math.max(1, Math.min(n, availW / 52));
        int rowsFit = Math.max(1, availH / 60);
        int rows = Math.min((n + maxCols - 1) / maxCols, rowsFit);
        int shown = Math.min(n, rows * maxCols);
        int cols = Math.max(1, (shown + rows - 1) / rows); // reparte por igual entre las filas
        int perRow = availH / rows, perCol = availW / cols;
        // Tres tamaños de círculo + nombre, según el hueco de cada contacto.
        int cellLayout = perRow >= 92 && perCol >= 72 ? R.layout.widget_contact_cell_large
                : perRow >= 72 && perCol >= 56 ? R.layout.widget_contact_cell : R.layout.widget_contact_cell_small;

        RemoteViews row = null;
        for (int i = 0; i < shown; i++) {
            if (i % cols == 0) {
                if (row != null) v.addView(R.id.wc_rows, row);
                row = new RemoteViews(c.getPackageName(), R.layout.widget_contacts_row);
            }
            String[] e = list.get(i);
            String name = TextUtils.isEmpty(e[0]) ? Util.number(c, e[1]) : e[0];
            RemoteViews cell = new RemoteViews(c.getPackageName(), cellLayout);
            int slot = Ui.avatarSlot(e[0]);
            String ini = Ui.initials(e[0]);
            cell.setTextViewText(R.id.cell_avatar, ini != null ? ini : "#");
            cell.setInt(R.id.cell_avatar, "setBackgroundResource", AV_BG[slot]);
            cell.setTextColor(R.id.cell_avatar, Ui.avatarFg(c, slot));
            cell.setTextViewText(R.id.cell_name, firstName(name));
            Intent call = new Intent(c, WidgetActionActivity.class)
                    .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.CALL)
                    .putExtra("number", e[1]);
            cell.setOnClickPendingIntent(R.id.cell, PendingIntent.getActivity(c, 7400 + id * 16 + i, call,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            cell.setContentDescription(R.id.cell, "Llamar a " + name);
            row.addView(R.id.wc_row, cell);
        }
        // Rellena la última fila para que todas las celdas tengan el mismo ancho.
        int rem = shown % cols;
        if (rem != 0 && rows > 1) for (int k = rem; k < cols; k++)
            row.addView(R.id.wc_row, new RemoteViews(c.getPackageName(), R.layout.widget_contact_cell_empty));
        if (row != null) v.addView(R.id.wc_rows, row);
        m.updateAppWidget(id, v);
    }

    private static String firstName(String n) {
        if (n == null) return "";
        String t = n.trim();
        if (t.isEmpty() || !Character.isLetter(t.codePointAt(0))) return t;
        int sp = t.indexOf(' ');
        return sp > 0 && t.length() > 12 ? t.substring(0, sp) : t;
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<?> k : new Class<?>[]{ContactsWidget.class, ContactsWidgetLarge.class}) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, k));
            if (ids != null) for (int id : ids) update(c, m, id);
        }
    }
}
