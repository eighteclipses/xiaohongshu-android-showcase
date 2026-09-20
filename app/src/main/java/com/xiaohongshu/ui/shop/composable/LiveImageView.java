package com.xiaohongshu.ui.shop.composable;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import com.xiaohongshu.R;
import com.xiaohongshu.util.AnimationHelper;

public class LiveImageView extends AppCompatImageView {
    private ValueAnimator alphaAnimator;
    private ValueAnimator sizeAnimator;
    private float alpha = 0.3f;
    private float circleSize = 50f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LiveImageView(Context context) {
        super(context);
        init();
    }

    public LiveImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiveImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(getResources().getColor(R.color.xhs_red, null));
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
        alphaAnimator = AnimationHelper.createInfiniteAnimator(0.3f, 1.0f, 1000, animation -> {
            Float value = (Float) animation.getAnimatedValue();
            alpha = value;
            invalidate();
        });
        alphaAnimator.start();

        sizeAnimator = AnimationHelper.createInfiniteAnimator(50f, 70f, 1000, animation -> {
            Float value = (Float) animation.getAnimatedValue();
            circleSize = value;
            invalidate();
        });
        sizeAnimator.start();
    }

    private void stopAnimation() {
        if (alphaAnimator != null) {
            alphaAnimator.cancel();
            alphaAnimator = null;
        }
        if (sizeAnimator != null) {
            sizeAnimator.cancel();
            sizeAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = circleSize / 2f;
        
        paint.setAlpha((int) (255 * alpha));
        canvas.drawCircle(centerX, centerY, radius, paint);
    }
}

