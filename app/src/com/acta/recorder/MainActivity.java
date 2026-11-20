package com.acta.recorder;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int TAB_RECENTS = 0, TAB_CONTACTS = 1, TAB_KEYPAD = 2, TAB_RECORDINGS = 3;
    private static final String[] TITLES = {"Recientes", "Contactos", "Teclado", "Grabaciones"};
    private static final int RC_ROLE = 10, RC_PERMS = 11;

    private int tab = TAB_KEYPAD;
    private View[] tabs;
    private View[] navs;
    private TextView title;

    // Datos
    private static final class Recent {
        String number, name;
        int type;
        long date;
        int duration;
        long recordingId = -1;
        String sim;   // «SIM 2» solo en móviles con doble SIM
    }

    private static final class Contact {
        long id;
        String lookup, name, photo, key;
        boolean starred;
        final List<String[]> numbers = new ArrayList<>(); // {número, etiqueta}
        String number() { return numbers.isEmpty() ? null : numbers.get(0)[0]; }
    }

    private final List<Object> recents = new ArrayList<>();
    private final List<Contact> allContacts = new ArrayList<>();
    private final List<Object> shownContacts = new ArrayList<>();
    private final List<Object> recordings = new ArrayList<>();

    private RecentsAdapter recentsAdapter;
    private ContactsAdapter contactsAdapter;
    private RecordingsAdapter recordingsAdapter;

    private final StringBuilder digits = new StringBuilder();
    private TextView digitsView, digitsMatch;
    private View delButton;
    private EditText search;

    private final android.database.ContentObserver contactsObserver =
            new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                @Override public void onChange(boolean self) { if (resumed) loadAll(); }
            };
    private boolean resumed;

    @Override
    protected void onDestroy() {
        getContentResolver().unregisterContentObserver(contactsObserver);
        setBackHandler(false);
        super.onDestroy();
    }

    private final BroadcastReceiver updated = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadAll(); }
    };

    private int themeSeen;

    @Override
    protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        themeSeen = Ui.themeGen;
        setContentView(R.layout.activity_main);
        title = findViewById(R.id.title);

        tabs = new View[]{findViewById(R.id.tab_recents), findViewById(R.id.tab_contacts),
                findViewById(R.id.tab_keypad), findViewById(R.id.tab_recordings)};
        navs = new View[]{findViewById(R.id.nav_recents), findViewById(R.id.nav_contacts),
                findViewById(R.id.nav_keypad), findViewById(R.id.nav_recordings)};
        for (int i = 0; i < navs.length; i++) {
            final int t = i;
            navs[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showTab(t); }
            });
        }
        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
                if (tab == TAB_KEYPAD && digits.length() > 0)
                    i.putExtra(ContactsContract.Intents.Insert.PHONE, digits.toString());
                safeStart(i);
            }
        });
        getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver);
        findViewById(R.id.btn_open_contacts).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openContactsApp(); }
        });
        findViewById(R.id.btn_search).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        setupRecents();
        setupContacts();
        setupKeypad();
        setupRecordings();

        showTab(b != null ? b.getInt("tab", TAB_KEYPAD) : TAB_KEYPAD);
        if (b == null) handleIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String a = i.getAction();
        if (Intent.ACTION_DIAL.equals(a) || Intent.ACTION_VIEW.equals(a)) {
            Uri u = i.getData();
            digits.setLength(0);
            if (u != null && "tel".equals(u.getScheme())) digits.append(Uri.decode(u.getSchemeSpecificPart()).replaceAll("[^0-9+*#]", ""));
            renderDigits();
            showTab(TAB_KEYPAD);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSeen != Ui.themeGen) { recreate(); return; }
        // Sin bloqueos: la app se abre siempre. Los permisos y la configuración de las dos voces
        // se ofrecen en el aviso de arriba, cuando el usuario quiera.
        // Si se volvió a bloquear (pantalla apagada o >30 s fuera), oculta las grabaciones.
        if (tab == TAB_RECORDINGS && AppLock.needed(this)) applyTab(TAB_KEYPAD);
        IntentFilter f = new IntentFilter(Util.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updated, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updated, f);
        resumed = true;
        if (tab == TAB_KEYPAD && digits.length() == 0) renderDigits();
        if (Util.nativeAdbEnabled(this)) {
            new Thread(new Runnable() { public void run() {
                final boolean ok = NativeAdbManager.ensureConnected(getApplicationContext());
                runOnUiThread(new Runnable() { public void run() { if (ok) refreshBanner(); } });
            } }).start();
        }
        refreshBanner();
        loadAll();
        new Thread(new Runnable() {
            @Override public void run() { AutoAnalyzer.resumePending(getApplicationContext()); }
        }).start();
        Cleanup.maybeRun(this);
        AiConsent.promptIfNeeded(this);
        if (Importer.canRead(this)) {
            new Thread(new Runnable() {
                @Override public void run() { Importer.run(getApplicationContext()); }
            }).start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        try { unregisterReceiver(updated); } catch (Exception ignored) { }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        refreshBanner();
        loadAll();
        if (Importer.canRead(this)) {
            new Thread(new Runnable() {
                @Override public void run() { Importer.run(getApplicationContext()); }
            }).start();
        }
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        refreshBanner();
    }

    // ---------------------------------------------------------------- Pestañas y aviso

    private void showTab(int t) {
        // Grabaciones protegidas con huella/PIN (si está activado en Ajustes).
        if (t == TAB_RECORDINGS && AppLock.needed(this)) {
            AppLock.require(this, new Runnable() {
                @Override public void run() { applyTab(TAB_RECORDINGS); }
            }, null);
            return;
        }
        applyTab(t);
    }

    private void applyTab(int t) {
        if (AppLock.enabled(this) && t == TAB_RECORDINGS)
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        if (t != TAB_RECORDINGS && selecting) exitSelection();
        tab = t;
        title.setText(TITLES[t]);
        int on = getColor(R.color.ink), off = getColor(R.color.ink_2), acc = getColor(R.color.accent);
        int[] icons = {R.id.nav_recents_icon, R.id.nav_contacts_icon, R.id.nav_keypad_icon, R.id.nav_recordings_icon};
        int[] labels = {R.id.nav_recents_label, R.id.nav_contacts_label, R.id.nav_keypad_label, R.id.nav_recordings_label};
        int[] pills = {R.id.nav_recents_pill, R.id.nav_contacts_pill, R.id.nav_keypad_pill, R.id.nav_recordings_pill};
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setVisibility(i == t ? View.VISIBLE : View.GONE);
            ((ImageView) findViewById(icons[i])).setImageTintList(ColorStateList.valueOf(i == t ? acc : off));
            findViewById(pills[i]).setBackgroundResource(i == t ? R.drawable.bg_nav_pill : 0);
            TextView l = findViewById(labels[i]);
            l.setTextColor(i == t ? on : off);
            l.setTypeface(i == t ? Ui.bold() : android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            navs[i].setSelected(i == t);
        }
        findViewById(R.id.btn_add).setVisibility(t == TAB_CONTACTS ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_search).setVisibility(t == TAB_RECORDINGS ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_open_contacts).setVisibility(t == TAB_CONTACTS ? View.VISIBLE : View.GONE);
        refreshBanner();
    }

    private void refreshBanner() {
        View banner = findViewById(R.id.banner);
        View okPill = findViewById(R.id.status_ok);
        TextView text = findViewById(R.id.banner_text);
        TextView sub = findViewById(R.id.banner_sub);
        TextView action = findViewById(R.id.banner_action);
        ImageView icon = findViewById(R.id.banner_icon);
        boolean full = Util.fullLine(this);
        boolean shiz = Util.shizukuEnabled(this);
        boolean nativ = Util.nativeAdbEnabled(this);
        boolean configured = NativeAdbManager.savedPort(this) != 0;
        String title = null, detail = null, act = null;
        boolean warn = true;
        View.OnClickListener click = null;
        if (!Util.isDefaultDialer(this)) {
            title = "Acta no es la app de teléfono";
            detail = "Hace falta para grabar las llamadas.";
            act = "Establecer";
            click = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = Util.requestDialerRole(MainActivity.this);
                    if (i != null) startActivityForResult(i, RC_ROLE);
                }
            };
        } else if (!Util.coreGranted(this) || !Util.has(this, Manifest.permission.READ_CONTACTS)) {
            title = "Faltan permisos";
            detail = "Teléfono, contactos y micrófono.";
            act = "Conceder";
            click = new View.OnClickListener() {
                @Override public void onClick(View v) { Util.askPermissions(MainActivity.this, RC_PERMS); }
            };
        }
        else if (shiz && !ShizukuManager.ready()) {
            title = "Shizuku no está listo";
            detail = "Ábrelo en Ajustes para grabar las dos voces.";
            act = "Ver";
            click = openSettings();
        } else if (!full && !shiz && (!nativ || !configured)) {
            title = "Graba las dos voces";
            detail = "Configúralo en 5 minutos con el tutorial.";
            act = "Empezar";
            warn = false;
            click = new View.OnClickListener() {
                @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, TutorialActivity.class)); }
            };
        } else if (!full && !shiz && !NativeAdbManager.connected(this)) {
            title = "Se perdió la conexión";
            detail = "Ahora solo se grabaría tu voz.";
            act = "Reconectar";
            click = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, TutorialActivity.class).putExtra(TutorialActivity.EXTRA_RECONNECT, true));
                }
            };
        }
        if (title == null) {
            banner.setVisibility(View.GONE);
            okPill.setVisibility(View.GONE);
            return;
        }
        okPill.setVisibility(View.GONE);
        int fg = getColor(warn ? R.color.warn : R.color.accent);
        banner.setBackgroundResource(warn ? R.drawable.bg_warn_card : R.drawable.bg_accent_card);
        icon.setImageResource(warn ? R.drawable.ic_alert : R.drawable.ic_wave);
        icon.setImageTintList(ColorStateList.valueOf(fg));
        text.setText(title);
        text.setTextColor(fg);
        sub.setText(detail);
        sub.setTextColor(fg);
        sub.setVisibility(detail == null ? View.GONE : View.VISIBLE);
        action.setText(act);
        action.setTextColor(fg);
        action.setOnClickListener(click);
        banner.setOnClickListener(click);
        banner.setVisibility(View.VISIBLE);
    }

    private View.OnClickListener openSettings() {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        };
    }

    private void loadAll() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Db.Call> recs = Db.get(MainActivity.this).all();
                final Set<Long> gone = new HashSet<>();
                for (Db.Call c : recs) if (!Cleanup.hasAudio(MainActivity.this, c)) gone.add(c.id);
                final List<Recent> rs = queryRecents(recs);
                final List<Contact> cs = queryContacts();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        fillRecents(rs);
                        allContacts.clear();
                        allContacts.addAll(cs);
                        filterContacts();
                        noAudio.clear();
                        noAudio.addAll(gone);
                        fillRecordings(recs);
                        renderDigits();
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- Recientes

    private void setupRecents() {
        ListView list = tabs[TAB_RECENTS].findViewById(R.id.list);
        recentsAdapter = new RecentsAdapter();
        list.setAdapter(recentsAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = recents.get(pos);
                if (!(o instanceof Recent)) return;
                Recent r = (Recent) o;
                if (r.recordingId >= 0) {
                    startActivity(new Intent(MainActivity.this, DetailActivity.class).putExtra("id", r.recordingId));
                } else if (!TextUtils.isEmpty(r.number)) {
                    digits.setLength(0);
                    digits.append(r.number);
                    renderDigits();
                    showTab(TAB_KEYPAD);
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = recents.get(pos);
                if (o instanceof Recent) recentMenu((Recent) o);
                return true;
            }
        });
        setEmpty(tabs[TAB_RECENTS], "Sin llamadas recientes", "Aquí aparecerá tu historial de llamadas.");
        ((ImageView) tabs[TAB_RECENTS].findViewById(R.id.empty_icon)).setImageResource(R.drawable.ic_tab_recents);
    }

    private List<Recent> queryRecents(List<Db.Call> recs) {
        List<Recent> out = new ArrayList<>();
        if (!Util.has(this, Manifest.permission.READ_CALL_LOG)) return out;
        boolean dual = SimInfo.dual(this);
        Cursor c = null;
        try {
            c = getContentResolver().query(CallLog.Calls.CONTENT_URI.buildUpon()
                            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "400").build(),
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                            CallLog.Calls.DATE, CallLog.Calls.DURATION,
                            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME, CallLog.Calls.PHONE_ACCOUNT_ID},
                    null, null, CallLog.Calls.DATE + " DESC");
            while (c != null && c.moveToNext()) {
                Recent r = new Recent();
                r.number = c.getString(0);
                r.name = c.getString(1);
                r.type = c.getInt(2);
                r.date = c.getLong(3);
                r.duration = c.getInt(4);
                if (dual) {
                    SimInfo.Sim s = SimInfo.forAccount(this, c.getString(5), c.getString(6));
                    if (s != null) r.sim = s.shortLabel();
                }
                for (Db.Call rc : recs) {
                    if (Math.abs(rc.startMs - r.date) < 120000 && sameNumber(rc.number, r.number)) {
                        r.recordingId = rc.id;
                        break;
                    }
                }
                out.add(r);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    private void fillRecents(List<Recent> rs) {
        recents.clear();
        long last = -1;
        for (Recent r : rs) {
            long k = Util.dayKey(r.date);
            if (k != last) { recents.add(Util.dayHeader(r.date)); last = k; }
            recents.add(r);
        }
        recentsAdapter.notifyDataSetChanged();
        tabs[TAB_RECENTS].findViewById(R.id.empty).setVisibility(recents.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private final class RecentsAdapter extends BaseAdapter {
        @Override public int getCount() { return recents.size(); }
        @Override public Object getItem(int i) { return recents.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return recents.get(i) instanceof String ? 0 : 1; }
        @Override public boolean isEnabled(int i) { return getItemViewType(i) == 1; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            Object o = recents.get(i);
            if (o instanceof String) {
                if (v == null) v = getLayoutInflater().inflate(R.layout.row_header, parent, false);
                ((TextView) v).setText((String) o);
                return v;
            }
            if (v == null) v = getLayoutInflater().inflate(R.layout.row_recent, parent, false);
            final Recent r = (Recent) o;
            boolean missed = r.type == CallLog.Calls.MISSED_TYPE || r.type == CallLog.Calls.REJECTED_TYPE
                    || r.type == CallLog.Calls.BLOCKED_TYPE;
            boolean out = r.type == CallLog.Calls.OUTGOING_TYPE;
            boolean first = i == 0 || recents.get(i - 1) instanceof String;
            boolean lastInGroup = i + 1 >= recents.size() || recents.get(i + 1) instanceof String;
            v.findViewById(R.id.card).setBackground(Ui.groupBg(MainActivity.this, first, lastInGroup));

            ImageView dir = v.findViewById(R.id.dir);
            dir.setImageResource(out ? R.drawable.ic_out : R.drawable.ic_in);
            dir.setImageTintList(ColorStateList.valueOf(getColor(missed ? R.color.danger : R.color.ink_2)));

            String formatted = Util.number(MainActivity.this, r.number);
            boolean named = !TextUtils.isEmpty(r.name);
            TextView t = v.findViewById(R.id.title);
            t.setText(named ? r.name : (TextUtils.isEmpty(r.number) ? "Número oculto" : formatted));
            t.setTextColor(getColor(missed ? R.color.danger : R.color.ink));

            Contact ct = named ? findContact(r.number) : null;
            avatarFor(v, named ? r.name : null, ct != null ? ct.photo : null);

            String kind = missed ? (r.type == CallLog.Calls.REJECTED_TYPE ? "Rechazada" : "Perdida")
                    : out ? "Saliente" : "Entrante";
            TextView subv = v.findViewById(R.id.sub);
            subv.setText(kind + " · " + Util.time(r.date));
            subv.setTextColor(getColor(missed ? R.color.danger : R.color.ink_2));
            boolean rec = r.recordingId >= 0;
            v.findViewById(R.id.rec).setVisibility(rec ? View.VISIBLE : View.GONE);
            TextView rd = v.findViewById(R.id.rec_dur);
            rd.setVisibility(rec || r.duration > 0 ? View.VISIBLE : View.GONE);
            rd.setText(r.duration > 0 ? Util.clock(r.duration) : "");
            rd.setTextColor(getColor(rec ? R.color.accent : R.color.ink_2));

            View call = v.findViewById(R.id.call);
            call.setVisibility(TextUtils.isEmpty(r.number) ? View.INVISIBLE : View.VISIBLE);
            call.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { SimInfo.call(MainActivity.this, r.number); }
            });
            call.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View x) { return SimInfo.chooseOnce(MainActivity.this, r.number); }
            });
            TextView simv = v.findViewById(R.id.sim);
            simv.setVisibility(r.sim != null ? View.VISIBLE : View.GONE);
            if (r.sim != null) simv.setText(r.sim);
            v.findViewById(R.id.sep).setVisibility(lastInGroup ? View.GONE : View.VISIBLE);
            return v;
        }
    }

    /** Avatar de una fila: foto del contacto, iniciales de color o icono de persona. */
    private void avatarFor(View row, String name, String photoUri) {
        TextView av = row.findViewById(R.id.avatar);
        ImageView icon = row.findViewById(R.id.avatar_icon);
        ImageView photo = row.findViewById(R.id.photo);
        Ui.avatar(av, icon, name);
        if (photo == null) return;
        photo.setClipToOutline(true);
        if (photoUri != null) {
            try { photo.setImageURI(Uri.parse(photoUri)); photo.setVisibility(View.VISIBLE); }
            catch (Exception e) { photo.setVisibility(View.GONE); }
        } else {
            photo.setImageDrawable(null);
            photo.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- Contactos

    private void setupContacts() {
        View root = tabs[TAB_CONTACTS];
        ListView list = root.findViewById(R.id.list);
        list.setFastScrollEnabled(true);
        contactsAdapter = new ContactsAdapter();
        list.setAdapter(contactsAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = shownContacts.get(pos);
                if (o instanceof Contact) openContact((Contact) o);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = shownContacts.get(pos);
                if (o instanceof Contact) contactMenu((Contact) o);
                return true;
            }
        });
        search = root.findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filterContacts(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        setEmpty(root, "Sin contactos", "Los contactos de tu teléfono aparecerán aquí.");
        ((ImageView) root.findViewById(R.id.empty_icon)).setImageResource(R.drawable.ic_tab_contacts);
    }

    /** Lee los contactos del teléfono: los mismos que muestra la app de Contactos de Xiaomi. */
    private List<Contact> queryContacts() {
        List<Contact> out = new ArrayList<>();
        if (!Util.has(this, Manifest.permission.READ_CONTACTS)) return out;
        java.util.LinkedHashMap<Long, Contact> map = new java.util.LinkedHashMap<>();
        Cursor c = null;
        try {
            c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.LABEL,
                            ContactsContract.CommonDataKinds.Phone.STARRED,
                            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC");
            while (c != null && c.moveToNext()) {
                String number = c.getString(3);
                if (TextUtils.isEmpty(number)) continue;
                long id = c.getLong(0);
                Contact ct = map.get(id);
                if (ct == null) {
                    ct = new Contact();
                    ct.id = id;
                    ct.lookup = c.getString(1);
                    ct.name = c.getString(2);
                    ct.starred = c.getInt(6) == 1;
                    ct.photo = c.getString(7);
                    map.put(id, ct);
                }
                boolean dup = false;
                for (String[] n : ct.numbers) if (sameNumber(n[0], number)) dup = true;
                if (dup) continue;
                CharSequence l = ContactsContract.CommonDataKinds.Phone.getTypeLabel(getResources(), c.getInt(4), c.getString(5));
                String[] entry = {number, l != null ? l.toString() : ""};
                if (c.getInt(8) == 1) ct.numbers.add(0, entry); else ct.numbers.add(entry);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        for (Contact ct : map.values()) {
            StringBuilder k = new StringBuilder(fold(ct.name));
            for (String[] n : ct.numbers) k.append(' ').append(n[0].replaceAll("[^0-9]", ""));
            ct.key = k.toString();
            out.add(ct);
        }
        return out;
    }

    private void filterContacts() {
        String q = search != null ? fold(search.getText().toString().trim()) : "";
        String qd = q.replaceAll("[^0-9]", "");
        boolean numeric = !qd.isEmpty() && qd.length() == q.replaceAll("[\\s+()-]", "").length();
        shownContacts.clear();
        if (q.isEmpty()) {
            boolean anyFav = false;
            for (Contact c : allContacts) if (c.starred) {
                if (!anyFav) { shownContacts.add("Favoritos"); anyFav = true; }
                shownContacts.add(c);
            }
            String letter = null;
            for (Contact c : allContacts) {
                String l = initial(TextUtils.isEmpty(c.name) ? c.number() : c.name);
                if (!l.equals(letter)) { shownContacts.add(l); letter = l; }
                shownContacts.add(c);
            }
        } else {
            for (Contact c : allContacts) {
                if (c.key.contains(q) || (numeric && c.key.contains(qd))) shownContacts.add(c);
            }
        }
        contactsAdapter.notifyDataSetChanged();
        tabs[TAB_CONTACTS].findViewById(R.id.empty).setVisibility(shownContacts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private final class ContactsAdapter extends BaseAdapter {
        @Override public int getCount() { return shownContacts.size(); }
        @Override public Object getItem(int i) { return shownContacts.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return shownContacts.get(i) instanceof String ? 0 : 1; }
        @Override public boolean isEnabled(int i) { return getItemViewType(i) == 1; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            Object o = shownContacts.get(i);
            if (o instanceof String) {
                if (v == null) v = getLayoutInflater().inflate(R.layout.row_header, parent, false);
                ((TextView) v).setText((String) o);
                return v;
            }
            if (v == null) v = getLayoutInflater().inflate(R.layout.row_contact, parent, false);
            final Contact c = (Contact) o;
            boolean first = i == 0 || shownContacts.get(i - 1) instanceof String;
            boolean last = i + 1 >= shownContacts.size() || shownContacts.get(i + 1) instanceof String;
            v.findViewById(R.id.card).setBackground(Ui.groupBg(MainActivity.this, first, last));
            v.findViewById(R.id.sep).setVisibility(last ? View.GONE : View.VISIBLE);
            String n = TextUtils.isEmpty(c.name) ? Util.number(MainActivity.this, c.number()) : c.name;
            avatarFor(v, TextUtils.isEmpty(c.name) ? null : c.name, c.photo);
            ((TextView) v.findViewById(R.id.title)).setText(n);
            String[] firstNum = c.numbers.get(0);
            String sub = Util.number(MainActivity.this, firstNum[0]);
            if (!TextUtils.isEmpty(firstNum[1])) sub = firstNum[1] + " · " + sub;
            if (c.numbers.size() > 1) sub += " · +" + (c.numbers.size() - 1);
            ((TextView) v.findViewById(R.id.sub)).setText(sub);
            v.findViewById(R.id.call).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { callContact(c); }
            });
            v.findViewById(R.id.call).setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View x) { return SimInfo.chooseOnce(MainActivity.this, c.number()); }
            });
            return v;
        }
    }

    private void callContact(final Contact c) {
        if (c.numbers.size() == 1) { SimInfo.call(this, c.number()); return; }
        String[] items = new String[c.numbers.size()];
        for (int i = 0; i < items.length; i++) {
            String[] n = c.numbers.get(i);
            items[i] = (TextUtils.isEmpty(n[1]) ? "" : n[1] + "  ·  ") + Util.number(this, n[0]);
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        SimInfo.call(MainActivity.this, c.numbers.get(w)[0]);
                    }
                }).show();
    }

    /** Abre la ficha del contacto en la app de Contactos del teléfono. */
    /** Abre la ficha del contacto en la app de Contactos del dispositivo. */
    private void openContact(Contact c) {
        Uri u = null;
        if (c.lookup != null) u = ContactsContract.Contacts.getLookupUri(c.id, c.lookup);
        if (u == null) u = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.id);
        Intent i = new Intent(Intent.ACTION_VIEW, u);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        safeStart(i);
    }

    /** Abre la aplicación de Contactos del teléfono (lista completa). */
    private void openContactsApp() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI));
            return;
        } catch (Exception ignored) { }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS);
        safeStart(main);
    }

    private void contactMenu(final Contact c) {
        final String[] items = {"Llamar", "Ver ficha", "Programar llamada", "Enviar mensaje", "Ver en Contactos", "Editar contacto"};
        new android.app.AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        Uri u = ContactsContract.Contacts.getLookupUri(c.id, c.lookup);
                        if (w == 0) callContact(c);
                        else if (w == 1) openPerson(c.number(), c.name);
                        else if (w == 2) ScheduleCall.show(MainActivity.this, c.number(), c.name, null);
                        else if (w == 3) safeStart(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + c.number())));
                        else if (w == 4) safeStart(new Intent(Intent.ACTION_VIEW, u));
                        else safeStart(new Intent(Intent.ACTION_EDIT, u).putExtra("finishActivityOnSaveCompleted", true));
                    }
                }).show();
    }

    private Contact findContact(String number) {
        if (TextUtils.isEmpty(number)) return null;
        for (Contact c : allContacts) for (String[] n : c.numbers) if (sameNumber(n[0], number)) return c;
        return null;
    }

    private void recentMenu(final Recent r) {
        if (TextUtils.isEmpty(r.number)) return;
        final Contact c = findContact(r.number);
        final boolean spam = Util.isSpam(this, r.number);
        final String[] items = {"Llamar", "Ver ficha", "Programar llamada", "Enviar mensaje",
                c != null ? "Ver contacto" : "Añadir a contactos",
                spam ? "Quitar de spam" : "Marcar como spam", "Copiar número", "Eliminar del historial"};
        new android.app.AlertDialog.Builder(this)
                .setTitle(c != null ? c.name : Util.number(this, r.number))
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        switch (w) {
                            case 0: SimInfo.call(MainActivity.this, r.number); break;
                            case 1: openPerson(r.number, c != null ? c.name : r.name); break;
                            case 2: ScheduleCall.show(MainActivity.this, r.number, c != null ? c.name : r.name, null); break;
                            case 3: safeStart(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + r.number))); break;
                            case 4:
                                if (c != null) openContact(c);
                                else addToContacts(r.number);
                                break;
                            case 5:
                                if (spam) { Util.removeSpam(MainActivity.this, r.number);
                                    android.widget.Toast.makeText(MainActivity.this, "Quitado de spam", android.widget.Toast.LENGTH_SHORT).show(); }
                                else { Util.addSpam(MainActivity.this, r.number);
                                    android.widget.Toast.makeText(MainActivity.this, "Marcado como spam. Sus llamadas se rechazarán solas.", android.widget.Toast.LENGTH_LONG).show(); }
                                break;
                            case 6:
                                android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Número", r.number));
                                break;
                            default: deleteRecent(r);
                        }
                    }
                }).show();
    }

    private void openPerson(String number, String name) {
        if (TextUtils.isEmpty(number)) return;
        startActivity(new Intent(this, PersonActivity.class).putExtra("number", number).putExtra("name", name));
    }

    private void addToContacts(String number) {
        Intent i = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        i.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        i.putExtra(ContactsContract.Intents.Insert.PHONE, number);
        safeStart(i);
    }

    private void deleteRecent(Recent r) {
        if (!Util.has(this, Manifest.permission.WRITE_CALL_LOG)) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CALL_LOG}, RC_PERMS);
            return;
        }
        try {
            getContentResolver().delete(CallLog.Calls.CONTENT_URI,
                    CallLog.Calls.DATE + "=? AND " + CallLog.Calls.NUMBER + "=?",
                    new String[]{String.valueOf(r.date), r.number});
        } catch (Exception ignored) { }
        loadAll();
    }

    private void safeStart(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { android.widget.Toast.makeText(this, "No hay ninguna app para esta acción", android.widget.Toast.LENGTH_SHORT).show(); }
    }

    // ---------------------------------------------------------------- Teclado

    private void setupKeypad() {
        View root = tabs[TAB_KEYPAD];
        digitsView = root.findViewById(R.id.digits);
        digitsMatch = root.findViewById(R.id.digits_match);
        delButton = root.findViewById(R.id.btn_del);
        Keypad.build((LinearLayout) root.findViewById(R.id.grid), false, new Keypad.Listener() {
            @Override public void onKey(char c) {
                if (digits.length() < 32) digits.append(c);
                renderDigits();
            }
            @Override public void onLongKey(char c) {
                if (c == '0') digits.append('+');
                else digits.append(c);
                renderDigits();
            }
        });
        // Mantener pulsado el número: Pegar / Copiar, como en el marcador nativo.
        View.OnLongClickListener pasteMenu = new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showPasteMenu(); return true; }
        };
        digitsView.setOnLongClickListener(pasteMenu);
        root.findViewById(R.id.digits_area).setOnLongClickListener(pasteMenu);
        delButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (digits.length() > 0) digits.deleteCharAt(digits.length() - 1);
                renderDigits();
            }
        });
        delButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                digits.setLength(0);
                renderDigits();
                return true;
            }
        });
        root.findViewById(R.id.btn_dial).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (digits.length() == 0) {
                    // Sin número: recupera el último marcado, como los teléfonos nativos.
                    for (Object o : recents) {
                        if (o instanceof Recent && ((Recent) o).type == CallLog.Calls.OUTGOING_TYPE) {
                            digits.append(((Recent) o).number);
                            break;
                        }
                    }
                    renderDigits();
                    return;
                }
                SimInfo.call(MainActivity.this, digits.toString());
            }
        });
        root.findViewById(R.id.btn_dial).setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                return digits.length() > 0 && SimInfo.chooseOnce(MainActivity.this, digits.toString());
            }
        });
    }

    private void renderDigits() {
        if (digitsView == null) return;
        String raw = digits.toString();
        String f = raw.length() > 3 ? android.telephony.PhoneNumberUtils.formatNumber(raw, Util.country(this)) : null;
        digitsView.setText(f != null ? f : raw);
        delButton.setVisibility(raw.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        String match = "";
        boolean canAdd = false;
        if (raw.length() >= 3) {
            Contact found = null;
            for (Contact c : allContacts) {
                for (String[] n : c.numbers) {
                    if (sameNumber(n[0], raw) || n[0].replaceAll("[^0-9+]", "").startsWith(raw)) { found = c; break; }
                }
                if (found != null) break;
            }
            if (found != null) match = found.name;
            else if (raw.length() >= 6) { match = "Añadir a contactos"; canAdd = true; }
        }
        digitsMatch.setText(match);
        digitsMatch.setTextColor(getColor(R.color.accent));
        digitsMatch.setCompoundDrawablesRelative(null, null, null, null);
        final boolean add = canAdd;
        digitsMatch.setOnClickListener(add ? new View.OnClickListener() {
            @Override public void onClick(View v) { addToContacts(digits.toString()); }
        } : null);
        digitsMatch.setClickable(add);
    }

    // ---------------------------------------------------------------- Pegar un número copiado


    private void showPasteMenu() {
        android.widget.PopupMenu m = new android.widget.PopupMenu(this, digitsView, android.view.Gravity.CENTER);
        android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
        if (cm != null && cm.hasPrimaryClip()) m.getMenu().add(0, 1, 0, "Pegar");
        if (digits.length() > 0) {
            m.getMenu().add(0, 2, 1, "Copiar");
            m.getMenu().add(0, 3, 2, "Borrar todo");
        }
        if (m.getMenu().size() == 0) {
            android.widget.Toast.makeText(this, "No hay nada copiado", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        m.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem it) {
                if (it.getItemId() == 1) pasteNumber();
                else if (it.getItemId() == 2) {
                    android.content.ClipboardManager c2 = getSystemService(android.content.ClipboardManager.class);
                    if (c2 != null) c2.setPrimaryClip(android.content.ClipData.newPlainText("Número", digits.toString()));
                }
                else { digits.setLength(0); renderDigits(); }
                return true;
            }
        });
        m.show();
    }

    private void pasteNumber() {
        String n = null;
        try {
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            android.content.ClipData clip = cm != null ? cm.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence t = clip.getItemAt(0).coerceToText(this);
                n = phoneFrom(t);
            }
        } catch (Throwable ignored) { }
        if (n == null) {
            android.widget.Toast.makeText(this, "Lo copiado no contiene un número de teléfono", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        digits.setLength(0);
        digits.append(n);
        renderDigits();
    }

    /** Saca el teléfono de un texto copiado: «+34 612 34 56 78», «(612) 345-678», «tel:612345678», «Llámame al 612.345.678». */
    static String phoneFrom(CharSequence text) {
        if (text == null) return null;
        String s = text.toString().replace('\u00A0', ' ').replace('\u2011', '-').replace('\u2012', '-')
                .replace('\u2013', '-').replace('\u2212', '-').trim();
        if (s.regionMatches(true, 0, "tel:", 0, 4)) s = Uri.decode(s.substring(4));
        // Códigos de servicio (*#06#, *21*600…#): se pegan tal cual.
        String compact = s.replaceAll("[\\s\\-().]", "");
        if (compact.matches("[*#+0-9]{2,32}") && (compact.indexOf('*') >= 0 || compact.indexOf('#') >= 0)) return compact;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\+?\\d[\\d\\s\\-().\\/]*\\d|\\d").matcher(s);
        String best = null;
        int bestDigits = 0;
        while (m.find()) {
            String g = m.group();
            String d = g.replaceAll("[^0-9]", "");
            if (d.length() > bestDigits) { bestDigits = d.length(); best = (g.startsWith("+") ? "+" : "") + d; }
        }
        if (best == null || bestDigits < 3 || bestDigits > 20) return null;
        return best.length() > 32 ? best.substring(0, 32) : best;
    }

    // ---------------------------------------------------------------- Grabaciones

    private void setupRecordings() {
        ListView list = tabs[TAB_RECORDINGS].findViewById(R.id.list);
        recordingsAdapter = new RecordingsAdapter();
        list.setAdapter(recordingsAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = recordings.get(pos);
                if (!(o instanceof Db.Call)) return;
                if (selecting) toggleSelected(((Db.Call) o).id);
                else startActivity(new Intent(MainActivity.this, DetailActivity.class).putExtra("id", ((Db.Call) o).id));
            }
        });
        // Pulsación larga: modo selección para borrar o marcar favoritas varias a la vez.
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = recordings.get(pos);
                if (!(o instanceof Db.Call)) return false;
                if (!selecting) {
                    selecting = true;
                    selected.clear();
                    setBackHandler(true);
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                }
                toggleSelected(((Db.Call) o).id);
                return true;
            }
        });
        findViewById(R.id.sel_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exitSelection(); }
        });
        findViewById(R.id.sel_all).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<Db.Call> all = recordingCalls();
                if (selected.size() == all.size()) { exitSelection(); return; }
                for (Db.Call c : all) selected.add(c.id);
                renderSelection();
            }
        });
        findViewById(R.id.sel_fav).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { favSelected(); }
        });
        findViewById(R.id.sel_delete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmBatchDelete(); }
        });
        setEmpty(tabs[TAB_RECORDINGS], "Sin grabaciones",
                "Cada llamada quedará grabada aquí con su número, fecha, hora y duración.");
        ((ImageView) tabs[TAB_RECORDINGS].findViewById(R.id.empty_icon)).setImageResource(R.drawable.ic_tab_recordings);
    }

    private void fillRecordings(List<Db.Call> calls) {
        if (selecting) {
            Set<Long> ids = new HashSet<>();
            for (Db.Call c : calls) ids.add(c.id);
            selected.retainAll(ids);
        }
        recordings.clear();
        long last = -1;
        for (Db.Call c : calls) {
            long k = Util.dayKey(c.startMs);
            if (k != last) { recordings.add(Util.dayHeader(c.startMs)); last = k; }
            recordings.add(c);
        }
        recordingsAdapter.notifyDataSetChanged();
        tabs[TAB_RECORDINGS].findViewById(R.id.empty).setVisibility(recordings.isEmpty() ? View.VISIBLE : View.GONE);
        if (selecting) {
            if (selected.isEmpty()) exitSelection();
            else renderSelection();
        }
    }

    // ---- Selección múltiple

    private boolean selecting;
    private final Set<Long> selected = new HashSet<>();
    private final Set<Long> noAudio = new HashSet<>();
    private Object backCallback;

    private List<Db.Call> recordingCalls() {
        List<Db.Call> out = new ArrayList<>();
        for (Object o : recordings) if (o instanceof Db.Call) out.add((Db.Call) o);
        return out;
    }

    private List<Db.Call> selectedCalls() {
        List<Db.Call> out = new ArrayList<>();
        for (Db.Call c : recordingCalls()) if (selected.contains(c.id)) out.add(c);
        return out;
    }

    private void toggleSelected(long id) {
        if (!selected.remove(id)) selected.add(id);
        if (selected.isEmpty()) exitSelection();
        else renderSelection();
    }

    private void exitSelection() {
        selecting = false;
        selected.clear();
        setBackHandler(false);
        renderSelection();
    }

    private void renderSelection() {
        findViewById(R.id.header).setVisibility(selecting ? View.GONE : View.VISIBLE);
        findViewById(R.id.sel_bar).setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (selecting) {
            int n = selected.size();
            ((TextView) findViewById(R.id.sel_count)).setText(n + (n == 1 ? " seleccionada" : " seleccionadas"));
            boolean allFav = true;
            for (Db.Call c : selectedCalls()) if (!c.fav) allFav = false;
            ImageView fav = findViewById(R.id.sel_fav);
            fav.setImageResource(allFav ? R.drawable.ic_star_on : R.drawable.ic_star);
            fav.setImageTintList(ColorStateList.valueOf(getColor(allFav ? R.color.accent : R.color.ink)));
            fav.setContentDescription(allFav ? "Quitar de favoritas" : "Añadir a favoritas");
            View all = findViewById(R.id.sel_all);
            all.setContentDescription(n == recordingCalls().size() ? "Quitar la selección" : "Seleccionar todas");
        }
        recordingsAdapter.notifyDataSetChanged();
    }

    /** Atrás cancela la selección. En Android 13+ el gesto atrás ya no pasa por onBackPressed. */
    private void setBackHandler(boolean on) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (on && backCallback == null) {
            android.window.OnBackInvokedCallback cb = new android.window.OnBackInvokedCallback() {
                @Override public void onBackInvoked() { exitSelection(); }
            };
            backCallback = cb;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        } else if (!on && backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                        (android.window.OnBackInvokedCallback) backCallback);
            } catch (Throwable ignored) { }
            backCallback = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (selecting) { exitSelection(); return; }
        super.onBackPressed();
    }

    private void favSelected() {
        final List<Db.Call> sel = selectedCalls();
        boolean allFav = true;
        for (Db.Call c : sel) if (!c.fav) allFav = false;
        final boolean fav = !allFav;
        for (Db.Call c : sel) c.fav = fav;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                for (Db.Call c : sel) Db.get(app).setFav(c.id, fav);
            }
        }).start();
        int n = sel.size();
        android.widget.Toast.makeText(this, fav
                ? (n == 1 ? "Añadida a favoritas: no se borrará" : n + " añadidas a favoritas: no se borrarán")
                : (n == 1 ? "Quitada de favoritas" : n + " quitadas de favoritas"), android.widget.Toast.LENGTH_SHORT).show();
        exitSelection();
    }

    private void confirmBatchDelete() {
        final List<Db.Call> targets = new ArrayList<>();
        int favs = 0;
        boolean anyAudio = false;
        for (Db.Call c : selectedCalls()) {
            if (c.fav) { favs++; continue; }
            targets.add(c);
            if (!noAudio.contains(c.id)) anyAudio = true;
        }
        if (targets.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle(favs == 1 ? "Es una favorita" : "Son favoritas")
                    .setMessage("Las grabaciones favoritas no se borran. Quítales la estrella si quieres borrarlas.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }
        final int n = targets.size();
        int dp = Math.round(getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(16 * dp, 0, 16 * dp, 4 * dp);
        String msg = "No se puede deshacer.";
        if (favs > 0) msg = (favs == 1 ? "La favorita seleccionada se conserva. " : "Las " + favs + " favoritas seleccionadas se conservan. ") + msg;
        TextView m = Ui.text(this, R.style.Text_Meta, msg);
        m.setPadding(8 * dp, 0, 8 * dp, 10 * dp);
        box.addView(m);
        final android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        if (anyAudio) box.addView(deleteOption(R.drawable.ic_wave, R.color.ink, "Borrar solo el audio",
                "Se conservan la transcripción, el resumen y las tareas", new Runnable() {
                    @Override public void run() { dlg[0].dismiss(); batchDelete(targets, false); }
                }));
        box.addView(deleteOption(R.drawable.ic_trash, R.color.danger, "Borrar todo",
                "Audio, transcripción, resumen y tareas", new Runnable() {
                    @Override public void run() { dlg[0].dismiss(); batchDelete(targets, true); }
                }));
        dlg[0] = new android.app.AlertDialog.Builder(this)
                .setTitle("Borrar " + n + (n == 1 ? " grabación" : " grabaciones"))
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private View deleteOption(int icon, int color, String title, String sub, final Runnable click) {
        int dp = Math.round(getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(12 * dp, 10 * dp, 12 * dp, 10 * dp);
        row.setMinimumHeight(60 * dp);
        row.setBackgroundResource(R.drawable.bg_tbtn);
        row.addView(Ui.iconView(this, icon, color, 22));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, R.style.Text, title, 16, true);
        t.setTextColor(getColor(color));
        col.addView(t);
        TextView s = Ui.text(this, R.style.Text_Meta, sub);
        s.setPadding(0, 2 * dp, 0, 0);
        col.addView(s);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.setMarginStart(16 * dp);
        row.addView(col, cl);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { click.run(); }
        });
        return row;
    }

    private void batchDelete(final List<Db.Call> targets, final boolean everything) {
        exitSelection();
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                int n = 0;
                for (Db.Call c : targets) {
                    if (everything) {
                        Cleanup.deleteAudio(app, c);
                        Db.get(app).delete(c.id);
                        n++;
                    } else if (!AutoAnalyzer.isRunning(c.id) && Cleanup.deleteAudio(app, c)) {
                        n++;
                    }
                }
                if (everything) try { TasksWidget.refresh(app); } catch (Throwable ignored) { }
                final int done = n;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        String t = everything
                                ? (done == 1 ? "1 grabación borrada" : done + " grabaciones borradas")
                                : (done == 1 ? "1 audio borrado" : done + " audios borrados");
                        android.widget.Toast.makeText(app, t, android.widget.Toast.LENGTH_SHORT).show();
                        loadAll();
                    }
                });
            }
        }).start();
    }

    private final class RecordingsAdapter extends BaseAdapter {
        @Override public int getCount() { return recordings.size(); }
        @Override public Object getItem(int i) { return recordings.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return recordings.get(i) instanceof String ? 0 : 1; }
        @Override public boolean isEnabled(int i) { return getItemViewType(i) == 1; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            LayoutInflater inf = getLayoutInflater();
            Object o = recordings.get(i);
            if (o instanceof String) {
                if (v == null) v = inf.inflate(R.layout.row_header, parent, false);
                ((TextView) v).setText((String) o);
                return v;
            }
            if (v == null) v = inf.inflate(R.layout.row_call, parent, false);
            Db.Call c = (Db.Call) o;
            String formatted = TextUtils.isEmpty(c.number) ? "Número oculto" : Util.number(MainActivity.this, c.number);
            boolean named = !TextUtils.isEmpty(c.name);
            boolean sel = selecting && selected.contains(c.id);
            v.findViewById(R.id.card).setBackgroundResource(sel ? R.drawable.bg_card_sel : R.drawable.bg_card_click);
            TextView av = v.findViewById(R.id.avatar);
            ImageView avIcon = v.findViewById(R.id.avatar_icon);
            if (sel) {
                av.setText("");
                av.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.accent)));
                avIcon.setImageResource(R.drawable.ic_check);
                avIcon.setImageTintList(ColorStateList.valueOf(getColor(R.color.on_accent)));
                avIcon.setVisibility(View.VISIBLE);
            } else {
                avIcon.setImageResource(R.drawable.ic_tab_contacts);
                Ui.avatar(av, avIcon, named ? c.name : null);
            }
            v.findViewById(R.id.fav).setVisibility(c.fav ? View.VISIBLE : View.GONE);
            ImageView dir = v.findViewById(R.id.dir);
            dir.setImageResource(c.direction == 1 ? R.drawable.ic_in : R.drawable.ic_out);
            dir.setImageTintList(ColorStateList.valueOf(getColor(R.color.ink_2)));
            ((TextView) v.findViewById(R.id.title)).setText(named ? c.name : formatted);
            ((TextView) v.findViewById(R.id.sub)).setText((c.direction == 1 ? "Entrante" : "Saliente") + " · " + Util.time(c.startMs));
            ((TextView) v.findViewById(R.id.dur)).setText(Util.clock(c.durationSec));

            Summarizer.Result ai = Summarizer.parse(c.summary);
            String summary = ai != null ? ai.summary : (c.summary != null ? c.summary.trim() : "");
            TextView sv = v.findViewById(R.id.summary);
            sv.setText(summary);
            sv.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);

            boolean busy = AutoAnalyzer.isRunning(c.id);
            v.findViewById(R.id.chip_busy).setVisibility(busy ? View.VISIBLE : View.GONE);
            TextView tasks = v.findViewById(R.id.chip_tasks);
            int pend = Summarizer.pendingTasks(c.summary);
            if (pend > 0) {
                tasks.setText(pend + (pend == 1 ? " tarea pendiente" : " tareas pendientes"));
                tasks.setBackgroundResource(R.drawable.bg_chip_a);
                tasks.setTextColor(getColor(R.color.accent));
                Ui.icon(tasks, R.drawable.ic_check, R.color.accent, 14);
                tasks.setVisibility(View.VISIBLE);
            } else if (ai != null && !ai.tasks.isEmpty()) {
                tasks.setText("Tareas hechas");
                tasks.setBackgroundResource(R.drawable.bg_chip_n);
                tasks.setTextColor(getColor(R.color.ink_2));
                Ui.icon(tasks, R.drawable.ic_check, R.color.ink_2, 14);
                tasks.setVisibility(View.VISIBLE);
            } else {
                tasks.setVisibility(View.GONE);
            }
            TextView src = v.findViewById(R.id.chip_src);
            boolean gone = noAudio.contains(c.id);
            String label = gone ? "Audio borrado" : Ui.sourceLabel(c.source);
            src.setText(label == null ? "" : label);
            src.setVisibility(label == null ? View.GONE : View.VISIBLE);
            Ui.icon(src, gone ? R.drawable.ic_doc : R.drawable.ic_wave, R.color.ink_2, 14);
            return v;
        }
    }

    // ---------------------------------------------------------------- Utilidades

    private void setEmpty(View root, String t, String s) {
        ((TextView) root.findViewById(R.id.empty_title)).setText(t);
        ((TextView) root.findViewById(R.id.empty_sub)).setText(s);
    }

    private static String tail(String n) {
        String d = n == null ? "" : n.replaceAll("[^0-9]", "");
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    private static boolean sameNumber(String a, String b) {
        if (a == null || b == null) return false;
        String x = tail(a), y = tail(b);
        return !x.isEmpty() && x.equals(y);
    }

    private static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    private static String initial(String n) {
        if (TextUtils.isEmpty(n)) return "#";
        int cp = n.codePointAt(0);
        return Character.isLetter(cp) ? new String(Character.toChars(cp)).toUpperCase(Util.ES) : "#";
    }
}
