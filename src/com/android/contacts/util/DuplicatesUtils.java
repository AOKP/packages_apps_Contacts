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

package com.android.contacts.util;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContactsEntity;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.SimContactsOperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class DuplicatesUtils {

    public static final HashSet<String> mOtherMimeTypes = new HashSet();

    static {
        mOtherMimeTypes.add(CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.Im.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.Identity.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.Note.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.Website.CONTENT_ITEM_TYPE);
        mOtherMimeTypes.add(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE);
    }

    private static ArrayList<MergeContacts> mMergeRawContacts;

    private static ProgressDialog mProgressDialog;

    public static boolean mSearchState = false;

    public static boolean mMergeState = false;

    private static final String[] RAWCONTACTS_NAME_ID_PROJECTION = new String[]{
            RawContacts.DISPLAY_NAME_PRIMARY, RawContacts._ID};

    private static final String[] CONTACTS_PROJECTION = new String[]{Contacts.LOOKUP_KEY,
            Contacts.DISPLAY_NAME_PRIMARY};

    /**
     * get rawContacts with name and ids.
     *
     * @return the HashMap with key contact name and value rawContact ids.
     */
    public static HashMap<String, List<Long>> getRawNameWithIds(ContentResolver resolver,
        String[] accountArgs) {
        HashMap map = new HashMap();
        String selection = RawContacts.DELETED + "= 0 AND "
                + SimContactsConstants.ACCOUNT_NAME + "=? " + "AND "
                + SimContactsConstants.ACCOUNT_TYPE + "=?";
        Cursor cursor = resolver.query(RawContacts.CONTENT_URI,
                RAWCONTACTS_NAME_ID_PROJECTION, selection, accountArgs, null);
        if (cursor == null) {
            return map;
        }
        while (cursor.moveToNext()) {
            String name = cursor.getString(cursor.getColumnIndex(RawContacts
                    .DISPLAY_NAME_PRIMARY));
            long l = cursor.getLong(cursor.getColumnIndex(RawContacts._ID));
            if (!TextUtils.isEmpty(name)) {
                name = name.toLowerCase();
            }
            List lst = (List) map.get(name);
            if (lst == null) {
                lst = new ArrayList();
                lst.add(Long.valueOf(l));
                map.put(name, lst);
            } else {
                lst.add(Long.valueOf(l));
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        return map;
    }

    public static EntityIterator getEntityByIds(ContentResolver resolver, List<Long> ids) {
        Cursor cursor = resolver.query(RawContactsEntity.CONTENT_URI, null, RawContactsEntity._ID
                    .concat(" IN (" + TextUtils.join(",", ids) + ")"), null,
                RawContactsEntity._ID + " DESC");
        if (cursor == null) {
            return null;
        }
        EntityIterator iterator = RawContacts.newEntityIterator(cursor);
        return iterator;
    }

    public static EntityIterator getEntityById(ContentResolver resolver, Long id) {
        Cursor cursor = resolver.query(RawContactsEntity.CONTENT_URI, null,
                RawContactsEntity._ID.concat(" = ?"),
                new String[]{String.valueOf(id)}, RawContactsEntity._ID + " DESC");
        if (cursor == null) {
            return null;
        }
        EntityIterator iterator = RawContacts.newEntityIterator(cursor);
        return iterator;
    }

    /**
     * calculate duplicate contacts which will be shown in UI.
     * @return true if it calculates completely.
     */
    public static boolean calculateMergeRawContacts(Context context, List<Account> accounts,
        ContentResolver resolver) {
        SimContactsOperation simContactsOperation = new SimContactsOperation(context);
        mMergeRawContacts = new ArrayList<>();
        int count = 0;
        // contacts in different accounts are separated.
        for (int i = 0; i < accounts.size() && mSearchState; i++) {
            Account account = accounts.get(i);
            HashMap<String, List<Long>> map = DuplicatesUtils.getRawNameWithIds(resolver,
                    new String[]{account.name, account.type});
            if (map != null && map.size() > 0) {
                ArrayList<ContactsInfo> lst1;
                Iterator<String> iterator = map.keySet().iterator();
                while (mSearchState && iterator.hasNext()) {
                    List<String> mergePhoneList = new ArrayList();
                    List<String> mergeEmailList = new ArrayList();
                    long contactId = -1;
                    lst1 = new ArrayList<>();
                    String keyName = iterator.next();
                    List<Long> lst = map.get(keyName);
                    if (lst.size() >= 2) {
                        EntityIterator entityIterator = DuplicatesUtils.getEntityByIds(resolver,
                                lst);
                        if (entityIterator == null) {
                            continue;
                        }
                        try {
                            while (entityIterator.hasNext()) {
                                ArrayList<String> phoneList = new ArrayList<>();
                                ArrayList<String> emailList = new ArrayList<>();
                                long photoId = 0;
                                Entity next1 = entityIterator.next();
                                ContentValues values = next1.getEntityValues();
                                Long rawId = values.getAsLong(RawContacts._ID);
                                String id = values.getAsString(RawContacts.CONTACT_ID);
                                if (!TextUtils.isEmpty(id)) {
                                    contactId = Long.parseLong(id);
                                }
                                Iterator<Entity.NamedContentValues> namedContentValuesIterator =
                                        next1.getSubValues().iterator();
                                while (namedContentValuesIterator.hasNext()) {
                                    ContentValues values1 = namedContentValuesIterator
                                            .next().values;
                                    String mimeType = values1.getAsString(Data.MIMETYPE);
                                    if (CommonDataKinds.StructuredName
                                            .CONTENT_ITEM_TYPE.equals(mimeType)) {
                                        continue;
                                    }
                                    if (CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                                            .equals(mimeType)) {
                                        photoId = values1.getAsLong(CommonDataKinds.Photo._ID)
                                                .longValue();
                                        continue;
                                    }
                                    if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                        String data1 = values1
                                                .getAsString(CommonDataKinds.Phone.DATA1);
                                        phoneList.add(data1);
                                        if (!TextUtils.isEmpty(data1)
                                                && !mergePhoneList.contains(data1)) {
                                            boolean contains = false;
                                            for (int j = 0; j < mergePhoneList.size(); j++) {
                                                if (PhoneNumberUtils.compare(data1,
                                                        mergePhoneList.get(j))) {
                                                    contains = true;
                                                    break;
                                                }
                                            }
                                            if (!contains) {
                                                mergePhoneList.add(data1);
                                            }
                                            continue;
                                        }
                                    }
                                    if (CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                        String email = values1
                                                .getAsString(CommonDataKinds.Email.DATA1);
                                        emailList.add(email);
                                        if (!mergeEmailList.contains(email)) {
                                            mergeEmailList.add(email);
                                        }
                                        continue;
                                    }
                                }
                                Cursor cursor = resolver.query(Contacts.CONTENT_URI,
                                        CONTACTS_PROJECTION, Contacts._ID.concat(" = ?"),
                                        new String[]{String.valueOf(contactId)}, null);

                                if (cursor != null && cursor.moveToNext()) {
                                    String lookUp = cursor.getString(cursor
                                            .getColumnIndex(Contacts.LOOKUP_KEY));
                                    String displayName = cursor.getString(cursor.getColumnIndex(
                                            Contacts.DISPLAY_NAME_PRIMARY));
                                    lst1.add(new ContactsInfo(contactId, lookUp, photoId,
                                            displayName, phoneList, emailList, rawId));
                                    cursor.close();
                                }
                            }
                        } finally {
                            if (entityIterator != null) {
                                entityIterator.close();
                            }
                        }
                        // for sim contacts, analyse if it can be merged.
                        if (contactId != -1
                                && account.type.equals(SimContactsConstants.ACCOUNT_TYPE_SIM)) {
                            int subscription = simContactsOperation.getSimSubscription(contactId);
                            int oneSimAnrCount = MoreContactUtils.getOneSimAnrCount(
                                    context, subscription);
                            int oneSimEmailCount = MoreContactUtils
                                    .getOneSimEmailCount(context, subscription);
                            if (mergePhoneList.size() > oneSimAnrCount + 1
                                    || mergeEmailList.size() > oneSimEmailCount) {
                                continue;
                            }
                        }
                        if (mMergeRawContacts != null) {
                            mMergeRawContacts.add(
                                new MergeContacts(account.name, account.type, lst1));
                        }
                    }
                    count += lst.size();
                    mProgressDialog.setProgress(count);
                }
            }
        }
        if (mSearchState) {
            // search ended, change the flag.
            mSearchState = false;
            return true;
        }
        return false;
    }

    private static void addData(HashMap<String, List<String>> map, String key, String value) {
        List<String> lst = map.get(key);
        if (lst == null) {
            lst = new ArrayList();
            lst.add(value);
            map.put(key, lst);
        } else {
            lst.add(value);
        }
    }

    /**
     * build the source contact, which will be update later.
     */
    public static HashMap buildSource(ContentResolver resolver, long sourceId) {
        HashMap<String, List<String>> map = new HashMap<>();
        EntityIterator entityIterator = null;
        try {
            entityIterator = getEntityById(resolver, sourceId);
            if (entityIterator == null) {
                return map;
            }
            while (entityIterator.hasNext()) {
                Entity next1 = entityIterator.next();
                Iterator<Entity.NamedContentValues> namedContentValuesIterator = next1
                        .getSubValues().iterator();
                while (namedContentValuesIterator.hasNext()) {
                    ContentValues values1 = namedContentValuesIterator.next().values;
                    String mimeType = values1.getAsString(Data.MIMETYPE);
                    addData(map, mimeType, values1.getAsString(Data.DATA1));
                }
            }
        } finally {
            if (entityIterator != null) {
                entityIterator.close();
            }
        }
        return map;
    }

    /**
     * compare the differences among rawContacts with rawContact ids.
     */
    public static ArrayList<ContentProviderOperation> diffRawEntity(boolean isSimAccount,
        ContentResolver resolver, long sourceId, HashMap<String, List<String>> hashMap,
        ArrayList<Long> rawIds) {
        ArrayList<ContentProviderOperation> dataInsertOps = new ArrayList<>();
        EntityIterator entityIterator = null;
        try {
            entityIterator = getEntityByIds(resolver, rawIds);
            if (entityIterator == null) {
                return dataInsertOps;
            }
            while (entityIterator.hasNext()) {
                Iterator<Entity.NamedContentValues> iterator = entityIterator.next().getSubValues()
                        .iterator();
                while (iterator.hasNext()) {
                    ContentValues values = iterator.next().values;
                    String mimeType = values.getAsString(Data.MIMETYPE);
                    boolean isNumber = false;
                    boolean isFirst = false;
                    if (CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                        continue;
                    }
                    // for Photos, we just need to chose one for storing, except sim contacts.
                    else if (!isSimAccount && CommonDataKinds.Photo
                            .CONTENT_ITEM_TYPE.equals(mimeType) && !hashMap.containsKey(mimeType)) {
                        dataInsertOps.add(buildDataInsertOperation(sourceId,
                                getInsertContentValves(values), isSimAccount, isNumber, isFirst));
                        addData(hashMap, mimeType,
                                values.getAsString(Data._ID));
                    } else if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            .equals(mimeType)) {
                        isNumber = true;
                        List<String> numberList = hashMap.get(mimeType);
                        // if the mimeType has not been saved in the hashMap, init the list.
                        if (numberList == null) {
                            numberList = new ArrayList<>();
                        }
                        String data1 = values.getAsString(Data.DATA1);
                        // for numbers, it may have some different formats.
                        // compare the numbers if it has already been added in the list.
                        boolean containsNumber = false;
                        for (int i = 0; i < numberList.size(); i++) {
                            if (PhoneNumberUtils.compare(data1, numberList.get(i))) {
                                containsNumber = true;
                                break;
                            }
                        }
                        if (!containsNumber) {
                            addData(hashMap, mimeType, data1);
                            List<String> list = hashMap.get(mimeType);
                            if (isSimAccount && list == null) {
                                isFirst = true;
                            }
                            dataInsertOps.add(buildDataInsertOperation(sourceId,
                                    getInsertContentValves(values), isSimAccount,
                                    isNumber, isFirst));
                        }
                    } else if (mOtherMimeTypes.contains(mimeType)) {
                        List<String> list = hashMap.get(mimeType);
                        // if the mimeType has not been saved in the hashMap, init the list.
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        String data1 = values.getAsString(Data.DATA1);
                        if (!list.contains(data1)) {
                            addData(hashMap, mimeType, data1);
                            dataInsertOps.add(buildDataInsertOperation(sourceId,
                                    getInsertContentValves(values), isSimAccount,
                                    isNumber, isFirst));
                        }
                    }
                }
            }
            return dataInsertOps;
        } finally {
            if (entityIterator != null) {
                entityIterator.close();
            }
        }
    }

    /**
     * get the ContentValues, remove the origin data which will be inserted later.
     */
    private static ContentValues getInsertContentValves(ContentValues values) {
        values.remove(Data._ID);
        values.remove(Data.DATA_VERSION);
        values.remove(Data.IS_READ_ONLY);
        values.remove(Data.RAW_CONTACT_ID);
        values.remove(Data.IS_PRIMARY);
        values.remove(CommonDataKinds.GroupMembership.GROUP_SOURCE_ID);
        values.remove(Data.SYNC1);
        values.remove(Data.SYNC2);
        values.remove(Data.SYNC3);
        values.remove(Data.SYNC4);
        return values;
    }

    /**
     * build the data insert operation for source contacts.
     */
    private static ContentProviderOperation buildDataInsertOperation(long rawId,
            ContentValues values,  boolean isSimAccount, boolean isNumber, boolean isFirst) {
        values.put(Data.RAW_CONTACT_ID, Long.valueOf(rawId));
        if (isSimAccount) {
            if (isNumber) {
                if (isFirst) {
                    values.put(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE);
                } else {
                    values.put(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_HOME);
                }
            }
        }
        return ContentProviderOperation.newInsert(Data.CONTENT_URI).withValues(values).build();
    }

    public static class ContactsInfo {
        private List<String> mEmails;
        private String mName;
        private List<String> mPhones;
        private long mPhotoId;
        private long mRawContactId;
        private long mContactId;
        private String mLookUp;

        public ContactsInfo(long contactId, String lookUp, long photoId, String name,
                List<String> phones, List<String> emails, long rawId) {
            this.mContactId = contactId;
            this.mLookUp = lookUp;
            this.mPhotoId = photoId;
            if (TextUtils.isEmpty(name)) {
                name = "";
            }
            this.mName = name;
            this.mPhones = phones;
            this.mEmails = emails;
            this.mRawContactId = rawId;
        }

        public String getLookUp() {
            return mLookUp;
        }

        public List<String> getEmails() {
            return mEmails;
        }

        public String getName() {
            return mName;
        }

        public List<String> getPhones() {
            return mPhones;
        }

        public long getPhotoId() {
            return mPhotoId;
        }

        public long getRawContactId() {
            return mRawContactId;
        }

        public long getContactId() {
            return mContactId;
        }

    }

    public static class MergeContacts {
        private boolean mChecked;
        private ArrayList<ContactsInfo> mContacts;
        private String mAccountName;
        private String mAccountType;

        public MergeContacts(String accountName, String accountType,
            ArrayList<ContactsInfo> ContactList) {
            this.mAccountName = accountName;
            this.mAccountType = accountType;
            this.mContacts = ContactList;
            this.mChecked = true;
        }

        public ArrayList<ContactsInfo> getContacts() {
            return mContacts;
        }

        public String getAccountName() {
            return mAccountName;
        }

        public String getAccountType() {
            return mAccountType;
        }

        public boolean isChecked() {
            return mChecked;
        }

        public void setChecked(boolean checked) {
            mChecked = checked;
        }
    }

    public static ArrayList<MergeContacts> getMergeRawContacts() {
        return mMergeRawContacts;
    }

    public static void setDialog(ProgressDialog dialog) {
        mProgressDialog = dialog;
    }

    public static void clearMergeRawContacts() {
        mMergeRawContacts = null;
    }
}
