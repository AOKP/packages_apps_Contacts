/**
 * Copyright (C) 2013-2016, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.activities;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.SimContactsOperation;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.multipicker.CallLogFragment;
import com.android.contacts.multipicker.ContactsFragment;
import com.android.contacts.multipicker.GroupsFragment;
import com.android.contacts.multipicker.DelCallLogFragment;
import com.android.contacts.multipicker.SearchFragment;
import com.android.contacts.list.ContactsPickMode;
import com.android.contacts.list.OnCheckListActionListener;
import com.android.contacts.R;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MultiPickContactsActivity extends Activity implements ViewPager.OnPageChangeListener,
        View.OnClickListener, View.OnFocusChangeListener {

    private final static String TAG = "MultiPickContactsActivity";
    private final static boolean DEBUG = true;

    private ContactsPickMode mPickMode;

    private ViewPager mViewPager;
    private ViewPagerTabs mViewPagerTabs;
    private ViewPagerAdapter mViewPagerAdapter;
    private LinearLayout mViewPagerLayout;

    private int mSelectedNums;

    private boolean mAreTabsHiddenInViewPager = false;

    private String[] mTabTitles;

    private static final int TAB_INDEX_RECENT = 0;
    private static final int TAB_INDEX_CONTACTS = 1;
    private static final int TAB_INDEX_GROUP = 2;

    private static final int TAB_INDEX_COUNT = 3;

    private DelCallLogFragment mDelCallLogFragment;
    private CallLogFragment mCallLogFragment;
    private ContactsFragment mContactsFragment;
    private GroupsFragment mGroupFragment;
    private SearchFragment mSearchFragment;

    private boolean mSearchUiVisible = false;

    private ArrayList<Long> mGroupIds = new ArrayList<Long>();

    // which group is selected
    private List<Long> mGroupSelected;

    private StringBuilder mForSearchCallLog;

    private static final int DIALOG_DEL_CALL = 1;
    public static final int ACTION_ADD_GROUP_MEMBER = 0;
    public static final int ACTION_MOVE_GROUP_MEMBER = 1;
    public static final int ACTION_DEFAULT_VALUE = -1;

    public static final String ADD_MOVE_GROUP_MEMBER_KEY = "add_move_group_member";
    public static final String KEY_GROUP_ID = "group_id";

    public static final String EXTRA_INFO = "info";
    public static final String EXTRA_VCARD = "vcard";

    private HashMap<String, List<String[]>> mSelectedContactInfo;

    private static final String ITEM_SEP = ", ";
    private static final String CONTACT_SEP_LEFT = "[";
    private static final String CONTACT_SEP_RIGHT = "]";

    // contains data ids
    private Bundle mChoiceSet;
    // contains call log ids
    private Bundle mChoiceNumberSet;
    private Bundle mBackupChoiceSet;

    private TextView mOKButton;
    private LinearLayout mButton_view;

    private ActionBar mActionBar;
    private EditText mSearchView;
    private ViewGroup mSearchViewContainer;
    private View mSelectionContainer;
    private Button mSelectionButton;

    private MenuItem searchItem;
    private MenuItem closeItem;

    private SelectionMenu mSelectionMenu;

    private Context mContext;
    private ProgressDialog mProgressDialog;
    private SimContactsOperation mSimContactsOperation;

    private static final int SIM_COLUMN_DISPLAY_NAME = 0;
    private static final int SIM_COLUMN_NUMBER = 1;
    private static final int SIM_COLUMN_EMAILS = 2;
    private static final int SIM_COLUMN_ANRS = 3;
    private static final int SIM_COLUMN_ID = 4;

    // reduce the value to avoid too large transaction.
    private int MAX_CONTACTS_NUM_TO_SELECT_ONCE = 1000;

    private int MAX_CONTACTS_NUM_TO_GROUP = 100;

    private static final int BUFFER_LENGTH = 400;

    public class ViewPagerAdapter extends FragmentPagerAdapter {

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public long getItemId(int position) {
            return getRtlPosition(position);
        }

        public void setTabsHidden(boolean hideTabs) {
            if (hideTabs == mAreTabsHiddenInViewPager) {
                return;
            }
            mAreTabsHiddenInViewPager = hideTabs;
            notifyDataSetChanged();
        }

        @Override
        public Fragment getItem(int position) {
            // As FragmentPagerAdapter will add a TAG and use findFragmentByTag automatically,
            // so the Fragment will only be created once. If we add a TAG manually, there will
            // occur crash.
            position = getRtlPosition(position);

            if (!mPickMode.isPickPhone()) {
                if (mPickMode.isPickCall()) {
                    mDelCallLogFragment = new DelCallLogFragment();
                    mDelCallLogFragment.setCheckListListener(new CheckListListener());
                    return mDelCallLogFragment;
                } else {
                    mContactsFragment = new ContactsFragment();
                    mContactsFragment.setCheckListListener(new CheckListListener());
                    return mContactsFragment;
                }
            } else {
                switch (position) {
                    case TAB_INDEX_RECENT:
                        mCallLogFragment = new CallLogFragment();
                        mCallLogFragment.setCheckListListener(new CheckListListener());
                        return mCallLogFragment;
                    case TAB_INDEX_CONTACTS:
                        mContactsFragment = new ContactsFragment();
                        mContactsFragment.setCheckListListener(new CheckListListener());
                        return mContactsFragment;
                    case TAB_INDEX_GROUP:
                        mGroupFragment = new GroupsFragment();
                        mGroupFragment.setCheckListListener(new CheckListListener());
                        return mGroupFragment;
                }
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (mPickMode.isPickCall()) {
                return mTabTitles[TAB_INDEX_RECENT];
            } else if (mPickMode.isPickPhone()) {
                return mTabTitles[position];
            } else {
                return mTabTitles[TAB_INDEX_CONTACTS];
            }
        }

        @Override
        public int getCount() {
            return mAreTabsHiddenInViewPager ? 1 : TAB_INDEX_COUNT;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {

            Fragment f = (Fragment) super.instantiateItem(container, position);
            if (!mPickMode.isPickPhone()) {
                if (mPickMode.isPickCall()) {
                    if (mDelCallLogFragment == null && f instanceof DelCallLogFragment) {
                        mDelCallLogFragment = (DelCallLogFragment) f;
                        mDelCallLogFragment
                                .setCheckListListener(new CheckListListener());
                    }
                } else {
                    if (mContactsFragment == null && f instanceof ContactsFragment) {
                        mContactsFragment = (ContactsFragment) f;
                        mContactsFragment
                                .setCheckListListener(new CheckListListener());
                    }
                }
            } else {
                switch (position) {
                case TAB_INDEX_RECENT:
                    if (mCallLogFragment == null && f instanceof CallLogFragment) {
                        mCallLogFragment = (CallLogFragment) f;
                        mCallLogFragment
                                .setCheckListListener(new CheckListListener());
                    }
                    break;
                case TAB_INDEX_CONTACTS:
                    if (mContactsFragment == null && f instanceof ContactsFragment) {
                        mContactsFragment = (ContactsFragment) f;
                        mContactsFragment
                                .setCheckListListener(new CheckListListener());
                    }
                    break;
                case TAB_INDEX_GROUP:
                    if (mGroupFragment == null && f instanceof GroupsFragment) {
                        mGroupFragment = (GroupsFragment) f;
                        mGroupFragment
                                .setCheckListListener(new CheckListListener());
                    }
                    break;
                }
            }
            return f;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        setContentView(R.layout.multi_pick_activity);

        mChoiceSet = new Bundle();
        mContext = getApplicationContext();

        Intent intent = getIntent();

        mActionBar = getActionBar();
        mActionBar.setDisplayShowHomeEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setTitle(null);

        mPickMode = ContactsPickMode.getInstance();
        mPickMode.setMode(getIntent());

        if (mPickMode.isPickPhone()) {
            mChoiceNumberSet = new Bundle();
            mGroupSelected = new ArrayList<Long>();
            mForSearchCallLog = new StringBuilder();
        }

        inflateSearchView();

        mTabTitles = new String[TAB_INDEX_COUNT];
        mTabTitles[0] = getString(R.string.multi_pick_recent_title);
        mTabTitles[1] = getString(R.string.multi_pick_contacts_title);
        mTabTitles[2] = getString(R.string.multi_pick_group_title);

        mViewPager = (ViewPager) findViewById(R.id.multi_pick_pager);
        mViewPagerLayout = (LinearLayout) findViewById(R.id.multi_pick_layout);

        mViewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);

        final FragmentManager fragmentManager = getFragmentManager();
        mViewPagerAdapter = new ViewPagerAdapter(fragmentManager);
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOffscreenPageLimit(TAB_INDEX_COUNT - 1);
        mViewPager.setOnPageChangeListener(this);

        mViewPagerTabs.setViewPager(mViewPager);

        showTab();

        mSimContactsOperation = new SimContactsOperation(this);
        initResource();

        if (mPickMode.isPickPhone()) {
            mActionBar.setElevation(0);
            mSearchFragment = new SearchFragment();
            mSearchFragment.setCheckListListener(new CheckListListener());
            FragmentTransaction mFragmentTransaction = fragmentManager.beginTransaction();
            mFragmentTransaction.add(R.id.search_layout, mSearchFragment);
            mFragmentTransaction.commit();
        } else {
            mActionBar.setElevation(4 * getResources().getDisplayMetrics().density);
            mViewPagerTabs.setVisibility(View.GONE);
        }
    }

    private void initResource() {
        mOKButton = (TextView) findViewById(R.id.btn_ok);
        mButton_view = (LinearLayout) findViewById(R.id.btn_view);
        if (mPickMode.isPickCall()) {
            mOKButton.setText(
                    mContext.getResources().getString(R.string.clear_call_log_button_text));
        }
        mOKButton.setOnClickListener(this);
        setOkStatus();
    }

    private void inflateSearchView() {
        LayoutInflater inflater = LayoutInflater.from(mActionBar.getThemedContext());
        mSearchViewContainer = (ViewGroup) inflater.inflate(R.layout.custom_pick_action_bar, null);
        mSearchView = (EditText) mSearchViewContainer.findViewById(R.id.search_view);

        mSearchView.setHintTextColor(getColor(R.color.searchbox_phone_hint_text_color));
        mSearchView.setTextColor(getColor(R.color.searchbox_phone_text_color));

        mSelectionContainer = inflater.inflate(R.layout.action_mode, null);
        mSelectionButton = (Button) mSelectionContainer.findViewById(R.id.selection_menu);
        mSelectionButton.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        mSelectedNums = 0;
        String countTitle = mContext.getResources().getString(R.string.contacts_selected,
                mSelectedNums);
        mSelectionButton.setText(countTitle);
        mSelectionButton.setElevation(4 * getResources().getDisplayMetrics().density);
        // Setup selection bar
        if (mSelectionMenu == null) {
            mSelectionMenu = new SelectionMenu(this, mSelectionButton,
                    new PopupList.OnPopupItemClickListener() {
                        @Override
                        public boolean onPopupItemClick(int itemId) {
                            if (itemId == SelectionMenu.SELECT_OR_DESELECT) {
                                setAllSelected();
                            }
                            return true;
                        }
                    });
            addSelectionMenuPopupListItem(countTitle);
        }

        mActionBar.setDisplayShowCustomEnabled(true);

        configureSearchMode();

        mSearchView.setHint(getString(R.string.search_menu_search));
        mSearchView.setFocusable(true);
        mSearchView.setOnFocusChangeListener(this);
        mSearchView.addTextChangedListener(new SearchTextWatcher());
    }

    private class SearchTextWatcher implements TextWatcher {
        @Override
        public void onTextChanged(CharSequence queryString, int start, int before, int count) {
            updateState(queryString.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
    }

    private void setAllSelected() {
        int position = mViewPager.getCurrentItem();
        boolean selectAll = false;
        int checkNum = -1;
        int num = -1;
        if (mAreTabsHiddenInViewPager) {
            checkNum = mChoiceSet.size();
            if (mPickMode.isPickCall()) {
                // add header view, the count of list need -1.
                num = mDelCallLogFragment.getListView().getCount() - 1;
                if (checkNum < num) {
                    selectAll = true;
                }
                mDelCallLogFragment.setSelectedAll(selectAll);
            } else {
                num = mContactsFragment.getListView().getCount() - 1;
                if (checkNum < num) {
                    selectAll = true;
                }
                mContactsFragment.setSelectedAll(selectAll);
            }
        } else {
            switch (position) {
                case TAB_INDEX_RECENT:
                    checkNum = mCallLogFragment.getAllCheckedListSize();
                    num = mCallLogFragment.getListView().getCount() - 1;
                    if (checkNum < num) {
                        selectAll = true;
                    }
                    mCallLogFragment.setSelectedAll(selectAll);
                    break;
                case TAB_INDEX_CONTACTS:
                    checkNum = mContactsFragment.getAllCheckedListSize();
                    num = mContactsFragment.getListView().getCount() - 1;
                    if (checkNum < num) {
                        selectAll = true;
                    }
                    mContactsFragment.setSelectedAll(selectAll);
                    break;
                case TAB_INDEX_GROUP:
                    checkNum = mGroupFragment.getAllCheckedListSize();
                    Cursor allContactsInGroups = mGroupFragment.getAllContactsInGroups();
                    if (allContactsInGroups == null) {
                        num = 0;
                    } else {
                        num = allContactsInGroups.getCount();
                    }
                    if (checkNum < num) {
                        selectAll = true;
                    }
                    mGroupFragment.setSelectedAll(selectAll);
                    break;
                default:
                    break;
            }
        }
    }

    private void addSelectionMenuPopupListItem(String countTitle) {
        mSelectionMenu.getPopupList().addItem(SelectionMenu.SELECTED, countTitle);
        boolean selectAll = true;
        if (mViewPager != null) {
            int position = mViewPager.getCurrentItem();
            int checkNum = -1;
            int num = -1;
            if (mPickMode.isPickPhone()) {
                switch (position) {
                    case TAB_INDEX_RECENT:
                        checkNum = mCallLogFragment.getAllCheckedListSize();
                        num = mCallLogFragment.getListView().getCount() - 1;
                        break;
                    case TAB_INDEX_CONTACTS:
                        checkNum = mContactsFragment.getAllCheckedListSize();
                        num = mContactsFragment.getListView().getCount() - 1;
                        break;
                    case TAB_INDEX_GROUP:
                        checkNum = mGroupFragment.getAllCheckedListSize();
                        Cursor allContactsInGroups = mGroupFragment.getAllContactsInGroups();
                        if (allContactsInGroups == null) {
                            num = 0;
                        } else {
                            num = allContactsInGroups.getCount();
                        }
                        break;
                    default:
                        break;
                }
            } else if (mPickMode.isPickCall()) {
                checkNum = mChoiceSet.size();
                num = mDelCallLogFragment.getListView().getCount() - 1;
            } else {
                checkNum = mChoiceSet.size();
                num = mContactsFragment.getListView().getCount() - 1;
            }
            if (checkNum == num && num > 0) {
                selectAll = false;
            }
        }

        mSelectionMenu.getPopupList().addItem(SelectionMenu.SELECT_OR_DESELECT,
                getString(selectAll ? R.string.menu_select_all : R.string.menu_select_none));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.search_menu, menu);

        searchItem = menu.findItem(R.id.menu_search);
        searchItem
                .setVisible(!mSearchUiVisible && !mPickMode.isPickCall() && !mPickMode.isPickSim());
        closeItem = menu.findItem(R.id.menu_close);
        closeItem.setVisible(false);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_search:
                mSearchUiVisible = true;
                enterSearchMode();
                configureSearchView();
                return true;
            case R.id.menu_close:
                closeItem.setVisible(false);
                mSearchView.setText(null);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final class CheckListListener implements OnCheckListActionListener {

        @Override
        public boolean onContainsKey(String key) {
            return mChoiceSet.containsKey(key);
        }

        @Override
        public boolean onContainsNumberKey(String key) {
            return mChoiceNumberSet.containsKey(key);
        }

        @Override
        public void putValue(String key, String[] value) {
            mChoiceSet.putStringArray(key, value);
            setOkStatus();
        }

        @Override
        public void putNumberValue(String key, String[] value) {
            mChoiceNumberSet.putStringArray(key, value);
            setOkStatus();
        }

        @Override
        public void onRemove(String key) {
            mChoiceSet.remove(key);
            if (mPickMode.isPickPhone() && !TextUtils.isEmpty(key)) {
                long id = Long.valueOf(key);
            }
            setOkStatus();
        }

        @Override
        public void onNumberRemove(String key) {
            mChoiceNumberSet.remove(key);
            setOkStatus();
        }

        @Override
        public void onClear() {
            mChoiceSet.clear();
            setOkStatus();
        }

        @Override
        public void onHideSoftKeyboard() {
            hideSoftKeyboard();
        }

        @Override
        public void onUpdateActionBar() {
            updateActionBar();
        }

        @Override
        public void exitSearch() {
            if (mPickMode.isSearchMode()) {
                mSearchUiVisible = false;
                exitSearchMode(true);
                configureSearchView();
            }
        }

        @Override
        public void addGroupId(long groupId) {
            mGroupSelected.add(groupId);
        }

        @Override
        public boolean onContainsGroupId(long groupId) {
            return mGroupSelected.contains(groupId);
        }

        @Override
        public void onRemoveGroupId(long groupId) {
            mGroupSelected.remove(groupId);
        }

        @Override
        public void onGroupClear() {
            mGroupSelected.clear();
        }

        @Override
        public List<Long> getGroupsList() {
            return mGroupSelected;
        }

        @Override
        public void appendStrangeCallLogId(String callLogId) {
            mForSearchCallLog.append("," + callLogId);
        }

        @Override
        public String getCallLogSelection() {
            String strangeCallLog = mForSearchCallLog.toString();
            if (TextUtils.isEmpty(strangeCallLog)) {
                return "";
            }
            return strangeCallLog.substring(1, strangeCallLog.length());
        }
    }

    public CheckListListener createListener() {
        return new CheckListListener();
    }

    private void configureSearchMode() {
        TextView topDividerLine = (TextView) findViewById(R.id.multi_pick_top_divider);
        if (mSearchUiVisible) {
            topDividerLine.setVisibility(View.VISIBLE);
            closeItem.setIcon(R.drawable.clear_dark);
            mActionBar.setHomeAsUpIndicator(R.drawable.back);
            int searchboxStatusBarColor = getColor(R.color.searchbox_phone_background_color);
            ColorDrawable searchboxStatusBarDrawable = new ColorDrawable(searchboxStatusBarColor);
            mActionBar.setBackgroundDrawable(searchboxStatusBarDrawable);

            mSelectionContainer.setVisibility(View.GONE);
            mSearchViewContainer.setVisibility(View.VISIBLE);
            mActionBar.setCustomView(mSearchViewContainer, new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT));
            mSearchView.requestFocus();
        } else {
            topDividerLine.setVisibility(View.GONE);
            int normalStatusBarColor = getColor(R.color.primary_color);
            getActionBar().setBackgroundDrawable(new ColorDrawable(normalStatusBarColor));
            mActionBar.setHomeAsUpIndicator(null);

            mSearchViewContainer.setVisibility(View.GONE);
            mSelectionContainer.setVisibility(View.VISIBLE);
            mActionBar.setCustomView(mSelectionContainer, new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT));
            if (mSearchView != null && !TextUtils.isEmpty(mSearchView.getText().toString())) {
                mSearchView.setText(null);
            }
        }
    }

    private void updateActionBar() {
        mSelectedNums = (mChoiceSet.isEmpty() ? 0 : mChoiceSet.size())
                + ((mPickMode.isPickPhone() && !mChoiceNumberSet.isEmpty())
                        ? mChoiceNumberSet.size() : 0);
        String countTitle = mContext.getResources().getString(R.string.contacts_selected,
                mSelectedNums);
        mSelectionButton.setText(countTitle);
        mSelectionMenu.getPopupList().clearItems();
        addSelectionMenuPopupListItem(countTitle);
        invalidateOptionsMenu();
    }

    @Override
    public void onBackPressed() {
        if (mSearchUiVisible) {
            mSearchUiVisible = false;
            exitSearchMode(true);
            configureSearchView();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        switch (view.getId()) {
            case R.id.search_view: {
                if (hasFocus) {
                    final InputMethodManager imm = (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mSearchView.findFocus(), 0);
                    updateState(null);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
        }

        super.onDestroy();
    }

    private void updateState(String query) {
        if (mPickMode.isPickPhone()) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction mFragmentTransaction = fragmentManager.beginTransaction();
            if (!TextUtils.isEmpty(query)) {
                if (!closeItem.isVisible()) {
                    closeItem.setVisible(true);
                }
                mViewPagerAdapter.setTabsHidden(true);
                mViewPagerLayout.setVisibility(View.GONE);
                mFragmentTransaction.show(mSearchFragment);
                mFragmentTransaction.commit();
                if (!mPickMode.isSearchMode()) {
                    mSearchUiVisible = true;
                    enterSearchMode();
                    configureSearchView();
                }
            } else {
                if (closeItem.isVisible()) {
                    closeItem.setVisible(false);
                }
            }
            mSearchFragment.doFilter(ContactsPickMode.MODE_SEARCH_GROUP, query);
        } else {
            if (!TextUtils.isEmpty(query)) {
                if (!closeItem.isVisible()) {
                    closeItem.setVisible(true);
                }
                if (!mPickMode.isSearchMode()) {
                    mSearchUiVisible = true;
                    enterSearchMode();
                    configureSearchView();
                }
            } else {
                if (closeItem.isVisible()) {
                    closeItem.setVisible(false);
                }
            }
            mContactsFragment.doFilter(query);

        }

    }

    private void showTab() {
        if (mPickMode.isPickPhone()) {
            mViewPagerAdapter.setTabsHidden(false);
            mViewPager.setCurrentItem(TAB_INDEX_CONTACTS);
        } else if (mPickMode.isPickCall()) {
            mViewPagerAdapter.setTabsHidden(true);
            mViewPager.setCurrentItem(TAB_INDEX_RECENT);
        } else {
            mViewPagerAdapter.setTabsHidden(true);
        }
    }

    private void setOkStatus() {
        if (mPickMode.isPickPhone() ? (0 == mChoiceSet.size() && 0 == mChoiceNumberSet.size())
                : 0 == mChoiceSet.size()) {
            mOKButton.setEnabled(false);
            mOKButton.setTextColor(
                    mContext.getResources().getColor(R.color.ok_or_clear_button_disable_color));
        } else {
            mOKButton.setEnabled(true);
            mOKButton.setTextColor(
                    mContext.getResources().getColor(R.color.ok_or_clear_button_normal_color));
        }
    }

    private void backupChoiceSet() {
        mBackupChoiceSet = (Bundle) mChoiceSet.clone();
    }

    private void restoreChoiceSet() {
        mChoiceSet = mBackupChoiceSet;
    }

    private void configureSearchView() {
        if (mPickMode.isPickPhone()) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction mFragmentTransaction = fragmentManager.beginTransaction();
            TextView topDividerLine = (TextView) findViewById(R.id.multi_pick_top_divider);
            if (mSearchUiVisible) {
                mViewPagerLayout.setVisibility(View.GONE);
                mFragmentTransaction.show(mSearchFragment);
                mFragmentTransaction.commit();
            } else {
                mViewPagerLayout.setVisibility(View.VISIBLE);
                mViewPagerAdapter.setTabsHidden(false);
                mFragmentTransaction.hide(mSearchFragment);
                mFragmentTransaction.commit();
            }
        }
        configureSearchMode();
    }

    private void enterSearchMode() {
        mButton_view.setVisibility(View.GONE);
        searchItem.setVisible(false);
        mPickMode.enterSearchMode();
        backupChoiceSet();
    }

    private void exitSearchMode(boolean isConfirmed) {
        mButton_view.setVisibility(View.VISIBLE);
        closeItem.setVisible(false);
        searchItem.setVisible(true);
        mPickMode.exitSearchMode();
        if (mPickMode.isPickPhone()) {
            switch (mViewPager.getCurrentItem()) {
                case TAB_INDEX_RECENT:
                    mCallLogFragment.setUserVisibleHint(true);
                    break;
                case TAB_INDEX_CONTACTS:
                    mContactsFragment.setUserVisibleHint(true);
                    break;
                case TAB_INDEX_GROUP:
                    mGroupFragment.setUserVisibleHint(true);
                    break;
            }
        } else {
            mContactsFragment.startQuery();
        }
        if (!isConfirmed) {
            restoreChoiceSet();
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        mViewPagerTabs.onPageScrolled(position, positionOffset, positionOffsetPixels);
    }

    @Override
    public void onPageSelected(int position) {
        mViewPagerTabs.onPageSelected(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        mViewPagerTabs.onPageScrollStateChanged(state);
    }

    private boolean isRTL() {
        final Locale locale = Locale.getDefault();
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
    }

    private int getRtlPosition(int position) {
        final Locale locale = Locale.getDefault();
        final int layoutDirection = TextUtils.getLayoutDirectionFromLocale(locale);
        if (isRTL()) {
            return mViewPagerAdapter.getCount() - 1 - position;
        }
        return position;
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(this).setTitle(R.string.deleteConfirmation_title)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.ContactMultiDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener()).create();
            }
            case DIALOG_DEL_CALL: {
                return new AlertDialog.Builder(this).setTitle(R.string.title_del_call)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.delete_call_alert)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener()).create();
            }
            case R.id.dialog_import_sim_contact_confirmation: {
                return new AlertDialog.Builder(this).setTitle(R.string.importConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.ContactMultiImportConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener()).create();
            }

        }

        return super.onCreateDialog(id, bundle);
    }

    private class DeleteContactsThread extends Thread implements OnCancelListener, OnClickListener {

        boolean mCanceled = false;

        private ArrayList<ContentProviderOperation> mOpsCalls = null;

        private ArrayList<ContentProviderOperation> mOpsContacts = null;

        public DeleteContactsThread() {
        }

        @Override
        public void run() {
            // The mChoiceSet object will change when activity restart, but
            // DeleteContactsThread running in background, so we need clone the
            // choiceSet to avoid ConcurrentModificationException.
            Bundle choiceSet = (Bundle) mChoiceSet.clone();
            Set<String> keySet = choiceSet.keySet();

            Iterator<String> it = keySet.iterator();

            android.content.ContentProviderOperation.Builder builder = null;

            ContentProviderOperation cpo = null;

            // Current contact count we can delete.
            int count = 0;

            // The contacts we batch delete once.
            final int BATCH_DELETE_CONTACT_NUMBER = 100;

            mOpsCalls = new ArrayList<ContentProviderOperation>();
            mOpsContacts = new ArrayList<ContentProviderOperation>();

            while (!mCanceled && it.hasNext()) {
                // Get value by key
                String[] ids = choiceSet.getStringArray(it.next());
                // Iterates ids array.
                for (String id : ids) {
                    Uri uri = null;
                    if (mPickMode.isPickCall()) {
                        uri = Uri.withAppendedPath(Calls.CONTENT_URI, String.valueOf(id));
                        builder = ContentProviderOperation.newDelete(uri);
                        cpo = builder.build();
                        mOpsCalls.add(cpo);
                    } else {
                        uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
                        long longId = Long.parseLong(id);
                        int subscription = mSimContactsOperation.getSimSubscription(longId);

                        if (subscription == SimContactsConstants.SLOT1
                                || subscription == SimContactsConstants.SLOT2) {
                            ContentValues values = mSimContactsOperation
                                    .getSimAccountValues(longId);
                            log("values is : " + values + "; sub is " + subscription);
                            int result = mSimContactsOperation.delete(values, subscription);
                            if (result == 0) {
                                mProgressDialog.incrementProgressBy(1);
                                continue;
                            }
                        }
                        builder = ContentProviderOperation.newDelete(uri);
                        cpo = builder.build();
                        mOpsContacts.add(cpo);
                    }
                }

                // If contacts more than 2000, delete all contacts
                // one by one will cause UI nonresponse.
                mProgressDialog.incrementProgressBy(1);
                // We batch delete contacts every 100.
                if (count % BATCH_DELETE_CONTACT_NUMBER == 0) {
                    batchDelete();
                }
                count++;
            }

            if (mCanceled) {
                finish();
                return;
            }

            batchDelete();
            mOpsCalls = null;
            mOpsContacts = null;
            Log.d(TAG, "DeleteContactsThread run, progress:" + mProgressDialog.getProgress());
            mProgressDialog.dismiss();
            finish();
        }

        /**
         * Batch delete contacts more efficient than one by one.
         */
        private void batchDelete() {
            try {
                mContext.getContentResolver().applyBatch(CallLog.AUTHORITY, mOpsCalls);
                mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, mOpsContacts);
                mOpsCalls.clear();
                mOpsContacts.clear();
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (OperationApplicationException e) {
                e.printStackTrace();
            }
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            Log.d(TAG, "DeleteContactsThread onCancel, progress:" + mProgressDialog.getProgress());
            // Give a toast show to tell user delete termination
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            }
        }

    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            CharSequence title = null;
            CharSequence message = null;

            if (mPickMode.isPickCall()) {
                title = getString(R.string.delete_call_title);
                message = getString(R.string.delete_call_message);
            } else if (mPickMode.isPickSim()) {
                title = getString(R.string.import_sim_contacts_title);
                message = getString(R.string.import_sim_contacts_message);
            } else {
                title = getString(R.string.delete_contacts_title);
                message = getString(R.string.delete_contacts_message);
            }

            Thread thread;
            if (mPickMode.isPickSim()) {
                thread = new ImportAllSimContactsThread();
            } else {
                thread = new DeleteContactsThread();
            }

            DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_SEARCH:
                        case KeyEvent.KEYCODE_CALL:
                            return true;
                        default:
                            return false;
                    }
                }
            };

            mProgressDialog = new ProgressDialog(MultiPickContactsActivity.this);
            mProgressDialog.setTitle(title);
            mProgressDialog.setMessage(message);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.btn_cancel), (OnClickListener) thread);
            mProgressDialog.setOnCancelListener((OnCancelListener) thread);
            mProgressDialog.setOnKeyListener(keyListener);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(mChoiceSet.size());

            // set dialog can not be canceled by touching outside area of
            // dialog.
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();

            thread.start();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.btn_ok:
                if (mPickMode.isSearchMode()) {
                    exitSearchMode(true);
                }
                if (mPickMode.isPickContact()) {
                    if (SimContactsConstants.ACTION_MULTI_PICK.equals(getIntent().getAction())) {
                            switch (getIntent().getIntExtra(ADD_MOVE_GROUP_MEMBER_KEY,
                                    ACTION_DEFAULT_VALUE)) {
                                case ACTION_ADD_GROUP_MEMBER:
                                    if (mChoiceSet.size() > MAX_CONTACTS_NUM_TO_GROUP) {
                                        Toast.makeText(mContext,
                                                mContext.getString(
                                                        R.string.too_many_contacts_add_to_group,
                                                        MAX_CONTACTS_NUM_TO_GROUP),
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    this.setResult(RESULT_OK, new Intent().putExtras(mChoiceSet));
                                    finish();
                                    break;
                                case ACTION_MOVE_GROUP_MEMBER:
                                    if (mChoiceSet.size() > MAX_CONTACTS_NUM_TO_GROUP) {
                                        Toast.makeText(mContext,
                                                mContext.getString(
                                                        R.string.too_many_contacts_add_to_group,
                                                        MAX_CONTACTS_NUM_TO_GROUP),
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    showGroupSelectionList(
                                            getIntent().getStringExtra(
                                                    SimContactsConstants.ACCOUNT_TYPE),
                                            getIntent().getLongExtra(KEY_GROUP_ID, -1));
                                    break;
                                default:
                                    if (mChoiceSet.size() > MAX_CONTACTS_NUM_TO_SELECT_ONCE) {
                                        Toast.makeText(mContext,
                                                mContext.getString(
                                                        R.string.too_many_contacts_add_to_group,
                                                        MAX_CONTACTS_NUM_TO_SELECT_ONCE),
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    Intent intent = new Intent();
                                    Bundle bundle = new Bundle();
                                    bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                                    intent.putExtras(bundle);
                                    this.setResult(RESULT_OK, intent);
                                    finish();
                            }
                    } else if (mChoiceSet.size() > 0) {
                        showDialog(R.id.dialog_delete_contact_confirmation);
                    }
                } else if (mPickMode.isPickPhone()) {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                    bundle.putBundle(SimContactsConstants.RESULT_KEY_ONLY_NUMBER,
                            mChoiceNumberSet);
                    intent.putExtras(bundle);
                    this.setResult(RESULT_OK, intent);
                    finish();
                } else if (mPickMode.isPickSim()) {
                    if (mChoiceSet.size() > 0) {
                        showDialog(R.id.dialog_import_sim_contact_confirmation);
                    }
                } else if (mPickMode.isPickEmail()) {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                    intent.putExtras(bundle);
                    this.setResult(RESULT_OK, intent);
                    finish();
                } else if (mPickMode.isPickCall()) {
                    if (mChoiceSet.size() > 0) {
                        if (mPickMode.isSelectCallLog()) {
                            Intent intent = new Intent();
                            Bundle bundle = new Bundle();
                            bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                            intent.putExtras(bundle);
                            this.setResult(RESULT_OK, intent);
                            finish();
                        } else {
                            showDialog(DIALOG_DEL_CALL);
                        }
                    }
                } else if (mPickMode.isPickContactInfo()) {
                    if (mSelectedContactInfo == null) {
                        mSelectedContactInfo = new HashMap<String, List<String[]>>();
                    }
                    Intent intent = new Intent();
                    String result = getSelectedContactInfo(mSelectedContactInfo);
                    if (result != null) {
                        putExtraWithContact(intent, result);
                        this.setResult(RESULT_OK, intent);
                    } else {
                        this.setResult(RESULT_CANCELED);
                    }
                    finish();
                } else if (mPickMode.isPickContactVcard()) {
                    String uri = getSelectedContactVcard();
                    setResultAndFinish(uri);
                }
                break;
            case R.id.btn_cancel:
                if (!mPickMode.isSearchMode()) {
                    this.setResult(this.RESULT_CANCELED);
                    finish();
                } else {
                    exitSearchMode(false);
                }
                break;
        }
    }

    private String getLookupKey(Set<String> mSelectedIds) {
        StringBuilder sb = new StringBuilder();
        sb.append(Contacts._ID);
        sb.append(" IN ( ");
        for (String contactId : mSelectedIds) {
            sb.append(contactId);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length()-1);
        sb.append(" )");

        final Cursor c = getContentResolver().query(Contacts.CONTENT_URI, new String[]{
                Contacts.LOOKUP_KEY}, sb.toString(), null, null);
        if (c == null) {
            return null;
        }

        sb = new StringBuilder();
        try {
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                final String lookupKey = c.getString(0);
                sb.append(lookupKey);
                sb.append(":");
            }
        } finally {
            c.close();
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private String getSelectedContactVcard() {
        Set<String> keySet = mChoiceSet.keySet();
        Uri uri = null;
        if (mChoiceSet.size() == 1) {
            for (String key : keySet) {
                // 1, represent lookupkey
                String result = mChoiceSet.getStringArray(key)[1];
                uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, result);
            }
        } else {
            uri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI,
                    Uri.encode(getLookupKey(keySet)));
        }
        return uri.toString();
    }

    private void setResultAndFinish(String uri) {
        Intent intent = new Intent();
        if (uri != null) {
            putExtraWithContact(intent, uri);
            setResult(RESULT_OK, intent);
        } else {
            this.setResult(RESULT_CANCELED);
        }
        finish();
    }

    private String getSelectedContactInfo(HashMap<String, List<String[]>> map) {
        Set<String> keySet = mChoiceSet.keySet();
        for (String key : keySet) {
            String[] value = mChoiceSet.getStringArray(key);
            String contactId = value[0];
            if (map.containsKey(contactId)) {
                map.get(contactId).add(value);
            } else {
                ArrayList<String[]> temp = new ArrayList<String[]>();
                temp.add(value);
                map.put(contactId, temp);
            }
        }
        StringBuilder result = new StringBuilder();
        Set<String> keySetData = map.keySet();
        for (String key : keySetData) {
            List<String[]> dataList = map.get(key);
            result.append(CONTACT_SEP_LEFT);
            // append the name string.
            result.append(getString(R.string.contact_info_text_as_name));
            result.append(dataList.get(0)[1]);
            for (int i = 0; i < dataList.size(); i++) {
                String[] values = dataList.get(i);
                result.append(ITEM_SEP);
                if (values[2] != null) {
                    // append the number
                    result.append(getString(R.string.contact_info_text_as_phone));
                    result.append(values[2]);
                } else {
                    // append the email
                    result.append(getString(R.string.contact_info_text_as_email));
                    result.append(values[3]);
                }
            }
            result.append(CONTACT_SEP_RIGHT);
        }
        return result.toString();
    }

    private void putExtraWithContact(Intent intent, String result) {
        if (mPickMode.isPickContactInfo()) {
            intent.putExtra(EXTRA_INFO, result);
        } else if (mPickMode.isPickContactVcard()) {
            intent.putExtra(EXTRA_VCARD, result);
        }
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
    }

    protected static void log(String msg) {
        if (DEBUG)
            Log.d(TAG, msg);
    }

    private class ImportAllSimContactsThread extends Thread
            implements OnCancelListener, DialogInterface.OnClickListener {
        boolean mCanceled = false;
        // The total count how many to import.
        private int mTotalCount = 0;
        // The real count have imported.
        private int mActualCount = 0;

        private Account mAccount;

        public ImportAllSimContactsThread() {
        }

        @Override
        public void run() {
            final ContentValues emptyContentValues = new ContentValues();
            final ContentResolver resolver = mContext.getContentResolver();

            String type = getIntent().getStringExtra(SimContactsConstants.ACCOUNT_TYPE);
            String name = getIntent().getStringExtra(SimContactsConstants.ACCOUNT_NAME);
            mAccount = new Account(name != null ? name : SimContactsConstants.PHONE_NAME,
                    type != null ? type : SimContactsConstants.ACCOUNT_TYPE_PHONE);
            log("import sim contact to account: " + mAccount);
            mTotalCount = mChoiceSet.size();
            ArrayList<ContentProviderOperation> operationList =
                    new ArrayList<ContentProviderOperation>();

            for (String key : mChoiceSet.keySet()) {
                if (mCanceled) {
                    if (operationList.size() > 0) {
                        doApplyBatch(operationList, resolver);
                    }
                    break;
                }
                String[] values = mChoiceSet.getStringArray(key);
                int firstBatch = operationList.size();
                buildSimContentProviderOperationList(values, resolver, mAccount, firstBatch,
                        operationList);
                int size = operationList.size();
                if (size > 0 && BUFFER_LENGTH - size < 10) {
                    doApplyBatch(operationList, resolver);
                }
                mActualCount++;
                mProgressDialog.incrementProgressBy(1);
            }
            if (operationList.size() > 0) {
                doApplyBatch(operationList, resolver);
            }
            finish();
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            // Give a toast show to tell user import termination.
            if (mActualCount < mTotalCount) {
                Toast.makeText(mContext, R.string.import_stop, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, R.string.import_finish, Toast.LENGTH_SHORT).show();
            }
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            }
        }
    }

    private static void doApplyBatch(ArrayList<ContentProviderOperation> operationList,
            ContentResolver resolver) {
        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (Exception e) {
            Log.w(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } finally {
            operationList.clear();
        }
    }

    private static void buildSimContentProviderOperationList(String[] values,
            final ContentResolver resolver, Account account, int backReference,
            ArrayList<ContentProviderOperation> operationList) {
        final String name = values[SIM_COLUMN_DISPLAY_NAME];
        final String phoneNumber = values[SIM_COLUMN_NUMBER];
        final String emailAddresses = values[SIM_COLUMN_EMAILS];
        final String anrs = values[SIM_COLUMN_ANRS];
        final String[] emailAddressArray;
        final String[] anrArray;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        if (!TextUtils.isEmpty(anrs)) {
            anrArray = anrs.split(SimContactsConstants.ANR_SEP);
        } else {
            anrArray = null;
        }
        log(" actuallyImportOneSimContact: name= " + name + ", phoneNumber= " + phoneNumber
                + ", emails= " + emailAddresses + ", anrs= " + anrs + ", account is " + account
                + ", backReference=" + backReference);
        ContentProviderOperation.Builder builder = ContentProviderOperation
                .newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
        }
        operationList.add(builder.build());

        if (!TextUtils.isEmpty(name)) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, backReference);
            builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
            builder.withValue(StructuredName.DISPLAY_NAME, name);
            operationList.add(builder.build());
        }

        if (!TextUtils.isEmpty(phoneNumber)) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(Phone.RAW_CONTACT_ID, backReference);
            builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
            builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
            builder.withValue(Phone.NUMBER, phoneNumber);
            builder.withValue(Data.IS_PRIMARY, 1);
            operationList.add(builder.build());
        }

        if (anrArray != null) {
            for (String anr : anrArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Phone.RAW_CONTACT_ID, backReference);
                builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                builder.withValue(Phone.NUMBER, anr);
                operationList.add(builder.build());
            }
        }

        if (emailAddresses != null) {
            for (String emailAddress : emailAddressArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Email.RAW_CONTACT_ID, backReference);
                builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                builder.withValue(Email.ADDRESS, emailAddress);
                operationList.add(builder.build());
            }
        }

    }

    /**
     * After turn on airplane mode, cancel import sim contacts operation.
     */
    private void cancelSimContactsImporting() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.cancel();
        }
    }

    private void showGroupSelectionList(String accountType, long srcGroupId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.groupsLabel));
        ContentResolver resolver = getContentResolver();
        String selection = Groups.ACCOUNT_TYPE + " =? AND " + Groups.DELETED + " != ? AND ("
                + Groups.SOURCE_ID + "!='RCS'" + " OR " + Groups.SOURCE_ID + " IS NULL)";

        ArrayList<String> items = new ArrayList<String>();

        mGroupIds.clear();
        items.clear();
        Cursor cursor = resolver.query(Groups.CONTENT_URI,
                new String[] {
                        Groups._ID, Groups.TITLE
        }, selection,
                new String[] {
                        accountType, "1"
        }, null);
        if (cursor == null || cursor.getCount() == 0) {
            Toast.makeText(mContext, R.string.message_can_not_move_members, Toast.LENGTH_LONG)
                    .show();
            return;
        } else {
            try {
                while (cursor.moveToNext()) {
                    if (!cursor.getString(0).equals(String.valueOf(srcGroupId))) {
                        mGroupIds.add(cursor.getLong(0));
                        items.add(cursor.getString(1));
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (mGroupIds.size() == 0) {
            Toast.makeText(mContext, R.string.message_can_not_move_members, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        String[] groupItem = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            groupItem[i] = items.get(i);
        }
        builder.setItems(groupItem, new ChooseActionListener());
        builder.create().show();
    }

    private class ChooseActionListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            new MoveGroupMemberTask(mChoiceSet, getIntent().getLongExtra(KEY_GROUP_ID, -1),
                    mGroupIds.get(which)).execute();
        }
    }

    class MoveGroupMemberTask extends AsyncTask<Object, Object, Object> {

        private static final String GROUP_QUERY_GROUP_MEMBER_SELECTION = Data.MIMETYPE + "=? AND "
                + GroupMembership.GROUP_ROW_ID + "=?";

        private static final String GROUP_QUERY_RAW_CONTACTS_SELECTION = RawContacts.CONTACT_ID
                + "=?";

        private static final String GROUP_DELETE_MEMBER_SELECTION = Data.CONTACT_ID + "=? AND "
                + Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?";

        private Bundle mChoiceSet;
        private long mDestGroupId;
        private long mSrcGroupId;
        private boolean mCanceled = false;

        private ArrayList<ContentProviderOperation> mAddOrMoveOperation;
        private ArrayList<ContentProviderOperation> mDeleteOperation;
        private ArrayList<String> mGroupMemberList = new ArrayList<String>();

        public MoveGroupMemberTask(Bundle choiceSet, long srcGroupId, long destGroupId) {
            mChoiceSet = choiceSet;
            mSrcGroupId = srcGroupId;
            mDestGroupId = destGroupId;
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(MultiPickContactsActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setTitle(getProgressDialogTitle());
            mProgressDialog.setMessage(getProgressDialogMessage());
            mProgressDialog.setMax(mChoiceSet != null ? mChoiceSet.keySet().size() : 100);
            mProgressDialog.setProgress(0);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    mCanceled = true;
                }
            });
            mProgressDialog.show();
        }

        @Override
        protected Bundle doInBackground(Object... params) {
            if (mChoiceSet == null || mSrcGroupId <= 0) {
                return null;
            }
            ContentResolver resolver = mContext.getContentResolver();
            Cursor memberCursor = null;

            memberCursor = resolver.query(Data.CONTENT_URI, new String[] {
                    Data.CONTACT_ID
            },
                    GROUP_QUERY_GROUP_MEMBER_SELECTION, new String[] {
                            GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(mDestGroupId)
            },
                    null);

            if (memberCursor != null && memberCursor.getCount() > 0) {
                try {
                    while (memberCursor.moveToNext()) {
                        // Mark those contacts that already exist in the dest
                        // group
                        mGroupMemberList.add(String.valueOf(memberCursor.getLong(0)));
                    }
                } finally {
                    if (memberCursor != null) {
                        memberCursor.close();
                    }
                }
            }

            Set<String> keySet = mChoiceSet.keySet();
            Iterator<String> it = keySet.iterator();

            ContentProviderOperation.Builder builder;

            mAddOrMoveOperation = new ArrayList<ContentProviderOperation>();
            mDeleteOperation = new ArrayList<ContentProviderOperation>();
            String id;
            int count = 0;
            int maxSize = mChoiceSet.keySet().size();
            while (!mCanceled && it.hasNext()) {
                id = it.next();
                ++count;

                if (mDestGroupId <= 0) {
                    // Invalid group id, cancel the task
                    return null;
                }
                if (mProgressDialog != null && mProgressDialog.isShowing()
                        && count < maxSize - (maxSize) / 100) {
                    mProgressDialog.incrementProgressBy(1);
                }
                if (mGroupMemberList.contains(id)) {
                    // If the contact already exists in the group, need to
                    // delete those
                    // contacts that in the previous group
                    builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
                    builder.withSelection(GROUP_DELETE_MEMBER_SELECTION, new String[] {
                            id,
                            GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(mSrcGroupId)
                    });
                    mDeleteOperation.add(builder.build());
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(GroupMembership.GROUP_ROW_ID, mDestGroupId);
                builder = ContentProviderOperation.newUpdate(Data.CONTENT_URI);
                builder.withSelection(GROUP_DELETE_MEMBER_SELECTION, new String[] {
                        id,
                        GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(mSrcGroupId)
                });
                builder.withValues(values);
                mAddOrMoveOperation.add(builder.build());
            }

            if (mDeleteOperation.size() > 0) {
                if (mDeleteOperation.size() > BUFFER_LENGTH) {
                    addOrMoveApplyBatchByBuffer(mDeleteOperation, resolver);
                } else {
                    try {
                        resolver.applyBatch(ContactsContract.AUTHORITY, mDeleteOperation);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    } catch (OperationApplicationException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (mAddOrMoveOperation.size() > BUFFER_LENGTH) {
                addOrMoveApplyBatchByBuffer(mAddOrMoveOperation, resolver);
            } else {
                try {
                    resolver.applyBatch(ContactsContract.AUTHORITY, mAddOrMoveOperation);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (OperationApplicationException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                finish();
            }
        }

        private void addOrMoveApplyBatchByBuffer(ArrayList<ContentProviderOperation> list,
                ContentResolver cr) {
            final ArrayList<ContentProviderOperation> temp =
                    new ArrayList<ContentProviderOperation>(BUFFER_LENGTH);
            int bufferSize = list.size() / BUFFER_LENGTH;
            for (int index = 0; index <= bufferSize; index++) {
                temp.clear();
                if (index == bufferSize) {
                    for (int i = index * BUFFER_LENGTH; i < list.size(); i++) {
                        temp.add(list.get(i));
                    }
                } else {
                    for (int i = index * BUFFER_LENGTH; i < index * BUFFER_LENGTH
                            + BUFFER_LENGTH; i++) {
                        temp.add(list.get(i));
                    }
                }
                if (!temp.isEmpty()) {
                    try {
                        cr.applyBatch(ContactsContract.AUTHORITY, temp);
                    } catch (Exception e) {
                        Log.e(TAG, "apply batch by buffer error:" + e);
                    }
                }
            }
        }

        private String getProgressDialogTitle() {
            return getString(R.string.title_move_members);
        }

        private String getProgressDialogMessage() {
            return getString(R.string.message_move_members);
        }
    }

}
