package com.xiaohongshu.ui.shop;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.shop.bean.GoodsBean;

import java.util.List;

/**
 * 商品列表适配器（支持Header）
 */
public class ShopGoodsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_GOODS = 1;
    
    private List<GoodsBean> dataList;
    private OnItemClickListener listener;
    private View headerView;
    
    /**
     * 商品点击监听器
     */
    public interface OnItemClickListener {
        void onItemClick(GoodsBean goods);
    }

    public ShopGoodsAdapter() {
        this.dataList = new java.util.ArrayList<>();
    }
    
    public ShopGoodsAdapter(OnItemClickListener listener) {
        this.dataList = new java.util.ArrayList<>();
        this.listener = listener;
    }
    
    public void setHeaderView(View headerView) {
        this.headerView = headerView;
        // 不要在这里调用notifyItemInserted，因为此时adapter可能还没有设置
        // 会在updateData时自动处理
    }

    public void updateData(List<GoodsBean> newData) {
        if (newData == null) {
            newData = new java.util.ArrayList<>();
        }
        
        int oldSize = dataList != null ? dataList.size() : 0;
        int newSize = newData.size();
        
        // 考虑header，计算实际位置偏移
        int headerOffset = headerView != null ? 1 : 0;
        
        this.dataList = newData;
        android.util.Log.d("ShopGoodsAdapter", "updateData: dataList size = " + this.dataList.size() + ", headerView = " + (headerView != null) + ", itemCount will be = " + getItemCount());
        
        // 使用增量更新提升性能
        if (oldSize == 0) {
            // 首次加载
            if (newSize > 0) {
                notifyItemRangeInserted(headerOffset, newSize);
            }
        } else if (newSize == 0) {
            // 清空列表
            notifyItemRangeRemoved(headerOffset, oldSize);
        } else if (oldSize == newSize) {
            // 大小相同，更新所有项
            notifyItemRangeChanged(headerOffset, newSize);
        } else if (newSize > oldSize) {
            // 新增了数据
            notifyItemRangeChanged(headerOffset, oldSize);
            notifyItemRangeInserted(headerOffset + oldSize, newSize - oldSize);
        } else {
            // 删除了数据
            notifyItemRangeChanged(headerOffset, newSize);
            notifyItemRangeRemoved(headerOffset + newSize, oldSize - newSize);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (headerView != null && position == 0) {
            return TYPE_HEADER;
        }
        return TYPE_GOODS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER && headerView != null) {
            // 如果headerView已经有parent，需要先移除
            if (headerView.getParent() != null) {
                ((ViewGroup) headerView.getParent()).removeView(headerView);
            }
            return new HeaderViewHolder(headerView);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_goods, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            return; // Header view已经设置好了
        }
        
        int goodsPosition = headerView != null ? position - 1 : position;
        if (goodsPosition >= 0 && goodsPosition < dataList.size() && dataList.get(goodsPosition) != null) {
            GoodsBean goods = dataList.get(goodsPosition);
            ((ViewHolder) holder).bind(goods);
            
            // 确保view可见
            holder.itemView.setVisibility(android.view.View.VISIBLE);
            
            // 设置点击事件
            if (listener != null) {
                holder.itemView.setOnClickListener(v -> listener.onItemClick(goods));
            }
        } else {
            // 如果位置无效，记录日志并隐藏这个view
            android.util.Log.w("ShopGoodsAdapter", "Invalid position: " + goodsPosition + ", dataList size: " + dataList.size());
            holder.itemView.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        int count = dataList != null ? dataList.size() : 0;
        int totalCount = headerView != null ? count + 1 : count;
        android.util.Log.d("ShopGoodsAdapter", "getItemCount: dataList size = " + count + ", headerView = " + (headerView != null) + ", returning = " + totalCount);
        return totalCount;
    }
    
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        HeaderViewHolder(View itemView) {
            super(itemView);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final TextView titleText;
        private final TextView priceText;
        private final TextView originalPriceText;
        private final TextView timesText;
        private final TextView tagText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            titleText = itemView.findViewById(R.id.titleText);
            priceText = itemView.findViewById(R.id.priceText);
            originalPriceText = itemView.findViewById(R.id.originalPriceText);
            timesText = itemView.findViewById(R.id.timesText);
            tagText = itemView.findViewById(R.id.tagText);
        }

        public void bind(GoodsBean goods) {
            // 设置商品图片
            if (imageView != null) {
                int imageRes = 0;
                
                // 首先尝试使用goods.getImage()
                if (goods.getImage() != 0) {
                    imageRes = goods.getImage();
                }
                
                // 如果image为0或无效，尝试从商品ID中提取索引来获取图片
                if (imageRes == 0) {
                    imageRes = getImageResourceFromGoodsId(goods.getId());
                }
                
                // 如果还是无法获取，随机分配一个图片
                if (imageRes == 0) {
                    imageRes = getRandomGoodsImageResource();
                }
                
                // 确保有有效的图片资源
                if (imageRes != 0) {
                    try {
                        imageView.setImageResource(imageRes);
                    } catch (Exception e) {
                        // 如果资源ID无效，使用默认图标
                        imageView.setImageResource(R.drawable.icon_shop);
                    }
                } else {
                    // 最后的备用方案
                    imageView.setImageResource(R.drawable.icon_shop);
                }
            }
            
            titleText.setText(goods.getTitle());
            priceText.setText("¥" + String.format("%.1f", goods.getPrice()));
            
            if (goods.getDiscount() > 0 && goods.getDiscount() < goods.getPrice()) {
                double originalPrice = goods.getPrice() / goods.getDiscount();
                originalPriceText.setText("¥" + String.format("%.1f", originalPrice));
                originalPriceText.setVisibility(android.view.View.VISIBLE);
            } else {
                originalPriceText.setVisibility(android.view.View.GONE);
            }
            
            if (goods.getTimes() > 0) {
                timesText.setText(goods.getTimes() + itemView.getContext().getString(com.xiaohongshu.R.string.people_bought));
                timesText.setVisibility(android.view.View.VISIBLE);
            } else {
                timesText.setVisibility(android.view.View.GONE);
            }
            
            // Set tag (fake one compensate ten or return shipping included)
            if (goods.getTimes() % 2 == 0) {
                tagText.setText(itemView.getContext().getString(com.xiaohongshu.R.string.fake_one_compensate_ten));
                tagText.setVisibility(android.view.View.VISIBLE);
            } else if (goods.getTimes() % 3 == 0) {
                tagText.setText(itemView.getContext().getString(com.xiaohongshu.R.string.return_shipping_included));
                tagText.setVisibility(android.view.View.VISIBLE);
            } else {
                tagText.setVisibility(android.view.View.GONE);
            }
        }
        
        /**
         * 从商品ID中提取索引并返回对应的图片资源ID
         */
        private int getImageResourceFromGoodsId(String goodsId) {
            if (goodsId == null || goodsId.isEmpty()) {
                return 0;
            }
            try {
                // 商品ID格式通常是 "goods_1", "goods_2" 等
                if (goodsId.startsWith("goods_")) {
                    String indexStr = goodsId.substring(6); // 去掉 "goods_" 前缀
                    int index = Integer.parseInt(indexStr);
                    return getGoodsImageResource(index);
                }
            } catch (NumberFormatException e) {
                // 解析失败，返回0
            }
            return 0;
        }
        
        /**
         * 根据索引获取商品图片资源ID
         */
        private int getGoodsImageResource(int index) {
            switch (index) {
                case 1: return R.drawable.goods_1;
                case 2: return R.drawable.goods_2;
                case 3: return R.drawable.goods_3;
                case 4: return R.drawable.goods_4;
                case 5: return R.drawable.goods_5;
                case 6: return R.drawable.goods_6;
                case 7: return R.drawable.goods_7;
                case 8: return R.drawable.goods_8;
                case 9: return R.drawable.goods_9;
                case 10: return R.drawable.goods_10;
                case 11: return R.drawable.goods_11;
                case 12: return R.drawable.goods_12;
                case 13: return R.drawable.goods_13;
                case 14: return R.drawable.goods_14;
                case 15: return R.drawable.goods_15;
                case 16: return R.drawable.goods_16;
                case 17: return R.drawable.goods_17;
                case 18: return R.drawable.goods_18;
                case 19: return R.drawable.goods_19;
                case 20: return R.drawable.goods_20;
                default: return R.drawable.goods_1;
            }
        }
        
        /**
         * 随机获取一个商品图片资源ID（用于未绑定图片的商品）
         */
        private int getRandomGoodsImageResource() {
            // 随机返回 goods_1 到 goods_20 中的一个
            int randomIndex = (int) (Math.random() * 20) + 1;
            return getGoodsImageResource(randomIndex);
        }
    }
}

