package com.dji.sdk.sample.demo.mobileremotecontroller;

import android.app.Service;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.utils.OnScreenJoystick;
import com.dji.sdk.sample.internal.utils.OnScreenJoystickListener;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.PresentableView;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;

import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks.CompletionCallback;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mobilerc.MobileRemoteController;
import dji.sdk.products.Aircraft;

/**
 * Class for mobile remote controller.
 */
public class MobileRemoteControllerView extends RelativeLayout
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, PresentableView {

    private Button btnTakeOff;
    private Button autoLand;
    private Button forceLand;

    private TextView textView;

    private OnScreenJoystick screenJoystickRight;
    private OnScreenJoystick screenJoystickLeft;
    private MobileRemoteController mobileRemoteController;
    private FlightController flightController;

    public MobileRemoteControllerView(Context context) {
        super(context);
        init(context);
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setUpListeners();
    }

    @Override
    protected void onDetachedFromWindow() {
        tearDownListeners();
        super.onDetachedFromWindow();
    }

    private void init(Context context) {
        setClickable(true);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_mobile_rc, this, true);
        initFlightController();
        initUI();
    }

    private void initFlightController() {
        Aircraft aircraft = DJISampleApplication.getAircraftInstance();
        if (aircraft != null) {
            flightController = aircraft.getFlightController();
            mobileRemoteController = aircraft.getMobileRemoteController();
        }
    }

    private void initUI() {
        btnTakeOff = (Button) findViewById(R.id.btn_take_off);
        autoLand = (Button) findViewById(R.id.btn_auto_land);
        forceLand = (Button) findViewById(R.id.btn_force_land);
        textView = (TextView) findViewById(R.id.textview_simulator);

        screenJoystickRight = (OnScreenJoystick) findViewById(R.id.directionJoystickRight);
        screenJoystickLeft = (OnScreenJoystick) findViewById(R.id.directionJoystickLeft);

        btnTakeOff.setOnClickListener(this);
        autoLand.setOnClickListener(this);
        forceLand.setOnClickListener(this);

        if (mobileRemoteController != null) {
            textView.setText(textView.getText() + "\n" + "Mobile Connected");
        } else {
            textView.setText(textView.getText() + "\n" + "Mobile Disconnected");
        }
    }

    private void setUpListeners() {
        screenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {
            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }
                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }
                if (mobileRemoteController != null) {
                    mobileRemoteController.setLeftStickHorizontal(pX);
                    mobileRemoteController.setLeftStickVertical(pY);
                }
            }
        });

        screenJoystickRight.setJoystickListener(new OnScreenJoystickListener() {
            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }
                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }
                if (mobileRemoteController != null) {
                    mobileRemoteController.setRightStickHorizontal(pX);
                    mobileRemoteController.setRightStickVertical(pY);
                }
            }
        });
    }

    private void tearDownListeners() {
        screenJoystickLeft.setJoystickListener(null);
        screenJoystickRight.setJoystickListener(null);
    }

    @Override
    public void onClick(View v) {
        if (flightController == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.btn_take_off:
                flightController.startTakeoff(new CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            showToast("Takeoff successful");
                        } else {
                            showToast("Takeoff failed: " + djiError.getDescription());
                        }
                    }
                });
                break;
            case R.id.btn_force_land:
                flightController.confirmLanding(new CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            showToast("Force landing successful");
                        } else {
                            showToast("Force landing failed: " + djiError.getDescription());
                        }
                    }
                });
                break;
            case R.id.btn_auto_land:
                flightController.startLanding(new CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            showToast("Auto landing successful");
                        } else {
                            showToast("Auto landing failed: " + djiError.getDescription());
                        }
                    }
                });
                break;
            default:
                break;
        }
    }

    private void showToast(String message) {
        ToastUtils.setResultToToast(message);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        // シミュレーター機能は削除しました
    }

    @Override
    public int getDescription() {
        return R.string.component_listview_mobile_remote_controller;
    }
}
