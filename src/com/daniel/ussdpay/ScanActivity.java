package com.daniel.ussdpay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ScanActivity extends Activity
        implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView preview;
    private TextView hint;
    private Camera camera;
    private MultiFormatReader reader;
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        preview = findViewById(R.id.preview);
        hint = findViewById(R.id.hint);

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            hint.setText("Camera permission is required");
            finish();
            return;
        }

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,
                java.util.Collections.singletonList(BarcodeFormat.QR_CODE));
        reader = new MultiFormatReader();
        reader.setHints(hints);

        preview.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
        } catch (Exception e) {
            hint.setText("Cannot open camera: " + e.getMessage());
            return;
        }
        try {
            camera.setPreviewDisplay(holder);
            Camera.Parameters p = camera.getParameters();
            p.setPreviewFormat(ImageFormat.NV21);
            Camera.Size size = chooseSize(p.getSupportedPreviewSizes());
            if (size != null) {
                p.setPreviewSize(size.width, size.height);
                camera.setParameters(p);
            }
            camera.setDisplayOrientation(90);
            int bufferSize = size == null
                    ? 1280 * 720 * 3 / 2
                    : size.width * size.height * 3 / 2;
            camera.addCallbackBuffer(new byte[bufferSize]);
            camera.setPreviewCallbackWithBuffer(this);
            camera.startPreview();
        } catch (Exception e) {
            hint.setText("Camera setup failed: " + e.getMessage());
        }
    }

    private Camera.Size chooseSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Camera.Size best = sizes.get(0);
        long bestScore = Long.MAX_VALUE;
        for (Camera.Size s : sizes) {
            long pixels = (long) s.width * s.height;
            long score = Math.abs(pixels - 1280L * 720L);
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera cam) {
        if (done || data == null) {
            return;
        }
        try {
            Camera.Parameters p = cam.getParameters();
            Camera.Size size = p.getPreviewSize();
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    data, size.width, size.height, 0, 0, size.width, size.height, false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = reader.decodeWithState(bitmap);
            if (result != null) {
                done = true;
                Intent out = new Intent();
                out.putExtra("qr", result.getText());
                setResult(RESULT_OK, out);
                finish();
                return;
            }
        } catch (Exception ignored) {
            // no code in this frame
        }
        cam.addCallbackBuffer(data);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            try {
                camera.setPreviewCallbackWithBuffer(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {
            }
            camera = null;
        }
    }
}
