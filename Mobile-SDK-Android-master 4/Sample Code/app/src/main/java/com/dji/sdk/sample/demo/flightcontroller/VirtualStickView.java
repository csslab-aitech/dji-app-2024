package com.dji.sdk.sample.demo.flightcontroller;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.MainActivity;
import com.dji.sdk.sample.internal.view.PresentableView;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;

import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;


public class VirtualStickView extends LinearLayout implements PresentableView, TextureView.SurfaceTextureListener {
    private TextureView videoSurface;
    private ImageView bitmapImage;
    private OverlayView overlayView;
    private DJICodecManager codecManager;
    private VideoFeeder.VideoDataListener videoDataListener;
    private Button buttonForward;
    private Button buttonEnableVirtualStick;
    private Button buttonTakeoffLand;
    private FlightController flightController;

    private static final String TAG = "VirtualStickView";

    private ObjectDetectorHelper objectDetectorHelper;
    private boolean isVirtualStickEnabled = false;

    public VirtualStickView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_virtual_stick, this, true);

        videoSurface = findViewById(R.id.video_surface);
        bitmapImage = findViewById(R.id.bitmap_image);
        overlayView = findViewById(R.id.overlay_view);

        buttonForward = findViewById(R.id.button_forward);
        buttonEnableVirtualStick = findViewById(R.id.button_enable_virtual_stick);
        buttonTakeoffLand = findViewById(R.id.button_takeoff_land);

        videoSurface.setSurfaceTextureListener(this);

        try {
            objectDetectorHelper = new ObjectDetectorHelper(context, this::onDetectionResults);
        } catch (Exception e) {
            handleError(e);
        }

        initCamera();
        buttonForward.setOnClickListener(v -> moveDroneForward());
        buttonEnableVirtualStick.setOnClickListener(v -> toggleVirtualStickMode());
        buttonTakeoffLand.setOnClickListener(v -> takeoffOrLand());
        initFlightController();
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
            // 人だけをフィルタリング
            List<Detection> personDetections = new ArrayList<>();
            for (Detection detection : results) {
                String label = detection.getCategories().get(0).getLabel();
                if ("person".equals(label)) {  // "person" ラベルを持つ検出結果をフィルタリング
                    personDetections.add(detection);
                }
            }

            if (!personDetections.isEmpty()) {
                Log.d(TAG, "Person detection results received. Updating overlay.");
                overlayView.setResults(personDetections);
            } else {
                Log.d(TAG, "No person detected.");
            }
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

    private void initFlightController() {
        if (DJISDKManager.getInstance().getProduct() != null) {
            if (DJISDKManager.getInstance().getProduct() instanceof Aircraft) {
                flightController = ((Aircraft) DJISDKManager.getInstance().getProduct()).getFlightController();
                if (flightController != null) {
                    Log.d(TAG, "FlightController initialized successfully.");
                }
            }
        }
    }

    private void toggleVirtualStickMode() {
        if (flightController != null) {
            flightController.setVirtualStickModeEnabled(!isVirtualStickEnabled, djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "Error toggling virtual stick mode: " + djiError.getDescription());
                } else {
                    isVirtualStickEnabled = !isVirtualStickEnabled;
                    Log.d(TAG, "Virtual stick mode " + (isVirtualStickEnabled ? "enabled" : "disabled") + " successfully.");
                    Toast.makeText(getContext(), "Virtual Stick Mode " + (isVirtualStickEnabled ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void takeoffOrLand() {
        if (flightController != null) {
            flightController.startTakeoff(djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "Error starting takeoff: " + djiError.getDescription());
                } else {
                    Log.d(TAG, "Takeoff started successfully.");
                    Toast.makeText(getContext(), "Takeoff started", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.e(TAG, "FlightController is null. Cannot start takeoff.");
        }
    }

    private void moveDroneForward() {
        if (!isVirtualStickEnabled) {
            Log.e(TAG, "Virtual Stick Mode is not enabled. Cannot move drone forward.");
            Toast.makeText(getContext(), "Enable Virtual Stick Mode first", Toast.LENGTH_SHORT).show();
            return;
        }

        float pitch = 15.0f; // 前進するためのピッチ
        float roll = 0.0f;
        float yaw = 0.0f;
        float throttle = 0.0f;

        Runnable sendCommand = () -> {
            if (flightController != null) {
                flightController.sendVirtualStickFlightControlData(
                        new FlightControlData(pitch, roll, yaw, throttle), djiError -> {
                            if (djiError != null) {
                                Log.e(TAG, "Error sending virtual stick data: " + djiError.getDescription());
                            } else {
                                Log.d(TAG, "Drone moving forward successfully.");
                                new Handler(Looper.getMainLooper()).post(() ->
                                        Toast.makeText(getContext(), "Drone is moving forward", Toast.LENGTH_SHORT).show());
                            }
                        });
            }
        };

        new Handler().postDelayed(() -> {
            if (flightController != null) {
                flightController.sendVirtualStickFlightControlData(
                        new FlightControlData(0, roll, yaw, throttle), djiError -> {
                            if (djiError != null) {
                                Log.e(TAG, "Error stopping drone: " + djiError.getDescription());
                            } else {
                                Log.d(TAG, "Drone stopped successfully.");
                                new Handler(Looper.getMainLooper()).post(() ->
                                        Toast.makeText(getContext(), "Drone has stopped", Toast.LENGTH_SHORT).show());
                            }
                        });
            }
        }, 2000);

        sendCommand.run();
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
    private RectF detectBoundingBox() {
        List<Detection> latestDetections = overlayView.getResults();
        if (latestDetections != null && !latestDetections.isEmpty()) {
            Detection firstDetection = latestDetections.get(0);  // 最初の検出結果を取得
            return firstDetection.getBoundingBox();  // バウンディングボックスを返す
        }
        return null;  // 検出されなかった場合
    }

}


//テスト