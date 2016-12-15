/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

package com.android.contacts.multipicker;

import android.accounts.Account;
import android.app.Activity;
import android.app.ExpandableListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Groups;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorTreeAdapter;
import android.widget.AbsListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.MultiPickContactsActivity;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.PhoneAccountType;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.common.widget.CheckableImageView;
import com.android.contacts.list.OnCheckListActionListener;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class GroupsFragment extends ExpandableListFragment implements OnGroupClickListener {
    private static final String TAG = "GroupsFragment";
    private static final boolean DEBUG = false;

    private Context mContext;

    private OnCheckListActionListener mCheckListListener;

    private static final int QUERY_TOKEN = 44;

    private Cursor allContactsInGroups;

    private ExpandableListView mList = null;
    private GroupsAdapter mAdapter = null;
    private Cursor mGroupsCursor = null;

    private Map<Long, String[]> mAllContactsCurosrMap;

    private ArrayList<String> checkedList;

    private static final String[] PHONES_PROJECTION = new String[] {
            Phone._ID, // 0
            Phone.TYPE, // 1
            Phone.LABEL, // 2
            Phone.NUMBER, // 3
            Phone.DISPLAY_NAME, // 4
            Contacts.PHOTO_ID, // 5
            Contacts.LOOKUP_KEY, // 6
            Phone.CONTACT_ID, // 7
            RawContacts.ACCOUNT_TYPE, // 8
            RawContacts.ACCOUNT_NAME, // 9
            Contacts.PHOTO_THUMBNAIL_URI // 10
    };

    private static final String[] GROUP_PROJECTION = new String[] {
            Groups._ID, // 0
            Groups.TITLE, // 1
            Groups.SUMMARY_COUNT, // 2
            Groups.SUMMARY_WITH_PHONES, // 3
            Groups.ACCOUNT_NAME, // 4
            Groups.ACCOUNT_TYPE, // 5
            Groups.DATA_SET // 6
    };

    // groups column
    private static final int GROUP_ID = 0;
    private static final int GROUP_TITLE = 1;
    private static final int GROUP_SUMMARY_COUNT = 2;
    private static final int GROUP_SUMMARY_WITH_PHONES = 3;
    private static final int GROUP_ACCOUNT_NAME = 4;
    private static final int GROUP_ACCOUNT_TYPE = 5;
    private static final int GROUP_ACCOUNT_DATA_SET = 6;
    // contacts column
    private static final int PHONE_COLUMN_ID = 0;
    private static final int PHONE_COLUMN_TYPE = 1;
    private static final int PHONE_COLUMN_LABEL = 2;
    private static final int PHONE_COLUMN_NUMBER = 3;
    private static final int PHONE_COLUMN_DISPLAY_NAME = 4;
    private static final int PHONE_COLUMN_PHOTO_ID = 5;
    private static final int PHONE_COLUMN_LOOKUP_KEY = 6;
    private static final int PHONE_COLUMN_CONTACT_ID = 7;
    private static final int PHONE_COLUMN_ACCOUNT_TYPE = 8;
    private static final int PHONE_COLUMN_ACCOUNT_NAME = 9;
    private static final int PHONE_COLUMN_PHOTO_THUMBNAIL_URI = 10;

    private final class ContactItemCache {
        long id;
        String name;
        String number;
        String lookupKey;
        String type;
        String label;
        String contact_id;
        String photoUri;
        long photoId;
    }

    private final class GroupItemCache {
        long id;
        String title;
        String summary_sount;
        String summary_with_phones;
        String account_name;
        String account_type;
        long photoId;
        int phone_numbers;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mList = getExpandableListView();
        mList.setGroupIndicator(null);
        mList.setOnGroupClickListener(this);
        mList.setOnChildClickListener(this);
        mList.setDivider(null);
        if (mCheckListListener == null) {
            mCheckListListener = ((MultiPickContactsActivity) getActivity())
                    .createListener();
        }
        if (mAdapter == null) {
            if (mAllContactsCurosrMap == null) {
                mAllContactsCurosrMap = new HashMap<Long, String[]>();
            }
            mAdapter = new GroupsAdapter(mContext, null, R.layout.pick_group_list_item_view,
                    new String[] {}, new int[] {}, R.layout.pick_child_list_item_view,
                    new String[] {}, new int[] {});
            View view = new View(mContext);
            AbsListView.LayoutParams layoutParams = new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    (int)(mContext.getResources().getDimension(R.dimen.header_listview_height)));
            view.setLayoutParams(layoutParams);
            mList.addHeaderView(view, null, false);
            setListAdapter(mAdapter);
            getGroupsCursor(mAdapter.getQueryHandler());
            checkedList = new ArrayList<String>();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;

        allContactsInGroups = getAllContactsCursorInGroups();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        fillAllContactsCursorMap();
    }

    @Override
    public void onDestroy() {
        mAdapter.getQueryHandler().removeCallbacksAndMessages(QUERY_TOKEN);

        if (mAdapter.getCursor() != null) {
            mAdapter.getCursor().close();
        }
        mAdapter.notifyDataSetInvalidated();
        if(allContactsInGroups!=null)
            allContactsInGroups.close();

        if (mAllContactsCurosrMap != null) {
            mAllContactsCurosrMap.clear();
        }

        super.onDestroy();
    }

    @Override
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
        mCheckListListener.onHideSoftKeyboard();

        GroupItemCache groupCache = (GroupItemCache) v.getTag();

        if (mCheckListListener.onContainsGroupId(groupCache.id)) {
            // group item click is cancel status
            mCheckListListener.onRemoveGroupId(groupCache.id);
        } else {
            // group item click is checked status
            mCheckListListener.addGroupId(groupCache.id);
        }

        // get the all contacts data in current group
        Cursor cursor = getContactsDetailCursor(groupCache.id);

        if (cursor == null) {
            return false;
        }

        String[] dataIds = new String[cursor.getCount()];
        for (int i = 0; i < cursor.getCount(); i++) {
            cursor.moveToPosition(i);
            dataIds[i] = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
        }
        if (!Arrays.equals(mAllContactsCurosrMap.get(groupCache.id), dataIds)) {
            mAllContactsCurosrMap.put(groupCache.id, dataIds);
        }

        if (!cursor.moveToFirst()) {
            return false;
        }

        try {
            // iterate over contacts information for current group
            do {
                String key = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
                ContactItemCache cache = new ContactItemCache();
                cache.id = cursor.getLong(PHONE_COLUMN_ID);
                cache.name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                cache.type = cursor.getString(PHONE_COLUMN_TYPE);
                cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                cache.contact_id = cursor.getString(PHONE_COLUMN_CONTACT_ID);
                if (mCheckListListener.onContainsGroupId(groupCache.id)) {
                    if (!mCheckListListener.onContainsKey(key)) {
                        String[] value = null;
                        value = new String[] {
                                cache.name, cache.number,
                                cache.type, cache.label,
                                cache.contact_id
                        };
                        mCheckListListener.putValue(key, value);
                        if (!checkedList.contains(key)) {
                            checkedList.add(key);
                        }
                    }
                } else {
                    if (mCheckListListener.onContainsKey(key)) {
                        mCheckListListener.onRemove(key);
                        if (checkedList.contains(key)) {
                            checkedList.remove(key);
                        }
                    }
                }
            } while (cursor.moveToNext());

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (mCheckListListener.onContainsGroupId(groupCache.id)) {
            addGroupsChecked();
        } else {
            removeGroupsChecked();
        }

        mCheckListListener.onUpdateActionBar();
        mAdapter.notifyDataSetChanged();
        return true;
    }

    /**
     * remove checked group item for groups
     */
    private void removeGroupsChecked() {
        // get selected group items
        List<Long> groups = mCheckListListener.getGroupsList();
        Iterator it = groups.iterator();
        // iterate list
        while (it.hasNext()) {
            long groupId = (long) it.next();
            String[] dataIds = mAllContactsCurosrMap.get(groupId);
            for (int i = 0; i < dataIds.length; i++) {
                // group contacts not in checked items
                if (!checkedList.contains(dataIds[i])) {
                    // remove group id for group list
                    it.remove();
                    break;
                }
            }
        }
    }

    /**
     * add checked group item for groups
     */
    private void addGroupsChecked() {
        for (int groupPosition = 0; groupPosition < mAllContactsCurosrMap.size(); groupPosition++) {
            mGroupsCursor.moveToPosition(groupPosition);
            long groupCacheId = mGroupsCursor.getLong(GROUP_ID);
            // group item is checked status
            if (!mCheckListListener.onContainsGroupId(groupCacheId)) {
                boolean isGroupChecked = true;
                // get contacts dataId for group
                String[] dataIds = mAllContactsCurosrMap.get(groupCacheId);
                // determine all selected status of the group contacts
                for (int i = 0; i < dataIds.length; i++) {
                    if (!checkedList.contains(dataIds[i])) {
                        isGroupChecked = false;
                        break;
                    }
                }
                // is group item checked status, add checked group itme for
                // groups
                if (isGroupChecked) {
                    mCheckListListener.addGroupId(groupCacheId);
                }
            }
        }
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
            int childPosition, long id) {
        ContactItemCache cache = (ContactItemCache) v.getTag();
        String key = String.valueOf(cache.id);

        // is contact item checked
        if (!mCheckListListener.onContainsKey(key)) {
            String[] value = null;
            value = new String[] {
                    cache.name, cache.number, cache.type, cache.label,
                    cache.contact_id
            };
            if (!checkedList.contains(key)) {
                checkedList.add(key);
            }
            // add contact item for contacts list, contact item setting selected
            // status
            mCheckListListener.putValue(key, value);
            addGroupsChecked();
        } else {
            // remove checked contact item for contacts list
            mCheckListListener.onRemove(key);
            if (checkedList.contains(key)) {
                checkedList.remove(key);
            }
            // remove checked group item for groups list
            removeGroupsChecked();
        }

        mCheckListListener.onUpdateActionBar();
        mAdapter.notifyDataSetChanged();

        return true;
    }

    private void init(Cursor c) {
        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(c);
    }

    private String getSortOrder(String[] projection) {
        return projection[5] + " desc," + projection[1] + " asc";
    }

    private Cursor getGroupsCursor(AsyncQueryHandler async) {

        Uri uri = Groups.CONTENT_SUMMARY_URI;

        StringBuilder selection = new StringBuilder();
        selection.append(Groups.SUMMARY_WITH_PHONES + "<>0 AND " + Groups.ACCOUNT_TYPE
                + " NOT NULL AND " + Groups.ACCOUNT_NAME + " NOT NULL AND " + Groups.AUTO_ADD
                + "=0 AND " + Groups.FAVORITES + "=0 AND " + Groups.DELETED + "!=1");

        Cursor ret = null;
        if (async != null) {
            async.startQuery(QUERY_TOKEN, null, uri, GROUP_PROJECTION, selection.toString(), null,
                    getSortOrder(GROUP_PROJECTION));
        } else {
            ret = mContext.getContentResolver().query(uri, GROUP_PROJECTION, selection.toString(),
                    null, getSortOrder(GROUP_PROJECTION));
        }
        return ret;
    }

    private String getSortOrder() {
        return RawContacts.SORT_KEY_PRIMARY;
    }

    private Cursor getContactsDetailCursor(long groupId) {

        StringBuilder selection = new StringBuilder();
        selection.append(Data.RAW_CONTACT_ID + " IN (" + " SELECT DISTINCT " + Data.RAW_CONTACT_ID
                + " FROM view_data WHERE " + Data.MIMETYPE + "=?" + " AND "
                + GroupMembership.GROUP_ROW_ID + "=?)");

        Cursor cursor = mContext.getContentResolver().query(Phone.CONTENT_URI, PHONES_PROJECTION,
                selection.toString(), createSelectionArgs(groupId), getSortOrder());

        return cursor;
    }

    public Cursor getAllContactsInGroups() {
        return allContactsInGroups;
    }

    private Cursor getAllContactsCursorInGroups() {
        StringBuilder selection = new StringBuilder();
        selection.append(Data.RAW_CONTACT_ID + " IN (" + " SELECT DISTINCT " + Data.RAW_CONTACT_ID
                + " FROM view_data WHERE " + Data.MIMETYPE + "=?)");

        Cursor cursor = mContext.getContentResolver().query(Phone.CONTENT_URI, PHONES_PROJECTION,
                selection.toString(), createSelectionArgs(), null);

        return cursor;
    }

    private String[] createSelectionArgs(long groupId) {
        List<String> selectionArgs = new ArrayList<String>();
        selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
        selectionArgs.add(String.valueOf(groupId));
        return selectionArgs.toArray(new String[0]);
    }

    private String[] createSelectionArgs() {
        List<String> selectionArgs = new ArrayList<String>();
        selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
        return selectionArgs.toArray(new String[0]);
    }

    public void setCheckListListener(OnCheckListActionListener checkListListener) {
        mCheckListListener = checkListListener;
    }

    private class GroupsAdapter extends SimpleCursorTreeAdapter {
        private AsyncQueryHandler mQueryHandler;
        private ContactPhotoManager mContactPhotoManager;

        LayoutInflater mInflater;
        AccountTypeManager mAccountTypes;

        class QueryHandler extends AsyncQueryHandler {

            public QueryHandler(ContentResolver res) {
                super(res);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                init(cursor);
            }
        }

        public GroupsAdapter(Context context, Cursor cursor, int expandedGroupLayout,
                String[] groupFrom, int[] groupTo, int childLayout, String[] childFrom,
                int[] childTo) {
            super(context, cursor, expandedGroupLayout, groupFrom, groupTo, childLayout, childFrom,
                    childTo);
            mQueryHandler = new QueryHandler(context.getContentResolver());
            mContactPhotoManager = ContactPhotoManager.getInstance(context);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mAccountTypes = AccountTypeManager.getInstance(context);
        }

        public AsyncQueryHandler getQueryHandler() {
            return mQueryHandler;
        }

        @Override
        public View newChildView(Context context, Cursor cursor, boolean isLastChild,
                ViewGroup parent) {
            View view = null;
            view = super.newChildView(context, cursor, isLastChild, parent);
            ContactItemCache cache = new ContactItemCache();
            view.setTag(cache);
            return view;
        }

        @Override
        public View newGroupView(Context context, Cursor cursor, boolean isExpanded,
                ViewGroup parent) {
            View view = null;
            view = super.newGroupView(context, cursor, isExpanded, parent);
            GroupItemCache groupCache = new GroupItemCache();
            view.setTag(groupCache);
            return view;
        }

        private void setGroupChecked(View view, Cursor cursor, GroupItemCache groupCache,
                int groupPosition) {
            CheckableImageView photoView = (CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo);
            groupCache.title = cursor.getString(GROUP_TITLE);
            int res_id;
            switch (groupPosition % 6) {
                case 0:
                    res_id = R.drawable.group_icon_1;
                    break;
                case 1:
                    res_id = R.drawable.group_icon_2;
                    break;
                case 2:
                    res_id = R.drawable.group_icon_3;
                    break;
                case 3:
                    res_id = R.drawable.group_icon_4;
                    break;
                case 4:
                    res_id = R.drawable.group_icon_5;
                    break;
                case 5:
                    res_id = R.drawable.group_icon_6;
                    break;
                default:
                    res_id = R.drawable.group_icon_6;
                    break;
            }

            if (res_id != 0) {
                Drawable drawable = null;
                drawable = mContext.getDrawable(res_id);
                photoView.setImageDrawable(drawable);
                photoView.setChecked(mCheckListListener.onContainsGroupId(groupCache.id), false);
                if (photoView.isChecked()) {
                    view.setActivated(true);
                } else {
                    view.setActivated(false);
                }
            }
        }

        private boolean isFirstGroupAccount(Cursor cursor, GroupItemCache groupCache) {
            int position = cursor.getPosition();
            String accountName = groupCache.account_name = cursor.getString(GROUP_ACCOUNT_NAME);
            String accountType = groupCache.account_type= cursor.getString(GROUP_ACCOUNT_TYPE);
            String dataSet = cursor.getString(GROUP_ACCOUNT_DATA_SET);
            int previousIndex = position - 1;
            boolean isFirstGroupInAccount = true;
            if (previousIndex >= 0 && cursor.moveToPosition(previousIndex)) {
                String previousGroupAccountName = cursor.getString(GROUP_ACCOUNT_NAME);
                String previousGroupAccountType = cursor.getString(GROUP_ACCOUNT_TYPE);
                String previousGroupDataSet = cursor.getString(GROUP_ACCOUNT_DATA_SET);

                if (accountName.equals(previousGroupAccountName) &&
                        accountType.equals(previousGroupAccountType) &&
                        Objects.equal(dataSet, previousGroupDataSet)) {
                    isFirstGroupInAccount = false;
                }
            }
            cursor.moveToPosition(position);
            return isFirstGroupInAccount;
        }

        @Override
        protected void bindGroupView(View view, Context context, Cursor cursor,
                boolean isExpanded) {
            GroupItemCache groupCache = (GroupItemCache) view.getTag();

            boolean isFirstGroupInAccount = isFirstGroupAccount(cursor, groupCache);
            TextView accountNameView = (TextView) view.findViewById(R.id.account_name);
            TextView accountTypeView = (TextView) view.findViewById(R.id.account_type);
            if (isFirstGroupInAccount) {
                AccountType accountType = mAccountTypes
                        .getAccountType(groupCache.account_type, null);
                accountTypeView
                        .setText(accountType.getDisplayLabel(context));

                if (PhoneAccountType.ACCOUNT_TYPE.equals(groupCache.account_type)) {
                    accountNameView.setVisibility(View.GONE);
                } else {
                    accountNameView.setVisibility(View.VISIBLE);
                    accountNameView.setText(groupCache.account_name);
                }
                accountTypeView.setVisibility(View.VISIBLE);
            } else {
                accountNameView.setVisibility(View.GONE);
                accountTypeView.setVisibility(View.GONE);
            }

            TextView tv = (TextView) view.findViewById(R.id.name);
            String name = cursor.getString(GROUP_TITLE);
            tv.setText(name);
            TextView cv = (TextView) view.findViewById(R.id.number_count);
            groupCache.id = cursor.getLong(GROUP_ID);
            if (groupCache.phone_numbers == 0) {
                String[] dataIds = mAllContactsCurosrMap.get(groupCache.id);
                if (dataIds != null)
                    groupCache.phone_numbers = dataIds.length;
            }
            String summary_count = context.getResources().getString(R.string.summary_count_numbers,
                    String.valueOf(groupCache.phone_numbers));
            cv.setText(summary_count);

            final int groupPosition = cursor.getPosition();
            setGroupChecked(view, cursor, groupCache, groupPosition);

            final ImageView expandGroup = (ImageView) view.findViewById(R.id.expand_group);
            expandGroup.setImageResource(mList.isGroupExpanded(groupPosition)
                    ? R.drawable.ic_menu_expander_minimized_holo_light
                    : R.drawable.ic_menu_expander_maximized_holo_light);
            expandGroup.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean expand = mList.isGroupExpanded(groupPosition);
                    if (expand) {
                        expandGroup
                                .setImageResource(R.drawable.ic_menu_expander_minimized_holo_light);
                        mList.collapseGroup(groupPosition);
                    } else {
                        expandGroup
                                .setImageResource(R.drawable.ic_menu_expander_maximized_holo_light);
                        mList.expandGroup(groupPosition);
                    }
                }
            });

        }

        @Override
        protected void bindChildView(View view, Context context, Cursor cursor,
                boolean isLastChild) {
            ContactItemCache cache = (ContactItemCache) view.getTag();
            TextView pv = (TextView) view.findViewById(R.id.pick_contact_number);
            String num = cursor.getString(PHONE_COLUMN_NUMBER);
            pv.setText(num);

            cache.id = cursor.getLong(PHONE_COLUMN_ID);
            cache.name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
            cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
            cache.label = cursor.getString(PHONE_COLUMN_LABEL);
            cache.type = String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE));

            setPhotoView(view, cursor, cache);
            setItemView(view, cursor, cache);
            setLabel(view, cursor);
        }

        private void setPhotoView(View view, Cursor cursor, ContactItemCache cache) {
            CheckableImageView photoView = ((CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo));
            photoView.setVisibility(View.VISIBLE);

            final String accountName = cursor.getString(PHONE_COLUMN_ACCOUNT_NAME);
            final String accountType = cursor.getString(PHONE_COLUMN_ACCOUNT_TYPE);
            Account account = new Account(accountName, accountType);

            cache.photoId = cursor.getLong(PHONE_COLUMN_PHOTO_ID);
            cache.photoUri = cursor.getString(PHONE_COLUMN_PHOTO_THUMBNAIL_URI);

            if (cache.photoId != 0) {
                mContactPhotoManager.loadThumbnail(photoView, cache.photoId, account, false, true,
                        null);
            } else {
                final Uri photoUri = cache.photoUri == null ? null : Uri.parse(cache.photoUri);
                DefaultImageRequest request = null;
                if (photoUri == null) {
                    cache.lookupKey = cursor
                            .getString(PHONE_COLUMN_LOOKUP_KEY);
                    request = new DefaultImageRequest(cache.name, cache.lookupKey, true);
                }
                mContactPhotoManager.loadDirectoryPhoto(photoView, photoUri, account, false, true,
                        request);
            }
            photoView.setChecked(mCheckListListener.onContainsKey(String.valueOf(cache.id)),
                    false);
        }

        private void setItemView(View view, Cursor cursor, ContactItemCache cache) {
            CheckableImageView photoView = (CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo);

            boolean isFirstEntry = true;
            int position = cursor.getPosition();
            long currentContactId = cursor.getLong(PHONE_COLUMN_CONTACT_ID);
            if (!cursor.isFirst() && cursor.moveToPrevious()) {
                long previousContactId = cursor.getLong(PHONE_COLUMN_CONTACT_ID);
                if (currentContactId == previousContactId) {
                    isFirstEntry = false;
                }
            }
            cursor.moveToPosition(position);

            if (isFirstEntry) {
                view.getLayoutParams().height = mContext.getResources()
                        .getDimensionPixelSize(R.dimen.pick_contact_first_item_height);
                photoView.setVisibility(View.VISIBLE);
                String contact = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                TextView nameView = (TextView) view.findViewById(R.id.pick_contact_name);
                nameView.setText(contact);
                nameView.setVisibility(View.VISIBLE);
            } else {
                view.getLayoutParams().height = mContext.getResources()
                        .getDimensionPixelSize(R.dimen.pick_contact_same_item_height);
                if (mCheckListListener.onContainsKey(String.valueOf(cache.id))) {
                    photoView.setVisibility(View.VISIBLE);
                } else {
                    photoView.setVisibility(View.INVISIBLE);
                }
                view.findViewById(R.id.pick_contact_name).setVisibility(View.GONE);
            }
            if (photoView.isChecked()) {
                view.setActivated(true);
            } else {
                view.setActivated(false);
            }
        }

        private void setLabel(View view, Cursor cursor) {
            TextView labelView = (TextView) view.findViewById(R.id.label);
            CharSequence label = null;
            final int type = cursor.getInt(PHONE_COLUMN_TYPE);
            final String customLabel = cursor.getString(PHONE_COLUMN_LABEL);
            label = Phone.getTypeLabel(mContext.getResources(), type, customLabel);
            labelView.setText(label);
        }

        private void hidePhotoView(View view) {
            ImageView photo = ((ImageView) view.findViewById(R.id.pick_contact_photo));
            photo.setVisibility(View.INVISIBLE);
        }

        @Override
        protected Cursor getChildrenCursor(Cursor groupCursor) {
            long groupId = groupCursor.getLong(GROUP_ID);
            Cursor c = getContactsDetailCursor(groupId);
            return c;
        }

        @Override
        public void changeCursor(Cursor cursor) {
            if (cursor != mGroupsCursor) {
                mGroupsCursor = cursor;
                fillAllContactsCursorMap();
                super.changeCursor(cursor);
            }
        }
    }

        /**
         * all contacts cursor fill to map
         */
        private void fillAllContactsCursorMap() {
            mAllContactsCurosrMap.clear();
            Cursor cursor = null;
            if (mGroupsCursor == null || mGroupsCursor.isClosed())
                return;
            for (int groupPosition = 0; groupPosition < mGroupsCursor.getCount(); groupPosition++) {
                mGroupsCursor.moveToPosition(groupPosition);
                long groupCacheId = mGroupsCursor.getLong(GROUP_ID);
                // get the all contacts data in current group
                cursor = getContactsDetailCursor(groupCacheId);
                if (cursor == null) {
                    continue;
                }
                if (!cursor.moveToFirst()) {
                    continue;
                }
                String[] dataIds = new String[cursor.getCount()];
                for (int i = 0; i < cursor.getCount(); i++) {
                    cursor.moveToPosition(i);
                    dataIds[i] = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
                }
                mAllContactsCurosrMap.put(groupCacheId, dataIds);
            }

            if (cursor != null) {
                cursor.close();
            }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // initialization mGroupFragme page, ensure that check groups and
            // contacts item is selected
            if (checkedList != null && mGroupsCursor != null) {
                Cursor cursor = allContactsInGroups;
                if (cursor == null) {
                    return;
                }
                int count = cursor.getCount();
                String key;
                for (int i = 0; i < count; i++) {
                    cursor.moveToPosition(i);
                    long id = cursor.getLong(PHONE_COLUMN_ID);
                    key = String.valueOf(id);
                    if (mCheckListListener.onContainsKey(key)) {
                        if (!checkedList.contains(key)) {
                            checkedList.add(key);
                        }
                    } else {
                        if (checkedList.contains(key)) {
                            checkedList.remove(key);
                        }
                    }
                }
                for (int groupPosition = 0; groupPosition < mGroupsCursor
                        .getCount(); groupPosition++) {
                    mGroupsCursor.moveToPosition(groupPosition);
                    long groupCacheId = mGroupsCursor.getLong(GROUP_ID);
                    boolean isGroupChecked = true;
                    String[] dataIds = mAllContactsCurosrMap.get(groupCacheId);
                    for (int i = 0; i < dataIds.length; i++) {
                        if (!checkedList.contains(dataIds[i])) {
                            isGroupChecked = false;
                            break;
                        }
                    }
                    if (isGroupChecked) {
                        if (!mCheckListListener.onContainsGroupId(groupCacheId)) {
                            mCheckListListener.addGroupId(groupCacheId);
                        }
                    } else {
                        if (mCheckListListener.onContainsGroupId(groupCacheId)) {
                            mCheckListListener.onRemoveGroupId(groupCacheId);
                        }
                    }
                }

                mCheckListListener.onUpdateActionBar();
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * @param isSelectedAll isSelectedAll is true, selected all groups and contacts isSelectedAll
     * is False, deselected all groups and contacts
     */
    public void setSelectedAll(boolean isSelectedAll) {
        Cursor cursor = allContactsInGroups;
        if (cursor == null) {
            return;
        }
        ContactItemCache cache = new ContactItemCache();
        String key;
        int count = cursor.getCount();
        if (isSelectedAll) {
            // all groups selected
            for (int position = 0; position < mGroupsCursor.getCount(); position++) {
                mGroupsCursor.moveToPosition(position);
                long groupCacheId = mGroupsCursor.getLong(GROUP_ID);
                if (!mCheckListListener.onContainsGroupId(groupCacheId)) {
                    mCheckListListener.addGroupId(groupCacheId);
                }
            }
            // all contacts selected
            for (int i = 0; i < count; i++) {
                cursor.moveToPosition(i);
                cache.id = cursor.getLong(PHONE_COLUMN_ID);
                key = String.valueOf(cache.id);
                if (!mCheckListListener.onContainsKey(key)) {
                    cache.name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                    cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                    cache.type = cursor.getString(PHONE_COLUMN_TYPE);
                    cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                    cache.contact_id = cursor.getString(PHONE_COLUMN_CONTACT_ID);
                    String[] value = new String[] {
                            cache.name, cache.number, cache.type,
                            cache.label, cache.contact_id
                    };
                    if (!checkedList.contains(key)) {
                        checkedList.add(key);
                    }
                    mCheckListListener.putValue(key, value);
                }
            }
        } else {
            // clear groups selected numbers
            mCheckListListener.onGroupClear();
            for (int i = 0; i < count; i++) {
                cursor.moveToPosition(i);
                key = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
                if (mCheckListListener.onContainsKey(key)) {
                    mCheckListListener.onRemove(key);
                }
            }
            // clear contacts selected numbers
            checkedList.clear();
        }
        mCheckListListener.onUpdateActionBar();
        mAdapter.notifyDataSetChanged();
    }

    public int getAllCheckedListSize() {
        return checkedList.size();
    }

}
