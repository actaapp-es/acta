package com.acta.recorder;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** Widget con las tareas pendientes de todas las llamadas. */
public class TasksWidget extends AppWidgetProvider {

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (Util.ACTION_UPDATED.equals(i.getAction())) refresh(c);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        int count = AppLock.enabled(c) ? -1 : 0;
        try { count = Tasks.pending(c, null).size(); } catch (Throwable ignored) { }
        for (int id : ids) m.updateAppWidget(id, build(c, id, count));
        m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, TasksWidget.class));
        if (ids == null || ids.length == 0) return;
        new TasksWidget().onUpdate(c, m, ids);
    }

    private static RemoteViews build(Context c, int id, int count) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_tasks);
        boolean locked = AppLock.enabled(c);
        v.setTextViewText(R.id.w_count, String.valueOf(Math.max(0, count)));
        v.setTextViewText(R.id.w_empty, locked
                ? (count > 0 ? "Tienes " + count + (count == 1 ? " tarea pendiente" : " tareas pendientes")
                + ".\nAbre Acta para verlas." : "Abre Acta para ver tus tareas.")
                : "No tienes tareas pendientes");

        Intent svc = new Intent(c, TasksWidgetService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.w_list, svc);
        v.setEmptyView(R.id.w_list, R.id.w_empty);

        Intent tpl = new Intent(c, WidgetActionActivity.class);
        v.setPendingIntentTemplate(R.id.w_list, PendingIntent.getActivity(c, 7100 + id, tpl,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        PendingIntent open = PendingIntent.getActivity(c, 7200 + id,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.w_header, open);
        v.setOnClickPendingIntent(R.id.w_empty, open);
        return v;
    }
}
