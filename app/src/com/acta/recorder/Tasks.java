package com.acta.recorder;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Tareas pendientes de todas las llamadas (para la ficha de cada persona y el widget). */
final class Tasks {

    private Tasks() { }

    static final class Item {
        long callId;
        int index;
        Summarizer.Task task;
        String who, number;
        long callMs;
    }

    /** Pendientes de todas las llamadas, o solo las de un número si se indica. */
    static List<Item> pending(Context c, String number) {
        List<Item> out = new ArrayList<>();
        List<Db.Call> calls = number == null ? Db.get(c).all() : Db.get(c).recentFor(number, 500);
        for (Db.Call call : calls) {
            Summarizer.Result r = Summarizer.parse(call.summary);
            if (r == null) continue;
            for (int i = 0; i < r.tasks.size(); i++) {
                Summarizer.Task t = r.tasks.get(i);
                if (t.done) continue;
                Item it = new Item();
                it.callId = call.id;
                it.index = i;
                it.task = t;
                it.number = call.number;
                it.who = !TextUtils.isEmpty(call.name) ? call.name
                        : TextUtils.isEmpty(call.number) ? "Número oculto" : Util.number(c, call.number);
                it.callMs = call.startMs;
                out.add(it);
            }
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                String da = a.task.date, db = b.task.date;
                if (da != null && db != null) {
                    int k = da.compareTo(db);
                    if (k != 0) return k;
                    String ta = a.task.time == null ? "99" : a.task.time, tb = b.task.time == null ? "99" : b.task.time;
                    return ta.compareTo(tb);
                }
                if (da != null) return -1;
                if (db != null) return 1;
                return Long.compare(b.callMs, a.callMs);
            }
        });
        return out;
    }

    static void setDone(Context c, long callId, int index, boolean done) {
        Db.Call call = Db.get(c).byId(callId);
        if (call == null) return;
        Summarizer.Result r = Summarizer.parse(call.summary);
        if (r == null || index < 0 || index >= r.tasks.size()) return;
        r.tasks.get(index).done = done;
        Db.get(c).updateSummary(callId, r.toJson());
        c.sendBroadcast(new Intent(Util.ACTION_UPDATED).setPackage(c.getPackageName()));
    }

    /** «Hoy», «Mañana», «viernes 20 nov» o «Sin fecha», con la hora si la hay. */
    static String when(Summarizer.Task t) {
        if (t.date == null) return t.time != null ? "Sin fecha · " + t.time : "Sin fecha";
        String day = t.date;
        try {
            java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", Util.ES).parse(t.date);
            day = ScheduleCall.dayLabel(d.getTime());
        } catch (Exception ignored) { }
        return t.time != null ? day + " · " + t.time : day;
    }
}
