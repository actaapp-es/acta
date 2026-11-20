package com.acta.recorder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class Db extends SQLiteOpenHelper {

    static final class Call {
        long id;
        String number;
        String name;
        int direction;      // 1 = entrante, 2 = saliente
        long startMs;
        int durationSec;    // duración de la llamada según el registro del sistema
        int recordedSec;    // duración real del audio
        String file;
        String source;
        String summary;
        String transcript;
        String segments;    // JSON [[segundo, "frase"], ...] con los tiempos de la transcripción
        String marks;       // segundos marcados durante la llamada, separados por comas
        String sim;         // «SIM 2 · Vodafone» (solo con doble SIM)
        boolean fav;        // favorita: el borrado automático y en lote no la toca
    }

    private static Db instance;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "acta.db", null, 7);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE calls (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "number TEXT," +
                "name TEXT," +
                "direction INTEGER," +
                "start_ms INTEGER," +
                "duration_sec INTEGER," +
                "recorded_sec INTEGER," +
                "file TEXT," +
                "source TEXT," +
                "summary TEXT," +
                "transcript TEXT," +
                "segments TEXT," +
                "marks TEXT," +
                "sim TEXT," +
                "fav INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX calls_start ON calls(start_ms)");
        createSpam(db);
    }

    private void createSpam(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS spam (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "number TEXT," +
                "name TEXT," +
                "start_ms INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) createSpam(db);
        if (oldV < 3) {
            try { db.execSQL("ALTER TABLE calls ADD COLUMN summary TEXT"); } catch (Exception ignored) { }
        }
        if (oldV < 4) {
            try { db.execSQL("ALTER TABLE calls ADD COLUMN transcript TEXT"); } catch (Exception ignored) { }
        }
        if (oldV < 5) {
            try { db.execSQL("ALTER TABLE calls ADD COLUMN segments TEXT"); } catch (Exception ignored) { }
            try { db.execSQL("ALTER TABLE calls ADD COLUMN marks TEXT"); } catch (Exception ignored) { }
        }
        if (oldV < 6) {
            try { db.execSQL("ALTER TABLE calls ADD COLUMN sim TEXT"); } catch (Exception ignored) { }
        }
        if (oldV < 7) {
            try { db.execSQL("ALTER TABLE calls ADD COLUMN fav INTEGER DEFAULT 0"); } catch (Exception ignored) { }
        }
    }

    /** Guarda el análisis de IA (JSON) y la transcripción. */
    void updateAnalysis(long id, String summary, String transcript) {
        ContentValues v = new ContentValues();
        v.put("summary", summary);
        v.put("transcript", transcript);
        getWritableDatabase().update("calls", v, "_id=?", new String[]{String.valueOf(id)});
    }

    /** Igual que el anterior, guardando también los tiempos de cada frase. */
    void updateAnalysis(long id, String summary, String transcript, String segments) {
        ContentValues v = new ContentValues();
        v.put("summary", summary);
        v.put("transcript", transcript);
        v.put("segments", segments);
        getWritableDatabase().update("calls", v, "_id=?", new String[]{String.valueOf(id)});
    }

    void updateMarks(long id, String marks) {
        ContentValues v = new ContentValues();
        v.put("marks", marks);
        getWritableDatabase().update("calls", v, "_id=?", new String[]{String.valueOf(id)});
    }

    void updateSummary(long id, String summary) {
        ContentValues v = new ContentValues();
        v.put("summary", summary);
        getWritableDatabase().update("calls", v, "_id=?", new String[]{String.valueOf(id)});
    }

    // ---- Papelera de spam (llamadas rechazadas) ----

    void insertSpam(String number, String name, long when) {
        ContentValues v = new ContentValues();
        v.put("number", number);
        v.put("name", name);
        v.put("start_ms", when);
        getWritableDatabase().insert("spam", null, v);
    }

    List<Call> allSpam() {
        List<Call> out = new ArrayList<>();
        Cursor cur = getReadableDatabase().query("spam", null, null, null, null, null, "start_ms DESC");
        try {
            while (cur.moveToNext()) {
                Call c = new Call();
                c.number = cur.getString(cur.getColumnIndexOrThrow("number"));
                c.name = cur.getString(cur.getColumnIndexOrThrow("name"));
                c.startMs = cur.getLong(cur.getColumnIndexOrThrow("start_ms"));
                out.add(c);
            }
        } finally {
            cur.close();
        }
        return out;
    }

    int spamCount() {
        Cursor cur = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM spam", null);
        try { return cur.moveToFirst() ? cur.getInt(0) : 0; } finally { cur.close(); }
    }

    void clearSpam() {
        getWritableDatabase().delete("spam", null, null);
    }

    long insert(Call c) {
        ContentValues v = new ContentValues();
        v.put("number", c.number);
        v.put("name", c.name);
        v.put("direction", c.direction);
        v.put("start_ms", c.startMs);
        v.put("duration_sec", c.durationSec);
        v.put("recorded_sec", c.recordedSec);
        v.put("file", c.file);
        v.put("source", c.source);
        v.put("marks", c.marks);
        v.put("sim", c.sim);
        return getWritableDatabase().insert("calls", null, v);
    }

    void delete(long id) {
        getWritableDatabase().delete("calls", "_id=?", new String[]{String.valueOf(id)});
    }

    void setFav(long id, boolean fav) {
        ContentValues v = new ContentValues();
        v.put("fav", fav ? 1 : 0);
        getWritableDatabase().update("calls", v, "_id=?", new String[]{String.valueOf(id)});
    }

    List<Call> all() {
        List<Call> out = new ArrayList<>();
        Cursor cur = getReadableDatabase().query("calls", null, null, null, null, null, "start_ms DESC");
        try {
            while (cur.moveToNext()) out.add(read(cur));
        } finally {
            cur.close();
        }
        return out;
    }

    /** Llamadas grabadas con ese número, de la más reciente a la más antigua. */
    List<Call> recentFor(String number, int limit) {
        List<Call> out = new ArrayList<>();
        if (number == null || number.isEmpty()) return out;
        Cursor cur = getReadableDatabase().query("calls", null, null, null, null, null, "start_ms DESC", "500");
        try {
            while (cur.moveToNext() && out.size() < limit) {
                Call c = read(cur);
                if (c.number != null && android.telephony.PhoneNumberUtils.compare(number, c.number)) out.add(c);
            }
        } finally {
            cur.close();
        }
        return out;
    }

    Call byId(long id) {
        Cursor cur = getReadableDatabase().query("calls", null, "_id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return cur.moveToFirst() ? read(cur) : null;
        } finally {
            cur.close();
        }
    }

    private static Call read(Cursor cur) {
        Call c = new Call();
        c.id = cur.getLong(cur.getColumnIndexOrThrow("_id"));
        c.number = cur.getString(cur.getColumnIndexOrThrow("number"));
        c.name = cur.getString(cur.getColumnIndexOrThrow("name"));
        c.direction = cur.getInt(cur.getColumnIndexOrThrow("direction"));
        c.startMs = cur.getLong(cur.getColumnIndexOrThrow("start_ms"));
        c.durationSec = cur.getInt(cur.getColumnIndexOrThrow("duration_sec"));
        c.recordedSec = cur.getInt(cur.getColumnIndexOrThrow("recorded_sec"));
        c.file = cur.getString(cur.getColumnIndexOrThrow("file"));
        c.source = cur.getString(cur.getColumnIndexOrThrow("source"));
        int si = cur.getColumnIndex("summary");
        if (si >= 0) c.summary = cur.getString(si);
        int ti = cur.getColumnIndex("transcript");
        if (ti >= 0) c.transcript = cur.getString(ti);
        int gi = cur.getColumnIndex("segments");
        if (gi >= 0) c.segments = cur.getString(gi);
        int mi = cur.getColumnIndex("marks");
        if (mi >= 0) c.marks = cur.getString(mi);
        int xi = cur.getColumnIndex("sim");
        if (xi >= 0) c.sim = cur.getString(xi);
        int fi = cur.getColumnIndex("fav");
        if (fi >= 0) c.fav = cur.getInt(fi) == 1;
        return c;
    }
}
