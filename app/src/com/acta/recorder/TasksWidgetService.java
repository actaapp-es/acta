package com.acta.recorder;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** Proporciona las filas del widget de tareas. */
public class TasksWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context c;
        private List<Tasks.Item> items = new ArrayList<>();

        Factory(Context c) { this.c = c; }

        @Override public void onCreate() { }

        @Override public void onDataSetChanged() {
            if (AppLock.enabled(c)) { items = new ArrayList<>(); return; }
            try {
                List<Tasks.Item> all = Tasks.pending(c, null);
                items = all.size() > 50 ? new ArrayList<>(all.subList(0, 50)) : all;
            } catch (Throwable t) {
                items = new ArrayList<>();
            }
        }

        @Override public void onDestroy() { items.clear(); }

        @Override public int getCount() { return items.size(); }

        @Override public RemoteViews getViewAt(int position) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_task_item);
            if (position >= items.size()) return v;
            Tasks.Item it = items.get(position);
            v.setTextViewText(R.id.item_title, it.task.title);
            v.setTextViewText(R.id.item_meta, it.who + " · " + Tasks.when(it.task));
            v.setOnClickFillInIntent(R.id.item_root, new Intent()
                    .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.OPEN)
                    .putExtra("id", it.callId));
            v.setOnClickFillInIntent(R.id.item_check, new Intent()
                    .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.TOGGLE)
                    .putExtra("id", it.callId).putExtra("index", it.index));
            return v;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) {
            if (position >= items.size()) return position;
            Tasks.Item it = items.get(position);
            return it.callId * 100 + it.index;
        }
        @Override public boolean hasStableIds() { return true; }
    }
}
