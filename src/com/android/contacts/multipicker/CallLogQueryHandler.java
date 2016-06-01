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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.provider.VoicemailContract.Voicemails;
import android.util.Log;

import com.android.contacts.common.database.NoNullCursorAsyncQueryHandler;
import com.google.common.collect.Lists;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Handles asynchronous queries to the call log.
 */
public class CallLogQueryHandler extends NoNullCursorAsyncQueryHandler {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final String TAG = "CallLogQueryHandler";
    private static final int NUM_LOGS_TO_DISPLAY = 1000;

    public static final int QUERY_CALLLOG_TOKEN = 54;

    private final WeakReference<Listener> mListener;

    public static Uri getCallLogUri() {
        return Calls.CONTENT_URI;
    }

    public static final String[] _PROJECTION = new String[] {
            Calls._ID, // 0
            Calls.NUMBER, // 1
            Calls.DATE, // 2
            Calls.TYPE, // 3
            Calls.CACHED_NAME, // 4
            Calls.CACHED_NUMBER_TYPE, // 5
            Calls.CACHED_NUMBER_LABEL, // 6
            Calls.COUNTRY_ISO, // 7
            Calls.GEOCODED_LOCATION, // 8
            Calls.CACHED_LOOKUP_URI, // 9
            Calls.CACHED_MATCHED_NUMBER, // 10
            Calls.CACHED_NORMALIZED_NUMBER, // 11
            Calls.CACHED_PHOTO_ID, // 12
            Calls.CACHED_FORMATTED_NUMBER, // 13
            Calls.NUMBER_PRESENTATION, // 14
            Calls.PHONE_ACCOUNT_COMPONENT_NAME, // 15
            Calls.PHONE_ACCOUNT_ID, // 16
            Calls.FEATURES, // 17
            Calls.CACHED_PHOTO_URI // 18
    };

    public static final int ID = 0;
    public static final int NUMBER = 1;
    public static final int DATE = 2;
    public static final int CALL_TYPE = 3;
    public static final int CACHED_NAME = 4;
    public static final int CACHED_NUMBER_TYPE = 5;
    public static final int CACHED_NUMBER_LABEL = 6;
    public static final int COUNTRY_ISO = 7;
    public static final int GEOCODED_LOCATION = 8;
    public static final int CACHED_LOOKUP_URI = 9;
    public static final int CACHED_MATCHED_NUMBER = 10;
    public static final int CACHED_NORMALIZED_NUMBER = 11;
    public static final int CACHED_PHOTO_ID = 12;
    public static final int CACHED_FORMATTED_NUMBER = 13;
    public static final int NUMBER_PRESENTATION = 14;
    public static final int ACCOUNT_COMPONENT_NAME = 15;
    public static final int ACCOUNT_ID = 16;
    public static final int FEATURES = 17;
    public static final int CACHED_PHOTO_URI = 18;

    protected class CatchingWorkerHandler extends WorkerHandler {
        public CatchingWorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                super.handleMessage(msg);
            } catch (Exception e) {
                Log.w(TAG, "Exception on background worker thread", e);
            }
        }
    }

    @Override
    protected Handler createHandler(Looper looper) {
        return new CatchingWorkerHandler(looper);
    }

    public CallLogQueryHandler(ContentResolver contentResolver,
            Listener listener) {
        super(contentResolver);
        mListener = new WeakReference<Listener>(listener);
    }

    public void fetchCalls(int token) {
        StringBuilder where = new StringBuilder();
        List<String> selectionArgs = Lists.newArrayList();

        // Ignore voicemails marked as deleted
        where.append(Voicemails.DELETED);
        where.append(" = 0");

        final String selection = where.length() > 0 ? where.toString() : null;
        Uri uri = getCallLogUri().buildUpon()
                .appendQueryParameter(Calls.LIMIT_PARAM_KEY, Integer.toString(NUM_LOGS_TO_DISPLAY))
                .build();

        startQuery(token, null, uri,
                _PROJECTION, selection, selectionArgs.toArray(EMPTY_STRING_ARRAY),
                Calls.DEFAULT_SORT_ORDER);
    }

    @Override
    protected synchronized void onNotNullableQueryComplete(int token, Object cookie,
            Cursor cursor) {
        if (cursor == null) {
            return;
        }
        try {
            if (token == QUERY_CALLLOG_TOKEN) {
                if (updateAdapterData(cursor)) {
                    cursor = null;
                }
            } else {
                Log.w(TAG, "Unknown query completed: ignoring: " + token);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean updateAdapterData(Cursor cursor) {
        final Listener listener = mListener.get();
        if (listener != null) {
            return listener.onCallsFetched(cursor);
        }
        return false;
    }

    public interface Listener {
        boolean onCallsFetched(Cursor combinedCursor);
    }
}
