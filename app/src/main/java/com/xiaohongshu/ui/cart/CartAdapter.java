package com.xiaohongshu.ui.cart;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.cart.viewmodel.ShoppingCartViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车列表适配器
 */
public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {
    private List<ShoppingCartViewModel.CartItem> dataList;
    private OnQuantityChangeListener quantityListener;
    private OnDeleteListener deleteListener;
    private OnSelectionChangeListener selectionListener;
    
    public interface OnQuantityChangeListener {
        void onQuantityChange(ShoppingCartViewModel.CartItem item, int newQuantity);
    }
    
    public interface OnDeleteListener {
        void onDelete(String goodsId);
    }
    
    public interface OnSelectionChangeListener {
        void onSelectionChange(String goodsId);
    }
    
    public CartAdapter(OnQuantityChangeListener quantityListener, OnDeleteListener deleteListener) {
        this.dataList = new ArrayList<>();
        this.quantityListener = quantityListener;
        this.deleteListener = deleteListener;
    }
    
    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionListener = listener;
    }
    
    public void updateData(List<ShoppingCartViewModel.CartItem> newData) {
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
                .inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShoppingCartViewModel.CartItem item = dataList.get(position);
        holder.bind(item);
    }
    
    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private CheckBox selectCheckBox;
        private ImageView goodsImage;
        private TextView goodsTitle;
        private TextView goodsPrice;
        private TextView quantityText;
        private TextView decreaseButton;
        private TextView increaseButton;
        private ImageView deleteButton;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            selectCheckBox = itemView.findViewById(R.id.selectCheckBox);
            goodsImage = itemView.findViewById(R.id.goodsImage);
            goodsTitle = itemView.findViewById(R.id.goodsTitle);
            goodsPrice = itemView.findViewById(R.id.goodsPrice);
            quantityText = itemView.findViewById(R.id.quantityText);
            decreaseButton = itemView.findViewById(R.id.decreaseButton);
            increaseButton = itemView.findViewById(R.id.increaseButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
        
        public void bind(ShoppingCartViewModel.CartItem item) {
            // 设置复选框状态
            if (selectCheckBox != null) {
                selectCheckBox.setChecked(item.isSelected);
                selectCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    item.isSelected = isChecked;
                    if (selectionListener != null) {
                        selectionListener.onSelectionChange(item.cart.goodsId);
                    }
                });
            }
            
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
                if (goodsPrice != null) {
                    double price = item.goods.price * item.cart.quantity;
                    goodsPrice.setText("¥" + String.format("%.2f", price));
                }
            }
            
            if (quantityText != null) {
                quantityText.setText(String.valueOf(item.cart.quantity));
            }
            
            // 减少数量
            if (decreaseButton != null) {
                decreaseButton.setOnClickListener(v -> {
                    if (item.cart.quantity > 1) {
                        int newQuantity = item.cart.quantity - 1;
                        // 立即更新UI
                        item.cart.quantity = newQuantity;
                        updateQuantityAndPrice(item);
                        if (quantityListener != null) {
                            quantityListener.onQuantityChange(item, newQuantity);
                        }
                    }
                });
            }
            
            // 增加数量
            if (increaseButton != null) {
                increaseButton.setOnClickListener(v -> {
                    int newQuantity = item.cart.quantity + 1;
                    // 立即更新UI
                    item.cart.quantity = newQuantity;
                    updateQuantityAndPrice(item);
                    if (quantityListener != null) {
                        quantityListener.onQuantityChange(item, newQuantity);
                    }
                });
            }
        }
        
        /**
         * 更新数量和价格显示
         */
        private void updateQuantityAndPrice(ShoppingCartViewModel.CartItem item) {
            if (quantityText != null) {
                quantityText.setText(String.valueOf(item.cart.quantity));
            }
            if (goodsPrice != null && item.goods != null) {
                double price = item.goods.price * item.cart.quantity;
                goodsPrice.setText("¥" + String.format("%.2f", price));
            }
            
            // 删除商品
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    if (deleteListener != null) {
                        deleteListener.onDelete(item.cart.goodsId);
                    }
                });
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

