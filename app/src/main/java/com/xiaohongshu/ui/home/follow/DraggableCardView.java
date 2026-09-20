package com.xiaohongshu.ui.home.follow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

public class DraggableCardView extends ViewGroup {
    private float swipeX = 0f;
    private float swipeY = 0f;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private boolean isDragging = false;
    private float screenWidth;
    private float swipeXLeft;
    private float swipeXRight;
    private OnSwipeListener onSwipeListener;
    private int currentIndex = 0;
    private float initialTouchX = 0f;
    private float initialTouchY = 0f;
    private static final float SWIPE_THRESHOLD = 0.25f; // 滑动阈值：屏幕宽度的25%
    private static final float DRAG_THRESHOLD = 8f; // 拖拽阈值：8dp
    private static final float VERTICAL_SWIPE_THRESHOLD = 1.5f; // 垂直滑动阈值比例

    public interface OnSwipeListener {
        void onSwiped(SwipeResult result, int index);
    }

    public enum SwipeResult {
        ACCEPTED, REJECTED
    }

    public DraggableCardView(Context context) {
        super(context);
        init();
    }

    public DraggableCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DraggableCardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        swipeXLeft = -(screenWidth * 3.2f);
        swipeXRight = screenWidth * 3.2f;
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenWidth = w;
        swipeXLeft = -(screenWidth * 3.2f);
        swipeXRight = screenWidth * 3.2f;
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        this.onSwipeListener = listener;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    public void addCardView(CardView cardView) {
        addView(cardView);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int left = (getWidth() - childWidth) / 2;
                int top = (getHeight() - childHeight) / 2;
                child.layout(left, top, left + childWidth, top + childHeight);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return getChildCount() > 0;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (getChildCount() == 0) {
            return false;
        }

        View topCard = getChildAt(getChildCount() - 1);
        if (topCard == null) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                initialTouchX = event.getX();
                initialTouchY = event.getY();
                isDragging = false;
                // 重置滑动值
                swipeX = 0f;
                swipeY = 0f;
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getX() - lastTouchX;
                float deltaY = event.getY() - lastTouchY;
                float totalDeltaX = event.getX() - initialTouchX;
                float totalDeltaY = event.getY() - initialTouchY;
                
                // 判断是否为水平滑动（水平滑动距离大于垂直滑动距离的1.5倍）
                boolean isHorizontalSwipe = Math.abs(totalDeltaX) > Math.abs(totalDeltaY) * VERTICAL_SWIPE_THRESHOLD;
                
                // 如果还没有开始拖拽，检查是否超过阈值
                if (!isDragging) {
                    float dragDistance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    if (dragDistance > DRAG_THRESHOLD && isHorizontalSwipe) {
                        isDragging = true;
                        // 请求父视图不要拦截触摸事件
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                if (isDragging && isHorizontalSwipe) {
                    swipeX += deltaX;
                    swipeY += deltaY * 0.3f; // 减少垂直滑动的影响
                    
                    // Clamp values
                    swipeX = Math.max(swipeXLeft, Math.min(swipeXRight, swipeX));
                    
                    updateCardTransform(topCard);
                }
                
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    handleSwipeEnd(topCard);
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                isDragging = false;
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void updateCardTransform(View card) {
        // 计算旋转角度，基于滑动距离
        float rotationFraction = (swipeX / 60f);
        rotationFraction = Math.max(-40f, Math.min(40f, rotationFraction));
        
        // 计算缩放比例，滑动时稍微缩小
        float scale = 1f - Math.abs(swipeX) / (screenWidth * 2f);
        scale = Math.max(0.9f, Math.min(1f, scale));
        
        card.setTranslationX(swipeX);
        card.setTranslationY(swipeY);
        card.setRotation(rotationFraction);
        card.setScaleX(scale);
        card.setScaleY(scale);
    }

    private void handleSwipeEnd(View card) {
        // 使用屏幕宽度的百分比作为阈值，使滑动更灵敏
        float threshold = screenWidth * SWIPE_THRESHOLD;
        
        if (Math.abs(swipeX) < threshold) {
            // Snap back - 卡片回到原位置
            animateCardToPosition(card, 0f, 0f, 0f, () -> {
                swipeX = 0f;
                swipeY = 0f;
            });
        } else {
            // Swipe out - 卡片滑出屏幕
            float targetX = swipeX > 0 ? swipeXRight : swipeXLeft;
            float targetRotation = swipeX > 0 ? 40f : -40f;
            animateCardToPosition(card, targetX, swipeY, targetRotation, () -> {
                if (onSwipeListener != null) {
                    SwipeResult result = swipeX > 0 ? SwipeResult.ACCEPTED : SwipeResult.REJECTED;
                    onSwipeListener.onSwiped(result, currentIndex);
                }
                removeView(card);
                swipeX = 0f;
                swipeY = 0f;
                
                // Update current index to next card
                if (getChildCount() > 0) {
                    currentIndex = Math.max(0, currentIndex - 1);
                    // Reset transform for next card
                    View nextCard = getChildAt(getChildCount() - 1);
                    if (nextCard != null) {
                        nextCard.setTranslationX(0f);
                        nextCard.setTranslationY(0f);
                        nextCard.setRotation(0f);
                        nextCard.setScaleX(1f);
                        nextCard.setScaleY(1f);
                    }
                }
            });
        }
    }

    private void animateCardToPosition(View card, float targetX, float targetY, float targetRotation, Runnable onEnd) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        // 根据距离调整动画时长，使动画更流畅
        float distance = (float) Math.sqrt(
            (targetX - card.getTranslationX()) * (targetX - card.getTranslationX()) +
            (targetY - card.getTranslationY()) * (targetY - card.getTranslationY())
        );
        int duration = (int) Math.min(400, Math.max(200, distance / 5));
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        
        float startX = card.getTranslationX();
        float startY = card.getTranslationY();
        float startRotation = card.getRotation();
        float startScaleX = card.getScaleX();
        float startScaleY = card.getScaleY();
        float targetScale = targetX == 0f ? 1f : 0.9f; // 回到原位置时恢复缩放
        
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            float currentX = startX + (targetX - startX) * fraction;
            float currentY = startY + (targetY - startY) * fraction;
            float currentRotation = startRotation + (targetRotation - startRotation) * fraction;
            float currentScaleX = startScaleX + (targetScale - startScaleX) * fraction;
            float currentScaleY = startScaleY + (targetScale - startScaleY) * fraction;
            
            card.setTranslationX(currentX);
            card.setTranslationY(currentY);
            card.setRotation(currentRotation);
            card.setScaleX(currentScaleX);
            card.setScaleY(currentScaleY);
        });
        
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (onEnd != null) {
                    onEnd.run();
                }
            }
        });
        
        animator.start();
    }
}

