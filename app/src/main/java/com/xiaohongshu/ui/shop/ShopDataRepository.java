package com.xiaohongshu.ui.shop;

import android.content.Context;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.GoodsEntity;
import com.xiaohongshu.ui.shop.bean.GoodsBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 购物数据仓库
 * 从数据库加载商品数据
 */
public class ShopDataRepository {
    private static ShopDataRepository instance;
    private final AppDatabase database;
    private final ExecutorService executorService;
    private final Context context;
    
    private ShopDataRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppApplication.getDatabase();
        this.executorService = Executors.newCachedThreadPool();
    }
    
    public static synchronized ShopDataRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ShopDataRepository(context);
        }
        return instance;
    }
    
    public static synchronized ShopDataRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ShopDataRepository must be initialized with context first");
        }
        return instance;
    }
    
    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(Exception error);
    }
    
    /**
     * 获取商品列表
     */
    public void getGoodsList(DataCallback<List<GoodsBean>> callback) {
        executorService.execute(() -> {
            try {
                // 等待应用启动时的 DatabaseInitializer 完成商品播种（同步栅栏），
                // 替代旧实现的 Thread.sleep + 轮询重试
                com.xiaohongshu.database.DatabaseInitializer.awaitReady(5000);

                List<GoodsEntity> entities = database.goodsDao().getAllGoodsSync();
                android.util.Log.d("ShopDataRepository", "Loaded " + (entities != null ? entities.size() : 0) + " goods from database");
                // 冷启动兜底：确实没有商品时补种一次再查
                if (entities == null || entities.isEmpty()) {
                    initializeGoodsIfNeeded();
                    entities = database.goodsDao().getAllGoodsSync();
                }

                // 确保entities不为null
                if (entities == null) {
                    entities = new ArrayList<>();
                }

                List<GoodsBean> goodsList = new ArrayList<>();

                android.util.Log.d("ShopDataRepository", "Final entities count: " + entities.size());
                
                if (entities != null) {
                    for (GoodsEntity entity : entities) {
                        // 强制从商品ID中提取图片资源ID，确保所有商品都有正确的图片
                        int imageRes = 0;
                        boolean needUpdate = false;
                        
                        // 优先从商品ID中提取（最可靠的方式）
                        if (entity.id != null && entity.id.startsWith("goods_")) {
                            try {
                                String indexStr = entity.id.substring(6);
                                int index = Integer.parseInt(indexStr);
                                imageRes = getGoodsImageResource(index);
                                // 如果提取成功，且与数据库中的不同，则更新数据库
                                if (imageRes != 0 && entity.image != imageRes) {
                                    needUpdate = true;
                                }
                            } catch (NumberFormatException e) {
                                // 如果无法从ID提取，使用随机分配
                                imageRes = getRandomGoodsImageResource();
                                needUpdate = true;
                            }
                        } else {
                            // 如果ID格式不对，使用随机分配
                            imageRes = getRandomGoodsImageResource();
                            needUpdate = true;
                        }
                        
                        // 如果当前image为0，也需要更新
                        if (entity.image == 0) {
                            needUpdate = true;
                        }
                        
                        // 如果图片资源ID需要更新，更新数据库
                        if (needUpdate && imageRes != 0) {
                            entity.image = imageRes;
                            database.goodsDao().update(entity);
                        }
                        
                        // 确保使用正确的图片资源ID（优先使用提取的，否则使用数据库中的）
                        if (imageRes == 0) {
                            imageRes = entity.image;
                        }
                        
                        // 如果还是0，使用随机分配
                        if (imageRes == 0) {
                            imageRes = getRandomGoodsImageResource();
                            entity.image = imageRes;
                            database.goodsDao().update(entity);
                        }
                        
                        GoodsBean goods = new GoodsBean(
                            entity.id,
                            entity.title,
                            imageRes,
                            entity.price,
                            entity.discount,
                            entity.stock // 使用stock作为购买次数
                        );
                        goodsList.add(goods);
                    }
                }
                
                // 如果goodsList为空或数量不足，手动创建一些商品
                if (goodsList.isEmpty() || goodsList.size() < 20) {
                    android.util.Log.w("ShopDataRepository", "GoodsList too small: " + goodsList.size() + ", manually creating goods");
                    
                    // 手动创建20个商品
                    String[] goodsTitles = {
                        "时尚潮流T恤", "休闲运动鞋", "时尚背包", "智能手表", "蓝牙耳机",
                        "时尚太阳镜", "运动手环", "便携充电宝", "时尚帽子", "休闲短裤",
                        "运动护膝", "时尚围巾", "智能音箱", "无线鼠标", "键盘套装",
                        "显示器支架", "桌面收纳盒", "创意台灯", "保温水杯", "旅行箱"
                    };
                    
                    double[] goodsPrices = {
                        99.0, 299.0, 199.0, 599.0, 199.0,
                        159.0, 199.0, 89.0, 79.0, 129.0,
                        69.0, 89.0, 299.0, 149.0, 299.0,
                        199.0, 59.0, 129.0, 79.0, 399.0
                    };
                    
                    double[] goodsDiscounts = {
                        0.8, 0.85, 0.9, 0.75, 0.8,
                        0.85, 0.8, 0.9, 0.85, 0.8,
                        0.9, 0.85, 0.75, 0.8, 0.85,
                        0.8, 0.9, 0.85, 0.8, 0.75
                    };
                    
                    int[] goodsStock = {
                        100, 50, 80, 30, 60,
                        40, 70, 90, 50, 60,
                        80, 45, 35, 55, 25,
                        40, 70, 50, 85, 20
                    };
                    
                    goodsList.clear();
                    for (int i = 0; i < 20; i++) {
                        String goodsId = "goods_" + (i + 1);
                        int imageRes = getGoodsImageResource(i + 1);
                        
                        GoodsBean goods = new GoodsBean(
                            goodsId,
                            goodsTitles[i],
                            imageRes,
                            goodsPrices[i],
                            goodsDiscounts[i],
                            goodsStock[i] // 使用stock作为购买次数
                        );
                        goodsList.add(goods);
                        
                        // 同时更新数据库
                        GoodsEntity entity = database.goodsDao().getGoodsById(goodsId);
                        if (entity == null) {
                            entity = new com.xiaohongshu.database.entity.GoodsEntity();
                            entity.id = goodsId;
                            entity.title = goodsTitles[i];
                            entity.image = imageRes;
                            entity.price = goodsPrices[i];
                            entity.discount = goodsDiscounts[i];
                            entity.description = goodsTitles[i] + " - 高品质商品";
                            entity.stock = goodsStock[i];
                            entity.createTime = System.currentTimeMillis();
                            database.goodsDao().insert(entity);
                        }
                    }
                }
                
                android.util.Log.d("ShopDataRepository", "Returning " + goodsList.size() + " goods to callback");
                callback.onSuccess(goodsList);
            } catch (Exception e) {
                android.util.Log.e("ShopDataRepository", "Error getting goods list: " + e.getMessage(), e);
                callback.onError(e);
            }
        });
    }
    
    /**
     * 如果需要，初始化商品数据
     */
    private void initializeGoodsIfNeeded() {
        // 调用DatabaseInitializer来初始化商品
        com.xiaohongshu.database.DatabaseInitializer initializer = 
            new com.xiaohongshu.database.DatabaseInitializer(context);
        initializer.initialize();
    }
    
    /**
     * 根据索引获取商品图片资源ID
     */
    private int getGoodsImageResource(int index) {
        switch (index) {
            case 1: return com.xiaohongshu.R.drawable.goods_1;
            case 2: return com.xiaohongshu.R.drawable.goods_2;
            case 3: return com.xiaohongshu.R.drawable.goods_3;
            case 4: return com.xiaohongshu.R.drawable.goods_4;
            case 5: return com.xiaohongshu.R.drawable.goods_5;
            case 6: return com.xiaohongshu.R.drawable.goods_6;
            case 7: return com.xiaohongshu.R.drawable.goods_7;
            case 8: return com.xiaohongshu.R.drawable.goods_8;
            case 9: return com.xiaohongshu.R.drawable.goods_9;
            case 10: return com.xiaohongshu.R.drawable.goods_10;
            case 11: return com.xiaohongshu.R.drawable.goods_11;
            case 12: return com.xiaohongshu.R.drawable.goods_12;
            case 13: return com.xiaohongshu.R.drawable.goods_13;
            case 14: return com.xiaohongshu.R.drawable.goods_14;
            case 15: return com.xiaohongshu.R.drawable.goods_15;
            case 16: return com.xiaohongshu.R.drawable.goods_16;
            case 17: return com.xiaohongshu.R.drawable.goods_17;
            case 18: return com.xiaohongshu.R.drawable.goods_18;
            case 19: return com.xiaohongshu.R.drawable.goods_19;
            case 20: return com.xiaohongshu.R.drawable.goods_20;
            default: return com.xiaohongshu.R.drawable.goods_1;
        }
    }
    
    /**
     * 根据ID获取商品
     */
    public void getGoodsById(String goodsId, DataCallback<GoodsBean> callback) {
        executorService.execute(() -> {
            try {
                GoodsEntity entity = database.goodsDao().getGoodsById(goodsId);
                if (entity != null) {
                    // 如果image为0，尝试从商品ID中提取并更新
                    int imageRes = entity.image;
                    if (imageRes == 0 && entity.id != null && entity.id.startsWith("goods_")) {
                        try {
                            String indexStr = entity.id.substring(6);
                            int index = Integer.parseInt(indexStr);
                            imageRes = getGoodsImageResource(index);
                            // 更新数据库中的图片资源ID
                            entity.image = imageRes;
                            database.goodsDao().update(entity);
                        } catch (NumberFormatException e) {
                            // 如果无法从ID提取，随机分配一个图片
                            imageRes = getRandomGoodsImageResource();
                            entity.image = imageRes;
                            database.goodsDao().update(entity);
                        }
                    } else if (imageRes == 0) {
                        // 如果image为0且无法从ID提取，随机分配一个图片
                        imageRes = getRandomGoodsImageResource();
                        entity.image = imageRes;
                        database.goodsDao().update(entity);
                    }
                    
                    GoodsBean goods = new GoodsBean(
                        entity.id,
                        entity.title,
                        imageRes,
                        entity.price,
                        entity.discount,
                        entity.stock
                    );
                    callback.onSuccess(goods);
                } else {
                    callback.onError(new Exception("商品不存在"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
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
