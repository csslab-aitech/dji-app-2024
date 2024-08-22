
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
                String label = result.getCategories().get(0).getLabel();  // クラス名を取得
                float confidence = result.getCategories().get(0).getScore();  // 信頼度を取得

                String text = label + " " + String.format("%.2f", confidence * 100) + "%";
                canvas.drawText(text, boundingBox.left, boundingBox.top - 10, textPaint);  // バウンディングボックスの上に描写
            }
        }
    }
}
