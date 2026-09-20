package com.xiaohongshu.ui.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.search.bean.HotspotBean;
import com.xiaohongshu.ui.search.viewmodel.SearchViewModel;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends BaseActivity {
    
    public static void start(Context context) {
        Intent intent = new Intent(context, SearchActivity.class);
        context.startActivity(intent);
    }
    private SearchViewModel viewModel;
    private RecyclerView historyRecyclerView;
    private RecyclerView suggestionsRecyclerView;
    private RecyclerView hotspotRecyclerView;
    private SearchHistoryAdapter historyAdapter;
    private SearchSuggestionAdapter suggestionAdapter;
    private SearchHotspotAdapter hotspotAdapter;
    private EditText searchInput;
    private View backButton;
    private View searchButton;
    private View deleteHistoryButton;
    private View refreshSuggestionsButton;
    private View hideSuggestionsButton;
    private boolean suggestionsHidden = false;
    /** 双击防抖：600ms 内的重复搜索请求直接忽略，避免连点启动多个结果页 */
    private long lastSearchAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        
        // Initialize ViewModel after setContentView
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        
        // Initialize views and data after setContentView
        initViews();
        initData();
    }

    @Override
    protected void initViews() {
        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        suggestionsRecyclerView = findViewById(R.id.suggestionsRecyclerView);
        hotspotRecyclerView = findViewById(R.id.hotspotRecyclerView);
        searchInput = findViewById(R.id.searchInput);
        backButton = findViewById(R.id.backButton);
        searchButton = findViewById(R.id.searchButton);
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput.setSingleLine(true);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_UP)) {
                String text = searchInput.getText().toString().trim();
                if (!text.isEmpty()) performSearch(text);
                return true;
            }
            return false;
        });
        deleteHistoryButton = findViewById(R.id.deleteHistoryButton);
        refreshSuggestionsButton = findViewById(R.id.refreshSuggestionsButton);
        hideSuggestionsButton = findViewById(R.id.hideSuggestionsButton);

        // Setup history RecyclerView with FlowLayoutManager for auto-wrap
        FlowLayoutManager flowLayoutManager = new FlowLayoutManager(this);
        historyRecyclerView.setLayoutManager(flowLayoutManager);
        historyRecyclerView.setHasFixedSize(false);
        historyRecyclerView.setNestedScrollingEnabled(false);
        historyAdapter = new SearchHistoryAdapter();
        historyAdapter.setOnItemClickListener(keyword -> {
            searchInput.setText(keyword);
            performSearch(keyword);
        });
        // No expand functionality needed - show all history items
        historyRecyclerView.setAdapter(historyAdapter);
        
        // Force layout after RecyclerView is measured to ensure correct height
        historyRecyclerView.post(() -> {
            if (historyRecyclerView.getLayoutManager() != null) {
                historyRecyclerView.getLayoutManager().requestLayout();
                historyRecyclerView.requestLayout();
            }
        });

        // Setup suggestions RecyclerView (2 columns)
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        suggestionsRecyclerView.setLayoutManager(gridLayoutManager);
        // Remove item decoration spacing for left alignment
        suggestionsRecyclerView.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, android.view.View view, 
                    androidx.recyclerview.widget.RecyclerView parent, 
                    androidx.recyclerview.widget.RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int spacing = (int) (8 * getResources().getDisplayMetrics().density);
                // No left spacing for first column (even positions)
                if (position % 2 == 0) {
                    outRect.left = 0;
                } else {
                    outRect.left = spacing;
                }
                outRect.right = 0;
                outRect.bottom = spacing;
            }
        });
        suggestionAdapter = new SearchSuggestionAdapter();
        suggestionAdapter.setOnItemClickListener(keyword -> {
            searchInput.setText(keyword);
            performSearch(keyword);
        });
        suggestionsRecyclerView.setAdapter(suggestionAdapter);

        // Setup hotspot RecyclerView
        hotspotRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        hotspotAdapter = new SearchHotspotAdapter();
        hotspotAdapter.setOnItemClickListener(hotspot -> {
            searchInput.setText(hotspot.getTitle());
            performSearch(hotspot.getTitle());
        });
        hotspotRecyclerView.setAdapter(hotspotAdapter);

        // Setup click listeners
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (searchButton != null) {
            searchButton.setOnClickListener(v -> {
                String keyword = searchInput.getText().toString().trim();
                if (!keyword.isEmpty()) {
                    performSearch(keyword);
                }
            });
        }

        if (deleteHistoryButton != null) {
            deleteHistoryButton.setOnClickListener(v -> {
                viewModel.clearHistory();
            });
        }

        if (refreshSuggestionsButton != null) {
            refreshSuggestionsButton.setOnClickListener(v -> {
                viewModel.load();
            });
        }

        if (hideSuggestionsButton != null) {
            hideSuggestionsButton.setOnClickListener(v -> {
                suggestionsHidden = !suggestionsHidden;
                suggestionsRecyclerView.setVisibility(suggestionsHidden ? View.GONE : View.VISIBLE);
                hideSuggestionsButton.setContentDescription(getString(
                        suggestionsHidden ? R.string.show : R.string.hide));
            });
        }

        // 输入过程触发联想词：实时按当前输入过滤本地笔记标题 + 历史搜索
        if (searchInput != null) {
            searchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override
                public void afterTextChanged(android.text.Editable s) {
                    String text = s == null ? "" : s.toString();
                    viewModel.loadSuggestionsByPrefix(text);
                }
            });
        }

        // Remove old expand button click listener as it's now in the adapter
    }

    @Override
    protected void initData() {
        // Load data after views are initialized
        if (viewModel != null) {
            viewModel.load();
        }
        
        viewModel.getHistoryList().observe(this, history -> {
            if (history != null && historyAdapter != null) {
                updateHistoryDisplay(history);
            }
        });

        viewModel.getSuggestionList().observe(this, suggestions -> {
            if (suggestions != null && suggestionAdapter != null) {
                suggestionAdapter.updateData(suggestions);
                // Force layout update to ensure RecyclerView displays correctly
                if (suggestionsRecyclerView != null) {
                    suggestionsRecyclerView.post(() -> {
                        if (suggestionsRecyclerView.getAdapter() != null) {
                            suggestionsRecyclerView.getAdapter().notifyDataSetChanged();
                        }
                        suggestionsRecyclerView.requestLayout();
                    });
                }
            }
        });

        viewModel.getHotspotList().observe(this, hotspots -> {
            if (hotspots != null && hotspotAdapter != null) {
                hotspotAdapter.updateData(hotspots);
            }
        });
    }

    private void performSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSearchAt < 600) {
            return;
        }
        lastSearchAt = now;

        // 保存搜索历史（持久化到 SharedPreferences）
        viewModel.addToHistory(keyword.trim());

        // 跳转到搜索结果页
        SearchResultActivity.start(this, keyword.trim());
    }

    private void updateHistoryDisplay(List<String> history) {
        if (history == null || history.isEmpty()) {
            historyAdapter.updateData(new ArrayList<>(), false, -1);
            return;
        }

        // Always show all history items, no expand functionality needed
        historyAdapter.updateData(history, false, -1);
        
        // Force layout update to ensure RecyclerView calculates correct height
        historyRecyclerView.post(() -> {
            if (historyRecyclerView.getLayoutManager() != null) {
                historyRecyclerView.getLayoutManager().requestLayout();
                historyRecyclerView.requestLayout();
                
                // Use ViewTreeObserver to ensure layout is complete and update height
                historyRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            historyRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            
                            // Calculate actual height from children
                            int childCount = historyRecyclerView.getChildCount();
                            if (childCount > 0) {
                                int maxBottom = 0;
                                for (int i = 0; i < childCount; i++) {
                                    View child = historyRecyclerView.getChildAt(i);
                                    if (child != null) {
                                        maxBottom = Math.max(maxBottom, child.getBottom());
                                    }
                                }
                                
                                // Set explicit height if needed
                                int currentHeight = historyRecyclerView.getHeight();
                                int calculatedHeight = maxBottom + historyRecyclerView.getPaddingBottom();
                                
                                if (currentHeight < calculatedHeight) {
                                    android.view.ViewGroup.LayoutParams params = historyRecyclerView.getLayoutParams();
                                    params.height = calculatedHeight;
                                    historyRecyclerView.setLayoutParams(params);
                                }
                            }
                        }
                    });
            }
        });
    }

}
