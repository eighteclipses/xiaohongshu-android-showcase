package com.xiaohongshu.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * 支持双击缩放与双指捏合的 ImageView，用于笔记图片详情的查看。
 * 缩放状态由自身维护，父级 ViewPager 可正常消费横向翻页手势。
 */
@SuppressLint("AppCompatCustomView")
public class ZoomableImageView extends ImageView {
    private static final float MAX_SCALE = 4f;
    private static final float MIN_SCALE = 1f;

    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private boolean isZoomed = false;
    private float lastTouchX;
    private float lastTouchY;
    /** false 时双击不缩放，改由 doubleTapAction 响应（如双击点赞） */
    private boolean doubleTapZoomEnabled = true;
    private Runnable doubleTapAction;

    public ZoomableImageView(Context context) {
        super(context);
        init();
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float currentScale = currentScale();
                float factor = detector.getScaleFactor();
                float target = Math.max(MIN_SCALE, Math.min(MAX_SCALE, currentScale * factor));
                if (target != currentScale) {
                    matrix.postScale(target / currentScale, target / currentScale,
                            detector.getFocusX(), detector.getFocusY());
                    clampTranslation();
                    setImageMatrix(matrix);
                    isZoomed = target > MIN_SCALE + 0.01f;
                }
                return true;
            }
        });

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!doubleTapZoomEnabled) {
                    if (doubleTapAction != null) doubleTapAction.run();
                    return true;
                }
                if (isZoomed) {
                    resetMatrix();
                } else {
                    float scale = Math.min(2.5f, MAX_SCALE);
                    matrix.postScale(scale / currentScale(), scale / currentScale(), e.getX(), e.getY());
                    clampTranslation();
                    setImageMatrix(matrix);
                    isZoomed = true;
                }
                return true;
            }
        });
    }

    /** 关闭双击缩放并指定双击动作（详情页双击点赞场景） */
    public void setDoubleTapAction(Runnable action) {
        this.doubleTapAction = action;
        this.doubleTapZoomEnabled = action == null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetMatrix();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        resetMatrix();
    }

    private void resetMatrix() {
        matrix.reset();
        Drawable drawable = getDrawable();
        if (drawable != null && getWidth() > 0 && getHeight() > 0) {
            // 初始 fitCenter：与原 scaleType 观感一致
            float viewW = getWidth();
            float viewH = getHeight();
            float dw = drawable.getIntrinsicWidth();
            float dh = drawable.getIntrinsicHeight();
            if (dw > 0 && dh > 0) {
                float scale = Math.min(viewW / dw, viewH / dh);
                matrix.postScale(scale, scale, viewW / 2f, viewH / 2f);
                matrix.postTranslate((viewW - dw * scale) / 2f, (viewH - dh * scale) / 2f);
            }
        }
        setImageMatrix(matrix);
        isZoomed = false;
    }

    private float currentScale() {
        matrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    /** 缩放后平移限位，避免图片被拖出视野 */
    private void clampTranslation() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;
        matrix.getValues(matrixValues);
        float scale = matrixValues[Matrix.MSCALE_X];
        float drawW = drawable.getIntrinsicWidth() * scale;
        float drawH = drawable.getIntrinsicHeight() * scale;
        float[] values = matrixValues;
        float minX = Math.min(0, getWidth() - drawW);
        float minY = Math.min(0, getHeight() - drawH);
        values[Matrix.MTRANS_X] = Math.max(minX, Math.min(0, values[Matrix.MTRANS_X]));
        values[Matrix.MTRANS_Y] = Math.max(minY, Math.min(0, values[Matrix.MTRANS_Y]));
        matrix.setValues(values);
    }

    /** 已放大时把拖拽手势留在自身，放行给 ViewPager 翻页 */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        // 已放大时支持单指拖拽查看局部
        if (isZoomed) {
            getParent().requestDisallowInterceptTouchEvent(true);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    matrix.postTranslate(event.getX() - lastTouchX, event.getY() - lastTouchY);
                    clampTranslation();
                    setImageMatrix(matrix);
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
                default:
                    break;
            }
        } else {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        // 始终交给父类做点击检测，保证单击打开全屏预览
        return super.onTouchEvent(event);
    }
}
