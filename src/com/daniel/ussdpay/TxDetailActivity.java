package com.daniel.ussdpay;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * Read-only detail view for one stored transaction.
 *
 * Tapping a row in HistoryActivity used to jump straight to the share sheet.
 * This shows what was recorded first - payee, VPA, amount, UTR, bank and time -
 * with Close and Share at the bottom.
 */
public class TxDetailActivity extends Activity {

    public static final String EXTRA_ID = "id";

    private String receipt = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tx_detail);

        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setBackgroundDrawable(new ColorDrawable(0xFF0D47A1));
        }
        setTitle("Transaction details");

        long id = getIntent().getLongExtra(EXTRA_ID, -1L);
        Cursor c = new TxDb(this).byId(id);
        try {
            if (!c.moveToFirst()) {
                finish();
                return;
            }
            long ts = c.getLong(c.getColumnIndexOrThrow("ts"));
            String bank = c.getString(c.getColumnIndexOrThrow("bank"));
            String vpa = c.getString(c.getColumnIndexOrThrow("vpa"));
            String payee = c.getString(c.getColumnIndexOrThrow("payee"));
            String amount = c.getString(c.getColumnIndexOrThrow("amount"));
            String note = c.getString(c.getColumnIndexOrThrow("note"));
            String ref = c.getString(c.getColumnIndexOrThrow("ref"));

            String shown = amount == null || amount.trim().isEmpty() ? "?" : amount.trim();
            ((TextView) findViewById(R.id.tvAmount)).setText("\u20B9" + shown);

            TextView tvPayee = findViewById(R.id.tvPayee);
            if (isBlank(payee)) {
                tvPayee.setVisibility(View.GONE);
            } else {
                tvPayee.setText(payee.trim());
            }

            TextView tvVpa = findViewById(R.id.tvVpa);
            if (isBlank(vpa)) {
                tvVpa.setVisibility(View.GONE);
            } else {
                tvVpa.setText(vpa.trim());
            }

            bind(R.id.rowBank, R.id.tvBank, bank);
            bind(R.id.rowRef, R.id.tvRef, ref);
            bind(R.id.rowWhen, R.id.tvWhen, TxFormat.when(ts));
            bind(R.id.rowNote, R.id.tvNote, note);

            receipt = TxFormat.receipt(ts, bank, vpa, payee, amount, ref);
        } finally {
            c.close();
        }

        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnShare).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                share();
            }
        });
    }

    /** Shows a label/value row, or hides it when the field was not recorded. */
    private void bind(int rowId, int valueId, String value) {
        View row = findViewById(rowId);
        if (isBlank(value)) {
            row.setVisibility(View.GONE);
            return;
        }
        row.setVisibility(View.VISIBLE);
        ((TextView) findViewById(valueId)).setText(value.trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void share() {
        if (receipt.isEmpty()) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, receipt);
        startActivity(Intent.createChooser(i, "Share receipt"));
    }
}
