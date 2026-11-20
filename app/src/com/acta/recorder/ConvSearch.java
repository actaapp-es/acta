package com.acta.recorder;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Búsqueda de palabras dentro de las conversaciones (transcripciones, resúmenes y tareas). */
final class ConvSearch {

    private ConvSearch() { }

    static final int MAX_HITS_PER_CALL = 5;

    static final class Hit {
        double t = -1;        // segundo del audio, o -1 si no aplica
        boolean approx;       // tiempo aproximado (transcripción sin tiempos)
        String where;         // "Conversación", "Resumen", "Tarea", "Nota"
        String snippet;
        int start, len;       // posición del término dentro de snippet
    }

    static final class Group {
        long callId;
        String name, number;
        long startMs;
        int durationSec;
        boolean nameMatch;
        final List<Hit> hits = new ArrayList<>();
        int total;            // coincidencias totales (puede ser mayor que hits.size())
    }

    /** Minúsculas y sin tildes, conservando la longitud para que las posiciones coincidan. */
    static String fold(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 128) { sb.append(Character.toLowerCase(ch)); continue; }
            String n = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD);
            char base = n.isEmpty() ? ch : n.charAt(0);
            sb.append(Character.toLowerCase(base));
        }
        return sb.toString();
    }

    static List<Group> search(List<Db.Call> calls, String query) {
        List<Group> out = new ArrayList<>();
        String q = fold(query).trim().replaceAll("\\s+", " ");
        if (q.length() < 2) return out;
        String[] words = q.split(" ");
        for (Db.Call c : calls) {
            Group g = new Group();
            g.callId = c.id;
            g.name = c.name;
            g.number = c.number;
            g.startMs = c.startMs;
            g.durationSec = c.recordedSec > 0 ? c.recordedSec : c.durationSec;

            // 1) Conversación con tiempos exactos.
            List<Summarizer.Seg> segs = Summarizer.parseSegments(c.segments);
            if (!segs.isEmpty()) {
                for (Summarizer.Seg sg : segs) {
                    int[] m = find(sg.s, q, words);
                    if (m == null) continue;
                    g.total++;
                    if (g.hits.size() < MAX_HITS_PER_CALL) g.hits.add(hit("Conversación", sg.s, m, sg.t, false));
                }
            } else if (c.transcript != null && !c.transcript.isEmpty()) {
                // 2) Transcripción antigua sin tiempos: posición aproximada.
                String f = fold(c.transcript);
                int from = 0;
                while (true) {
                    int idx = f.indexOf(q, from);
                    if (idx < 0) break;
                    g.total++;
                    if (g.hits.size() < MAX_HITS_PER_CALL) {
                        double t = g.durationSec > 0 ? (double) idx / Math.max(1, f.length()) * g.durationSec : -1;
                        g.hits.add(window(c.transcript, idx, q.length(), t));
                    }
                    from = idx + q.length();
                }
            }

            // 3) Análisis: contexto, resumen, nota y tareas.
            Summarizer.Result r = Summarizer.parse(c.summary);
            if (r != null) {
                addText(g, "Resumen", r.summary, q, words);
                addText(g, "Resumen", r.context, q, words);
                addText(g, "Nota", r.note, q, words);
                for (Summarizer.Task t : r.tasks) {
                    addText(g, "Tarea", t.title + (t.detail.isEmpty() ? "" : " — " + t.detail), q, words);
                }
            }

            // 4) Nombre o número.
            String who = fold(c.name) + " " + (c.number == null ? "" : c.number.replaceAll("[^0-9+]", ""));
            g.nameMatch = who.contains(q) || (q.replaceAll("[^0-9]", "").length() >= 3
                    && who.contains(q.replaceAll("[^0-9+]", "")));

            if (g.total > 0 || g.nameMatch) out.add(g);
        }
        return out;
    }

    private static void addText(Group g, String where, String text, String q, String[] words) {
        if (text == null || text.isEmpty()) return;
        int[] m = find(text, q, words);
        if (m == null) return;
        g.total++;
        if (g.hits.size() < MAX_HITS_PER_CALL) g.hits.add(hit(where, text, m, -1, false));
    }

    /** Posición {inicio, longitud} de la frase buscada, o de la primera palabra si están todas. */
    static int[] find(String text, String q, String[] words) {
        String f = fold(text);
        int idx = f.indexOf(q);
        if (idx >= 0) return new int[]{idx, q.length()};
        if (words.length < 2) return null;
        int first = -1;
        for (String w : words) {
            if (w.length() < 2) continue;
            int i = f.indexOf(w);
            if (i < 0) return null;
            if (first < 0 || i < first) first = i;
        }
        return first < 0 ? null : new int[]{first, words[0].length()};
    }

    private static Hit hit(String where, String text, int[] m, double t, boolean approx) {
        Hit h = window(text, m[0], m[1], t);
        h.where = where;
        h.approx = approx;
        return h;
    }

    /** Recorta el texto alrededor de la coincidencia (±70 caracteres). */
    private static Hit window(String text, int idx, int len, double t) {
        Hit h = new Hit();
        h.where = "Conversación";
        h.t = t;
        h.approx = t >= 0;
        int from = Math.max(0, idx - 70);
        int to = Math.min(text.length(), idx + len + 70);
        // Ajusta a límites de palabra.
        if (from > 0) { int sp = text.indexOf(' ', from); if (sp >= 0 && sp < idx) from = sp + 1; }
        if (to < text.length()) { int sp = text.lastIndexOf(' ', to); if (sp > idx + len) to = sp; }
        String pre = from > 0 ? "…" : "";
        String post = to < text.length() ? "…" : "";
        h.snippet = pre + text.substring(from, to) + post;
        h.start = pre.length() + idx - from;
        h.len = Math.min(len, h.snippet.length() - h.start);
        return h;
    }

    static String clock(double sec) {
        int s = (int) Math.max(0, Math.round(sec));
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
