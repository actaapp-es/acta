package com.acta.recorder;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Utilidades del diseño: tema claro/oscuro, avatares, tarjetas y botones. */
final class Ui {

    private Ui() { }

    // ------------------------------------------------------------------ Tema

    static final int THEME_AUTO = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    static final String[] THEME_LABELS = {"Automático (como el móvil)", "Claro", "Oscuro"};
    static final String[] THEME_SHORT = {"Automático", "Claro", "Oscuro"};

    /** Sube cada vez que cambia el tema, para que las pantallas abiertas se recreen. */
    static volatile int themeGen;

    static int theme(Context c) {
        return Util.prefs(c).getInt("theme", THEME_AUTO);
    }

    static void setTheme(Context c, int mode) {
        Util.prefs(c).edit().putInt("theme", mode).apply();
        themeGen++;
    }

    /** Aplica el tema elegido en Ajustes al contexto de una pantalla. */
    static Context wrap(Context base) {
        int mode;
        try { mode = theme(base); } catch (Throwable t) { return base; }
        if (mode == THEME_AUTO) return base;
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        int night = mode == THEME_DARK ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(cfg);
    }

    // ------------------------------------------------------------------ Medidas y textos

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, int style, CharSequence s) {
        TextView t = new TextView(c, null, 0, style);
        t.setText(s);
        return t;
    }

    static TextView text(Context c, int style, CharSequence s, float sp, boolean bold) {
        TextView t = text(c, style, s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }
    static Typeface bold() { return Typeface.create("sans-serif-medium", Typeface.BOLD); }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lpTop(Context c, int w, int h, float top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        l.topMargin = dp(c, top);
        return l;
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // ------------------------------------------------------------------ Tarjetas

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.bg_card);
        l.setClipToOutline(true);
        return l;
    }

    /** Fondo de una fila dentro de un grupo: esquinas redondeadas solo arriba/abajo. */
    static Drawable groupBg(Context c, boolean top, boolean bottom) {
        float r = dp(c, 22);
        float t = top ? r : 0, b = bottom ? r : 0;
        GradientDrawable g = new GradientDrawable();
        g.setColor(c.getColor(R.color.surface));
        g.setCornerRadii(new float[]{t, t, t, t, b, b, b, b});
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadii(new float[]{t, t, t, t, b, b, b, b});
        return new RippleDrawable(ColorStateList.valueOf(c.getColor(R.color.ripple)), g, mask);
    }

    static View separator(Context c, float startDp) {
        View v = new View(c);
        v.setBackgroundColor(c.getColor(R.color.line));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(c, 1)));
        l.setMarginStart(dp(c, startDp));
        v.setLayoutParams(l);
        return v;
    }

    static TextView section(Context c, String s) {
        TextView t = text(c, R.style.Text_Section, s);
        t.setPadding(dp(c, 10), dp(c, 22), dp(c, 10), dp(c, 8));
        return t;
    }

    // ------------------------------------------------------------------ Botones

    static TextView button(Context c, String s, boolean primary) {
        TextView b = new TextView(c, null, 0, R.style.Text);
        b.setText(s);
        b.setTextSize(primary ? 16 : 15);
        b.setTypeface(bold());
        b.setGravity(Gravity.CENTER);
        b.setTextColor(c.getColor(primary ? R.color.on_accent : R.color.accent));
        b.setBackgroundResource(primary ? R.drawable.bg_btn : R.drawable.bg_btn2);
        b.setMinHeight(dp(c, 54));
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setCompoundDrawablePadding(dp(c, 8));
        return b;
    }

    static void icon(TextView t, int res, int colorRes, float sizeDp) {
        Context c = t.getContext();
        Drawable d = c.getDrawable(res).mutate();
        int s = dp(c, sizeDp);
        d.setBounds(0, 0, s, s);
        d.setTint(c.getColor(colorRes));
        t.setCompoundDrawablesRelative(d, null, null, null);
    }

    static TextView textButton(Context c, String s) {
        TextView t = new TextView(c, null, 0, R.style.Text_Action);
        t.setText(s);
        return t;
    }

    static ImageView iconView(Context c, int res, int colorRes, float sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(c.getColor(colorRes)));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    static TextView chip(Context c, String s, boolean accent) {
        TextView t = new TextView(c, null, 0, R.style.Text_Chip);
        t.setText(s);
        if (accent) {
            t.setBackgroundResource(R.drawable.bg_chip_a);
            t.setTextColor(c.getColor(R.color.accent));
        }
        return t;
    }

    // ------------------------------------------------------------------ Avatares

    private static final int[][] AV = {
            {R.color.av1_bg, R.color.av1_fg}, {R.color.av2_bg, R.color.av2_fg}, {R.color.av3_bg, R.color.av3_fg},
            {R.color.av4_bg, R.color.av4_fg}, {R.color.av5_bg, R.color.av5_fg}};

    /** Iniciales («LM», «M») o null si el nombre es un número. */
    static String initials(String name) {
        if (TextUtils.isEmpty(name)) return null;
        String n = name.trim();
        if (n.isEmpty() || !Character.isLetter(n.codePointAt(0))) return null;
        String[] parts = n.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            int cp = p.codePointAt(0);
            if (!Character.isLetter(cp)) continue;
            sb.appendCodePoint(Character.toUpperCase(cp));
            if (sb.length() >= 2) break;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 0 si no hay iniciales; 1..5 según el color estable del nombre (para widgets). */
    static int avatarSlot(String name) {
        if (initials(name) == null) return 0;
        return 1 + (name.trim().hashCode() & 0x7fffffff) % AV.length;
    }

    static int avatarFg(Context c, int slot) {
        return c.getColor(slot == 0 ? R.color.ink_2 : AV[slot - 1][1]);
    }

    /** Pinta un avatar redondo: iniciales con color estable según el nombre, o icono de persona. */
    static void avatar(TextView av, ImageView icon, String name) {
        Context c = av.getContext();
        String ini = initials(name);
        if (ini == null) {
            av.setText("");
            av.setBackgroundTintList(ColorStateList.valueOf(c.getColor(R.color.surface_2)));
            if (icon != null) {
                icon.setVisibility(View.VISIBLE);
                icon.setImageTintList(ColorStateList.valueOf(c.getColor(R.color.ink_2)));
            }
            return;
        }
        int[] col = AV[(name.trim().hashCode() & 0x7fffffff) % AV.length];
        av.setText(ini);
        av.setTextColor(c.getColor(col[1]));
        av.setBackgroundTintList(ColorStateList.valueOf(c.getColor(col[0])));
        if (icon != null) icon.setVisibility(View.GONE);
    }

    /** Etiqueta corta de la fuente de audio para las pastillas, o null si no se sabe. */
    static String sourceLabel(String source) {
        if (TextUtils.isEmpty(source)) return null;
        if (source.startsWith("Ambas voces")) return "Ambas voces";
        String s = source.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("solo tu voz")) return "Solo tu voz";
        if (s.contains("interlocutor")) return "Solo la otra persona";
        if (s.contains("micr")) return "Solo tu voz";
        if (s.contains("completa") || s.contains("ambas")) return "Ambas voces";
        return null;
    }

    // ------------------------------------------------------------------ Pantalla completa (Android 15+)

    /** Pantallas que colocan ellas mismas los márgenes de las barras del sistema. */
    interface InsetsAware {
        void onInsets(int left, int top, int right, int bottom);
    }

    /**
     * Desde Android 15 las apps se dibujan detrás de la barra de estado y la de navegación.
     * Aplica esos márgenes (y el del teclado) al contenido de cada pantalla.
     */
    static void applyInsets(final Activity a) {
        if (android.os.Build.VERSION.SDK_INT < 35 || a.isFinishing()) return;
        final View content = a.findViewById(android.R.id.content);
        if (content == null) return;
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
                android.graphics.Insets bars = in.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = in.getInsets(android.view.WindowInsets.Type.ime());
                int bottom = Math.max(bars.bottom, ime.bottom);
                if (a instanceof InsetsAware) {
                    ((InsetsAware) a).onInsets(bars.left, bars.top, bars.right, bottom);
                } else {
                    v.setPadding(bars.left, bars.top, bars.right, bottom);
                }
                return android.view.WindowInsets.CONSUMED;
            }
        });
        content.requestApplyInsets();
    }

    static void recreateIfThemeChanged(Activity a, int seenGen) {
        if (seenGen != themeGen) a.recreate();
    }
}
