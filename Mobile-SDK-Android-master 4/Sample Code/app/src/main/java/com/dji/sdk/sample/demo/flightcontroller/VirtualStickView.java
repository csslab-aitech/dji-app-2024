package com.dji.sdk.sample.demo.flightcontroller;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.MainActivity;
import com.dji.sdk.sample.internal.view.PresentableView;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.List;

import dji.sdk.codec.DJICodecManager;
import dji.sdk.camera.VideoFeeder;

public class VirtualStickView extends LinearLayout implements PresentableView, TextureView.SurfaceTextureListener {
    private TextureView videoSurface;
    private ImageView bitmapImage;
    private OverlayView overlayView; // 追加: OverlayView の参照
    private DJICodecManager codecManager;
    private VideoFeeder.VideoDataListener videoDataListener;
    private static final String TAG = "VirtualStickView";

    private ObjectDetectorHelper objectDetectorHelper;

    public VirtualStickView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_virtual_stick, this, true);

        videoSurface = findViewById(R.id.video_surface);
        bitmapImage = findViewById(R.id.bitmap_image);
        overlayView = findViewById(R.id.overlay_view); // 追加: OverlayView の初期化

        videoSurface.setSurfaceTextureListener(this);

        try {
            objectDetectorHelper = new ObjectDetectorHelper(context, this::onDetectionResults);
        } catch (Exception e) {
            handleError(e);
        }

        initCamera();
    }

    private void initCamera() {
        videoDataListener = (videoBuffer, size) -> {
            try {
                if (codecManager != null) {
                    codecManager.sendDataToDecoder(videoBuffer, size);
                } else {
                    Log.e(TAG, "codecManager is null. Cannot decode video data.");
                }
            } catch (Exception e) {
                handleError(e);
            }
        };
        VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable: Initializing DJICodecManager.");
        codecManager = new DJICodecManager(getContext(), surface, width, height);
        if (codecManager == null) {
            Log.e(TAG, "Failed to initialize DJICodecManager.");
        } else {
            Log.d(TAG, "DJICodecManager initialized successfully.");
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // サイズが変更された場合の処理（必要に応じて）
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureUpdated: SurfaceTexture updated.");
        // SurfaceTextureが更新されたタイミングでBitmapをキャプチャ
        Bitmap bitmap = videoSurface.getBitmap();
        if (bitmap != null) {
            Log.d(TAG, "Captured bitmap from TextureView. Width: " + bitmap.getWidth() + ", Height: " + bitmap.getHeight());
            objectDetectorHelper.detect(bitmap, this::onDetectionResults);
        } else {
            Log.e(TAG, "getBitmap returned null in onSurfaceTextureUpdated.");
        }
    }

    private void onDetectionResults(List<Detection> results) {
        if (results != null) {
            Log.d(TAG, "Detection results received. Updating overlay.");
            overlayView.setResults(results); // OverlayView に検出結果を渡す
        } else {
            Log.e(TAG, "onDetectionResults: Received null detection results.");
        }
    }


    private void handleError(Exception e) {
        Log.e(TAG, "handleError: " + e.getMessage(), e);
        new Handler(Looper.getMainLooper()).post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Error")
                    .setMessage(e.getMessage())
                    .setPositiveButton("OK", (dialog, which) -> {
                        Context context = getContext();
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intent);
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    @Override
    public int getDescription() {
        return R.string.flight_controller_listview_virtual_stick;
    }

    @NonNull
    @Override
    public String getHint() {
        return "VirtualStickView";
    }
}
