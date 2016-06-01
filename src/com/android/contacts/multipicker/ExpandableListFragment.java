package com.wada811.android.expandablelistfragment;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.R;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * An fragment that displays an expandable list of items by binding to a data
 * source implementing the ExpandableListAdapter, and exposes event handlers
 * when the user selects an item.
 */
public class ExpandableListFragment extends Fragment implements ExpandableListView.OnGroupCollapseListener,
                                                                ExpandableListView.OnGroupExpandListener,
                                                                ExpandableListView.OnGroupClickListener,
                                                                ExpandableListView.OnChildClickListener {

    private static final int INTERNAL_EMPTY_ID = R.id.EXPANDABLE_LIST_FRAGMENT_EMPTY_ID;
    private static final int INTERNAL_PROGRESS_CONTAINER_ID = R.id.EXPANDABLE_LIST_FRAGMENT_PROGRESS_CONTAINER_ID;
    private static final int INTERNAL_LIST_CONTAINER_ID = R.id.EXPANDABLE_LIST_FRAGMENT_LIST_CONTAINER_ID;

    private final Handler handler = new Handler();

    private final Runnable requestFocus = new Runnable() {
        @Override
        public void run(){
            list.focusableViewAvailable(list);
        }
    };

    private ExpandableListAdapter adapter;
    private ExpandableListView list;
    private View emptyView;
    private TextView standardEmptyView;
    private View progressContainer;
    private View listContainer;
    private CharSequence emptyText;
    private boolean isListShown;

    public ExpandableListFragment(){
    }

    /**
     * Provide default implementation to return a expandable list view. Subclasses
     * can override to replace with their own layout. If doing so, the returned
     * view hierarchy <em>must</em> have a ExpandableListView whose id
     * is {@link android.R.id#list android.R.id.list} and can optionally
     * have a sibling view id {@link android.R.id#empty android.R.id.empty} that is to be shown when
     * the list is empty.
     * <p/>
     * <p/>
     * If you are overriding this method with your own custom content, consider including the
     * standard layout {@link android.R.layout#list_content} in your layout file, so that you
     * continue to retain all of the standard behavior of ExpandableListFragment. In particular,
     * this is currently the only way to have the built-in indeterminant progress state be shown.
     *
     * @param inflater The LayoutInflater object that can be used to inflate any views in the
     * fragment,
     * @param container If non-null, this is the parent view that the fragment's UI should be
     * attached to. The fragment should not add the view itself, but this can be used to
     * generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous
     * saved state as given here.
     *
     * @return Return the View for the fragment's UI, or null.
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState){
        final Context context = getActivity();

        final FrameLayout root = new FrameLayout(context);

        // ------------------------------------------------------------------

        final LinearLayout pframe = new LinearLayout(context);
        pframe.setId(INTERNAL_PROGRESS_CONTAINER_ID);
        pframe.setOrientation(LinearLayout.VERTICAL);
        pframe.setVisibility(View.GONE);
        pframe.setGravity(Gravity.CENTER);

        final ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleLarge);
        pframe.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(pframe, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ------------------------------------------------------------------

        final FrameLayout lframe = new FrameLayout(context);
        lframe.setId(INTERNAL_LIST_CONTAINER_ID);

        final TextView tv = new TextView(getActivity());
        tv.setId(INTERNAL_EMPTY_ID);
        tv.setGravity(Gravity.CENTER);
        lframe.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ExpandableListView lv = new ExpandableListView(getActivity());
        lv.setId(android.R.id.list);
        lv.setDrawSelectorOnTop(false);
        lframe.addView(lv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(lframe, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ------------------------------------------------------------------

        root.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return root;
    }

    /**
     * Attach to expandable list view once the view hierarchy has been created.
     *
     * @param view The View returned by {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous
     * saved state as given here.
     */
    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);
        ensureList();
    }

    /**
     * Detach from expandable list view.
     */
    @Override
    public void onDestroyView(){
        handler.removeCallbacks(requestFocus);
        list = null;
        isListShown = false;
        emptyView = progressContainer = listContainer = null;
        standardEmptyView = null;
        super.onDestroyView();
    }

    @Override
    public boolean onGroupClick(final ExpandableListView parent, final View v, final int groupPosition, final long id){
        return false;
    }

    /**
     * Override this for receiving callbacks when a group has been expanded.
     *
     * @param groupPosition The group position that was expanded
     */
    @Override
    public void onGroupExpand(final int groupPosition){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO){
            list.setSelectedGroup(groupPosition);
        }
    }

    /**
     * Override this for receiving callbacks when a group has been collapsed.
     *
     * @param groupPosition The group position that was collapsed
     */
    @Override
    public void onGroupCollapse(final int groupPosition){
    }

    /**
     * Override this for receiving callbacks when a child has been clicked.
     * Callback method to be invoked when a child in this expandable list has been clicked.
     *
     * @param parent The ExpandableListView where the click happened
     * @param v The view within the expandable list/ListView that was clicked
     * @param groupPosition The group position that contains the child that was clicked
     * @param childPosition The child position within the group
     * @param id The row id of the child that was clicked
     */
    @Override
    public boolean onChildClick(final ExpandableListView parent, final View v, final int groupPosition, final int childPosition, final long id){
        return false;
    }

    /**
     * Provide the adapter for the expandable list.
     */
    public void setListAdapter(final ExpandableListAdapter adapter){
        final boolean hadAdapter = this.adapter != null;
        this.adapter = adapter;
        if(list != null){
            list.setAdapter(adapter);
            if(!isListShown && !hadAdapter){
                // The list was hidden, and previously didn't have an
                // adapter.  It is now time to show it.
                setListShown(true, getView().getWindowToken() != null);
            }
        }
    }

    /**
     * Sets the selection to the specified group.
     *
     * @param groupPosition The position of the group that should be selected.
     */
    public void setSelectedGroup(final int groupPosition){
        ensureList();
        list.setSelectedGroup(groupPosition);
    }

    /**
     * Sets the selection to the specified child.
     * If the child is in a collapsed group, the group will only be expanded and child subsequently
     * selected if shouldExpandGroup is set to true, otherwise the method will return false.
     *
     * @param groupPosition The position of the group that contains the child.
     * @param childPosition The position of the child within the group.
     * @param shouldExpandGroup Whether the child's group should be expanded if it is collapsed.
     *
     * @return Whether the selection was successfully set on the child.
     */
    public boolean setSelectedChild(final int groupPosition, final int childPosition, final boolean shouldExpandGroup){
        ensureList();
        return list.setSelectedChild(groupPosition, childPosition, shouldExpandGroup);
    }

    /**
     * Gets the position (in packed position representation) of the currently selected group or
     * child.
     * Use getPackedPositionType(long), getPackedPositionGroup(long), and
     * getPackedPositionChild(long) to unpack the returned packed position.
     *
     * @return A packed position representation containing the currently selected group or child's
     * position and type.
     */
    public long getSelectedPosition(){
        ensureList();
        return list.getSelectedPosition();
    }

    /**
     * Gets the ID of the currently selected group or child.
     *
     * @return The ID of the currently selected group or child.
     */
    public long getSelectedId(){
        ensureList();
        return list.getSelectedId();
    }

    /**
     * Get the activity's expandable list view widget.
     */
    public ExpandableListView getExpandableListView(){
        ensureList();
        return list;
    }

    /**
     * The default content for a ExpandableListFragment has a TextView that can be shown when the
     * list is empty.
     * If you would like to have it shown, call this method to supply the text it should use.
     */
    public void setEmptyText(final CharSequence text){
        ensureList();
        if(standardEmptyView == null){
            throw new IllegalStateException("Can't be used with a custom content view");
        }
        standardEmptyView.setText(text);
        if(emptyText == null){
            list.setEmptyView(standardEmptyView);
        }
        emptyText = text;
    }

    /**
     * Control whether the list is being displayed.
     * You can make it not displayed if you are waiting for the initial data to show in it.
     * During this time an indeterminant progress indicator will be shown instead.
     * <p/>
     * <p/>
     * Applications do not normally need to use this themselves. The default behavior of
     * ListFragment is to start with the list not being shown, only showing it once an adapter is
     * given with {@link #setListAdapter(ListAdapter)}. If the list at that point had not been
     * shown, when it does get shown it will be do without the user ever seeing the hidden state.
     *
     * @param shown If true, the list view is shown; if false, the progress indicator. The initial
     * value is true.
     */
    public void setListShown(final boolean shown){
        setListShown(shown, true);
    }

    /**
     * Like {@link #setListShown(boolean)}, but no animation is used when transitioning from the
     * previous state.
     */
    public void setListShownNoAnimation(final boolean shown){
        setListShown(shown, false);
    }

    /**
     * Control whether the list is being displayed.
     * You can make it not displayed if you are waiting for the initial data to show in it.
     * During this time an indeterminant progress indicator will be shown instead.
     *
     * @param shown If true, the list view is shown; if false, the progress indicator. The initial
     * value is true.
     * @param animate If true, an animation will be used to transition to the new state.
     */
    private void setListShown(final boolean shown, final boolean animate){
        ensureList();
        if(progressContainer == null){
            throw new IllegalStateException("Can't be used with a custom content view");
        }
        if(isListShown == shown){
            return;
        }
        isListShown = shown;
        if(shown){
            if(animate){
                progressContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out));
                listContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
            }else{
                progressContainer.clearAnimation();
                listContainer.clearAnimation();
            }
            progressContainer.setVisibility(View.GONE);
            listContainer.setVisibility(View.VISIBLE);
        }else{
            if(animate){
                progressContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
                listContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out));
            }else{
                progressContainer.clearAnimation();
                listContainer.clearAnimation();
            }
            progressContainer.setVisibility(View.VISIBLE);
            listContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Get the ExpandableListAdapter associated with this activity's ExpandableListView.
     */
    public ExpandableListAdapter getExpandableListAdapter(){
        return adapter;
    }

    private void ensureList(){
        if(list != null){
            return;
        }
        final View root = getView();
        if(root == null){
            throw new IllegalStateException("Content view not yet created");
        }
        if(root instanceof ExpandableListView){
            list = (ExpandableListView)root;
        }else{
            standardEmptyView = (TextView)root.findViewById(INTERNAL_EMPTY_ID);
            if(standardEmptyView == null){
                emptyView = root.findViewById(android.R.id.empty);
            }else{
                standardEmptyView.setVisibility(View.GONE);
            }
            progressContainer = root.findViewById(INTERNAL_PROGRESS_CONTAINER_ID);
            listContainer = root.findViewById(INTERNAL_LIST_CONTAINER_ID);
            View rawListView = root.findViewById(android.R.id.list);
            if(!(rawListView instanceof ExpandableListView)){
                if(rawListView == null){
                    throw new RuntimeException("Your content must have a ExpandableListView whose id attribute is 'android.R.id.list'");
                }
                throw new RuntimeException("Content has view with id attribute 'android.R.id.list' that is not a ExpandableListView class");
            }
            list = (ExpandableListView)rawListView;
            if(emptyView != null){
                list.setEmptyView(emptyView);
            }else if(emptyText != null){
                standardEmptyView.setText(emptyText);
                list.setEmptyView(standardEmptyView);
            }
        }
        isListShown = true;
        // list.setOnCreateContextMenuListener(this);
        list.setOnChildClickListener(this);
        list.setOnGroupCollapseListener(this);
        list.setOnGroupExpandListener(this);
        list.setOnGroupClickListener(this);
        if(adapter != null){
            final ExpandableListAdapter adapter = this.adapter;
            this.adapter = null;
            setListAdapter(adapter);
        }else{
            // We are starting without an adapter, so assume we won't
            // have our data right away and start with the progress indicator.
            if(progressContainer != null){
                setListShown(false, false);
            }
        }
        handler.post(requestFocus);
    }

}
