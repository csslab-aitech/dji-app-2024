package com.dji.sdk.sample.demo.flightcontroller;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.MainActivity;
import com.dji.sdk.sample.internal.view.PresentableView;

import dji.sdk.codec.DJICodecManager;
import dji.sdk.camera.VideoFeeder;

public class VirtualStickView extends LinearLayout implements PresentableView, SurfaceHolder.Callback {
    private SurfaceView videoSurface;
    private ImageView bitmapImage;
    private SurfaceHolder surfaceHolder;
    private DJICodecManager codecManager;
    private VideoFeeder.VideoDataListener videoDataListener;
    private static final String TAG = "VirtualStickView";

    private ObjectDetectorHelper objectDetectorHelper;

    public VirtualStickView(Context context) {
        super(context);
        init(context);
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    @Override
    public int getDescription() {
        return R.string.flight_controller_listview_virtual_stick;
    }

    private void init(Context context) {
        Log.d(TAG, "init: Starting initialization.");
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_virtual_stick, this, true);

        videoSurface = findViewById(R.id.video_surface);
        bitmapImage = findViewById(R.id.bitmap_image);

        surfaceHolder = videoSurface.getHolder();
        surfaceHolder.addCallback(this);

        try {
            objectDetectorHelper = new ObjectDetectorHelper(context, this::onDetectionResults);
            Log.d(TAG, "init: ObjectDetectorHelper initialized.");
        } catch (Exception e) {
            handleError(e);
        }

        initCamera();
    }

    private void initCamera() {
        Log.d(TAG, "initCamera: Starting camera initialization.");
        try {
            videoDataListener = (videoBuffer, size) -> {
                try {
                    Log.d(TAG, "videoDataListener: Received video data of size: " + size);
                    if (codecManager != null) {
                        codecManager.sendDataToDecoder(videoBuffer, size);
                    }
                    if (objectDetectorHelper != null) {
                        int videoHeight = videoSurface.getHeight();
                        int videoWidth = videoSurface.getWidth();
                        objectDetectorHelper.detect(videoBuffer, videoWidth, videoHeight, bitmap -> {
                            bitmapImage.post(() -> bitmapImage.setImageBitmap(bitmap));
                        });
                    }
                } catch (Exception e) {
                    handleError(e);
                }
            };
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
            Log.d(TAG, "initCamera: Video data listener added.");
        } catch (Exception e) {
            handleError(e);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated: Surface created.");
        try {
            if (codecManager == null) {
                codecManager = new DJICodecManager(getContext(), holder, holder.getSurfaceFrame().width(), holder.getSurfaceFrame().height());
                Log.d(TAG, "surfaceCreated: DJICodecManager initialized.");
            }
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
            Log.d(TAG, "surfaceCreated: Video data listener added to primary video feed.");
        } catch (Exception e) {
            handleError(e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged: Surface changed.");
        // Do nothing
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed: Surface destroyed.");
        try {
            if (codecManager != null) {
                codecManager.cleanSurface();
                codecManager = null;
                Log.d(TAG, "surfaceDestroyed: DJICodecManager cleaned.");
            }
            VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(videoDataListener);
            Log.d(TAG, "surfaceDestroyed: Video data listener removed from primary video feed.");
        } catch (Exception e) {
            handleError(e);
        }
    }

    private void onDetectionResults(Bitmap bitmap) {
        Log.d(TAG, "onDetectionResults: Detection results received.");
        try {
            bitmapImage.setImageBitmap(bitmap);
        } catch (Exception e) {
            handleError(e);
        }
    }

    private void handleError(Exception e) {
        Log.e(TAG, "handleError: Handling error - " + e.getMessage(), e);

        // 新しいスレッドを作成して、Looperを準備し、ダイアログを表示します
        new Thread(() -> {
            Looper.prepare();
            new Handler(Looper.getMainLooper()).post(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("エラーが発生しました")
                        .setMessage(e.getMessage())
                        .setPositiveButton("OK", (dialog, which) -> {
                            // ダイアログを閉じた後にログイン画面に遷移
                            Context context = getContext();
                            Intent intent = new Intent(context, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            context.startActivity(intent);
                        })
                        .setCancelable(false)
                        .show();
            });
            Looper.loop();
        }).start();
    }
}
