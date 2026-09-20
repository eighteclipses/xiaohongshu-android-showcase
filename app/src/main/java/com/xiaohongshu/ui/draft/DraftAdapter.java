package com.xiaohongshu.ui.draft;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.publish.model.NoteModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 草稿列表适配器
 */
public class DraftAdapter extends RecyclerView.Adapter<DraftAdapter.ViewHolder> {
    private List<NoteModel> dataList;
    private OnItemClickListener itemClickListener;
    private OnDeleteClickListener deleteClickListener;
    
    public interface OnItemClickListener {
        void onItemClick(NoteModel draft);
    }
    
    public interface OnDeleteClickListener {
        void onDelete(String draftId);
    }
    
    public DraftAdapter(OnItemClickListener itemClickListener, OnDeleteClickListener deleteClickListener) {
        this.dataList = new ArrayList<>();
        this.itemClickListener = itemClickListener;
        this.deleteClickListener = deleteClickListener;
    }
    
    public void updateData(List<NoteModel> newData) {
        if (newData == null) {
            newData = new ArrayList<>();
        }
        
        int oldSize = dataList != null ? dataList.size() : 0;
        int newSize = newData.size();
        
        this.dataList = newData;
        
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
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_draft, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NoteModel draft = dataList.get(position);
        holder.bind(draft);
    }
    
    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private View colorBlock;
        private ImageView previewImage;
        private LinearLayout textPreviewLayout;
        private TextView textPreviewNumber;
        private TextView timeText;
        private ImageView deleteButton;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            colorBlock = itemView.findViewById(R.id.colorBlock);
            previewImage = itemView.findViewById(R.id.previewImage);
            textPreviewLayout = itemView.findViewById(R.id.textPreviewLayout);
            textPreviewNumber = itemView.findViewById(R.id.textPreviewNumber);
            timeText = itemView.findViewById(R.id.timeText);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
        
        public void bind(NoteModel draft) {
            // 显示时间戳
            if (timeText != null) {
                timeText.setText(formatTime(draft.getUpdateTime()));
            }
            
            // 判断草稿类型：有图片显示图片，有文字显示文字预览
            if (draft.getImageUris() != null && !draft.getImageUris().isEmpty()) {
                // 有图片，显示图片预览
                if (colorBlock != null) colorBlock.setVisibility(View.VISIBLE);
                if (previewImage != null) {
                    previewImage.setVisibility(View.VISIBLE);
                    try {
                        previewImage.setImageURI(Uri.parse(draft.getImageUris().get(0)));
                    } catch (Exception e) {
                        previewImage.setImageResource(R.drawable.icon_picture);
                    }
                }
                if (textPreviewLayout != null) textPreviewLayout.setVisibility(View.GONE);
            } else if (draft.getContent() != null && !draft.getContent().isEmpty()) {
                // 有文字，显示文字预览
                if (colorBlock != null) colorBlock.setVisibility(View.VISIBLE);
                if (previewImage != null) previewImage.setVisibility(View.GONE);
                if (textPreviewLayout != null) {
                    textPreviewLayout.setVisibility(View.VISIBLE);
                    // 显示内容长度或前几个字符
                    if (textPreviewNumber != null) {
                        int length = draft.getContent().length();
                        textPreviewNumber.setText(String.valueOf(length));
                    }
                }
            } else {
                // 只有颜色块
                if (colorBlock != null) colorBlock.setVisibility(View.VISIBLE);
                if (previewImage != null) previewImage.setVisibility(View.GONE);
                if (textPreviewLayout != null) textPreviewLayout.setVisibility(View.GONE);
            }
            
            // 点击草稿
            itemView.setOnClickListener(v -> {
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(draft);
                }
            });
            
            // 删除按钮
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    if (deleteClickListener != null) {
                        deleteClickListener.onDelete(draft.getId());
                    }
                });
            }
        }
        
        private String formatTime(long timestamp) {
            Date date = new Date(timestamp);
            Date now = new Date();
            long diff = now.getTime() - date.getTime();
            
            // 今天
            if (diff < 24 * 60 * 60 * 1000) {
                SimpleDateFormat sdf = new SimpleDateFormat("今天 HH:mm", Locale.getDefault());
                return sdf.format(date);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                return sdf.format(date);
            }
        }
    }
}
