package com.xiaohongshu.ui.message;

import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseFragment;
import com.xiaohongshu.ui.message.bean.RecommendFriendBean;
import com.xiaohongshu.ui.message.viewmodel.MessageViewModel;

public class MessageFragment extends BaseFragment {
    private MessageViewModel viewModel;
    private RecyclerView recyclerView;
    private RecyclerView recommendFriendsRecyclerView;
    private MessageAdapter adapter;
    private RecommendFriendAdapter recommendFriendAdapter;
    private View likesAndCollectionsButton;
    private View newFollowsButton;
    private View commentsAndMentionsButton;
    private View closeRecommendButton;
    private View dmSection;
    private RecyclerView dmRecyclerView;
    private DmConversationAdapter dmAdapter;

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_message;
    }

    @Override
    protected void initViews(@NonNull View rootView) {
        recyclerView = rootView.findViewById(R.id.recyclerView);
        recommendFriendsRecyclerView = rootView.findViewById(R.id.recommendFriendsRecyclerView);
        likesAndCollectionsButton = rootView.findViewById(R.id.likesAndCollectionsButton);
        newFollowsButton = rootView.findViewById(R.id.newFollowsButton);
        commentsAndMentionsButton = rootView.findViewById(R.id.commentsAndMentionsButton);
        closeRecommendButton = rootView.findViewById(R.id.closeRecommendButton);

        View messageTopBar = rootView.findViewById(R.id.messageTopBar);
        if (messageTopBar != null) {
            View discoverGroupChatButton = messageTopBar.findViewById(R.id.discoverGroupChatButton);
            if (discoverGroupChatButton != null) {
                discoverGroupChatButton.setOnClickListener(v ->
                        com.xiaohongshu.ui.common.InfoActivity.start(getContext(), "发现群聊", "群聊推荐和加入群聊功能入口。"));
            }
        }

        // 私信会话区块
        dmSection = rootView.findViewById(R.id.dmSection);
        dmRecyclerView = rootView.findViewById(R.id.dmRecyclerView);
        dmRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        dmAdapter = new DmConversationAdapter(user -> {
            if (user != null && user.has("id") && !user.get("id").isJsonNull()) {
                com.xiaohongshu.activity.chat.ChatActivity.start(getContext(), user.get("id").getAsString());
            }
        });
        dmRecyclerView.setAdapter(dmAdapter);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MessageAdapter(message -> {
            // 点击消息项跳转到对应详情
            if (message != null) {
                // 根据消息类型跳转到不同页面
                int messageType = getMessageType(message);
                switch (messageType) {
                    case 5:
                        new android.app.AlertDialog.Builder(getContext()).setTitle("审核结果").setMessage(message.getContent()).setPositiveButton("知道了",null).show();
                        break;
                    case 0: // 点赞
                    case 1: // 收藏
                        // 跳转到笔记详情
                        if (message.getId() != null) {
                            com.xiaohongshu.activity.graphic.GraphicActivity.newInstance(getContext(), message.getId());
                        }
                        break;
                    case 2: // 评论
                    case 4: // @我
                        // 跳转到笔记详情
                        if (message.getId() != null) {
                            com.xiaohongshu.activity.graphic.GraphicActivity.newInstance(getContext(), message.getId());
                        }
                        break;
                    case 3: // 关注
                        // 跳转到用户资料页
                        if (message.getUser() != null && message.getUser().getId() != null) {
                            com.xiaohongshu.ui.profile.UserProfileActivity.start(getContext(), message.getUser().getId());
                        }
                        break;
                }
            }
        });
        recyclerView.setAdapter(adapter);

        recommendFriendsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recommendFriendAdapter = new RecommendFriendAdapter();
        recommendFriendAdapter.setOnItemClickListener(friend -> {
            // 处理关注操作
            if (friend != null && friend.getUser() != null && friend.getUser().getId() != null) {
                handleFollowAction(friend);
            }
        });
        recommendFriendAdapter.setOnDismissListener(friend -> {
            // 从推荐列表中移除
            if (friend != null && viewModel != null) {
                viewModel.removeRecommendFriend(friend);
            }
        });
        recommendFriendsRecyclerView.setAdapter(recommendFriendAdapter);

        if (likesAndCollectionsButton != null) {
            likesAndCollectionsButton.setOnClickListener(v -> {
                // 跳转到点赞和收藏消息页
                com.xiaohongshu.ui.message.LikesAndCollectionsActivity.start(getContext());
            });
        }

        if (newFollowsButton != null) {
            newFollowsButton.setOnClickListener(v -> {
                // 跳转到新关注消息页
                com.xiaohongshu.ui.message.NewFollowsActivity.start(getContext());
            });
        }

        if (commentsAndMentionsButton != null) {
            commentsAndMentionsButton.setOnClickListener(v -> {
                // 跳转到评论和@我消息页
                com.xiaohongshu.ui.message.CommentsAndMentionsActivity.start(getContext());
            });
        }

        if (closeRecommendButton != null) {
            closeRecommendButton.setOnClickListener(v -> {
                View parent = (View) closeRecommendButton.getParent().getParent();
                if (parent != null) {
                    parent.setVisibility(View.GONE);
                }
            });
        }
    }

    @Override
    protected void initData() {
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getActivity().getApplication())).get(MessageViewModel.class);
        viewModel.init(getContext());
        
        viewModel.getMessageList().observe(getViewLifecycleOwner(), messages -> {
            if (messages != null) {
                adapter.updateData(messages);
            }
        });

        viewModel.getRecommendFriendList().observe(getViewLifecycleOwner(), friends -> {
            if (friends != null) {
                recommendFriendAdapter.updateData(friends);
            }
        });
        
        viewModel.load();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 进入消息页即视为全部已读，并清除底部导航红点
        if (viewModel != null) {
            viewModel.markAllAsRead();
        }
        if (getActivity() instanceof com.xiaohongshu.MainActivity) {
            ((com.xiaohongshu.MainActivity) getActivity()).clearMessageBadge();
        }
        loadDmConversations();
    }

    /** 拉取服务端私信会话列表；未登录或失败时隐藏区块 */
    private void loadDmConversations() {
        if (getContext() == null) return;
        com.xiaohongshu.bean.UserBean currentUser =
                com.xiaohongshu.ui.login.LoginDataRepository.getInstance(getContext()).getCurrentUser();
        if (currentUser == null || currentUser.getToken() == null || currentUser.getToken().isEmpty()) {
            if (dmSection != null) dmSection.setVisibility(View.GONE);
            return;
        }
        final String token = currentUser.getToken();
        new Thread(() -> {
            try {
                com.google.gson.JsonObject data = new com.xiaohongshu.network.RemoteApiClient(getContext())
                        .getDmConversations(token);
                java.util.List<com.google.gson.JsonObject> conversations = new java.util.ArrayList<>();
                if (data.has("conversations") && data.get("conversations").isJsonArray()) {
                    data.getAsJsonArray("conversations").forEach(element -> {
                        if (element.isJsonObject()) conversations.add(element.getAsJsonObject());
                    });
                }
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (dmSection == null) return;
                    if (conversations.isEmpty()) {
                        dmSection.setVisibility(View.GONE);
                    } else {
                        dmSection.setVisibility(View.VISIBLE);
                        dmAdapter.updateData(conversations);
                    }
                });
            } catch (Exception ignored) {
                if (getActivity() != null && dmSection != null) {
                    getActivity().runOnUiThread(() -> dmSection.setVisibility(View.GONE));
                }
            }
        }).start();
    }
    
    /**
     * 根据消息内容判断消息类型（简化实现）
     */
    private int getMessageType(com.xiaohongshu.ui.message.bean.MessageBean message) {
        String title = message.getTitle();
        if (title != null) {
            if (title.contains("审核")) {
                return 5;
            } else if (title.contains("点赞")) {
                return 0;
            } else if (title.contains("收藏")) {
                return 1;
            } else if (title.contains("评论")) {
                return 2;
            } else if (title.contains("关注")) {
                return 3;
            } else if (title.contains("@")) {
                return 4;
            }
        }
        return -1;
    }
    
    /**
     * 处理关注操作
     */
    private void handleFollowAction(com.xiaohongshu.ui.message.bean.RecommendFriendBean friend) {
        if (getContext() == null) {
            return;
        }
        
        com.xiaohongshu.bean.UserBean currentUser = 
            com.xiaohongshu.ui.login.LoginDataRepository.getInstance(getContext()).getCurrentUser();
        if (currentUser == null) {
            return;
        }
        
        new Thread(() -> {
            com.xiaohongshu.app.AppApplication.getDatabase().userDao().getUserByUsername(currentUser.getUsername());
            com.xiaohongshu.database.entity.UserEntity userEntity = 
                com.xiaohongshu.app.AppApplication.getDatabase().userDao().getUserByUsername(currentUser.getUsername());
            if (userEntity != null && friend.getUser() != null && friend.getUser().getId() != null) {
                String currentUserId = userEntity.id;
                String targetUserId = friend.getUser().getId();
                
                com.xiaohongshu.database.entity.FollowEntity existingFollow = 
                    com.xiaohongshu.app.AppApplication.getDatabase().followDao().checkFollow(currentUserId, targetUserId);
                
                if (existingFollow != null) {
                    // 取消关注
                    com.xiaohongshu.app.AppApplication.getDatabase().followDao().unfollow(currentUserId, targetUserId);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            android.widget.Toast.makeText(getContext(), "已取消关注", android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    // 关注
                    com.xiaohongshu.database.entity.FollowEntity follow = 
                        new com.xiaohongshu.database.entity.FollowEntity(currentUserId, targetUserId, System.currentTimeMillis());
                    com.xiaohongshu.app.AppApplication.getDatabase().followDao().insert(follow);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            android.widget.Toast.makeText(getContext(), "已关注", android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        }).start();
    }
}

