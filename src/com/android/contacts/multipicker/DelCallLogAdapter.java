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

package com.android.contacts.multipicker;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.QuickContact;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.common.widget.GroupingListAdapter;
import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.common.widget.CheckableImageView;
import com.android.contacts.list.OnCheckListActionListener;
import com.android.contacts.widget.CallTypeIconsView;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class DelCallLogAdapter extends GroupingListAdapter implements View.OnClickListener {

    private static final Set<String> LEGACY_UNKNOWN_NUMBERS = Sets.newHashSet("-1", "-2", "-3");

    public static final int STAND_ALONE_ITEM_SIZE = 1;

    private static final int MAX_CALL_TYPE_COUNT = 3;

    private Context mContext;

    private OnCheckListActionListener mCheckListListener;

    private ContactPhotoManager mContactPhotoManager;

    public interface CallFetcher {
        void fetchCalls();
    }

    public DelCallLogAdapter(Context context) {
        super(context);
        this.mContext = context;
        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
    }

    @Override
    protected void addGroups(Cursor cursor) {
        if (cursor == null) {
            return;
        }
        final int count = cursor.getCount();
        if (count == 0) {
            return;
        }
        int currentGroupSize = 1;
        cursor.moveToFirst();
        // The number of the first entry in the group.
        String firstNumber = cursor.getString(CallLogQueryHandler.NUMBER);
        // This is the type of the first call in the group.
        int firstCallType = cursor.getInt(CallLogQueryHandler.CALL_TYPE);
        // The account information of the first entry in the group.
        String firstAccountComponentName = cursor
                .getString(CallLogQueryHandler.ACCOUNT_COMPONENT_NAME);
        String firstAccountId = cursor.getString(CallLogQueryHandler.ACCOUNT_ID);

        while (cursor.moveToNext()) {
            // The number of the current row in the cursor.
            final String currentNumber = cursor.getString(CallLogQueryHandler.NUMBER);
            final int callType = cursor.getInt(CallLogQueryHandler.CALL_TYPE);
            final String currentAccountComponentName = cursor
                    .getString(CallLogQueryHandler.ACCOUNT_COMPONENT_NAME);
            final String currentAccountId = cursor.getString(CallLogQueryHandler.ACCOUNT_ID);
            // Judge whether number is same.
            final boolean isSameNumber = equalNumbers(firstNumber, currentNumber);
            final boolean isSameAccount = isSameAccount(
                    firstAccountComponentName, currentAccountComponentName,
                    firstAccountId, currentAccountId);
            boolean shouldGroup;

            if (!isSameNumber || !isSameAccount) {
                // Should only group with calls from the same number.
                shouldGroup = false;
            } else if (firstCallType == Calls.VOICEMAIL_TYPE) {
                // Never group voicemail.
                shouldGroup = false;
            } else {
                // Incoming, outgoing, and missed calls group together.
                shouldGroup = callType != Calls.VOICEMAIL_TYPE;
            }

            if (shouldGroup) {
                // Increment the size of the group to include the current call,
                // but do not create the group until we find a call that does not match.
                currentGroupSize++;
            } else {
                // Create a group for the previous set of calls, excluding the
                // current one, but do not create a group for a single call.
                if (currentGroupSize > 1) {
                    addGroup(cursor.getPosition() - currentGroupSize, currentGroupSize, false);
                }
                // Start a new group; it will include at least the current call.
                currentGroupSize = 1;
                // The current entry is now the first in the group.
                firstNumber = currentNumber;
                firstCallType = callType;
                firstAccountComponentName = currentAccountComponentName;
                firstAccountId = currentAccountId;
            }
        }
        // If the last set of calls at the end of the call log was itself a
        // group, create it now.
        if (currentGroupSize > 1) {
            addGroup(count - currentGroupSize, currentGroupSize, false);
        }

    }

    boolean equalNumbers(String number1, String number2) {
        if (PhoneNumberHelper.isUriNumber(number1) || PhoneNumberHelper.isUriNumber(number2)) {
            return compareSipAddresses(number1, number2);
        } else {
            return PhoneNumberUtils.compare(number1, number2);
        }
    }

    private boolean isSameAccount(String name1, String name2, String id1, String id2) {
        return TextUtils.equals(name1, name2) && TextUtils.equals(id1, id2);
    }

    private boolean compareSipAddresses(String number1, String number2) {
        if (number1 == null || number2 == null)
            return number1 == number2;

        int index1 = number1.indexOf('@');
        final String userinfo1;
        final String rest1;
        if (index1 != -1) {
            userinfo1 = number1.substring(0, index1);
            rest1 = number1.substring(index1);
        } else {
            userinfo1 = number1;
            rest1 = "";
        }

        int index2 = number2.indexOf('@');
        final String userinfo2;
        final String rest2;
        if (index2 != -1) {
            userinfo2 = number2.substring(0, index2);
            rest2 = number2.substring(index2);
        } else {
            userinfo2 = number2;
            rest2 = "";
        }

        return userinfo1.equals(userinfo2) && rest1.equalsIgnoreCase(rest2);
    }

    @Override
    protected View newStandAloneView(Context context, ViewGroup viewGroup) {

        View standAloneView = LayoutInflater.from(mContext).inflate(R.layout.call_log_list_item,
                viewGroup, false);
        PhoneCallDetails details = new PhoneCallDetails();
        standAloneView.setTag(details);
        return standAloneView;
    }

    @Override
    protected void bindStandAloneView(View view, Context context, Cursor cursor) {
        PhoneCallDetails details = (PhoneCallDetails) view.getTag();
        bindData(details, cursor, STAND_ALONE_ITEM_SIZE);
        bindView(details, view);
    }

    @Override
    protected View newGroupView(Context context, ViewGroup viewGroup) {
        View groupView = LayoutInflater.from(mContext).inflate(R.layout.call_log_list_item,
                viewGroup, false);
        PhoneCallDetails details = new PhoneCallDetails();
        groupView.setTag(details);
        return groupView;
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
            boolean expanded) {
        PhoneCallDetails details = (PhoneCallDetails) view.getTag();
        bindData(details, cursor, groupSize);
        bindView(details, view);
    }

    @Override
    protected View newChildView(Context context, ViewGroup viewGroup) {
        /* No Child View */
        return null;
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor) {
        /* No Child View */
    }

    class DelCallLogViewHolder {
        public View mView;
        public CheckableImageView mPhotoView;
        public TextView mNameText;
        public CallTypeIconsView mCallTypeIcons;
        public TextView mNumberText;
        public ImageView mCallAccountIcon;
        public TextView mCallLocationAndDateText;
        public TextView mLabelText;
        public LinearLayout mCallTypeLayout;

        public DelCallLogViewHolder(View view) {
            this.mView = view;
            this.mPhotoView = (CheckableImageView) view.findViewById(R.id.photo_view);
            this.mNameText = (TextView) view.findViewById(R.id.name);
            this.mNumberText = (TextView) view.findViewById(R.id.number);
            this.mCallTypeIcons = (CallTypeIconsView) view.findViewById(R.id.call_type_icons);
            this.mCallAccountIcon = (ImageView) view.findViewById(R.id.call_account_icon);
            this.mCallLocationAndDateText = (TextView) view
                    .findViewById(R.id.call_count_or_location_and_date);
            this.mLabelText = (TextView) view.findViewById(R.id.label);
            this.mCallTypeLayout = (LinearLayout) view.findViewById(R.id.call_type);
        }
    }

    private void bindData(PhoneCallDetails details, Cursor cursor, int groupSize) {

        details.mLookupUri = UriUtils.parseUriOrNull(
                cursor.getString(CallLogQueryHandler.CACHED_LOOKUP_URI));

        details.mLookupKey = UriUtils.getLookupKeyFromUri(details.mLookupUri);

        // formatted number. e.g: 177 0514 xxxx
        details.mFormattedNumber = cursor.getString(CallLogQueryHandler.CACHED_FORMATTED_NUMBER);

        details.mNumber = cursor.getString(CallLogQueryHandler.NUMBER);
        /** The number presenting rules set by the network e.g, {@link Calls#NUMBER_PRESENTATION} */
        details.mNumberPresentation = cursor.getInt(CallLogQueryHandler.NUMBER_PRESENTATION);
        details.mDisplayNumber = getDisplayNumber(
                mContext, details.mNumber,
                details.mNumberPresentation,
                details.mFormattedNumber).toString();

        details.mPhotoId = cursor.getLong(CallLogQueryHandler.CACHED_PHOTO_ID);
        details.mPhotoUri = UriUtils.parseUriOrNull(
                cursor.getString(CallLogQueryHandler.CACHED_PHOTO_URI));

        details.mName = cursor.getString(CallLogQueryHandler.CACHED_NAME);

        if (!TextUtils.isEmpty(details.mName)) {
            details.mNumberLabel = cursor.getString(CallLogQueryHandler.CACHED_NUMBER_LABEL);
            details.mNumberType = cursor.getInt(CallLogQueryHandler.CACHED_NUMBER_TYPE);
        } else {
            details.mGeoLocation = cursor.getString(CallLogQueryHandler.GEOCODED_LOCATION);
        }

        // Get features with a group.
        details.mFeatures = getCallFeatures(cursor, groupSize);

        details.mCallId = cursor.getInt(CallLogQueryHandler.ID);

        details.mCallIds = getCallIds(cursor, groupSize);

        // According mAccountComponentName and mAccountId to get accountHandle.
        details.mAccountId = cursor.getString(CallLogQueryHandler.ACCOUNT_ID);
        details.mAccountComponentName = cursor
                .getString(CallLogQueryHandler.ACCOUNT_COMPONENT_NAME);
        final PhoneAccountHandle accountHandle = getAccount(details.mAccountComponentName,
                details.mAccountId);
        details.mAccountIcon = getAccountIcon(mContext, accountHandle);

        details.mCallDate = cursor.getLong(CallLogQueryHandler.DATE);

        details.mCallTypes = getCallTypes(details, cursor, groupSize);

    }

    private CharSequence getDisplayNumber(Context context, CharSequence number,
            int presentation, CharSequence formattedNumber) {
        final CharSequence displayName = getDisplayName(context, number, presentation);
        if (!TextUtils.isEmpty(displayName)) {
            return displayName;
        }

        if (!TextUtils.isEmpty(formattedNumber)) {
            return formattedNumber;
        } else if (!TextUtils.isEmpty(number)) {
            return number;
        } else {
            return "";
        }
    }

    private CharSequence getDisplayName(Context context, CharSequence number,
            int presentation) {
        if (presentation == CallLog.Calls.PRESENTATION_UNKNOWN) {
            return context.getResources().getString(R.string.unknown);
        }
        if (presentation == CallLog.Calls.PRESENTATION_RESTRICTED) {
            return context.getResources().getString(R.string.private_num);
        }
        if (presentation == CallLog.Calls.PRESENTATION_PAYPHONE) {
            return context.getResources().getString(R.string.payphone);
        }
        if (isLegacyUnknownNumbers(number)) {
            return context.getResources().getString(R.string.unknown);
        }
        return "";
    }

    private boolean isLegacyUnknownNumbers(CharSequence number) {
        return number != null && LEGACY_UNKNOWN_NUMBERS.contains(number.toString());
    }

    /**
     * Determine the features which were enabled for any of the calls that make up a call log entry.
     *
     * @param cursor The cursor.
     * @param groupSize The number of calls for the current call log entry.
     * @return The features.
     */
    private int getCallFeatures(Cursor cursor, int groupSize) {
        int features = 0;
        int position = cursor.getPosition();
        for (int index = 0; index < groupSize; ++index) {
            features |= cursor.getInt(CallLogQueryHandler.FEATURES);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return features;
    }

    /**
     * Retrieves the call Ids represented by the current call log row.
     *
     * @param cursor Call log cursor to retrieve call Ids from.
     * @param groupSize Number of calls associated with the current call log row.
     * @return Array of call Ids.
     */
    public String[] getCallIds(final Cursor cursor, final int groupSize) {
        // Restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        String[] ids = new String[groupSize];
        // Copy the ids of the rows in the group.
        for (int index = 0; index < groupSize; index++) {
            ids[index] = String.valueOf(cursor.getInt(CallLogQueryHandler.ID));
            cursor.moveToNext();
        }
        cursor.moveToPosition(startingPosition);
        return ids;
    }

    private Drawable getAccountIcon(Context context, PhoneAccountHandle accountHandle) {
        TelecomManager telecomManager = (TelecomManager) context
                .getSystemService(Context.TELECOM_SERVICE);
        final PhoneAccount account;

        if (telecomManager.getCallCapablePhoneAccounts().size() <= 1) {
            account = null;
        } else {
            account = telecomManager.getPhoneAccount(accountHandle);
        }

        if (account == null) {
            return null;
        } else {
            return account.getIcon().loadDrawable(context);
        }
    }

    /**
     * Get PhoneAccount from component name and account id.
     */
    public static PhoneAccountHandle getAccount(String componentString, String accountId) {
        if (TextUtils.isEmpty(componentString) || TextUtils.isEmpty(accountId)) {
            return null;
        }
        final ComponentName componentName = ComponentName.unflattenFromString(componentString);
        return new PhoneAccountHandle(componentName, accountId);
    }

    /**
     * Returns the call types for the given number of items in the cursor.
     *
     * @param cursor The cursor of each group
     * @param count The count of each group
     * @return
     */
    public int[] getCallTypes(PhoneCallDetails details, Cursor cursor, int count) {
        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        for (int index = 0; index < count; ++index) {
            callTypes[index] = cursor.getInt(CallLogQueryHandler.CALL_TYPE);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }

    /**
     * @param details Provider data.
     * @param convertView Provider root view.
     */
    private void bindView(PhoneCallDetails details, View convertView) {

        DelCallLogViewHolder viewHolder = new DelCallLogViewHolder(convertView);

        if (!TextUtils.isEmpty(details.mName)) {
            viewHolder.mNameText.setText(details.mName);
        } else {
            viewHolder.mNameText.setText(details.mDisplayNumber);
        }

        bindPhotoView(details, viewHolder);

        // Clear CallTypeIcons data before add.
        viewHolder.mCallTypeIcons.clear();

        // Set call type icons.
        for (int callType = 0; callType < details.mCallTypes.length
                && callType < MAX_CALL_TYPE_COUNT; ++callType) {
            viewHolder.mCallTypeIcons.add(details.mCallTypes[callType]);
        }

        // Set video icon if exist.
        viewHolder.mCallTypeIcons
                .setShowVideo((details.mFeatures & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO);
        viewHolder.mCallTypeIcons.requestLayout();
        viewHolder.mCallTypeIcons.setVisibility(View.VISIBLE);

        // Set account icon
        if (details.mAccountIcon == null) {
            viewHolder.mCallAccountIcon.setVisibility(View.GONE);
        } else {
            viewHolder.mCallAccountIcon.setVisibility(View.VISIBLE);
            viewHolder.mCallAccountIcon.setImageDrawable(details.mAccountIcon);
        }

        ArrayList<CharSequence> callDescription = Lists.newArrayList();
        CharSequence callLocationOrLabel;
        if (TextUtils.isEmpty(details.mName)) {
            // Set geo location if it is not contact.
            callLocationOrLabel = details.mGeoLocation;
        } else {
            // Set number label if it is contact.
            callLocationOrLabel = Phone.getTypeLabel(mContext.getResources(),
                    details.mNumberType, details.mNumberLabel);
        }
        if (!TextUtils.isEmpty(callLocationOrLabel)) {
            // Join call geo location
            callDescription.add(callLocationOrLabel);
        }
        CharSequence formattedDate = getCallDate(details.mCallDate);
        // Join call date.
        callDescription.add(formattedDate);

        // Set display call count.
        CharSequence description;
        if (details.mCallTypes.length > MAX_CALL_TYPE_COUNT) {
            description = mContext.getResources().getString(
                    R.string.recent_call_count_or_location_and_date,
                    details.mCallTypes.length,
                    joinDescriptionList(mContext.getResources(), callDescription));
        } else {
            description = joinDescriptionList(mContext.getResources(), callDescription);
        }
        viewHolder.mCallLocationAndDateText.setText(description);

    }

    /**
     * @param details Provider data.
     * @param viewHolder Provider view.
     */
    private void bindPhotoView(PhoneCallDetails details, DelCallLogViewHolder viewHolder) {

        CheckableImageView photoView = viewHolder.mPhotoView;
        if (details.mPhotoId != 0) {
            mContactPhotoManager.loadThumbnail(photoView, details.mPhotoId, false, true, null);
        } else {
            final Uri photoUri = details.mPhotoUri == null ? null : details.mPhotoUri;
            DefaultImageRequest request = null;
            if (photoUri == null) {
                String displayName;
                if (TextUtils.isEmpty(details.mName)) {
                    displayName = details.mDisplayNumber;
                } else {
                    displayName = details.mName;
                }
                details.mLookupKey = UriUtils.getLookupKeyFromUri(details.mLookupUri);
                request =
                        new DefaultImageRequest(displayName, details.mLookupKey, true);
            }
            mContactPhotoManager.loadDirectoryPhoto(photoView, photoUri, false, true, request);
        }

        // The key is first call id in every group or stand alone view item.
        photoView.setChecked(
                mCheckListListener.onContainsKey(String.valueOf(details.mCallId)), false);

        // Activate photo when photo is check.
        if (photoView.isChecked()) {
            viewHolder.mView.setActivated(true);
            photoView.setOnClickListener(null);
            photoView.setClickable(false);
        } else {
            viewHolder.mView.setActivated(false);
            photoView.setOnClickListener(this);
        }
        photoView.setTag(details);
    }

    /**
     * Get formatted date.
     *
     * @param date Unformatted date
     * @return Formatted date.
     */
    public CharSequence getCallDate(long date) {
        return DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    /**
     * Join call location and date.
     *
     * @param resources
     * @param list Store location and date.
     * @return
     */
    public CharSequence joinDescriptionList(Resources resources, Iterable<CharSequence> list) {
        StringBuilder sb = new StringBuilder();
        final BidiFormatter formatter = BidiFormatter.getInstance();
        final CharSequence separator = resources.getString(R.string.description_list_delimiter);

        Iterator<CharSequence> itr = list.iterator();
        boolean firstTime = true;
        while (itr.hasNext()) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(separator);
            }
            // Unicode wrap the elements of the list to respect RTL for individual strings.
            sb.append(formatter.unicodeWrap(
                    itr.next().toString(), TextDirectionHeuristics.FIRSTSTRONG_LTR));
        }
        // Unicode wrap the joined value, to respect locale's RTL ordering for the whole list.
        return formatter.unicodeWrap(sb.toString());
    }

    @Override
    public void onClick(View v) {
        PhoneCallDetails details = (PhoneCallDetails) v.getTag();
        int clickId = v.getId();
        switch (clickId) {
            case R.id.photo_view:
                if (details != null && details.mLookupUri != null) {
                    QuickContact.showQuickContact(mContext, v, details.mLookupUri,
                            null, Phone.CONTENT_ITEM_TYPE);
                }
                break;
            default:
                throw new IllegalStateException("this click is valid");
        }
    }

    public void setCheckListListener(OnCheckListActionListener checkListActionListener) {
        mCheckListListener = checkListActionListener;
    }
}
