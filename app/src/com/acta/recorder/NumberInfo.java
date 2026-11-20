package com.acta.recorder;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Información deducible de un número de teléfono SIN conexión: país, zona (solo
 * fijos españoles) y tipo de línea. No da ubicación real del llamante (imposible)
 * ni operadora (poco fiable por la portabilidad).
 */
final class NumberInfo {

    String lineType;   // Móvil, Fijo, Tarificación especial…
    String origin;     // Provincia (fijos ES) o país

    private NumberInfo() { }

    private static final Map<String, String> ES_PROV = new HashMap<>();
    private static final Map<String, String> COUNTRY = new HashMap<>();

    static {
        // Provincias por prefijo de fijo español (2-3 dígitos, sin el 9/8 inicial se usa tal cual)
        String[][] p = {
                {"91", "Madrid"}, {"81", "Madrid"}, {"93", "Barcelona"}, {"83", "Barcelona"},
                {"954", "Sevilla"}, {"955", "Sevilla"}, {"952", "Málaga"}, {"951", "Málaga"},
                {"950", "Almería"}, {"956", "Cádiz"}, {"957", "Córdoba"}, {"958", "Granada"},
                {"959", "Huelva"}, {"953", "Jaén"}, {"96", "Valencia"}, {"963", "Valencia"},
                {"965", "Alicante"}, {"966", "Alicante"}, {"964", "Castellón"}, {"94", "Bizkaia"},
                {"944", "Bizkaia"}, {"943", "Gipuzkoa"}, {"945", "Álava"}, {"948", "Navarra"},
                {"941", "La Rioja"}, {"942", "Cantabria"}, {"976", "Zaragoza"}, {"974", "Huesca"},
                {"978", "Teruel"}, {"981", "A Coruña"}, {"982", "Lugo"}, {"986", "Pontevedra"},
                {"988", "Ourense"}, {"985", "Asturias"}, {"984", "Asturias"}, {"983", "Valladolid"},
                {"979", "Palencia"}, {"980", "Zamora"}, {"975", "Soria"}, {"987", "León"},
                {"947", "Burgos"}, {"921", "Segovia"}, {"920", "Ávila"}, {"923", "Salamanca"},
                {"924", "Badajoz"}, {"927", "Cáceres"}, {"925", "Toledo"}, {"926", "Ciudad Real"},
                {"967", "Albacete"}, {"969", "Cuenca"}, {"949", "Guadalajara"}, {"968", "Murcia"},
                {"977", "Tarragona"}, {"972", "Girona"}, {"973", "Lleida"}, {"971", "Baleares"},
                {"922", "S.C. de Tenerife"}, {"928", "Las Palmas"},
        };
        for (String[] e : p) ES_PROV.put(e[0], e[1]);

        String[][] c = {
                {"1", "Norteamérica"}, {"7", "Rusia"}, {"20", "Egipto"}, {"27", "Sudáfrica"},
                {"30", "Grecia"}, {"31", "Países Bajos"}, {"32", "Bélgica"}, {"33", "Francia"},
                {"34", "España"}, {"351", "Portugal"}, {"352", "Luxemburgo"}, {"353", "Irlanda"},
                {"39", "Italia"}, {"40", "Rumanía"}, {"41", "Suiza"}, {"43", "Austria"},
                {"44", "Reino Unido"}, {"45", "Dinamarca"}, {"46", "Suecia"}, {"47", "Noruega"},
                {"48", "Polonia"}, {"49", "Alemania"}, {"212", "Marruecos"}, {"213", "Argelia"},
                {"51", "Perú"}, {"52", "México"}, {"53", "Cuba"}, {"54", "Argentina"},
                {"55", "Brasil"}, {"56", "Chile"}, {"57", "Colombia"}, {"58", "Venezuela"},
                {"591", "Bolivia"}, {"593", "Ecuador"}, {"595", "Paraguay"}, {"598", "Uruguay"},
                {"502", "Guatemala"}, {"503", "El Salvador"}, {"504", "Honduras"}, {"505", "Nicaragua"},
                {"506", "Costa Rica"}, {"507", "Panamá"}, {"509", "Haití"}, {"52", "México"},
                {"60", "Malasia"}, {"61", "Australia"}, {"62", "Indonesia"}, {"63", "Filipinas"},
                {"64", "Nueva Zelanda"}, {"65", "Singapur"}, {"66", "Tailandia"}, {"81", "Japón"},
                {"82", "Corea del Sur"}, {"84", "Vietnam"}, {"86", "China"}, {"90", "Turquía"},
                {"91", "India"}, {"92", "Pakistán"}, {"971", "Emiratos Árabes"}, {"972", "Israel"},
                {"974", "Catar"}, {"966", "Arabia Saudí"}, {"880", "Bangladés"}, {"234", "Nigeria"},
        };
        for (String[] e : c) if (!COUNTRY.containsKey(e[0])) COUNTRY.put(e[0], e[1]);
    }

    static NumberInfo of(String number) {
        NumberInfo n = new NumberInfo();
        if (TextUtils.isEmpty(number)) return null;
        String raw = number.replaceAll("[^0-9+]", "");
        if (raw.isEmpty()) return null;

        String national;
        boolean spain;
        if (raw.startsWith("+34") || raw.startsWith("0034")) {
            national = raw.replaceFirst("^(\\+34|0034)", "");
            spain = true;
        } else if (raw.startsWith("+") || raw.startsWith("00")) {
            return foreign(n, raw);
        } else {
            national = raw;        // sin prefijo: asumimos España
            spain = true;
        }
        if (!spain) return foreign(n, raw);

        if (national.isEmpty()) return null;
        char first = national.charAt(0);
        if (first == '6' || first == '7') {
            n.lineType = "Móvil";
            n.origin = "España";
        } else if (first == '9' || first == '8') {
            // Tarificación especial 80x/90x
            if (national.startsWith("80") || national.startsWith("90") || national.startsWith("70")) {
                n.lineType = "Tarificación especial";
                n.origin = "España";
            } else {
                n.lineType = "Fijo";
                n.origin = provincia(national);
            }
        } else {
            n.lineType = "España";
            n.origin = "España";
        }
        return n;
    }

    private static String provincia(String national) {
        if (national.length() >= 3) {
            String p3 = national.substring(0, 3);
            if (ES_PROV.containsKey(p3)) return ES_PROV.get(p3) + " (fijo)";
        }
        if (national.length() >= 2) {
            String p2 = national.substring(0, 2);
            if (ES_PROV.containsKey(p2)) return ES_PROV.get(p2) + " (fijo)";
        }
        return "España (fijo)";
    }

    private static NumberInfo foreign(NumberInfo n, String raw) {
        String digits = raw.replaceFirst("^(\\+|00)", "");
        String country = null;
        for (int len = 3; len >= 1; len--) {
            if (digits.length() >= len) {
                String code = digits.substring(0, len);
                if (COUNTRY.containsKey(code)) { country = COUNTRY.get(code); break; }
            }
        }
        n.origin = country != null ? country : "Internacional";
        n.lineType = "Internacional";
        return n;
    }
}
