package com.xiaohongshu.ui.message.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.ui.message.MessageDataRepository;
import com.xiaohongshu.ui.message.bean.MessageBean;
import com.xiaohongshu.ui.message.bean.RecommendFriendBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 消息ViewModel
 */
public class MessageViewModel extends AndroidViewModel {
    private final MutableLiveData<List<MessageBean>> messageList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<RecommendFriendBean>> recommendFriendList = new MutableLiveData<>(new ArrayList<>());
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private MessageDataRepository repository;
    
    public MessageViewModel(Application application) {
        super(application);
    }
    
    public void init(android.content.Context context) {
        repository = MessageDataRepository.getInstance(context);
    }
    
    public LiveData<List<MessageBean>> getMessageList() {
        return messageList;
    }
    
    public LiveData<List<RecommendFriendBean>> getRecommendFriendList() {
        return recommendFriendList;
    }
    
    public void load() {
        if (repository == null) {
            return;
        }
        
        repository.getMessageList(new MessageDataRepository.DataCallback<List<MessageBean>>() {
            @Override
            public void onSuccess(List<MessageBean> data) {
                messageList.postValue(data);
            }
            
            @Override
            public void onError(Exception error) {
                messageList.postValue(new ArrayList<>());
            }
        });
        
        // 加载推荐好友
        repository.getRecommendFriendList(new MessageDataRepository.DataCallback<List<RecommendFriendBean>>() {
            @Override
            public void onSuccess(List<RecommendFriendBean> data) {
                recommendFriendList.postValue(data);
            }
            
            @Override
            public void onError(Exception error) {
                recommendFriendList.postValue(new ArrayList<>());
            }
        });
    }
    
    /**
     * 根据类型加载消息
     */
    public void loadMessagesByType(int type) {
        loadMessagesByTypes(java.util.Collections.singletonList(type));
    }

    /**
     * 根据多个类型加载消息，如赞与收藏 [0, 1]、评论与@ [2, 4]
     */
    public void loadMessagesByTypes(List<Integer> types) {
        if (repository == null) {
            return;
        }

        repository.getMessagesByTypes(types, new MessageDataRepository.DataCallback<List<MessageBean>>() {
            @Override
            public void onSuccess(List<MessageBean> data) {
                messageList.postValue(data);
            }

            @Override
            public void onError(Exception error) {
                messageList.postValue(new ArrayList<>());
            }
        });
    }
    
    /**
     * 标记所有消息为已读
     */
    public void markAllAsRead() {
        if (repository != null) {
            repository.markAllAsRead();
        }
    }

    /**
     * 从推荐列表中移除好友
     */
    public void removeRecommendFriend(com.xiaohongshu.ui.message.bean.RecommendFriendBean friend) {
        if (repository != null) {
            repository.removeRecommendFriend(friend);
            // 重新加载推荐列表
            load();
        }
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
