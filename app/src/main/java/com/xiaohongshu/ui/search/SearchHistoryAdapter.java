package com.xiaohongshu.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import java.util.ArrayList;
import java.util.List;

public class SearchHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HISTORY = 0;
    private static final int TYPE_EXPAND = 1;
    
    private List<String> historyList = new ArrayList<>();
    private OnItemClickListener listener;
    private OnExpandClickListener expandListener;
    private boolean showExpandButton = false;
    private int expandButtonPosition = -1;

    public interface OnItemClickListener {
        void onItemClick(String keyword);
    }

    public interface OnExpandClickListener {
        void onExpandClick();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnExpandClickListener(OnExpandClickListener listener) {
        this.expandListener = listener;
    }

    public void updateData(List<String> list, boolean showExpand, int expandPos) {
        if (list == null) {
            list = new ArrayList<>();
        }
        
        int oldSize = getItemCount();
        this.historyList = list;
        this.showExpandButton = showExpand;
        this.expandButtonPosition = expandPos;
        int newSize = getItemCount();
        
        // 使用增量更新提升性能
        if (oldSize == 0) {
            // 首次加载
            notifyItemRangeInserted(0, newSize);
        } else if (newSize == 0) {
            // 清空列表
            notifyItemRangeRemoved(0, oldSize);
        } else if (oldSize == newSize) {
            // 大小相同，更新所有项
            notifyItemRangeChanged(0, newSize);
        } else if (newSize > oldSize) {
            // 新增了数据
            notifyItemRangeChanged(0, oldSize);
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        } else {
            // 删除了数据
            notifyItemRangeChanged(0, newSize);
            notifyItemRangeRemoved(newSize, oldSize - newSize);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (showExpandButton && position == expandButtonPosition) {
            return TYPE_EXPAND;
        }
        return TYPE_HISTORY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_EXPAND) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history_expand, parent, false);
            return new ExpandViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history, parent, false);
            return new ViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ExpandViewHolder) {
            ExpandViewHolder expandHolder = (ExpandViewHolder) holder;
            expandHolder.itemView.setOnClickListener(v -> {
                if (expandListener != null) {
                    expandListener.onExpandClick();
                }
            });
        } else if (holder instanceof ViewHolder) {
            int actualPosition = position;
            // If expand button exists and current position is after it, adjust index
            if (showExpandButton && position > expandButtonPosition) {
                actualPosition = position - 1;
            }
            // Make sure we don't go out of bounds
            if (actualPosition >= 0 && actualPosition < historyList.size()) {
                String keyword = historyList.get(actualPosition);
                ViewHolder viewHolder = (ViewHolder) holder;
                viewHolder.textView.setText(keyword);
                viewHolder.itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onItemClick(keyword);
                    }
                });
            } else {
                // Hide view if position is invalid
                holder.itemView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        int count = historyList.size();
        if (showExpandButton) {
            count += 1; // Add expand button
        }
        return count;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }

    static class ExpandViewHolder extends RecyclerView.ViewHolder {
        ImageView expandIcon;

        ExpandViewHolder(@NonNull View itemView) {
            super(itemView);
            expandIcon = itemView.findViewById(R.id.expandIcon);
        }
    }
}

