package com.proxy.wireopen.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Custom hardware-accelerated Oscilloscope Waveform View.
 * Renders smooth cardiac/oscilloscope Bézier curves for Downlink (emerald green)
 * and Uplink (tech purple/indigo dashed line) with an animated glowing pulse particle at the trailing edge.
 */
public class OscilloscopeWaveformView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dlGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ulPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulseCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulseHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulseStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path dlPath = new Path();
    private final Path ulPath = new Path();

    // Data points buffer
    private static final int DEFAULT_POINT_COUNT = 12;
    private final List<Float> historyDL = new ArrayList<>(Arrays.asList(
            14f, 20f, 26f, 32f, 28f, 42f, 48f, 44f, 58f, 54f, 62f, 68f
    ));
    private final List<Float> historyUL = new ArrayList<>(Arrays.asList(
            4f, 6f, 8f, 12f, 9f, 14f, 16f, 15f, 20f, 18f, 22f, 25f
    ));

    private float lastPulseX = 0f;
    private float lastPulseY = 0f;
    private float pulsePhase = 0f; // 0.0 to 1.0 for pulsation animation
    private ValueAnimator pulseAnimator;
    private boolean isConnected = true;

    public OscilloscopeWaveformView(Context context) {
        super(context);
        init();
    }

    public OscilloscopeWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OscilloscopeWaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        // Grid paint (Minimalist dashed lines)
        gridPaint.setColor(Color.parseColor("#10B981"));
        gridPaint.setAlpha(26); // ~10% opacity
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{6 * density, 6 * density}, 0));

        // Downlink Curve: Emerald Green (#10B981)
        dlPaint.setColor(Color.parseColor("#10B981"));
        dlPaint.setStyle(Paint.Style.STROKE);
        dlPaint.setStrokeWidth(2f * density);
        dlPaint.setStrokeCap(Paint.Cap.ROUND);
        dlPaint.setStrokeJoin(Paint.Join.ROUND);

        // Downlink Glow pass (subtle aura underneath)
        dlGlowPaint.setColor(Color.parseColor("#34D399"));
        dlGlowPaint.setStyle(Paint.Style.STROKE);
        dlGlowPaint.setStrokeWidth(4.5f * density);
        dlGlowPaint.setAlpha(45);
        dlGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        dlGlowPaint.setStrokeJoin(Paint.Join.ROUND);

        // Uplink Curve: Tech Purple / Indigo Dashed Line (#818CF8)
        ulPaint.setColor(Color.parseColor("#818CF8"));
        ulPaint.setStyle(Paint.Style.STROKE);
        ulPaint.setStrokeWidth(1.5f * density);
        ulPaint.setStrokeCap(Paint.Cap.ROUND);
        ulPaint.setPathEffect(new DashPathEffect(new float[]{5 * density, 5 * density}, 0));

        // Pulse Particle Paints
        pulseHaloPaint.setColor(Color.parseColor("#34D399"));
        pulseHaloPaint.setStyle(Paint.Style.FILL);

        pulseCorePaint.setColor(Color.parseColor("#34D399"));
        pulseCorePaint.setStyle(Paint.Style.FILL);

        pulseStrokePaint.setColor(Color.WHITE);
        pulseStrokePaint.setStyle(Paint.Style.STROKE);
        pulseStrokePaint.setStrokeWidth(1.2f * density);

        // Continuous pulse animation for trailing dot
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(1200);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            pulsePhase = (float) animation.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        pulseAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }

    public synchronized void setConnected(boolean connected) {
        this.isConnected = connected;
        if (!connected) {
            reset();
        }
        postInvalidate();
    }

    /**
     * Injects a real or simulated traffic throughput sample.
     * @param dlBytesPerSec Downlink speed in bytes/sec
     * @param ulBytesPerSec Uplink speed in bytes/sec
     */
    public synchronized void addSpeedSample(double dlBytesPerSec, double ulBytesPerSec) {
        if (!isConnected) return;

        float dlVal;
        if (dlBytesPerSec <= 0) {
            dlVal = 6f;
        } else {
            // Logarithmic dynamic scaling: smooth & lively across 1KB/s to 100MB/s
            double mb = dlBytesPerSec / (1024.0 * 1024.0);
            if (mb < 0.1) {
                dlVal = (float) (8.0 + (dlBytesPerSec / (1024.0 * 100.0)) * 14.0);
            } else {
                dlVal = (float) Math.min(85.0, 22.0 + Math.log10(mb + 1.0) * 38.0);
            }
        }

        float ulVal;
        if (ulBytesPerSec <= 0) {
            ulVal = 3f;
        } else {
            double mb = ulBytesPerSec / (1024.0 * 1024.0);
            if (mb < 0.1) {
                ulVal = (float) (4.0 + (ulBytesPerSec / (1024.0 * 100.0)) * 10.0);
            } else {
                ulVal = (float) Math.min(48.0, 14.0 + Math.log10(mb + 1.0) * 22.0);
            }
        }

        if (historyDL.size() >= DEFAULT_POINT_COUNT) {
            historyDL.remove(0);
        }
        historyDL.add(dlVal);

        if (historyUL.size() >= DEFAULT_POINT_COUNT) {
            historyUL.remove(0);
        }
        historyUL.add(ulVal);

        postInvalidate();
    }

    /**
     * Resets waveform to baseline quiet line.
     */
    public synchronized void reset() {
        historyDL.clear();
        historyUL.clear();
        for (int i = 0; i < DEFAULT_POINT_COUNT; i++) {
            historyDL.add(6f);
            historyUL.add(3f);
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Draw Minimalist Grid Dashed Lines
        float yGrid1 = h * 0.35f;
        float yGrid2 = h * 0.70f;
        canvas.drawLine(0, yGrid1, w, yGrid1, gridPaint);
        canvas.drawLine(0, yGrid2, w, yGrid2, gridPaint);

        // 2. Build smooth Catmull-Rom Bézier spline paths
        synchronized (this) {
            float maxVal = 90f;
            buildBezierPath(historyDL, w, h, maxVal, dlPath, true);
            buildBezierPath(historyUL, w, h, maxVal, ulPath, false);
        }

        // 3. Draw Uplink (Tech Purple / Indigo Dashed Line)
        canvas.drawPath(ulPath, ulPaint);

        // 4. Draw Downlink (Emerald Glow + Emerald Line)
        canvas.drawPath(dlPath, dlGlowPaint);
        canvas.drawPath(dlPath, dlPaint);

        // 5. Draw Pulse Particle at leading edge of Downlink curve
        if (isConnected && lastPulseX > 0) {
            float density = getResources().getDisplayMetrics().density;
            // Pulsing halo
            float haloRadius = (3.5f + 4.5f * pulsePhase) * density;
            int haloAlpha = (int) (120 * (1f - pulsePhase));
            pulseHaloPaint.setAlpha(Math.max(0, haloAlpha));
            canvas.drawCircle(lastPulseX, lastPulseY, haloRadius, pulseHaloPaint);

            // Core dot
            float coreRadius = 3.2f * density;
            canvas.drawCircle(lastPulseX, lastPulseY, coreRadius, pulseCorePaint);
            canvas.drawCircle(lastPulseX, lastPulseY, coreRadius, pulseStrokePaint);
        }
    }

    private void buildBezierPath(List<Float> points, float w, float h, float max, Path path, boolean recordLastPulse) {
        path.reset();
        int n = points.size();
        if (n < 2) return;

        float density = getResources().getDisplayMetrics().density;
        float padLeft = 4f * density;
        float padRight = 14f * density; // Room for trailing pulse particle
        float usableW = Math.max(10f, w - padLeft - padRight);

        float step = usableW / (n - 1);
        PointF[] pts = new PointF[n];
        for (int i = 0; i < n; i++) {
            float p = points.get(i);
            float y = h - (p / max) * (h * 0.72f) - (h * 0.12f);
            pts[i] = new PointF(padLeft + i * step, Math.max(4f, Math.min(h - 4f, y)));
        }

        path.moveTo(pts[0].x, pts[0].y);

        for (int i = 0; i < n - 1; i++) {
            PointF p0 = (i == 0) ? pts[0] : pts[i - 1];
            PointF p1 = pts[i];
            PointF p2 = pts[i + 1];
            PointF p3 = (i + 2 < n) ? pts[i + 2] : p2;

            float cp1x = p1.x + (p2.x - p0.x) / 6f;
            float cp1y = p1.y + (p2.y - p0.y) / 6f;
            float cp2x = p2.x - (p3.x - p1.x) / 6f;
            float cp2y = p2.y - (p3.y - p1.y) / 6f;

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
        }

        if (recordLastPulse) {
            lastPulseX = pts[n - 1].x;
            lastPulseY = pts[n - 1].y;
        }
    }
}
