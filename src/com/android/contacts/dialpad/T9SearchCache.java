/*
 * Copyright (C) 2011 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.dialpad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.dialpad.util.NameToNumber;
import com.android.contacts.dialpad.util.NameToNumberFactory;

/**
 * @author shade, Danesh, pawitp
 */
public class T9SearchCache implements ComponentCallbacks2 {
    public interface Callback {
        public void onLoadFinished();
    }

    public static final String T9_CACHE_SERVICE = "t9cache";

    // List sort modes
    private static final int NAME_FIRST = 1;
    private static final int NUMBER_FIRST = 2;

    // Phone number, nickname, organization query
    private static final String[] DATA_PROJECTION = new String[] {
        Data.CONTACT_ID,
        Data.MIMETYPE,
        Phone.NUMBER,
        Phone.IS_SUPER_PRIMARY,
        Phone.TYPE,
        Phone.LABEL,
        Organization.COMPANY,
        Nickname.NAME
    };
    private static final int DATA_COLUMN_CONTACT = 0;
    private static final int DATA_COLUMN_MIMETYPE = 1;
    private static final int DATA_COLUMN_PHONENUMBER = 2;
    private static final int DATA_COLUMN_PRIMARY = 3;
    private static final int DATA_COLUMN_PHONETYPE = 4;
    private static final int DATA_COLUMN_PHONELABEL = 5;
    private static final int DATA_COLUMN_ORGANIZATION = 6;
    private static final int DATA_COLUMN_NICKNAME = 7;

    private static final String DATA_SELECTION =
            Data.MIMETYPE + " = ? or " + Data.MIMETYPE + " = ? or " + Data.MIMETYPE + " = ?";
    private static final String[] DATA_SELECTION_ARGS = new String[] {
        Phone.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE
    };
    private static final String DATA_SORT = Data.CONTACT_ID + " ASC";

    private static final String[] CONTACT_PROJECTION = new String[] {
        Contacts._ID,
        Contacts.DISPLAY_NAME,
        Contacts.TIMES_CONTACTED,
        Contacts.PHOTO_THUMBNAIL_URI
    };
    private static final int CONTACT_COLUMN_ID = 0;
    private static final int CONTACT_COLUMN_NAME = 1;
    private static final int CONTACT_COLUMN_CONTACTED = 2;
    private static final int CONTACT_COLUMN_PHOTO_URI = 3;

    private static final String CONTACT_QUERY = Contacts.HAS_PHONE_NUMBER + " > 0";
    private static final String CONTACT_SORT = Contacts._ID + " ASC";

    // Local variables
    private Context mContext;
    private int mHighlightColor;
    private Set<Callback> mCallbacks = new HashSet<Callback>();

    private LoadTask mLoadTask;
    private boolean mLoaded;
    private Set<ContactItem> mAllResults = new LinkedHashSet<ContactItem>();

    private ArrayList<ContactItem> mContacts = new ArrayList<ContactItem>();
    private String mPrevInput;

    private String mT9Chars;
    private String mT9Digits;

    private BroadcastReceiver mLocaleChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mLoaded) {
                return;
            }

            NameToNumber normalizer = NameToNumberFactory.create(mContext, mT9Chars, mT9Digits);
            for (ContactItem contact : mContacts) {
                for (NameMatchEntry entry : contact.nameEntries.values()) {
                    entry.normalValue = normalizer.convert(entry.value);
                }
            }
            notifyLoadFinished();
        }
    };

    private ContentObserver mContactObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mLoaded = false;
            cancelLoad();
        }
    };

    public static synchronized T9SearchCache getInstance(Context context) {
        Context applicationContext = context.getApplicationContext();
        T9SearchCache service = (T9SearchCache)
                applicationContext.getSystemService(T9_CACHE_SERVICE);
        if (service == null) {
            service = createT9Cache(applicationContext);
        }
        return service;
    }

    public static synchronized T9SearchCache createT9Cache(Context context) {
        return new T9SearchCache(context);
    }

    private T9SearchCache(Context context) {
        mContext = context;
        mContext.registerReceiver(mLocaleChangedReceiver,
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        mContext.getContentResolver().registerContentObserver(
                Contacts.CONTENT_URI, true, mContactObserver);
        mHighlightColor = context.getResources().getColor(android.R.color.holo_blue_dark);
    }

    public void refresh(Callback cb) {
        if (mLoaded) {
            cb.onLoadFinished();
            return;
        }

        mCallbacks.add(cb);

        if (mLoadTask == null || mLoadTask.getStatus() == AsyncTask.Status.FINISHED) {
            mLoadTask = new LoadTask();
            mLoadTask.execute();
        }
    }

    public void cancelRefresh(Callback cb) {
        mCallbacks.remove(cb);
        if (mCallbacks.isEmpty()) {
            cancelLoad();
        }
    }

    private void cancelLoad() {
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
    }

    private class LoadTask extends AsyncTask<Void, Void, Void> {
        private ArrayList<ContactItem> contacts = new ArrayList<ContactItem>();

        @Override
        protected Void doInBackground(Void... args) {
            initT9Map();

            NameToNumber normalizer = NameToNumberFactory.create(mContext, mT9Chars, mT9Digits);

            Cursor contact = mContext.getContentResolver().query(
                    Contacts.CONTENT_URI, CONTACT_PROJECTION, CONTACT_QUERY,
                    null, CONTACT_SORT);
            Cursor data = mContext.getContentResolver().query(
                    Data.CONTENT_URI, DATA_PROJECTION, DATA_SELECTION,
                    DATA_SELECTION_ARGS, DATA_SORT);

            data.moveToFirst();

            while (contact.moveToNext()) {
                if (isCancelled()) {
                    break;
                }

                long contactId = contact.getLong(CONTACT_COLUMN_ID);
                String nickName = null, organization = null;
                int contactContactedCount = contact.getInt(CONTACT_COLUMN_CONTACTED);
                ArrayList<ContactItem> contactItems = new ArrayList<ContactItem>();
                String photoUri = contact.getString(CONTACT_COLUMN_PHOTO_URI);
                Uri photo = photoUri != null ? Uri.parse(photoUri) : null;

                while (!data.isAfterLast() && data.getLong(DATA_COLUMN_CONTACT) < contactId) {
                    data.moveToNext();
                }

                while (!data.isAfterLast() && data.getLong(DATA_COLUMN_CONTACT) == contactId) {
                    final String mimeType = data.getString(DATA_COLUMN_MIMETYPE);
                    if (TextUtils.equals(mimeType, Phone.CONTENT_ITEM_TYPE)) {
                        String num = data.getString(DATA_COLUMN_PHONENUMBER);
                        ContactItem contactInfo = new ContactItem();

                        contactInfo.id = contactId;
                        contactInfo.number = PhoneNumberUtils.formatNumber(num);
                        contactInfo.normalNumber = removeNonDigits(num);
                        contactInfo.timesContacted = contactContactedCount;
                        contactInfo.isSuperPrimary = data.getInt(DATA_COLUMN_PRIMARY) > 0;
                        contactInfo.groupType = Phone.getTypeLabel(mContext.getResources(),
                                data.getInt(DATA_COLUMN_PHONETYPE), data.getString(DATA_COLUMN_PHONELABEL));
                        contactInfo.photo = photo;
                        contactItems.add(contactInfo);
                    } else if (TextUtils.equals(mimeType, Organization.CONTENT_ITEM_TYPE)) {
                        organization = data.getString(DATA_COLUMN_ORGANIZATION);
                    } else if (TextUtils.equals(mimeType, Nickname.CONTENT_ITEM_TYPE)) {
                        nickName = data.getString(DATA_COLUMN_NICKNAME);
                    }
                    data.moveToNext();
                }

                for (ContactItem item : contactItems) {
                    item.addNameEntry(ENTRY_NAME, contact.getString(CONTACT_COLUMN_NAME), normalizer);
                    item.addNameEntry(ENTRY_NICKNAME, nickName, normalizer);
                    item.addNameEntry(ENTRY_ORGANIZATION, organization, normalizer);
                    contacts.add(item);
                }
            }

            contact.close();
            data.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mLoaded = true;
            mPrevInput = null;
            mAllResults.clear();
            mContacts = contacts;
            notifyLoadFinished();
        }
    }

    private void notifyLoadFinished() {
        for (Callback cb : mCallbacks) {
            cb.onLoadFinished();
        }
    }

    // ComponentCallbacks2
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    // ComponentCallbacks2
    @Override
    public void onLowMemory() {
    }

    // ComponentCallbacks2
    @Override
    public void onTrimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            mLoaded = false;
            mPrevInput = null;
            mAllResults.clear();
            mContacts.clear();
        }
    }

    public static class T9SearchResult {

        private final ArrayList<ContactItem> mResults;
        private ContactItem mTopContact = new ContactItem();

        public T9SearchResult (final ArrayList<ContactItem> results) {
            mTopContact = results.get(0);
            mResults = results;
            mResults.remove(0);
        }

        public int getNumResults() {
            return mResults.size() + 1;
        }

        public ContactItem getTopContact() {
            return mTopContact;
        }

        public ArrayList<ContactItem> getResults() {
            return mResults;
        }
    }

    private static class NameMatchEntry {
        String value;
        String normalValue;
        int matchId;
        String prefix;
        String postfix;
    }

    private static final int ENTRY_NAME = 0;
    private static final int ENTRY_NICKNAME = 1;
    private static final int ENTRY_ORGANIZATION = 2;
    private static final int MATCH_ENTRY_COUNT = 3;

    public static class ContactItem {
        Uri photo;
        String number;
        String normalNumber;
        int numberMatchId;
        Map<Integer, NameMatchEntry> nameEntries;
        int timesContacted;
        CharSequence groupType;
        long id;
        boolean isSuperPrimary;

        public ContactItem() {
            nameEntries = new LinkedHashMap<Integer, NameMatchEntry>();
        }
        public void addNameEntry(int type, String value, NameToNumber normalizer) {
            NameMatchEntry entry = new NameMatchEntry();
            entry.matchId = -1;
            entry.value = value;
            if (value != null) {
                entry.normalValue = normalizer.convert(value);
            }
            if (type == ENTRY_NICKNAME) {
                entry.prefix = " (";
                entry.postfix = ")";
            } else if (type == ENTRY_ORGANIZATION) {
                entry.prefix = " - ";
            }
            nameEntries.put(type, entry);
        }
    }

    public T9SearchResult search(String number) {
        if (!mLoaded) {
            return null;
        }

        number = removeNonDigits(number);

        int pos = 0;
        final ArrayList<ContactItem> numberResults = new ArrayList<ContactItem>();
        final ArrayList<ContactItem> nameResults = new ArrayList<ContactItem>();
        boolean newQuery = mPrevInput == null || number.length() <= mPrevInput.length();

        // Go through each contact
        for (ContactItem item : (newQuery ? mContacts : mAllResults)) {
            pos = item.normalNumber.indexOf(number);
            if (pos != -1) {
                item.numberMatchId = pos;
                numberResults.add(item);
            } else {
                item.numberMatchId = -1;
            }

            boolean hasNameMatch = false;

            for (NameMatchEntry entry : item.nameEntries.values()) {
                pos = entry.normalValue != null ? entry.normalValue.indexOf(number) : -1;
                if (pos != -1) {
                    int lastSpace = entry.normalValue.lastIndexOf("0", pos);
                    if (lastSpace == -1) {
                        lastSpace = 0;
                    }
                    entry.matchId = pos - lastSpace;
                    hasNameMatch = true;
                } else {
                    entry.matchId = -1;
                }
            }

            if (hasNameMatch) {
                nameResults.add(item);
            }
        }

        mAllResults.clear();
        mPrevInput = number;

        Collections.sort(numberResults, sNumberComparator);
        Collections.sort(nameResults, sNameComparator);

        if (nameResults.isEmpty() && numberResults.isEmpty()) {
            return null;
        }

        if (preferSortByName()) {
            mAllResults.addAll(nameResults);
            mAllResults.addAll(numberResults);
        } else {
            mAllResults.addAll(numberResults);
            mAllResults.addAll(nameResults);
        }

        return new T9SearchResult(new ArrayList<ContactItem>(mAllResults));
    }

    private boolean preferSortByName() {
        String mode = PreferenceManager.getDefaultSharedPreferences(mContext).getString("t9_sort", null);
        if (TextUtils.equals(mode, Integer.toString(NUMBER_FIRST))) {
            return false;
        }
        return true;
    }

    private static final Comparator<ContactItem> sNameComparator = new Comparator<ContactItem>() {
        @Override
        public int compare(ContactItem lhs, ContactItem rhs) {
            int ret;
            for (int i = 0; i < MATCH_ENTRY_COUNT; i++) {
                ret = Integer.compare(lhs.nameEntries.get(i).matchId, rhs.nameEntries.get(i).matchId);
                if (ret != 0) {
                    return ret;
                }
            }
            ret = Integer.compare(rhs.timesContacted, lhs.timesContacted);
            if (ret != 0) {
                return ret;
            }

            return Boolean.compare(rhs.isSuperPrimary, lhs.isSuperPrimary);
        }
    };

    private static final Comparator<ContactItem> sNumberComparator = new Comparator<ContactItem>() {
        @Override
        public int compare(ContactItem lhs, ContactItem rhs) {
            int ret = Integer.compare(lhs.numberMatchId, rhs.numberMatchId);
            if (ret == 0) ret = Integer.compare(rhs.timesContacted, lhs.timesContacted);
            if (ret == 0) ret = Boolean.compare(rhs.isSuperPrimary, lhs.isSuperPrimary);
            return ret;
        }
    };

    private void initT9Map() {
        StringBuilder bT9Chars = new StringBuilder();
        StringBuilder bT9Digits = new StringBuilder();

        for (String item : mContext.getResources().getStringArray(R.array.t9_map)) {
            bT9Chars.append(item);
            for (int i = 0; i < item.length(); i++) {
                bT9Digits.append(item.charAt(0));
            }
        }

        mT9Chars = bT9Chars.toString();
        mT9Digits = bT9Digits.toString();
    }

    private String removeNonDigits(String number) {
        int len = number.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char ch = number.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '*' || ch == '#' || ch == '+') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public T9Adapter createT9Adapter(Context context, ArrayList<ContactItem> items) {
        return new T9Adapter(context, items);
    }

    protected class T9Adapter extends ArrayAdapter<ContactItem> {

        private ArrayList<ContactItem> mItems;
        private LayoutInflater mInflater;
        private ContactPhotoManager mPhotoLoader;
        private View mLoadingView;

        protected T9Adapter(Context context, ArrayList<ContactItem> items) {
            super(context, 0, items);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mPhotoLoader = ContactPhotoManager.getInstance(context);
            mItems = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (!mLoaded) {
                if (mLoadingView == null) {
                    mLoadingView = mInflater.inflate(R.layout.row_loading, null);
                }
                return mLoadingView;
            }

            if (convertView == null || convertView.getTag() == null) {
                convertView = mInflater.inflate(R.layout.row, null);
                holder = new ViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.rowName);
                holder.number = (TextView) convertView.findViewById(R.id.rowNumber);
                holder.icon = (QuickContactBadge) convertView.findViewById(R.id.rowBadge);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ContactItem o = mItems.get(position);
            if (o.nameEntries.isEmpty()) {
                holder.name.setText(getContext().getResources().getString(R.string.t9_add_to_contacts));
                holder.number.setVisibility(View.GONE);
                holder.icon.setImageResource(R.drawable.ic_menu_add_field_holo_light);
                holder.icon.assignContactFromPhone(o.number, true);
            } else {
                SpannableStringBuilder nameBuilder = new SpannableStringBuilder();
                for (NameMatchEntry entry : o.nameEntries.values()) {
                    if (TextUtils.isEmpty(entry.value)) {
                        continue;
                    }

                    if (!TextUtils.isEmpty(entry.prefix)) {
                        nameBuilder.append(entry.prefix);
                    }

                    int start = nameBuilder.length() + entry.matchId;
                    nameBuilder.append(entry.value);

                    if (!TextUtils.isEmpty(entry.postfix)) {
                        nameBuilder.append(entry.postfix);
                    }

                    if (entry.matchId != -1) {
                        if (start <= entry.value.length()
                                && start + mPrevInput.length() <= entry.value.length()) {
                            nameBuilder.setSpan(new ForegroundColorSpan(mHighlightColor),
                                    start, start + mPrevInput.length(),
                                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                        }
                    }
                }

                SpannableStringBuilder numberBuilder = new SpannableStringBuilder();
                numberBuilder.append(o.normalNumber);
                numberBuilder.append(" (");
                numberBuilder.append(o.groupType);
                numberBuilder.append(")");
                if (o.numberMatchId != -1) {
                    int numberStart = o.numberMatchId;
                    numberBuilder.setSpan(new ForegroundColorSpan(mHighlightColor),
                            numberStart, numberStart + mPrevInput.length(),
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                }

                holder.name.setText(nameBuilder);
                holder.number.setText(numberBuilder);
                holder.number.setVisibility(View.VISIBLE);

                if (o.photo != null) {
                    mPhotoLoader.loadDirectoryPhoto(holder.icon, o.photo, true);
                } else {
                    holder.icon.setImageResource(
                            ContactPhotoManager.getDefaultAvatarResId(false, true));
                }
                holder.icon.assignContactFromPhone(o.number, true);
            }

            return convertView;
        }

        class ViewHolder {
            TextView name;
            TextView number;
            QuickContactBadge icon;
        }

    }

}
