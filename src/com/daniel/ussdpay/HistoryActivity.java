package com.daniel.ussdpay;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/** Transaction history for the last 30 days; tap a row to share the receipt. */
public class HistoryActivity extends Activity {

    private final List<String> rows = new ArrayList<>();
    private final List<String> receipts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ListView list = findViewById(R.id.list);
        load();

        list.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, rows));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                share(receipts.get(position));
            }
        });
    }

    private void load() {
        rows.clear();
        receipts.clear();
        Cursor c = new TxDb(this).recent();
        try {
            while (c.moveToNext()) {
                long ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                String amount = c.getString(c.getColumnIndexOrThrow("amount"));
                String vpa = c.getString(c.getColumnIndexOrThrow("vpa"));
                String ref = c.getString(c.getColumnIndexOrThrow("ref"));
                String bank = c.getString(c.getColumnIndexOrThrow("bank"));
                String payee = c.getString(c.getColumnIndexOrThrow("payee"));
                rows.add(TxFormat.row(ts, amount, vpa, ref));
                receipts.add(TxFormat.receipt(ts, bank, vpa, payee, amount, ref));
            }
        } finally {
            c.close();
        }
        if (rows.isEmpty()) {
            rows.add("No transactions in the last 30 days");
            receipts.add("");
        }
    }

    private void share(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Share receipt"));
    }
}
