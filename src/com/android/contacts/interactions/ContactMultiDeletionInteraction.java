/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.interactions;

import com.google.common.collect.Sets;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.SimContactsOperation;
import com.android.contacts.common.SimContactsConstants;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * An interaction invoked to delete multiple contacts.
 *
 * This class is very similar to {@link ContactDeletionInteraction}.
 */
public class ContactMultiDeletionInteraction extends Fragment
        implements LoaderCallbacks<Cursor> {

    public interface MultiContactDeleteListener {
        void onDeletionFinished();
    }

    private static final String FRAGMENT_TAG = "deleteMultipleContacts";
    private static final String TAG = "ContactMultiDeletionInteraction";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CONTACTS_IDS = "contactIds";
    public static final String ARG_CONTACT_IDS = "contactIds";

    private static final String[] RAW_CONTACT_PROJECTION = new String[] {
            RawContacts._ID,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.DATA_SET,
            RawContacts.CONTACT_ID,
    };

    private static final int COLUMN_INDEX_RAW_CONTACT_ID = 0;
    private static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
    private static final int COLUMN_INDEX_DATA_SET = 2;
    private static final int COLUMN_INDEX_CONTACT_ID = 3;

    private boolean mIsLoaderActive;
    private TreeSet<Long> mContactIds;
    private Context mContext;
    private AlertDialog mDialog;

    private ProgressDialog mProgressDialog;
    private SimContactsOperation mSimContactsOperation;

    private DeleteContactsThread mDeleteContactsThread;

    /**
     * Starts the interaction.
     *
     * @param activity the activity within which to start the interaction
     * @param contactIds the IDs of contacts to be deleted
     * @return the newly created interaction
     */
    public static ContactMultiDeletionInteraction start(
            Activity activity, TreeSet<Long> contactIds) {
        if (contactIds == null) {
            return null;
        }

        final FragmentManager fragmentManager = activity.getFragmentManager();
        ContactMultiDeletionInteraction fragment =
                (ContactMultiDeletionInteraction) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ContactMultiDeletionInteraction();
            fragment.setContactIds(contactIds);
            fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG)
                    .commitAllowingStateLoss();
        } else {
            fragment.setContactIds(contactIds);
        }
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mSimContactsOperation = new SimContactsOperation(mContext);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retain this fragment
        setRetainInstance(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
            mDialog = null;
        }
    }

    public void setContactIds(TreeSet<Long> contactIds) {
        mContactIds = contactIds;
        mIsLoaderActive = true;
        if (isStarted()) {
            Bundle args = new Bundle();
            args.putSerializable(ARG_CONTACT_IDS, mContactIds);
            getLoaderManager().restartLoader(R.id.dialog_delete_multiple_contact_loader_id,
                    args, this);
        }
    }

    private boolean isStarted() {
        return isAdded();
    }

    @Override
    public void onStart() {
        if (mIsLoaderActive) {
            Bundle args = new Bundle();
            args.putSerializable(ARG_CONTACT_IDS, mContactIds);
            getLoaderManager().initLoader(
                    R.id.dialog_delete_multiple_contact_loader_id, args, this);
        }
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDialog != null) {
            mDialog.hide();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final TreeSet<Long> contactIds = (TreeSet<Long>) args.getSerializable(ARG_CONTACT_IDS);
        final Object[] parameterObject = contactIds.toArray();
        final String[] parameters = new String[contactIds.size()];

        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contactIds.size(); i++) {
            parameters[i] = String.valueOf(parameterObject[i]);
            if (builder.length() != 0) {
                builder.append(",");
            }
            builder.append(parameters[i]);
            if (i == contactIds.size() -1) {
                break;
            }
        }

        builder.insert(0, RawContacts.CONTACT_ID + " IN(");
        builder.append(")");
        return new CursorLoader(mContext, RawContacts.CONTENT_URI,
                RAW_CONTACT_PROJECTION, builder.toString(), null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (!mIsLoaderActive) {
            return;
        }

        if (cursor == null || cursor.isClosed()) {
            Log.e(TAG, "Failed to load contacts");
            return;
        }

        // This cursor may contain duplicate raw contacts, so we need to de-dupe them first
        final HashSet<Long> readOnlyRawContacts = Sets.newHashSet();
        final HashSet<Long> writableRawContacts = Sets.newHashSet();
        final HashSet<Long> contactIds = Sets.newHashSet();

        AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long rawContactId = cursor.getLong(COLUMN_INDEX_RAW_CONTACT_ID);
            final String accountType = cursor.getString(COLUMN_INDEX_ACCOUNT_TYPE);
            final String dataSet = cursor.getString(COLUMN_INDEX_DATA_SET);
            final long contactId = cursor.getLong(COLUMN_INDEX_CONTACT_ID);
            contactIds.add(contactId);
            final AccountType type = accountTypes.getAccountType(accountType, dataSet);
            boolean writable = type == null || type.areContactsWritable();
            if (writable) {
                writableRawContacts.add(rawContactId);
            } else {
                readOnlyRawContacts.add(rawContactId);
            }
        }

        final int readOnlyCount = readOnlyRawContacts.size();
        final int writableCount = writableRawContacts.size();

        final int messageId;
        int positiveButtonId = android.R.string.ok;
        if (readOnlyCount > 0 && writableCount > 0) {
            messageId = R.string.batch_delete_multiple_accounts_confirmation;
        } else if (readOnlyCount > 0 && writableCount == 0) {
            messageId = R.string.batch_delete_read_only_contact_confirmation;
            positiveButtonId = R.string.readOnlyContactWarning_positive_button;
        } else if (writableCount == 1) {
            messageId = R.string.single_delete_confirmation;
            positiveButtonId = R.string.deleteConfirmation_positive_button;
        } else {
            messageId = R.string.batch_delete_confirmation;
            positiveButtonId = R.string.deleteConfirmation_positive_button;
        }

        // Convert set of contact ids into a format that is easily parcellable and iterated upon
        // for the sake of ContactSaveService.
        final Long[] contactIdObjectArray = contactIds.toArray(new Long[contactIds.size()]);
        final long[] contactIdArray = new long[contactIds.size()];
        for (int i = 0; i < contactIds.size(); i++) {
            contactIdArray[i] = contactIdObjectArray[i];
        }

        showDialog(messageId, positiveButtonId, contactIdArray);

        // We don't want onLoadFinished() calls any more, which may come when the database is
        // updating.
        getLoaderManager().destroyLoader(R.id.dialog_delete_multiple_contact_loader_id);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void showDialog(int messageId, int positiveButtonId, final long[] contactIds) {
        mDialog = new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(messageId)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                    new DeleteClickListener()
                )
                .create();

        mDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mIsLoaderActive = false;
                mDialog = null;
            }
        });
        mDialog.show();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ACTIVE, mIsLoaderActive);
        outState.putSerializable(KEY_CONTACTS_IDS, mContactIds);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mIsLoaderActive = savedInstanceState.getBoolean(KEY_ACTIVE);
            mContactIds = (TreeSet<Long>) savedInstanceState.getSerializable(KEY_CONTACTS_IDS);
        }
    }

    protected void doDeleteContact(long[] contactIds) {
        mContext.startService(ContactSaveService.createDeleteMultipleContactsIntent(mContext,
                contactIds));
        notifyListenerActivity();
    }

    private void notifyListenerActivity() {
        if (getActivity() instanceof MultiContactDeleteListener) {
            final MultiContactDeleteListener listener = (MultiContactDeleteListener) getActivity();
            listener.onDeletionFinished();
        }
    }

    /**
     * Delete contacts thread
     */
    public class DeleteContactsThread extends Thread
            implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {

        // Use to judge whether is cancel delete contacts.
        boolean mCanceled = false;

        // Use to save the operated contacts.
        private ArrayList<ContentProviderOperation> mOpsContacts = null;

        public DeleteContactsThread() {
        }

        @Override
        public void run() {

            TreeSet<Long> contactsIdSet = (TreeSet<Long>) mContactIds.clone();
            Iterator<Long> iterator = contactsIdSet.iterator();

            ContentProviderOperation cpo = null;
            ContentProviderOperation.Builder builder = null;

            // Current contact count we can delete.
            int count = 0;

            // The contacts we batch delete once.
            final int BATCH_DELETE_CONTACT_NUMBER = 200;

            mOpsContacts = new ArrayList<ContentProviderOperation>();

            while (!mCanceled & iterator.hasNext()) {
                // Set the progress of progress dialog.
                mProgressDialog.setProgress(count);
                String id = String.valueOf(iterator.next());
                long longId = Long.parseLong(id);
                // Get contacts Uri
                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id);

                // Judge the contacts whether is the SIM card contacts
                ContentValues values = mSimContactsOperation
                        .getSimAccountValues(longId);
                String accountType = values
                        .getAsString(SimContactsConstants.ACCOUNT_TYPE);
                String accountName = values
                        .getAsString(SimContactsConstants.ACCOUNT_NAME);
                int subscription = MoreContactUtils.getSubscription(
                        accountType, accountName);
                if (subscription == SimContactsConstants.SLOT1
                        || subscription == SimContactsConstants.SLOT2) {
                    int result = mSimContactsOperation.delete(values,
                            subscription);
                    if (result == 0) {
                        mProgressDialog.incrementProgressBy(1);
                        continue;
                    }
                }
                builder = ContentProviderOperation.newDelete(uri);
                cpo = builder.build();
                mOpsContacts.add(cpo);
                // If contacts more than 2000, delete all contacts
                // one by one will cause UI nonresponse.
                mProgressDialog.incrementProgressBy(1);
                // We batch delete contacts every 100.
                if (count % BATCH_DELETE_CONTACT_NUMBER == 0) {
                    batchDelete();
                }
                count++;
            }
            batchDelete();
            mOpsContacts = null;
            dismissProgressDialog();
            // Set thread to null when complete delete.
            setDeleteContactsThread(null);
        }

        /**
         * Batch delete contacts more efficient than one by one.
         */
        private void batchDelete() {
            try {
                mContext.getContentResolver().applyBatch(
                        android.provider.ContactsContract.AUTHORITY, mOpsContacts);
                mOpsContacts.clear();
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (OperationApplicationException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onCancel(DialogInterface dialogInterface) {
            // Cancel delete operate.
            mCanceled = true;
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            if (i == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            }
        }

        /**
         * Rebuild delete contact's progress dialog when DeleteContactsThread was
         * running.
         * @param activity
         */
        public void setActivity(PeopleActivity activity) {

            if (activity == null) {
                mProgressDialog.dismiss();
                return;
            }

            mContext = activity;
            if (mContext != null) {
                CharSequence title = getString(R.string.delete_contacts_title);
                CharSequence message = getString(R.string.delete_contacts_message);

                mProgressDialog = new ProgressDialog(mContext);
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage(message);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                        getString(android.R.string.cancel), getDeleteContactsThread());
                mProgressDialog.setOnCancelListener(getDeleteContactsThread());

                mProgressDialog.setMax(mContactIds.size());

                // set dialog can not be canceled by touching outside area of
                // dialog.
                mProgressDialog.setCanceledOnTouchOutside(false);
                mProgressDialog.show();
            }
        }

    }

    /**
     * Set thread data.
     * @param thread
     */
    private void setDeleteContactsThread(DeleteContactsThread thread) {
        mDeleteContactsThread = thread;
    }

    /**
     * Use to provide thread data.
     * @return
     */
    public DeleteContactsThread getDeleteContactsThread() {
        return mDeleteContactsThread;
    }


    /**
     * Monitor delete contacts operate.
     */
    private class DeleteClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {

            CharSequence title = getString(R.string.delete_contacts_title);
            CharSequence message = getString(R.string.delete_contacts_message);

            if (mProgressDialog != null) {
                dismissProgressDialog();
            }

            // Build delete contacts thread
            Thread mThread = new DeleteContactsThread();

            // Build the ProgressDialog.
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setTitle(title);
            mProgressDialog.setMessage(message);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(android.R.string.cancel), (DialogInterface.OnClickListener) mThread);
            mProgressDialog.setOnCancelListener((DialogInterface.OnCancelListener) mThread);
            mProgressDialog.setMax(mContactIds.size());

            // set dialog can not be canceled by touching outside area of
            // dialog.
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();
            notifyListenerActivity();
            // Set DeleteContactsThread
            setDeleteContactsThread((DeleteContactsThread)mThread);

            // Start delete contacts thread
            mThread.start();
        }
    }

    private void dismissProgressDialog() {
        if (getActivity() != null && !getActivity().isDestroyed()
                && mProgressDialog.isShowing() && isAdded()) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}
