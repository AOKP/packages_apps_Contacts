/*
  * Copyright (C) 2016, The Linux Foundation. All Rights Reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of The Linux Foundation nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.contacts.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Intents.Insert;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.Configuration;

import com.android.contacts.R;
import com.android.contacts.activities.ConfirmAddDetailActivity;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;

import java.util.ArrayList;
import java.util.List;

public class ConfirmReplaceDetailActivity extends Activity implements
        View.OnClickListener {

    private TextView replaceTitleView;
    private LinearLayout singleLayout;
    private TextView singleNumberView;
    private RadioGroup radioGroup;

    private RawContactDeltaList mEntityDeltaList;
    private RawContactDelta mRawContactDelta;
    private String mMimeType = Phone.CONTENT_ITEM_TYPE;
    private Bundle extras;

    private List<String> mList;

    @Override
    protected void onCreate(Bundle icicle) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(icicle);
        setContentView(R.layout.confirm_replace_detail_activity);
        initView();

        resolveIntent(getIntent());

        showContent();
    }

    private void initView() {
        replaceTitleView = (TextView) findViewById(R.id.replace_title);
        singleLayout = (LinearLayout) findViewById(R.id.single_layout);
        singleNumberView = (TextView) findViewById(R.id.single_number);
        radioGroup = (RadioGroup) findViewById(R.id.radio_group);

        findViewById(R.id.btn_cancel).setOnClickListener(this);
        findViewById(R.id.btn_replace).setOnClickListener(this);
    }

    private void resolveIntent(Intent intent) {
        extras = intent.getExtras();

        mEntityDeltaList = intent.getParcelableExtra(
                ConfirmAddDetailActivity.RAWCONTACTS_DELTA_LIST);
        mRawContactDelta = mEntityDeltaList.getFirstWritableRawContact(this);

        if (extras.containsKey(Insert.PHONE)) {
            mMimeType = Phone.CONTENT_ITEM_TYPE;
        } else if (extras.containsKey(Insert.EMAIL)) {
            mMimeType = Email.CONTENT_ITEM_TYPE;
        }
    }

    private void showContent() {
        List<ValuesDelta> mValuesDelta = mRawContactDelta.getMimeEntries(mMimeType);
        int count = mValuesDelta.size();
        mList = new ArrayList<>();

        String DATA_TYPE = (mMimeType == Phone.CONTENT_ITEM_TYPE)? Phone.NUMBER : Email.DATA;
        if (count == 1) {
            String singleReplaceNumber = mValuesDelta.get(0).getAsString(DATA_TYPE);
            mList.add(singleReplaceNumber);
            singleNumberView.setText(singleReplaceNumber);
            singleLayout.setVisibility(View.VISIBLE);
            replaceTitleView.setText(R.string.replace_number_title_1);
        } else {
            for (int i=0; i < count; i++) {
                ValuesDelta valuesDelta = mValuesDelta.get(i);
                String number = valuesDelta.getAsString(DATA_TYPE);
                mList.add(number);
                RadioButton radioButton = new RadioButton(this);
                radioButton.setText(number);
                radioGroup.addView(radioButton);
                if (i == 0) {
                    radioGroup.check(radioButton.getId());
                }
            }
            replaceTitleView.setText(R.string.replace_number_title_2);
            replaceTitleView.setTypeface(null, Typeface.BOLD);
            radioGroup.setVisibility(View.VISIBLE);
        }
    }

    private void doReplaceAction() {
        String oldNumber = null;
        if (mList.size() == 1) {
            oldNumber = mList.get(0);
        } else {
            int radioButtonId = radioGroup.getCheckedRadioButtonId();
            oldNumber = ((RadioButton)findViewById(radioButtonId)).getText().toString();
        }

        int position =  mList.indexOf(oldNumber);

        ValuesDelta valuesDelta = mRawContactDelta.getMimeEntries(mMimeType).get(position);

        String newNumber = null;
        if (mMimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
            newNumber = extras.getString(Insert.PHONE);
            valuesDelta.getAfter().put(Phone.NUMBER, newNumber);
        } else {
            newNumber = extras.getString(Insert.EMAIL);
            valuesDelta.getAfter().put(Email.ADDRESS, newNumber);
        }

        Intent intent = new Intent();
        intent.putExtra(ConfirmAddDetailActivity.RAWCONTACTS_DELTA_LIST,
                (Parcelable)mEntityDeltaList);

        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_replace:
                doReplaceAction();
                break;
            case R.id.btn_cancel:
                setResult(RESULT_CANCELED);
                finish();
                break;
            default:
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

}
