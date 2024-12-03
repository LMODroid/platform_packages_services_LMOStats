/*t
 * SPDX-FileCopyrightText: 2024 LibreMobileOS Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package com.libremobileos.stats;

import android.os.Bundle;
import android.content.Intent;
import android.util.Log;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.collapsingtoolbar.SettingsTransitionActivity;

public class StatsActivity extends CollapsingToolbarBaseActivity {
    private static final String TAG = StatsUploadJobService.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new StatsFragment())
                    .commit();
        }

        // Start ReportingService
        Intent intent = new Intent(this, ReportingService.class);
        startService(intent);
        if (DEBUG)
        Log.d(TAG, "ReportingService started");
    }
}
