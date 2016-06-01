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


package com.android.contacts.list;

import com.android.contacts.common.SimContactsConstants;

import android.content.Intent;

import com.android.contacts.R;


/**
 * Parsed form of the intent sent to the Contacts application.
 */
public class ContactsPickMode {

    private static ContactsPickMode mPickMode;

    private static final String MODE = "mode";

    private static final int MODE_INFO = 1;
    private static final int MODE_VCARD = 2;

    private boolean mSelectCallLog;
    private static final String KEY_SELECT_CALLLOG = "selectcalllog";

    private int mMode;
    private Intent mIntent;

    private static final int MODE_MASK_SEARCH = 0x80000000;

    public static final int MODE_DEFAULT_CONTACT = 0;
    public static final int MODE_DEFAULT_PHONE = 1;
    public static final int MODE_DEFAULT_EMAIL = 1 << 1;
    public static final int MODE_DEFAULT_CALL = 1 << 1 << 1;
    public static final int MODE_DEFAULT_SIM = 1 << 1 << 1 << 1;
    public static final int MODE_DEFAULT_GROUP = 1 << 1 << 1 << 1 << 1;
    public static final int MODE_DEFAULT_CONTACT_INFO = 1 << 1 << 1 << 1 << 1 << 1;
    public static final int MODE_DEFAULT_CONTACT_VCARD = 1 << 1 << 1 << 1 << 1 << 1 << 1;
    public static final int MODE_SEARCH_CONTACT = MODE_DEFAULT_CONTACT | MODE_MASK_SEARCH;
    public static final int MODE_SEARCH_PHONE = MODE_DEFAULT_PHONE | MODE_MASK_SEARCH;
    public static final int MODE_SEARCH_EMAIL = MODE_DEFAULT_EMAIL | MODE_MASK_SEARCH;
    public static final int MODE_SEARCH_CALL = MODE_DEFAULT_CALL | MODE_MASK_SEARCH;
    public static final int MODE_SEARCH_SIM = MODE_DEFAULT_SIM | MODE_MASK_SEARCH;
    public static final int MODE_SEARCH_GROUP = MODE_DEFAULT_GROUP | MODE_MASK_SEARCH;
    public static final int MODE_SEARCH_CONTACT_INFO
            = MODE_DEFAULT_CONTACT_INFO | MODE_MASK_SEARCH;
    public static final int MODE_SEARCH_CONTACT_VCARD
            = MODE_DEFAULT_CONTACT_VCARD | MODE_MASK_SEARCH;

    public static ContactsPickMode getInstance() {
        if (mPickMode == null) {
            mPickMode = new ContactsPickMode();
        }
        return mPickMode;
    }

    public boolean isSelectCallLog() {
        return mSelectCallLog;
    }

    public boolean isSearchMode() {
        return (mMode & MODE_MASK_SEARCH) == MODE_MASK_SEARCH;
    }

    public boolean isPickContact() {
        return mMode == MODE_DEFAULT_CONTACT || mMode == MODE_SEARCH_CONTACT;
    }

    public boolean isPickPhone() {
        return mMode == MODE_DEFAULT_PHONE || mMode == MODE_SEARCH_PHONE;
    }

    public boolean isPickSim() {
        return mMode == MODE_DEFAULT_SIM || mMode == MODE_SEARCH_SIM;
    }

    public boolean isPickEmail() {
        return mMode == MODE_DEFAULT_EMAIL || mMode == MODE_SEARCH_EMAIL;
    }

    public boolean isPickCall() {
        return mMode == MODE_DEFAULT_CALL || mMode == MODE_SEARCH_CALL;
    }

    public boolean isPickContactInfo() {
        return mMode == MODE_DEFAULT_CONTACT_INFO || mMode == MODE_SEARCH_CONTACT_INFO;
    }

    public boolean isPickContactVcard() {
        return mMode == MODE_DEFAULT_CONTACT_VCARD || mMode == MODE_SEARCH_CONTACT_VCARD;
    }

    public void enterSearchMode() {
        mMode |= MODE_MASK_SEARCH;
    }

    public void exitSearchMode() {
        mMode &= ~MODE_MASK_SEARCH;
    }

    public void setMode(int mode) {
        mMode = mode;
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(Intent intent) {
        mIntent = intent;
        String action = intent.getAction();
        boolean isContact = intent.getBooleanExtra(
                SimContactsConstants.IS_CONTACT, false);
        if (Intent.ACTION_DELETE.equals(action)) {
            mMode = MODE_DEFAULT_CONTACT;
        } else if (SimContactsConstants.ACTION_MULTI_PICK.equals(action)) {
            if (!isContact) {
                int mode = intent.getIntExtra(MODE, 0);
                if (mode == MODE_INFO) {
                    mMode = MODE_DEFAULT_CONTACT_INFO;
                } else if (mode == MODE_VCARD) {
                    mMode = MODE_DEFAULT_CONTACT_VCARD;
                } else {
                    mMode = MODE_DEFAULT_PHONE;
                }
            } else {
                mMode = MODE_DEFAULT_CONTACT;
            }
        } else if (SimContactsConstants.ACTION_MULTI_PICK_EMAIL.equals(action)) {
            mMode = MODE_DEFAULT_EMAIL;
        } else if (SimContactsConstants.ACTION_MULTI_PICK_CALL.equals(action)) {
            mMode = MODE_DEFAULT_CALL;
            if (intent.getBooleanExtra(KEY_SELECT_CALLLOG, false)) {
                mSelectCallLog = true;
            }
        } else if (SimContactsConstants.ACTION_MULTI_PICK_SIM.equals(action)) {
            mMode = MODE_DEFAULT_SIM;
        }
    }

    public Intent getIntent() {
        return mIntent;
    }
}
