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
import android.widget.TextView;
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
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.sdk.base.BaseProduct;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class VirtualStickView extends LinearLayout implements PresentableView, TextureView.SurfaceTextureListener {
    private TextureView videoSurface;
    private ImageView bitmapImage;
    private OverlayView overlayView;
    private TextView distanceTextView;
    private TextView angleTextView;
    private TextView movementTextView; // 移動量を表示するTextView
    private DJICodecManager codecManager;
    private VideoFeeder.VideoDataListener videoDataListener;
    private Button buttonForward;
    private Button buttonEnableVirtualStick;
    private Button buttonTakeoffLand;
    private FlightController flightController;
    private Gimbal gimbal;
    private static final int DETECTION_INTERVAL = 5;
    private int frameCount = 0;

    private static final String TAG = "VirtualStickView";

    private ObjectDetectorHelper objectDetectorHelper;
    private boolean isVirtualStickEnabled = false;

    public VirtualStickView(Context context) {
        super(context);
        init(context);
        initGimbal();
    }

    private void initGimbal() {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null && product instanceof Aircraft) {
            Aircraft aircraft = (Aircraft) product;
            if (aircraft.getGimbal() != null) {
                gimbal = aircraft.getGimbal();
                Log.d(TAG, "ジンバルが正常に初期化されました。");
            } else {
                Log.e(TAG, "ジンバルが利用できません。");
            }
        } else {
            Log.e(TAG, "製品が航空機ではないか、nullです。");
        }
    }

    private void init(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_virtual_stick, this, true);

        videoSurface = findViewById(R.id.video_surface);
        bitmapImage = findViewById(R.id.bitmap_image);
        overlayView = findViewById(R.id.overlay_view);
        distanceTextView = findViewById(R.id.distance_text_view);
        angleTextView = findViewById(R.id.angle_text_view);
        movementTextView = findViewById(R.id.movement_text_view); // 移動量表示用のTextViewを初期化

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
                    Log.e(TAG, "codecManagerがnullです。ビデオデータをデコードできません。");
                }
            } catch (Exception e) {
                handleError(e);
            }
        };
        VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable: DJICodecManagerを初期化しています。");
        codecManager = new DJICodecManager(getContext(), surface, width, height);
        if (codecManager == null) {
            Log.e(TAG, "DJICodecManagerの初期化に失敗しました。");
        } else {
            Log.d(TAG, "DJICodecManagerが正常に初期化されました。");
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
        frameCount++;
        if (frameCount % DETECTION_INTERVAL == 0) {
            Bitmap bitmap = videoSurface.getBitmap();
            if (bitmap != null) {
                objectDetectorHelper.detect(bitmap, this::onDetectionResults);
            } else {
                Log.e(TAG, "onSurfaceTextureUpdatedでgetBitmapがnullを返しました。");
            }
        }
    }

    private void adjustGimbalPitchAndYaw(float deltaX, float deltaY) {
        if (gimbal == null) {
            Log.e(TAG, "ジンバルが初期化されていません。");
            return;
        }

        float threshold = 20.0f;

        if (Math.abs(deltaX) > threshold || Math.abs(deltaY) > threshold) {
            float yawAdjustment = 0.0f;
            float pitchAdjustment = 0.0f;

            if (Math.abs(deltaX) > threshold) {
                yawAdjustment = deltaX > 0 ? 10.0f : -10.0f;
            }

            if (Math.abs(deltaY) > threshold) {
                pitchAdjustment = deltaY > 0 ? -10.0f : 10.0f;
            }

            Rotation.Builder rotationBuilder = new Rotation.Builder()
                    .mode(RotationMode.SPEED)
                    .pitch(pitchAdjustment)
                    .yaw(yawAdjustment)
                    .roll(0);

            gimbal.rotate(rotationBuilder.build(), djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "ジンバル調整エラー: " + djiError.getDescription());
                } else {
                    Log.d(TAG, "物体を中心にするためにジンバルが正常に調整されました。");
                }
            });
        }
    }


    private void adjustGimbalToCenterObject(Detection detection) {
        int cameraCenterX = videoSurface.getWidth() / 2;
        int cameraCenterY = videoSurface.getHeight() / 2;

        RectF boundingBox = detection.getBoundingBox();
        float objectCenterX = boundingBox.centerX();
        float objectCenterY = boundingBox.centerY();

        float deltaX = objectCenterX - cameraCenterX;
        float deltaY = objectCenterY - cameraCenterY;

        Log.d(TAG, "DeltaX: " + deltaX + ", DeltaY: " + deltaY);

        // 差に基づいてジンバルを調整
        adjustGimbalPitchAndYaw(deltaX, deltaY);

        // ジンバルの向きに合わせて機体を回転させる
        float yawAdjustment = calculateYawAdjustment(deltaX);
        rotateAircraft(yawAdjustment);
    }

    private void rotateAircraft(float yawAdjustment) {
        if (flightController != null) {
            FlightControlData flightControlData = new FlightControlData(0, 0, yawAdjustment, 0);
            flightController.sendVirtualStickFlightControlData(flightControlData, djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "機体回転エラー: " + djiError.getDescription());
                } else {
                    Log.d(TAG, "機体が正常に回転しました。");
                }
            });
        }
    }


    private void onDetectionResults(List<Detection> results) {
        if (results != null) {
            List<Detection> personDetections = new ArrayList<>();
            for (Detection detection : results) {
                String label = detection.getCategories().get(0).getLabel();
                if ("person".equals(label)) {
                    personDetections.add(detection);
                }
            }

            if (!personDetections.isEmpty()) {
                Log.d(TAG, "人が検出されました。オーバーレイを更新します。");
                overlayView.setResults(personDetections);

                Detection firstPerson = personDetections.get(0);
                adjustGimbalToCenterObject(firstPerson);

                // 距離と角度の計算
                float distance = calculateDistanceToBoundingBox(firstPerson.getBoundingBox());
                float[] angles = calculateAngleToBoundingBox(firstPerson.getBoundingBox());

                // 移動量と方向の計算
                String[] movementDirections = calculateMovementDirections(angles, distance);
                float[] movements = calculateMovement(angles, distance);

                // 距離、角度、移動方向と量を表示
                displayDistance(distance);
                displayAngle(angles);
                displayMovement(movementDirections, movements);

                // ドローンの動きを調整
                adjustDroneMovement(angles, distance);
            } else {
                Log.d(TAG, "人が検出されませんでした。");
                displayDistance(-1);
                displayAngle(new float[]{Float.NaN, Float.NaN});
                displayMovement(new String[]{"", ""}, new float[]{0.0f, 0.0f});
            }
        } else {
            Log.e(TAG, "onDetectionResults: 検出結果がnullでした。");
            displayDistance(-1);
            displayAngle(new float[]{Float.NaN, Float.NaN});
            displayMovement(new String[]{"", ""}, new float[]{0.0f, 0.0f});
        }
    }

    private float calculateDistanceToBoundingBox(RectF boundingBox) {
        float focalLength = 0.004f;
        float sensorWidth = 0.00617f;
        float realObjectWidth = 0.5f;

        float boundingBoxWidthPx = boundingBox.width();
        int imageWidthPx = videoSurface.getWidth();

        float sensorWidthInMeters = boundingBoxWidthPx / imageWidthPx * sensorWidth;

        return (focalLength * realObjectWidth) / sensorWidthInMeters;
    }

    private float[] calculateAngleToBoundingBox(RectF boundingBox) {
        int imageCenterX = videoSurface.getWidth() / 2;
        int imageCenterY = videoSurface.getHeight() / 2;

        float boxCenterX = boundingBox.centerX();
        float boxCenterY = boundingBox.centerY();

        float horizontalFOV = 78.8f;
        float verticalFOV = 63.4f;

        float anglePerPixelX = horizontalFOV / videoSurface.getWidth();
        float anglePerPixelY = verticalFOV / videoSurface.getHeight();

        float deltaX = boxCenterX - imageCenterX;
        float deltaY = boxCenterY - imageCenterY;

        float angleX = deltaX * anglePerPixelX;
        float angleY = deltaY * anglePerPixelY;

        return new float[]{angleX, angleY};
    }

    private String[] calculateMovementDirections(float[] angles, float distance) {
        if (Float.isNaN(angles[0]) || Float.isNaN(angles[1]) || distance < 0) {
            return new String[]{"", ""};
        }

        String horizontalDirection = angles[0] > 0 ? "右" : "左";
        String verticalDirection = angles[1] > 0 ? "下" : "上";

        return new String[]{horizontalDirection, verticalDirection};
    }

    private float[] calculateMovement(float[] angles, float distance) {
        if (Float.isNaN(angles[0]) || Float.isNaN(angles[1]) || distance < 0) {
            return new float[]{0.0f, 0.0f};
        }

        float horizontalMovement = (float) (distance * Math.tan(Math.toRadians(angles[0])));
        float verticalMovement = (float) (distance * Math.tan(Math.toRadians(angles[1])));

        return new float[]{horizontalMovement, verticalMovement};
    }

    private void adjustDroneMovement(float[] angles, float distance) {
        float yawAdjustment = calculateYawAdjustment(angles[0]);
        float rollAdjustment = calculateRollAdjustment(angles[0], distance);
        float pitchAdjustment = calculatePitchAdjustment(angles[1], distance);
        float throttleAdjustment = calculateThrottleAdjustment(angles[1]);

        if (yawAdjustment != 0.0f) {
            Rotation.Builder rotationBuilder = new Rotation.Builder()
                    .mode(RotationMode.SPEED)
                    .yaw(yawAdjustment)
                    .pitch(0)
                    .roll(0);
            gimbal.rotate(rotationBuilder.build(), djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "Yaw調整エラー: " + djiError.getDescription());
                } else {
                    Log.d(TAG, "Yawが正常に調整されました。");
                }
            });
        }

        if (flightController != null) {
            flightController.sendVirtualStickFlightControlData(
                    new FlightControlData(pitchAdjustment, rollAdjustment, yawAdjustment, throttleAdjustment), djiError -> {
                        if (djiError != null) {
                            Log.e(TAG, "フライトコントロールデータエラー: " + djiError.getDescription());
                        } else {
                            Log.d(TAG, "ドローンの動きが正常に調整されました。");
                        }
                    });
        }
    }
    private float calculateYawAdjustment(float angleX) {
        float yawThreshold = 1.0f;

        if (Math.abs(angleX) > yawThreshold) {
            return angleX;
        } else {
            return 0.0f;
        }
    }

    private float calculateRollAdjustment(float angleX, float distance) {
        return (float) (distance * Math.tan(Math.toRadians(angleX)));
    }

    private float calculatePitchAdjustment(float angleY, float distance) {
        return (float) (distance * Math.tan(Math.toRadians(angleY)));
    }

    private float calculateThrottleAdjustment(float angleY) {
        float throttleThreshold = 1.0f;

        if (Math.abs(angleY) > throttleThreshold) {
            return -angleY;
        } else {
            return 0.0f;
        }
    }

    private void displayDistance(float distance) {
        if (distance >= 0) {
            distanceTextView.setText(String.format("距離: %.2f m", distance));
        } else {
            distanceTextView.setText("距離: N/A");
        }
    }

    private void displayAngle(float[] angles) {
        if (!Float.isNaN(angles[0]) && !Float.isNaN(angles[1])) {
            angleTextView.setText(String.format("角度 X: %.2f°, 角度 Y: %.2f°", angles[0], angles[1]));
        } else {
            angleTextView.setText("角度: N/A");
        }
    }

    private void displayMovement(String[] movements, float[] distances) {
        if (movements != null && distances != null) {
            movementTextView.setText(String.format("%sに %.2f m, %sに %.2f m 移動",
                    movements[0], distances[0],
                    movements[1], distances[1]));
        } else {
            movementTextView.setText("移動: N/A");
        }
    }

    private void handleError(Exception e) {
        Log.e(TAG, "エラーが発生しました: " + e.getMessage(), e);
        new Handler(Looper.getMainLooper()).post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("エラー")
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
                    Log.d(TAG, "フライトコントローラーが正常に初期化されました。");
                }
            }
        }
    }

    private void toggleVirtualStickMode() {
        if (flightController != null) {
            flightController.setVirtualStickModeEnabled(!isVirtualStickEnabled, djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "バーチャルスティックモードの切り替えエラー: " + djiError.getDescription());
                } else {
                    isVirtualStickEnabled = !isVirtualStickEnabled;
                    Log.d(TAG, "バーチャルスティックモードが " + (isVirtualStickEnabled ? "有効" : "無効") + " になりました。");
                    Toast.makeText(getContext(), "バーチャルスティックモードが " + (isVirtualStickEnabled ? "有効" : "無効") + " になりました", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void takeoffOrLand() {
        if (flightController != null) {
            flightController.startTakeoff(djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "離陸開始エラー: " + djiError.getDescription());
                } else {
                    Log.d(TAG, "離陸が正常に開始されました。");
                    Toast.makeText(getContext(), "離陸が開始されました", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.e(TAG, "フライトコントローラーがnullです。離陸を開始できません。");
        }
    }

    private void moveDroneForward() {
        if (!isVirtualStickEnabled) {
            Log.e(TAG, "バーチャルスティックモードが有効ではありません。ドローンを前進させることができません。");
            Toast.makeText(getContext(), "まずバーチャルスティックモードを有効にしてください", Toast.LENGTH_SHORT).show();
            return;
        }

        float pitch = 15.0f;
        float roll = 0.0f;
        float yaw = 0.0f;
        float throttle = 0.0f;

        Runnable sendCommand = () -> {
            if (flightController != null) {
                flightController.sendVirtualStickFlightControlData(
                        new FlightControlData(pitch, roll, yaw, throttle), djiError -> {
                            if (djiError != null) {
                                Log.e(TAG, "仮想スティックデータ送信エラー: " + djiError.getDescription());
                            } else {
                                Log.d(TAG, "ドローンが前進しています。");
                                new Handler(Looper.getMainLooper()).post(() ->
                                        Toast.makeText(getContext(), "ドローンが前進しています", Toast.LENGTH_SHORT).show());
                            }
                        });
            }
        };

        new Handler().postDelayed(() -> {
            if (flightController != null) {
                flightController.sendVirtualStickFlightControlData(
                        new FlightControlData(0, roll, yaw, throttle), djiError -> {
                            if (djiError != null) {
                                Log.e(TAG, "ドローン停止エラー: " + djiError.getDescription());
                            } else {
                                Log.d(TAG, "ドローンが正常に停止しました。");
                                new Handler(Looper.getMainLooper()).post(() ->
                                        Toast.makeText(getContext(), "ドローンが停止しました", Toast.LENGTH_SHORT).show());
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
        return "バーチャルスティックビュー";
    }
}