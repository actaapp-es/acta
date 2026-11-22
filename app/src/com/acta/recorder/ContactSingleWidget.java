package com.acta.recorder;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Widget de 1×1: un contacto; al pulsarlo se abre el desplegable (llamar, WhatsApp, historial). */
public class ContactSingleWidget extends AppWidgetProvider {

    private static final int[] AV_BG = {R.drawable.bg_av0, R.drawable.bg_av1, R.drawable.bg_av2,
            R.drawable.bg_av3, R.drawable.bg_av4, R.drawable.bg_av5};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Util.prefs(c).edit().remove("wc_" + id).apply();
    }

    static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_contact_single);
        List<String[]> list = ContactsWidget.load(c, id);
        if (list.isEmpty()) {
            v.setViewVisibility(R.id.cs_content, View.GONE);
            v.setViewVisibility(R.id.cs_empty, View.VISIBLE);
            v.setOnClickPendingIntent(R.id.cs_root, PendingIntent.getActivity(c, 7500 + id,
                    new Intent(c, ContactSingleConfig.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            m.updateAppWidget(id, v);
            return;
        }
        String[] e = list.get(0);
        String name = TextUtils.isEmpty(e[0]) ? Util.number(c, e[1]) : e[0];
        int slot = Ui.avatarSlot(e[0]);
        String ini = Ui.initials(e[0]);
        v.setViewVisibility(R.id.cs_empty, View.GONE);
        v.setViewVisibility(R.id.cs_content, View.VISIBLE);
        v.setTextViewText(R.id.cs_avatar, ini != null ? ini : "#");
        v.setInt(R.id.cs_avatar, "setBackgroundResource", AV_BG[slot]);
        v.setTextColor(R.id.cs_avatar, Ui.avatarFg(c, slot));
        v.setTextViewText(R.id.cs_name, firstName(name));
        v.setOnClickPendingIntent(R.id.cs_root, PendingIntent.getActivity(c, 7600 + id,
                ContactMenuActivity.intent(c, e[1], e[0]),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setContentDescription(R.id.cs_root, "Opciones de " + name + ": llamar, WhatsApp o historial");
        m.updateAppWidget(id, v);
    }

    private static String firstName(String n) {
        if (n == null) return "";
        String t = n.trim();
        if (t.isEmpty() || !Character.isLetter(t.codePointAt(0))) return t;
        int sp = t.indexOf(' ');
        return sp > 0 ? t.substring(0, sp) : t;
    }
}
