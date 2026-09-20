package com.xiaohongshu.ui.home.follow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseFragment;
import com.xiaohongshu.ui.home.bean.UserBean;

import java.util.ArrayList;
import java.util.List;

public class FollowPageFragment extends BaseFragment {
    private DraggableCardView draggableCardContainer;
    private LinearLayout emptyState;
    private ImageView loveButton;
    private AnimatedCircleView loaderView;
    private FollowPageViewModel viewModel;
    private List<UserBean> currentUserList = new ArrayList<>();
    private int currentUserIndex = -1;

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_follow;
    }

    @Override
    protected void initViews(@NonNull View rootView) {
        draggableCardContainer = rootView.findViewById(R.id.draggableCardContainer);
        emptyState = rootView.findViewById(R.id.emptyState);
        loveButton = rootView.findViewById(R.id.loveButton);
        loaderView = rootView.findViewById(R.id.loaderView);

        loveButton.setOnClickListener(v -> {
            // 关注当前显示的用户
            if (currentUserList != null && !currentUserList.isEmpty() && currentUserIndex >= 0 && currentUserIndex < currentUserList.size()) {
                UserBean currentUser = currentUserList.get(currentUserIndex);
                // 执行关注操作（这里可以添加实际的关注逻辑）
                followUser(currentUser);
            } else {
                // 如果没有用户，加载推荐用户
                if (viewModel.getRecommendUserList().getValue() == null || 
                    viewModel.getRecommendUserList().getValue().isEmpty()) {
                    viewModel.load();
                }
            }
        });

        draggableCardContainer.setOnSwipeListener((result, index) -> {
            // 更新当前用户索引（滑动后，当前索引减1，因为顶部卡片被移除了）
            if (currentUserIndex > 0) {
                currentUserIndex--;
            }
            
            // After swiping, show next card
            if (draggableCardContainer.getChildCount() > 1) {
                // There are more cards, show the next one
                View nextCard = draggableCardContainer.getChildAt(draggableCardContainer.getChildCount() - 2);
                if (nextCard != null) {
                    nextCard.setVisibility(View.VISIBLE);
                    nextCard.setAlpha(0f);
                    nextCard.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
                }
            } else {
                // No more cards, reload
                currentUserIndex = -1;
                viewModel.load();
            }
        });
    }

    @Override
    protected void initData() {
        viewModel = new ViewModelProvider(this).get(FollowPageViewModel.class);
        
        viewModel.getRecommendUserList().observe(getViewLifecycleOwner(), users -> {
            if (users == null || users.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                draggableCardContainer.setVisibility(View.GONE);
                loaderView.setVisibility(View.GONE);
                currentUserList.clear();
                currentUserIndex = -1;
            } else {
                emptyState.setVisibility(View.GONE);
                draggableCardContainer.setVisibility(View.VISIBLE);
                loaderView.setVisibility(View.GONE);
                currentUserList = new ArrayList<>(users);
                updateCards(users);
            }
        });

        // Show loader initially
        loaderView.setVisibility(View.VISIBLE);
        viewModel.load();
    }

    private void updateCards(List<UserBean> users) {
        if (draggableCardContainer == null || users == null) {
            return;
        }
        draggableCardContainer.removeAllViews();
        
        for (int i = 0; i < users.size(); i++) {
            UserBean user = users.get(i);
            androidx.cardview.widget.CardView cardView = createCardView(user);
            if (cardView == null) {
                continue; // 跳过无效的卡片
            }
            draggableCardContainer.addCardView(cardView);
            
            // Only show the top card initially, hide others
            if (i < users.size() - 1) {
                cardView.setVisibility(View.INVISIBLE);
            } else {
                cardView.setVisibility(View.VISIBLE);
                draggableCardContainer.setCurrentIndex(i);
                currentUserIndex = i; // 更新当前用户索引
            }
        }
    }
    
    /**
     * 关注用户
     * @param user 要关注的用户
     */
    private void followUser(UserBean user) {
        viewModel.followUser(user, () -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "关注成功", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private androidx.cardview.widget.CardView createCardView(UserBean user) {
        android.content.Context context = getContext();
        if (context == null || user == null || draggableCardContainer == null) {
            // 如果context、user或container为null，返回null，让调用者处理
            return null;
        }
        
        android.view.View cardView = LayoutInflater.from(context)
                .inflate(R.layout.item_user_card, draggableCardContainer, false);
        androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) cardView;
        
        android.widget.ImageView userImage = cardView.findViewById(R.id.userImage);
        android.widget.TextView userName = cardView.findViewById(R.id.userName);
        android.widget.TextView userInfo = cardView.findViewById(R.id.userInfo);
        android.widget.TextView userAddress = cardView.findViewById(R.id.userAddress);
        
        // 优先显示用户设置的头像 URI（统一走 Coil，失败回退占位圆），否则按用户ID生成固定资源头像
        if (userImage != null) {
            if (user.getImageUri() != null && !user.getImageUri().isEmpty()) {
                com.xiaohongshu.util.ImageLoader.load(userImage, user.getImageUri(), R.drawable.placeholder_avatar);
            }
            int avatarRes;
            String userId = user.getId();
            if (userId != null && !userId.isEmpty()) {
                // 根据用户ID生成固定的头像索引，确保同一用户总是显示相同的头像
                int avatarIndex = userId.hashCode();
                avatarRes = getAvatarResource(avatarIndex);
            } else {
                // 如果用户ID为空，使用默认头像
                avatarRes = R.drawable.p1;
            }
            if (user.getImageUri() == null || user.getImageUri().isEmpty()) userImage.setImageResource(avatarRes);
        }
        
        if (userName != null) {
            userName.setText(user.getName() != null ? user.getName() : "");
        }
        
        if (user.getUserInfo() != null) {
            if (userInfo != null) {
                String info = user.getUserInfo().getAge() + "  " +
                        (user.getUserInfo().getSex() == 0 ? "女" : "男");
                userInfo.setText(info);
                userInfo.setVisibility(View.VISIBLE);
            }
            if (userAddress != null) {
                userAddress.setText(user.getUserInfo().getAddress() != null ? user.getUserInfo().getAddress() : "");
                userAddress.setVisibility(View.VISIBLE);
            }
        } else {
            // 无补充资料时隐藏占位行，避免出现「年龄 性别 / 地址」假文案
            if (userInfo != null) userInfo.setVisibility(View.GONE);
            if (userAddress != null) userAddress.setVisibility(View.GONE);
        }

        return card;
    }
    
    /**
     * 根据索引获取头像资源（使用p1-p11）
     * @param index 索引值（可以是任意整数）
     * @return 对应的头像资源ID
     */
    private int getAvatarResource(int index) {
        int[] avatars = {
            R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
            R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
            R.drawable.p9, R.drawable.p10, R.drawable.p11,
            R.drawable.p12, R.drawable.p13, R.drawable.p14,
            R.drawable.p15, R.drawable.p16
        };
        return avatars[Math.abs(index) % avatars.length];
    }
}
