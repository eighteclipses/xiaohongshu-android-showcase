package com.xiaohongshu.ui.home.discovery;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.xiaohongshu.R;
import com.xiaohongshu.activity.graphic.GraphicActivity;
import com.xiaohongshu.activity.video.VideoActivity;
import com.xiaohongshu.base.BaseFragment;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.GraphicCardType;
import com.xiaohongshu.ui.home.composable.ShimmerLayout;

import java.util.List;

public class DiscoveryFragment extends BaseFragment {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ShimmerLayout shimmerLayout;
    private DiscoveryViewModel viewModel;
    private DiscoveryAdapter adapter;

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_discovery;
    }

    @Override
    protected void initViews(@NonNull View rootView) {
        recyclerView = rootView.findViewById(R.id.recyclerView);
        swipeRefresh = rootView.findViewById(R.id.swipeRefresh);
        shimmerLayout = rootView.findViewById(R.id.shimmerLayout);

        // Setup RecyclerView with StaggeredGridLayoutManager (2 columns)
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new DiscoveryAdapter(card -> {
            if (card.getType() == GraphicCardType.Graphic) {
                GraphicActivity.newInstance(getContext(), card.getId());
            } else {
                VideoActivity.newInstance(getContext(), card.getId());
            }
        });
        recyclerView.setAdapter(adapter);

        // Setup SwipeRefresh
        swipeRefresh.setColorSchemeColors(
                getResources().getColor(R.color.theme_red, null),
                getResources().getColor(R.color.theme_background_gray, null)
        );
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.reload();
        });
    }

    @Override
    protected void initData() {
        viewModel = new ViewModelProvider(this).get(DiscoveryViewModel.class);
        
        viewModel.getGraphicCardList().observe(getViewLifecycleOwner(), cards -> {
            if (cards == null || cards.isEmpty()) {
                shimmerLayout.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                shimmerLayout.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.updateData(cards);
            }
            if (swipeRefresh.isRefreshing()) {
                swipeRefresh.setRefreshing(false);
            }
        });

        viewModel.load();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 从其他页面返回时，刷新笔记列表以更新用户头像
        // 直接调用，避免不必要的延迟
        if (viewModel != null) {
            viewModel.load();
        }
    }
}

