package com.xiaohongshu.activity.graphic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.util.ImageLoader;
import com.xiaohongshu.util.ZoomableImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记详情图片轮播适配器：支持 http/相对路径/file/android.resource 全部 URI 形态。
 */
public class DetailImageAdapter extends RecyclerView.Adapter<DetailImageAdapter.ImageViewHolder> {
    public interface OnImageClickListener {
        void onImageClick();
    }

    private final List<String> imageUris = new ArrayList<>();
    private final int placeholder;
    private OnImageClickListener clickListener;
    /** 非全屏模式下双击图片（对齐真实小红书的双击点赞） */
    private Runnable doubleTapAction;
    /** 全屏预览模式：占满容器高度，不做列表高度的 wrap 适配 */
    private boolean fullScreenMode = false;

    public DetailImageAdapter(int placeholder) {
        this.placeholder = placeholder;
    }

    public void setFullScreenMode(boolean fullScreenMode) {
        this.fullScreenMode = fullScreenMode;
    }

    public void setOnImageClickListener(OnImageClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnImageDoubleTapListener(Runnable action) {
        this.doubleTapAction = action;
    }

    public void submit(List<String> uris) {
        imageUris.clear();
        if (uris != null) imageUris.addAll(uris);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String uri = imageUris.get(position);
        ImageLoader.load((ImageView) holder.itemView, uri, placeholder);
        if (holder.itemView instanceof ZoomableImageView) {
            ZoomableImageView zoomable = (ZoomableImageView) holder.itemView;
            // 全屏预览双击缩放；详情轮播双击点赞
            zoomable.setDoubleTapAction(!fullScreenMode && doubleTapAction != null ? doubleTapAction : null);
        }
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onImageClick();
        });
    }

    @Override
    public int getItemCount() {
        return imageUris.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
