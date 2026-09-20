package com.xiaohongshu.ui.home.follow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.xiaohongshu.R;
import com.xiaohongshu.util.AnimationHelper;

public class AnimatedCircleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float radius = 10f;
    private int color;
    private float radiusEnd = 200f;

    public AnimatedCircleView(Context context) {
        super(context);
        init();
    }

    public AnimatedCircleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnimatedCircleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        color = ContextCompat.getColor(getContext(), R.color.theme_red);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setRadiusEnd(float radiusEnd) {
        this.radiusEnd = radiusEnd;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    private void startAnimation() {
        animator = AnimationHelper.createInfiniteAnimator(10f, radiusEnd, 1200, animation -> {
            Float value = (Float) animation.getAnimatedValue();
            radius = value;
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float centerX = 10f;
        float centerY = 10f;

        // Draw outer circle
        paint.setColor(color);
        paint.setAlpha((int) (255 * 0.8f));
        canvas.drawCircle(centerX, centerY, radius, paint);

        // Draw middle circle
        paint.setAlpha((int) (255 * 0.4f));
        canvas.drawCircle(centerX, centerY, radius / 2, paint);

        // Draw inner circle
        paint.setAlpha((int) (255 * 0.2f));
        canvas.drawCircle(centerX, centerY, radius / 4, paint);
    }
}

