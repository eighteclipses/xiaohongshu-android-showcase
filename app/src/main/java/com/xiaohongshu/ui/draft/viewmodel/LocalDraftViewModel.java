package com.xiaohongshu.ui.draft.viewmodel;

import android.app.Application;
import android.content.Context;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.ui.publish.model.NoteModel;
import com.xiaohongshu.ui.publish.repository.NoteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本地草稿ViewModel
 */
public class LocalDraftViewModel extends AndroidViewModel {
    private MutableLiveData<List<NoteModel>> drafts;
    private final MutableLiveData<String> operationResult=new MutableLiveData<>();
    public MutableLiveData<String> getOperationResult(){return operationResult;}
    private NoteRepository repository;
    private ExecutorService executorService;
    
    public LocalDraftViewModel(Application application) {
        super(application);
        drafts = new MutableLiveData<>(new ArrayList<>());
        executorService = Executors.newSingleThreadExecutor();
    }
    
    public void init(Context context) {
        repository = NoteRepository.getInstance(context);
    }
    
    public MutableLiveData<List<NoteModel>> getDrafts() {
        return drafts;
    }
    
    public void loadDrafts() {
        executorService.execute(() -> {
            if (repository != null) {
                List<NoteModel> draftList = repository.getDrafts();
                drafts.postValue(draftList != null ? draftList : new ArrayList<>());
            }
        });
    }
    
    public void deleteDraft(String draftId) {
        executorService.execute(() -> {
            if (repository != null && draftId != null) {
                try { repository.deleteDraft(draftId);operationResult.postValue("已移入回收站，可在网页创作者中心恢复");loadDrafts(); }
                catch(Exception error){operationResult.postValue("操作失败："+error.getMessage());}
            }
        });
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
