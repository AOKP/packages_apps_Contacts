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

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.RawContacts;
import android.view.Window;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.util.DuplicatesUtils;

import java.util.ArrayList;
import java.util.List;

public class SearchDupActivity extends Activity {

    private ProgressDialog mProgressDialog;
    private MyHandler handler = new MyHandler();

    private final String[] RAWCONTACTS_ID_PROJECTION = new String[] {RawContacts._ID};
    private final int FIND_DUPLICATED = 1;
    private final int NO_DUPLICATED = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        showDialog(R.id.search_dup_dialog);
    }

    @Override
    protected void onUserLeaveHint() {
        // If MergeContactActivity starts or user presses home key, end searching.
        DuplicatesUtils.mSearchState = false;
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            finish();
        }
        super.onUserLeaveHint();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case R.id.search_dup_dialog: {
                mProgressDialog = initSearchingDialog();
                DuplicatesUtils.setDialog(mProgressDialog);
                Thread thread = new SearchDuplicatedThread();
                DuplicatesUtils.mSearchState = true;
                thread.start();
                return mProgressDialog;
            }
        }
        return super.onCreateDialog(id);
    }

    private class SearchDuplicatedThread extends Thread {
        @Override
        public void run() {
            List<AccountWithDataSet> list = AccountTypeManager
                    .getInstance(SearchDupActivity.this).getAccounts(true);
            ArrayList<Account> accountsList = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                accountsList.add(list.get(i).getAccountOrNull());
            }

            /* calculate the contacts which can be merged. */
            boolean isComplete = DuplicatesUtils
                    .calculateMergeRawContacts(SearchDupActivity.this, accountsList,
                            getContentResolver());
            Message msg = Message.obtain();
            if (isComplete) {
                ArrayList<DuplicatesUtils.MergeContacts> mergeRawContacts =
                        DuplicatesUtils.getMergeRawContacts();
                if (mergeRawContacts != null && mergeRawContacts.size() > 0) {
                    msg.what = FIND_DUPLICATED;
                } else {
                    msg.what = NO_DUPLICATED;
                }
                handler.sendMessage(msg);
            }
        }
    }

    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NO_DUPLICATED:
                    Toast.makeText(SearchDupActivity.this, R.string.no_duplicated_contacts,
                            Toast.LENGTH_LONG).show();
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        finish();
                    }
                    break;
                case FIND_DUPLICATED:
                    Intent intent = new Intent(SearchDupActivity.this, MergeContactActivity.class);
                    startActivity(intent);
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        finish();
                    }
                    break;
            }
        }
    }

    private ProgressDialog initSearchingDialog() {
        ProgressDialog dialog = new MyProgressDialog(this);
        dialog.setMessage(this.getString(R.string.searching_duplicated_contacts));
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setCanceledOnTouchOutside(false);
        Cursor cursor = null;
        int allCount = 0;
        String selection = RawContacts.DELETED + "= 0";
        try {
            cursor = getContentResolver()
                    .query(RawContacts.CONTENT_URI, RAWCONTACTS_ID_PROJECTION,
                    selection, null, null);
        } finally {
            if (cursor != null) {
                allCount = cursor.getCount();
                cursor.close();
            }
        }
        dialog.setMax(allCount);
        return dialog;
    }

    private class MyProgressDialog extends ProgressDialog {

        public MyProgressDialog(Context context) {
            super(context);
        }

        @Override
        public void onBackPressed() {
            DuplicatesUtils.mSearchState = false;
            DuplicatesUtils.clearMergeRawContacts();
            finish();
        }
    }
}
