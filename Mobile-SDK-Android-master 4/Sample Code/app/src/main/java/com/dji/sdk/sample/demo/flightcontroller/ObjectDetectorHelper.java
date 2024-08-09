package com.dji.sdk.sample.demo.flightcontroller;

import static dji.midware.data.manager.P3.ServiceManager.getContext;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.graphics.Rect;
import android.util.Log;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;


import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class ObjectDetectorHelper {
    private static final String TAG = "ObjectDetectorHelper";
    private ObjectDetector objectDetector;
    private Context context;
    private DetectionResultsListener detectionResultsListener;
    private Handler handler = new Handler(Looper.getMainLooper());  // メインスレッドのHandlerを取得

    public interface DetectionResultsListener {
        void onDetectionResults(Bitmap bitmap);
    }

    public ObjectDetectorHelper(Context context, DetectionResultsListener detectionResultsListener) {
        this.context = context;
        this.detectionResultsListener = detectionResultsListener;
        setupObjectDetector();
    }

    private void setupObjectDetector() {
        try {
            ObjectDetectorOptions options = ObjectDetectorOptions.builder()
                    .setMaxResults(3)
                    .setScoreThreshold(0.5f)
                    .setBaseOptions(BaseOptions.builder().setNumThreads(2).build())
                    .build();

            objectDetector = ObjectDetector.createFromFileAndOptions(context, "mobilenetv1.tflite", options);
            Log.d(TAG, "Object detector was successfully created.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create object detector.", e);
        }
    }

    public void detect(byte[] videoBuffer, int width, int height, DetectionResultsListener listener) {
        showToast("Detecting objects in video buffer.");
        Log.d(TAG, "Detecting objects in video buffer of size: " + videoBuffer.length);
        Bitmap bitmap = convertNV21ToBitmap(videoBuffer, width, height);
        if (bitmap != null) {
            detect(bitmap, listener);
        }
    }

    private void detect(Bitmap bitmap, DetectionResultsListener listener) {
        showToast("Starting inference");
        Log.d(TAG, "Converting Bitmap to TensorImage");
        TensorImage tensorImage = new TensorImage(DataType.UINT8);
        tensorImage.load(bitmap);

        long startTime = System.currentTimeMillis();
        List<Detection> results = objectDetector.detect(tensorImage);
        long endTime = System.currentTimeMillis();
        Log.d(TAG, "Inference time: " + (endTime - startTime) + " ms");

        Bitmap outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        for (Detection result : results) {
            RectF boundingBox = result.getBoundingBox();
            // Draw bounding boxes on the bitmap (implementation not shown here)
        }

        if (listener != null) {
            listener.onDetectionResults(outputBitmap);
        }
    }

    private Bitmap convertNV21ToBitmap(byte[] nv21Data, int width, int height) {
        try {
            // NV21のYUVデータをJPEGに圧縮
            YuvImage yuvImage = new YuvImage(nv21Data, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, baos); // 圧縮率を100に設定
            byte[] jpegArray = baos.toByteArray();

            // JPEGデータからBitmapを生成
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.length);
            Log.d(TAG, "Converted NV21 data to Bitmap");
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert NV21 to Bitmap", e);
            return null;
        }
    }


    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
    }

}