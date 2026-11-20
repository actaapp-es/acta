package com.acta.recorder;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Locale;

import io.github.muntashirakon.adb.AdbStream;

/**
 * Motor de grabación de ambas voces con el cliente ADB propio de Acta (sin apps externas).
 *
 * Diseño (verificado con un simulador de adbd):
 *  - El grabador (AdbRecorderMain, motor AudioRecord + VOICE_CALL) se lanza como proceso
 *    HIJO en segundo plano y en SESIÓN PROPIA (setsid + nohup), con su salida a un log.
 *    Así no es el proceso que adbd vigila: cerrar o perder el canal ADB no lo mata y graba
 *    la llamada entera.
 *  - Un único canal lanza el grabador y espera a que el log diga "REC" (confirmación fiable
 *    sin abrir canales extra). El lanzamiento es idempotente: reintentar no duplica grabadores.
 *  - Al colgar, un único canal crea el archivo de parada, espera a que el WAV se cierre y lo
 *    envía. Se comprueba que llega completo (cabecera WAV) y se reintenta si no.
 */
final class NativeAdbManager {

    private static final class Rec {
        String id;
    }

    private static volatile Rec current;      // grabación activa (null si no se graba)
    private static volatile boolean busy;      // arranque/parada en curso
    private static volatile String lastSource = "";
    private static volatile String lastError = "";
    private static volatile boolean connectedFlag = false;

    private NativeAdbManager() { }

    static String lastError() { return lastError; }
    static String lastSource() { return lastSource; }

    /** Hay una grabación activa o una transición en curso (no tocar la conexión). */
    static boolean isBusy() { return current != null || busy; }

    // ================================================================== Conexión

    /** Estado cacheado: NO toca el ADB, para poder llamarse desde el hilo principal. */
    static boolean connected(Context c) { return connectedFlag; }

    /** Comprueba el estado real. Solo en segundo plano. */
    static boolean refreshConnected(Context c) {
        try {
            connectedFlag = ActaAdb.get(c).isConnected();
        } catch (Throwable t) {
            connectedFlag = false;
        }
        return connectedFlag;
    }

    static boolean pair(Context c, String host, int port, String code) {
        try {
            return ActaAdb.get(c).pair(host, port, code);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Comprueba rápido si el puerto TCP está abierto, para no colgarse en un puerto cerrado. */
    static boolean portOpen(String host, int port, int timeoutMs) {
        java.net.Socket s = new java.net.Socket();
        try {
            s.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    /** Conecta al ADB del propio móvil (puerto de 'adb tcpip 5555'). */
    static synchronized boolean connect(Context c, String host, int port) {
        lastError = "";
        try {
            // La librería devuelve "false" si ya estaba conectado: eso es éxito, no fallo.
            if (ActaAdb.get(c).isConnected()) { connectedFlag = true; return true; }
        } catch (Throwable ignored) { }
        if (!portOpen(host, port, 1500)) {
            lastError = "puerto cerrado (tras reiniciar el móvil, repite 'adb tcpip 5555' en el PC)";
            connectedFlag = false;
            return false;
        }
        try {
            // Limpia una conexión anterior medio muerta antes de abrir otra.
            try { ActaAdb.get(c).disconnect(); } catch (Throwable ignored) { }
            boolean ok = ActaAdb.get(c).connect(host, port);
            if (!ok) ok = ActaAdb.get(c).isConnected();
            if (!ok) lastError = "sin respuesta (¿puerto correcto y 'adb tcpip 5555' hecho?)";
            connectedFlag = ok;
            if (ok) AdbWatch.restored(c);
            return ok;
        } catch (io.github.muntashirakon.adb.AdbPairingRequiredException e) {
            lastError = "el puerto pide emparejamiento (usa el de 'adb tcpip 5555')";
            connectedFlag = false;
            return false;
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            connectedFlag = false;
            return false;
        }
    }

    static void disconnect(Context c) {
        connectedFlag = false;
        try { ActaAdb.get(c).disconnect(); } catch (Throwable ignored) { }
    }

    /** Desconecta y abre una conexión nueva y limpia. */
    static synchronized boolean forceReconnect(Context c) {
        try { ActaAdb.get(c).disconnect(); } catch (Throwable ignored) { }
        connectedFlag = false;
        String host = savedHost(c);
        int port = savedPort(c);
        if (host == null || port == 0) { lastError = "no hay conexión guardada (pulsa Conectar en Ajustes)"; return false; }
        return connect(c, host, port);
    }

    /** Reconecta si hace falta, con la clave ya autorizada. */
    static synchronized boolean ensureConnected(Context c) {
        if (refreshConnected(c)) return true;
        String host = savedHost(c);
        int port = savedPort(c);
        if (host == null || port == 0) return false;
        return connect(c, host, port);
    }

    static void saveEndpoint(Context c, String host, int port) {
        Util.prefs(c).edit().putString("adb_host", host).putInt("adb_port", port).apply();
    }

    static String savedHost(Context c) { return Util.prefs(c).getString("adb_host", "127.0.0.1"); }

    static int savedPort(Context c) { return Util.prefs(c).getInt("adb_port", 0); }

    /** IP IPv4 propia del móvil (preferible wlan). */
    static String deviceIp() {
        String best = null;
        try {
            java.util.Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                try {
                    if (ni.isLoopback()) continue;
                    boolean wlan = ni.getName() != null && ni.getName().toLowerCase(Locale.ROOT).contains("wlan");
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                        if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                            if (wlan) return a.getHostAddress();
                            if (best == null) best = a.getHostAddress();
                        }
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return best;
    }

    // ================================================================== Keepalive

    private static Thread keepAlive;

    /** Comprueba cada 5 s que la conexión siga viva y reconecta si cayó (sin abrir canales). */
    static synchronized void startKeepAlive(Context c) {
        final Context app = c.getApplicationContext();
        if (keepAlive != null && keepAlive.isAlive()) return;
        keepAlive = new Thread(new Runnable() {
            @Override public void run() {
                int fails = 0;
                while (true) {
                    try {
                        if (Util.nativeAdbEnabled(app) && savedPort(app) != 0 && !isBusy()) {
                            if (ensureConnected(app)) {
                                fails = 0;
                                AdbWatch.restored(app);
                            } else if (++fails == 2) { // ~10 s sin conexión: avisar
                                AdbWatch.lost(app, AdbWatch.reason(lastError));
                            }
                        }
                    } catch (Throwable ignored) { }
                    try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
                }
            }
        }, "acta-adb-keepalive");
        keepAlive.setDaemon(true);
        keepAlive.start();
    }

    // ================================================================== Comandos remotos

    private static String vars(String id) {
        return "D=/data/local/tmp; W=$D/acta_" + id + ".wav; F=$D/acta_" + id + ".stop; L=$D/acta_" + id + ".log; ";
    }

    /** Lanza el grabador (si no está ya lanzado) en segundo plano y sesión propia, y espera a "REC". */
    private static String launchCmd(String apk, String id, boolean split) {
        return vars(id)
                + "if [ ! -f \"$L\" ]; then "
                // Para grabadores colgados de llamadas anteriores (liberan el audio).
                + "st=0; for o in \"$D\"/acta_*.log; do [ -f \"$o\" ] || continue; [ \"$o\" = \"$L\" ] && continue; "
                + "touch \"${o%.log}.stop\"; st=1; done; [ $st = 1 ] && sleep 0.4; "
                + "find \"$D\" -name 'acta_*' -mmin +180 -delete 2>/dev/null; "
                + "S=; [ -x /system/bin/setsid ] && S=/system/bin/setsid; "
                + "N=; [ -x /system/bin/nohup ] && N=/system/bin/nohup; "
                + "CLASSPATH=" + apk + " $N $S app_process /system/bin com.acta.recorder.AdbRecorderMain "
                + "\"$W\" arfile \"$F\"" + (split ? " split" : "") + " >\"$L\" 2>&1 </dev/null & fi; "
                + "i=0; while [ $i -lt 80 ]; do "
                + "if grep -q '^REC ' \"$L\" 2>/dev/null; then grep '^REC ' \"$L\"; exit 0; fi; "
                + "if grep -q '^ALLFAIL' \"$L\" 2>/dev/null; then cat \"$L\"; echo ALLFAIL; exit 1; fi; "
                + "sleep 0.1; i=$((i+1)); done; cat \"$L\" 2>/dev/null; echo TIMEOUT";
    }

    /** Para el grabador, espera a que cierre el WAV y lo envía (solo bytes del WAV). */
    private static String stopCmd(String id) {
        return vars(id)
                + "touch \"$F\" 2>/dev/null; i=0; while [ $i -lt 60 ]; do "
                + "grep -q '^DONE' \"$L\" 2>/dev/null && break; [ -f \"$L\" ] || break; "
                + "sleep 0.1; i=$((i+1)); done; cat \"$W\" 2>/dev/null";
    }

    private static String cleanCmd(String id) {
        return vars(id) + "rm -f \"$W\" \"$W.vad\" \"$F\" \"$L\"";
    }

    private static String abandonCmd(String id) {
        return vars(id) + "touch \"$F\" 2>/dev/null; sleep 0.5; rm -f \"$W\" \"$W.vad\" \"$F\" \"$L\"";
    }

    /** Ejecuta por exec: y devuelve la salida de texto (hasta fin, marcador o tiempo). null = fallo de transporte. */
    private static String execText(Context c, String cmd, int timeoutMs) {
        AdbStream s = null;
        try {
            s = ActaAdb.get(c).openStream("exec:" + cmd);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                int a = s.available();
                if (a > 0) {
                    int n = s.read(buf, 0, Math.min(a, buf.length));
                    if (n < 0) break;
                    sb.append(new String(buf, 0, n, "UTF-8"));
                    if (finished(sb)) break;
                } else if (s.isEof()) {
                    break;
                } else {
                    Thread.sleep(25);
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return null;
        } finally {
            if (s != null) try { s.close(); } catch (Throwable ignored) { }
        }
    }

    private static boolean finished(StringBuilder sb) {
        String t = sb.toString();
        return lineStarting(t, "REC ") != null || t.contains("ALLFAIL") || t.contains("TIMEOUT");
    }

    /** Línea completa (terminada en \n) que empieza por el prefijo, o null. */
    private static String lineStarting(String text, String prefix) {
        int from = 0;
        while (from < text.length()) {
            int nl = text.indexOf('\n', from);
            if (nl < 0) return null;
            String line = text.substring(from, nl).replace("\r", "").trim();
            if (line.startsWith(prefix)) return line;
            from = nl + 1;
        }
        return null;
    }

    /** Ejecuta por exec: y vuelca la salida binaria al archivo. Devuelve bytes o -1 si falla. */
    private static long execToFile(Context c, String cmd, File dest, int idleTimeoutMs) {
        AdbStream s = null;
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(dest);
            s = ActaAdb.get(c).openStream("exec:" + cmd);
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            long lastData = System.currentTimeMillis();
            while (true) {
                int a = s.available();
                if (a > 0) {
                    int n = s.read(buf, 0, Math.min(a, buf.length));
                    if (n < 0) break;
                    fo.write(buf, 0, n);
                    total += n;
                    lastData = System.currentTimeMillis();
                } else if (s.isEof()) {
                    break;
                } else {
                    if (System.currentTimeMillis() - lastData > idleTimeoutMs) {
                        throw new IOException("sin datos durante " + (idleTimeoutMs / 1000) + " s");
                    }
                    Thread.sleep(15);
                }
            }
            fo.flush();
            return total;
        } catch (Throwable t) {
            lastError = "recogida: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            return -1;
        } finally {
            if (fo != null) try { fo.close(); } catch (Throwable ignored) { }
            if (s != null) try { s.close(); } catch (Throwable ignored) { }
        }
    }

    /** Ejecuta un comando de mantenimiento; si el canal falla, reconecta y reintenta una vez. */
    private static void quietExec(Context c, String cmd) {
        try {
            if (!(refreshConnected(c) || ensureConnected(c))) { if (!forceReconnect(c)) return; }
            if (execText(c, cmd, 6000) != null) return;
            if (forceReconnect(c)) execText(c, cmd, 6000);
        } catch (Throwable ignored) { }
    }

    // ================================================================== Grabación

    /** Arranca la grabación de ambas voces. true si el grabador confirmó "REC". */
    static synchronized boolean startRecording(Context c) {
        if (current != null) return false;
        busy = true;
        String apk = c.getApplicationInfo().sourceDir;
        Rec r = new Rec();
        r.id = String.valueOf(System.currentTimeMillis());
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                boolean conn = attempt == 0 ? (refreshConnected(c) || ensureConnected(c)) : forceReconnect(c);
                if (!conn) {
                    if (attempt == 0) continue; // prueba con reconexión limpia
                    break;                       // puerto cerrado: no tiene sentido insistir
                }
                String res = execText(c, launchCmd(apk, r.id, SpeakerSplit.enabled(c)), 10000);
                if (res == null) continue;       // fallo de transporte: reintento (idempotente)
                String rec = lineStarting(res, "REC ");
                if (rec != null) {
                    lastSource = labelFor(rec.substring(4).trim());
                    lastError = "";
                    current = r;
                    return true;
                }
                lastError = trim(res.isEmpty() ? "el grabador no respondió" : res);
                if (res.contains("ALLFAIL")) break; // ninguna fuente de audio disponible
            }
            quietExec(c, abandonCmd(r.id)); // que no quede ningún grabador suelto
            return false;
        } finally {
            busy = false;
        }
    }

    /** Deshace una grabación que arrancó tarde (cuando ya se grababa al micro). */
    static synchronized void abandon(Context c) {
        Rec rec = current;
        current = null;
        if (rec == null) return;
        busy = true;
        try {
            quietExec(c, abandonCmd(rec.id));
        } finally {
            busy = false;
        }
    }

    /** Detiene la grabación y trae el WAV completo. null si no se pudo recuperar. */
    static synchronized File stopRecording(Context c, File dest) {
        Rec rec = current;
        current = null;
        if (rec == null) return null;
        busy = true;
        boolean ok = false;
        try {
            for (int attempt = 0; attempt < 4 && !ok; attempt++) {
                boolean conn = attempt == 0 ? (refreshConnected(c) || ensureConnected(c)) : forceReconnect(c);
                if (!conn) { sleep(800); continue; }
                long n = execToFile(c, stopCmd(rec.id), dest, 12000);
                ok = n > 1024 && wavComplete(dest);
                if (!ok) sleep(300);
            }
            if (ok) {
                fixWavHeader(dest);
                fetchVoiceTrack(c, rec.id, dest);
                quietExec(c, cleanCmd(rec.id));
            }
        } finally {
            busy = false;
        }
        if (!ok) {
            if (dest.exists()) //noinspection ResultOfMethodCallIgnored
                dest.delete();
            return null;
        }
        return dest;
    }

    /** Última información de la pista de tu voz: «VAD VOICE_UPLINK», «VAD MIC», «VAD none», «VAD_OFF…». */
    static volatile String lastVad;

    /** Recoge la pista de tu voz (<wav>.vad) junto a la grabación. Si falla, no pasa nada. */
    private static void fetchVoiceTrack(Context c, String id, File dest) {
        File vad = new File(dest.getPath() + ".vad");
        try {
            String log = execText(c, vars(id) + "grep '^VAD' \"$L\" 2>/dev/null; echo END", 4000);
            lastVad = null;
            if (log != null) {
                for (String line : log.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("VAD")) lastVad = lastVad == null || line.startsWith("VAD_OFF") ? line : lastVad;
                }
            }
            if (lastVad != null && lastVad.startsWith("VAD_OFF")) SpeakerSplit.markBad(c);
            long n = execToFile(c, vars(id) + "cat \"$W.vad\" 2>/dev/null", vad, 4000);
            if (n < 12 || !SpeakerSplit.validVad(vad)) //noinspection ResultOfMethodCallIgnored
                vad.delete();
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            vad.delete();
        }
    }

    // ================================================================== Diagnóstico

    /**
     * Prueba de extremo a extremo con el mismo camino que una llamada real: arranca, simula una
     * caída de la conexión a mitad, para y recoge. Comprueba que se grabó todo el tiempo.
     */
    static String diagnose(Context c) {
        if (current != null) return "Hay una grabación en curso. Prueba cuando no estés en una llamada.";
        StringBuilder out = new StringBuilder();
        if (!(refreshConnected(c) || ensureConnected(c) || forceReconnect(c))) {
            return "No se pudo conectar al ADB.\n" + lastError;
        }
        long t0 = System.currentTimeMillis();
        boolean ok = startRecording(c);
        out.append("1) Arranque: ").append(ok ? "OK · " + lastSource : "FALLO · " + lastError)
           .append(" (").append(System.currentTimeMillis() - t0).append(" ms)\n");
        if (!ok) return out.toString();

        sleep(2000);
        out.append("2) Corto la conexión a propósito a mitad de la grabación…\n");
        disconnect(c);
        sleep(3000);

        File tmp = new File(c.getCacheDir(), "acta_diag.wav");
        long t1 = System.currentTimeMillis();
        File f = stopRecording(c, tmp);
        if (f == null) {
            out.append("3) Recogida: FALLO · ").append(lastError).append('\n');
            return out.toString();
        }
        double secs = wavSeconds(f);
        out.append(String.format(Locale.ROOT, "3) Recogida: OK · %d KB · %.1f s grabados de ~5 s (%d ms)\n",
                f.length() / 1024, secs, System.currentTimeMillis() - t1));
        File vad = new File(f.getPath() + ".vad");
        String v = lastVad == null ? "" : lastVad;
        out.append("4) Separar voces: ").append(!SpeakerSplit.enabled(c) ? "desactivado"
                : v.startsWith("VAD_OFF") ? "no compatible (se ha desactivado solo)"
                : v.contains("UPLINK") ? "OK · tu voz por la línea"
                : v.contains("MIC") ? "OK · tu voz por el micrófono"
                : "no disponible: la IA deducirá quién habla por el contenido").append('\n');
        //noinspection ResultOfMethodCallIgnored
        vad.delete();
        out.append(secs >= 4.0
                ? "\n✓ Todo correcto: la grabación sigue aunque se corte la conexión."
                : "\n✗ La grabación se cortó al perder la conexión.");
        out.append("\n\n(Fuera de una llamada se graba silencio; es normal.)");
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        return out.toString();
    }

    // ================================================================== WAV

    /** El archivo recibido tiene, al menos, todos los datos que dice su cabecera. */
    private static boolean wavComplete(File f) {
        if (f.length() < 44) return false;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[44];
            raf.readFully(h);
            if (h[0] != 'R' || h[1] != 'I' || h[2] != 'F' || h[3] != 'F') return false;
            if (h[8] != 'W' || h[9] != 'A' || h[10] != 'V' || h[11] != 'E') return false;
            long dataLen = le32(h, 40);
            return f.length() >= 44 + dataLen;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ajusta la cabecera al tamaño real recibido (duración exacta y WAV siempre válido). */
    private static void fixWavHeader(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            long data = f.length() - 44;
            if (data < 0) return;
            raf.seek(4);  wr32(raf, (int) (36 + data));
            raf.seek(40); wr32(raf, (int) data);
        } catch (Throwable ignored) { }
    }

    private static double wavSeconds(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[44];
            raf.readFully(h);
            long rate = le32(h, 24);
            int ch = (h[22] & 0xff) | ((h[23] & 0xff) << 8);
            int bits = (h[34] & 0xff) | ((h[35] & 0xff) << 8);
            long bps = rate * ch * bits / 8;
            return bps > 0 ? (f.length() - 44) / (double) bps : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long le32(byte[] b, int o) {
        return (b[o] & 0xffL) | ((b[o + 1] & 0xffL) << 8) | ((b[o + 2] & 0xffL) << 16) | ((b[o + 3] & 0xffL) << 24);
    }

    private static void wr32(RandomAccessFile raf, int v) throws IOException {
        raf.write(v & 0xff); raf.write((v >> 8) & 0xff); raf.write((v >> 16) & 0xff); raf.write((v >> 24) & 0xff);
    }

    // ================================================================== Utilidades

    private static String labelFor(String name) {
        if ("VOICE_CALL".equals(name)) return "Ambas voces (línea completa)";
        if ("VOICE_DOWNLINK".equals(name)) return "Solo el interlocutor";
        if ("VOICE_UPLINK".equals(name)) return "Solo tu voz (línea)";
        if ("VOICE_COMMUNICATION".equals(name)) return "Comunicación (solo tu voz)";
        if ("VOICE_RECOGNITION".equals(name) || "MIC".equals(name)) return "Micrófono (solo tu voz)";
        return name;
    }

    private static String trim(String s) {
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
