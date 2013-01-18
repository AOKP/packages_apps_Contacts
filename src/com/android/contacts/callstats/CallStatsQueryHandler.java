/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.contacts.callstats;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.util.Log;

import com.google.common.collect.Lists;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Class to handle call-log queries, optionally with a date-range filter
 */
public class CallStatsQueryHandler extends AsyncQueryHandler {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final int QUERY_CALLS_TOKEN = 100;

    public static final int CALL_TYPE_ALL = 0;

    private static final String TAG = "CallStatsQueryHandler";

    private final WeakReference<Listener> mListener;

    /**
     * Simple handler that wraps background calls to catch
     * {@link SQLiteException}, such as when the disk is full.
     */
    protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
        public CatchingWorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                // Perform same query while catching any exceptions
                super.handleMessage(msg);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            }
        }
    }

    @Override
    protected Handler createHandler(Looper looper) {
        // Provide our special handler that catches exceptions
        return new CatchingWorkerHandler(looper);
    }

    public CallStatsQueryHandler(ContentResolver contentResolver, Listener listener) {
        super(contentResolver);
        mListener = new WeakReference<Listener>(listener);
    }

    public void fetchCalls(long from, long to) {
        cancelOperation(QUERY_CALLS_TOKEN);

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = Lists.newArrayList();

        if (from != -1) {
            selection.append(String.format("(%s > ?)", Calls.DATE));
            selectionArgs.add(String.valueOf(from));
        }
        if (to != -1) {
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append(String.format("(%s < ?)", Calls.DATE));
            selectionArgs.add(String.valueOf(to));
        }

        startQuery(QUERY_CALLS_TOKEN, null, Calls.CONTENT_URI, CallStatsQuery._PROJECTION,
                selection.toString(), selectionArgs.toArray(EMPTY_STRING_ARRAY),
                Calls.NUMBER + " DESC");
    }

    @Override
    protected synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (token == QUERY_CALLS_TOKEN) {
            final Listener listener = mListener.get();
            if (listener != null) {
                listener.onCallsFetched(cursor);
            }
        }
    }

    public interface Listener {
        void onCallsFetched(Cursor c);
    }
}
