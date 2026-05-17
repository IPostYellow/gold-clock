package com.example.goldclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class KLineChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint risePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bodyRect = new RectF();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd", Locale.US);

    private List<KLineEntry> entries = new ArrayList<>();

    public KLineChartView(Context context) {
        super(context);
        init();
    }

    public KLineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint.setColor(0xFFE3D8C6);
        gridPaint.setStrokeWidth(1f);

        textPaint.setColor(0xFF64748B);
        textPaint.setTextSize(sp(11));

        risePaint.setColor(0xFF047857);
        risePaint.setStrokeWidth(dp(1.4f));

        fallPaint.setColor(0xFFB91C1C);
        fallPaint.setStrokeWidth(dp(1.4f));

        linePaint.setColor(0xFFC08418);
        linePaint.setStrokeWidth(dp(1.2f));

        emptyPaint.setColor(0xFF64748B);
        emptyPaint.setTextSize(sp(14));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setEntries(List<KLineEntry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (entries.isEmpty()) {
            canvas.drawText("暂无 K 线数据", getWidth() / 2f, getHeight() / 2f, emptyPaint);
            return;
        }

        float left = dp(52);
        float right = getWidth() - dp(10);
        float top = dp(14);
        float bottom = getHeight() - dp(34);
        float width = right - left;
        float height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (KLineEntry entry : entries) {
            min = Math.min(min, entry.low);
            max = Math.max(max, entry.high);
        }
        double padding = Math.max((max - min) * 0.08d, 1d);
        min -= padding;
        max += padding;

        drawGrid(canvas, left, right, top, bottom, min, max);
        drawCandles(canvas, left, top, width, height, min, max);
        drawDates(canvas, left, right, bottom);
        drawLastClose(canvas, left, right, top, height, min, max);
    }

    private void drawGrid(Canvas canvas, float left, float right, float top, float bottom, double min, double max) {
        int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            float y = top + (bottom - top) * i / gridLines;
            canvas.drawLine(left, y, right, y, gridPaint);

            double value = max - (max - min) * i / gridLines;
            canvas.drawText(String.format(Locale.US, "%.0f", value), dp(3), y + dp(4), textPaint);
        }
    }

    private void drawCandles(Canvas canvas, float left, float top, float width, float height, double min, double max) {
        int count = entries.size();
        float slot = width / count;
        float candleWidth = Math.max(dp(3), Math.min(dp(10), slot * 0.58f));

        for (int i = 0; i < count; i++) {
            KLineEntry entry = entries.get(i);
            float centerX = left + slot * i + slot / 2f;
            float highY = valueToY(entry.high, top, height, min, max);
            float lowY = valueToY(entry.low, top, height, min, max);
            float openY = valueToY(entry.open, top, height, min, max);
            float closeY = valueToY(entry.close, top, height, min, max);
            Paint paint = entry.isRising() ? risePaint : fallPaint;

            canvas.drawLine(centerX, highY, centerX, lowY, paint);
            float rectTop = Math.min(openY, closeY);
            float rectBottom = Math.max(openY, closeY);
            if (rectBottom - rectTop < dp(2)) {
                rectBottom = rectTop + dp(2);
            }
            bodyRect.set(centerX - candleWidth / 2f, rectTop, centerX + candleWidth / 2f, rectBottom);
            canvas.drawRect(bodyRect, paint);
        }
    }

    private void drawDates(Canvas canvas, float left, float right, float bottom) {
        KLineEntry first = entries.get(0);
        KLineEntry last = entries.get(entries.size() - 1);
        canvas.drawText(formatDate(first.timestampSeconds), left, bottom + dp(22), textPaint);

        String lastDate = formatDate(last.timestampSeconds);
        float lastWidth = textPaint.measureText(lastDate);
        canvas.drawText(lastDate, right - lastWidth, bottom + dp(22), textPaint);
    }

    private void drawLastClose(Canvas canvas, float left, float right, float top, float height, double min, double max) {
        KLineEntry last = entries.get(entries.size() - 1);
        float y = valueToY(last.close, top, height, min, max);
        canvas.drawLine(left, y, right, y, linePaint);
    }

    private float valueToY(double value, float top, float height, double min, double max) {
        return top + (float) ((max - value) / (max - min) * height);
    }

    private String formatDate(long timestampSeconds) {
        return dateFormat.format(new Date(timestampSeconds * 1000L));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
