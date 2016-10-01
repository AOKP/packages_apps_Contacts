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

import android.app.Activity;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.AbsListView;

import com.android.contacts.R;
import com.android.contacts.activities.MultiPickContactsActivity;
import com.android.contacts.list.OnCheckListActionListener;

public class DelCallLogFragment extends ListFragment
        implements CallLogQueryHandler.Listener, DelCallLogAdapter.CallFetcher {

    private OnCheckListActionListener mCheckListListener;

    private ContentResolver resolver;

    private CallLogQueryHandler mCallLogQueryHandler;
    private DelCallLogAdapter mDelCallLogAdapter;
    private Context mContext;

    @Override
    public void fetchCalls() {
        mCallLogQueryHandler.fetchCalls(CallLogQueryHandler.QUERY_CALLLOG_TOKEN);
    }

    @Override
    public boolean onCallsFetched(Cursor combinedCursor) {
        if (getActivity() == null || getActivity().isFinishing() || combinedCursor == null) {
            // Did not take the cursor
            return false;
        }
        mDelCallLogAdapter.changeCursor(combinedCursor);
        return true;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Activity activity = getActivity();
        resolver = activity.getContentResolver();
        mCallLogQueryHandler = new CallLogQueryHandler(resolver, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.recents_fragment, container, false);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mCheckListListener == null)
            mCheckListListener = ((MultiPickContactsActivity) getActivity())
                    .createListener();
        if (mDelCallLogAdapter == null) {
            mDelCallLogAdapter = new DelCallLogAdapter(mContext);
            mDelCallLogAdapter.setCheckListListener(mCheckListListener);
        }
        View view = new View(mContext);
        AbsListView.LayoutParams layoutParams = new AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                (int)(mContext.getResources().getDimension(R.dimen.header_listview_height)));
        view.setLayoutParams(layoutParams);
        getListView().addHeaderView(view, null, false);
        setListAdapter(mDelCallLogAdapter);
        fetchCalls();
    }

    @Override
    public void onDestroy() {
        if (mDelCallLogAdapter.getCursor() != null) {
            mDelCallLogAdapter.getCursor().close();
        }
        super.onDestroy();
    }

    public DelCallLogAdapter getAdapter() {
        return mDelCallLogAdapter;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mCheckListListener.onHideSoftKeyboard();
        PhoneCallDetails details = (PhoneCallDetails) v.getTag();

        if (null != details) {
            String key = String.valueOf(details.mCallId);
            if (!mCheckListListener.onContainsKey(key)) {
                mCheckListListener.putValue(key, details.mCallIds);
            } else {
                mCheckListListener.onRemove(key);
            }
            mCheckListListener.onUpdateActionBar();
            mDelCallLogAdapter.notifyDataSetChanged();
        }
    }

    public void setCheckListListener(OnCheckListActionListener checkListActionListener) {
        mCheckListListener = checkListActionListener;
        if (mDelCallLogAdapter != null) {
            mDelCallLogAdapter.setCheckListListener(checkListActionListener);
        }
    }

    /**
     * @param isSelectedAll isSelectedAll is true, selected all call logs isSelectedAll is False,
     *            deselected all call logs
     */
    public void setSelectedAll(boolean isSelectedAll) {
        final int count = mDelCallLogAdapter.getCount();
        if (count == 0) {
            return;
        }

        String key;
        String[] value;

        if (isSelectedAll) {
            for (int position = 0; position < count; position++) {
                Cursor cursor = (Cursor) mDelCallLogAdapter.getItem(position);
                if (cursor == null) {
                    continue;
                }
                int groupSize = mDelCallLogAdapter.isGroupHeader(position)
                        ? mDelCallLogAdapter.getGroupSize(position)
                        : mDelCallLogAdapter.STAND_ALONE_ITEM_SIZE;

                value = mDelCallLogAdapter.getCallIds(cursor, groupSize);
                key = String.valueOf(cursor.getInt(CallLogQueryHandler.ID));

                if (!mCheckListListener.onContainsKey(key)) {
                    mCheckListListener.putValue(key, value);
                }
            }
        } else {
            for (int position = 0; position < count; position++) {
                Cursor cursor = (Cursor) mDelCallLogAdapter.getItem(position);
                if (cursor == null) {
                    continue;
                }
                key = String.valueOf(cursor.getInt(CallLogQueryHandler.ID));
                mCheckListListener.onRemove(key);
            }
        }
        mCheckListListener.onUpdateActionBar();
        mDelCallLogAdapter.notifyDataSetChanged();
    }

}
