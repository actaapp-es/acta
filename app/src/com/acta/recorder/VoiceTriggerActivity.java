package com.acta.recorder;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Atajo «Hablar con Acta» (mosaico de ajustes rápidos, acceso directo del icono): abre el micrófono solo un momento,
 * para una orden, sin escuchar de fondo. Es transparente y se cierra al instante.
 */
public class VoiceTriggerActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Summarizer.hasKey(this)) {
            Toast.makeText(this, "Primero añade tu clave de Groq en Ajustes de Acta.", Toast.LENGTH_LONG).show();
        } else if (!Util.has(this, Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS, Manifest.permission.CALL_PHONE}, 92);
            return;
        } else {
            VoiceService.listenNow(this);
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (Util.has(this, Manifest.permission.RECORD_AUDIO)) VoiceService.listenNow(this);
        finish();
    }
}
