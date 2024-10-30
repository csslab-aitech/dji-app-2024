package com.dji.sdk.sample.demo.flightcontroller;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ObjectDetectorHelper {
    private static final String TAG = "ObjectDetectorHelper";
    private ObjectDetector objectDetector;
    private Context context;
    private DetectionResultsListener detectionResultsListener;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public interface DetectionResultsListener {
        void onDetectionResults(List<Detection> results);
    }

    public ObjectDetectorHelper(Context context, DetectionResultsListener detectionResultsListener) {
        this.context = context;
        this.detectionResultsListener = detectionResultsListener;
        setupObjectDetector();
    }

    private void setupObjectDetector() {
        try {
            ObjectDetectorOptions options = ObjectDetectorOptions.builder()
                    .setMaxResults(1)
                    .setScoreThreshold(0.5f)
                    .setBaseOptions(BaseOptions.builder().setNumThreads(2).build())
                    .build();

            objectDetector = ObjectDetector.createFromFileAndOptions(context, "efficientdet-lite0.tflite", options);
            Log.d(TAG, "Object detector was successfully created.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create object detector.", e);
        }
    }

    public void detect(Bitmap bitmap, DetectionResultsListener listener) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null, skipping detection.");
            return;
        }

        Log.d(TAG, "Starting inference");
        TensorImage tensorImage = new TensorImage(DataType.UINT8);
        tensorImage.load(bitmap);

        executorService.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                List<Detection> results = objectDetector.detect(tensorImage);
                long endTime = System.currentTimeMillis();
                Log.d(TAG, "Inference time: " + (endTime - startTime) + " ms");

                // 結果をリスナーに通知
                handler.post(() -> {
                    if (listener != null) {
                        listener.onDetectionResults(results); // List<Detection> をリスナーに渡す
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error during object detection: ", e);
                showToast("Error during object detection: " + e.getMessage());
            }
        });
    }




    private void showToast(final String message) {
        handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());  // contextを直接使用
    }
}