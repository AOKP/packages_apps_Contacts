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

import java.util.List;

public interface OnCheckListActionListener {

    /**
     * judge mChoiceSet contain key
     *
     * @key represent data id, contact id
     */
    boolean onContainsKey(String key);

    /**
     * judge mChoiceNumberSet contain key
     *
     * @key represent call log id
     */
    boolean onContainsNumberKey(String key);

    /**
     * put value to mChoiceSet
     */
    void putValue(String key, String[] value);

    /**
     * remove value from mChoiceSet
     */
    void onRemove(String key);

    /**
     * put value to mChoiceNumberSet, only for call log value
     */
    void putNumberValue(String key, String[] value);

    /**
     * remove value from mChoiceNumberSet, only for call log value
     *
     * @key represent call log id
     */
    void onNumberRemove(String key);

    /**
     * pick phone mode, need to add groupId to mGroupSelected when selected
     *
     * @groupId represent selected group id
     */
    void addGroupId(long groupId);

    /**
     * judge mGroupSelected contain groupId
     */
    boolean onContainsGroupId(long groupId);

    /**
     * remove selected group id
     */
    void onRemoveGroupId(long groupId);

    /**
     * get all selected group ids
     */
    List<Long> getGroupsList();

    /**
     * clear all selected groups ids
     */
    void onGroupClear();

    /**
     * clear mChoiceSet
     */
    void onClear();

    /**
     * hide softkeyboard
     */
    void onHideSoftKeyboard();

    /**
     * update action bar
     */
    void onUpdateActionBar();

    /**
     * exit search mode
     */
    void exitSearch();

    /**
     * add strange call log id
     */
    void appendStrangeCallLogId(String callLogid);

    /**
    * get the ids queried by calllogfragment, then can show call log datas on searchfragment
    */
    String getCallLogSelection();
}
