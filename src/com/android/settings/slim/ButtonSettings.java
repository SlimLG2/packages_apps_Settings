/*
 * Copyright (C) 2012 Slimroms
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

package com.android.settings.slim;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.R;

import android.widget.Toast;

import org.cyanogenmod.hardware.KeyDisabler;

public class ButtonSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String TAG = "ButtonsSettings";
    private static final String DISABLE_HARDWARE_BUTTONS = "disable_hardware_button";
    private static final String KEY_BUTTON_BACKLIGHT = "button_backlight";
    private static final String ENABLE_NAVIGATION_BAR = "enable_nav_bar";
    private static final String KEYS_OVERFLOW_BUTTON = "keys_overflow_button";
    private static final String KEY_BLUETOOTH_INPUT_SETTINGS = "bluetooth_input_settings";

    private SwitchPreference mDisableHardwareButtons;
    private ButtonBacklightBrightness mBacklight;
    private SwitchPreference mEnableNavigationBar;
    private ListPreference mOverflowButtonMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.button_settings);

        PreferenceScreen prefs = getPreferenceScreen();

        mDisableHardwareButtons = (SwitchPreference) prefs.findPreference(DISABLE_HARDWARE_BUTTONS);
        if (isKeyDisablerSupported()) {
            mDisableHardwareButtons.setOnPreferenceChangeListener(this);
        } else {
            prefs.removePreference(mDisableHardwareButtons);
        }

        mBacklight = (ButtonBacklightBrightness) prefs.findPreference(KEY_BUTTON_BACKLIGHT);
        if (!mBacklight.isButtonSupported() && !mBacklight.isKeyboardSupported()) {
            prefs.removePreference(mBacklight);
        }

        mOverflowButtonMode = (ListPreference) prefs.findPreference(KEYS_OVERFLOW_BUTTON);
        mOverflowButtonMode.setOnPreferenceChangeListener(this);

        mEnableNavigationBar = (SwitchPreference) prefs.findPreference(ENABLE_NAVIGATION_BAR);
        mEnableNavigationBar.setOnPreferenceChangeListener(this);

        Utils.updatePreferenceToSpecificActivityFromMetaDataOrRemove(getActivity(),
                getPreferenceScreen(), KEY_BLUETOOTH_INPUT_SETTINGS);

        updatePreferences();
    }

    private void updatePreferences() {
        final boolean navbarIsDefault = getResources().getBoolean(
            com.android.internal.R.bool.config_showNavigationBar);
        final String hwkeysProp = SystemProperties.get("qemu.hw.mainkeys");
        final boolean navBarOverride = (hwkeysProp.equals("0") || hwkeysProp.equals("1"));

        // KeyDisabler
        if (isKeyDisablerSupported()) {
            boolean isHWKeysDisabled = KeyDisabler.isActive();
            mDisableHardwareButtons.setChecked(isHWKeysDisabled);
            if (mBacklight != null) {
                mBacklight.setEnabled(isHWKeysDisabled ? false : true);
            }
            mOverflowButtonMode.setEnabled(isHWKeysDisabled ? false : true);
            if (navBarOverride) {
                mEnableNavigationBar.setEnabled(false);
                mEnableNavigationBar.setSummary(R.string.navbar_summary_override);
            } else {
                mEnableNavigationBar.setEnabled(isHWKeysDisabled ? false : true);
                mEnableNavigationBar.setSummary("");
            }
        } else if (navbarIsDefault || navBarOverride) {
            mOverflowButtonMode.setEnabled(false);
            mEnableNavigationBar.setEnabled(false);
            mEnableNavigationBar.setSummary(R.string.navbar_summary_override);
        }

        // Backlight
        if (mBacklight != null) {
            mBacklight.updateSummary();
        }

        // Overflow
        String overflowButtonMode = Integer.toString(Settings.System.getInt(getContentResolver(),
                Settings.System.UI_OVERFLOW_BUTTON, 2));
        mOverflowButtonMode.setValue(overflowButtonMode);
        mOverflowButtonMode.setSummary(mOverflowButtonMode.getEntry());

        // NavBar
        boolean enableNavigationBar = Settings.System.getInt(getContentResolver(),
            Settings.System.NAVIGATION_BAR_SHOW, navbarIsDefault ? 1 : 0) == 1;
        mEnableNavigationBar.setChecked(enableNavigationBar);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mDisableHardwareButtons) {
            boolean value = (Boolean) newValue;

            // Disable hw keys on kernel level
            KeyDisabler.setActive(value);

            // Disable backlight
            int defaultBrightness = getResources().getInteger(
                com.android.internal.R.integer.config_buttonBrightnessSettingDefault);
            int brightness = value ? 0 : defaultBrightness;
            Settings.System.putInt(getContentResolver(), Settings.System.BUTTON_BRIGHTNESS, brightness);

            // Enable overflow button
            Settings.System.putInt(getContentResolver(), Settings.System.UI_OVERFLOW_BUTTON, 2);
            mOverflowButtonMode.setSummary(mOverflowButtonMode.getEntries()[2]);

            // Enable NavBar
            final String hwkeysProp = SystemProperties.get("qemu.hw.mainkeys");
            final boolean navBarOverride = (hwkeysProp.equals("0") || hwkeysProp.equals("1"));
            if (!navBarOverride) {
                Settings.System.putInt(getContentResolver(), Settings.System.NAVIGATION_BAR_SHOW, 1);
            }

            // Update preferences
            updatePreferences();

            return true;
        } else if (preference == mEnableNavigationBar) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NAVIGATION_BAR_SHOW,
                    ((Boolean) newValue) ? 1 : 0);
            return true;
        } else if (preference == mOverflowButtonMode) {
            int val = Integer.parseInt((String) newValue);
            int index = mOverflowButtonMode.findIndexOfValue((String) newValue);
            Settings.System.putInt(getContentResolver(), Settings.System.UI_OVERFLOW_BUTTON, val);
            mOverflowButtonMode.setSummary(mOverflowButtonMode.getEntries()[index]);
            Toast.makeText(getActivity(), R.string.keys_overflow_toast, Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    private static boolean isKeyDisablerSupported() {
        try {
            return KeyDisabler.isSupported();
        } catch (NoClassDefFoundError e) {
            // Hardware abstraction framework not installed
            return false;
        }
    }

    public static void restore(Context context) {
        if (isKeyDisablerSupported()) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean enabled = prefs.getBoolean(DISABLE_HARDWARE_BUTTONS, false);
            KeyDisabler.setActive(enabled);
        }
    }

}