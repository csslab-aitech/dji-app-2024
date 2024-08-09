package com.dji.sdk.sample.demo.flightcontroller;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.utils.OnScreenJoystick;
import com.dji.sdk.sample.internal.utils.OnScreenJoystickListener;

import dji.common.error.DJIError;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class FlightControlActivity2 extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "FlightControlActivity2";

    private FlightController flightController;
    private DJICodecManager codecManager;
    private SurfaceView videoSurface;
    private SurfaceHolder surfaceHolder;
    private OnScreenJoystick joystickLeft;
    private OnScreenJoystick joystickRight;
    private TextView commandTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight_control);

        initFlightController();
        initUI();

        Button takeOffButton = findViewById(R.id.button_takeoff);
        takeOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeOff();
            }
        });

        Button landButton = findViewById(R.id.button_land);
        landButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                land();
            }
        });

        joystickLeft = findViewById(R.id.joystick_left);
        joystickRight = findViewById(R.id.joystick_right);
        commandTextView = findViewById(R.id.command_text_view);

        // ジョイスティックのリスナーを設定
        joystickLeft.setJoystickListener(new OnScreenJoystickListener() {
            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                handleJoystickLeft(pX, pY);
            }
        });

        joystickRight.setJoystickListener(new OnScreenJoystickListener() {
            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                handleJoystickRight(pX, pY);
            }
        });
    }

    private void initFlightController() {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                flightController = ((Aircraft) product).getFlightController();
            } else {
                showToast("Product is not an Aircraft.");
            }
        } else {
            showToast("Product is not connected.");
        }
    }

    private void initUI() {
        videoSurface = findViewById(R.id.video_surface);
        surfaceHolder = videoSurface.getHolder();
        surfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (codecManager == null) {
            codecManager = new DJICodecManager(this, holder, holder.getSurfaceFrame().width(), holder.getSurfaceFrame().height());
        }
        VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Do nothing
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager = null;
        }
        VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(videoDataListener);
    }

    private VideoFeeder.VideoDataListener videoDataListener = new VideoFeeder.VideoDataListener() {
        @Override
        public void onReceive(byte[] videoBuffer, int size) {
            if (codecManager != null) {
                codecManager.sendDataToDecoder(videoBuffer, size);
            }
        }
    };

    private void takeOff() {
        if (flightController != null) {
            flightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        showToast("Takeoff successful");
                        updateCommandTextView("Takeoff successful");
                    } else {
                        showToast("Takeoff failed: " + djiError.getDescription());
                        updateCommandTextView("Takeoff failed: " + djiError.getDescription());
                    }
                }
            });
        }
    }

    private void land() {
        if (flightController != null) {
            flightController.startLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        showToast("Landing successful");
                        updateCommandTextView("Landing successful");
                    } else {
                        showToast("Landing failed: " + djiError.getDescription());
                        updateCommandTextView("Landing failed: " + djiError.getDescription());
                    }
                }
            });
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateCommandTextView(String message) {
        commandTextView.setText(message);
    }

    private void handleJoystickLeft(float pX, float pY) {
        if (flightController != null) {
            float pitch = pY * 10;  // JoystickのY値に基づいてピッチを設定
            float roll = pX * 10;   // JoystickのX値に基づいてロールを設定

            String action = "停止";
            if (pX > 0.02) {
                action = "右移動";
            } else if (pX < -0.02) {
                action = "左移動";
            }
            if (pY > 0.02) {
                action = "前進";
            } else if (pY < -0.02) {
                action = "後退";
            }
            Log.d(TAG, "左ジョイスティック: " + action);
            updateCommandTextView("左ジョイスティック: " + action);

            flightController.sendVirtualStickFlightControlData(new FlightControlData(roll, pitch, 0, 0), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast("Failed to send control data: " + djiError.getDescription());
                        updateCommandTextView("Failed to send control data: " + djiError.getDescription());
                    }
                }
            });
        }
    }

    private void handleJoystickRight(float pX, float pY) {
        if (flightController != null) {
            float yaw = pX * 30;       // JoystickのX値に基づいてヨーを設定
            float throttle = pY * 2;   // JoystickのY値に基づいてスロットルを設定

            String action = "停止";
            if (pX > 0.02) {
                action = "右回転";
            } else if (pX < -0.02) {
                action = "左回転";
            }
            if (pY > 0.02) {
                action = "上昇";
            } else if (pY < -0.02) {
                action = "下降";
            }
            Log.d(TAG, "右ジョイスティック: " + action);
            updateCommandTextView("右ジョイスティック: " + action);

            flightController.sendVirtualStickFlightControlData(new FlightControlData(0, 0, yaw, throttle), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast("Failed to send control data: " + djiError.getDescription());
                        updateCommandTextView("Failed to send control data: " + djiError.getDescription());
                    }
                }
            });
        }
    }
}
