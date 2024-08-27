package com.dji.sdk.sample.demo.flightcontroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.List;

public class OverlayView extends View {
    private List<Detection> results;
    private Paint boxPaint;
    private Paint textPaint;
    private Paint centerPointPaint;

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5.0f);

        textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(40);
        textPaint.setStyle(Paint.Style.FILL);

        centerPointPaint = new Paint();
        centerPointPaint.setColor(Color.GREEN);
        centerPointPaint.setStyle(Paint.Style.FILL);
    }

    public void setResults(List<Detection> results) {
        this.results = results;
        postInvalidate(); // 画面の再描画を指示
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (results != null) {
            for (Detection result : results) {
                // バウンディングボックスを描画
                RectF boundingBox = result.getBoundingBox();
                canvas.drawRect(boundingBox, boxPaint);

                // クラス名と信頼度を描画
                String label = result.getCategories().get(0).getLabel();
                float confidence = result.getCategories().get(0).getScore();
                String text = label + " " + String.format("%.2f", confidence * 100) + "%";

                // バウンディングボックスの左上にテキストを表示
                canvas.drawText(text, boundingBox.left, boundingBox.top - 10, textPaint);

                // バウンディングボックスの中心を計算して表示
                float centerX = boundingBox.centerX();
                float centerY = boundingBox.centerY();
                canvas.drawCircle(centerX, centerY, 10.0f, centerPointPaint); // 中心に緑色の点を描画

                // 座標テキストをバウンディングボックスの下に表示
                String coordinatesText = String.format("Center: (%.1f, %.1f)",
                         centerX, centerY);
                canvas.drawText(coordinatesText, boundingBox.left, boundingBox.bottom + 40, textPaint);
            }
        }
    }

    // 新しく追加するメソッド
    public List<Detection> getResults() {
        return results;
    }
}
