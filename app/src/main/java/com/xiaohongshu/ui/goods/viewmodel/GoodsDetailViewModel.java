package com.xiaohongshu.ui.goods.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.ui.shop.ShopDataRepository;
import com.xiaohongshu.ui.shop.bean.GoodsBean;

/**
 * 商品详情ViewModel
 */
public class GoodsDetailViewModel extends AndroidViewModel {
    private String goodsId = "";
    private final MutableLiveData<GoodsBean> goods = new MutableLiveData<>();
    private ShopDataRepository repository;
    
    public GoodsDetailViewModel(Application application) {
        super(application);
    }
    
    public void init(android.content.Context context) {
        repository = ShopDataRepository.getInstance(context);
    }
    
    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }
    
    public LiveData<GoodsBean> getGoods() {
        return goods;
    }
    
    public void load() {
        if (repository == null || goodsId == null || goodsId.isEmpty()) {
            return;
        }
        
        repository.getGoodsById(goodsId, new ShopDataRepository.DataCallback<GoodsBean>() {
            @Override
            public void onSuccess(GoodsBean data) {
                goods.postValue(data);
            }
            
            @Override
            public void onError(Exception error) {
                // Handle error
            }
        });
    }
}

