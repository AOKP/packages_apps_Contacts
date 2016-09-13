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

package com.android.contacts.activities;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.SimContactsOperation;
import com.android.contacts.util.DuplicatesUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MergeContactActivity extends ListActivity {
    private static final String TAG = "MergeContactActivity";
    private static final int BATCH_SIZE = 300;

    private static ProgressDialog mProgressDialog;
    private ArrayList<DuplicatesUtils.MergeContacts> mMergeList;
    private SimContactsOperation mSimContactsOperation;

    private int mMergeProgress = 0;
    private int mSelectCount = -1;

    private MergeContactAdapter adapter;

    private MenuItem mergeItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.merge_list);
        mMergeList = DuplicatesUtils.getMergeRawContacts();
        initData();
    }

    private void initData() {
        adapter = new MergeContactAdapter(this);
        adapter.setData(mMergeList);
        getListView().setAdapter(adapter);
        mSelectCount = mMergeList.size();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.merge_options, menu);
        mergeItem = menu.findItem(R.id.menu_merge);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_merge: {
                mProgressDialog = new MyProgressDialog(MergeContactActivity.this);
                mProgressDialog.setMessage(this.getString(R.string.merging_contacts));
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                Iterator<DuplicatesUtils.MergeContacts> iterator = mMergeList.iterator();
                while (iterator.hasNext()) {
                    DuplicatesUtils.MergeContacts next = iterator.next();
                    if (!next.isChecked()) {
                        iterator.remove();
                    }
                }
                mProgressDialog.setMax(mMergeList.size());
                mProgressDialog.setCancelable(false);
                mProgressDialog.setCanceledOnTouchOutside(false);
                mProgressDialog.show();
                Thread thread = new MergeDuplicatedThread();
                DuplicatesUtils.mMergeState = true;
                thread.start();
            }
        }
        return true;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        MergeContactAdapter adapter = (MergeContactAdapter) l.getAdapter();
        DuplicatesUtils.MergeContacts item = (DuplicatesUtils.MergeContacts) adapter
                .getItem(position);
        CheckBox cb = (CheckBox) v.findViewById(R.id.row_box);
        // mark the mergeContacts item, which will be merged later, if true.
        // calculate the selected count.
        if (cb.isChecked() && item.isChecked()) {
            cb.setChecked(false);
            item.setChecked(false);
            mSelectCount--;
        } else {
            cb.setChecked(true);
            item.setChecked(true);
            mSelectCount++;
        }
        // if the selected count is '0', disable the menuItem.
        if (mSelectCount == 0 && mergeItem != null && mergeItem.isEnabled()) {
            mergeItem.setEnabled(false);
        } else if (mSelectCount > 0 && mergeItem != null && !mergeItem.isEnabled()) {
            mergeItem.setEnabled(true);
        }
    }

    private class MergeDuplicatedThread extends Thread {

        ArrayList<ContentProviderOperation> dataInsertList = new ArrayList<>();
        ArrayList<ContentProviderOperation> rawUpdateList = new ArrayList<>();
        ArrayList<ContentProviderOperation> rawDelList = new ArrayList<>();

        @Override
        public void run() {
            Looper.prepare();
            joinSetContacts(mMergeList);

            rawUpdateList.clear();
            dataInsertList.clear();
            rawDelList.clear();
            DuplicatesUtils.mMergeState = false;
            Looper.loop();
        }

        /**
         * @param mergeList each item will be merged into only one contact.
         */
        private void joinSetContacts(ArrayList<DuplicatesUtils.MergeContacts> mergeList) {
            mSimContactsOperation = new SimContactsOperation(MergeContactActivity.this);
            mMergeProgress = 0;

            for (int i = 0; i < mergeList.size() && DuplicatesUtils.mMergeState; i++) {
                DuplicatesUtils.MergeContacts mergeContacts = mergeList.get(i);
                if (mergeContacts.getAccountType().equals(SimContactsConstants.ACCOUNT_TYPE_SIM)) {
                    // if the set of sim contacts merged successfully.
                    boolean result = joinSetSimContacts(mergeList.get(i));
                    if (!result) {
                        break;
                    }
                } else {
                    boolean result = joinSetLocalContacts(false, mergeList.get(i));
                    if (!result) {
                        break;
                    }
                }
                if (mProgressDialog != null) {
                    mProgressDialog.setProgress(++mMergeProgress);
                }
            }
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, rawUpdateList);
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, dataInsertList);
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, rawDelList);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (OperationApplicationException e) {
                e.printStackTrace();
            }

            // no matter the join process is success or not, dismiss the dialog.
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            //all set are joined successfully.
            if (mMergeProgress == mergeList.size()) {
                Toast.makeText(MergeContactActivity.this, R.string.merge_complete,
                        Toast.LENGTH_SHORT).show();
            }
            DuplicatesUtils.clearMergeRawContacts();
            finish();
        }

        /**
         * if the set of contacts are Sim contacts.
         */
        private boolean joinSetSimContacts(DuplicatesUtils.MergeContacts mergeContacts) {
            // get the rawContacts to be merged.
            ArrayList<DuplicatesUtils.ContactsInfo> contactsInfos = mergeContacts.getContacts();
            String resultName = null;
            String resultNumber = null;
            StringBuilder resultEmails = new StringBuilder();
            StringBuilder resultAnrNumber = new StringBuilder();
            ContentValues sourceValues = null;
            long sourceContactId;

            List<String> simNumberList = new ArrayList<>();
            List<String> simEmailList = new ArrayList<>();

            boolean needUpdate = false;
            int subscription = -1;
            ArrayList<Long> delContactIds = new ArrayList<>();
            HashMap<Long, ArrayList<Long>> delRawIdsMap = new HashMap<>();
            // add the delete uri to list for simContacts at local.
            for (int i = 0; i < contactsInfos.size(); i++) {
                DuplicatesUtils.ContactsInfo contactsInfo = contactsInfos.get(i);
                Long contactId = contactsInfo.getContactId();
                Long rawId = contactsInfo.getRawContactId();
                if (i == 0) {
                    // get the subscription of the sim card.
                    // only needed execute once for one set rawContacts.
                    subscription = mSimContactsOperation.getSimSubscription(contactId);

                    // build the source simContact, which will be update later.
                    sourceContactId = contactId;
                    sourceValues = mSimContactsOperation.getSimAccountValues(sourceContactId);
                    if (sourceValues != null) {
                        resultName = sourceValues.getAsString(SimContactsConstants.STR_TAG);
                        String oldNumber = sourceValues
                                .getAsString(SimContactsConstants.STR_NUMBER);
                        String oldAnrs = sourceValues.getAsString(SimContactsConstants.STR_ANRS);
                        String oldEmails = sourceValues
                                .getAsString(SimContactsConstants.STR_EMAILS);
                        if (oldNumber != null) {
                            simNumberList.add(oldNumber);
                        }
                        if (oldAnrs != null) {
                            String[] split = oldAnrs.split(SimContactsConstants.ANR_SEP);
                            for (int j = 0; j < split.length; j++) {
                                simNumberList.add(split[j]);
                            }
                        }

                        if (oldEmails != null) {
                            String[] split = oldEmails.split(SimContactsConstants.EMAIL_SEP);
                            for (int j = 0; j < split.length; j++) {
                                simEmailList.add(split[j]);
                            }
                        }
                    }
                    continue;
                }

                if (!delContactIds.contains(contactId)) {
                    delContactIds.add(contactId);
                    ArrayList<Long> list = delRawIdsMap.get(contactId);
                    if (list == null) {
                        list = new ArrayList<>();
                    }
                    if (!list.contains(rawId)) {
                        list.add(rawId);
                    }
                    delRawIdsMap.put(contactId, list);
                }
            }

            // calculate the value of the new insert contact.
            for (int i = 0; i < delContactIds.size(); i++) {
                ContentValues values = mSimContactsOperation
                        .getSimAccountValues(delContactIds.get(i));
                String number = values.getAsString(SimContactsConstants.STR_NUMBER);
                String anr = values.getAsString(SimContactsConstants.STR_ANRS);
                String emails = values.getAsString(SimContactsConstants.STR_EMAILS);

                if (!TextUtils.isEmpty(number) && !simNumberList.contains(number)) {
                    boolean containsNumber = false;
                    for (int j = 0; j < simNumberList.size(); j++) {
                        if (PhoneNumberUtils.compare(number, simNumberList.get(j))) {
                            containsNumber = true;
                            break;
                        }
                    }
                    if (!containsNumber) {
                        needUpdate = true;
                        simNumberList.add(number);
                    }
                }

                if (!TextUtils.isEmpty(anr)) {
                    String[] splitAnr = anr.split(SimContactsConstants.ANR_SEP);
                    for (int j = 0; j < splitAnr.length; j++) {
                        if (!TextUtils.isEmpty(splitAnr[j]) && !simNumberList
                                .contains(splitAnr[j])) {
                            boolean containsAnr = false;
                            for (int k = 0; k < simNumberList.size(); k++) {
                                if (PhoneNumberUtils.compare(splitAnr[j], simNumberList.get(k))) {
                                    containsAnr = true;
                                    break;
                                }
                            }
                            if (!containsAnr) {
                                needUpdate = true;
                                simNumberList.add(splitAnr[j]);
                            }
                        }
                    }
                }

                if (!TextUtils.isEmpty(emails)) {
                    String[] splitEmail = emails.split(SimContactsConstants.EMAIL_SEP);
                    for (int j = 0; j < splitEmail.length; j++) {
                        if (!TextUtils.isEmpty(splitEmail[j]) && !simEmailList
                                .contains(splitEmail[j])) {
                            needUpdate = true;
                            simEmailList.add(splitEmail[j]);
                        }
                    }
                }
            }

            // prepare the insert data of the sim contact.
            if (simNumberList.size() > 0) {
                resultNumber = simNumberList.remove(0);
            }
            if (simNumberList.size() > 0) {
                if (MoreContactUtils.getSpareAnrCount(MergeContactActivity.this, subscription)
                        >= simNumberList.size()) {
                    for (int i = 0; i < simNumberList.size(); i++) {
                        resultAnrNumber.append(simNumberList.get(i))
                                .append(SimContactsConstants.ANR_SEP);
                    }
                } else {
                    Toast.makeText(MergeContactActivity.this, R.string.sim_anr_full,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            if (simEmailList.size() > 0) {
                if (MoreContactUtils.getSpareEmailCount(MergeContactActivity.this, subscription)
                        >= simEmailList.size()) {
                    for (int i = 0; i < simEmailList.size(); i++) {
                        resultEmails.append(simEmailList.get(i))
                                .append(SimContactsConstants.EMAIL_SEP);
                    }
                } else {
                    Toast.makeText(MergeContactActivity.this, R.string.sim_email_full,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            // update the new sim contact.
            // we update the sim contact before delete the origin ones to avoid losing data.
            sourceValues.put(SimContactsConstants.STR_NEW_TAG, resultName);
            sourceValues.put(SimContactsConstants.STR_NEW_NUMBER, resultNumber);
            sourceValues.put(SimContactsConstants.STR_NEW_ANRS, resultAnrNumber.toString());
            sourceValues.put(SimContactsConstants.STR_NEW_EMAILS, resultEmails.toString());
            sourceValues.remove(SimContactsConstants.ACCOUNT_TYPE);
            sourceValues.remove(SimContactsConstants.ACCOUNT_NAME);
            // update the contacts in sim.
            int simResult = 1;
            if (needUpdate)
                mSimContactsOperation.update(sourceValues, subscription);

            // if update sim contacts fail, stop merging.
            if (simResult <= 0) {
                Toast.makeText(MergeContactActivity.this, R.string.merge_fail,
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            // update local contact.
            if (!joinSetLocalContacts(true, mergeContacts)) {
                Toast.makeText(MergeContactActivity.this, R.string.merge_fail,
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            for (int i = 0; i < delContactIds.size(); i++) {
                Long contactId = delContactIds.get(i);
                ContentValues values = mSimContactsOperation.getSimAccountValues(contactId);
                int res = mSimContactsOperation.delete(values, subscription);
                // if it deletes sim contact successfully, add it to local delete list.
                if (res > 0) {
                    ArrayList<Long> list = delRawIdsMap.get(contactId);

                    if (rawDelList.size() + list.size() >= BATCH_SIZE) {
                        applyBatch();
                    }
                    for (int j = 0; j < list.size(); j++) {
                        Uri uri = Uri.withAppendedPath(RawContacts
                                .CONTENT_URI, String.valueOf(list.get(j)));
                        rawDelList.add(ContentProviderOperation.newDelete(uri).build());
                    }
                } else {
                    Toast.makeText(MergeContactActivity.this, R.string.merge_fail,
                            Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }

        /**
         * if the set of contacts are not sim contacts.
         */
        private boolean joinSetLocalContacts(boolean isSimAccount,
            DuplicatesUtils.MergeContacts mergeContacts) {
            ArrayList<DuplicatesUtils.ContactsInfo> contactsInfos = mergeContacts.getContacts();
            // the id of the rawContacts which will be update.
            long sourceId = -1;
            ArrayList<Long> rawIds = new ArrayList<>();
            for (int i = 0; i < contactsInfos.size(); i++) {
                DuplicatesUtils.ContactsInfo contactsInfo = contactsInfos.get(i);
                long rawContactId = contactsInfo.getRawContactId();
                // keep the first rawContact as the one which will be updated later.
                if (i == 0) {
                    sourceId = rawContactId;
                    continue;
                }
                // add the remaining rawContacts ids to the list, which will be deleted later.
                rawIds.add(rawContactId);
            }

            if (rawUpdateList.size() + 1 >= BATCH_SIZE ||
                rawDelList.size() + rawIds.size() >= BATCH_SIZE) {
                if (!applyBatch()) {
                    Toast.makeText(MergeContactActivity.this, R.string.merge_fail,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            //for sim card, do delete itself.
            //it should delete local only when it deletes sim successfully.
            for (int i = 0; !isSimAccount && i < rawIds.size(); i++) {
                Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawIds.get(i));
                // the delete operation list for the rawContacts.
                rawDelList.add(ContentProviderOperation.newDelete(uri).build());
            }

            // build the source contact, which will be update later.
            HashMap<String, List<String>> hashMap = DuplicatesUtils.buildSource(
                    getContentResolver(), sourceId);
            // build the differences among the source rawContact with the remaining ones.

            ArrayList<ContentProviderOperation> dataInsertOps = DuplicatesUtils.diffRawEntity(
                    isSimAccount, getContentResolver(), sourceId, hashMap, rawIds);
            if (dataInsertList.size() + dataInsertOps.size() > BATCH_SIZE) {
                try {
                    getContentResolver().applyBatch(ContactsContract.AUTHORITY, rawUpdateList);
                    getContentResolver().applyBatch(ContactsContract.AUTHORITY, dataInsertList);
                    rawUpdateList.clear();
                    dataInsertList.clear();
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return false;
                } catch (OperationApplicationException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            dataInsertList.addAll(dataInsertOps);
            // disable aggregation mode.
            ContentValues values = new ContentValues();
            values.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);
            rawUpdateList.add(ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI)
                    .withValues(values).withSelection(RawContacts._ID.concat(" = ?"),
                            new String[]{String.valueOf(sourceId)}).build());
            return true;
        }

        private boolean applyBatch() {
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, rawUpdateList);
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, dataInsertList);
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, rawDelList);
                rawUpdateList.clear();
                dataInsertList.clear();
                rawDelList.clear();
            } catch (RemoteException e) {
                e.printStackTrace();
                return false;
            } catch (OperationApplicationException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    }

    private class MyProgressDialog extends ProgressDialog {

        public MyProgressDialog(Context context) {
            super(context);
        }

        @Override
        public void onBackPressed() {
            // show the confirm dialog, whether the user want to cancel merging or not.
            Builder builder = new Builder(MergeContactActivity.this);
            builder.setMessage(R.string.give_up_merging);
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.give_up, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    DuplicatesUtils.mMergeState = false;
                    DuplicatesUtils.clearMergeRawContacts();
                    mProgressDialog.dismiss();
                    finish();
                }
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DuplicatesUtils.mMergeState = false;
        DuplicatesUtils.clearMergeRawContacts();
        mProgressDialog = null;
    }
}
