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
import android.os.SystemProperties;
import android.util.ArraySet;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;

import java.util.ArrayList;
import java.util.Set;

/**
 * Class for local storage.
 */
public final class UserPreferences {
    private static final String FILENAME = "prefs";
    private static final String KEY_DEVICE_STATE = "device_state";
    private static final String KEY_KIOSK_SIGNING_CERT = "kiosk_signing_cert";
    private static final String KEY_HOME_PACKAGE_OVERRIDE = "home_override_package";
    private static final String KEY_LOCK_TASK_ALLOWLIST = "lock_task_allowlist";
    private static final String KEY_NEED_CHECK_IN = "need_check_in";
    static final String KEY_REGISTERED_DEVICE_ID = "registered_device_id";
    private static final String KEY_FORCED_PROVISION = "forced_provision";
    public static final String KEY_ENROLLMENT_TOKEN = "enrollment_token";


    private UserPreferences() {
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        final Context deviceContext = context.createDeviceProtectedStorageContext();

        return deviceContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }

    /**
     * Gets the current device state.
     *
     * @param context Context used to get the shared preferences.
     * @return the current device state.
     */
    @DeviceState
    public static int getDeviceState(Context context) {
        return getSharedPreferences(context).getInt(KEY_DEVICE_STATE, DeviceState.UNPROVISIONED);
    }

    /**
     * Get the kiosk app signature.
     *
     * @param context Context used to get the shared preferences.
     * @return the kiosk app signature.
     */
    @Nullable
    public static String getKioskSignature(Context context) {
        return getSharedPreferences(context).getString(KEY_KIOSK_SIGNING_CERT, null);
    }

    /**
     * Sets the current device state.
     *
     * @param context Context used to get the shared preferences.
     * @param state   New state.
     */
    public static void setDeviceState(Context context, @DeviceState int state) {
        getSharedPreferences(context).edit().putInt(KEY_DEVICE_STATE, state).apply();
    }

    /**
     * Sets the kiosk app signature.
     *
     * @param context   Context used to get the shared preferences.
     * @param signature Kiosk app signature.
     */
    public static void setKioskSignature(Context context, String signature) {
        getSharedPreferences(context).edit().putString(KEY_KIOSK_SIGNING_CERT, signature).apply();
    }

    /**
     * Gets the name of the package overriding home.
     *
     * @param context Context used to get the shared preferences.
     * @return Package overriding home.
     */
    @Nullable
    public static String getPackageOverridingHome(Context context) {
        return getSharedPreferences(context).getString(KEY_HOME_PACKAGE_OVERRIDE, null);
    }

    /**
     * Sets the name of the package overriding home.
     *
     * @param context     Context used to get the shared preferences.
     * @param packageName Package overriding home.
     */
    public static void setPackageOverridingHome(Context context, @Nullable String packageName) {
        getSharedPreferences(context).edit().putString(KEY_HOME_PACKAGE_OVERRIDE, packageName)
                .apply();
    }

    /**
     * Gets the list of packages allowlisted in lock task mode.
     *
     * @param context Context used to get the shared preferences.
     * @return List of packages that are allowed in lock task mode.
     */
    public static ArrayList<String> getLockTaskAllowlist(Context context) {
        final ArrayList<String> allowlistArray = new ArrayList<>();
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        final Set<String> allowlist =
                sharedPreferences.getStringSet(KEY_LOCK_TASK_ALLOWLIST, /* defValue= */ null);
        if (allowlist != null) {
            allowlistArray.addAll(allowlist);
        }

        return allowlistArray;
    }

    /**
     * Sets the list of packages allowlisted in lock task mode.
     *
     * @param context   Context used to get the shared preferences.
     * @param allowlist List of packages that are allowed in lock task mode.
     */
    public static void setLockTaskAllowlist(Context context, ArrayList<String> allowlist) {
        final Set<String> allowlistSet = new ArraySet<>(allowlist);

        getSharedPreferences(context)
                .edit()
                .putStringSet(KEY_LOCK_TASK_ALLOWLIST, allowlistSet)
                .apply();
    }

    /**
     * Checks if a check-in request needs to be performed.
     *
     * @param context Context used to get the shared preferences.
     * @return true if check-in request needs to be performed.
     */
    public static boolean needCheckIn(Context context) {
        // TODO(b/257092561): Remove the flag before release
        return SystemProperties.getBoolean("devicelock.checkin.enabled", false)
                && getSharedPreferences(context)
                        .getBoolean(KEY_NEED_CHECK_IN, /* defValue= */ true);
    }

    /**
     * Sets the value of whether this device needs to perform check-in request.
     *
     * @param context     Context used to get the shared preferences.
     * @param needCheckIn new state of whether the device needs to perform check-in request.
     */
    public static void setNeedCheckIn(Context context, boolean needCheckIn) {
        getSharedPreferences(context)
                .edit()
                .putBoolean(KEY_NEED_CHECK_IN, needCheckIn)
                .apply();
    }

    /**
     * Gets the unique identifier that is regisered to DeviceLock backend server.
     *
     * @param context Context used to get the shared preferences.
     * @return The registered device unique identifier; null if device has never checked in with
     * backed server.
     */
    @Nullable
    public static String getRegisteredDeviceId(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_REGISTERED_DEVICE_ID, null);
    }

    /**
     * Set the unique identifier that is registered to DeviceLock backend server.
     *
     * @param context            Context used to get the shared preferences.
     * @param registeredDeviceId The registered device unique identifier.
     */
    public static void setRegisteredDeviceId(Context context, String registeredDeviceId) {
        getSharedPreferences(context)
                .edit()
                .putString(KEY_REGISTERED_DEVICE_ID, registeredDeviceId)
                .apply();
    }

    /**
     * Check if provision should be forced.
     *
     * @param context Context used to get the shared preferences.
     * @return True if the provision should be forced without any delays.
     */
    public static boolean isProvisionForced(Context context) {
        return getSharedPreferences(context).getBoolean(KEY_FORCED_PROVISION, false);
    }

    /**
     * Set provision is forced
     *
     * @param context  Context used to get the shared preferences.
     * @param isForced The new value of the forced provision flag.
     */
    public static void setProvisionForced(Context context, boolean isForced) {
        getSharedPreferences(context)
                .edit()
                .putBoolean(KEY_FORCED_PROVISION, isForced)
                .apply();
    }

    /**
     * Get the enrollment token assigned by the Device Lock backend server.
     *
     * @param context Context used to get the shared preferences.
     * @return A string value of the enrollment token.
     */
    @Nullable
    public static String getEnrollmentToken(Context context) {
        return getSharedPreferences(context).getString(KEY_ENROLLMENT_TOKEN, null);
    }

    /**
     * Set the enrollment token assigned by the Device Lock backend server.
     *
     * @param context  Context used to get the shared preferences.
     * @param token The string value of the enrollment token.
     */
    public static void setEnrollmentToken(Context context, String token) {
        getSharedPreferences(context)
                .edit()
                .putString(KEY_ENROLLMENT_TOKEN, token)
                .apply();
    }
}
