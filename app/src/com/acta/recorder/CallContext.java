package com.acta.recorder;

import android.content.Context;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Contexto al recibir (o hacer) una llamada: de qué hablasteis la última vez,
 * a partir del último análisis con IA de una llamada con ese número.
 * Ej.: «Hace 3 días: quedó en enviarte el presupuesto».
 */
final class CallContext {

    private CallContext() { }

    static boolean enabled(Context c) { return Util.prefs(c).getBoolean("ai_context", true); }

    /**
     * @param redact true para no mostrar el contenido (bloqueo activo y móvil bloqueado):
     *               solo indica cuándo fue la última llamada.
     */
    static String line(Context c, String number, boolean redact) {
        if (!enabled(c) || TextUtils.isEmpty(number)) return null;
        List<Db.Call> calls = Db.get(c).recentFor(number, 40);
        if (calls.isEmpty()) return null;
        Db.Call last = calls.get(0);
        Db.Call analyzed = null;
        Summarizer.Result r = null;
        for (Db.Call k : calls) {
            Summarizer.Result p = Summarizer.parse(k.summary);
            if (p != null) { analyzed = k; r = p; break; }
        }
        if (redact || r == null) {
            String d = last.durationSec > 0 ? " · " + Util.duration(last.durationSec) : "";
            return cap("última llamada " + ago(last.startMs)) + d;
        }
        String what = null;
        if (!r.note.isEmpty()) {
            what = r.note;
        } else {
            for (Summarizer.Task t : r.tasks) {
                if (t.done) continue;
                boolean mine = t.owner.isEmpty() || t.owner.equalsIgnoreCase("tú") || t.owner.equalsIgnoreCase("tu")
                        || t.owner.toLowerCase(Util.ES).startsWith("sin determinar");
                what = "pendiente: " + lower(t.title) + (mine ? "" : " (" + t.owner + ")");
                break;
            }
            if (what == null) what = firstSentence(!r.summary.isEmpty() ? r.summary : r.context);
        }
        if (TextUtils.isEmpty(what)) return cap("última llamada " + ago(last.startMs));
        return cap(ago(analyzed.startMs)) + ": " + what;
    }

    /**
     * Resumen para el botón «Contexto» durante la llamada: quién es esa persona según las llamadas
     * anteriores analizadas (resúmenes, notas y tareas pendientes). Funciona sin conexión.
     */
    static String detail(Context c, String number, boolean redact) {
        if (TextUtils.isEmpty(number)) return null;
        List<Db.Call> calls = Db.get(c).recentFor(number, 40);
        if (calls.isEmpty()) return null;
        Db.Call last = calls.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(calls.size() == 1 ? "Habéis hablado 1 vez" : "Habéis hablado " + calls.size() + " veces")
                .append(" · última ").append(ago(last.startMs)).append(".");
        if (redact) return sb.append("\n\nDesbloquea el móvil para ver el resumen.").toString();
        StringBuilder talk = new StringBuilder(), todo = new StringBuilder();
        int shown = 0, tasks = 0;
        for (Db.Call k : calls) {
            Summarizer.Result r = Summarizer.parse(k.summary);
            if (r == null) continue;
            if (shown < 3) {
                String what = !r.summary.isEmpty() ? r.summary : !r.context.isEmpty() ? r.context : r.note;
                if (!TextUtils.isEmpty(what)) {
                    if (what.length() > 260) what = what.substring(0, 257).trim() + "…";
                    talk.append("• ").append(cap(ago(k.startMs))).append(": ").append(what).append("\n");
                    shown++;
                }
            }
            for (Summarizer.Task t : r.tasks) {
                if (t.done || tasks >= 5) continue;
                todo.append("• ").append(t.title);
                if (!t.owner.isEmpty() && !t.owner.equalsIgnoreCase("tú") && !t.owner.equalsIgnoreCase("tu")
                        && !t.owner.toLowerCase(Util.ES).startsWith("sin determinar")) todo.append(" (").append(t.owner).append(")");
                todo.append("\n");
                tasks++;
            }
        }
        if (talk.length() == 0 && todo.length() == 0) {
            return sb.append("\n\nAún no hay resúmenes de esas llamadas. Con la IA activada y la clave de Groq, "
                    + "aquí verás de qué habláis y lo que queda pendiente.").toString();
        }
        if (talk.length() > 0) sb.append("\n\nDe qué habéis hablado\n").append(talk.toString().trim());
        if (todo.length() > 0) sb.append("\n\nPendiente\n").append(todo.toString().trim());
        return sb.toString();
    }

    /** «hace un momento», «hace 20 min», «hoy a las 10:15», «ayer», «hace 3 días», «el 2 de octubre». */
    static String ago(long t) {
        long diff = System.currentTimeMillis() - t;
        long min = diff / 60_000;
        if (min < 2) return "hace un momento";
        if (min < 60) return "hace " + min + " min";
        Calendar now = Calendar.getInstance(), then = Calendar.getInstance();
        then.setTimeInMillis(t);
        int days = (int) Math.round((startOfDay(now) - startOfDay(then)) / 86_400_000.0);
        if (days == 0) return "hoy a las " + Util.time(t);
        if (days == 1) return "ayer";
        if (days < 7) return "hace " + days + " días";
        if (days < 30) { int w = days / 7; return w == 1 ? "hace una semana" : "hace " + w + " semanas"; }
        return new SimpleDateFormat(now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                ? "'el' d 'de' MMMM" : "'el' d 'de' MMMM 'de' yyyy", Util.ES).format(new Date(t));
    }

    private static long startOfDay(Calendar c) {
        Calendar x = (Calendar) c.clone();
        x.set(Calendar.HOUR_OF_DAY, 0); x.set(Calendar.MINUTE, 0); x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }

    private static String firstSentence(String s) {
        if (TextUtils.isEmpty(s)) return null;
        int i = s.indexOf(". ");
        String f = i > 0 ? s.substring(0, i + 1) : s;
        if (f.length() > 110) f = f.substring(0, 107).trim() + "…";
        return lower(f);
    }

    private static String lower(String s) {
        if (TextUtils.isEmpty(s) || s.length() < 2) return s;
        // No toca siglas ni nombres propios evidentes (dos mayúsculas seguidas).
        if (Character.isUpperCase(s.charAt(1))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String cap(String s) {
        if (TextUtils.isEmpty(s)) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
