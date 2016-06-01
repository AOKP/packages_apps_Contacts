package com.android.contacts.multipicker;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.FrameLayout;

public abstract class ExpandableListFragment extends Fragment
        implements ExpandableListView.OnChildClickListener
        {

    static final int INTERNAL_LIST_CONTAINER_ID = 0x00ff0001;

    final private Handler mHandler = new Handler();

    final private Runnable mRequestFocus = new Runnable() {
        public void run() {
            mExpandableList.focusableViewAvailable(mExpandableList);
        }
    };

    ExpandableListAdapter mAdapter;
    ExpandableListView mExpandableList;
    boolean mFinishedStart = false;
    View mEmptyView;
    View mExpandableListContainer;
    CharSequence mEmptyText;

    public ExpandableListFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final Context context = getActivity();

        FrameLayout listContainer = new FrameLayout(context);
        listContainer.setId(INTERNAL_LIST_CONTAINER_ID);

        ExpandableListView lv = new ExpandableListView(getActivity());
        lv.setId(android.R.id.list);
        lv.setDrawSelectorOnTop(false);
        listContainer.addView(lv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return listContainer;
    }

    /**
     * Attach to list view once the view hierarchy has been created.
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureList();
    }

    /**
     * Detach from list view.
     */
    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mRequestFocus);
        mExpandableList = null;
        mEmptyView = mExpandableListContainer = null;
        super.onDestroyView();
    }

    /**
     * Provide the cursor for the list view.
     */
    public void setListAdapter(ExpandableListAdapter adapter) {
        mAdapter = adapter;
        if (mExpandableList != null) {
            mExpandableList.setAdapter(adapter);
        }
    }

    public void setSelection(int position) {
        ensureList();
        mExpandableList.setSelection(position);
    }

    /**
     * Get the position of the currently selected list item.
     */
    public int getSelectedItemPosition() {
        ensureList();
        return mExpandableList.getSelectedItemPosition();
    }

    /**
     * Get the cursor row ID of the currently selected list item.
     */
    public long getSelectedItemId() {
        ensureList();
        return mExpandableList.getSelectedItemId();
    }

    /**
     * Get the activity's list view widget.
     */
    public ExpandableListView getListView() {
        ensureList();
        return mExpandableList;
    }

    /**
     * Get the ListAdapter associated with this activity's ListView.
     */
    public ExpandableListAdapter getListAdapter() {
        return mAdapter;
    }

    private void ensureList() {
        if (mExpandableList != null) {
            return;
        }
        View root = getView();
        if (root == null) {
            throw new IllegalStateException("Content view not yet created");
        }
        if (root instanceof ExpandableListView) {
            mExpandableList = (ExpandableListView) root;
        } else {
            mEmptyView = root.findViewById(android.R.id.empty);
            mExpandableListContainer = root.findViewById(INTERNAL_LIST_CONTAINER_ID);
            View rawExpandableListView = root.findViewById(android.R.id.list);
            if (!(rawExpandableListView instanceof ExpandableListView)) {
                if (rawExpandableListView == null) {
                    throw new RuntimeException(
                            "Your content must have a ListView whose id attribute is " +
                                    "'android.R.id.list'");
                }
                throw new RuntimeException(
                        "Content has view with id attribute 'android.R.id.list' "
                                + "that is not a ListView class");
            }
            mExpandableList = (ExpandableListView) rawExpandableListView;
            if (mEmptyView != null) {
                mExpandableList.setEmptyView(mEmptyView);
            }
        }
        if (mAdapter != null) {
            setListAdapter(mAdapter);
        }
        mHandler.post(mRequestFocus);
    }

    /**
     * Get the activity's expandable list view widget. This can be used to get the selection, set
     * the selection, and many other useful functions.
     *
     * @see ExpandableListView
     */
    public ExpandableListView getExpandableListView() {
        ensureList();
        return mExpandableList;
    }

    /**
     * Get the ExpandableListAdapter associated with this activity's ExpandableListView.
     */
    public ExpandableListAdapter getExpandableListAdapter() {
        return mAdapter;
    }

    /**
     * Gets the ID of the currently selected group or child.
     *
     * @return The ID of the currently selected group or child.
     */
    public long getSelectedId() {
        return mExpandableList.getSelectedId();
    }

    public long getSelectedPosition() {
        return mExpandableList.getSelectedPosition();
    }

    public boolean setSelectedChild(int groupPosition, int childPosition,
            boolean shouldExpandGroup) {
        return mExpandableList.setSelectedChild(groupPosition, childPosition, shouldExpandGroup);
    }

    /**
     * Sets the selection to the specified group.
     *
     * @param groupPosition The position of the group that should be selected.
     */
    public void setSelectedGroup(int groupPosition) {
        mExpandableList.setSelectedGroup(groupPosition);
    }
}
