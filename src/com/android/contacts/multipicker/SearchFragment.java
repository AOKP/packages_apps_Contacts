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
import android.app.ListFragment;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.activities.MultiPickContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.format.TextHighlighter;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.contacts.common.util.SearchUtil;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.common.widget.CheckableImageView;
import com.android.contacts.list.ContactsPickMode;
import com.android.contacts.list.OnCheckListActionListener;
import com.google.common.collect.Sets;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

public class SearchFragment extends ListFragment {
    private final static String TAG = "SearchFragment";
    private final static boolean DEBUG = true;

    private static final String SORT_ORDER = " desc";

    static final String[] PHONES_PROJECTION = new String[] {
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

    static final String PHONES_SELECTION = RawContacts.ACCOUNT_TYPE + "<>?";

    static final String[] PHONES_SELECTION_ARGS = {
            SimContactsConstants.ACCOUNT_TYPE_SIM
    };

    private final static String[] GROUP_PROJECTION = new String[] {
            Groups._ID, // 0
            Groups.TITLE, // 1
            Groups.SUMMARY_COUNT, // 2
            Groups.SUMMARY_WITH_PHONES, // 3
            Groups.ACCOUNT_NAME, // 4
            Groups.ACCOUNT_TYPE, // 5
            Groups.DATA_SET // 6
    };

    public static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID, // 0
            Calls.NUMBER, // 1
            Calls.CACHED_LOOKUP_URI, // 2
            Calls.CACHED_PHOTO_ID, // 3
            Calls.CACHED_PHOTO_URI, // 4
            Calls.CACHED_FORMATTED_NUMBER // 5
    };

    // phone column
    private static final int PHONE_COLUMN_ID = 0;
    private static final int PHONE_COLUMN_TYPE = 1;
    private static final int PHONE_COLUMN_LABEL = 2;
    private static final int PHONE_COLUMN_NUMBER = 3;
    private static final int PHONE_COLUMN_DISPLAY_NAME = 4;
    private static final int PHONE_COLUMN_PHOTO_ID = 5;
    private static final int PHONE_COLUMN_LOOKUP_KEY = 6;
    private static final int PHONE_COLUMN_CONTACT_ID = 7;
    private static final int PHONE_ACCOUNT_TYPE = 8;
    private static final int PHONE_ACCOUNT_NAME = 9;
    private static final int PHONE_COLUMN_PHOTO_URI = 10;
    // groups column
    private static final int GROUP_ID = 0;
    private static final int GROUP_TITLE = 1;
    private static final int GROUP_SUMMARY_COUNT = 2;
    private static final int GROUP_SUMMARY_WITH_PHONES = 3;
    private static final int GROUP_ACCOUNT_NAME = 4;
    private static final int GROUP_ACCOUNT_TYPE = 5;
    private static final int GROUP_DATA_SET = 6;
    // call log column
    public static final int ID = 0;
    public static final int NUMBER = 1;
    public static final int CACHED_LOOKUP_URI = 2;
    public static final int CACHED_PHOTO_ID = 3;
    public static final int CACHED_PHOTO_URI = 4;
    public static final int CACHED_FORMATTED_NUMBER = 5;

    private static final int QUERY_TOKEN = 42;
    private static final int CONTACT_QUERY_TOKEN = 43;
    private static final int GROUP_QUERY_TOKEN = 44;
    private static final int CALLLOG_QUERY_TOKEN = 45;

    // Include PHONE, GROUPS and CALL
    private static final int VIEW_TYPE_COUNT = 3;

    private QueryHandler mQueryHandler;

    private Context mContext;

    private ContactsPickMode mPickMode;
    private int mMode;

    private OnCheckListActionListener mCheckListListener;

    private ContactItemListAdapter mContactListAdapter;

    private String query;

    private String mFilter;

    private View mRootView;

    private ArrayList<Cursor> mCursors;

    private static final int TYPE_CONTACTS = 0;
    private static final int TYPE_GROUP = 1;
    private static final int TYPE_CALLLOG = 2;
    private static final int TYPE_GROUP_CONTACTS = 3;

    /**
     * control of whether show the contacts in SIM card, if intent has this flag,not show.
     */
    private static final String EXT_NOT_SHOW_SIM_FLAG = "not_sim_show";

    private static final int BUFFER_LENGTH = 500;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mContactListAdapter == null) {
            mContactListAdapter = new ContactItemListAdapter(mContext);
            mCursors = new ArrayList<Cursor>();
        }
        setListAdapter(mContactListAdapter);
        mQueryHandler = new QueryHandler(mContext);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mPickMode = ContactsPickMode.getInstance();
        mMode = mPickMode.getMode();
        mContext = (MultiPickContactsActivity) activity;
    }

    public void setCheckListListener(OnCheckListActionListener checkListListener) {
        mCheckListListener = checkListListener;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        int Type = mContactListAdapter.getItemViewType(position);
        switch (Type) {
            case TYPE_GROUP:
                GroupItemCache groupCache = (GroupItemCache) v.getTag();
                if (mCheckListListener.onContainsGroupId(groupCache.id)) {
                    mCheckListListener.onRemoveGroupId(groupCache.id);
                } else {
                    mCheckListListener.addGroupId(groupCache.id);
                }
                Cursor cursor = getContactsDetailCursor(groupCache.id);

                if (cursor == null) {
                    break;
                }
                if (!cursor.moveToFirst()) {
                    break;
                }
                do {
                    String key = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
                    if (mCheckListListener.onContainsGroupId(groupCache.id)) {
                        if (!mCheckListListener.onContainsKey(key)) {
                            ContactItemCache cache = new ContactItemCache();
                            cache.id = cursor.getLong(PHONE_COLUMN_ID);
                            cache.name = cursor
                                    .getString(PHONE_COLUMN_DISPLAY_NAME);
                            cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                            cache.type = cursor.getString(PHONE_COLUMN_TYPE);
                            cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                            cache.contact_id = cursor
                                    .getString(PHONE_COLUMN_CONTACT_ID);
                            String[] value = null;
                            value = new String[] {
                                    cache.name, cache.number,
                                    cache.type, cache.label, cache.contact_id
                            };
                            mCheckListListener.putValue(key, value);
                        }
                    } else {
                        if (mCheckListListener.onContainsKey(key)) {
                            mCheckListListener.onRemove(key);
                        }
                    }
                } while (cursor.moveToNext());
                break;
            case TYPE_CONTACTS:
                ContactItemCache cache = (ContactItemCache) v.getTag();
                String key = String.valueOf(cache.id);

                if (!mCheckListListener.onContainsKey(key)) {
                    String[] value = null;
                    value = new String[] {
                            cache.name, cache.number, cache.type,
                            cache.label, cache.contact_id
                    };
                    mCheckListListener.putValue(key, value);
                } else {
                    mCheckListListener.onRemove(key);
                }
                break;
            case TYPE_CALLLOG:
                /* Click searched call log */
                PhoneCallDetails details = (PhoneCallDetails) v.getTag();
                String callLogKey = String.valueOf(details.mCallId);
                if (null != details) {

                    if (!mCheckListListener.onContainsNumberKey(callLogKey)) {
                        String[] value;
                        value = new String[] {
                                details.mNumber
                        };
                        mCheckListListener.putNumberValue(callLogKey, value);
                    } else {
                        mCheckListListener.onNumberRemove(callLogKey);
                    }
                }
                break;
            default:
                break;
        }

        mCheckListListener.onUpdateActionBar();
        mCheckListListener.exitSearch();
    }

    @Override
    public void onDetach() {
        super.onDetach();

        mContext = null;
    }

    @Override
    public void onDestroy() {
        mQueryHandler.removeCallbacksAndMessages(QUERY_TOKEN);
        mQueryHandler.removeCallbacksAndMessages(CONTACT_QUERY_TOKEN);
        mQueryHandler.removeCallbacksAndMessages(GROUP_QUERY_TOKEN);
        mQueryHandler.removeCallbacksAndMessages(CALLLOG_QUERY_TOKEN);

        if (mContactListAdapter.getCursor() != null) {
            mContactListAdapter.getCursor().close();
        }

        super.onDestroy();
    }

    private Uri getUriToQuery() {
        Uri uri;
        switch (mMode) {
            case ContactsPickMode.MODE_DEFAULT_PHONE:
            case ContactsPickMode.MODE_SEARCH_PHONE:
                uri = Phone.CONTENT_URI;
                break;
            case ContactsPickMode.MODE_DEFAULT_CALL:
            case ContactsPickMode.MODE_SEARCH_CALL:
                uri = Calls.CONTENT_URI;
                break;
            /*
             * case ContactsPickMode.MODE_SEARCH_GROUP: uri = Groups.CONTENT_SUMMARY_URI; break;
             */
            default:
                throw new IllegalArgumentException("getUriToQuery: Incorrect mode: " + mMode);
        }
        return uri.buildUpon().appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
                .build();
    }

    private Uri getFilterUri() {
        return Phone.CONTENT_FILTER_URI;
    }

    public String[] getProjectionForQuery() {
        switch (mMode) {
            case ContactsPickMode.MODE_SEARCH_PHONE:
                return PHONES_PROJECTION;
            case ContactsPickMode.MODE_SEARCH_CALL:
                return CALL_LOG_PROJECTION;
            case ContactsPickMode.MODE_SEARCH_GROUP:
                return GROUP_PROJECTION;
            default:
                log("getProjectionForQuery: Incorrect mode: " + mMode);
        }
        return PHONES_PROJECTION;
    }

    private String getSortOrder() {
        switch (mMode) {
            case ContactsPickMode.MODE_SEARCH_CALL:
                return CALL_LOG_PROJECTION[2] + SORT_ORDER;
            case ContactsPickMode.MODE_SEARCH_GROUP:
                return createGroupSortOrder();
            default:
                return RawContacts.SORT_KEY_PRIMARY;
        }
    }

    private String getSelectionForQuery() {
        switch (mMode) {
            case ContactsPickMode.MODE_SEARCH_PHONE:
                if (isShowSIM()) {
                    return null;
                } else {
                    return PHONES_SELECTION;
                }
            case ContactsPickMode.MODE_SEARCH_GROUP:
                return createGroupSelection(query);
            case ContactsPickMode.MODE_SEARCH_CALL:
                return createCallLogSelection(query);
            default:
                return null;
        }
    }

    private int getQueryToken() {
        switch (mMode) {
            case ContactsPickMode.MODE_SEARCH_GROUP:
                return GROUP_QUERY_TOKEN;
            case ContactsPickMode.MODE_SEARCH_PHONE:
                return CONTACT_QUERY_TOKEN;
            case ContactsPickMode.MODE_SEARCH_CALL:
                return CALLLOG_QUERY_TOKEN;
            default:
                return QUERY_TOKEN;
        }
    }

    private String getQueryStr() {
        switch (mMode) {
            case ContactsPickMode.MODE_SEARCH_GROUP:
            case ContactsPickMode.MODE_SEARCH_PHONE:
            case ContactsPickMode.MODE_SEARCH_CALL:
                return query;
            default:
                return null;
        }
    }

    private String[] getSelectionArgsForQuery() {
        switch (mMode) {
            case ContactsPickMode.MODE_SEARCH_PHONE:
                if (isShowSIM()) {
                    return null;
                } else {
                    return PHONES_SELECTION_ARGS;
                }
            default:
                return null;
        }
    }

    private boolean isShowSIM() {
        // if airplane mode on, do not show SIM.
        return !mPickMode.getIntent().hasExtra(EXT_NOT_SHOW_SIM_FLAG);
    }

    public void doFilter(int mode, String s) {
        query = s;

        if (TextUtils.isEmpty(s)) {
            mContactListAdapter.changeCursor(null);
            return;
        }

        Uri uri = null;
        switch (mode) {
            case ContactsPickMode.MODE_SEARCH_GROUP:
                mMode = mode;
                uri = Groups.CONTENT_SUMMARY_URI;
                break;
            case ContactsPickMode.MODE_SEARCH_PHONE:
                mMode = mode;
                uri = Uri.withAppendedPath(getFilterUri(), query);
                break;
            case ContactsPickMode.MODE_SEARCH_CALL:
                mMode = mode;
                uri = getUriToQuery();
                break;
            default:
                mMode = mode;
                uri = Uri.withAppendedPath(getFilterUri(), query);
                break;
        }

        mQueryHandler.startQuery(getQueryToken(), getQueryStr(), uri, getProjectionForQuery(),
                getSelectionForQuery(),
                getSelectionArgsForQuery(), getSortOrder());

    }

    private class QueryHandler extends AsyncQueryHandler {
        protected WeakReference<SearchFragment> mFragment;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mFragment = new WeakReference<SearchFragment>((SearchFragment) SearchFragment.this);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // In the case of low memory, the WeakReference object may be
            // recycled.
            if (mFragment == null || mFragment.get() == null) {
                mFragment = new WeakReference<SearchFragment>(SearchFragment.this);
            }
            final SearchFragment fragment = mFragment.get();
            String filter = (String) cookie;
            // setFilterString(filter);
            switch (token) {
                case QUERY_TOKEN:
                    mContactListAdapter.changeCursor(cursor);
                    break;
                case GROUP_QUERY_TOKEN:
                    if (cursor != null) {
                        mCursors.add(cursor);
                    }
                    doFilter(ContactsPickMode.MODE_SEARCH_PHONE, filter);
                    break;
                case CONTACT_QUERY_TOKEN:
                    if (cursor != null) {
                        mCursors.add(cursor);
                    }
                    doFilter(ContactsPickMode.MODE_SEARCH_CALL, filter);
                    break;
                case CALLLOG_QUERY_TOKEN:
                    // Call log query complete.
                    if (cursor != null) {
                        mCursors.add(cursor);
                    }
                    if (mCursors.size() == 0) {
                        Toast.makeText(mContext, R.string.listFoundAllContactsZero,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Cursor[] cursors = new Cursor[mCursors.size()];
                        for (int i = 0; i < mCursors.size(); i++) {
                            cursors[i] = mCursors.get(i);
                        }
                        Cursor mergeCursor = new MergeCursor(cursors);
                        mMode = ContactsPickMode.MODE_SEARCH_PHONE;
                        mPickMode.setMode(mMode);
                        mContactListAdapter.changeCursor(mergeCursor);
                        if (mCursors != null) {
                            mCursors.clear();
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private String getDisplayNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        if (PhoneNumberUtils.isVoiceMailNumber(number.toString())) {
            return getString(R.string.voicemail);
        }
        return number;
    }

    private class ContactItemCache {
        long id;
        String name;
        String number;
        String lookupKey;
        String type;
        String label;
        String contact_id;
        String email;
        String anrs;
        long nameRawContactId;
        Uri photoUri;
        Uri contactUri;
        long photoId;
    }

    private class GroupItemCache {
        long id;
        String title;
        String summary_count;
        String summary_with_phones;
        String account_name;
        String account_type;
        int phone_numbers;
    }

    public class ContactItemListAdapter extends CursorAdapter {
        protected LayoutInflater mInflater;
        private ContactPhotoManager mContactPhotoManager;
        private final TextHighlighter mTextHighlighter;

        private int contactsNum;
        private int groupNum;
        private int calllogNum;

        public ContactItemListAdapter(Context context) {
            super(context, null, false);

            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
            mTextHighlighter = new TextHighlighter(Typeface.BOLD);
        }

        private void setLabel(View view, Cursor cursor) {
            TextView labelView = (TextView) view.findViewById(R.id.label);
            CharSequence label = null;
            int accountTypeIndex = cursor.getColumnIndexOrThrow(Phone.TYPE);
            int customLabelIndex = cursor.getColumnIndexOrThrow(Phone.LABEL);
            if (!cursor.isNull(accountTypeIndex)) {
                final int type = cursor.getInt(accountTypeIndex);
                final String customLabel = cursor.getString(customLabelIndex);

                label = Phone.getTypeLabel(mContext.getResources(), type, customLabel);
            }
            labelView.setText(label);
        }

        /**
         * Compose PhoneAccount object from component name and account id.
         */
        public PhoneAccountHandle getAccount(String componentString, String accountId) {
            if (TextUtils.isEmpty(componentString) || TextUtils.isEmpty(accountId)) {
                return null;
            }
            final ComponentName componentName = ComponentName.unflattenFromString(componentString);
            return new PhoneAccountHandle(componentName, accountId);
        }

        private void setItemView(View view, Cursor cursor, ContactItemCache cache) {
            CheckableImageView photoView = (CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo);

            boolean isFirstEntry = true;
            int contactIdIndex = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID);
            if (!cursor.isNull(contactIdIndex)) {
                long currentContactId = cursor.getLong(contactIdIndex);

                int position = cursor.getPosition();
                if (!cursor.isFirst() && cursor.moveToPrevious()
                        && (getItemViewType(position - 1) == TYPE_CONTACTS)) {
                    int preContactIdIndex = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID);
                    if (!cursor.isNull(contactIdIndex)) {
                        final long previousContactId = cursor.getLong(preContactIdIndex);
                        if (currentContactId == previousContactId) {
                            isFirstEntry = false;
                        }
                    }
                }
                cursor.moveToPosition(position);
            }

            if (isFirstEntry) {
                view.getLayoutParams().height = mContext.getResources()
                        .getDimensionPixelSize(R.dimen.pick_contact_first_item_height);
                photoView.setVisibility(View.VISIBLE);
                view.findViewById(R.id.pick_contact_name).setVisibility(View.VISIBLE);
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
        }

        private void bindCallLogView(View view, PhoneCallDetails details) {
            // Set Strange call logs filter highlight
            CharSequence formattedNumber = mTextHighlighter.applyPrefixHighlight(
                    details.mFormattedNumber, query.toUpperCase());
            details.numberText.setVisibility(View.GONE);
            details.labelText.setVisibility(View.GONE);
            details.nameText.setText(formattedNumber);

            CheckableImageView photoView = details.photoView;

            if (details.mPhotoId != 0) {
                mContactPhotoManager.loadThumbnail(photoView, details.mPhotoId, false, true, null);
            } else {
                final Uri photoUri = details.mPhotoUri == null ? null : details.mPhotoUri;
                DefaultImageRequest request = null;
                if (photoUri == null) {
                    details.mLookupKey = UriUtils.getLookupKeyFromUri(details.mLookupUri);
                    request = new DefaultImageRequest(details.mDisplayNumber,
                            details.mLookupKey, true);
                }
                mContactPhotoManager.loadDirectoryPhoto(photoView, photoUri, false, true,
                        request);
            }

            photoView.setChecked(
                    mCheckListListener.onContainsNumberKey(String.valueOf(details.mCallId)), false);

            // Activate photo when photo is check.
            if (photoView.isChecked()) {
                view.setActivated(true);
                return;
            } else {
                view.setActivated(false);
            }
        }

        private void setPhotoView(View view, Cursor cursor, ContactItemCache cache) {
            CheckableImageView photoView = ((CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo));
            photoView.setVisibility(View.VISIBLE);

            int photoIdIndex = cursor.getColumnIndexOrThrow(Contacts.PHOTO_ID);
            if (!cursor.isNull(photoIdIndex)) {
                cache.photoId = cursor.getLong(photoIdIndex);
            } else {
                cache.photoId = 0;
            }

            int photoUriIndex = cursor.getColumnIndexOrThrow(Contacts.PHOTO_THUMBNAIL_URI);
            if (!cursor.isNull(photoUriIndex)) {
                cache.photoUri = UriUtils.parseUriOrNull(cursor.getString(photoUriIndex));
            } else {
                cache.photoUri = null;
            }

            Account account = null;
            int accountNameIndex = cursor.getColumnIndexOrThrow(RawContacts.ACCOUNT_NAME);
            int accoutTypeIndex = cursor.getColumnIndexOrThrow(RawContacts.ACCOUNT_TYPE);
            if (!cursor.isNull(accoutTypeIndex) && !cursor.isNull(accountNameIndex)) {
                final String accountType = cursor.getString(accoutTypeIndex);
                final String accountName = cursor.getString(accountNameIndex);
                account = new Account(accountName, accountType);
            }

            if (cache.photoId != 0) {
                mContactPhotoManager.loadThumbnail(photoView, cache.photoId, account, false, true,
                        null);
            } else {
                final Uri photoUri = cache.photoUri == null ? null : cache.photoUri;
                DefaultImageRequest request = null;
                if (photoUri == null) {
                    cache.lookupKey = cursor
                            .getString(cursor.getColumnIndexOrThrow(Contacts.LOOKUP_KEY));
                    request = new DefaultImageRequest(cache.name, cache.lookupKey, true);
                }
                mContactPhotoManager.loadDirectoryPhoto(photoView, photoUri, account, false, true,
                        request);
            }

            photoView.setChecked(mCheckListListener.onContainsKey(String.valueOf(cache.id)), false);
            if (photoView.isChecked()) {
                view.setActivated(true);
            } else {
                view.setActivated(false);
            }
        }

        @Override
        public int getViewTypeCount() {
            return VIEW_TYPE_COUNT;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = null;

            int type = getItemViewType(cursor.getPosition());
            switch (type) {
                case TYPE_GROUP:
                    v = mInflater.inflate(R.layout.pick_group_list_item_view, parent, false);
                    GroupItemCache groupCache = new GroupItemCache();
                    v.setTag(groupCache);
                    break;
                case TYPE_CALLLOG:
                    // New call log's view.
                    v = mInflater.inflate(R.layout.multi_pick_contact_item, parent, false);
                    PhoneCallDetails details = new PhoneCallDetails(v);
                    v.setTag(details);
                    break;
                default:
                    v = mInflater.inflate(R.layout.multi_pick_contact_item, parent, false);
                    ContactItemCache dataCache = new ContactItemCache();
                    v.setTag(dataCache);
                    break;
            }

            return v;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v;

            if (!getCursor().moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            if (convertView != null && convertView.getTag() != null) {
                v = convertView;
            } else {
                v = newView(mContext, getCursor(), parent);
            }
            bindView(v, mContext, getCursor());
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            int position = cursor.getPosition();
            int Type = getItemViewType(position);
            switch (Type) {
                case TYPE_GROUP:
                    GroupItemCache groupCache = (GroupItemCache) view.getTag();
                    groupCache.id = cursor.getLong(GROUP_ID);
                    groupCache.title = cursor.getString(GROUP_TITLE);
                    groupCache.summary_count = cursor.getString(GROUP_SUMMARY_COUNT);
                    groupCache.summary_with_phones = cursor.getString(GROUP_SUMMARY_WITH_PHONES);
                    String summary_count = mContext.getResources().getString(
                            R.string.summary_count_numbers,
                            groupCache.summary_with_phones);
                    setGroupChecked(view, cursor, groupCache, position);
                    setGroupFilterHighLight(view, groupCache);
                    break;
                case TYPE_CONTACTS:
                    hideSectionView(view);
                    ContactItemCache cache = (ContactItemCache) view.getTag();
                    cache.id = cursor.getLong(PHONE_COLUMN_ID);
                    cache.name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                    cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                    cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                    cache.type = String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE));

                    setPhotoView(view, cursor, cache);
                    setItemView(view, cursor, cache);
                    setLabel(view, cursor);
                    setFilterHighLight(view, cache.name, cache.number);
                    break;
                case TYPE_CALLLOG:
                    // Bind searched call log view
                    PhoneCallDetails details = (PhoneCallDetails) view.getTag();
                    bindCallLogData(details, cursor);
                    bindCallLogView(view, details);
                    break;
            }
        }

        private void setFilterHighLight(View view, String name, String number) {
            if (query == null) {
                return;
            }
            TextView numberView = (TextView) view.findViewById(R.id.pick_contact_number);
            CharSequence numberText = number;
            if (ContactDisplayUtils.isPossiblePhoneNumber(query)) {
                numberText = mTextHighlighter.applyPrefixHighlight(numberText, query.toUpperCase());
            }
            numberView.setText(numberText);

            TextView nameView = (TextView) view.findViewById(R.id.pick_contact_name);
            CharSequence nameText = name;
            nameText = mTextHighlighter.applyPrefixHighlight(nameText, query.toUpperCase());
            nameView.setText(nameText);
        }

        private void setGroupFilterHighLight(View view, GroupItemCache groupCache) {
            TextView groupView = (TextView) view.findViewById(R.id.name);
            CharSequence groupNameText = groupCache.title;
            groupNameText = mTextHighlighter.applyPrefixHighlight(groupNameText,
                    query.toUpperCase());
            groupView.setText(groupNameText);
        }

        private void bindCallLogData(PhoneCallDetails details, Cursor cursor) {

            details.mCallId = cursor.getLong(ID);

            details.mNumber = cursor.getString(NUMBER);
            details.mFormattedNumber = cursor.getString(CACHED_FORMATTED_NUMBER);
            details.mDisplayNumber = getDisplayNumber(details.mNumber);

            details.mPhotoId = cursor.getLong(CACHED_PHOTO_ID);
            details.mPhotoUri = UriUtils.parseUriOrNull(cursor.getString(CACHED_PHOTO_URI));

            details.mLookupUri = UriUtils.parseUriOrNull(
                    cursor.getString(CACHED_LOOKUP_URI));
            if (details.mLookupUri != null) {
                details.mLookupKey = UriUtils.getLookupKeyFromUri(details.mLookupUri);
            }
        }

        private void setGroupChecked(View view, Cursor cursor, GroupItemCache groupCache,
                int groupPosition) {
            if (groupCache.phone_numbers == 0) {
                Cursor c = getContactsDetailCursor(groupCache.id);
                groupCache.phone_numbers = c.getCount();
            }
            String summary_count = getResources()
                    .getString(R.string.summary_count_numbers,
                            String.valueOf(groupCache.phone_numbers));
            ((TextView) view.findViewById(R.id.number_count)).setText(summary_count);
            CheckableImageView photoView = (CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo);
            String newTitle = cursor.getString(cursor.getColumnIndexOrThrow(Groups.TITLE));
            groupCache.title = newTitle;
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

        @Override
        public int getItemViewType(int position) {
            if (!getCursor().moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }
            int ColumnCounts = getCursor().getColumnCount();

            if (ColumnCounts == PHONES_PROJECTION.length) {
                return TYPE_CONTACTS;
            } else if (ColumnCounts == GROUP_PROJECTION.length) {
                return TYPE_GROUP;
            } else if (ColumnCounts == CALL_LOG_PROJECTION.length) {
                return TYPE_CALLLOG;
            } else {
                return TYPE_GROUP_CONTACTS;
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
        }

        private void hideSectionView(View view) {
            TextView section = (TextView) view.findViewById(R.id.section_index);
            section.setVisibility(View.GONE);
        }

    }

    protected static void log(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mRootView = inflater.inflate(R.layout.multi_pick_contacts_fragment, container, false);
        mRootView.setPadding(0, 0, 0, 0);
        return mRootView;
    }

    private String createGroupSelection(String query) {
        StringBuilder where = new StringBuilder();
        where.append(Groups.ACCOUNT_TYPE + " NOT NULL AND " + Groups.ACCOUNT_NAME + " NOT NULL AND "
                + Groups.AUTO_ADD
                + "=0 AND " + Groups.FAVORITES + "=0 AND " + Groups.DELETED + "!=1 AND "
                + Groups.TITLE + " like '%"
                + query + "%'");
        where.append(" AND (" + Groups.SUMMARY_WITH_PHONES + "<>0)");
        return where.toString();
    }

    private String createGroupSortOrder() {
            return Groups.SOURCE_ID;
    }

    private String createCallLogSelection(String filter) {
        String selection = Calls._ID + " in ( " + mCheckListListener.getCallLogSelection() + " )"
                + " and (" + Calls.NUMBER + " like '%" + filter + "%' and "
                + Calls.CACHED_NAME + " is null)";
        return selection;
    }

    private String createEmailOrNumberSelection() {
        StringBuilder selection = new StringBuilder();
        selection.append(ContactsContract.Data.MIMETYPE + "='" + Email.CONTENT_ITEM_TYPE + "'")
                .append(" OR ")
                .append(ContactsContract.Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'");
        return selection.toString();
    }

    private Cursor getContactsDetailCursor(long groupId) {

        StringBuilder selection = new StringBuilder();
        selection.append(Data.RAW_CONTACT_ID + " IN (" + " SELECT DISTINCT " + Data.RAW_CONTACT_ID
                + " FROM view_data WHERE " + Data.MIMETYPE + "=?" + " AND "
                + GroupMembership.GROUP_ROW_ID + "=?)");

        Cursor cursor = mContext.getContentResolver().query(Phone.CONTENT_URI, PHONES_PROJECTION,
                selection.toString(),
                createSelectionArgs(groupId), null);

        return cursor;
    }

    private String[] createSelectionArgs(long groupId) {
        List<String> selectionArgs = new ArrayList<String>();
        selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
        selectionArgs.add(String.valueOf(groupId));
        return selectionArgs.toArray(new String[0]);
    }

}
