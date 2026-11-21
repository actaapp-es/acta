package com.acta.recorder;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

/**
 * Reduce el peso de las grabaciones: convierte el WAV sin comprimir de la grabación con ambas voces
 * a AAC (.m4a) mono de 24 kbps, unas 10 veces más pequeño y con calidad de sobra para voz.
 * Antes de convertir se guarda la energía del audio (para saber quién habla al analizar con IA).
 */
final class AudioPack {

    private AudioPack() { }

    private static final String MIME = "audio/mp4a-latm";

    // ------------------------------------------------------------------ Conversión

    /** Convierte un WAV PCM 16 bits a m4a. Devuelve false (y no deja archivo) si algo falla. */
    static boolean compress(File wav, File out) {
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        boolean ok = false;
        try (RandomAccessFile raf = new RandomAccessFile(wav, "r")) {
            byte[] h = new byte[12];
            raf.readFully(h);
            int rate = 0, ch = 1, bits = 16;
            long dataStart = -1;
            byte[] ck = new byte[8];
            while (raf.getFilePointer() + 8 <= raf.length()) {
                raf.readFully(ck);
                long size = le32(ck, 4) & 0xffffffffL;
                String id = new String(ck, 0, 4, "US-ASCII");
                if (id.equals("fmt ")) {
                    byte[] f = new byte[(int) Math.min(size, 40)];
                    raf.readFully(f);
                    ch = Math.max(1, le16(f, 2));
                    rate = le32(f, 4);
                    bits = le16(f, 14);
                    raf.seek(raf.getFilePointer() + (size - f.length) + (size & 1));
                } else if (id.equals("data")) {
                    dataStart = raf.getFilePointer();
                    break;
                } else {
                    raf.seek(raf.getFilePointer() + size + (size & 1));
                }
            }
            if (rate <= 0 || bits != 16 || dataStart < 0) return false;
            long dataLen = raf.length() - dataStart;
            int frameBytes = 2 * ch;
            long frames = dataLen / frameBytes;
            if (frames < rate / 2) return false; // menos de medio segundo

            int decim = rate % 16000 == 0 && rate > 16000 ? rate / 16000 : 1;
            int sr = rate / decim;
            int bitrate = sr <= 16000 ? 24000 : 32000;

            MediaFormat fmt = MediaFormat.createAudioFormat(MIME, sr, 1);
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768);
            codec = MediaCodec.createEncoderByType(MIME);
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            if (out.exists() && !out.delete()) return false;
            muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int track = -1;
            boolean muxStarted = false;

            raf.seek(dataStart);
            byte[] raw = new byte[frameBytes * decim * 2048];
            long fed = 0;               // muestras de salida entregadas al codificador
            long remaining = frames;
            boolean inputDone = false, outputDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!outputDone) {
                if (!inputDone) {
                    int ix = codec.dequeueInputBuffer(10_000);
                    if (ix >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ix);
                        ib.clear();
                        int maxOut = Math.min(ib.capacity() / 2, 2048);
                        long take = Math.min(remaining, (long) maxOut * decim);
                        if (take <= 0) {
                            codec.queueInputBuffer(ix, 0, 0, fed * 1_000_000L / sr, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            int n = (int) take;
                            raf.readFully(raw, 0, n * frameBytes);
                            int outN = n / decim;
                            if (outN == 0) outN = 1;
                            for (int o = 0; o < outN; o++) {
                                int acc = 0, cnt = 0;
                                for (int d = 0; d < decim; d++) {
                                    int idx = (o * decim + d) * frameBytes;
                                    if (idx + 1 >= n * frameBytes) break;
                                    acc += (short) ((raw[idx] & 0xff) | (raw[idx + 1] << 8));
                                    cnt++;
                                }
                                short v = (short) (cnt == 0 ? 0 : acc / cnt);
                                ib.put((byte) (v & 0xff)).put((byte) ((v >> 8) & 0xff));
                            }
                            codec.queueInputBuffer(ix, 0, outN * 2, fed * 1_000_000L / sr, 0);
                            fed += outN;
                            remaining -= take;
                        }
                    }
                }
                int ox = codec.dequeueOutputBuffer(info, 10_000);
                if (ox == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxStarted = true;
                } else if (ox >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(ox);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && muxStarted) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        muxer.writeSampleData(track, ob, info);
                    }
                    codec.releaseOutputBuffer(ox, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            ok = muxStarted;
        } catch (Throwable t) {
            ok = false;
        } finally {
            try { if (codec != null) { codec.stop(); codec.release(); } } catch (Throwable ignored) { }
            try { if (muxer != null) { muxer.stop(); muxer.release(); } } catch (Throwable e) { ok = false; }
            if (!ok && out.exists()) //noinspection ResultOfMethodCallIgnored
                out.delete();
        }
        return ok && out.exists() && out.length() > 1024;
    }

    private static int le16(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8); }
    private static int le32(byte[] b, int o) { return le16(b, o) | (le16(b, o + 2) << 16); }

    // ------------------------------------------------------------------ Energía (quién habla)

    static File nrgFor(Context c, String recordingName) {
        File d = new File(c.getFilesDir(), "vad");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, recordingName + ".nrg");
    }

    static void saveEnergy(Context c, String recordingName, int[] e) {
        if (e == null || e.length < 10) return;
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(nrgFor(c, recordingName))) {
            byte[] b = new byte[4 + 2 * e.length];
            b[0] = 'A'; b[1] = 'N'; b[2] = 'R'; b[3] = 'G';
            for (int i = 0; i < e.length; i++) { b[4 + 2 * i] = (byte) (e[i] & 0xff); b[5 + 2 * i] = (byte) ((e[i] >> 8) & 0xff); }
            o.write(b);
        } catch (Throwable ignored) { }
    }

    static int[] readEnergy(Context c, String recordingName) {
        File f = nrgFor(c, recordingName);
        if (!f.exists()) return null;
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] b = new byte[(int) r.length()];
            r.readFully(b);
            if (b.length < 24 || b[0] != 'A' || b[1] != 'N' || b[2] != 'R' || b[3] != 'G') return null;
            int[] e = new int[(b.length - 4) / 2];
            for (int i = 0; i < e.length; i++) e[i] = (b[4 + 2 * i] & 0xff) | ((b[5 + 2 * i] & 0xff) << 8);
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Grabaciones antiguas

    /** Cuántas grabaciones WAV hay sin reducir y cuánto ocupan. {cantidad, bytes} */
    static long[] pending(Context c) {
        long n = 0, bytes = 0;
        for (Db.Call call : Db.get(c).all()) {
            if (call.file == null || !call.file.toLowerCase(Locale.ROOT).endsWith(".wav")) continue;
            File f = new File(Util.dir(c), call.file);
            if (!f.exists()) continue;
            n++;
            bytes += f.length();
        }
        return new long[]{n, bytes};
    }

    /** Convierte las grabaciones WAV antiguas a m4a. Devuelve cuántas se redujeron. */
    static synchronized int migrateOld(Context c) {
        int done = 0;
        List<Db.Call> all = Db.get(c).all();
        for (Db.Call call : all) {
            if (call.file == null || !call.file.toLowerCase(Locale.ROOT).endsWith(".wav")) continue;
            if (AutoAnalyzer.isRunning(call.id)) continue;
            File src = new File(Util.dir(c), call.file);
            if (!src.exists()) continue;
            File plain = new File(c.getCacheDir(), "pack_" + call.id + ".wav");
            File m4a = null;
            try {
                Crypto.decryptTo(c, src, plain);
                int[] nrg = SpeakerSplit.wavEnergy(plain);
                String base = call.file.substring(0, call.file.length() - 4);
                m4a = new File(Util.dir(c), base + ".m4a");
                if (!AudioPack.compress(plain, m4a)) continue;
                if (!Crypto.encryptFile(c, m4a)) { m4a.delete(); continue; }
                String newName = m4a.getName();
                saveEnergy(c, newName, nrg);
                File oldVad = SpeakerSplit.vadFor(c, call.file);
                if (oldVad.exists()) //noinspection ResultOfMethodCallIgnored
                    oldVad.renameTo(SpeakerSplit.vadFor(c, newName));
                Db.get(c).updateFile(call.id, newName);
                //noinspection ResultOfMethodCallIgnored
                src.delete();
                done++;
            } catch (Throwable t) {
                if (m4a != null && m4a.exists() && !Db.get(c).byId(call.id).file.equals(m4a.getName())) //noinspection ResultOfMethodCallIgnored
                    m4a.delete();
            } finally {
                //noinspection ResultOfMethodCallIgnored
                plain.delete();
            }
        }
        if (done > 0) c.sendBroadcast(new android.content.Intent(Util.ACTION_UPDATED).setPackage(c.getPackageName()));
        return done;
    }
}
