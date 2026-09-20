package com.xiaohongshu.ui.home.composable;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.util.AnimationHelper;

public class ShimmerLayout extends RecyclerView {
    private ValueAnimator translateAnimator;

    public ShimmerLayout(Context context) {
        super(context);
        init();
    }

    public ShimmerLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ShimmerLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayoutManager(new GridLayoutManager(getContext(), 2));
        setAdapter(new ShimmerAdapter());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startShimmerAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopShimmerAnimation();
    }

    private void startShimmerAnimation() {
        translateAnimator = AnimationHelper.createInfiniteAnimator(0f, 1000f, 1000, animation -> {
            Float value = (Float) animation.getAnimatedValue();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof ShimmerItemView) {
                    ((ShimmerItemView) child).setTranslateValue(value);
                }
            }
        });
        translateAnimator.start();
    }

    private void stopShimmerAnimation() {
        if (translateAnimator != null) {
            translateAnimator.cancel();
            translateAnimator = null;
        }
    }

    private class ShimmerAdapter extends RecyclerView.Adapter<ShimmerAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = new ShimmerItemView(parent.getContext());
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            // Shimmer item view handles its own drawing
        }

        @Override
        public int getItemCount() {
            return 6;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    private class ShimmerItemView extends View {
        private final Paint paint = new Paint();
        private float translateValue = 0;

        public ShimmerItemView(Context context) {
            super(context);
            paint.setAntiAlias(true);
        }

        public void setTranslateValue(float value) {
            this.translateValue = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int[] colors = {
                    getResources().getColor(R.color.shimmer_light, null),
                    getResources().getColor(R.color.shimmer_medium, null),
                    getResources().getColor(R.color.shimmer_dark, null)
            };

            LinearGradient gradient = new LinearGradient(
                    0, 0, translateValue, translateValue,
                    colors, null, Shader.TileMode.CLAMP
            );
            paint.setShader(gradient);

            // Draw shimmer rectangles
            float width = getWidth();
            
            // Image placeholder
            canvas.drawRect(0, 0, width, width, paint);
            
            // Title placeholder
            canvas.drawRect(0, width + 6, width, width + 26, paint);
            
            // User info placeholder
            canvas.drawRect(0, width + 32, width * 0.6f, width + 48, paint);
        }
    }
}

