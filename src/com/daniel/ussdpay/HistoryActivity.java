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

/** Transaction history for the last 30 days; tap a row to view its receipt. */
public class HistoryActivity extends Activity {

    private final List<String> rows = new ArrayList<>();
    private final List<Long> ids = new ArrayList<>();

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
                long txId = ids.get(position);
                if (txId < 0) {
                    return;   // "no transactions" placeholder
                }
                startActivity(new Intent(HistoryActivity.this, TxDetailActivity.class)
                        .putExtra(TxDetailActivity.EXTRA_ID, txId));
            }
        });
    }

    private void load() {
        rows.clear();
        ids.clear();
        Cursor c = new TxDb(this).recent();
        try {
            while (c.moveToNext()) {
                long txId = c.getLong(c.getColumnIndexOrThrow("_id"));
                long ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                String amount = c.getString(c.getColumnIndexOrThrow("amount"));
                String vpa = c.getString(c.getColumnIndexOrThrow("vpa"));
                String ref = c.getString(c.getColumnIndexOrThrow("ref"));
                rows.add(TxFormat.row(ts, amount, vpa, ref));
                ids.add(txId);
            }
        } finally {
            c.close();
        }
        if (rows.isEmpty()) {
            rows.add("No transactions in the last 30 days");
            ids.add(-1L);
        }
    }
}
