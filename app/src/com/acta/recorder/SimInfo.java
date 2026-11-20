package com.acta.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Doble SIM: detecta las tarjetas, recuerda con cuál se llama a cada número y deja elegir.
 * Si el móvil tiene una sola SIM, nada de esto se muestra.
 */
final class SimInfo {

    private SimInfo() { }

    static final class Sim {
        PhoneAccountHandle handle;
        int slot;          // 0, 1…
        String carrier;    // «Movistar», «Vodafone»…
        String number;

        String label() { return "SIM " + (slot + 1) + (TextUtils.isEmpty(carrier) ? "" : " · " + carrier); }
        String shortLabel() { return "SIM " + (slot + 1); }
    }

    private static List<Sim> cache;
    private static long cacheAt;

    static synchronized List<Sim> list(Context c) {
        if (cache != null && System.currentTimeMillis() - cacheAt < 10_000) return cache;
        List<Sim> out = new ArrayList<>();
        try {
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            if (tm == null || !Util.has(c, android.Manifest.permission.READ_PHONE_STATE)) return out;
            List<PhoneAccountHandle> hs = tm.getCallCapablePhoneAccounts();
            if (hs == null) return out;
            SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
            List<SubscriptionInfo> subs = null;
            try { if (sm != null) subs = sm.getActiveSubscriptionInfoList(); } catch (SecurityException ignored) { }
            TelephonyManager tel = c.getSystemService(TelephonyManager.class);
            int idx = 0;
            for (PhoneAccountHandle h : hs) {
                PhoneAccount acc = tm.getPhoneAccount(h);
                if (acc == null || !acc.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) continue;
                Sim s = new Sim();
                s.handle = h;
                s.slot = idx;
                CharSequence l = acc.getLabel();
                s.carrier = l != null ? l.toString() : "";
                SubscriptionInfo info = null;
                if (subs != null) {
                    int subId = -1;
                    if (Build.VERSION.SDK_INT >= 30 && tel != null) {
                        try { subId = tel.getSubscriptionId(h); } catch (Throwable ignored) { }
                    }
                    if (subId < 0) try { subId = Integer.parseInt(h.getId()); } catch (Exception ignored) { }
                    for (SubscriptionInfo si : subs) {
                        if (si.getSubscriptionId() == subId) info = si;
                        else if (info == null && h.getId() != null && h.getId().equals(iccid(si))) info = si;
                    }
                }
                if (info != null) {
                    s.slot = Math.max(0, info.getSimSlotIndex());
                    CharSequence dn = info.getDisplayName();
                    if (dn != null && dn.length() > 0) s.carrier = dn.toString();
                    try { s.number = info.getNumber(); } catch (Throwable ignored) { }
                }
                out.add(s);
                idx++;
            }
        } catch (Throwable ignored) { }
        java.util.Collections.sort(out, new java.util.Comparator<Sim>() {
            @Override public int compare(Sim a, Sim b) { return a.slot - b.slot; }
        });
        cache = out;
        cacheAt = System.currentTimeMillis();
        return out;
    }

    private static String iccid(SubscriptionInfo si) {
        try { return si.getIccId(); } catch (Throwable t) { return null; }
    }

    /** true solo si hay dos o más SIM activas para llamar. */
    static boolean dual(Context c) { return list(c).size() >= 2; }

    static String key(PhoneAccountHandle h) {
        return h == null ? "" : h.getComponentName().flattenToString() + "|" + h.getId();
    }

    static Sim forHandle(Context c, PhoneAccountHandle h) {
        if (h == null) return null;
        String k = key(h);
        for (Sim s : list(c)) if (key(s.handle).equals(k)) return s;
        return null;
    }

    /** Para el historial del sistema (columnas PHONE_ACCOUNT_COMPONENT_NAME y PHONE_ACCOUNT_ID). */
    static Sim forAccount(Context c, String component, String id) {
        if (id == null) return null;
        for (Sim s : list(c)) {
            if (!id.equals(s.handle.getId())) continue;
            if (component == null || component.equals(s.handle.getComponentName().flattenToString())) return s;
        }
        return null;
    }

    private static String tail(String n) {
        String d = n == null ? "" : n.replaceAll("[^0-9]", "");
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    /** SIM recordada para ese número, o null. */
    static Sim preferred(Context c, String number) {
        String t = tail(number);
        if (t.isEmpty()) return null;
        String k = Util.prefs(c).getString("sim_" + t, null);
        if (k == null) return null;
        for (Sim s : list(c)) if (key(s.handle).equals(k)) return s;
        return null;
    }

    static void remember(Context c, String number, PhoneAccountHandle h) {
        String t = tail(number);
        if (t.isEmpty() || h == null) return;
        Util.prefs(c).edit().putString("sim_" + t, key(h)).apply();
    }

    /** Registra la SIM de una llamada si para ese número aún no había ninguna recordada. */
    static void learn(Context c, String number, PhoneAccountHandle h) {
        if (h == null || !dual(c)) return;
        String t = tail(number);
        if (t.isEmpty() || Util.prefs(c).contains("sim_" + t)) return;
        if (forHandle(c, h) != null) remember(c, number, h);
    }

    static int rememberedCount(Context c) {
        int n = 0;
        for (String k : Util.prefs(c).getAll().keySet()) if (k.startsWith("sim_")) n++;
        return n;
    }

    static void forgetAll(Context c) {
        android.content.SharedPreferences.Editor e = Util.prefs(c).edit();
        for (String k : Util.prefs(c).getAll().keySet()) if (k.startsWith("sim_")) e.remove(k);
        e.apply();
    }

    // ------------------------------------------------------------------ Llamar

    /** Llamar: con una SIM, como siempre; con dos, con la recordada o preguntando la primera vez. */
    static void call(Activity a, String number) {
        if (TextUtils.isEmpty(number)) return;
        if (!dual(a)) { Util.placeCall(a, number, null); return; }
        Sim p = preferred(a, number);
        if (p != null) { Util.placeCall(a, number, p.handle); return; }
        choose(a, number, true);
    }

    /** Mantener pulsado Llamar: elegir la SIM solo para esta llamada (no cambia la recordada). */
    static boolean chooseOnce(Activity a, String number) {
        if (TextUtils.isEmpty(number) || !dual(a)) return false;
        choose(a, number, false);
        return true;
    }

    private static void choose(final Activity a, final String number, final boolean save) {
        pick(a, number, save ? "Se recordará para este número. Mantén pulsado Llamar para usar otra."
                : "Solo para esta llamada.", new Picked() {
            @Override public void on(Sim s) {
                if (save) remember(a, number, s.handle);
                Util.placeCall(a, number, s.handle);
            }
        });
    }

    interface Picked { void on(Sim s); }

    /** Diálogo para elegir la SIM. */
    static void pick(final Activity a, String number, String hint, final Picked picked) {
        List<Sim> sims = list(a);
        int dp = Math.round(a.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(16 * dp, 4 * dp, 16 * dp, 4 * dp);
        Sim pref = preferred(a, number);
        final AlertDialog[] dlg = new AlertDialog[1];
        for (final Sim s : sims) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(12 * dp, 10 * dp, 12 * dp, 10 * dp);
            row.setMinimumHeight(60 * dp);
            row.setBackgroundResource(R.drawable.bg_tbtn);
            TextView badge = Ui.text(a, R.style.Text, String.valueOf(s.slot + 1), 15, true);
            badge.setGravity(Gravity.CENTER);
            badge.setTextColor(a.getColor(R.color.accent));
            badge.setBackgroundResource(R.drawable.bg_circle_soft);
            row.addView(badge, new LinearLayout.LayoutParams(40 * dp, 40 * dp));
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(Ui.text(a, R.style.Text, s.label(), 16, true));
            String sub = !TextUtils.isEmpty(s.number) ? Util.number(a, s.number) : null;
            if (pref != null && key(pref.handle).equals(key(s.handle))) sub = (sub == null ? "" : sub + " · ") + "La habitual para este número";
            if (sub != null) {
                TextView st = Ui.text(a, R.style.Text_Meta, sub);
                st.setPadding(0, 2 * dp, 0, 0);
                col.addView(st);
            }
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.setMarginStart(14 * dp);
            row.addView(col, cl);
            row.addView(Ui.iconView(a, R.drawable.ic_call_small, R.color.accent, 20));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (dlg[0] != null) dlg[0].dismiss();
                    picked.on(s);
                }
            });
            box.addView(row);
        }
        if (hint != null) {
            TextView h = Ui.text(a, R.style.Text_Meta, hint);
            h.setPadding(12 * dp, 10 * dp, 12 * dp, 4 * dp);
            h.setLineSpacing(2 * dp, 1f);
            box.addView(h);
        }
        String who = Util.contactName(a, number);
        dlg[0] = new AlertDialog.Builder(a)
                .setTitle("Llamar a " + (!TextUtils.isEmpty(who) ? who : Util.number(a, number)) + " con")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .show();
    }

}
