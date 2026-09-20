package com.xiaohongshu;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.tabs.TabLayout;
import com.xiaohongshu.ui.search.SearchResultActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SearchRegressionTest {
    @Test public void aiTopicsAreAppliedTogetherWithoutDroppingEarlierTopics() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            com.xiaohongshu.ui.publish.viewmodel.NoteEditViewModel model =
                    new com.xiaohongshu.ui.publish.viewmodel.NoteEditViewModel();
            model.addTopics(java.util.Arrays.asList("#学习", "#校园", "#时间管理"));
            model.addTopics(java.util.Arrays.asList("#校园", "#自习"));
            assertEquals(java.util.Arrays.asList("#学习", "#校园", "#时间管理", "#自习"), model.getTopics().getValue());
        });
    }
    @Test public void searchLaunchAndAllCategoriesStayUsable() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), SearchResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(SearchResultActivity.KEY_KEYWORD, "校园");
        Activity activity = instrumentation.startActivitySync(intent);
        try {
            instrumentation.runOnMainSync(() -> {
                TabLayout tabs = activity.findViewById(R.id.tabLayout);
                assertEquals(3, tabs.getTabCount());
                assertEquals("用户", tabs.getTabAt(1).getText());
                int[] lists = { R.id.notesRecyclerView, R.id.usersRecyclerView, R.id.goodsRecyclerView };
                for (int index = 0; index < lists.length; index++) {
                    tabs.getTabAt(index).select();
                    for (int other = 0; other < lists.length; other++) {
                        assertNotNull(activity.findViewById(lists[other]));
                        assertEquals(index == other ? View.VISIBLE : View.GONE, activity.findViewById(lists[other]).getVisibility());
                    }
                }
                tabs.getTabAt(0).select();
                activity.findViewById(R.id.sortLatest).performClick();
                activity.findViewById(R.id.sortHottest).performClick();
                assertFalse(activity.isFinishing());
            });
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }
}
