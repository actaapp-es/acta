package com.acta.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

/** Aviso y consentimiento antes de enviar datos de llamadas al servicio de IA (Groq). */
final class AiConsent {

    private AiConsent() { }

    private static boolean askedThisRun;

    static boolean granted(Context c) { return Util.prefs(c).getBoolean("ai_consent", false); }

    static void revoke(Context c) { Util.prefs(c).edit().putBoolean("ai_consent", false).apply(); }

    /** Ejecuta ok si ya hay consentimiento; si no, muestra el aviso y lo ejecuta al aceptar. */
    static void require(final Activity a, final Runnable ok) {
        if (granted(a)) { if (ok != null) ok.run(); return; }
        show(a, ok);
    }

    /** Al abrir la app: si hay clave pero falta el consentimiento, lo pide una vez. */
    static void promptIfNeeded(Activity a) {
        if (askedThisRun || granted(a) || !Summarizer.hasKey(a)) return;
        askedThisRun = true;
        show(a, null);
    }

    private static void show(final Activity a, final Runnable ok) {
        new AlertDialog.Builder(a)
                .setTitle("Análisis con IA")
                .setMessage("Para transcribir y resumir una llamada, Acta envía a Groq (un servicio de inteligencia "
                        + "artificial de terceros), mediante tu clave y con conexión cifrada:\n\n"
                        + "• El audio de la llamada y su transcripción.\n"
                        + "• El nombre del contacto, la fecha, la hora y la duración.\n\n"
                        + "No se envía ningún número de teléfono. Si activas el análisis automático, esto ocurre "
                        + "al colgar cada llamada grabada. Puedes desactivarlo cuando quieras en Ajustes.")
                .setNegativeButton("No, gracias", null)
                .setNeutralButton("Privacidad", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        a.startActivity(new Intent(a, LegalActivity.class).putExtra("doc", "privacy"));
                    }
                })
                .setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Util.prefs(a).edit().putBoolean("ai_consent", true).apply();
                        if (ok != null) ok.run();
                        if (AnalysisJob.enabled(a)) AutoAnalyzer.resumePending(a.getApplicationContext());
                    }
                })
                .show();
    }

    /** «Informar»: marca un resultado de la IA como inapropiado o incorrecto. */
    static void report(final Activity a, final String what, final String content) {
        final String[] reasons = {"Ofensivo o inapropiado", "Incorrecto o inventado", "Otro problema"};
        new AlertDialog.Builder(a)
                .setTitle("Informar de este contenido")
                .setItems(reasons, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int n = Util.prefs(a).getInt("ai_reports", 0) + 1;
                        Util.prefs(a).edit().putInt("ai_reports", n).apply();
                        final String body = "Informe de contenido generado por IA en Acta\n\nMotivo: " + reasons[w]
                                + "\nTipo: " + what + "\n\nContenido:\n" + content;
                        new AlertDialog.Builder(a)
                                .setTitle("Gracias por informar")
                                .setMessage("Queda registrado en este móvil. Si quieres, envía el informe al "
                                        + "desarrollador (correo de contacto de la ficha de Google Play).")
                                .setNegativeButton("Cerrar", null)
                                .setPositiveButton("Enviar informe", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d2, int w2) {
                                        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                                .putExtra(Intent.EXTRA_SUBJECT, "Acta · informe de contenido de IA")
                                                .putExtra(Intent.EXTRA_TEXT, body);
                                        try { a.startActivity(Intent.createChooser(i, "Enviar informe")); }
                                        catch (Exception ignored) { }
                                    }
                                }).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
