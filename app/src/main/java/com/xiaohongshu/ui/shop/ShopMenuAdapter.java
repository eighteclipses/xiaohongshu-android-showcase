package com.xiaohongshu.ui.shop;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.shop.bean.MenuBean;

import java.util.List;

/**
 * Shop Menu RecyclerView Adapter
 */
public class ShopMenuAdapter extends RecyclerView.Adapter<ShopMenuAdapter.ViewHolder> {
    private final List<MenuBean> dataList;
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(MenuBean menu);
    }

    public ShopMenuAdapter(List<MenuBean> dataList) {
        this.dataList = dataList;
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shop_menu, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MenuBean menu = dataList.get(position);
        holder.bind(menu, listener);
    }

    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView titleText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.iconView);
            titleText = itemView.findViewById(R.id.titleText);
        }

        public void bind(MenuBean menu, OnItemClickListener listener) {
            iconView.setImageResource(menu.getIcon());
            titleText.setText(menu.getTitle());
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(menu);
                }
            });
        }
    }
}

