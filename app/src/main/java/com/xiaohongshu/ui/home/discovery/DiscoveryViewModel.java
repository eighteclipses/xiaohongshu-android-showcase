package com.xiaohongshu.ui.home.discovery;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.xiaohongshu.ui.home.HomeDataRepository;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discovery ViewModel
 */
public class DiscoveryViewModel extends ViewModel {
    private final MutableLiveData<List<GraphicCardBean>> graphicCardList = new MutableLiveData<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final HomeDataRepository repository = HomeDataRepository.getInstance();

    public LiveData<List<GraphicCardBean>> getGraphicCardList() {
        return graphicCardList;
    }

    public void load() {
        repository.getGraphicCardList(false, new HomeDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                graphicCardList.postValue(data);
            }

            @Override
            public void onError(Exception error) {
                // Handle error
            }
        });
    }

    public void reload() {
        repository.getGraphicCardList(true, new HomeDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                graphicCardList.postValue(data);
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

