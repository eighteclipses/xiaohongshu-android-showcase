package com.xiaohongshu.ui.order;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.order.viewmodel.OrderListViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单列表适配器
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {
    private List<OrderListViewModel.OrderItem> dataList;
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(OrderListViewModel.OrderItem order);
    }
    
    public OrderAdapter(OnItemClickListener listener) {
        this.dataList = new ArrayList<>();
        this.listener = listener;
    }
    
    public void updateData(List<OrderListViewModel.OrderItem> newData) {
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
                .inflate(R.layout.item_order, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrderListViewModel.OrderItem item = dataList.get(position);
        holder.bind(item);
        
        if (listener != null) {
            holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
    
    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView goodsImage;
        private TextView goodsTitle;
        private TextView orderStatus;
        private TextView orderPrice;
        private TextView orderTime;
        private TextView orderQuantity;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            goodsImage = itemView.findViewById(R.id.goodsImage);
            goodsTitle = itemView.findViewById(R.id.goodsTitle);
            orderStatus = itemView.findViewById(R.id.orderStatusText);
            orderPrice = itemView.findViewById(R.id.orderPriceText);
            orderTime = itemView.findViewById(R.id.orderTimeText);
            orderQuantity = null; // 布局中没有数量显示
        }
        
        public void bind(OrderListViewModel.OrderItem item) {
            if (item.goods != null) {
                if (goodsImage != null) {
                    int imageRes = 0;
                    
                    // 首先尝试使用item.goods.image
                    if (item.goods.image != 0) {
                        imageRes = item.goods.image;
                    } else {
                        // 如果image为0，尝试从商品ID中提取索引来获取图片
                        imageRes = getImageResourceFromGoodsId(item.goods.id);
                        if (imageRes == 0) {
                            // 如果还是无法获取，随机分配一个图片
                            imageRes = getRandomGoodsImageResource();
                        }
                    }
                    
                    if (imageRes != 0) {
                        goodsImage.setImageResource(imageRes);
                    } else {
                        // 最后的备用方案
                        goodsImage.setImageResource(R.drawable.icon_shop);
                    }
                }
                if (goodsTitle != null) {
                    goodsTitle.setText(item.goods.title);
                }
            }
            
            if (orderPrice != null) {
                orderPrice.setText("¥" + String.format("%.2f", item.order.totalPrice));
            }
            
            if (orderStatus != null) {
                String statusText = getStatusText(item.order.status);
                orderStatus.setText(statusText);
                // 根据订单状态设置不同的背景和文字颜色
                int statusBgRes = getStatusBackground(item.order.status);
                int statusTextColorRes = getStatusTextColor(item.order.status);
                if (statusBgRes != 0) {
                    orderStatus.setBackgroundResource(statusBgRes);
                }
                if (statusTextColorRes != 0) {
                    orderStatus.setTextColor(ContextCompat.getColor(orderStatus.getContext(), statusTextColorRes));
                }
            }
            
            if (orderTime != null && item.order != null) {
                // 格式化订单时间
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                String timeStr = sdf.format(new java.util.Date(item.order.createTime));
                orderTime.setText(timeStr);
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
        
        private String getStatusText(int status) {
            switch (status) {
                case 0:
                    return "待付款";
                case 1:
                    return "待发货";
                case 2:
                    return "待收货";
                case 3:
                    return "已完成";
                case 4:
                    return "已取消";
                default:
                    return "未知";
            }
        }
        
        /**
         * 根据订单状态获取对应的背景资源
         */
        private int getStatusBackground(int status) {
            switch (status) {
                case 0: // 待付款
                    return R.drawable.bg_order_status_pending_payment;
                case 1: // 待发货
                    return R.drawable.bg_order_status_pending_shipment;
                case 2: // 待收货
                    return R.drawable.bg_order_status_pending_receipt;
                case 3: // 已完成
                    return R.drawable.bg_order_status_completed;
                case 4: // 已取消
                    return R.drawable.bg_order_status_cancelled;
                default:
                    return R.drawable.bg_order_status_cancelled;
            }
        }
        
        /**
         * 根据订单状态获取对应的文字颜色资源
         */
        private int getStatusTextColor(int status) {
            switch (status) {
                case 0: // 待付款 - 红色
                    return R.color.xhs_red;
                case 1: // 待发货 - 橙色
                    return R.color.status_orange;
                case 2: // 待收货 - 蓝色
                    return R.color.status_blue;
                case 3: // 已完成 - 绿色
                    return R.color.status_green;
                case 4: // 已取消 - 灰色
                    return R.color.text_secondary;
                default:
                    return R.color.text_secondary;
            }
        }
    }
}

