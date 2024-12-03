/*
 * SPDX-FileCopyrightText: 2012 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-FileCopyrightText: 2024 LibreMobileOS Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package com.libremobileos.stats;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.libremobileos.providers.LMOSettings;

public class ReportingServiceManager extends BroadcastReceiver {
    private static final long MILLIS_PER_HOUR = 60L * 60L * 1000L;
    private static final long MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR;
    private static final long UPDATE_INTERVAL = 1L * MILLIS_PER_DAY;

    private static final String TAG = ReportingServiceManager.class.getSimpleName();

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String ACTION_LAUNCH_SERVICE =
            "com.libremobileos.stats.action.TRIGGER_REPORT_METRICS";
    public static final String EXTRA_FORCE = "force";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (DEBUG)
            Log.d(TAG, "BOOT_COMPLETED received, checking stats collection setting...");
            if (Settings.Secure.getInt(context.getContentResolver(),
                    LMOSettings.Secure.STATS_COLLECTION, 1) == 1) {
                if (DEBUG)
                Log.d(TAG, "Stats collection enabled, starting services...");
                setAlarm(context);
                Intent serviceIntent = new Intent(context, ReportingService.class);
                context.startServiceAsUser(serviceIntent, UserHandle.SYSTEM);
            } else {
                if (DEBUG)
                Log.d(TAG, "Stats collection not enabled, services not started.");
            }
        } else if (intent.getAction().equals(ACTION_LAUNCH_SERVICE)){
            launchService(context, intent.getBooleanExtra(EXTRA_FORCE, false));
        }
    }

    public static void setAlarm(Context context) {
        SharedPreferences prefs = AnonymousStats.getPreferences(context);
        if (prefs.contains(AnonymousStats.ANONYMOUS_OPT_IN)) {
            migrate(context, prefs);
        }
        if (!Utilities.isStatsCollectionEnabled(context)) {
            return;
        }
        long lastSynced = prefs.getLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, 0);
        if (lastSynced == 0) {
            launchService(context, true); // service will reschedule the next alarm
            return;
        }
        long millisFromNow = (lastSynced + UPDATE_INTERVAL) - System.currentTimeMillis();

        Intent intent = new Intent(ACTION_LAUNCH_SERVICE);
        intent.setClass(context, ReportingServiceManager.class);

        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + millisFromNow,
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        Log.d(TAG, "Next sync attempt in : "
                + (millisFromNow / MILLIS_PER_HOUR) + " hours");
    }

    public static void launchService(Context context, boolean force) {
        SharedPreferences prefs = AnonymousStats.getPreferences(context);

        if (!Utilities.isStatsCollectionEnabled(context)) {
            return;
        }

        if (!force) {
            long lastSynced = prefs.getLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, 0);
            if (lastSynced == 0) {
                setAlarm(context);
                return;
            }
            long timeElapsed = System.currentTimeMillis() - lastSynced;
            if (timeElapsed < UPDATE_INTERVAL) {
                long timeLeft = UPDATE_INTERVAL - timeElapsed;
                Log.d(TAG, "Waiting for next sync : "
                        + timeLeft / MILLIS_PER_HOUR + " hours");
                return;
            }
        }

        Intent intent = new Intent();
        intent.setClass(context, ReportingService.class);
        context.startServiceAsUser(intent, UserHandle.SYSTEM);
    }

    private static void migrate(Context context, SharedPreferences prefs) {
        Utilities.setStatsCollectionEnabled(context,
                prefs.getBoolean(AnonymousStats.ANONYMOUS_OPT_IN, true));
        prefs.edit().remove(AnonymousStats.ANONYMOUS_OPT_IN).commit();
    }
}
