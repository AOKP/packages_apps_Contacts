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

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.widget.CheckableImageView;

public class PhoneCallDetails {

    public Uri mLookupUri;

    public String mLookupKey;

    public String mName;

    public int[] mCallTypes;

    public Drawable mAccountIcon;

    public int mNumberPresentation;

    public int mNumberType;

    public int mFeatures;

    public CharSequence mNumberLabel;

    public String mNumber;

    public CharSequence mFormattedNumber;

    public String mDisplayNumber;

    public String mGeoLocation;

    public long mPhotoId;

    public Uri mPhotoUri;

    public long mCallDate;

    public long mCallId;

    public String[] mCallIds;

    public String mAccountComponentName;
    public String mAccountId;

    public long mDataID;

    CheckableImageView photoView;
    TextView nameText;
    TextView numberText;
    TextView labelText;

    public PhoneCallDetails() {
    }

    public PhoneCallDetails(View view) {
        if (view != null) {
            this.photoView = (CheckableImageView) view.findViewById(R.id.pick_contact_photo);
            this.nameText = (TextView) view.findViewById(R.id.pick_contact_name);
            this.numberText = (TextView) view.findViewById(R.id.pick_contact_number);
            this.labelText = (TextView) view.findViewById(R.id.label);
        }
    }

}
