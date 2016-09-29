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

import android.app.ListFragment;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.VoicemailContract.Voicemails;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.AbsListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.MultiPickContactsActivity;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.common.widget.CheckableImageView;
import com.android.contacts.list.ContactsPickMode;
import com.android.contacts.list.OnCheckListActionListener;
import com.google.common.collect.Sets;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Set;

public class CallLogFragment extends ListFragment {

    private static final int NUM_LOGS_TO_DISPLAY = 15;

    private static final String groupBy = "groupby";

    public static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID, // 0
            Calls.NUMBER, // 1
            Calls.TYPE, // 2
            Calls.CACHED_NAME, // 3
            Calls.CACHED_NUMBER_TYPE, // 4
            Calls.CACHED_NUMBER_LABEL, // 5
            Calls.CACHED_LOOKUP_URI, // 6
            Calls.CACHED_PHOTO_ID, // 7
            Calls.CACHED_PHOTO_URI // 8
    };

    public static final String[] CALL_LOG_AND_DATA_PROJECTION = new String[] {
            Calls._ID, // 0
            Calls.NUMBER, // 1
            Calls.TYPE, // 2
            Calls.CACHED_NAME, // 3
            Calls.CACHED_NUMBER_TYPE, // 4
            Calls.CACHED_NUMBER_LABEL, // 5
            Calls.CACHED_LOOKUP_URI, // 6
            Calls.CACHED_PHOTO_ID, // 7
            Calls.CACHED_PHOTO_URI, // 8
            PhoneLookup.DATA_ID // 9
    };

    private static final int ID = 0;
    private static final int NUMBER = 1;
    private static final int CALL_TYPE = 2;
    private static final int CACHED_NAME = 3;
    private static final int CACHED_NUMBER_TYPE = 4;
    private static final int CACHED_NUMBER_LABEL = 5;
    private static final int CACHED_LOOKUP_URI = 6;
    private static final int CACHED_PHOTO_ID = 7;
    private static final int CACHED_PHOTO_URI = 8;
    private static final int DATA_ID = 9;

    public static final String[] PHONE_LOOKUP_PROJECTION = new String[] {
            PhoneLookup._ID, // 0
            PhoneLookup.DATA_ID, // 1
            PhoneLookup.CONTACT_ID, // 2
            PhoneLookup.DISPLAY_NAME, // 3
            PhoneLookup.TYPE, // 4
            PhoneLookup.LABEL, // 5
            PhoneLookup.NUMBER, // 6
            PhoneLookup.NORMALIZED_NUMBER, // 7
            PhoneLookup.PHOTO_ID, // 8
            PhoneLookup.PHOTO_URI, // 9
            PhoneLookup.LOOKUP_KEY // 10
    };

    private static final int PHONE_LOOKUP_ID = 0;
    private static final int PHONE_LOOKUP_DATA_ID = 1;
    private static final int PHONE_LOOKUP_CONTACT_ID = 2;
    private static final int PHONE_LOOKUP_DISPLAY_NAME = 3;
    private static final int PHONE_LOOKUP_TYPE = 4;
    private static final int PHONE_LOOKUP_LABEL = 5;
    private static final int PHONE_LOOKUP_NUMBER = 6;
    private static final int PHONE_LOOKUP_NORMALIZED_NUMBER = 7;
    private static final int PHONE_LOOKUP_PHOTO_ID = 8;
    private static final int PHONE_LOOKUP_PHOTO_URI = 9;
    private static final int PHONE_LOOKUP_KEY = 10;

    private QueryHandler mQueryHandler;

    private static final int QUERY_TOKEN = 42;

    private Context mContext;

    private OnCheckListActionListener mCheckListListener;

    private CallLogItemListAdapter mCallLogListAdapter;

    private String mQuery;

    private View mRootView;

    // Only in pick phone mode, use this to count selected items number.
    private ArrayList<String> mContactsCheckedList;

    private ArrayList<String> mStrangersCheckedList;

    private ContentResolver resolver;
    private final ContentObserver mCallLogObserver = new CustomContentObserver();

    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            startQuery();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        resolver = mContext.getContentResolver();
        resolver.registerContentObserver(Calls.CONTENT_URI, true, mCallLogObserver);

        if (mCheckListListener == null) {
            mCheckListListener = ((MultiPickContactsActivity) getActivity())
                    .createListener();
        }
        if (mCallLogListAdapter == null) {
            mCallLogListAdapter = new CallLogItemListAdapter(mContext);
            mContactsCheckedList = new ArrayList<String>();
            mStrangersCheckedList = new ArrayList<String>();
        }
        View view = new View(mContext);
        AbsListView.LayoutParams layoutParams = new AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                (int)(mContext.getResources().getDimension(R.dimen.header_listview_height)));
        view.setLayoutParams(layoutParams);
        getListView().addHeaderView(view, null, false);
        setListAdapter(mCallLogListAdapter);
        mQueryHandler = new QueryHandler(mContext);
        startQuery();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    public void setCheckListListener(OnCheckListActionListener checkListListener) {
        mCheckListListener = checkListListener;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onDestroy() {
        resolver.unregisterContentObserver(mCallLogObserver);
        mQueryHandler.removeCallbacksAndMessages(QUERY_TOKEN);

        if (mCallLogListAdapter.getCursor() != null) {
            mCallLogListAdapter.getCursor().close();
        }

        super.onDestroy();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mCheckListListener.onHideSoftKeyboard();
        PhoneCallDetails details = (PhoneCallDetails) v.getTag();
        if (details != null) {
            String key;
            String[] value;
            // 0 is strangers, !0 is local contacts.
            if (details.mDataID == 0) {
                // Use call Id to set the key
                key = String.valueOf(details.mCallId);
                value = new String[] {
                        details.mNumber
                };
                // Use to add or remove a strange contact.
                if (!mCheckListListener.onContainsNumberKey(key)) {
                    mCheckListListener.putNumberValue(key, value);
                    mStrangersCheckedList.add(key);
                } else {
                    mCheckListListener.onNumberRemove(key);
                    mStrangersCheckedList.remove(key);
                }

            } else {
                // Use data Id to set the key.
                key = String.valueOf(details.mDataID);
                value = new String[] {
                        details.mName,
                        details.mNumber,
                        String.valueOf(details.mNumberType),
                        String.valueOf(details.mNumberLabel),
                        String.valueOf(details.mDataID)
                };
                // Use to add or remove a stored contact.
                if (!mCheckListListener.onContainsKey(key)) {
                    mCheckListListener.putValue(key, value);
                    mContactsCheckedList.add(key);
                } else {
                    mCheckListListener.onRemove(key);
                    mContactsCheckedList.remove(key);
                }
            }

            mCheckListListener.onUpdateActionBar();
            mCallLogListAdapter.notifyDataSetChanged();
        }
    }

    public void startQuery() {
        Uri uri = Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(groupBy, Calls.NUMBER)
                .appendQueryParameter(Calls.LIMIT_PARAM_KEY, Integer.toString(NUM_LOGS_TO_DISPLAY))
                .build();

        String[] projection = CALL_LOG_PROJECTION;

        StringBuilder where = new StringBuilder();
        // Ignore voicemails marked as deleted
        where.append(Voicemails.DELETED);
        where.append(" = 0");
        String selection = where.length() > 0 ? where.toString() : null;

        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection, null,
                Calls.DEFAULT_SORT_ORDER);
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected WeakReference<CallLogFragment> mFragment;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mFragment = new WeakReference<CallLogFragment>((CallLogFragment) CallLogFragment.this);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // In the case of low memory, the WeakReference object may be
            // recycled.
            if (mFragment == null || mFragment.get() == null) {
                mFragment = new WeakReference<CallLogFragment>(CallLogFragment.this);
            }
            final CallLogFragment fragment = mFragment.get();

            MatrixCursor matrixCursor = new MatrixCursor(CALL_LOG_AND_DATA_PROJECTION);
            setMatrixCursor(matrixCursor, cursor);

            mCallLogListAdapter.changeCursor(matrixCursor);
        }
    }

    private void setMatrixCursor(MatrixCursor matrixCursor, Cursor cursor) {
        if (cursor == null)
            return;
        if (!cursor.moveToFirst())
            return;
        Object[] tabRows = new Object[10];

        do {
            long dataId = 0;
            long callId = cursor.getLong(ID);
            String number = cursor.getString(NUMBER);
            int type = cursor.getInt(CALL_TYPE);
            String name = cursor.getString(CACHED_NAME);
            int numberType = cursor.getInt(CACHED_NUMBER_TYPE);
            String numberLabel = cursor.getString(CACHED_NUMBER_LABEL);
            String lookupUri = cursor.getString(CACHED_LOOKUP_URI);
            int photoId = cursor.getInt(CACHED_PHOTO_ID);
            Uri photoUri = UriUtils.parseUriOrNull(cursor.getString(CACHED_PHOTO_URI));

            if (TextUtils.isEmpty(number)) {
                name = null;
                numberType = 0;
                numberLabel = null;
                photoId = 0;
                photoUri = null;
                mCheckListListener.appendStrangeCallLogId(String
                        .valueOf(callId));
            } else {
                Uri uri = PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                        .appendPath(number).build();
                Cursor phoneLookupCursor = null;
                try {
                    phoneLookupCursor = mContext.getContentResolver().query(
                            uri, PHONE_LOOKUP_PROJECTION, null, null, null);
                    if (phoneLookupCursor == null
                            || !phoneLookupCursor.moveToFirst()) {
                        name = null;
                        numberType = 0;
                        numberLabel = null;
                        photoId = 0;
                        photoUri = null;
                        mCheckListListener.appendStrangeCallLogId(String
                                .valueOf(callId));
                    } else {
                        dataId = phoneLookupCursor
                                .getLong(PHONE_LOOKUP_DATA_ID);
                        name = phoneLookupCursor
                                .getString(PHONE_LOOKUP_DISPLAY_NAME);
                        numberType = phoneLookupCursor
                                .getInt(PHONE_LOOKUP_TYPE);
                        numberLabel = phoneLookupCursor
                                .getString(PHONE_LOOKUP_LABEL);
                        photoId = phoneLookupCursor
                                .getInt(PHONE_LOOKUP_PHOTO_ID);
                        photoUri = UriUtils.parseUriOrNull(phoneLookupCursor
                                .getString(PHONE_LOOKUP_PHOTO_URI));
                        String lookupKey = phoneLookupCursor
                                .getString(PHONE_LOOKUP_KEY);
                        long contact_id = phoneLookupCursor
                                .getLong(PHONE_LOOKUP_CONTACT_ID);
                        lookupUri = Contacts
                                .getLookupUri(contact_id, lookupKey).toString();
                    }

                } catch (Exception e) {
                    return;
                } finally {
                    if (phoneLookupCursor != null) {
                        phoneLookupCursor.close();
                    }
                }
            }
            tabRows[0] = callId;
            tabRows[1] = number;
            tabRows[2] = type;
            tabRows[3] = name;
            tabRows[4] = numberType;
            tabRows[5] = numberLabel;
            tabRows[6] = lookupUri;
            tabRows[7] = photoId;
            tabRows[8] = photoUri;
            tabRows[9] = dataId;

            matrixCursor.addRow(tabRows);

        } while (cursor.moveToNext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mRootView = inflater.inflate(R.layout.multi_pick_contacts_fragment, container, false);
        return mRootView;
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

    public class CallLogItemListAdapter extends CursorAdapter {
        protected LayoutInflater mInflater;
        private ContactPhotoManager mContactPhotoManager;

        public CallLogItemListAdapter(Context context) {
            super(context, null, false);

            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            PhoneCallDetails details = (PhoneCallDetails) view.getTag();

            details.mCallId = cursor.getLong(ID);
            details.mDataID = cursor.getLong(DATA_ID);

            details.mNumber = cursor.getString(NUMBER);
            details.mDisplayNumber = getDisplayNumber(details.mNumber);

            details.mPhotoId = cursor.getLong(CACHED_PHOTO_ID);
            details.mPhotoUri = UriUtils.parseUriOrNull(cursor.getString(CACHED_PHOTO_URI));
            details.mLookupUri = UriUtils.parseUriOrNull(
                    cursor.getString(CACHED_LOOKUP_URI));
            if (details.mLookupUri != null) {
                details.mLookupKey = UriUtils.getLookupKeyFromUri(details.mLookupUri);
            }

            details.mName = cursor.getString(CACHED_NAME);
            details.mNumberType = cursor.getInt(CACHED_NUMBER_TYPE);
            String numberLabel = cursor.getString(CACHED_NUMBER_LABEL);
            details.mNumberLabel = Phone.getTypeLabel(mContext.getResources(),
                    details.mNumberType, numberLabel);

            setPhotoView(view, details);

            if (TextUtils.isEmpty(details.mName)) {
                details.nameText.setText(details.mDisplayNumber);
                details.numberText.setVisibility(View.GONE);
                details.labelText.setVisibility(View.GONE);
            } else {
                details.nameText.setText(details.mName);
                details.numberText.setVisibility(View.VISIBLE);
                details.labelText.setVisibility(View.VISIBLE);
                details.numberText.setText(details.mNumber);
                details.labelText.setText(details.mNumberLabel);
            }
        }

        private void setPhotoView(View view, PhoneCallDetails details) {
            CheckableImageView photoView = details.photoView;
            photoView.setVisibility(View.VISIBLE);

            if (details.mPhotoId != 0) {
                mContactPhotoManager.loadThumbnail(photoView, details.mPhotoId, false, true, null);
            } else {
                final Uri photoUri = details.mPhotoUri == null ? null : details.mPhotoUri;
                DefaultImageRequest request = null;
                if (photoUri == null) {
                    String displayName;
                    if (TextUtils.isEmpty(details.mName)) {
                        displayName = details.mNumber;
                    } else {
                        displayName = details.mName;
                    }
                    request = new DefaultImageRequest(displayName, details.mLookupKey, true);
                }
                mContactPhotoManager.loadDirectoryPhoto(photoView, photoUri, false, true,
                        request);
            }

            // The key is first call id in every group or stand alone view item.
            boolean isChecked;
            if (details.mDataID == 0) {
                isChecked = mCheckListListener.onContainsNumberKey(String.valueOf(details.mCallId));
            } else {
                isChecked = mCheckListListener.onContainsKey(String.valueOf(details.mDataID));
            }
            photoView.setChecked(isChecked, false);

            // Activate photo when photo is check.
            if (photoView.isChecked()) {
                view.setActivated(true);
            } else {
                view.setActivated(false);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v;
            v = mInflater.inflate(R.layout.multi_pick_contact_item, parent, false);
            PhoneCallDetails details = new PhoneCallDetails(v);
            v.setTag(details);
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

    }

    /**
     * @param isSelectedAll isSelectedAll is true, selected all contacts
     * isSelectedAll is False, deselected all contacts
     */
    public void setSelectedAll(boolean isSelectedAll) {
        Cursor cursor = mCallLogListAdapter.getCursor();
        if (cursor == null) {
            return;
        }
        PhoneCallDetails details = new PhoneCallDetails(null);
        String key;
        String[] value;
        // selected all contacts
        if (isSelectedAll) {
            int count = cursor.getCount();
            for (int i = 0; i < count; i++) {
                cursor.moveToPosition(i);

                long dataId = cursor.getLong(DATA_ID);
                details.mNumber = cursor.getString(NUMBER);
                if (dataId != 0) {
                    key = String.valueOf(dataId);
                    if (!mCheckListListener.onContainsKey(key)) {
                        details.mName = cursor.getString(CACHED_NAME);
                        details.mNumberLabel = cursor.getString(CACHED_NUMBER_LABEL);
                        details.mNumberType = cursor.getInt(CACHED_NUMBER_TYPE);
                        value = new String[] {
                                details.mName, details.mNumber, String.valueOf(details.mNumberType),
                                String.valueOf(details.mNumberLabel),
                                /* details.contact_id */ };
                        mCheckListListener.putValue(key, value);
                        if (!mContactsCheckedList.contains(key)) {
                            mContactsCheckedList.add(key);
                        }
                    }
                } else {
                    key = String.valueOf(cursor.getInt(ID));
                    if (!mCheckListListener.onContainsNumberKey(key)) {
                        value = new String[] {
                                details.mNumber
                        };
                        mCheckListListener.putNumberValue(key, value);
                        if (!mStrangersCheckedList.contains(key)) {
                            mStrangersCheckedList.add(key);
                        }
                    }
                }

            }
        } else {
            // deselected all contacts
            int count = cursor.getCount();
            for (int i = 0; i < count; i++) {
                cursor.moveToPosition(i);
                long dataId = cursor.getLong(DATA_ID);
                if (dataId != 0) {
                    key = String.valueOf(dataId);
                    if (mCheckListListener.onContainsKey(key)) {
                        mCheckListListener.onRemove(key);
                    }
                } else {
                    key = String.valueOf(cursor.getLong(ID));
                    if (mCheckListListener.onContainsNumberKey(key)) {
                        mCheckListListener.onNumberRemove(key);
                    }
                }
            }
            // clear checked item numbers
            mContactsCheckedList.clear();
            mStrangersCheckedList.clear();
        }

        // update actionbar selected button to display selected item numbers
        mCheckListListener.onUpdateActionBar();
        mCallLogListAdapter.notifyDataSetChanged();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // initialization contactsFragme page, ensure that check contact
            // item is selected
            if (mContactsCheckedList != null || mStrangersCheckedList != null) {
                mCallLogListAdapter.notifyDataSetChanged();
                Cursor cursor = mCallLogListAdapter.getCursor();
                if (cursor == null)
                    return;
                String key;
                for (int i = 0; i < cursor.getCount(); i++) {
                    cursor.moveToPosition(i);

                    long dataId = cursor.getLong(DATA_ID);
                    if (dataId != 0) {
                        key = String.valueOf(dataId);
                        if (mCheckListListener.onContainsKey(key)) {
                            if (!mContactsCheckedList.contains(key)) {
                                mContactsCheckedList.add(key);
                            }
                        } else {
                            if (mContactsCheckedList.contains(key)) {
                                mContactsCheckedList.remove(key);
                            }
                        }
                    } else {
                        key = String.valueOf(cursor.getLong(ID));
                        if (mCheckListListener.onContainsNumberKey(key)) {
                            if (!mStrangersCheckedList.contains(key)) {
                                mStrangersCheckedList.add(key);
                            }
                        } else {
                            if (mStrangersCheckedList.contains(key)) {
                                mStrangersCheckedList.remove(key);
                            }
                        }
                    }
                }
                mCheckListListener.onUpdateActionBar();
            }
        }
    }

    public int getAllCheckedListSize() {
        return mContactsCheckedList.size() + mStrangersCheckedList.size();
    }
}
