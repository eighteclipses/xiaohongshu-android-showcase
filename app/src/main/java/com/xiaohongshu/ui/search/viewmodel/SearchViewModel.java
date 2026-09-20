package com.xiaohongshu.ui.search.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.xiaohongshu.ui.search.SearchDataRepository;
import com.xiaohongshu.ui.search.bean.HotspotBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search ViewModel
 * 热点/建议/历史均来自 SearchDataRepository（真实数据 + SP 持久化），
 * 数据库与 SP 读取统一切到后台线程。
 */
public class SearchViewModel extends ViewModel {
    private final MutableLiveData<List<HotspotBean>> hotspotList = new MutableLiveData<>();
    private final MutableLiveData<List<String>> historyList = new MutableLiveData<>();
    private final MutableLiveData<List<String>> suggestionList = new MutableLiveData<>();
    private final SearchDataRepository repository = SearchDataRepository.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LiveData<List<HotspotBean>> getHotspotList() {
        return hotspotList;
    }

    public LiveData<List<String>> getHistoryList() {
        return historyList;
    }

    public LiveData<List<String>> getSuggestionList() {
        return suggestionList;
    }

    public void load() {
        executor.execute(() -> {
            hotspotList.postValue(repository.getHotspotList());
            suggestionList.postValue(repository.getSuggestionList());
            historyList.postValue(repository.getHistory());
        });
    }

    /**
     * 输入过程触发联想词：按 prefix 模糊匹配本地笔记标题与历史搜索；
     * prefix 为空时回退到默认"猜你想搜"。
     * debounce 由调用方控制（避免每次按键都查库）。
     */
    public void loadSuggestionsByPrefix(String prefix) {
        executor.execute(() -> {
            List<String> list;
            if (prefix == null || prefix.trim().length() < 1) {
                list = repository.getSuggestionList();
            } else {
                list = repository.getSuggestionsByPrefix(prefix);
                // 联想为空时退回默认建议，避免输入框下方突然空白
                if (list == null || list.isEmpty()) {
                    list = repository.getSuggestionList();
                }
            }
            suggestionList.postValue(list);
        });
    }

    public void clearHistory() {
        executor.execute(() -> {
            repository.clearHistory();
            historyList.postValue(new ArrayList<>());
        });
    }

    public void addToHistory(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return;
        executor.execute(() -> historyList.postValue(repository.addToHistory(keyword.trim())));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
