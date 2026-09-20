package com.xiaohongshu.ui.shop.viewmodel;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.shop.ShopDataRepository;
import com.xiaohongshu.ui.shop.bean.GoodsBean;
import com.xiaohongshu.ui.shop.bean.MenuBean;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shop ViewModel
 */
public class ShopViewModel extends ViewModel {
    private final List<MenuBean> menuList = Arrays.asList(
            new MenuBean(R.drawable.icon_order, "我的订单"),
            new MenuBean(R.drawable.icon_shopping_car, "购物车"),
            new MenuBean(R.drawable.icon_pinglun, "客服消息"),
            new MenuBean(R.drawable.icon_kaquan, "卡券"),
            new MenuBean(R.drawable.icon_clock, "浏览记录"),
            new MenuBean(R.drawable.icon_dianpu, "关注店铺"),
            new MenuBean(R.drawable.icon_xinyuan, "心愿单")
    );
    
    private final MutableLiveData<List<GoodsBean>> goodsList = new MutableLiveData<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ShopDataRepository repository;
    
    public void init(Context context) {
        repository = ShopDataRepository.getInstance(context);
    }

    public List<MenuBean> getMenuList() {
        return menuList;
    }

    public LiveData<List<GoodsBean>> getGoodsList() {
        return goodsList;
    }

    public void load() {
        if (repository == null) {
            return;
        }
        repository.getGoodsList(new ShopDataRepository.DataCallback<List<GoodsBean>>() {
            @Override
            public void onSuccess(List<GoodsBean> data) {
                goodsList.postValue(data);
            }

            @Override
            public void onError(Exception error) {
                // Handle error
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

