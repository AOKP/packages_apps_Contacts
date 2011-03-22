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
import 	android.preference.PreferenceScreen;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.Intent;
import java.util.List;
import java.util.ArrayList;
import android.content.pm.ResolveInfo;

import android.util.Log;

public class ContactsPreferences extends PreferenceActivity implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "ContactsPreferences";

    private static final String VM_BUTTON = "vm_button";
    private static final String VM_HANDLER = "vm_handler";
    private static final String PRESSED_DIGIT_COLOR = "pressed_digits_color";
    private static final String FOCUSED_DIGIT_COLOR = "focused_digits_color";
    private static final String UNSELECTED_DIGIT_COLOR = "unselected_digits_color";
    private static final String DEFAULT_PHONE_TAB = "misc_default_phone_tab";

    private ListPreference mVMButton;
    private ListPreference mVMHandler;
    private ListPreference mDefaultPhoneTab;
    private Preference colorFocused, colorPressed, colorUnselected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.contacts_preferences);

        mVMButton = (ListPreference) findPreference(VM_BUTTON);
        mVMHandler = (ListPreference) findPreference(VM_HANDLER);
        mDefaultPhoneTab = (ListPreference) findPreference(DEFAULT_PHONE_TAB);
        colorPressed = (Preference) findPreference(PRESSED_DIGIT_COLOR);
        colorFocused = (Preference) findPreference(FOCUSED_DIGIT_COLOR);
        colorUnselected = (Preference) findPreference(UNSELECTED_DIGIT_COLOR);

        mVMButton.setOnPreferenceChangeListener(this);
        mVMHandler.setOnPreferenceChangeListener(this);
        mDefaultPhoneTab.setOnPreferenceChangeListener(this);
        loadHandlers();

        updatePrefs(mVMButton, mVMButton.getValue());
        updatePrefs(mVMHandler, mVMHandler.getValue());
        updatePrefs(mDefaultPhoneTab, mDefaultPhoneTab.getValue());
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
            if (p.getKey().equals(VM_BUTTON) || p.getKey().equals(VM_HANDLER) || p.getKey().equals(DEFAULT_PHONE_TAB)) {
                p.setValue("0");
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
        
        entries.add(getResources().getString(R.string.entry_no_vm_handler));
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
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == colorPressed) {
            ColorPickerDialog cp = new ColorPickerDialog(this,
                mColorPressedListener,
                readColorPressed());
            cp.show();
            return true;
        }
        else if (preference == colorFocused) {
            ColorPickerDialog cp = new ColorPickerDialog(this,
                mColorFocusedListener,
                readColorFocused());
            cp.show();
            return true;           
        }
        else if (preference == colorUnselected) {
            ColorPickerDialog cp = new ColorPickerDialog(this,
                mColorUnselectedListener,
                readColorUnselected());
            cp.show();
            return true;
        }
        
        return false;
    }
    
    ColorPickerDialog.OnColorChangedListener mColorPressedListener = 
        new ColorPickerDialog.OnColorChangedListener() {
            public void colorChanged(int color) {           
                SharedPreferences.Editor editor = colorPressed.getEditor();
				editor.putInt(PRESSED_DIGIT_COLOR, color);
				editor.commit();
            }
    };
    
    private int readColorPressed() {
        SharedPreferences mPrefs = colorPressed.getSharedPreferences();
		return mPrefs.getInt(PRESSED_DIGIT_COLOR, -16777216);
    }
    
    ColorPickerDialog.OnColorChangedListener mColorFocusedListener = 
        new ColorPickerDialog.OnColorChangedListener() {
            public void colorChanged(int color) {
                SharedPreferences.Editor editor = colorPressed.getEditor();
				editor.putInt(FOCUSED_DIGIT_COLOR, color);
				editor.commit();
            }
    };
    
    private int readColorFocused() {
        SharedPreferences mPrefs = colorPressed.getSharedPreferences();
		return mPrefs.getInt(FOCUSED_DIGIT_COLOR, -1);
    }
    
    ColorPickerDialog.OnColorChangedListener mColorUnselectedListener = 
        new ColorPickerDialog.OnColorChangedListener() {
            public void colorChanged(int color) {
                SharedPreferences.Editor editor = colorPressed.getEditor();
				editor.putInt(UNSELECTED_DIGIT_COLOR, color);
				editor.commit();
            }
    };
    
    private int readColorUnselected() {
        SharedPreferences mPrefs = colorPressed.getSharedPreferences();
		return mPrefs.getInt(UNSELECTED_DIGIT_COLOR, -1);
    }    
    
}
