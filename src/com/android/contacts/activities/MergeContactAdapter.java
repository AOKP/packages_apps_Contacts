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
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.util.DuplicatesUtils;

import java.util.ArrayList;

public class MergeContactAdapter extends BaseAdapter {
    private Context mContext;
    private ArrayList<DuplicatesUtils.MergeContacts> mMergeList;

    public MergeContactAdapter(Context context) {
        this.mContext = context;
    }

    @Override
    public int getCount() {
        return mMergeList.size();
    }

    @Override
    public Object getItem(int position) {
        return mMergeList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            view = LayoutInflater.from(mContext).inflate(R.layout.merge_row, parent, false);
            viewHolder.inner = (LinearLayout) view.findViewById(R.id.item_container);
            viewHolder.box = (CheckBox) view.findViewById(R.id.row_box);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        DuplicatesUtils.MergeContacts item = (DuplicatesUtils.MergeContacts) getItem(position);
        viewHolder.box.setChecked(item.isChecked());
        viewHolder.inner.removeAllViews();
        // remove the inner cache items and add the new ones.
        ArrayList<DuplicatesUtils.ContactsInfo> childItem = item.getContacts();
        // bind data with each inner item and add it to the container.
        for (int i = 0; i < childItem.size(); i++) {
            ContactListItemView childView = new ContactListItemView(mContext, null);
            bindItem(item, childItem.get(i), childView);
            viewHolder.inner.addView(childView);
        }
        return view;
    }

    /**
     * bind data with the inner child item.
     */
    private void bindItem(DuplicatesUtils.MergeContacts mergeContacts,
            DuplicatesUtils.ContactsInfo info, ContactListItemView childView) {
        Account account = new Account(mergeContacts.getAccountName(),
                mergeContacts.getAccountType());
        long photoId = info.getPhotoId();
        ContactPhotoManager.getInstance(mContext).loadThumbnail(childView.getPhotoView(), photoId,
                account, false, true, new ContactPhotoManager.DefaultImageRequest(info.getName(),
                        info.getLookUp(), true));
        String number = null;
        // for all phone numbers of the rawContact, we just need one number to show.
        if (info.getPhones() != null && info.getPhones().size() > 0) {
            number = info.getPhones().get(0);
        }
        // show the number, if it exists. Otherwise just show the name.
        childView.setPhoneNumber(number, null);
        childView.setDisplayName(info.getName());
    }

    private class ViewHolder {
        public CheckBox box;
        public LinearLayout inner;
    }

    public void setData(ArrayList list) {
        mMergeList = list;
    }
}
