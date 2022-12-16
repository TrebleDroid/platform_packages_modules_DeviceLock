/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.devicelockcontroller.setup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.ArraySet;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.collect.ImmutableList;

import java.util.Set;

/**
 * Store provisioning parameters
 */
public final class SetupParameters {
    private static final String TAG = "SetupParameters";

    private static final String FILENAME = "setup-prefs";
    private static final String KEY_KIOSK_PACKAGE = "kiosk-package-name";
    private static final String KEY_KIOSK_DOWNLOAD_URL = "kiosk-download-url";
    private static final String KEY_KIOSK_SIGNATURE_CHECKSUM = "kiosk-signature-checksum";
    private static final String KEY_KIOSK_SETUP_ACTIVITY = "kiosk-setup-activity";
    private static final String KEY_KIOSK_ALLOWLIST = "kiosk-allowlist";
    private static final String KEY_KIOSK_DISABLE_OUTGOING_CALLS =
            "kiosk-disable-outgoing-calls";
    private static final String KEY_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE =
            "kiosk-enable-notifications-in-lock-task-mode";

    public static final String EXTRA_KIOSK_PACKAGE =
            "com.android.devicelockcontroller.KIOSK_PACKAGE";

    /**
     * URL to download the kiosk app.
     *
     * <p>DLC will look for a pre-installed package with the name defined by {@link
     * #EXTRA_KIOSK_PACKAGE}. If the package is not present, DLC will try to download the package
     * from the URL provided.
     */
    public static final String EXTRA_KIOSK_DOWNLOAD_URL =
            "com.android.devicelockcontroller.KIOSK_DOWNLOAD_URL";

    /**
     * Intent's extras key for Base64 encoded SHA-256 hash checksum of the kiosk app's signing
     * certificate.
     */
    public static final String EXTRA_KIOSK_SIGNATURE_CHECKSUM =
            "com.android.devicelockcontroller.KIOSK_SIGNATURE_CHECKSUM";

    public static final String EXTRA_KIOSK_SETUP_ACTIVITY =
            "com.android.devicelockcontroller.KIOSK_SETUP_ACTIVITY";
    public static final String EXTRA_KIOSK_NAME =
            "com.android.devicelockcontroller.KIOSK_NAME";
    public static final String EXTRA_KIOSK_DISABLE_OUTGOING_CALLS =
            "com.android.devicelockcontroller.KIOSK_DISABLE_OUTGOING_CALLS";
    public static final String EXTRA_KIOSK_READ_IMEI_ALLOWED =
            "com.android.devicelockcontroller.KIOSK_READ_IMEI_ALLOWED";
    /**
     * Used to control if notifications are enabled in lock task mode. The default value is false.
     *
     * @see android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_NOTIFICATIONS
     */
    public static final String EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE =
            "com.android.devicelockcontroller.KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE";
    public static final String EXTRA_KIOSK_ALLOWLIST =
            "com.android.devicelockcontroller.KIOSK_ALLOWLIST";
    private SetupParameters() {}

    private static SharedPreferences getSharedPreferences(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        return deviceContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }

    /**
     * Parse setup parameters from the extras bundle.
     *
     * @param context Application context
     * @param bundle Bundle with provisioning parameters.
     */
    public static synchronized void createPrefs(Context context, Bundle bundle) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        if (sharedPreferences.contains(KEY_KIOSK_PACKAGE)) {
            LogUtil.i(TAG, "Setup parameters are already populated");

            return;
        }

        populatePreferencesLocked(sharedPreferences, bundle);
    }

    private static void populatePreferencesLocked(SharedPreferences sharedPreferences,
            Bundle bundle) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_KIOSK_PACKAGE, bundle.getString(EXTRA_KIOSK_PACKAGE));
        editor.putString(KEY_KIOSK_DOWNLOAD_URL, bundle.getString(EXTRA_KIOSK_DOWNLOAD_URL));
        editor.putString(KEY_KIOSK_SIGNATURE_CHECKSUM,
                bundle.getString(EXTRA_KIOSK_SIGNATURE_CHECKSUM));
        editor.putString(KEY_KIOSK_SETUP_ACTIVITY, bundle.getString(EXTRA_KIOSK_SETUP_ACTIVITY));
        editor.putBoolean(KEY_KIOSK_DISABLE_OUTGOING_CALLS,
                bundle.getBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS));
        editor.putBoolean(KEY_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE,
                bundle.getBoolean(EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE));
        editor.putStringSet(KEY_KIOSK_ALLOWLIST,
                new ArraySet<>(bundle.getStringArrayList(EXTRA_KIOSK_ALLOWLIST)));
        editor.apply();
    }

    /**
     * Get the name of the package implementing the kiosk app.
     *
     * @param context Context used to get the shared preferences.
     * @return kiosk app package name.
     */
    @Nullable
    public static String getKioskPackage(Context context) {
        return getSharedPreferences(context).getString(KEY_KIOSK_PACKAGE, null /* defValue */);
    }

    /**
     * Get the kiosk app download URL.
     *
     * @param context Context used to get the shared preferences.
     * @return Kiosk app download URL.
     */
    @Nullable
    public static String getKioskDownloadUrl(Context context) {
        return getSharedPreferences(context).getString(KEY_KIOSK_DOWNLOAD_URL, null /* defValue */);
    }

    /**
     * Get the kiosk app signature checksum.
     *
     * @param context Context used to get the shared preferences.
     * @return Signature checksum.
     */
    @Nullable
    public static String getKioskSignatureChecksum(Context context) {
        return getSharedPreferences(context)
                .getString(KEY_KIOSK_SIGNATURE_CHECKSUM, null /* defValue */);
    }

    /**
     * Get the setup activity for the kiosk app.
     *
     * @param context Context used to get the shared preferences.
     * @return Setup activity.
     */
    @Nullable
    public static String getKioskSetupActivity(Context context) {
        return getSharedPreferences(context)
                .getString(KEY_KIOSK_SETUP_ACTIVITY, null /* defValue */);
    }

    /**
     * Check if the configuration disables outgoing calls.
     *
     * @param context Context used to get the shared preferences.
     * @return True if outgoign calls are disabled.
     */
    public static boolean getOutgoingCallsDisabled(Context context) {
        return getSharedPreferences(context)
                .getBoolean(KEY_KIOSK_DISABLE_OUTGOING_CALLS, false /* defValue */);
    }

    /**
     * Get package allowlist provisioned by the server.
     *
     * @param context Context used to get the shared preferences.
     * @return List of allowed packages.
     */
    public static ImmutableList<String> getKioskAllowlist(Context context) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        Set<String> allowlistSet =
                sharedPreferences.getStringSet(KEY_KIOSK_ALLOWLIST, null /* defValue */);
        return allowlistSet == null ? ImmutableList.of() : ImmutableList.copyOf(allowlistSet);
    }

    /**
     * Check if notifications are enabled in lock task mode.
     *
     * @param context Context used to get the shared preferences.
     * @return True if notification are enabled.
     */
    public static boolean isNotificationsInLockTaskModeEnabled(Context context) {
        return getSharedPreferences(context)
                .getBoolean(KEY_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE, false /* defValue */);
    }
}
