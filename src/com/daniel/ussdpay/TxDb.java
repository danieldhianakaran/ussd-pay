package com.daniel.ussdpay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Local transaction store.
 *
 * Bounded on purpose: only the last 30 days are kept, and never more than
 * MAX_ROWS. Older rows are dropped on every insert and on open, so the phone
 * never accumulates a permanent payment history - the bank statement is the
 * authoritative record.
 */
public class TxDb extends SQLiteOpenHelper {

    private static final String NAME = "ussdpay.db";
    private static final int VERSION = 1;
    public static final long THIRTY_DAYS_MS = 30L * 24 * 3600 * 1000;
    public static final int MAX_ROWS = 500;

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

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        prune(db);
    }

    /** Drops anything older than 30 days, then keeps only the newest MAX_ROWS. */
    private void prune(SQLiteDatabase db) {
        long cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS;
        db.delete("tx", "ts<?", new String[]{String.valueOf(cutoff)});
        db.execSQL("DELETE FROM tx WHERE _id NOT IN "
                + "(SELECT _id FROM tx ORDER BY ts DESC LIMIT " + MAX_ROWS + ")");
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
        SQLiteDatabase db = getWritableDatabase();
        long id = db.insert("tx", null, v);
        prune(db);
        return id;
    }

    /** Everything currently retained, newest first. */
    public Cursor recent() {
        return getReadableDatabase().query("tx", null, null, null,
                null, null, "ts DESC");
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
