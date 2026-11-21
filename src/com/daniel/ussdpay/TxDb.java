package com.daniel.ussdpay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Local transaction store, last 30 days. */
public class TxDb extends SQLiteOpenHelper {

    private static final String NAME = "ussdpay.db";
    private static final int VERSION = 1;
    public static final long THIRTY_DAYS_MS = 30L * 24 * 3600 * 1000;

    public TxDb(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tx ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "bank TEXT,"
                + "vpa TEXT,"
                + "payee TEXT,"
                + "amount TEXT,"
                + "note TEXT,"
                + "ref TEXT,"
                + "raw TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public long insert(long ts, String bank, String vpa, String payee,
                       String amount, String note, String ref, String raw) {
        ContentValues v = new ContentValues();
        v.put("ts", ts);
        v.put("bank", bank == null ? "" : bank);
        v.put("vpa", vpa == null ? "" : vpa);
        v.put("payee", payee == null ? "" : payee);
        v.put("amount", amount == null ? "" : amount);
        v.put("note", note == null ? "" : note);
        v.put("ref", ref == null ? "" : ref);
        v.put("raw", raw == null ? "" : raw);
        return getWritableDatabase().insert("tx", null, v);
    }

    /** Everything from the last 30 days, newest first. */
    public Cursor recent() {
        long since = System.currentTimeMillis() - THIRTY_DAYS_MS;
        return getReadableDatabase().query("tx", null, "ts>=?",
                new String[]{String.valueOf(since)}, null, null, "ts DESC");
    }

    public Cursor byId(long id) {
        return getReadableDatabase().query("tx", null, "_id=?",
                new String[]{String.valueOf(id)}, null, null, null);
    }

    /** Rows newer than the given id, oldest first. */
    public Cursor newerThan(long id) {
        return getReadableDatabase().query("tx", null, "_id>?",
                new String[]{String.valueOf(id)}, null, null, "_id ASC");
    }
}
