package com.acta.recorder;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Análisis de llamadas con IA (Groq, nivel gratuito, clave del usuario).
 *  1. Transcribe con Whisper (español), troceando las grabaciones largas.
 *  2. Un modelo de lenguaje devuelve un JSON con contexto, resumen breve y tareas
 *     (responsable, fecha y hora resueltas a partir de la fecha de la llamada).
 * El audio solo se envía cuando el usuario lo pide.
 */
final class Summarizer {

    private static final String BASE = "https://api.groq.com/openai/v1";
    private static final String TRANSCRIBE_MODEL = "whisper-large-v3";
    /** Preferencia de modelos de texto (se cruza con los disponibles en la cuenta). */
    private static final String[] PREFERRED = {
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "qwen/qwen3-32b",
            "moonshotai/kimi-k2-instruct",
            "llama-3.3-70b-versatile",
            "meta-llama/llama-4-maverick-17b-128e-instruct",
            "llama-3.1-8b-instant"
    };
    /** Límite de subida de Groq (25 MB); troceamos por debajo. */
    private static final long MAX_UPLOAD = 24L * 1024 * 1024;
    private static final int CHUNK_SECONDS = 600;

    // ------------------------------------------------------------------ Modelo de datos

    static final class Task {
        String title = "", detail = "", owner = "", date, time;
        boolean done;
    }

    static final class Result {
        String context = "", summary = "", note = "";
        final List<Task> tasks = new ArrayList<>();

        int pending() {
            int n = 0;
            for (Task t : tasks) if (!t.done) n++;
            return n;
        }

        String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("v", 2);
                o.put("contexto", context);
                o.put("resumen", summary);
                o.put("nota", note);
                JSONArray arr = new JSONArray();
                for (Task t : tasks) {
                    JSONObject j = new JSONObject();
                    j.put("titulo", t.title);
                    j.put("detalle", t.detail);
                    j.put("responsable", t.owner);
                    j.put("fecha", t.date == null ? JSONObject.NULL : t.date);
                    j.put("hora", t.time == null ? JSONObject.NULL : t.time);
                    j.put("hecha", t.done);
                    arr.put(j);
                }
                o.put("tareas", arr);
                return o.toString();
            } catch (Exception e) {
                return "";
            }
        }
    }

    /** Lee un resumen guardado. null si no es del formato estructurado (resúmenes antiguos en texto). */
    static Result parse(String stored) {
        if (TextUtils.isEmpty(stored)) return null;
        String s = stored.trim();
        if (!s.startsWith("{")) return null;
        try {
            return fromJson(new JSONObject(s));
        } catch (Exception e) {
            return null;
        }
    }

    /** Tareas pendientes de un resumen guardado (para la lista de grabaciones). */
    static int pendingTasks(String stored) {
        Result r = parse(stored);
        return r == null ? 0 : r.pending();
    }

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");

    private static Result fromJson(JSONObject o) {
        Result r = new Result();
        r.context = clean(o.optString("contexto", ""));
        r.summary = clean(o.optString("resumen", ""));
        r.note = clean(o.optString("nota", ""));
        JSONArray arr = o.optJSONArray("tareas");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j == null) continue;
                Task t = new Task();
                t.title = clean(j.optString("titulo", ""));
                if (t.title.isEmpty()) continue;
                t.detail = clean(j.optString("detalle", ""));
                t.owner = clean(j.optString("responsable", ""));
                String d = j.isNull("fecha") ? null : j.optString("fecha", null);
                String h = j.isNull("hora") ? null : j.optString("hora", null);
                t.date = d != null && DATE.matcher(d.trim()).matches() ? d.trim() : null;
                if (h != null && TIME.matcher(h.trim()).matches()) {
                    String hh = h.trim();
                    t.time = hh.length() == 4 ? "0" + hh : hh;
                }
                t.done = j.optBoolean("hecha", false);
                r.tasks.add(t);
            }
        }
        return r;
    }

    private static String clean(String s) {
        if (s == null || "null".equalsIgnoreCase(s.trim())) return "";
        return s.trim();
    }

    // ------------------------------------------------------------------ Clave

    /** La clave de Groq se guarda cifrada con el Keystore; si había una antigua en claro, se migra. */
    static String key(Context c) {
        android.content.SharedPreferences p = Util.prefs(c);
        String sealed = p.getString("ai_key_s", "");
        if (!sealed.isEmpty()) {
            try { return Crypto.openText(c, sealed).trim(); } catch (Exception e) { return ""; }
        }
        String old = p.getString("ai_key", "").trim();
        if (!old.isEmpty()) setKey(c, old);
        return old;
    }

    static void setKey(Context c, String k) {
        android.content.SharedPreferences.Editor e = Util.prefs(c).edit();
        k = k == null ? "" : k.trim();
        try {
            if (k.isEmpty()) { e.remove("ai_key_s").remove("ai_key"); }
            else { e.putString("ai_key_s", Crypto.sealText(c, k)).remove("ai_key"); }
        } catch (Exception ex) {
            e.putString("ai_key", k); // último recurso: que la IA no deje de funcionar
        }
        e.apply();
    }

    static boolean hasKey(Context c) { return !key(c).isEmpty(); }

    // ------------------------------------------------------------------ Proceso completo

    /** Salida del análisis: resultado estructurado + transcripción. */
    static final class Output {
        Result result;
        String transcript;
        String segments; // JSON [[segundo, "frase", hablante, fin], ...] o null
        boolean speakers; // la transcripción indica quién habla
    }

    /** Frase de la transcripción con el segundo en que empieza. */
    static final class Seg {
        double t, e = -1;          // inicio y fin (segundos)
        String s;
        char spk = SpeakerSplit.UNKNOWN; // 'y' = tú (dueño del móvil), 'o' = la otra persona
        Seg(double t, String s) { this.t = t; this.s = s; }
        Seg(double t, double e, String s) { this.t = t; this.e = e; this.s = s; }
    }

    static List<Seg> parseSegments(String json) {
        List<Seg> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONArray e = a.getJSONArray(i);
                Seg sg = new Seg(e.getDouble(0), e.getString(1));
                String sp = e.optString(2, "");
                if (sp.length() == 1) sg.spk = sp.charAt(0);
                sg.e = e.optDouble(3, -1);
                out.add(sg);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    static Output analyze(Context c, Db.Call call, File audio) throws Exception {
        String k = key(c);
        if (k.isEmpty()) throw new Exception("Falta la clave de IA. Ponla en Ajustes.");
        if (audio == null || !audio.exists()) throw new Exception("No hay audio para analizar.");

        String who = !TextUtils.isEmpty(call.name) ? call.name : "";
        File tmpDir = new File(c.getCacheDir(), "ai");
        //noinspection ResultOfMethodCallIgnored
        tmpDir.mkdirs();
        // Las grabaciones están cifradas: se descifran en un temporal privado que se borra al terminar.
        File plain = audio;
        if (Crypto.isEncrypted(audio)) {
            plain = new File(tmpDir, "plain_" + audio.getName());
            Crypto.decryptTo(c, audio, plain);
        }
        List<Seg> segs = new ArrayList<>();
        List<Seg> clean = new ArrayList<>();
        boolean timed = false;
        char[] spk = null;
        try {
            transcribeAll(plain, k, who, tmpDir, segs);
            // Limpia alucinaciones y frases repetidas, conservando el tiempo de cada frase.
            String prev = null;
            for (Seg sg : segs) {
                String q = HALLUCINATIONS.matcher(sg.s).replaceAll(" ").replaceAll("\\s+", " ").trim();
                if (q.isEmpty() || q.replaceAll("[\\p{Punct}\\s]", "").isEmpty() || q.equalsIgnoreCase(prev)) continue;
                prev = q;
                clean.add(new Seg(sg.t, sg.e, q));
                if (sg.t >= 0) timed = true;
            }
            // Quién habla, a partir de la pista de tu voz grabada aparte (si la hay).
            if (timed && clean.size() >= 2 && plain.getName().toLowerCase(Locale.ROOT).endsWith(".wav")) {
                SpeakerSplit.Vad vad = SpeakerSplit.readVad(SpeakerSplit.vadFor(c, call.file));
                if (vad != null) {
                    double[] st = new double[clean.size()], en = new double[clean.size()];
                    for (int i = 0; i < clean.size(); i++) {
                        st[i] = clean.get(i).t;
                        double e = clean.get(i).e;
                        double next = i + 1 < clean.size() ? clean.get(i + 1).t : st[i] + 4;
                        en[i] = e > st[i] ? Math.min(e, next + 0.3) : next;
                    }
                    spk = SpeakerSplit.fromAudio(SpeakerSplit.wavEnergy(plain), vad, st, en);
                }
            }
        } finally {
            if (plain != audio) //noinspection ResultOfMethodCallIgnored
                plain.delete();
        }
        // Respaldo: si no hay pista de tu voz o no es concluyente, la IA deduce quién habla por el contenido.
        if (clean.size() >= 2 && (spk == null || SpeakerSplit.unknownCount(spk) * 10 > clean.size() * 4)) {
            try { spk = diarizeWithAi(c, k, call, clean); } catch (Throwable ignored) { spk = null; }
        }
        if (spk != null) {
            SpeakerSplit.fillGaps(spk);
            for (int i = 0; i < clean.size(); i++) clean.get(i).spk = spk[i];
        }
        List<String> texts = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        for (Seg sg : clean) { texts.add(sg.s); all.append(sg.s).append(' '); }
        String transcript = spk != null ? SpeakerSplit.turns(texts, spk, call) : cleanTranscript(all.toString());
        Output out = new Output();
        out.transcript = transcript;
        out.speakers = spk != null;
        if (timed && clean.size() > 0) {
            JSONArray ja = new JSONArray();
            for (Seg sg : clean) {
                if (sg.t < 0) continue;
                JSONArray e = new JSONArray().put(Math.round(sg.t * 10) / 10.0).put(sg.s)
                        .put(String.valueOf(sg.spk));
                if (sg.e > sg.t) e.put(Math.round(sg.e * 10) / 10.0);
                ja.put(e);
            }
            out.segments = ja.toString();
        }

        if (wordCount(transcript) < 4) {
            Result r = new Result();
            r.context = "No se distingue una conversación clara en la grabación.";
            out.result = r;
            return out;
        }
        String json = chat(c, k, systemPrompt(out.speakers), userPrompt(c, call, transcript, out.speakers));
        Result r = fromJson(new JSONObject(extractJson(json)));
        if (r.context.isEmpty() && r.summary.isEmpty() && r.tasks.isEmpty())
            throw new Exception("La IA no devolvió un análisis válido. Prueba otra vez.");
        out.result = r;
        return out;
    }

    // ------------------------------------------------------------------ Preguntas sobre una llamada

    static final class Answer {
        String text;
        double t = -1;
    }

    /** Responde una pregunta usando solo lo que se habló en la llamada. */
    static Answer ask(Context c, Db.Call call, String question) throws Exception {
        String k = key(c);
        if (k.isEmpty()) throw new Exception("Falta la clave de IA. Ponla en Ajustes.");
        if (TextUtils.isEmpty(call.transcript)) throw new Exception("Analiza la llamada primero para poder preguntarle.");
        if (!AiConsent.granted(c)) throw new Exception("Acepta el aviso de análisis con IA para usar esta función.");
        StringBuilder tr = new StringBuilder();
        List<Seg> segs = parseSegments(call.segments);
        if (!segs.isEmpty()) {
            for (Seg sg : segs) {
                tr.append('[').append(ConvSearch.clock(sg.t)).append("] ");
                String l = SpeakerSplit.label(sg.spk, call);
                if (!l.isEmpty()) tr.append(l).append(": ");
                tr.append(sg.s).append('\n');
            }
        } else {
            tr.append(call.transcript);
        }
        String t = tr.length() > 28000 ? tr.substring(0, 28000) + "…" : tr.toString();
        String who = !TextUtils.isEmpty(call.name) ? call.name : "la otra persona";
        String owner = SpeakerSplit.ownerName(c);
        String system = "Eres el asistente de Acta. Respondes preguntas del dueño del teléfono sobre UNA llamada "
                + "suya, usando EXCLUSIVAMENTE la transcripción que se te da (puede tener errores de reconocimiento). "
                + "Cuando las líneas llevan etiqueta, «Tú» es SIEMPRE el dueño del teléfono (quien te pregunta"
                + (owner.isEmpty() ? "" : ", se llama " + owner) + ") y el otro nombre es la otra persona. Háblale de tú "
                + "(«te dijo», «quedaste en») y no confundas lo que dijo cada uno. Si no hay etiquetas, dedúcelo con "
                + "prudencia por el contenido. Responde SOLO con un JSON: {\"respuesta\": \"...\", \"segundo\": número o null}.\n"
                + "- respuesta: en español, directa y breve (1 a 3 frases). Cita cifras, fechas y nombres tal como se "
                + "dijeron. Si en la llamada no se habló de eso, dilo claramente («En la llamada no se menciona…»). "
                + "No inventes nada. Si algo es dudoso por la transcripción, indícalo con prudencia.\n"
                + "- segundo: si la transcripción tiene marcas de tiempo [m:ss], el segundo donde está la respuesta "
                + "(p. ej. [2:05] → 125). Si no hay marcas o no aplica, null.";
        String user = "Llamada " + (call.direction == 1 ? "entrante" : "saliente") + " con " + who + ", el "
                + Util.dateFull(call.startMs) + " a las " + Util.time(call.startMs) + ".\n\nTRANSCRIPCIÓN:\n" + t
                + "\n\nPREGUNTA: " + question;
        String json = chat(c, k, system, user);
        JSONObject o = new JSONObject(extractJson(json));
        Answer a = new Answer();
        a.text = o.optString("respuesta", "").trim();
        if (a.text.isEmpty()) throw new Exception("La IA no devolvió una respuesta. Prueba otra vez.");
        if (o.has("segundo") && !o.isNull("segundo")) {
            double sec = o.optDouble("segundo", -1);
            if (!Double.isNaN(sec) && sec >= 0 && sec <= Math.max(call.recordedSec, call.durationSec) + 5) a.t = sec;
        }
        return a;
    }

    // ------------------------------------------------------------------ Quién habla (respaldo con IA)

    /** La IA asigna cada frase al dueño del teléfono (y) o a la otra persona (o) por el contenido. */
    private static char[] diarizeWithAi(Context c, String k, Db.Call call, List<Seg> segs) throws Exception {
        char[] out = new char[segs.size()];
        Arrays.fill(out, SpeakerSplit.UNKNOWN);
        String owner = SpeakerSplit.ownerName(c);
        String other = !TextUtils.isEmpty(call.name) ? call.name : "la otra persona";
        String system = "Separas por hablante la transcripción automática de UNA llamada telefónica entre DOS personas: "
                + "D = el dueño del teléfono" + (owner.isEmpty() ? "" : " (se llama " + owner + ")")
                + " y O = " + other + ". Cada línea numerada es una frase. Devuelve SOLO un JSON "
                + "{\"h\": \"...\"} con exactamente una letra D u O por línea, en el mismo orden y sin espacios.\n"
                + "Pistas: " + (call.direction == 1
                ? "la llamada es ENTRANTE: O llamó a D, así que D contesta (suele decir «¿sí?», «¿diga?», «dime») y O "
                + "explica el motivo de la llamada. "
                : "la llamada es SALIENTE: D llamó a O, así que O contesta («¿sí?», «¿diga?») y D explica el motivo. ")
                + "Cada uno llama al otro por su nombre, no a sí mismo. Las preguntas y respuestas suelen alternar. Quien "
                + "dice «te envío», «te llamo» o «me encargo» se compromete él. Una línea con las dos voces: elige la que "
                + "domine.";
        int chunk = 200;
        for (int from = 0; from < segs.size(); from += chunk) {
            int to = Math.min(segs.size(), from + chunk);
            StringBuilder u = new StringBuilder();
            if (from > 0) {
                u.append("Contexto anterior (ya etiquetado):\n");
                for (int i = Math.max(0, from - 6); i < from; i++)
                    u.append(out[i] == 'y' ? "D" : "O").append(": ").append(segs.get(i).s).append('\n');
                u.append('\n');
            }
            u.append("Etiqueta estas ").append(to - from).append(" líneas:\n");
            for (int i = from; i < to; i++) u.append(i - from + 1).append(". ").append(segs.get(i).s).append('\n');
            String json = chat(c, k, system, u.toString());
            String h = new JSONObject(extractJson(json)).optString("h", "").replaceAll("[^DOdo]", "").toUpperCase(Locale.ROOT);
            for (int i = from; i < to && i - from < h.length(); i++) out[i] = h.charAt(i - from) == 'D' ? 'y' : 'o';
        }
        return SpeakerSplit.unknownCount(out) * 2 > out.length ? null : out;
    }

    // ------------------------------------------------------------------ Prompts

    private static String systemPrompt(boolean speakers) {
        return "Eres el asistente de Acta, una app que graba llamadas telefónicas. Recibes la transcripción "
                + "automática de UNA llamada entre dos personas: el DUEÑO del teléfono (quien usa Acta) y la OTRA "
                + "persona. " + (speakers
                ? "La transcripción indica quién habla: «Tú» es SIEMPRE el dueño del teléfono y el otro nombre (o "
                + "«Otra persona») es su interlocutor. Usa esas etiquetas para saber quién dice, pide o promete cada "
                + "cosa. Son automáticas: si alguna es claramente incoherente con lo que se dice, corrígela en silencio. "
                : "La transcripción no indica quién habla: dedúcelo con cuidado por el contenido (quién llamó a quién, "
                + "saludos, nombres, preguntas y respuestas). ")
                + "Puede tener errores de reconocimiento. Analízala con rigor y responde SOLO con un objeto JSON válido, en español, "
                + "con exactamente esta forma:\n"
                + "{\"contexto\": \"...\", \"resumen\": \"...\", \"nota\": \"...\", \"tareas\": [{\"titulo\": \"...\", "
                + "\"detalle\": \"...\", \"responsable\": \"...\", \"fecha\": \"AAAA-MM-DD\" o null, "
                + "\"hora\": \"HH:MM\" o null}]}\n\n"
                + "Reglas:\n"
                + "- Escribe SIEMPRE desde el punto de vista del dueño del teléfono: refiérete a él como «tú» (segunda "
                + "persona: «te pidió», «quedaste en», «le dijiste») y a la otra persona por su nombre o como «el "
                + "contacto». Nunca confundas lo que dijo uno con lo que dijo el otro.\n"
                + "- contexto: 1 o 2 frases. Quién habla con quién (usa el nombre del contacto si se conoce), "
                + "qué relación parece haber entre ellos y el motivo o la situación de la llamada.\n"
                + "- resumen: breve, de 2 a 4 frases. Lo esencial: qué se habló, qué se decidió o acordó y en qué "
                + "quedó la llamada. Sin relleno ni frases genéricas como «la llamada trata de varios temas».\n"
                + "- nota: UNA frase muy breve (máximo 90 caracteres), dirigida al dueño del teléfono, que le "
                + "recuerde en la PRÓXIMA llamada con esta persona lo más importante de esta. Empieza en minúscula "
                + "salvo nombres propios. Ejemplos: «quedó en enviarte el presupuesto el viernes», «le debes "
                + "confirmar la reunión del lunes», «te contó que se cambia de oficina». Si no hay nada relevante, \"\".\n"
                + "- tareas: solo acciones concretas que alguien pide, ordena, encarga o se compromete a hacer "
                + "(llamar, enviar, pagar, revisar, preparar, reunirse, recoger, confirmar…). No inventes tareas: "
                + "si no hay ninguna, devuelve [].\n"
                + "  · titulo: corto, empieza por un verbo en infinitivo (p. ej. «Enviar el presupuesto a Juan»).\n"
                + "  · detalle: lo necesario para hacerla: qué, a quién, dónde, importes, referencias. Puede ser \"\".\n"
                + "  · responsable: «Tú» si la tiene que hacer el dueño del teléfono; el nombre del contacto si la "
                + "hace la otra persona; «Ambos» si es compartida; «Sin determinar» si no queda claro.\n"
                + "  · fecha y hora: convierte expresiones como «mañana», «el viernes», «la semana que viene», "
                + "«a las cinco» usando la fecha y hora de la llamada que se te dan. Si no se dice cuándo, pon null. "
                + "No supongas fechas que no se mencionan.\n"
                + "- Sé fiel a la transcripción: no inventes nombres, cifras ni datos. Si algo es dudoso, dilo con "
                + "prudencia («parece que…»). Corrige en silencio errores evidentes de reconocimiento.\n"
                + "- Si no hay una conversación entendible, pon en contexto «No se distingue una conversación "
                + "clara en la grabación.», resumen \"\", nota \"\" y tareas [].";
    }

    private static String userPrompt(Context c, Db.Call call, String transcript, boolean speakers) {
        String owner = SpeakerSplit.ownerName(c);
        SimpleDateFormat f = new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy (yyyy-MM-dd) 'a las' HH:mm", Util.ES);
        f.setTimeZone(TimeZone.getDefault());
        String contact = !TextUtils.isEmpty(call.name) ? call.name : "Desconocido";
        return "Datos de la llamada:\n"
                + "- Fecha y hora: " + f.format(new Date(call.startMs)) + " (zona horaria " + TimeZone.getDefault().getID() + ")\n"
                + "- Tipo: " + (call.direction == 1
                        ? "entrante (el contacto llamó al dueño del teléfono)"
                        : "saliente (el dueño del teléfono llamó al contacto)") + "\n"
                + "- Dueño del teléfono: «Tú»" + (owner.isEmpty() ? "" : " (se llama " + owner + ")") + "\n"
                + "- Contacto (la otra persona): " + contact + "\n"
                + "- Duración: " + Util.duration(call.durationSec) + "\n\n"
                + "Transcripción:\n\"\"\"\n" + transcript + "\n\"\"\"";
    }

    // ------------------------------------------------------------------ Chat con elección de modelo

    private static String chat(Context c, String key, String system, String user) throws Exception {
        String lastErr = "sin modelos disponibles";
        for (String model : candidateModels(c, key)) {
            boolean extras = true; // response_format / reasoning_effort
            for (int round = 0; round < 2; round++) {
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("temperature", 0.2);
                body.put("max_completion_tokens", 4096);
                if (extras) {
                    body.put("response_format", new JSONObject().put("type", "json_object"));
                    if (model.startsWith("openai/gpt-oss")) body.put("reasoning_effort", "medium");
                }
                JSONArray msgs = new JSONArray();
                msgs.put(new JSONObject().put("role", "system").put("content", system));
                msgs.put(new JSONObject().put("role", "user").put("content", user));
                body.put("messages", msgs);

                Http r = post(BASE + "/chat/completions", key, body.toString());
                if (r.code < 400) {
                    JSONArray choices = new JSONObject(r.body).optJSONArray("choices");
                    String content = choices != null && choices.length() > 0
                            ? choices.getJSONObject(0).getJSONObject("message").optString("content", "") : "";
                    if (content.contains("{")) {
                        Util.prefs(c).edit().putString("ai_model", model).apply();
                        return content;
                    }
                    lastErr = "respuesta vacía";
                    break; // siguiente modelo
                }
                lastErr = brief(r.body);
                String low = lastErr.toLowerCase(Locale.ROOT);
                if (r.code == 401) throw new Exception("Clave de IA no válida. Revísala en Ajustes.");
                if (r.code == 429) throw new Exception("Límite gratuito de Groq alcanzado por ahora. Prueba en unos minutos.");
                if (r.code == 400 && extras && (low.contains("response_format") || low.contains("reasoning")
                        || low.contains("json") || low.contains("not supported"))) {
                    extras = false; // reintenta el mismo modelo sin parámetros opcionales
                    continue;
                }
                break; // modelo no disponible u otro error: siguiente modelo
            }
        }
        throw new Exception("No se pudo analizar con ningún modelo de Groq. Último aviso: " + lastErr);
    }

    /** Modelos a probar: preferidos que existan en la cuenta + el último que funcionó. */
    private static List<String> candidateModels(Context c, String key) {
        Set<String> out = new LinkedHashSet<>();
        String last = Util.prefs(c).getString("ai_model", "");
        Set<String> available = new LinkedHashSet<>();
        try {
            Http r = get(BASE + "/models", key);
            if (r.code < 400) {
                JSONArray data = new JSONObject(r.body).optJSONArray("data");
                if (data != null) for (int i = 0; i < data.length(); i++) {
                    String id = data.getJSONObject(i).optString("id", "");
                    if (!id.isEmpty()) available.add(id);
                }
            }
        } catch (Throwable ignored) { }

        if (available.isEmpty()) {
            if (!last.isEmpty()) out.add(last);
            for (String m : PREFERRED) out.add(m);
            return new ArrayList<>(out);
        }
        for (String m : PREFERRED) if (available.contains(m)) out.add(m);
        if (!last.isEmpty() && available.contains(last)) out.add(last);
        for (String id : available) { // cualquier otro modelo de texto como último recurso
            String l = id.toLowerCase(Locale.ROOT);
            if (l.contains("whisper") || l.contains("guard") || l.contains("tts") || l.contains("orpheus")
                    || l.contains("playai") || l.contains("compound") || l.contains("allam")) continue;
            out.add(id);
        }
        return new ArrayList<>(out);
    }

    private static String extractJson(String s) throws Exception {
        String t = s.trim();
        int a = t.indexOf('{');
        int b = t.lastIndexOf('}');
        if (a < 0 || b <= a) throw new Exception("La IA no devolvió un análisis válido. Prueba otra vez.");
        return t.substring(a, b + 1);
    }

    // ------------------------------------------------------------------ Transcripción

    private static void transcribeAll(File audio, String key, String who, File tmpDir, List<Seg> out) throws Exception {
        String name = audio.getName().toLowerCase(Locale.ROOT);
        String hint = "Llamada telefónica en español" + (who.isEmpty() ? "." : " con " + who + ".");
        if (name.endsWith(".wav")) {
            List<File> parts = splitWav(audio, tmpDir);
            try {
                double offset = 0;
                for (File p : parts) {
                    transcribe(p, key, hint, "audio/wav", offset, out);
                    offset += wavSeconds(p);
                }
            } finally {
                for (File p : parts) if (!p.equals(audio)) //noinspection ResultOfMethodCallIgnored
                    p.delete();
            }
            return;
        }
        if (audio.length() > MAX_UPLOAD)
            throw new Exception("La grabación es demasiado larga para analizarla (máx. ~30 min).");
        transcribe(audio, key, hint, name.endsWith(".mp3") ? "audio/mpeg" : "audio/mp4", 0, out);
    }

    /** Duración de un WAV PCM según su cabecera. */
    static double wavSeconds(File wav) {
        try (RandomAccessFile raf = new RandomAccessFile(wav, "r")) {
            byte[] h = new byte[44];
            raf.readFully(h);
            int ch = (h[22] & 0xff) | ((h[23] & 0xff) << 8);
            long rate = (h[24] & 0xffL) | ((h[25] & 0xffL) << 8) | ((h[26] & 0xffL) << 16) | ((h[27] & 0xffL) << 24);
            int bits = (h[34] & 0xff) | ((h[35] & 0xff) << 8);
            double bps = rate * Math.max(1, ch) * Math.max(8, bits) / 8.0;
            return bps > 0 ? (raf.length() - 44) / bps : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Trocea un WAV PCM en partes de ≤10 min (y ≤24 MB). Devuelve el original si cabe. */
    private static List<File> splitWav(File wav, File tmpDir) throws Exception {
        List<File> out = new ArrayList<>();
        byte[] h = new byte[44];
        try (RandomAccessFile raf = new RandomAccessFile(wav, "r")) {
            if (raf.length() < 44) throw new Exception("El audio está vacío.");
            raf.readFully(h);
            int ch = (h[22] & 0xff) | ((h[23] & 0xff) << 8);
            long rate = (h[24] & 0xffL) | ((h[25] & 0xffL) << 8) | ((h[26] & 0xffL) << 16) | ((h[27] & 0xffL) << 24);
            int bits = (h[34] & 0xff) | ((h[35] & 0xff) << 8);
            long bps = rate * Math.max(1, ch) * Math.max(8, bits) / 8;
            long data = raf.length() - 44;
            long chunk = Math.min(bps * CHUNK_SECONDS, MAX_UPLOAD - 44);
            chunk -= chunk % Math.max(1, ch * bits / 8);
            if (bps <= 0 || data <= chunk) { out.add(wav); return out; }
            byte[] buf = new byte[64 * 1024];
            long pos = 0;
            int idx = 0;
            while (pos < data) {
                long len = Math.min(chunk, data - pos);
                File part = new File(tmpDir, "acta_part_" + (idx++) + ".wav");
                try (FileOutputStream fo = new FileOutputStream(part)) {
                    byte[] hh = h.clone();
                    put32(hh, 4, (int) (36 + len));
                    put32(hh, 40, (int) len);
                    fo.write(hh);
                    raf.seek(44 + pos);
                    long left = len;
                    while (left > 0) {
                        int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                        if (n <= 0) break;
                        fo.write(buf, 0, n);
                        left -= n;
                    }
                }
                out.add(part);
                pos += len;
            }
        }
        return out;
    }

    private static void put32(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    /** Transcribe un archivo y añade sus frases (con tiempos si el servicio los da) a out. */
    private static void transcribe(File audio, String key, String hint, String mime, double offset, List<Seg> out) throws Exception {
        String resp;
        try {
            resp = transcribeRaw(audio, key, hint, mime, "verbose_json");
        } catch (Exception e) {
            String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (!(m.contains("(400)") && (m.contains("format") || m.contains("verbose")))) throw e;
            resp = transcribeRaw(audio, key, hint, mime, "json");
        }
        JSONObject o = new JSONObject(resp);
        JSONArray segs = o.optJSONArray("segments");
        if (segs != null && segs.length() > 0) {
            for (int i = 0; i < segs.length(); i++) {
                JSONObject sg = segs.optJSONObject(i);
                if (sg == null) continue;
                String t = sg.optString("text", "").trim();
                if (t.isEmpty()) continue;
                // Descarta tramos que Whisper marca como casi seguro silencio.
                if (sg.optDouble("no_speech_prob", 0) > 0.85 && sg.optDouble("avg_logprob", 0) < -0.8) continue;
                double st = sg.optDouble("start", 0);
                out.add(new Seg(offset + st, offset + sg.optDouble("end", st), t));
            }
        } else {
            String t = o.optString("text", "").trim();
            if (!t.isEmpty()) out.add(new Seg(-1, t));
        }
    }

    private static String transcribeRaw(File audio, String key, String hint, String mime, String format) throws Exception {
        String boundary = "----acta" + System.currentTimeMillis();
        ByteArrayOutputStream pre = new ByteArrayOutputStream();
        field(pre, boundary, "model", TRANSCRIBE_MODEL);
        field(pre, boundary, "response_format", format);
        field(pre, boundary, "language", "es");
        field(pre, boundary, "temperature", "0");
        field(pre, boundary, "prompt", hint);
        pre.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        pre.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + audio.getName() + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        pre.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] head = pre.toByteArray();
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + "/audio/transcriptions").openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(300000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setFixedLengthStreamingMode(head.length + audio.length() + tail.length);
        OutputStream out = conn.getOutputStream();
        out.write(head);
        try (FileInputStream fis = new FileInputStream(audio)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = fis.read(buf)) > 0) out.write(buf, 0, n);
        }
        out.write(tail);
        out.flush();
        out.close();
        int code = conn.getResponseCode();
        String resp = read(code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (code == 401) throw new Exception("Clave de IA no válida. Revísala en Ajustes.");
        if (code == 429) throw new Exception("Límite gratuito de Groq alcanzado por ahora. Prueba en unos minutos.");
        if (code == 413) throw new Exception("La grabación es demasiado grande para Groq.");
        if (code >= 400) throw new Exception("Error al transcribir (" + code + "): " + brief(resp));
        return resp;
    }

    /** Frases que Whisper "alucina" en silencios y bucles repetidos. */
    private static final Pattern HALLUCINATIONS = Pattern.compile(
            "(?i)(subt[ií]tulos?( realizados)? por la comunidad de amara\\.org|subt[ií]tulos realizados por .{0,40}"
                    + "|gracias por ver( el v[ií]deo)?|¡?suscr[ií]bete( al canal)?!?|[\\[(]m[uú]sica[\\])]"
                    + "|amara\\.org)");

    private static String cleanTranscript(String t) {
        if (t == null) return "";
        String s = HALLUCINATIONS.matcher(t).replaceAll(" ");
        // Quita frases repetidas en bucle (típico de Whisper en silencios).
        String[] parts = s.split("(?<=[.!?…])\\s+");
        StringBuilder sb = new StringBuilder();
        String prev = null;
        int repeats = 0;
        for (String p : parts) {
            String q = p.trim();
            if (q.isEmpty()) continue;
            if (q.equalsIgnoreCase(prev)) { if (++repeats >= 1) continue; } else repeats = 0;
            sb.append(q).append(' ');
            prev = q;
        }
        return sb.toString().replaceAll("(^|\\s)[.,;:]+(?=\\s|$)", " ").replaceAll("\\s+", " ").trim();
    }

    private static int wordCount(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        return s.trim().split("\\s+").length;
    }

    // ------------------------------------------------------------------ HTTP

    private static final class Http { int code; String body; }

    private static Http post(String url, String key, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(180000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        Http r = new Http();
        r.code = conn.getResponseCode();
        r.body = read(r.code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        return r;
    }

    private static Http get(String url, String key) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Authorization", "Bearer " + key);
        Http r = new Http();
        r.code = conn.getResponseCode();
        r.body = read(r.code < 400 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        return r;
    }

    private static void field(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }

    private static String brief(String s) {
        if (s == null) return "";
        try {
            JSONObject o = new JSONObject(s);
            JSONObject err = o.optJSONObject("error");
            if (err != null) return err.optString("message", s);
        } catch (Exception ignored) { }
        return s.length() > 160 ? s.substring(0, 160) : s;
    }
}
