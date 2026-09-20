package com.xiaohongshu.ui.search;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public class FlowLayoutManager extends RecyclerView.LayoutManager {
    private int horizontalSpacing;
    private int verticalSpacing;
    private Context context;
    private int totalHeight = 0;

    public FlowLayoutManager(Context context) {
        this.context = context;
        this.horizontalSpacing = (int) (8 * context.getResources().getDisplayMetrics().density);
        this.verticalSpacing = (int) (8 * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        );
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getItemCount() == 0) {
            detachAndScrapAttachedViews(recycler);
            return;
        }

        if (state.isPreLayout()) {
            return;
        }

        detachAndScrapAttachedViews(recycler);

        int width = getWidth();
        
        // If RecyclerView hasn't been measured yet, use screen width as fallback
        if (width <= 0) {
            if (context != null) {
                width = context.getResources().getDisplayMetrics().widthPixels;
            } else {
                // If no context, try to get from first view
                if (getChildCount() > 0) {
                    View firstChild = getChildAt(0);
                    if (firstChild != null) {
                        width = firstChild.getContext().getResources().getDisplayMetrics().widthPixels;
                    }
                }
            }
        }
        
        // Use a reasonable default if still no width
        if (width <= 0) {
            width = 1080; // Default width in pixels
        }
        
        int availableWidth = width - getPaddingLeft() - getPaddingRight();
        if (availableWidth <= 0) {
            availableWidth = width;
        }
        
        int currentX = getPaddingLeft();
        int currentY = getPaddingTop();
        int maxHeight = 0;

        for (int i = 0; i < getItemCount(); i++) {
            View view = recycler.getViewForPosition(i);
            if (view == null) {
                // If view is null, try to get it from recycler
                view = recycler.getViewForPosition(i);
                if (view == null) continue;
            }
            
            addView(view);
            measureChildWithMargins(view, 0, 0);

            int viewWidth = getDecoratedMeasuredWidth(view);
            int viewHeight = getDecoratedMeasuredHeight(view);
            
            // Ensure minimum width
            if (viewWidth <= 0) {
                viewWidth = (int) (100 * context.getResources().getDisplayMetrics().density);
            }
            if (viewHeight <= 0) {
                viewHeight = (int) (40 * context.getResources().getDisplayMetrics().density);
            }

            // Check if we need to wrap to next line
            if (currentX + viewWidth > availableWidth + getPaddingLeft() && currentX > getPaddingLeft()) {
                // Move to next line
                currentX = getPaddingLeft();
                currentY += maxHeight + verticalSpacing;
                maxHeight = 0;
            }

            layoutDecorated(view, currentX, currentY, currentX + viewWidth, currentY + viewHeight);
            currentX += viewWidth + horizontalSpacing;
            maxHeight = Math.max(maxHeight, viewHeight);
        }
        
        // Calculate total height
        // currentY starts at getPaddingTop(), so we need to add maxHeight for the last row
        totalHeight = currentY + maxHeight + getPaddingBottom();
        
        // Request layout if height changed significantly
        if (Math.abs(getHeight() - totalHeight) > 1) {
            requestLayout();
        }
    }
    

    @Override
    public boolean canScrollHorizontally() {
        return false;
    }

    @Override
    public boolean canScrollVertically() {
        return false;
    }
    
    @Override
    public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec, int heightSpec) {
        int width = View.MeasureSpec.getSize(widthSpec);
        int height = View.MeasureSpec.getSize(heightSpec);
        
        if (width <= 0 && context != null) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        
        // If height is unspecified or at_most, calculate based on actual layout
        if (View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED || 
            View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.AT_MOST) {
            // Use calculated totalHeight if available
            if (totalHeight > 0) {
                height = totalHeight;
            } else {
                // Estimate based on item count
                int itemCount = getItemCount();
                if (itemCount > 0 && context != null) {
                    // More accurate estimate: measure a sample item
                    int sampleHeight = (int) (50 * context.getResources().getDisplayMetrics().density);
                    // Estimate rows based on width and average item width
                    int avgItemWidth = (int) (120 * context.getResources().getDisplayMetrics().density);
                    int itemsPerRow = Math.max(1, (width - getPaddingLeft() - getPaddingRight()) / avgItemWidth);
                    int estimatedRows = (int) Math.ceil((double) itemCount / itemsPerRow);
                    int estimatedHeight = sampleHeight * estimatedRows + verticalSpacing * (estimatedRows - 1) + 
                        getPaddingTop() + getPaddingBottom();
                    if (height == 0 || height < estimatedHeight) {
                        height = estimatedHeight;
                    }
                }
            }
        }
        
        setMeasuredDimension(width, height);
    }
    
    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return totalHeight > 0 ? totalHeight : getHeight();
    }
    
    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return getHeight();
    }
    
    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        return 0;
    }
}

