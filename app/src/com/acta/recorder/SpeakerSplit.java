package com.acta.recorder;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Quién habla en cada frase. Con la pista de tu voz (energía cada 100 ms) se compara con el audio
 * completo de la llamada: donde tu pista suena casi tan fuerte como la llamada, hablas tú; donde
 * la llamada suena y tu pista no, habla la otra persona.
 */
final class SpeakerSplit {

    private SpeakerSplit() { }

    static final char OWNER = 'y', OTHER = 'o', UNKNOWN = '?';

    // ------------------------------------------------------------------ Ajustes

    static boolean enabled(Context c) {
        return Util.prefs(c).getBoolean("split_on", true) && !bad(c);
    }

    static boolean bad(Context c) { return Util.prefs(c).getBoolean("split_bad", false); }

    static void markBad(Context c) { Util.prefs(c).edit().putBoolean("split_bad", true).apply(); }

    static void setEnabled(Context c, boolean on) {
        Util.prefs(c).edit().putBoolean("split_on", on).putBoolean("split_bad", false).apply();
    }

    /** Nombre del dueño del móvil (opcional, para la IA). */
    static String ownerName(Context c) { return Util.prefs(c).getString("owner_name", "").trim(); }

    static String otherLabel(Db.Call call) {
        if (call != null && !TextUtils.isEmpty(call.name)) {
            String n = call.name.trim();
            return n.length() > 24 ? n.substring(0, 24) : n;
        }
        return "Otra persona";
    }

    static String label(char spk, Db.Call call) {
        return spk == OWNER ? "Tú" : spk == OTHER ? otherLabel(call) : "";
    }

    // ------------------------------------------------------------------ Archivos

    static File vadFor(Context c, String recordingName) {
        File d = new File(c.getFilesDir(), "vad");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, recordingName + ".vad");
    }

    static boolean validVad(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            if (r.length() < 12) return false;
            byte[] h = new byte[4];
            r.readFully(h);
            return h[0] == 'A' && h[1] == 'V' && h[2] == 'A' && h[3] == 'D';
        } catch (Throwable t) {
            return false;
        }
    }

    static final class Vad {
        int source;      // 2 = VOICE_UPLINK (tu voz por la línea), 1 = micrófono
        int delayMs;     // cuándo empezó respecto al audio de la llamada
        int[] energy;    // un valor cada 100 ms
    }

    static Vad readVad(File f) {
        if (f == null || !f.exists() || !validVad(f)) return null;
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] all = new byte[(int) r.length()];
            r.readFully(all);
            Vad v = new Vad();
            v.source = all[5] & 0xff;
            v.delayMs = (all[6] & 0xff) | ((all[7] & 0xff) << 8) | ((all[8] & 0xff) << 16) | ((all[9] & 0xff) << 24);
            int n = (all.length - 10) / 2;
            v.energy = new int[n];
            for (int i = 0; i < n; i++) v.energy[i] = (all[10 + 2 * i] & 0xff) | ((all[11 + 2 * i] & 0xff) << 8);
            return v.energy.length > 10 ? v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Energía del audio completo cada 100 ms (WAV PCM 16 bits mono). */
    static int[] wavEnergy(File wav) {
        try (RandomAccessFile r = new RandomAccessFile(wav, "r")) {
            byte[] h = new byte[44];
            r.readFully(h);
            int rate = (h[24] & 0xff) | ((h[25] & 0xff) << 8) | ((h[26] & 0xff) << 16) | ((h[27] & 0xff) << 24);
            int ch = Math.max(1, (h[22] & 0xff) | ((h[23] & 0xff) << 8));
            if (rate <= 0) return null;
            int frameBytes = rate / 10 * 2 * ch;
            long frames = (r.length() - 44) / frameBytes;
            int[] out = new int[(int) frames];
            byte[] b = new byte[frameBytes];
            for (int i = 0; i < frames; i++) {
                r.readFully(b);
                double sum = 0;
                int samples = frameBytes / 2;
                for (int k = 0; k < samples; k++) {
                    short s = (short) ((b[2 * k] & 0xff) | (b[2 * k + 1] << 8));
                    sum += (double) s * s;
                }
                out[i] = (int) Math.min(32767, Math.sqrt(sum / samples));
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Separación por audio

    /**
     * Etiqueta cada frase con OWNER / OTHER / UNKNOWN a partir de la pista de tu voz.
     * starts/ends en segundos. Devuelve null si no hay datos suficientes o no se distinguen.
     */
    static char[] fromAudio(int[] mix, Vad vad, double[] starts, double[] ends) {
        if (mix == null || vad == null || starts.length == 0) return null;
        int off = Math.round(vad.delayMs / 100f);
        // Ruido de fondo de la llamada: por debajo de esto no hay voz.
        int[] sorted = mix.clone();
        Arrays.sort(sorted);
        int floor = Math.max(40, sorted[(int) (sorted.length * 0.2)] * 2);
        double[] ratio = new double[starts.length];
        boolean[] valid = new boolean[starts.length];
        for (int i = 0; i < starts.length; i++) {
            int a = (int) Math.floor(starts[i] * 10), b = (int) Math.ceil(ends[i] * 10);
            double so = 0, sm = 0;
            int active = 0;
            for (int f = Math.max(0, a); f < Math.min(mix.length, b); f++) {
                if (mix[f] < floor) continue;
                int vi = f - off;
                if (vi < 0 || vi >= vad.energy.length) continue;
                so += vad.energy[vi];
                sm += mix[f];
                active++;
            }
            if (active >= 3 && sm > 0) {
                ratio[i] = Math.log((so + 1) / (sm + 1));
                valid[i] = true;
            }
        }
        int nValid = 0;
        for (boolean v : valid) if (v) nValid++;
        if (nValid < 2) return null;

        // Se separan los dos grupos de frases (método de Otsu): en las tuyas, tu pista suena mucho
        // más respecto a la llamada que en las de la otra persona.
        double[] v = new double[nValid];
        int k = 0;
        for (int i = 0; i < ratio.length; i++) if (valid[i]) v[k++] = ratio[i];
        Arrays.sort(v);
        double best = -1, threshold = v[v.length / 2];
        for (int cut = 1; cut < v.length; cut++) {
            double m1 = 0, m2 = 0;
            for (int j = 0; j < cut; j++) m1 += v[j];
            for (int j = cut; j < v.length; j++) m2 += v[j];
            m1 /= cut;
            m2 /= (v.length - cut);
            double w1 = (double) cut / v.length, w2 = 1 - w1;
            double between = w1 * w2 * (m1 - m2) * (m1 - m2);
            if (between > best) { best = between; threshold = (v[cut - 1] + v[cut]) / 2; }
        }
        // Si los dos grupos no están claramente separados (p. ej. solo habla uno), no se decide por audio.
        double lo = 0, hi = 0;
        int nl = 0, nh = 0;
        for (double x : v) { if (x < threshold) { lo += x; nl++; } else { hi += x; nh++; } }
        double minGap = vad.source == 2 ? 0.6 : 0.7;
        if (nl == 0 || nh == 0 || (hi / nh) - (lo / nl) < minGap) return null;
        char[] out = new char[starts.length];
        for (int i = 0; i < out.length; i++) out[i] = !valid[i] ? UNKNOWN : ratio[i] >= threshold ? OWNER : OTHER;
        return out;
    }

    /** Frases sin decidir: toman el hablante de la frase vecina más cercana. */
    static void fillGaps(char[] spk) {
        for (int i = 0; i < spk.length; i++) {
            if (spk[i] != UNKNOWN) continue;
            char left = UNKNOWN, right = UNKNOWN;
            for (int j = i - 1; j >= 0; j--) if (spk[j] != UNKNOWN) { left = spk[j]; break; }
            for (int j = i + 1; j < spk.length; j++) if (spk[j] != UNKNOWN) { right = spk[j]; break; }
            spk[i] = left != UNKNOWN ? left : right;
        }
    }

    static int unknownCount(char[] spk) {
        int n = 0;
        if (spk != null) for (char c : spk) if (c == UNKNOWN) n++;
        return n;
    }

    /** Une frases seguidas del mismo hablante en turnos: «Tú: …\n\nLaura: …». */
    static String turns(List<String> texts, char[] spk, Db.Call call) {
        StringBuilder sb = new StringBuilder();
        char cur = 0;
        for (int i = 0; i < texts.size(); i++) {
            char s = spk == null ? UNKNOWN : spk[i];
            if (s != cur) {
                if (sb.length() > 0) sb.append("\n\n");
                String l = label(s, call);
                if (!l.isEmpty()) sb.append(l).append(": ");
                cur = s;
            } else {
                sb.append(' ');
            }
            sb.append(texts.get(i));
        }
        return sb.toString();
    }

    static List<String> list(String... s) { return new ArrayList<>(Arrays.asList(s)); }
}
