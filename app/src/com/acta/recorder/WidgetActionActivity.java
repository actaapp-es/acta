package com.acta.recorder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Acciones de los widgets (marcar tarea, abrir llamada, llamar). Sin interfaz propia. */
public class WidgetActionActivity extends Activity {

    static final String EXTRA_ACTION = "w_action";
    static final String OPEN = "open", TOGGLE = "toggle", CALL = "call";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        String a = i.getStringExtra(EXTRA_ACTION);
        try {
            if (TOGGLE.equals(a)) {
                Tasks.setDone(this, i.getLongExtra("id", -1), i.getIntExtra("index", -1), true);
                TasksWidget.refresh(this);
                Toast.makeText(this, "Tarea hecha", Toast.LENGTH_SHORT).show();
            } else if (OPEN.equals(a)) {
                startActivity(new Intent(this, DetailActivity.class).putExtra("id", i.getLongExtra("id", -1))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (CALL.equals(a)) {
                String n = i.getStringExtra("number");
                SimInfo.Sim p = SimInfo.dual(this) ? SimInfo.preferred(this, n) : null;
                // Sin SIM recordada, la pantalla de llamada de Acta preguntará con cuál.
                Util.placeCall(this, n, p != null ? p.handle : null);
            }
        } catch (Throwable ignored) { }
        finish();
    }
}
