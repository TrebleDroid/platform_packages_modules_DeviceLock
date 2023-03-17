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

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ALLOWLIST;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_APP_PROVIDER_NAME;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DOWNLOAD_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_SETUP_ACTIVITY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_SIGNATURE_CHECKSUM;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_MANDATORY_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_PROVISIONING_TYPE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.TYPE_UNDEFINED;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.ArraySet;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;
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
    private static final String KEY_PROVISIONING_TYPE = "provisioning-type";
    private static final String KEY_MANDATORY_PROVISION = "mandatory-provision";
    private static final String KEY_KIOSK_APP_PROVIDER_NAME = "kiosk-app-provider-name";
    private static final String KEY_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES =
            "disallow-installing-from-unknown-sources";

    private SetupParameters() {
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        return deviceContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }

    /**
     * Parse setup parameters from the extras bundle.
     *
     * @param context Application context
     * @param bundle  Bundle with provisioning parameters.
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
        editor.putInt(KEY_PROVISIONING_TYPE, bundle.getInt(EXTRA_PROVISIONING_TYPE));
        editor.putBoolean(KEY_MANDATORY_PROVISION, bundle.getBoolean(EXTRA_MANDATORY_PROVISION));
        editor.putString(KEY_KIOSK_APP_PROVIDER_NAME,
                bundle.getString(EXTRA_KIOSK_APP_PROVIDER_NAME));
        editor.putBoolean(KEY_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES,
                bundle.getBoolean(EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES));
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
     * @return True if outgoing calls are disabled.
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

    /**
     * Get the provisioning type of this configuration.
     *
     * @param context Context used to get the shared preferences.
     * @return The type of provisioning which could be one of {@link ProvisioningType}.
     */
    @ProvisioningType
    public static int getProvisioningType(Context context) {
        return getSharedPreferences(context).getInt(KEY_PROVISIONING_TYPE, TYPE_UNDEFINED);
    }

    /**
     * Check if provision is mandatory.
     *
     * @param context Context used to get the shared preferences.
     * @return True if the provision should be mandatory.
     */
    public static boolean isProvisionMandatory(Context context) {
        return getSharedPreferences(context).getBoolean(KEY_MANDATORY_PROVISION, false);
    }

    /**
     * Get the name of the provider of the kiosk app.
     *
     * @param context Context used to get the shared preferences.
     * @return the name of the provider.
     */
    @Nullable
    public static String getKioskAppProviderName(Context context) {
        return getSharedPreferences(context).getString(KEY_KIOSK_APP_PROVIDER_NAME,
                null /* defValue */);
    }

    /**
     * Check if installing from unknown sources should be disallowed on this device after provision
     *
     * @param context Context used to get the shared preferences.
     * @return True if installing from unknown sources is disallowed.
     */
    public static Boolean isInstallingFromUnknownSourcesDisallowed(Context context) {
        return getSharedPreferences(context).getBoolean(
                KEY_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES, /* defValue= */ false);
    }
}
