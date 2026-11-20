package com.acta.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** «Programar llamada»: elige cuándo y a esa hora llega un aviso con un botón para llamar. */
final class ScheduleCall {

    private ScheduleCall() { }

    static void show(final Activity a, final String number, final String name, final Runnable done) {
        if (TextUtils.isEmpty(number)) return;
        final String who = !TextUtils.isEmpty(name) ? name : Util.number(a, number);
        final List<String> labels = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
        Calendar now = Calendar.getInstance();

        labels.add("Dentro de 1 hora");
        times.add(System.currentTimeMillis() + 3_600_000L);

        Calendar evening = at(0, 18, 0);
        if (evening.getTimeInMillis() - now.getTimeInMillis() > 30 * 60_000L) {
            labels.add("Esta tarde a las 18:00");
            times.add(evening.getTimeInMillis());
        }
        Calendar morning = at(1, 9, 0);
        labels.add("Mañana a las 9:00");
        times.add(morning.getTimeInMillis());
        Calendar tenAm = at(1, 10, 0);
        labels.add("Mañana a las 10:00");
        times.add(tenAm.getTimeInMillis());
        labels.add("Elegir día y hora…");
        times.add(-1L);

        new AlertDialog.Builder(a)
                .setTitle("Llamar a " + who)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        long t = times.get(w);
                        if (t > 0) save(a, number, name, who, t, done);
                        else pick(a, number, name, who, done);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void pick(final Activity a, final String number, final String name, final String who, final Runnable done) {
        final Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        DatePickerDialog dp = new DatePickerDialog(a, new DatePickerDialog.OnDateSetListener() {
            @Override public void onDateSet(DatePicker v, int y, int m, int d) {
                c.set(y, m, d);
                new TimePickerDialog(a, new TimePickerDialog.OnTimeSetListener() {
                    @Override public void onTimeSet(TimePicker tv, int h, int min) {
                        c.set(Calendar.HOUR_OF_DAY, h);
                        c.set(Calendar.MINUTE, min);
                        c.set(Calendar.SECOND, 0);
                        c.set(Calendar.MILLISECOND, 0);
                        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
                            Toast.makeText(a, "Esa hora ya ha pasado. Elige otra.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        save(a, number, name, who, c.getTimeInMillis(), done);
                    }
                }, 10, 0, true).show();
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.show();
    }

    private static void save(Activity a, String number, String name, String who, long at, Runnable done) {
        QuickActions.scheduleCall(a, number, name, at);
        String exact = QuickActions.exactAlarmsAllowed(a) ? "" : " (aprox.)";
        Toast.makeText(a, "Te avisaré " + dayLabel(at).toLowerCase(Util.ES) + " a las " + Util.time(at) + exact
                + " para llamar a " + who, Toast.LENGTH_LONG).show();
        if (done != null) done.run();
    }

    private static Calendar at(int addDays, int h, int m) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, addDays);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** «Hoy», «Mañana» o «Viernes, 20 de noviembre». */
    static String dayLabel(long ms) {
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(ms);
        Calendar t = Calendar.getInstance();
        if (same(d, t)) return "Hoy";
        t.add(Calendar.DAY_OF_YEAR, 1);
        if (same(d, t)) return "Mañana";
        return Util.dayHeader(ms);
    }

    private static boolean same(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Lista de llamadas programadas con opción de cancelar. */
    static void manage(final Activity a, final Runnable changed) {
        final List<QuickActions.Reminder> list = QuickActions.pending(a);
        if (list.isEmpty()) {
            new AlertDialog.Builder(a).setTitle("Llamadas programadas")
                    .setMessage("No tienes llamadas programadas. Prográmalas desde la ficha de una persona o "
                            + "manteniendo pulsada una llamada en Recientes.")
                    .setPositiveButton("Entendido", null).show();
            return;
        }
        String[] items = new String[list.size()];
        for (int i = 0; i < items.length; i++) {
            QuickActions.Reminder r = list.get(i);
            String who = !TextUtils.isEmpty(r.name) ? r.name : Util.number(a, r.number);
            items[i] = who + " · " + dayLabel(r.at) + ", " + Util.time(r.at);
        }
        new AlertDialog.Builder(a).setTitle("Llamadas programadas")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, final int w) {
                        new AlertDialog.Builder(a).setMessage("¿Cancelar este aviso?")
                                .setNegativeButton("No", null)
                                .setPositiveButton("Cancelar aviso", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d2, int w2) {
                                        QuickActions.cancel(a, list.get(w).id);
                                        if (changed != null) changed.run();
                                    }
                                }).show();
                    }
                })
                .setPositiveButton("Cerrar", null).show();
    }
}
