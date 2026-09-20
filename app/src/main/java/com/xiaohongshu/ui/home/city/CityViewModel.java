package com.xiaohongshu.ui.home.city;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.xiaohongshu.ui.home.HomeDataRepository;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * City ViewModel
 */
public class CityViewModel extends ViewModel {
    private final MutableLiveData<List<GraphicCardBean>> cityGraphicCardList = new MutableLiveData<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final HomeDataRepository repository = HomeDataRepository.getInstance();

    public LiveData<List<GraphicCardBean>> getCityGraphicCardList() {
        return cityGraphicCardList;
    }

    public void load() {
        repository.getCityGraphicCardList(false, new HomeDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                cityGraphicCardList.postValue(data);
            }

            @Override
            public void onError(Exception error) {
                // Handle error
            }
        });
    }

    public void reload() {
        repository.getCityGraphicCardList(true, new HomeDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                cityGraphicCardList.postValue(data);
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

