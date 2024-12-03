/*
 * SPDX-FileCopyrightText: 2012 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-FileCopyrightText: 2024 LibreMobileOS Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package com.libremobileos.stats;

import android.content.Context;
import android.os.Bundle;
import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.preference.PreferenceScreen;
import com.libremobileos.support.preferences.SecureSettingMainSwitchPreference;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class StatsFragment extends PreferenceFragmentCompat {

    private static final String UNIQUE_ID = "preview_id";
    private static final String DEVICE = "preview_device";
    private static final String VERSION = "preview_version";
    private static final String COUNTRY = "preview_country";
    private static final String CARRIER = "preview_carrier";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.stats, rootKey);

        final PreferenceScreen prefSet = getPreferenceScreen();
        final Context context = requireActivity();
        prefSet.findPreference(UNIQUE_ID).setSummary(Utilities.getUniqueID(context));
        prefSet.findPreference(DEVICE).setSummary(Utilities.getDevice());
        prefSet.findPreference(VERSION).setSummary(Utilities.getModVersion());
        prefSet.findPreference(COUNTRY).setSummary(Utilities.getCountryCode(context));
        prefSet.findPreference(CARRIER).setSummary(Utilities.getCarrier(context));

        final SecureSettingMainSwitchPreference statsCollectionPref =
            (SecureSettingMainSwitchPreference) prefSet.findPreference("stats_collection");
        statsCollectionPref.setOnPreferenceChangeListener(
            new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (!(Boolean) newValue) {
                    new AlertDialog.Builder(context)
                        .setTitle(R.string.disable_stats_warning_title)
                        .setMessage(R.string.disable_stats_warning)
                        .setPositiveButton(R.string.disable, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                statsCollectionPref.setChecked(false);
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                statsCollectionPref.setChecked(true);
                                dialog.dismiss();
                            }
                        })
                        .show();
                    return false;
                }
                return true;
            }
        });
    }
}
