/*
 * SPDX-FileCopyrightText: 2012 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-FileCopyrightText: 2024-2025 LibreMobileOS Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package com.libremobileos.stats;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.PersistableBundle;
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
                scheduleJob(context);
            } else {
                if (DEBUG)
                Log.d(TAG, "Stats collection not enabled, services not started.");
            }
        } else if (intent.getAction().equals(ACTION_LAUNCH_SERVICE)){
            launchService(context, intent.getBooleanExtra(EXTRA_FORCE, false));
        }
    }

    public static void scheduleJob(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);

        String deviceId = Utilities.getUniqueID(context);
        String deviceName = Utilities.getDevice();
        String deviceVersion = Utilities.getModVersion();
        String deviceCountry = Utilities.getCountryCode(context);
        String deviceCarrier = Utilities.getCarrier(context);
        String deviceCarrierId = Utilities.getCarrierId(context);

        final int libremobileosOldJobId = AnonymousStats.getLastJobId(context);
        final int libremobileosComJobId = AnonymousStats.getNextJobId(context);

        if (DEBUG) Log.d(TAG, "scheduling job id: " + libremobileosComJobId);

        PersistableBundle libremobileosBundle = new PersistableBundle();
        libremobileosBundle.putString(StatsUploadJobService.KEY_DEVICE_NAME, deviceName);
        libremobileosBundle.putString(StatsUploadJobService.KEY_UNIQUE_ID, deviceId);
        libremobileosBundle.putString(StatsUploadJobService.KEY_VERSION, deviceVersion);
        libremobileosBundle.putString(StatsUploadJobService.KEY_COUNTRY, deviceCountry);
        libremobileosBundle.putString(StatsUploadJobService.KEY_CARRIER, deviceCarrier);
        libremobileosBundle.putString(StatsUploadJobService.KEY_CARRIER_ID, deviceCarrierId);
        libremobileosBundle.putLong(
            StatsUploadJobService.KEY_TIMESTAMP, System.currentTimeMillis());

        // set job types
        libremobileosBundle.putInt(StatsUploadJobService.KEY_JOB_TYPE,
                StatsUploadJobService.JOB_TYPE_LIBREMOBILEOSCOM);

        // schedule libremobileos stats upload
        js.schedule(new JobInfo.Builder(libremobileosComJobId, new ComponentName(context.getPackageName(),
                StatsUploadJobService.class.getName()))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1000)
                .setExtras(libremobileosBundle)
                .setPersisted(true)
                .build());

        // cancel old job in case it didn't run yet
        js.cancel(libremobileosOldJobId);

        // reschedule
        AnonymousStats.updateLastSynced(context);
        setAlarm(context);
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

        scheduleJob(context);
    }

    private static void migrate(Context context, SharedPreferences prefs) {
        Utilities.setStatsCollectionEnabled(context,
                prefs.getBoolean(AnonymousStats.ANONYMOUS_OPT_IN, true));
        prefs.edit().remove(AnonymousStats.ANONYMOUS_OPT_IN).commit();
    }
}
