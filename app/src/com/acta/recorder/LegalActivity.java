package com.acta.recorder;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Aviso legal y exención de responsabilidad. */
public class LegalActivity extends Activity {

    static final String UPDATED = "18 de noviembre de 2026";

    /** {título, texto} de cada cláusula. */
    static final String[][] CLAUSES = {
            {"1. Objeto y aceptación",
                    "El presente aviso legal regula el uso de la aplicación Acta (en adelante, «la Aplicación»), puesta a "
                            + "disposición por su desarrollador o titular (en adelante, «el Desarrollador»). La instalación, el "
                            + "acceso o el uso de la Aplicación implican la aceptación plena y sin reservas de todas las "
                            + "cláusulas de este aviso. Si no está de acuerdo con ellas, no instale ni utilice la Aplicación y "
                            + "desinstálela de su dispositivo. Para cualquier comunicación con el Desarrollador puede utilizar el "
                            + "correo de contacto que figura en la ficha de la Aplicación en Google Play."},
            {"2. Licencia de uso y ausencia de garantías",
                    "La Aplicación se facilita de forma gratuita, «tal cual» y «según disponibilidad», sin garantías de "
                            + "ningún tipo, expresas o implícitas, incluidas, a título enunciativo, las de comerciabilidad, "
                            + "idoneidad para un fin determinado, exactitud, disponibilidad, continuidad, ausencia de errores "
                            + "o de virus y no infracción de derechos de terceros. El Desarrollador no garantiza que la "
                            + "Aplicación funcione de forma ininterrumpida, que sea compatible con todos los dispositivos, "
                            + "versiones del sistema operativo u operadores, ni que los defectos vayan a corregirse."},
            {"3. Responsabilidad exclusiva del usuario",
                    "El usuario es el único y exclusivo responsable del uso que haga de la Aplicación y de las "
                            + "grabaciones, transcripciones, resúmenes, tareas y cualesquiera otros contenidos que genere, "
                            + "almacene, comparta o difunda con ella, así como de las consecuencias de dicho uso."},
            {"4. Grabación de conversaciones y cumplimiento normativo",
                    "La grabación, conservación, tratamiento, uso y difusión de conversaciones telefónicas pueden estar "
                            + "sujetos a requisitos legales que varían según el país y las circunstancias, entre otros los "
                            + "derivados del derecho a la intimidad, al honor y a la propia imagen, del secreto de las "
                            + "comunicaciones y de la normativa de protección de datos personales (en particular, el "
                            + "Reglamento (UE) 2016/679 y la Ley Orgánica 3/2018). Corresponde exclusivamente al usuario "
                            + "conocer y cumplir la legislación aplicable, informar a los demás interlocutores y recabar su "
                            + "consentimiento cuando sea exigible, y abstenerse de difundir o utilizar las grabaciones de "
                            + "forma ilícita. El Desarrollador no ofrece asesoramiento jurídico y no será responsable, en "
                            + "ningún caso, de las grabaciones realizadas por el usuario ni del uso que este haga de ellas."},
            {"5. Usos prohibidos",
                    "Queda prohibido utilizar la Aplicación para fines ilícitos o contrarios a la buena fe, entre ellos "
                            + "grabar o interceptar comunicaciones en las que el usuario no participe, acosar, amenazar, "
                            + "suplantar la identidad de terceros, eludir la identificación con fines fraudulentos o vulnerar "
                            + "derechos de terceros. El usuario asumirá en exclusiva cualquier responsabilidad derivada de "
                            + "dichos usos."},
            {"6. Fallos de grabación y pérdida de datos",
                    "El funcionamiento de la grabación depende del dispositivo, del sistema operativo, del fabricante, "
                            + "del operador y de la configuración realizada por el usuario. El Desarrollador no garantiza que "
                            + "todas las llamadas se graben, ni que las grabaciones sean completas, íntegras, audibles o "
                            + "recuperables, y no responde de la pérdida, corrupción, borrado o imposibilidad de descifrar "
                            + "grabaciones o datos. Es responsabilidad del usuario conservar sus propias copias de seguridad."},
            {"7. Funciones técnicas avanzadas",
                    "Algunas funciones requieren activar opciones de desarrollador, depuración USB u otras herramientas "
                            + "del sistema, así como ejecutar comandos desde un ordenador. El usuario realiza estas "
                            + "operaciones por su cuenta y riesgo, y el Desarrollador no responde de los daños, fallos, "
                            + "pérdida de garantía, problemas de seguridad o mal funcionamiento que pudieran derivarse para "
                            + "el dispositivo, el ordenador o los datos del usuario."},
            {"8. Llamadas de emergencia",
                    "La Aplicación puede actuar como aplicación de teléfono predeterminada. El Desarrollador no garantiza "
                            + "su funcionamiento en llamadas de emergencia ni en ninguna otra llamada. Ante cualquier fallo, el "
                            + "usuario debe utilizar la aplicación de teléfono original del dispositivo u otro medio "
                            + "disponible. El Desarrollador declina toda responsabilidad por llamadas no realizadas, no "
                            + "recibidas, interrumpidas o retrasadas."},
            {"9. Ocultación del número y privacidad",
                    "La ocultación del número de teléfono depende del operador y de la red y puede no funcionar en todos "
                            + "los casos. Los servicios de emergencia, los operadores y las autoridades competentes pueden "
                            + "conocer el número en todo caso. El Desarrollador no garantiza el anonimato del usuario."},
            {"10. Inteligencia artificial y servicios de terceros",
                    "Las funciones de análisis, resumen, tareas, contexto y preguntas se basan en servicios de "
                            + "inteligencia artificial de terceros (como Groq) contratados directamente por el usuario mediante "
                            + "su propia clave, y en otras aplicaciones o servicios de terceros (como WhatsApp, el calendario "
                            + "o el operador telefónico). Los resultados generados por la inteligencia artificial pueden ser "
                            + "inexactos, incompletos o erróneos y no deben utilizarse como única base para tomar decisiones; "
                            + "el usuario debe verificarlos. Al utilizar estas funciones, el audio y la transcripción de la "
                            + "llamada se envían al proveedor elegido por el usuario, cuyas condiciones y política de "
                            + "privacidad son ajenas al Desarrollador. El Desarrollador no responde de dichos servicios, de "
                            + "su disponibilidad, de sus costes ni del tratamiento que hagan de los datos."},
            {"11. Datos personales",
                    "La Aplicación almacena la información en el propio dispositivo del usuario. El Desarrollador no "
                            + "recopila, recibe ni trata las grabaciones, contactos, registros de llamadas ni otros datos "
                            + "personales del usuario. Respecto de los datos de terceros que el usuario grabe o trate, el "
                            + "usuario actúa bajo su exclusiva responsabilidad."},
            {"12. Limitación de responsabilidad",
                    "En la máxima medida permitida por la legislación aplicable, el Desarrollador no será responsable "
                            + "de ningún daño o perjuicio, directo o indirecto, incidental, especial, consecuencial o punitivo, "
                            + "incluidos, entre otros, la pérdida de datos, de beneficios, de oportunidades o de reputación, "
                            + "las sanciones administrativas, las reclamaciones de terceros o los daños al dispositivo, que "
                            + "se deriven del uso o de la imposibilidad de uso de la Aplicación, aun cuando se hubiera "
                            + "advertido de su posibilidad."},
            {"13. Indemnidad",
                    "El usuario se obliga a mantener indemne al Desarrollador frente a cualquier reclamación, sanción, "
                            + "procedimiento, daño, gasto u honorario, incluidos los de abogados y procuradores, que traiga "
                            + "causa del uso de la Aplicación por el usuario, del incumplimiento de este aviso o de la "
                            + "vulneración de la ley o de derechos de terceros."},
            {"14. Derechos irrenunciables",
                    "Nada de lo dispuesto en este aviso limita o excluye la responsabilidad que, conforme a la ley "
                            + "aplicable, no pueda ser limitada o excluida, ni los derechos que la normativa de protección de "
                            + "las personas consumidoras reconozca con carácter irrenunciable. Si alguna cláusula fuera "
                            + "declarada nula o inaplicable, se tendrá por no puesta y el resto conservará su plena validez."},
            {"15. Modificaciones",
                    "El Desarrollador podrá modificar la Aplicación, suspenderla o modificar este aviso en cualquier "
                            + "momento y sin previo aviso. La versión vigente será la incluida en la Aplicación."},
            {"16. Legislación aplicable y jurisdicción",
                    "Este aviso se rige por la legislación española. Para la resolución de cualquier controversia, las "
                            + "partes se someten a los juzgados y tribunales que correspondan conforme a la normativa "
                            + "aplicable y, cuando el usuario tenga la condición de consumidor, a los de su domicilio."},
    };

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        LinearLayout top = new LinearLayout(this);
        top.setPadding(dp(6), dp(8), dp(6), 0);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(getColor(R.color.ink)));
        back.setBackgroundResource(R.drawable.bg_icon_btn);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("Volver");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(24), dp(2), dp(24), dp(32));
        scroll.addView(c);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        boolean privacy = "privacy".equals(getIntent().getStringExtra("doc"));
        TextView title = Ui.text(this, R.style.Text_Title, privacy ? "Política de privacidad" : "Aviso legal");
        title.setTextSize(28);
        c.addView(title);
        TextView sub = Ui.text(this, R.style.Text_Meta, privacy ? "Última actualización: " + PrivacyText.UPDATED
                : "Exención de responsabilidad · Última actualización: " + UPDATED);
        sub.setPadding(0, dp(6), 0, dp(8));
        c.addView(sub);
        if (privacy) {
            TextView intro = Ui.text(this, R.style.Text, PrivacyText.INTRO);
            intro.setTextSize(14.5f);
            intro.setLineSpacing(dp(4), 1f);
            intro.setTextColor(getColor(R.color.accent));
            intro.setBackgroundResource(R.drawable.bg_note);
            intro.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            il.topMargin = dp(8);
            c.addView(intro, il);
        }
        for (String[] cl : privacy ? PrivacyText.SECTIONS : CLAUSES) {
            TextView h = Ui.text(this, R.style.Text, cl[0], 15.5f, true);
            h.setPadding(0, dp(18), 0, dp(6));
            c.addView(h);
            TextView p = Ui.text(this, R.style.Text, cl[1]);
            p.setTextSize(14.5f);
            p.setTextColor(getColor(R.color.ink_2));
            p.setLineSpacing(dp(4), 1f);
            p.setTextIsSelectable(true);
            c.addView(p);
        }
        setContentView(root);
    }
}
