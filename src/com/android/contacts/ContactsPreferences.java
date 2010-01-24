/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.ListPreference;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.Intent;
import java.util.List;
import java.util.ArrayList;
import android.content.pm.ResolveInfo;

import android.util.Log;

public class ContactsPreferences extends PreferenceActivity implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "ContactsPreferences";

    private ListPreference mVMButton;
    private ListPreference mVMHandler;
    private ListPreference colorFocused, colorPressed, colorUnselected;
    private CheckBoxPreference useCustomColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.contacts_preferences);

        mVMButton = (ListPreference) findPreference("vm_button");
        mVMHandler = (ListPreference) findPreference("vm_handler");       
        colorFocused = (ListPreference) findPreference("focused_digit_color");
        colorPressed = (ListPreference) findPreference("pressed_digit_color");
        colorUnselected = (ListPreference) findPreference("unselected_digit_color");
        useCustomColor = (CheckBoxPreference) findPreference("dial_digit_use_custom_color");

        mVMButton.setOnPreferenceChangeListener(this);
        mVMHandler.setOnPreferenceChangeListener(this);
        colorFocused.setOnPreferenceChangeListener(this);
        colorPressed.setOnPreferenceChangeListener(this);
        colorUnselected.setOnPreferenceChangeListener(this);
        useCustomColor.setOnPreferenceClickListener(this);

        loadHandlers();

        updatePrefs(mVMButton, mVMButton.getValue());
        updatePrefs(mVMHandler, mVMHandler.getValue());
        updatePrefs(colorFocused, colorFocused.getValue());
        updatePrefs(colorPressed, colorPressed.getValue());
        updatePrefs(colorUnselected, colorUnselected.getValue());        
        updatePrefs(useCustomColor);
    }
    
    public boolean onPreferenceClick(Preference preference) {
        updatePrefs(preference);
        
        return true;
    }
    
    private void updatePrefs(Preference preference) {
        if (preference.getKey().equals("dial_digit_use_custom_color")) {
            CheckBoxPreference p = (CheckBoxPreference) findPreference(preference.getKey());
            if (p.isChecked()) {
                colorFocused.setEnabled(false);
                colorPressed.setEnabled(false);
                colorUnselected.setEnabled(false);
            }
            else {
                colorFocused.setEnabled(true);
                colorPressed.setEnabled(true);
                colorUnselected.setEnabled(true);
            }            
        }
    }
    
    public boolean onPreferenceChange (Preference preference, Object newValue) {
        updatePrefs(preference, newValue);
        return true;
    }

    private void updatePrefs(Preference preference, Object newValue) {  
        ListPreference p = (ListPreference) findPreference(preference.getKey());
        
        try {       
            p.setSummary(p.getEntries()[p.findIndexOfValue((String) newValue)]);
        } catch (ArrayIndexOutOfBoundsException e) {
            if (p.getKey().equals("vm_button") || p.getKey().equals("vm_handler")) {
                p.setValue("0");
            }
            else if (p.getKey().equals("focused_digit_color") || p.getKey().equals("pressed_digit_color")) {
                p.setValue("-16777216");
            }
            else if (p.getKey().equals("unselected_digit_color")) {
                p.setValue("-1");
            }
            updatePrefs(p, p.getValue());
        }
    }

    private void loadHandlers () {
        final PackageManager packageManager = getPackageManager();
        String[] vmHandlers = getResources().getStringArray(R.array.vm_handlers);
        Intent intent;
        ComponentName component;
        List<String> entries = new ArrayList<String>();
        List<String> entryValues = new ArrayList<String>();
        
        entries.add("None (Dial Voicemail Number)");
        entryValues.add("0");

        for (String s : vmHandlers) {
            String [] cmp = s.split("/");
            intent = new Intent(Intent.ACTION_MAIN);
            component = new ComponentName(cmp[1], cmp[1] + cmp[2]);
            intent.setComponent(component);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            List<ResolveInfo> list = 
                packageManager.queryIntentActivities(intent,
                            PackageManager.MATCH_DEFAULT_ONLY);
            if (list.size() > 0) {
                entries.add(cmp[0]);
                entryValues.add(cmp[1] + "/" + cmp[2]);
            }
        }
        String[] entriesArray = entries.toArray(new String[0]);
        mVMHandler.setEntries(entriesArray);
        String[] entryValuesArray = entryValues.toArray(new String[0]);
        mVMHandler.setEntryValues(entryValuesArray);
    }
}
