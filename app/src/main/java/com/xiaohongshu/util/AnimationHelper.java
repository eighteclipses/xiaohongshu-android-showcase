package com.xiaohongshu.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Helper class for common animations.
 * Provides utility methods for animating views.
 */
public class AnimationHelper {

    /**
     * Fade in animation
     */
    public static void fadeIn(View view, long duration) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(duration)
                .setListener(null);
    }

    /**
     * Fade out animation
     */
    public static void fadeOut(View view, long duration, Runnable onEnd) {
        view.animate()
                .alpha(0f)
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(View.GONE);
                        if (onEnd != null) {
                            onEnd.run();
                        }
                    }
                });
    }

    /**
     * Scale animation
     */
    public static void scale(View view, float fromScale, float toScale, long duration) {
        view.setScaleX(fromScale);
        view.setScaleY(fromScale);
        view.animate()
                .scaleX(toScale)
                .scaleY(toScale)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator());
    }

    /**
     * Create a ValueAnimator for float values
     */
    public static ValueAnimator createFloatAnimator(float from, float to, long duration, ValueAnimator.AnimatorUpdateListener listener) {
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());
        if (listener != null) {
            animator.addUpdateListener(listener);
        }
        return animator;
    }

    /**
     * Create an infinite repeating ValueAnimator
     */
    public static ValueAnimator createInfiniteAnimator(float from, float to, long duration, ValueAnimator.AnimatorUpdateListener listener) {
        ValueAnimator animator = createFloatAnimator(from, to, duration, listener);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        return animator;
    }

    /**
     * Translate animation
     */
    public static void translateY(View view, float fromY, float toY, long duration) {
        view.setTranslationY(fromY);
        view.animate()
                .translationY(toY)
                .setDuration(duration)
                .setInterpolator(new AccelerateDecelerateInterpolator());
    }
}

