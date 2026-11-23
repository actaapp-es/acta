package com.acta.recorder;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Agente de voz de Acta. Recibe lo que dijiste tras «Hola Acta», se lo pasa a Groq para entender la intención
 * y ejecuta: llamar a un contacto, guardar un contacto o programar una llamada dentro de X tiempo.
 */
final class VoiceAgent {
    private VoiceAgent() { }

    private static final String SYSTEM =
            "Eres «Acta», el asistente de voz de una app de teléfono. Recibes la frase que el usuario ha dicho justo después "
            + "de decir «Hola Acta». Viene de un reconocedor de voz y puede tener errores (nombres propios y números sobre todo). "
            + "Entiende qué quiere y responde SOLO con un objeto JSON válido, en español, con esta forma:\n"
            + "{\"acciones\": [ ... ], \"respuesta\": \"...\"}\n\n"
            + "Acciones posibles (puede haber varias, en el orden pedido):\n"
            + "- {\"accion\":\"llamar\",\"contacto\":\"nombre tal como lo dijo\"}  o  {\"accion\":\"llamar\",\"numero\":\"solo dígitos\"}\n"
            + "- {\"accion\":\"guardar_contacto\",\"nombre\":\"nombre completo\",\"numero\":\"solo dígitos, con + si dice prefijo\"}\n"
            + "- {\"accion\":\"programar_llamada\",\"contacto\":\"nombre\",\"minutos\":N}  o  con \"numero\" en vez de \"contacto\"\n\n"
            + "Reglas:\n"
            + "- Números dichos en palabras pásalos a dígitos («seis uno dos tres cuatro cinco seis siete ocho» → 612345678). "
            + "Si dice un número de 9 dígitos no inventes prefijo.\n"
            + "- minutos: «dentro de 2 horas» = 120, «en media hora» = 30, «dentro de una hora y media» = 90, «en 45 minutos» = 45. "
            + "Siempre un entero positivo. Si no dice cuándo, no programes: acciones [] y pregunta cuándo.\n"
            + "- «llámame a», «ponme con», «marca a» = llamar. «guarda», «apunta», «añade el contacto» = guardar_contacto. "
            + "«avísame para llamar a», «recuérdame llamar a», «agenda una llamada» = programar_llamada.\n"
            + "- Usa solo esas acciones. Si pide otra cosa (mandar un mensaje, buscar algo…) o no se entiende, acciones [] y en "
            + "respuesta di en una frase qué sabes hacer.\n"
            + "- Si falta un dato imprescindible (a quién, el número, cuándo), acciones [] y pídelo en respuesta.\n"
            + "- No inventes nombres ni números que no se hayan dicho.\n"
            + "- respuesta: frase muy corta (máximo 18 palabras) que se dirá en voz alta. Cuando hay acciones, resume lo que vas a hacer.";

    /** Resultado: texto que se dirá en voz alta. */
    static String run(Context c, String said) {
        String key = Summarizer.key(c);
        if (key.isEmpty()) return "Falta tu clave de Groq. Añádela en Ajustes.";
        String raw;
        try {
            raw = Summarizer.chat(c, key, SYSTEM, "Frase del usuario: " + said);
        } catch (Summarizer.RateLimit rl) {
            return "Groq ha llegado a su límite. Prueba otra vez en un momento.";
        } catch (Exception e) {
            return "No he podido hablar con Groq. Revisa la conexión.";
        }
        JSONObject o;
        try {
            int a = raw.indexOf('{'), b = raw.lastIndexOf('}');
            o = new JSONObject(raw.substring(a, b + 1));
        } catch (Exception e) {
            return "No te he entendido. Prueba otra vez.";
        }
        JSONArray acts = o.optJSONArray("acciones");
        String reply = o.optString("respuesta", "").trim();
        if (acts == null || acts.length() == 0) return reply.isEmpty() ? "No te he entendido. Prueba otra vez." : reply;
        List<String> out = new ArrayList<>();
        boolean call = false;
        for (int i = 0; i < acts.length() && i < 4; i++) {
            JSONObject a = acts.optJSONObject(i);
            if (a == null) continue;
            String kind = a.optString("accion", "");
            try {
                if ("llamar".equals(kind)) {
                    if (call) continue; // una sola llamada por orden
                    String r = doCall(c, a);
                    out.add(r);
                    call = r.startsWith("Llamando");
                } else if ("guardar_contacto".equals(kind)) out.add(doSave(c, a));
                else if ("programar_llamada".equals(kind)) out.add(doSchedule(c, a));
            } catch (Throwable t) {
                out.add("No he podido completar esa acción.");
            }
        }
        if (out.isEmpty()) return reply.isEmpty() ? "No te he entendido. Prueba otra vez." : reply;
        return TextUtils.join(" ", out);
    }

    // ------------------------------------------------------------------ Acciones

    private static final class Target { String name, number; String problem; }

    private static Target resolve(Context c, JSONObject a) {
        Target t = new Target();
        String digits = clean(a.optString("numero", ""));
        String who = a.optString("contacto", "").trim();
        if (who.isEmpty()) who = a.optString("nombre", "").trim();
        if (!who.isEmpty()) {
            if (!Util.has(c, Manifest.permission.READ_CONTACTS)) { t.problem = "Necesito permiso para leer tus contactos."; return t; }
            List<String[]> hits = findContacts(c, who);
            if (hits.size() == 1 || (hits.size() > 1 && sameNumber(hits.get(0)[1], hits.get(1)[1]) && distinctNames(hits) == 1)) {
                t.name = hits.get(0)[0];
                t.number = hits.get(0)[1];
                return t;
            }
            if (hits.size() > 1) {
                List<String> names = new ArrayList<>();
                for (String[] h : hits) if (!names.contains(h[0]) && names.size() < 3) names.add(h[0]);
                t.problem = "Hay varios contactos: " + TextUtils.join(", ", names) + ". Dime el nombre completo.";
                return t;
            }
            if (digits.isEmpty()) { t.problem = "No encuentro a " + who + " en tus contactos."; return t; }
        }
        if (digits.length() >= 6) {
            t.number = digits;
            t.name = Util.contactName(c, digits);
            return t;
        }
        t.problem = "No he entendido a quién.";
        return t;
    }

    private static String doCall(Context c, JSONObject a) {
        Target t = resolve(c, a);
        if (t.problem != null) return t.problem;
        SimInfo.Sim p = SimInfo.dual(c) ? SimInfo.preferred(c, t.number) : null;
        Util.placeCall(c, t.number, p != null ? p.handle : null);
        return "Llamando a " + (!TextUtils.isEmpty(t.name) ? t.name : spell(t.number)) + ".";
    }

    private static String doSchedule(Context c, JSONObject a) {
        int min = a.optInt("minutos", 0);
        if (min <= 0) return "No he entendido para cuándo.";
        if (min > 7 * 24 * 60) return "Solo puedo programar hasta dentro de una semana.";
        Target t = resolve(c, a);
        if (t.problem != null) return t.problem;
        long at = System.currentTimeMillis() + min * 60_000L;
        QuickActions.scheduleCall(c, t.number, t.name, at);
        String who = !TextUtils.isEmpty(t.name) ? t.name : spell(t.number);
        return "Hecho. Te aviso a las " + Util.time(at) + " para llamar a " + who + ".";
    }

    private static String doSave(Context c, JSONObject a) {
        String name = a.optString("nombre", "").trim();
        if (name.isEmpty()) name = a.optString("contacto", "").trim();
        String number = clean(a.optString("numero", ""));
        if (name.isEmpty()) return "No he entendido el nombre del contacto.";
        if (number.length() < 6) return "No he entendido el número. Dímelo otra vez, cifra a cifra.";
        if (!Util.has(c, Manifest.permission.WRITE_CONTACTS)) return "Necesito permiso para guardar contactos. Actívalo en Ajustes.";
        if (Util.has(c, Manifest.permission.READ_CONTACTS)) {
            String ex = Util.contactName(c, number);
            if (!TextUtils.isEmpty(ex)) return "Ese número ya está guardado como " + ex + ".";
        }
        try {
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
            c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            c.sendBroadcast(new android.content.Intent(Util.ACTION_UPDATED).setPackage(c.getPackageName()));
            return "Guardado " + name + ".";
        } catch (Throwable t) {
            return "No he podido guardar el contacto.";
        }
    }

    // ------------------------------------------------------------------ Contactos

    /** Contactos cuyo nombre contiene todas las palabras dichas. Devuelve {nombre, número}; mejores primero. */
    static List<String[]> findContacts(Context c, String query) {
        String[] q = norm(query).split(" ");
        List<String[]> best = new ArrayList<>();
        int bestScore = 0;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE},
                    null, null, null);
            while (cur != null && cur.moveToNext()) {
                String name = cur.getString(0), number = cur.getString(1);
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(number)) continue;
                String n = " " + norm(name);
                int score = 0;
                boolean all = true;
                for (String w : q) {
                    if (w.isEmpty()) continue;
                    if (n.contains(" " + w + " ") || n.endsWith(" " + w)) score += 3;
                    else if (n.contains(" " + w)) score += 2;
                    else { all = false; break; }
                }
                if (!all || score == 0) continue;
                if (n.trim().equals(norm(query))) score += 10;
                if (score > bestScore) { bestScore = score; best.clear(); }
                if (score == bestScore) best.add(new String[]{name, number});
            }
        } catch (Throwable ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return best;
    }

    private static int distinctNames(List<String[]> l) {
        List<String> s = new ArrayList<>();
        for (String[] h : l) if (!s.contains(h[0])) s.add(h[0]);
        return s.size();
    }

    private static boolean sameNumber(String a, String b) {
        String x = ContactMedia.key(a), y = ContactMedia.key(b);
        return x != null && x.equals(y);
    }

    private static String norm(String s) {
        String t = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[^0-9+]", "");
    }

    /** Número en grupos de cifras para que el sintetizador lo lea con calma. */
    private static String spell(String number) {
        return number.replaceAll("(\\d{3})(?=\\d)", "$1 ");
    }

    // ------------------------------------------------------------------ Transcripción del comando

    static String transcribe(Context c, File wav) throws Exception {
        String key = Summarizer.key(c);
        if (key.isEmpty()) throw new Exception("Falta la clave de Groq");
        String resp = Summarizer.transcribeRaw(wav, key,
                "Orden de voz a un teléfono: llama a, guarda el contacto, programa una llamada dentro de dos horas. "
                        + "Nombres propios y números de teléfono.", "audio/wav", "json");
        return new JSONObject(resp).optString("text", "").trim();
    }
}
