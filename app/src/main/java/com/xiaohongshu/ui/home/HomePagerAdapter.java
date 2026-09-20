package com.xiaohongshu.ui.home;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.xiaohongshu.ui.home.city.CityFragment;
import com.xiaohongshu.ui.home.discovery.DiscoveryFragment;
import com.xiaohongshu.ui.home.follow.FollowPageFragment;

/**
 * Home ViewPager Adapter
 */
public class HomePagerAdapter extends FragmentStateAdapter {
    public HomePagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    public HomePagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new FollowPageFragment();
            case 1:
                return new DiscoveryFragment();
            case 2:
                return new CityFragment();
            default:
                return new DiscoveryFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}

