package com.daniel.ussdpay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 1;
    private static final int REQ_PHONE = 2;
    private static final int REQ_SCAN = 10;

    private TextView status;
    private TextView transcript;
    private TextView tvAutofillState;
    private EditText etVpa, etAmount, etNote, etReply;
    private SharedPreferences prefs;

    private TelephonyManager tm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder log = new StringBuilder();
    private boolean busy;          // a USSD request is in flight
    private Runnable busyTimeout;  // clears the lock if the network never answers

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        transcript = findViewById(R.id.tvTranscript);
        tvAutofillState = findViewById(R.id.tvAutofillState);
        etVpa = findViewById(R.id.etVpa);
        etAmount = findViewById(R.id.etAmount);
        etNote = findViewById(R.id.etNote);
        etReply = findViewById(R.id.etReply);

        prefs = getSharedPreferences("ussdpay", MODE_PRIVATE);

        Button autofill = findViewById(R.id.btnAutofill);
        autofill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                Toast.makeText(MainActivity.this,
                        "Enable \"USSD Pay\" under Accessibility", Toast.LENGTH_LONG).show();
            }
        });

        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (tm == null) {
            status.setText("No telephony service on this device");
        }

        Button scan = findViewById(R.id.btnScan);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    return;
                }
                startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), REQ_SCAN);
            }
        });

        Button start = findViewById(R.id.btnStart);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String vpa = etVpa.getText().toString().trim();
                String amt = etAmount.getText().toString().trim();
                String msg = "Start a *99# USSD session?";
                if (!vpa.isEmpty()) {
                    msg = "Send " + (amt.isEmpty() ? "the amount" : "Rs." + amt)
                            + " to " + vpa + " via *99#?";
                }
                confirmAndSend("*99#", msg);
            }
        });

        Button send = findViewById(R.id.btnSend);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String reply = etReply.getText().toString().trim();
                if (TextUtils.isEmpty(reply)) {
                    return;
                }
                confirmAndSend(reply, "Send \"" + reply + "\" to the USSD session?");
            }
        });

        Button clear = findViewById(R.id.btnClear);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.setLength(0);
                transcript.setText("No session yet.");
            }
        });

        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_PHONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCAN && resultCode == RESULT_OK && data != null) {
            String text = data.getStringExtra("qr");
            UpiParser.Upi u = UpiParser.parse(text);
            if (u.valid) {
                etVpa.setText(u.vpa);
                if (!TextUtils.isEmpty(u.amount)) {
                    etAmount.setText(u.amount);
                }
                if (!TextUtils.isEmpty(u.note)) {
                    etNote.setText(u.note);
                }
                status.setText("Scanned: " + u.vpa
                        + (TextUtils.isEmpty(u.name) ? "" : " (" + u.name + ")"));
            } else {
                status.setText("Scanned code is not a UPI payment QR");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PHONE && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            status.setText("Ready");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAutofillState();
    }

    private boolean autofillEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        String me = getPackageName() + "/" + AutoPilotService.class.getName();
        return enabled.toLowerCase().contains(me.toLowerCase());
    }

    private void updateAutofillState() {
        boolean on = autofillEnabled();
        tvAutofillState.setText(on
                ? "Autofill: ON - fills UPI ID and amount, never the PIN"
                : "Autofill: off - tap the button above to enable it");
    }

    private void confirmAndSend(final String code, String message) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm USSD")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        sendUssd(code);
                    }
                })
                .show();
    }

    private void sendUssd(final String code) {
        if (busy) {
            status.setText("Still waiting for the previous USSD reply");
            return;
        }
        if (tm == null) {
            status.setText("No telephony service");
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_PHONE);
            return;
        }
        append(">> " + code);
        status.setText("Sending " + code + " ...");
        lockFor(90);

        if ("*99#".equals(code)) {
            prefs.edit()
                    .putString("vpa", etVpa.getText().toString().trim())
                    .putString("amount", etAmount.getText().toString().trim())
                    .putString("note", etNote.getText().toString().trim())
                    .putBoolean("armed", autofillEnabled())
                    .apply();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                tm.sendUssdRequest(code, new TelephonyManager.UssdResponseCallback() {
                    @Override
                    public void onReceiveUssdResponse(TelephonyManager telephonyManager,
                                                      String request,
                                                      CharSequence response) {
                        append("<< " + (response == null ? "" : response.toString()));
                        status.setText("Reply with a menu number");
                        unlock();
                    }

                    @Override
                    public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager,
                                                            String request,
                                                            int failureCode) {
                        append("<< FAILED, code " + failureCode);
                        status.setText("USSD failed (code " + failureCode + ")");
                        unlock();
                    }
                }, handler);
            } catch (Exception e) {
                append("<< ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
                status.setText("USSD error");
                unlock();
            }
        } else {
            status.setText("This Android version cannot send USSD programmatically");
            unlock();
        }
    }

    /** Blocks a second send until the network answers (or the timeout fires). */
    private void lockFor(int seconds) {
        busy = true;
        if (busyTimeout != null) {
            handler.removeCallbacks(busyTimeout);
        }
        busyTimeout = new Runnable() {
            @Override
            public void run() {
                if (busy) {
                    busy = false;
                    status.setText("No response from network");
                }
            }
        };
        handler.postDelayed(busyTimeout, seconds * 1000L);
    }

    private void unlock() {
        busy = false;
        if (busyTimeout != null) {
            handler.removeCallbacks(busyTimeout);
            busyTimeout = null;
        }
    }

    private void append(String line) {
        log.append(line).append('\n');
        transcript.setText(log.toString());
        int scroll = transcript.getLayout() == null ? 0 : transcript.getLayout().getLineTop(transcript.getLineCount()) - transcript.getHeight();
        if (scroll > 0) {
            transcript.scrollTo(0, scroll);
        }
    }
}
