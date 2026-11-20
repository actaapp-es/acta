package com.acta.recorder;

import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;

/**
 * Se ejecuta en el proceso que arranca Shizuku, con identidad de shell (ADB).
 * Con esos permisos, AudioSource.VOICE_CALL captura las dos partes de la llamada.
 * Graba directamente sobre el descriptor de archivo que abre Acta.
 */
public class RecorderUserService extends IRecorderService.Stub {

    private MediaRecorder recorder;
    private long startMs;
    private int lastSource = -1;

    // Shizuku instancia el servicio con uno de estos dos constructores.
    public RecorderUserService() { }
    public RecorderUserService(android.content.Context context) { }

    @Override
    public int start(ParcelFileDescriptor pfd, int wantSource) {
        stopQuietly();
        if (pfd == null) return -1;
        int[] order = wantSource == MediaRecorder.AudioSource.MIC
                ? new int[]{MediaRecorder.AudioSource.MIC}
                : new int[]{MediaRecorder.AudioSource.VOICE_CALL,
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            MediaRecorder.AudioSource.MIC};
        for (int src : order) {
            MediaRecorder r = new MediaRecorder();
            try {
                r.setAudioSource(src);
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                r.setAudioChannels(1);
                r.setAudioSamplingRate(src == MediaRecorder.AudioSource.VOICE_CALL ? 16000 : 44100);
                r.setAudioEncodingBitRate(src == MediaRecorder.AudioSource.VOICE_CALL ? 64000 : 96000);
                r.setOutputFile(pfd.getFileDescriptor());
                r.prepare();
                r.start();
                recorder = r;
                lastSource = src;
                startMs = System.currentTimeMillis();
                return src;
            } catch (Throwable e) {
                try { r.release(); } catch (Throwable ignored) { }
            }
        }
        return -1;
    }

    @Override
    public long stop() {
        if (recorder == null) return 0;
        long dur = System.currentTimeMillis() - startMs;
        try { recorder.stop(); } catch (Throwable e) { dur = -1; }
        try { recorder.release(); } catch (Throwable ignored) { }
        recorder = null;
        return dur;
    }

    @Override
    public boolean isRecording() {
        return recorder != null;
    }

    @Override
    public int lastSource() {
        return lastSource;
    }

    @Override
    public void destroy() {
        stopQuietly();
    }

    private void stopQuietly() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Throwable ignored) { }
            try { recorder.release(); } catch (Throwable ignored) { }
            recorder = null;
        }
    }
}
