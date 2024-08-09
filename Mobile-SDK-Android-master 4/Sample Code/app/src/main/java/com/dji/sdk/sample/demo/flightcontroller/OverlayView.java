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
    private Paint paint;

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
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5.0f);
    }

    public void setResults(List<Detection> results) {
        this.results = results;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (results != null) {
            for (Detection result : results) {
                RectF boundingBox = result.getBoundingBox();
                canvas.drawRect(boundingBox, paint);
            }
        }
    }
}
