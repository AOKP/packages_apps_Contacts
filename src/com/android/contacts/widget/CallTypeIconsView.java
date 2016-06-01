/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.util.AttributeSet;
import android.view.View;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.BitmapUtil;
import com.android.contacts.R;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Combine call type view to one view
 */
public class CallTypeIconsView extends View {
    private List<Integer> mCallTypes = Lists.newArrayListWithCapacity(3);
    private boolean mShowVideo = false;
    private int mWidth;
    private int mHeight;

    private static Resources sResources;

    public CallTypeIconsView(Context context) {
        this(context, null);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (sResources == null) {
          sResources = new Resources(context);
        }
    }

    public void clear() {
        mCallTypes.clear();
        mWidth = 0;
        mHeight = 0;
        invalidate();
    }

    public void add(int callType) {
        mCallTypes.add(callType);

        final Drawable drawable = getCallTypeDrawable(callType);
        mWidth += drawable.getIntrinsicWidth() + sResources.iconMargin;
        mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
        invalidate();
    }

    public void setShowVideo(boolean showVideo) {
        mShowVideo = showVideo;
        if (showVideo) {
            mWidth += sResources.videoCall.getIntrinsicWidth();
            mHeight = Math.max(mHeight, sResources.videoCall.getIntrinsicHeight());
            invalidate();
        }
    }

    public boolean isVideoShown() {
        return mShowVideo;
    }

    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                return sResources.incoming;
            case Calls.OUTGOING_TYPE:
                return sResources.outgoing;
            case Calls.MISSED_TYPE:
                return sResources.missed;
            case Calls.VOICEMAIL_TYPE:
                return sResources.voicemail;
            default:
                return sResources.missed;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = 0;
        for (Integer callType : mCallTypes) {
            final Drawable drawable = getCallTypeDrawable(callType);
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + sResources.iconMargin;
        }

        // If showing the video call icon, draw it scaled appropriately.
        if (mShowVideo) {
            final Drawable drawable = sResources.videoCall;
            final int right = left + sResources.videoCall.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, sResources.videoCall.getIntrinsicHeight());
            drawable.draw(canvas);
        }
    }

    private static class Resources {

        public final Drawable incoming;

        public final Drawable outgoing;

        public final Drawable missed;

        public final Drawable voicemail;

        public final Drawable blocked;

        public final Drawable videoCall;

        public final int iconMargin;

        public Resources(Context context) {
            final android.content.res.Resources r = context.getResources();

            incoming = r.getDrawable(R.drawable.ic_call_arrow);
            incoming.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);

            // mutate, make a copy of the drawablem,
            // if not, the same instance will recolored here by other instance
            missed = r.getDrawable(R.drawable.ic_call_arrow).mutate();
            missed.setColorFilter(r.getColor(R.color.missed_call), PorterDuff.Mode.MULTIPLY);

            // Create a rotated instance
            outgoing = BitmapUtil.getRotatedDrawable(r, R.drawable.ic_call_arrow, 180f);
            outgoing.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);

            voicemail = r.getDrawable(R.drawable.ic_call_voicemail_holo_dark);

            blocked = r.getDrawable(R.drawable.ic_block_24dp);
            blocked.setColorFilter(r.getColor(R.color.blocked_call), PorterDuff.Mode.MULTIPLY);

            videoCall = r.getDrawable(R.drawable.ic_videocam_24dp);
            videoCall.setColorFilter(r.getColor(R.color.dialtacts_secondary_text_color),
                    PorterDuff.Mode.MULTIPLY);

            iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
        }
    }
}
