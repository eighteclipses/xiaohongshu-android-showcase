package com.xiaohongshu.util;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

/**
 * Helper class for working with ViewBinding and views.
 * Provides utility methods for finding views and setting up click listeners.
 */
public class ViewBindingHelper {

    /**
     * Find a view by ID, throwing an exception if not found.
     * This is a convenience method for ViewBinding-style null safety.
     */
    @NonNull
    public static <T extends View> T requireView(@NonNull View root, @IdRes int id) {
        T view = root.findViewById(id);
        if (view == null) {
            throw new IllegalStateException("View with ID " + id + " not found");
        }
        return view;
    }

    /**
     * Find a view by ID, returning null if not found.
     */
    @SuppressWarnings("unchecked")
    public static <T extends View> T findView(@NonNull View root, @IdRes int id) {
        return (T) root.findViewById(id);
    }

    /**
     * Set click listener for multiple views at once
     */
    public static void setOnClickListeners(View.OnClickListener listener, View... views) {
        for (View view : views) {
            if (view != null) {
                view.setOnClickListener(listener);
            }
        }
    }

    /**
     * Set visibility for multiple views
     */
    public static void setVisibility(int visibility, View... views) {
        for (View view : views) {
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    /**
     * Enable/disable multiple views
     */
    public static void setEnabled(boolean enabled, View... views) {
        for (View view : views) {
            if (view != null) {
                view.setEnabled(enabled);
            }
        }
    }

    /**
     * Remove all child views from a ViewGroup
     */
    public static void removeAllViews(@NonNull ViewGroup viewGroup) {
        viewGroup.removeAllViews();
    }
}

