/*
 * Copyright (c) 2013-2016, The Linux Foundation. All rights reserved.
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
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
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
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.AbsListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.android.contacts.activities.MultiPickContactsActivity;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.format.TextHighlighter;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.list.ContactsSectionIndexer;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.model.account.SimAccountType;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.common.widget.CheckableImageView;
import com.android.contacts.list.ContactsPickMode;
import com.android.contacts.list.OnCheckListActionListener;
import com.android.contacts.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContactsFragment extends ListFragment {
    private final static String TAG = "ContactsFragment";
    private final static boolean DEBUG = true;

    private static final String SORT_ORDER = " desc";

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
            Contacts._ID, // 0
            Contacts.DISPLAY_NAME_PRIMARY, // 1
            Contacts.DISPLAY_NAME_ALTERNATIVE, // 2
            Contacts.PHOTO_ID, // 3
            Contacts.LOOKUP_KEY, // 4
            RawContacts.ACCOUNT_TYPE, // 5
            RawContacts.ACCOUNT_NAME, // 6
            Contacts.NAME_RAW_CONTACT_ID, // 7
            Contacts.PHOTO_THUMBNAIL_URI // 8
    };

    static final String CONTACTS_SELECTION = Contacts.IN_VISIBLE_GROUP + "=1";

    static final String PHONES_SELECTION = RawContacts.ACCOUNT_TYPE + "<>?";

    static final String[] PHONES_SELECTION_ARGS = {
            SimContactsConstants.ACCOUNT_TYPE_SIM
    };

    private static final String[] DATA_PROJECTION = new String[] {
            Data._ID, // 0
            Data.DATA1, // 1 Phone.NUMBER, Email.address
            Data.DATA2, // 2  phone.type
            Data.DATA3, // 3 Phone.LABEL
            Data.DISPLAY_NAME,// 4 Phone.DISPLAY_NAME
            Data.MIMETYPE, // 5
            Data.CONTACT_ID, // 6 Phone.CONTACT_ID
            RawContacts.ACCOUNT_TYPE, // 7
            RawContacts.ACCOUNT_NAME, // 8
            Contacts.LOOKUP_KEY, // 9
            Contacts.PHOTO_ID, // 10
            Contacts.PHOTO_THUMBNAIL_URI, // 11
    };

    // contacts column
    private static final int SUMMARY_ID_COLUMN_INDEX = 0;
    private static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    private static final int SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    private static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 3;
    private static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 4;
    private static final int SUMMARY_ACCOUNT_TYPE = 5;
    private static final int SUMMARY_ACCOUNT_NAME = 6;
    private static final int SUMMARY_CONTACT_COLUMN_RAW_CONTACT_ID = 7;
    private static final int SUMMARY_CONTACT_COLUMN_PHOTO_URI = 8;
    // phone column
    private static final int PHONE_COLUMN_ID = 0;
    private static final int PHONE_COLUMN_NUMBER = 1;
    private static final int PHONE_COLUMN_TYPE = 2;
    private static final int PHONE_COLUMN_LABEL = 3;
    private static final int PHONE_COLUMN_DISPLAY_NAME = 4;
    private static final int PHONE_COLUMN_CONTACT_ID = 6;
    private static final int PHONE_ACCOUNT_TYPE = 7;
    private static final int PHONE_ACCOUNT_NAME = 8;
    private static final int PHONE_COLUMN_LOOKUP_KEY = 9;
    private static final int PHONE_COLUMN_PHOTO_ID = 10;
    private static final int PHONE_COLUMN_PHOTO_URI = 11;
    // email column
    private static final int EMAIL_COLUMN_ID = 0;
    private static final int EMAIL_COLUMN_ADDRESS = 1;
    private static final int EMAIL_COLUMN_DISPLAY_NAME = 4;
    // data column
    private static final int DATA_ID = 0;
    private static final int DATA_DATA1_COLUMN = 1;
    private static final int DATA_DATA2_COLUMN = 2;
    private static final int DATA_DATA3_COLUMN = 3;
    private static final int DATA_DISPLAY_NAME = 4;
    private static final int DATA_MIMETYPE_COLUMN = 5;
    private static final int DATA_CONTACT_ID = 6;

    private static final int QUERY_TOKEN = 43;

    public static final int ACTION_ADD_GROUP_MEMBER = 0;
    public static final int ACTION_MOVE_GROUP_MEMBER = 1;
    public static final int ACTION_DEFAULT_VALUE = -1;

    public static final String ADD_GROUP_MEMBERS = "add_group_members";

    public static final String ADD_MOVE_GROUP_MEMBER_KEY = "add_move_group_member";
    public static final String KEY_GROUP_ID = "group_id";

    private int subscription;

    private QueryHandler mQueryHandler;
    private Bundle mChoiceSet;
    private TextView mSelectAllLabel;

    private static final String KEY_SEP = ",";
    private static final String ITEM_SEP = ", ";
    private static final String CONTACT_SEP_LEFT = "[";
    private static final String CONTACT_SEP_RIGHT = "]";

    private Intent mIntent;
    private Context mContext;

    private ContactsPickMode mPickMode;
    private int mMode;

    private OnCheckListActionListener mCheckListListener;

    private ContactItemListAdapter mContactListAdapter;

    private String query;

    private String mFilter;

    private View mRootView;

    private SectionIndexer mIndexer;

    private View mHeaderView;

    // Only in pick phone mode, use this to count selected items number.
    private ArrayList<String> checkedList;

    private static final String[] COLUMN_NAMES = new String[] {
            "name",
            "number",
            "emails",
            "anrs",
            "_id"
    };

    private static final int SIM_COLUMN_DISPLAY_NAME = 0;
    private static final int SIM_COLUMN_NUMBER = 1;
    private static final int SIM_COLUMN_EMAILS = 2;
    private static final int SIM_COLUMN_ANRS = 3;
    private static final int SIM_COLUMN_ID = 4;

    /**
     * control of whether show the contacts in SIM card, if intent has this flag,not show.
     */
    private static final String EXT_NOT_SHOW_SIM_FLAG = "not_sim_show";

    /**
     * An item view is displayed differently depending on whether it is placed at the beginning,
     * middle or end of a section. It also needs to know the section header when it is at the
     * beginning of a section. This object captures all this configuration.
     */
    public static final class Placement {
        private int position = ListView.INVALID_POSITION;
        public boolean firstInSection;
        public boolean lastInSection;
        public String sectionHeader;

        public void invalidate() {
            position = ListView.INVALID_POSITION;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mContactListAdapter == null) {
            mContactListAdapter = new ContactItemListAdapter(mContext);
            if (mPickMode.isPickPhone()) {
                checkedList = new ArrayList<String>();
            }
        }
        if (mCheckListListener == null)
            mCheckListListener = ((MultiPickContactsActivity) getActivity())
                    .createListener();
        mHeaderView = new View(mContext);
        AbsListView.LayoutParams layoutParams = new AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                (int)(mContext.getResources().getDimension(R.dimen.header_listview_height)));
        mHeaderView.setLayoutParams(layoutParams);
        getListView().addHeaderView(mHeaderView, null, false);
        setListAdapter(mContactListAdapter);
        mQueryHandler = new QueryHandler(mContext);
        startQuery();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mPickMode = ContactsPickMode.getInstance();
        mMode = mPickMode.getMode();
        mContext = (MultiPickContactsActivity) activity;
    }

    @Override
    public void onResume() {
        // ContactsPickMode is singleton, its mode may be changed by other mode.
        // need to reset
        mPickMode.setMode(mMode);
        super.onResume();
    }

    @Override
    public void onStop() {
        mMode = mPickMode.getMode();
        super.onStop();
    }

    public void setCheckListListener(OnCheckListActionListener checkListListener) {
        mCheckListListener = checkListListener;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mCheckListListener.onHideSoftKeyboard();

        ContactItemCache cache = (ContactItemCache) v.getTag();
        String key = String.valueOf(cache.id);

        if (!mCheckListListener.onContainsKey(key)) {
            String[] value = null;
            if (mPickMode.isPickContact()) {
                value = new String[] {
                        cache.lookupKey, key,
                        String.valueOf(cache.nameRawContactId),
                        cache.photoUri == null ?
                        null : String.valueOf(cache.photoUri),
                        cache.name
                };
            } else if (mPickMode.isPickPhone()) {
                value = new String[] {
                        cache.name, cache.number,
                        cache.type, cache.label,
                        cache.contact_id
                };
                if (!checkedList.contains(key)) {
                    checkedList.add(key);
                }
            } else if (mPickMode.isPickEmail()) {
                value = new String[] {
                        cache.name,
                        cache.email
                };
            } else if (mPickMode.isPickSim()) {
                value = new String[] {
                        cache.name, cache.number,
                        cache.email, cache.anrs
                };
            } else if (mPickMode.isPickContactInfo()) {
                value = new String[] {
                        cache.contact_id, cache.name,
                        cache.number, cache.email
                };
            } else if (mPickMode.isPickContactVcard()) {
                value = new String[] {
                        cache.name,
                        cache.lookupKey
                };
            }
            mCheckListListener.putValue(key, value);
        } else {
            mCheckListListener.onRemove(key);
            if (mPickMode.isPickPhone()) {
                checkedList.remove(key);
            }
        }

        mCheckListListener.onUpdateActionBar();
        mCheckListListener.exitSearch();
        mContactListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onDestroy() {
        mQueryHandler.removeCallbacksAndMessages(QUERY_TOKEN);

        if (mContactListAdapter.getCursor() != null) {
            mContactListAdapter.getCursor().close();
        }

        super.onDestroy();
    }

    private Uri getUriToQuery() {
        Uri uri;
        switch (mPickMode.getMode()) {
            case ContactsPickMode.MODE_DEFAULT_CONTACT:
            case ContactsPickMode.MODE_SEARCH_CONTACT:
                mIntent = mPickMode.getIntent();
                int operation = mIntent.getIntExtra(ADD_MOVE_GROUP_MEMBER_KEY, -1);
                long groupId = mIntent.getLongExtra(KEY_GROUP_ID, -1);
                String accountName = mIntent.getStringExtra(SimContactsConstants.ACCOUNT_NAME);
                String accountType = mIntent.getStringExtra(SimContactsConstants.ACCOUNT_TYPE);
                switch (operation) {
                    case ACTION_ADD_GROUP_MEMBER:
                    case ACTION_MOVE_GROUP_MEMBER:
                        Builder builder = Contacts.CONTENT_GROUP_URI.buildUpon();
                        builder.appendQueryParameter(ADD_GROUP_MEMBERS, String.valueOf(
                                operation == ACTION_ADD_GROUP_MEMBER));
                        builder.appendQueryParameter(Groups._ID, String.valueOf(groupId));
                        builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType);
                        builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName);
                        uri = builder.build();
                        break;
                    default:
                        uri = Contacts.CONTENT_URI;
                        break;
                }
                break;
            case ContactsPickMode.MODE_DEFAULT_EMAIL:
            case ContactsPickMode.MODE_SEARCH_EMAIL:
                uri = Email.CONTENT_URI;
                break;
            case ContactsPickMode.MODE_DEFAULT_PHONE:
            case ContactsPickMode.MODE_SEARCH_PHONE:
                uri = Phone.CONTENT_URI;
                uri = uri.buildUpon().appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                        .appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true")
                        .build();
                break;
            case ContactsPickMode.MODE_DEFAULT_SIM:
            case ContactsPickMode.MODE_SEARCH_SIM: {
                mIntent = mPickMode.getIntent();
                subscription = mIntent.getIntExtra(SimContactsConstants.SLOT_KEY,
                        SimContactsConstants.SLOT1);
                uri = querySimContacts(subscription);
            }
                break;
            case ContactsPickMode.MODE_DEFAULT_CONTACT_INFO:
            case ContactsPickMode.MODE_SEARCH_CONTACT_INFO:
                uri = Data.CONTENT_URI;
                break;
            case ContactsPickMode.MODE_DEFAULT_CONTACT_VCARD:
            case ContactsPickMode.MODE_SEARCH_CONTACT_VCARD:
                uri = Contacts.CONTENT_URI;
                break;
            default:
                uri = Contacts.CONTENT_URI;
        }
        return uri.buildUpon().appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
                .build();
    }

    /**
     * Just get the uri we need to query contacts.
     *
     * @return uri with account info parameter if explicit request contacts fit current account,
     *         else just search contacts fit specified keyword.
     */
    private Uri getContactsFilterUri() {
        Uri filterUri = Contacts.CONTENT_FILTER_URI;

        // To confirm if the search rule must contain account limitation.
        Intent intent = mPickMode.getIntent();
        ContactListFilter filter = (ContactListFilter) intent
                .getParcelableExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
        int operation = intent.getIntExtra(ADD_MOVE_GROUP_MEMBER_KEY, -1);
        long groupId = intent.getLongExtra(KEY_GROUP_ID, -1);
        String accountName = intent.getStringExtra(SimContactsConstants.ACCOUNT_NAME);
        String accountType = intent.getStringExtra(SimContactsConstants.ACCOUNT_TYPE);
        switch (operation) {
            case ACTION_ADD_GROUP_MEMBER:
            case ACTION_MOVE_GROUP_MEMBER:
                Builder builder = Contacts.CONTENT_FILTER_URI.buildUpon();
                builder.appendQueryParameter(ADD_GROUP_MEMBERS,
                        String.valueOf(operation == ACTION_ADD_GROUP_MEMBER));
                builder.appendQueryParameter(Groups._ID, String.valueOf(groupId));
                builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName);
                builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType);
                return builder.build();
        }
        if (filter != null && filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {

            // Need consider account info limitation, construct the uri with
            // account info query parameter.
            Builder builder = filterUri.buildUpon();
            filter.addAccountQueryParameterToUrl(builder);
            return builder.build();
        }

        if (!isShowSIM()) {
            filterUri = filterUri.buildUpon()
                    .appendQueryParameter(RawContacts.ACCOUNT_TYPE, SimAccountType.ACCOUNT_TYPE)
                    .appendQueryParameter(SimContactsConstants.WITHOUT_SIM_FLAG, "true").build();
        }
        // No need to consider account info limitation, just return a uri
        // with "filter" path.
        return filterUri;
    }

    private Uri getFilterUri() {
        switch (mPickMode.getMode()) {
            case ContactsPickMode.MODE_SEARCH_CONTACT:
                return getContactsFilterUri();
            case ContactsPickMode.MODE_SEARCH_PHONE:
                return Phone.CONTENT_FILTER_URI;
            case ContactsPickMode.MODE_SEARCH_EMAIL:
                return Email.CONTENT_FILTER_URI;
            default:
                log("getFilterUri: Incorrect mode: " + mPickMode.getMode());
        }
        return Contacts.CONTENT_FILTER_URI;
    }

    public String[] getProjectionForQuery() {
        switch (mPickMode.getMode()) {
            case ContactsPickMode.MODE_DEFAULT_CONTACT:
            case ContactsPickMode.MODE_SEARCH_CONTACT:
            case ContactsPickMode.MODE_DEFAULT_CONTACT_VCARD:
            case ContactsPickMode.MODE_SEARCH_CONTACT_VCARD:
                return CONTACTS_SUMMARY_PROJECTION;
            case ContactsPickMode.MODE_DEFAULT_PHONE:
            case ContactsPickMode.MODE_SEARCH_PHONE:
            case ContactsPickMode.MODE_DEFAULT_EMAIL:
            case ContactsPickMode.MODE_SEARCH_EMAIL:
            case ContactsPickMode.MODE_DEFAULT_CONTACT_INFO:
            case ContactsPickMode.MODE_SEARCH_CONTACT_INFO:
                return DATA_PROJECTION;
            case ContactsPickMode.MODE_DEFAULT_SIM:
            case ContactsPickMode.MODE_SEARCH_SIM:
                return COLUMN_NAMES;
            default:
                log("getProjectionForQuery: Incorrect mode: " + mPickMode.getMode());
        }
        return CONTACTS_SUMMARY_PROJECTION;
    }

    private String getSortOrder(String[] projection) {
        switch (mMode) {
            case ContactsPickMode.MODE_DEFAULT_CONTACT_INFO:
                return RawContacts.SORT_KEY_PRIMARY + " ," + RawContacts.CONTACT_ID + " ,"
                        + Data.MIMETYPE + SORT_ORDER;
            default:
                return RawContacts.SORT_KEY_PRIMARY;
        }
    }

    private String getSelectionForQuery() {
        switch (mPickMode.getMode()) {
            case ContactsPickMode.MODE_DEFAULT_EMAIL:
            case ContactsPickMode.MODE_SEARCH_EMAIL:
            case ContactsPickMode.MODE_DEFAULT_PHONE:
            case ContactsPickMode.MODE_SEARCH_PHONE:
                if (isShowSIM()) {
                    return null;
                } else {
                    return PHONES_SELECTION;
                }
            case ContactsPickMode.MODE_DEFAULT_CONTACT:
                return getSelectionForAccount();
            case ContactsPickMode.MODE_DEFAULT_SIM:
            case ContactsPickMode.MODE_SEARCH_SIM:
                return null;
            case ContactsPickMode.MODE_DEFAULT_CONTACT_INFO:
            case ContactsPickMode.MODE_SEARCH_CONTACT_INFO:
                return createEmailOrNumberSelection(query);
            default:
                return null;
        }
    }

    private String getSelectionForAccount() {
        @SuppressWarnings("deprecation")
        ContactListFilter filter = (ContactListFilter) mPickMode.getIntent()
                .getParcelableExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
        if (filter == null) {
            return null;
        }
        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS:
                return null;
            case ContactListFilter.FILTER_TYPE_CUSTOM:
                return CONTACTS_SELECTION;
            case ContactListFilter.FILTER_TYPE_ACCOUNT:
                return null;
        }
        return null;
    }

    private String[] getSelectionArgsForQuery() {
        switch (mPickMode.getMode()) {
            case ContactsPickMode.MODE_DEFAULT_EMAIL:
            case ContactsPickMode.MODE_SEARCH_EMAIL:
            case ContactsPickMode.MODE_DEFAULT_PHONE:
            case ContactsPickMode.MODE_SEARCH_PHONE:
                if (isShowSIM()) {
                    return null;
                } else {
                    return PHONES_SELECTION_ARGS;
                }
            case ContactsPickMode.MODE_DEFAULT_SIM:
            case ContactsPickMode.MODE_SEARCH_SIM:
                return null;
            default:
                return null;
        }
    }

    private boolean isShowSIM() {
        // if airplane mode on, do not show SIM.
        return !mPickMode.getIntent().hasExtra(EXT_NOT_SHOW_SIM_FLAG);
    }

    public void startQuery() {
        Uri uri = getUriToQuery();
        if(uri == null)
            return;
        ContactListFilter filter = (ContactListFilter) mPickMode.getIntent()
                .getParcelableExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
        if (filter != null) {
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                // We should exclude the invisiable contacts.
                uri = uri.buildUpon().appendQueryParameter(RawContacts.ACCOUNT_NAME,
                        filter.accountName)
                        .appendQueryParameter(RawContacts.ACCOUNT_TYPE,
                                filter.accountType)
                        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                                ContactsContract.Directory.DEFAULT + "")
                        .build();
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
                // Do not query sim contacts in airplane mode.
                if (!isShowSIM()) {
                    uri = uri.buildUpon().appendQueryParameter(RawContacts.ACCOUNT_TYPE,
                            SimAccountType.ACCOUNT_TYPE)
                            .appendQueryParameter(SimContactsConstants.WITHOUT_SIM_FLAG,
                                    "true")
                            .build();
                }
            }
        }
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForQuery();
        String[] selectionArgs = getSelectionArgsForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection, selectionArgs,
                getSortOrder(projection));
    }

    public void doFilter(String s) {
        query = s;
        if (TextUtils.isEmpty(s)) {
            // mPickMode.exitSearchMode();
            // startQuery();
            mContactListAdapter.changeCursor(null);
            return;
        }

        Uri uri;
        if (mPickMode.isPickContactInfo()) {
            uri = Data.CONTENT_URI;
        } else {
            uri = Uri.withAppendedPath(getFilterUri(), Uri.encode(query));
        }
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForQuery();
        String[] selectionArgs = getSelectionArgsForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection, selectionArgs,
                getSortOrder(projection));
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected WeakReference<ContactsFragment> mFragment;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mFragment = new WeakReference<ContactsFragment>(
                    (ContactsFragment) ContactsFragment.this);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // In the case of low memory, the WeakReference object may be
            // recycled.
            if (mFragment == null || mFragment.get() == null) {
                mFragment = new WeakReference<ContactsFragment>(ContactsFragment.this);
            }
            final ContactsFragment fragment = mFragment.get();
            if (mHeaderView != null && mPickMode.isSearchMode()) {
                getListView().removeHeaderView(mHeaderView);
            }
            mContactListAdapter.changeCursor(cursor);
        }
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

    public class ContactItemListAdapter extends CursorAdapter
            implements SectionIndexer, View.OnClickListener {
        protected LayoutInflater mInflater;
        private ContactPhotoManager mContactPhotoManager;
        private final TextHighlighter mTextHighlighter;

        private Placement mPlacementCache = new Placement();

        public ContactItemListAdapter(Context context) {
            super(context, null, false);

            mInflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
            mTextHighlighter = new TextHighlighter(Typeface.BOLD);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContactItemCache cache = (ContactItemCache) view.getTag();
            if (mPickMode.isPickContact()) {
                cache.id = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                cache.lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                cache.name = cursor.getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                cache.nameRawContactId = cursor.getLong(SUMMARY_CONTACT_COLUMN_RAW_CONTACT_ID);
                ((TextView) view.findViewById(R.id.pick_contact_name))
                        .setText(cache.name == null ? "" : cache.name);
                view.findViewById(R.id.pick_contact_number).setVisibility(View.GONE);
                setPhotoView(view, cursor, cache);
                setHeaderAndHighLightIfNeed(view, cache, cursor);
            } else if (mPickMode.isPickPhone()) {
                cache.id = cursor.getLong(PHONE_COLUMN_ID);
                cache.name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
                cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                cache.type = String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE));
                ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.number);

                setPhotoView(view, cursor, cache);
                setItemView(view, cursor, cache);
                setLabel(view, cursor);
                setHeaderAndHighLightIfNeed(view, cache, cursor);
            } else if (mPickMode.isPickSim()) {
                cache.id = cursor.getLong(SIM_COLUMN_ID);
                cache.name = cursor.getString(SIM_COLUMN_DISPLAY_NAME);
                cache.number = cursor.getString(SIM_COLUMN_NUMBER);
                cache.email = cursor.getString(SIM_COLUMN_EMAILS);
                cache.anrs = cursor.getString(SIM_COLUMN_ANRS);
                TextView mSimNameView = (TextView) view.findViewById(R.id.pick_contact_name);
                if (TextUtils.isEmpty(cache.name)) {
                    mSimNameView.setVisibility(View.GONE);
                } else {
                    mSimNameView.setVisibility(View.VISIBLE);
                    mSimNameView.setText(cache.name);
                }
                if (!TextUtils.isEmpty(cache.number)) {
                    ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.number);
                } else if (!TextUtils.isEmpty(cache.email)) {
                    String[] emailArray = (cache.email).split(",");
                    ((TextView) view.findViewById(R.id.pick_contact_number)).setText(emailArray[0]);
                }
                setSimPhoto(view, cache);
            } else if (mPickMode.isPickEmail()) {
                cache.id = cursor.getLong(EMAIL_COLUMN_ID);
                cache.name = cursor.getString(EMAIL_COLUMN_DISPLAY_NAME);
                cache.email = cursor.getString(EMAIL_COLUMN_ADDRESS);
                ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.email);
                setPhotoView(view, cursor, cache);
                setItemView(view, cursor, cache);
                setLabel(view, cursor);
                setHeaderAndHighLightIfNeed(view, cache, cursor);
            } else if (mPickMode.isPickContactInfo()) {
                cache.id = cursor.getLong(DATA_ID);
                cache.name = cursor.getString(DATA_DISPLAY_NAME);
                cache.type = cursor.getString(DATA_MIMETYPE_COLUMN);
                if (cache.type.equals(Phone.CONTENT_ITEM_TYPE)) {
                    cache.number = cursor.getString(DATA_DATA1_COLUMN);
                    ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.number);
                } else if (cache.type.equals(Email.CONTENT_ITEM_TYPE)) {
                    cache.email = cursor.getString(DATA_DATA1_COLUMN);
                    ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.email);
                }
                cache.label = cursor.getString(DATA_DATA3_COLUMN);
                cache.contact_id = cursor.getString(DATA_CONTACT_ID);

                setPhotoView(view, cursor, cache);
                setItemView(view, cursor, cache);
                setLabel(view, cursor);
                setHeaderAndHighLightIfNeed(view, cache, cursor);
            } else if (mPickMode.isPickContactVcard()) {
                cache.id = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                cache.name = cursor.getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                cache.lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                ((TextView) view.findViewById(R.id.pick_contact_name))
                        .setText(cache.name == null ? "" : cache.name);
                ((TextView) view.findViewById(R.id.pick_contact_number))
                        .setVisibility(View.GONE);

                setPhotoView(view, cursor, cache);
                setHeaderAndHighLightIfNeed(view, cache, cursor);
            }

        }

        private void setHeaderAndHighLightIfNeed(View view, ContactItemCache cache,
                Cursor cursor) {
            if (mPickMode.isSearchMode()) {
                hideSectionHeader(view);
                if (!TextUtils.isEmpty(query)) {
                    setFilterHighLight(view, cache);
                }
            } else {
                bindSectionHeader(view, cursor.getPosition());
            }
        }

        private void setFilterHighLight(View view, ContactItemCache cache) {
            TextView nameView = (TextView) view.findViewById(R.id.pick_contact_name);
            CharSequence nameText = cache.name;
            nameText = mTextHighlighter.applyPrefixHighlight(nameText, query.toUpperCase());
            nameView.setText(nameText);

            TextView numberView = (TextView) view.findViewById(R.id.pick_contact_number);
            if (mPickMode.isPickEmail()) {
                CharSequence emailText = cache.email;
                emailText = mTextHighlighter.applyPrefixHighlight(emailText, query.toUpperCase());
                numberView.setText(emailText);
            } else if (mPickMode.isPickContactInfo()) {
                CharSequence infoText = null;
                if (cache.type.equals(Phone.CONTENT_ITEM_TYPE)) {
                    infoText = cache.number;
                } else if (cache.type.equals(Email.CONTENT_ITEM_TYPE)) {
                    infoText = cache.email;
                }
                infoText = mTextHighlighter.applyPrefixHighlight(infoText, query.toUpperCase());
                numberView.setText(infoText);
            } else {
                CharSequence numberText = cache.number;
                numberText = mTextHighlighter.applyPrefixHighlight(numberText, query.toUpperCase());
                numberView.setText(numberText);
            }
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

        private void setItemView(View view, Cursor cursor, ContactItemCache cache) {
            CheckableImageView photoView = (CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo);

            boolean isFirstEntry = true;
            int contactIdIndex = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID);
            if (!cursor.isNull(contactIdIndex)) {
                long currentContactId = cursor.getLong(contactIdIndex);

                int position = cursor.getPosition();
                if (!cursor.isFirst() && cursor.moveToPrevious()) {
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
                ((TextView) view.findViewById(R.id.pick_contact_name))
                        .setText(cache.name == null ? "" : cache.name);
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

        private void setSimPhoto(View view, ContactItemCache cache) {
            CheckableImageView photoView = ((CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo));
            photoView.setVisibility(View.VISIBLE);

            Account account = null;
            //account = MoreContactUtils.getAcount(subscription);

            account = MoreContactUtils.getAcount(mContext, subscription);

            DefaultImageRequest request = new DefaultImageRequest(
                    "".equals(cache.name) ? null : cache.name, String.valueOf(cache.id), true);
            mContactPhotoManager.loadThumbnail(photoView, 0, account, false, true, request);

            photoView.setChecked(mCheckListListener.onContainsKey(String.valueOf(cache.id)),
                    false);
            if (photoView.isChecked()) {
                view.setActivated(true);
            } else {
                view.setActivated(false);
            }
        }

        private void hidePhotoView(View view) {
            CheckableImageView photoView = ((CheckableImageView) view
                    .findViewById(R.id.pick_contact_photo));
            photoView.setVisibility(View.GONE);
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

            if ((mPickMode.isPickContact() || mPickMode.isPickContactVcard())) {
                if (!photoView.isChecked()) {
                    long contactId = cursor.getLong(cursor.getColumnIndexOrThrow(Contacts._ID));
                    String lookupKey = cursor
                            .getString(cursor.getColumnIndexOrThrow(Contacts.LOOKUP_KEY));
                    cache.contactUri = Contacts.getLookupUri(contactId, lookupKey);
                    photoView.setTag(cache);
                    photoView.setOnClickListener(this);
                } else {
                    photoView.setOnClickListener(null);
                    photoView.setClickable(false);
                }
            }

            if (photoView.isChecked()) {
                view.setActivated(true);
            } else {
                view.setActivated(false);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = null;
            v = mInflater.inflate(R.layout.multi_pick_contact_item, parent, false);
            ContactItemCache dataCache = new ContactItemCache();
            v.setTag(dataCache);
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
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            updateIndexer(cursor);
        }

        @Override
        public void onClick(View v) {
            ContactItemCache cache = (ContactItemCache) v.getTag();
            int clickId = v.getId();
            switch (clickId) {
                case R.id.pick_contact_photo:
                    if (cache != null && cache.contactUri != null) {
                        QuickContact.showQuickContact(mContext, v, cache.contactUri,
                                null, Phone.CONTENT_ITEM_TYPE);
                    }
                    break;
                default:
                    throw new IllegalStateException("this click is valid");
            }
        }

        private void bindSectionHeader(View view, int position) {
            TextView section = (TextView) view.findViewById(R.id.section_index);
            section.setVisibility(View.VISIBLE);
            Placement placement = getItemPlacementInSection(position);
            section.setText(placement.sectionHeader);
        }

        private void hideSectionHeader(View view) {
            TextView section = (TextView) view.findViewById(R.id.section_index);
            section.setVisibility(View.GONE);
        }

        /**
         * Updates the indexer, which is used to produce section headers.
         */
        private void updateIndexer(Cursor cursor) {
            if (cursor == null) {
                setIndexer(null);
                return;
            }

            Bundle bundle = cursor.getExtras();
            if (bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)
                    && bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS)) {
                String sections[] = bundle.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
                int counts[] = bundle.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
                setIndexer(new ContactsSectionIndexer(sections, counts));
            } else {
                setIndexer(null);
            }
        }

        public void setIndexer(SectionIndexer indexer) {
            mIndexer = indexer;
            mPlacementCache.invalidate();
        }

        public Object[] getSections() {
            if (mIndexer == null) {
                return new String[] {
                        " "
                };
            } else {
                return mIndexer.getSections();
            }
        }

        /**
         * @return relative position of the section in the indexed partition
         */
        public int getPositionForSection(int sectionIndex) {
            if (mIndexer == null) {
                return -1;
            }

            return mIndexer.getPositionForSection(sectionIndex);
        }

        /**
         * @param position relative position in the indexed partition
         */
        public int getSectionForPosition(int position) {
            if (mIndexer == null) {
                return -1;
            }

            return mIndexer.getSectionForPosition(position);
        }

        /**
         * Computes the item's placement within its section and populates the {@code placement}
         * object accordingly. Please note that the returned object is volatile and should be copied
         * if the result needs to be used later.
         */
        public Placement getItemPlacementInSection(int position) {
            if (mPlacementCache.position == position) {
                return mPlacementCache;
            }

            mPlacementCache.position = position;
            int section = getSectionForPosition(position);
            if (section != -1 && getPositionForSection(section) == position) {
                mPlacementCache.firstInSection = true;
                mPlacementCache.sectionHeader = (String) getSections()[section];
            } else {
                mPlacementCache.firstInSection = false;
                mPlacementCache.sectionHeader = null;
            }

            mPlacementCache.lastInSection = (getPositionForSection(section + 1) - 1 == position);
            return mPlacementCache;
        }
    }

    protected static void log(String msg) {
        if (DEBUG)
            Log.d(TAG, msg);
    }

    private Uri querySimContacts(int subscription) {
        Uri uri = null;

        if (subscription != SimContactsConstants.SLOT1
                && subscription != SimContactsConstants.SLOT2) {
            return uri;
        }
        int subId = MoreContactUtils.getActiveSubId(mContext, subscription);
        if (subId > 0) {
            uri = Uri.parse(SimContactsConstants.SIM_SUB_URI + subId);
        }

        return uri;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mRootView = inflater.inflate(R.layout.multi_pick_contacts_fragment, container, false);
        return mRootView;
    }

    // support filter email and phone types together
    private String createEmailOrNumberSelection(String s) {
        StringBuilder selection = new StringBuilder();
        selection.append("(");
        selection
                .append(Data.MIMETYPE + "='"
                        + Email.CONTENT_ITEM_TYPE + "'")
                .append(" OR ")
                .append(Data.MIMETYPE + "='"
                        + Phone.CONTENT_ITEM_TYPE + "'");
        selection.append(")");
        if (s == null || !mPickMode.isSearchMode()) {
            return selection.toString();
        }
        selection.append(" AND ");
        selection.append("(");
        selection.append(Data.CONTACT_ID);
        selection.append(" IN ");
        queryContactsIDByFilter(selection, s);
        selection.append(")");
        return selection.toString();
    }

    private void queryContactsIDByFilter(StringBuilder sb, String query) {
        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, query);
        Cursor cursor = getActivity().getContentResolver().query(uri, new String[]{Contacts._ID},
                null, null, null);
        sb.append("( ");
        if (cursor != null && cursor.getCount() != 0) {
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                int contact_id = cursor.getInt(0);
                sb.append(contact_id);
                sb.append(",");
            }
            cursor.close();
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(")");
    }

    /**
     * @param isSelectedAll isSelectedAll is true, selected all contacts
     * isSelectedAll is False, deselected all contacts
     */
    public void setSelectedAll(boolean isSelectedAll) {
        Cursor cursor = mContactListAdapter.getCursor();
        if (cursor == null) {
            return;
        }
        ContactItemCache cache = new ContactItemCache();
        String key;
        // selected all contacts
        if (isSelectedAll) {
            int count = cursor.getCount();
            for (int i = 0; i < count; i++) {
                cursor.moveToPosition(i);
                // only pick sim mode, id index is SIM_COLUMN_ID
                // other mode, id index is 0
                if (mPickMode.isPickSim()) {
                    key = String.valueOf(cursor.getLong(SIM_COLUMN_ID));
                } else {
                    key = String.valueOf(cursor.getLong(0));
                }
                if (!mCheckListListener.onContainsKey(key)) {
                    String[] value = null;
                    if (mPickMode.isPickContact()) {
                        cache.lookupKey = cursor
                                .getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                        cache.name = cursor
                                .getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                        cache.nameRawContactId = cursor
                                .getLong(SUMMARY_CONTACT_COLUMN_RAW_CONTACT_ID);
                        String photoUri = cursor.getString(SUMMARY_CONTACT_COLUMN_PHOTO_URI);
                        cache.photoUri = UriUtils.parseUriOrNull(photoUri);
                        value = new String[] {
                                cache.lookupKey,
                                key, String.valueOf(cache.nameRawContactId),
                                photoUri, cache.name
                        };
                    } else if (mPickMode.isPickPhone()) {
                        cache.name = cursor
                                .getString(PHONE_COLUMN_DISPLAY_NAME);
                        cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                        cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                        cache.type = String.valueOf(cursor
                                .getInt(PHONE_COLUMN_TYPE));
                        value = new String[] {
                                cache.name, cache.number,
                                cache.type, cache.label, cache.contact_id
                        };
                        if (!checkedList.contains(key)) {
                            checkedList.add(key);
                        }
                    } else if (mPickMode.isPickEmail()) {
                        cache.name = cursor
                                .getString(EMAIL_COLUMN_DISPLAY_NAME);
                        cache.email = cursor.getString(EMAIL_COLUMN_ADDRESS);
                        value = new String[] {
                                cache.name, cache.email
                        };
                    } else if (mPickMode.isPickSim()) {
                        cache.name = cursor.getString(SIM_COLUMN_DISPLAY_NAME);
                        cache.number = cursor.getString(SIM_COLUMN_NUMBER);
                        cache.email = cursor.getString(SIM_COLUMN_EMAILS);
                        cache.anrs = cursor.getString(SIM_COLUMN_ANRS);
                        value = new String[] {
                                cache.name, cache.number,
                                cache.email, cache.anrs
                        };
                    } else if (mPickMode.isPickContactInfo()) {
                        cache.name = cursor.getString(DATA_DISPLAY_NAME);
                        cache.type = cursor.getString(DATA_MIMETYPE_COLUMN);
                        if (cache.type.equals(Phone.CONTENT_ITEM_TYPE)) {
                            cache.number = cursor.getString(DATA_DATA1_COLUMN);
                        } else if (cache.type.equals(Email.CONTENT_ITEM_TYPE)) {
                            cache.email = cursor.getString(DATA_DATA1_COLUMN);
                        }
                        cache.label = cursor.getString(DATA_DATA3_COLUMN);
                        cache.contact_id = cursor.getString(DATA_CONTACT_ID);
                        value = new String[] {
                                cache.contact_id, cache.name,
                                cache.number, cache.email
                        };
                    } else if (mPickMode.isPickContactVcard()) {
                        cache.name = cursor.getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                        cache.lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                        value = new String[] {
                                cache.name, cache.lookupKey
                        };
                    }
                    mCheckListListener.putValue(key, value);
                }
            }
        } else {
            // deselected all contacts
            if (!mPickMode.isPickPhone()) {
                mCheckListListener.onClear();
            } else {
                int count = cursor.getCount();
                for (int i = 0; i < count; i++) {
                    cursor.moveToPosition(i);
                    key = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
                    if (mCheckListListener.onContainsKey(key)) {
                        mCheckListListener.onRemove(key);
                    }
                }
                // clear checked item numbers
                checkedList.clear();
            }
        }

        // update actionbar selected button to display selected item numbers
        mCheckListListener.onUpdateActionBar();
        mContactListAdapter.notifyDataSetChanged();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // initialization contactsFragme page, ensure that check contact item is selected
            if (checkedList != null) {
                mContactListAdapter.notifyDataSetChanged();
                Cursor mCursor = mContactListAdapter.getCursor();
                if (mCursor == null)
                    return;
                for (int i = 0; i < mCursor.getCount(); i++) {
                    mCursor.moveToPosition(i);
                    String key;
                    if (mPickMode.isPickSim()) {
                        key = String.valueOf(mCursor.getLong(SIM_COLUMN_ID));
                    } else {
                        key = String.valueOf(mCursor.getLong(0));
                    }
                    if (mCheckListListener.onContainsKey(key)) {
                        if (mPickMode.isPickPhone()) {
                            if (!checkedList.contains(key)) {
                                checkedList.add(key);
                            }
                        }
                    } else {
                        if (checkedList.contains(key)) {
                            checkedList.remove(key);
                        }
                    }
                }
                mCheckListListener.onUpdateActionBar();
            }
        }
    }

    public int getAllCheckedListSize() {
        return checkedList.size();
    }

}
