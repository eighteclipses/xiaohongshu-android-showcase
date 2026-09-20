package com.xiaohongshu.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.search.bean.HotspotBean;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class SearchHotspotAdapter extends RecyclerView.Adapter<SearchHotspotAdapter.ViewHolder> {
    private List<HotspotBean> hotspotList = new ArrayList<>();
    private OnItemClickListener listener;
    private final DecimalFormat df = new DecimalFormat("#.#");

    public interface OnItemClickListener {
        void onItemClick(HotspotBean hotspot);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void updateData(List<HotspotBean> list) {
        if (list == null) {
            list = new ArrayList<>();
        }
        // 热点列表较短，直接全量刷新；手动分段通知在快速连续刷新时容易出现
        // inconsistent item count 崩溃，不再使用
        hotspotList = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_search_hotspot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HotspotBean hotspot = hotspotList.get(position);
        int rank = position + 1;
        holder.numberText.setText(String.valueOf(rank));
        holder.titleText.setText(hotspot.getTitle());
        
        // Set color based on rank
        int colorRes;
        if (rank == 1) {
            colorRes = R.color.hotspot_rank_1; // Red
        } else if (rank == 2) {
            colorRes = R.color.hotspot_rank_2; // Orange
        } else if (rank == 3) {
            colorRes = R.color.hotspot_rank_3; // Yellow
        } else {
            colorRes = R.color.hotspot_rank_other; // Gray
        }
        holder.numberText.setTextColor(holder.itemView.getContext().getResources().getColor(colorRes, null));
        
        double heat = hotspot.getHeat();
        String heatText;
        if (heat >= 10000) {
            heatText = df.format(heat / 10000) + "万";
        } else {
            heatText = String.valueOf((int) heat);
        }
        holder.viewsText.setText(heatText);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(hotspot);
            }
        });
    }

    @Override
    public int getItemCount() {
        return hotspotList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView numberText;
        TextView titleText;
        TextView viewsText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            numberText = itemView.findViewById(R.id.hotspotNumber);
            titleText = itemView.findViewById(R.id.hotspotTitle);
            viewsText = itemView.findViewById(R.id.hotspotViews);
        }
    }
}

