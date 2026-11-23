package com.acta.recorder;

import android.content.Context;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Detector local de «Hola Acta» con tu propia voz: grabas la frase unas veces y el móvil la compara (MFCC + DTW).
 * Funciona sin red y sin el reconocedor de voz del sistema, así que el micrófono queda abierto de forma continua
 * y estable, sin pitidos ni reinicios. Un acierto local se confirma después con Groq antes de hacer nada.
 */
final class WakeWord {
    private WakeWord() { }

    static final int RATE = 16000, HOP = 160, WIN = 400, NFFT = 512, MELS = 26, COEF = 12;
    static final int MIN_SAMPLES_TEMPLATE = RATE * 4 / 10, MAX_SAMPLES_TEMPLATE = RATE * 2;

    // ------------------------------------------------------------------ Características (MFCC)

    private static final double[] HAMMING = new double[WIN];
    private static final double[][] MEL = new double[MELS][NFFT / 2 + 1];
    private static final double[][] DCT = new double[COEF][MELS];
    static {
        for (int i = 0; i < WIN; i++) HAMMING[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (WIN - 1));
        double lo = mel(100), hi = mel(7000);
        double[] pts = new double[MELS + 2];
        for (int i = 0; i < pts.length; i++) pts[i] = imel(lo + (hi - lo) * i / (MELS + 1));
        for (int m = 0; m < MELS; m++) {
            for (int k = 0; k <= NFFT / 2; k++) {
                double f = (double) k * RATE / NFFT;
                if (f >= pts[m] && f <= pts[m + 1]) MEL[m][k] = (f - pts[m]) / (pts[m + 1] - pts[m]);
                else if (f > pts[m + 1] && f <= pts[m + 2]) MEL[m][k] = (pts[m + 2] - f) / (pts[m + 2] - pts[m + 1]);
            }
        }
        for (int i = 0; i < COEF; i++)
            for (int j = 0; j < MELS; j++) DCT[i][j] = Math.cos(Math.PI * (i + 1) * (j + 0.5) / MELS);
    }
    private static double mel(double f) { return 2595 * Math.log10(1 + f / 700); }
    private static double imel(double m) { return 700 * (Math.pow(10, m / 2595) - 1); }

    /** MFCC (12 coeficientes, sin la energía) con la media restada: no depende del volumen ni del micrófono. */
    static float[][] features(short[] pcm, int len) {
        int frames = len < WIN ? 0 : 1 + (len - WIN) / HOP;
        float[][] out = new float[frames][COEF];
        double[] re = new double[NFFT], im = new double[NFFT], mel = new double[MELS];
        for (int f = 0; f < frames; f++) {
            int o = f * HOP;
            for (int i = 0; i < NFFT; i++) { re[i] = 0; im[i] = 0; }
            double prev = o > 0 ? pcm[o - 1] : 0;
            for (int i = 0; i < WIN; i++) {
                double s = pcm[o + i];
                re[i] = (s - 0.97 * prev) * HAMMING[i];
                prev = s;
            }
            fft(re, im);
            for (int m = 0; m < MELS; m++) {
                double e = 0;
                for (int k = 0; k <= NFFT / 2; k++) if (MEL[m][k] != 0) e += MEL[m][k] * (re[k] * re[k] + im[k] * im[k]);
                mel[m] = Math.log(e + 1e-3);
            }
            for (int c = 0; c < COEF; c++) {
                double v = 0;
                for (int m = 0; m < MELS; m++) v += DCT[c][m] * mel[m];
                out[f][c] = (float) v;
            }
        }
        if (frames > 0) {
            for (int c = 0; c < COEF; c++) {
                double mean = 0;
                for (int f = 0; f < frames; f++) mean += out[f][c];
                mean /= frames;
                for (int f = 0; f < frames; f++) out[f][c] -= (float) mean;
            }
        }
        return out;
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = i + j + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double t = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = t;
                }
            }
        }
    }

    // ------------------------------------------------------------------ DTW

    private static double dist(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) { double d = a[i] - b[i]; s += d * d; }
        return Math.sqrt(s);
    }

    /** DTW entre plantilla y fragmento. Con openEnd el fragmento puede seguir más allá de la plantilla (la orden). */
    static double dtw(float[][] t, float[][] x, boolean openEnd) {
        int n = t.length, m = x.length;
        if (n == 0 || m == 0) return Double.MAX_VALUE;
        final double INF = 1e18;
        double[][] d = new double[n][m];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, (int) (i * 0.55) - 6), hi = Math.min(m - 1, (int) (i * 1.9) + 6);
            for (int j = 0; j < m; j++) d[i][j] = INF;
            for (int j = lo; j <= hi; j++) {
                double c = dist(t[i], x[j]);
                double best;
                if (i == 0 && j == 0) best = 0;
                else {
                    best = INF;
                    if (i > 0 && d[i - 1][j] < best) best = d[i - 1][j];
                    if (j > 0 && d[i][j - 1] < best) best = d[i][j - 1];
                    if (i > 0 && j > 0 && d[i - 1][j - 1] < best) best = d[i - 1][j - 1];
                }
                if (best < INF) d[i][j] = best + c;
            }
        }
        double res = INF;
        if (openEnd) {
            for (int j = Math.max(0, (int) (n * 0.55) - 6); j < m; j++)
                if (d[n - 1][j] < INF) res = Math.min(res, d[n - 1][j] / (n + j + 2));
        } else if (d[n - 1][m - 1] < INF) res = d[n - 1][m - 1] / (n + m);
        return res;
    }

    /** Menor distancia del fragmento a las plantillas (fin abierto). */
    static double score(List<float[][]> templates, float[][] x) {
        double best = Double.MAX_VALUE;
        for (float[][] t : templates) best = Math.min(best, dtw(t, x, true));
        return best;
    }

    // ------------------------------------------------------------------ Plantillas

    private static File file(Context c) { return new File(c.getFilesDir(), "wake_templates.bin"); }

    static boolean enrolled(Context c) { return file(c).exists() && threshold(c) > 0; }

    static double threshold(Context c) { return Double.longBitsToDouble(Util.prefs(c).getLong("wake_thr", 0)); }

    static void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
        Util.prefs(c).edit().remove("wake_thr").apply();
    }

    private static List<float[][]> cache;
    private static long cacheStamp;

    static synchronized List<float[][]> load(Context c) {
        File f = file(c);
        if (!f.exists()) return new ArrayList<>();
        if (cache != null && cacheStamp == f.lastModified()) return cache;
        List<float[][]> out = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(f)))) {
            int n = in.readInt();
            for (int k = 0; k < n; k++) {
                int frames = in.readInt();
                float[][] t = new float[frames][COEF];
                for (int i = 0; i < frames; i++) for (int j = 0; j < COEF; j++) t[i][j] = in.readFloat();
                out.add(t);
            }
        } catch (Throwable ignored) { return new ArrayList<>(); }
        cache = out;
        cacheStamp = f.lastModified();
        return out;
    }

    /** Guarda las muestras y calcula el umbral: más holgado que lo que se parecen entre sí tus propias muestras. */
    static boolean save(Context c, List<short[]> samples) {
        if (samples.size() < 3) return false;
        List<float[][]> feats = new ArrayList<>();
        for (short[] s : samples) feats.add(features(s, s.length));
        double maxD = 0;
        for (int i = 0; i < feats.size(); i++)
            for (int j = 0; j < feats.size(); j++) {
                if (i == j) continue;
                double d = dtw(feats.get(i), feats.get(j), false);
                if (d < 1e17) maxD = Math.max(maxD, d);
            }
        if (maxD <= 0) return false;
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(file(c))))) {
            out.writeInt(feats.size());
            for (float[][] t : feats) {
                out.writeInt(t.length);
                for (float[] row : t) for (float v : row) out.writeFloat(v);
            }
        } catch (Throwable t) { return false; }
        Util.prefs(c).edit().putLong("wake_thr", Double.doubleToLongBits(maxD * 1.35)).apply();
        synchronized (WakeWord.class) { cache = null; }
        return true;
    }

    // ------------------------------------------------------------------ Voz / silencio

    static double rms(short[] b, int from, int len) {
        double s = 0;
        for (int i = from; i < from + len; i++) s += (double) b[i] * b[i];
        return len == 0 ? 0 : Math.sqrt(s / len);
    }
}
