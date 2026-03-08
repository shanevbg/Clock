// SPDX-License-Identifier: GPL-3.0-only

package com.best.deskclock.screensaver;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.best.deskclock.data.SettingsDAO;

import java.util.Random;

/**
 * A custom View that renders an animated color-shifting background for the screensaver.
 *
 * <p>Supports three modes:</p>
 * <ul>
 *   <li><b>Hue Rotation:</b> Solid color cycling through the full HSV hue spectrum.</li>
 *   <li><b>Gradient:</b> Two user-selected colors blended via a rotating linear gradient.</li>
 *   <li><b>Aurora:</b> Multiple soft radial gradient blobs drifting independently.</li>
 * </ul>
 */
public class ColorShiftingBackgroundView extends View {

    private static final int AURORA_BLOB_COUNT = 4;
    private static final float AURORA_BLOB_RADIUS_FACTOR = 0.45f;

    private String mMode = "hue_rotation";
    private int mSpeed = 50;
    private int mColor1 = Color.RED;
    private int mColor2 = Color.BLUE;
    private float mBrightnessFactor = 1f;

    private ValueAnimator mAnimator;
    private float mAnimationProgress;

    // Hue rotation state
    private final float[] mHsv = {0f, 1f, 1f};

    // Gradient state
    private final Paint mGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Aurora state
    private final Paint mAuroraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[][] mBlobPositions = new float[AURORA_BLOB_COUNT][2];
    private final float[][] mBlobVelocities = new float[AURORA_BLOB_COUNT][2];
    private final float[] mBlobHues = new float[AURORA_BLOB_COUNT];
    private final Random mRandom = new Random();
    private boolean mBlobsInitialized = false;

    public ColorShiftingBackgroundView(Context context) {
        super(context);
        init();
    }

    public ColorShiftingBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorShiftingBackgroundView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mAuroraPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
    }

    /**
     * Configure the view from SharedPreferences and start the animation.
     */
    public void configure(SharedPreferences prefs) {
        mMode = SettingsDAO.getScreensaverColorShiftMode(prefs);
        mSpeed = SettingsDAO.getScreensaverColorShiftSpeed(prefs);
        mColor1 = SettingsDAO.getScreensaverColorShiftColor1(prefs);
        mColor2 = SettingsDAO.getScreensaverColorShiftColor2(prefs);

        int brightnessPercentage = SettingsDAO.getScreensaverBrightness(prefs);
        mBrightnessFactor = 0.1f + (brightnessPercentage / 100f) * 0.9f;

        mBlobsInitialized = false;
        startAnimation();
    }

    /**
     * Starts or restarts the animation loop.
     */
    public void startAnimation() {
        stopAnimation();

        // Map speed (1-100) to animator duration.
        // Higher speed = shorter cycle. Range: 120s (slow) to 4s (fast).
        long durationMs = (long) (120_000 - (mSpeed / 100f) * 116_000);
        durationMs = Math.max(4_000, durationMs);

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(durationMs);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setRepeatMode(ValueAnimator.RESTART);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mAnimationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.start();
    }

    /**
     * Stops the running animation if any.
     */
    public void stopAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBlobsInitialized = false;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        switch (mMode) {
            case "hue_rotation" -> drawHueRotation(canvas);
            case "gradient" -> drawGradientShift(canvas);
            case "aurora" -> drawAurora(canvas);
        }
    }

    private void drawHueRotation(Canvas canvas) {
        mHsv[0] = mAnimationProgress * 360f;
        mHsv[1] = 0.8f;
        mHsv[2] = mBrightnessFactor * 0.6f;
        canvas.drawColor(Color.HSVToColor(mHsv));
    }

    private void drawGradientShift(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float gradientAngle = mAnimationProgress * 360f;
        double rad = Math.toRadians(gradientAngle);
        float cx = w / 2f;
        float cy = h / 2f;
        float diagonal = (float) Math.sqrt(w * w + h * h) / 2f;

        float x0 = cx + (float) Math.cos(rad) * diagonal;
        float y0 = cy + (float) Math.sin(rad) * diagonal;
        float x1 = cx - (float) Math.cos(rad) * diagonal;
        float y1 = cy - (float) Math.sin(rad) * diagonal;

        int c1 = applyBrightness(mColor1);
        int c2 = applyBrightness(mColor2);

        LinearGradient gradient = new LinearGradient(x0, y0, x1, y1, c1, c2, Shader.TileMode.CLAMP);
        mGradientPaint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, mGradientPaint);
    }

    private void drawAurora(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        canvas.drawColor(applyBrightness(Color.BLACK));

        if (!mBlobsInitialized) {
            initializeBlobs(w, h);
        }

        updateBlobPositions(w, h);

        float radius = Math.min(w, h) * AURORA_BLOB_RADIUS_FACTOR;

        int saveCount = canvas.saveLayer(0, 0, w, h, null);

        for (int i = 0; i < AURORA_BLOB_COUNT; i++) {
            float bx = mBlobPositions[i][0];
            float by = mBlobPositions[i][1];

            float hue = (mBlobHues[i] + mAnimationProgress * 360f) % 360f;
            float[] hsv = {hue, 0.7f, mBrightnessFactor * 0.5f};
            int color = Color.HSVToColor(160, hsv);

            RadialGradient radialGradient = new RadialGradient(
                    bx, by, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            mAuroraPaint.setShader(radialGradient);
            canvas.drawCircle(bx, by, radius, mAuroraPaint);
        }

        canvas.restoreToCount(saveCount);
    }

    private void initializeBlobs(int w, int h) {
        for (int i = 0; i < AURORA_BLOB_COUNT; i++) {
            mBlobPositions[i][0] = mRandom.nextFloat() * w;
            mBlobPositions[i][1] = mRandom.nextFloat() * h;

            float baseSpeed = 0.3f + mRandom.nextFloat() * 0.7f;
            float angle = mRandom.nextFloat() * (float) (2 * Math.PI);
            mBlobVelocities[i][0] = (float) Math.cos(angle) * baseSpeed;
            mBlobVelocities[i][1] = (float) Math.sin(angle) * baseSpeed;

            mBlobHues[i] = mRandom.nextFloat() * 360f;
        }
        mBlobsInitialized = true;
    }

    private void updateBlobPositions(int w, int h) {
        float speedFactor = mSpeed / 50f;

        for (int i = 0; i < AURORA_BLOB_COUNT; i++) {
            mBlobPositions[i][0] += mBlobVelocities[i][0] * speedFactor;
            mBlobPositions[i][1] += mBlobVelocities[i][1] * speedFactor;

            if (mBlobPositions[i][0] < 0 || mBlobPositions[i][0] > w) {
                mBlobVelocities[i][0] *= -1;
                mBlobPositions[i][0] = Math.max(0, Math.min(w, mBlobPositions[i][0]));
            }
            if (mBlobPositions[i][1] < 0 || mBlobPositions[i][1] > h) {
                mBlobVelocities[i][1] *= -1;
                mBlobPositions[i][1] = Math.max(0, Math.min(h, mBlobPositions[i][1]));
            }
        }
    }

    private int applyBrightness(int color) {
        int r = Math.min(255, (int) (Color.red(color) * mBrightnessFactor));
        int g = Math.min(255, (int) (Color.green(color) * mBrightnessFactor));
        int b = Math.min(255, (int) (Color.blue(color) * mBrightnessFactor));
        return Color.rgb(r, g, b);
    }
}
