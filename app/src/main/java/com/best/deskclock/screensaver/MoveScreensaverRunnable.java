/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.screensaver;

import static com.best.deskclock.utils.AnimatorUtils.getAlphaAnimator;
import static com.best.deskclock.utils.AnimatorUtils.getScaleAnimator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.best.deskclock.uidata.UiDataModel;
import com.best.deskclock.utils.Utils;

/**
 * This runnable chooses a random initial position for {@link #mSaverView} within
 * {@link #mContentView} if {@link #mSaverView} is transparent. It also schedules itself to run
 * each minute, at which time {@link #mSaverView} is faded out, set to a new random location, and
 * faded in.
 */
public final class MoveScreensaverRunnable implements Runnable {

    /**
     * The duration over which the fade in/out animations occur.
     */
    private static final long FADE_TIME = 3000L;

    /**
     * Accelerate the hide animation.
     */
    private final Interpolator mAcceleration = new AccelerateInterpolator();

    /**
     * Decelerate the show animation.
     */
    private final Interpolator mDeceleration = new DecelerateInterpolator();

    /**
     * The container that houses {@link #mSaverView}.
     */
    private final View mContentView;

    /**
     * The display within the {@link #mContentView} that is randomly positioned.
     */
    private final View mSaverView;

    /**
     * Tracks the currently executing animation if any; used to gracefully stop the animation.
     */
    private Animator mActiveAnimator;

    /**
     * The index of this element among combo floating elements (0-based).
     * Used to spread initial positions across different screen regions.
     */
    private final int mRegionIndex;

    /**
     * The total number of combo floating elements.
     * Used together with {@link #mRegionIndex} to divide the screen into zones.
     */
    private final int mRegionCount;

    /**
     * @param contentView contains the {@code saverView}
     * @param saverView   a child view of {@code contentView} that periodically moves around
     */
    public MoveScreensaverRunnable(View contentView, View saverView) {
        this(contentView, saverView, 0, 1);
    }

    /**
     * @param contentView contains the {@code saverView}
     * @param saverView   a child view of {@code contentView} that periodically moves around
     * @param regionIndex the index of this element for initial spread (0-based)
     * @param regionCount the total number of elements being spread
     */
    public MoveScreensaverRunnable(View contentView, View saverView, int regionIndex, int regionCount) {
        mContentView = contentView;
        mSaverView = saverView;
        mRegionIndex = regionIndex;
        mRegionCount = Math.max(1, regionCount);
    }

    /**
     * @return a random integer between 0 and the {@code maximum} exclusive.
     */
    private static float getRandomPoint(float maximum) {
        return (int) (Math.random() * maximum);
    }

    /**
     * Start or restart the random movement of the saver view within the content view.
     */
    public void start() {
        // Stop any existing animations or callbacks.
        stop();

        // Reset the alpha to 0 so saver view will be randomly positioned within the new bounds.
        mSaverView.setAlpha(0);

        // Execute the position updater runnable to choose the first random position of saver view.
        run();

        // Schedule callbacks every half minute to adjust the position of mSaverView.
        // For combo elements, add a per-element time jitter so they don't all move simultaneously.
        long jitter = mRegionCount > 1 ? (long) (mRegionIndex * 7000L) : 0;
        UiDataModel.getUiDataModel().addHalfMinuteCallback(this, -FADE_TIME + jitter);
    }

    /**
     * Stop the random movement of the saver view within the content view.
     */
    public void stop() {
        UiDataModel.getUiDataModel().removePeriodicCallback(this);

        // End any animation currently running.
        if (mActiveAnimator != null) {
            mActiveAnimator.end();
            mActiveAnimator = null;
        }
    }

    @Override
    public void run() {
        Utils.enforceMainLooper();

        final boolean selectInitialPosition = mSaverView.getAlpha() == 0f;
        if (selectInitialPosition) {
            // When selecting an initial position for the saver view the width and height of
            // mContentView are untrustworthy if this was caused by a configuration change. To
            // combat this, we position the mSaverView randomly within the smallest box that is
            // guaranteed to work.
            // Use the full content view dimensions for initial placement.
            final float cw = mContentView.getWidth();
            final float ch = mContentView.getHeight();
            final float sw = Math.max(mSaverView.getWidth(), 1);
            final float sh = Math.max(mSaverView.getHeight(), 1);

            final float newX;
            final float newY;

            if (mRegionCount > 1 && cw > sw && ch > sh) {
                // Place elements at well-separated fixed positions with slight randomness.
                // Predefined positions spread across the screen (as proportions).
                final float[][] positions = {
                        {0.10f, 0.10f},  // top-left
                        {0.70f, 0.15f},  // top-right
                        {0.30f, 0.65f},  // bottom-center-left
                        {0.60f, 0.55f},  // bottom-center-right
                };
                float px = positions[mRegionIndex % positions.length][0];
                float py = positions[mRegionIndex % positions.length][1];

                // Add slight randomness (±10% of screen)
                float jitterX = (float) (Math.random() - 0.5) * 0.1f * cw;
                float jitterY = (float) (Math.random() - 0.5) * 0.1f * ch;

                newX = Math.max(0, Math.min(cw - sw, px * cw + jitterX));
                newY = Math.max(0, Math.min(ch - sh, py * ch + jitterY));
            } else {
                final int smallestDim = Math.min((int) cw, (int) ch);
                newX = getRandomPoint(Math.max(1, smallestDim - sw));
                newY = getRandomPoint(Math.max(1, smallestDim - sh));
            }

            mSaverView.setX(newX);
            mSaverView.setY(newY);
            mActiveAnimator = getAlphaAnimator(mSaverView, 0f, 1f);
            mActiveAnimator.setDuration(FADE_TIME);
            mActiveAnimator.setInterpolator(mDeceleration);
        } else {
            // Select a new random position anywhere in mContentView that will fit mSaverView.
            // Try to avoid overlapping with sibling floating elements.
            final float maxX = Math.max(1, mContentView.getWidth() - mSaverView.getWidth());
            final float maxY = Math.max(1, mContentView.getHeight() - mSaverView.getHeight());
            final float[] pos = findBestPosition(maxX, maxY);
            final float newX = pos[0];
            final float newY = pos[1];

            // Fade out and shrink the saver view.
            final AnimatorSet hide = new AnimatorSet();
            hide.setDuration(FADE_TIME);
            hide.setInterpolator(mAcceleration);
            hide.play(getAlphaAnimator(mSaverView, 1f, 0f))
                    .with(getScaleAnimator(mSaverView, 1f, 0.85f));

            // Fade in and grow the saver view after altering its position.
            final AnimatorSet show = new AnimatorSet();
            show.setDuration(FADE_TIME);
            show.setInterpolator(mDeceleration);
            show.play(getAlphaAnimator(mSaverView, 0f, 1f))
                    .with(getScaleAnimator(mSaverView, 0.85f, 1f));
            show.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mSaverView.setX(newX);
                    mSaverView.setY(newY);
                }
            });

            // Execute hide followed by show.
            final AnimatorSet all = new AnimatorSet();
            all.play(show).after(hide);
            mActiveAnimator = all;
        }
        mActiveAnimator.start();
    }

    /**
     * Try several random positions and pick the one with the least overlap with siblings.
     */
    private float[] findBestPosition(float maxX, float maxY) {
        float bestX = getRandomPoint(maxX);
        float bestY = getRandomPoint(maxY);

        if (mRegionCount <= 1) {
            return new float[]{bestX, bestY};
        }

        float bestOverlap = computeOverlapWithSiblings(bestX, bestY);

        for (int attempt = 0; attempt < 10 && bestOverlap > 0; attempt++) {
            float tryX = getRandomPoint(maxX);
            float tryY = getRandomPoint(maxY);
            float overlap = computeOverlapWithSiblings(tryX, tryY);
            if (overlap < bestOverlap) {
                bestX = tryX;
                bestY = tryY;
                bestOverlap = overlap;
            }
        }

        return new float[]{bestX, bestY};
    }

    /**
     * Computes the total overlap area between this view (at the candidate position) and
     * all sibling views in the content container.
     */
    private float computeOverlapWithSiblings(float candidateX, float candidateY) {
        if (!(mContentView instanceof ViewGroup container)) {
            return 0;
        }

        RectF candidate = new RectF(
                candidateX, candidateY,
                candidateX + mSaverView.getWidth(),
                candidateY + mSaverView.getHeight());

        float totalOverlap = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            View sibling = container.getChildAt(i);
            if (sibling == mSaverView || sibling.getVisibility() != View.VISIBLE) {
                continue;
            }

            RectF siblingRect = new RectF(
                    sibling.getX(), sibling.getY(),
                    sibling.getX() + sibling.getWidth(),
                    sibling.getY() + sibling.getHeight());

            if (RectF.intersects(candidate, siblingRect)) {
                float overlapX = Math.min(candidate.right, siblingRect.right) - Math.max(candidate.left, siblingRect.left);
                float overlapY = Math.min(candidate.bottom, siblingRect.bottom) - Math.max(candidate.top, siblingRect.top);
                totalOverlap += overlapX * overlapY;
            }
        }

        return totalOverlap;
    }
}
