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

package com.android.devicelockcontroller.storage;

import android.annotation.CurrentTimeMillisLong;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.Locale;

/**
 * Stores global parameters.
 * <p>
 * Note that, these parameter values are common across all users which means any users can read or
 * write them. Due to this reason, unlike {@link UserParameters}, they must be accessed all the time
 * via the {@link GlobalParametersClient}.
 */
final class GlobalParameters {
    private static final String FILENAME = "global-params";
    private static final String KEY_NEED_CHECK_IN = "need_check_in";
    private static final String KEY_REGISTERED_DEVICE_ID = "registered_device_id";
    private static final String KEY_FORCED_PROVISION = "forced_provision";
    private static final String KEY_ENROLLMENT_TOKEN = "enrollment_token";
    private static final String KEY_LAST_RECEIVED_PROVISION_STATE = "last-received-provision-state";
    public static final String TAG = "GlobalParameters";
    public static final String KEY_BOOT_TIME_MILLS = "boot-time-mills";
    public static final String KEY_NEXT_CHECK_IN_TIME_MILLIS = "next-check-in-time-millis";
    public static final String KEY_RESUME_PROVISION_TIME_MILLIS =
            "resume-provision-time-millis";
    public static final String KEY_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS =
            "next-provision-failed-step-time-millis";


    private GlobalParameters() {
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        final Context deviceContext = context.createDeviceProtectedStorageContext();

        return deviceContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }

    /**
     * Checks if a check-in request needs to be performed.
     *
     * @param context Context used to get the shared preferences.
     * @return true if check-in request needs to be performed.
     */
    static boolean needCheckIn(Context context) {
        return getSharedPreferences(context).getBoolean(KEY_NEED_CHECK_IN, /* defValue= */ true);
    }

    /**
     * Sets the value of whether this device needs to perform check-in request.
     *
     * @param context     Context used to get the shared preferences.
     * @param needCheckIn new state of whether the device needs to perform check-in request.
     */
    static void setNeedCheckIn(Context context, boolean needCheckIn) {
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
    static String getRegisteredDeviceId(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_REGISTERED_DEVICE_ID, null);
    }

    /**
     * Set the unique identifier that is registered to DeviceLock backend server.
     *
     * @param context            Context used to get the shared preferences.
     * @param registeredDeviceId The registered device unique identifier.
     */
    static void setRegisteredDeviceId(Context context, String registeredDeviceId) {
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
    static boolean isProvisionForced(Context context) {
        return getSharedPreferences(context).getBoolean(KEY_FORCED_PROVISION, false);
    }

    /**
     * Set provision is forced
     *
     * @param context  Context used to get the shared preferences.
     * @param isForced The new value of the forced provision flag.
     */
    static void setProvisionForced(Context context, boolean isForced) {
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
    static String getEnrollmentToken(Context context) {
        return getSharedPreferences(context).getString(KEY_ENROLLMENT_TOKEN, null);
    }

    /**
     * Set the enrollment token assigned by the Device Lock backend server.
     *
     * @param context Context used to get the shared preferences.
     * @param token   The string value of the enrollment token.
     */
    static void setEnrollmentToken(Context context, String token) {
        getSharedPreferences(context)
                .edit()
                .putString(KEY_ENROLLMENT_TOKEN, token)
                .apply();
    }

    @DeviceProvisionState
    static int getLastReceivedProvisionState(Context context) {
        return getSharedPreferences(context).getInt(KEY_LAST_RECEIVED_PROVISION_STATE,
                DeviceProvisionState.PROVISION_STATE_UNSPECIFIED);
    }

    static void setLastReceivedProvisionState(Context context,
            @DeviceProvisionState int provisionState) {
        getSharedPreferences(context)
                .edit()
                .putInt(KEY_LAST_RECEIVED_PROVISION_STATE, provisionState)
                .apply();
    }

    @CurrentTimeMillisLong
    static long getBootTimeMillis(Context context) {
        return getSharedPreferences(context).getLong(KEY_BOOT_TIME_MILLS, 0L);
    }

    static void setBootTimeMillis(Context context, @CurrentTimeMillisLong long bootTime) {
        getSharedPreferences(context).edit().putLong(KEY_BOOT_TIME_MILLS, bootTime).apply();
    }

    @CurrentTimeMillisLong
    static long getNextCheckInTimeMillis(Context context) {
        return getSharedPreferences(context).getLong(KEY_NEXT_CHECK_IN_TIME_MILLIS, 0L);
    }

    static void setNextCheckInTimeMillis(Context context,
            @CurrentTimeMillisLong long nextCheckInTime) {
        getSharedPreferences(context).edit().putLong(KEY_NEXT_CHECK_IN_TIME_MILLIS,
                nextCheckInTime).apply();
    }

    @CurrentTimeMillisLong
    static long getResumeProvisionTimeMillis(Context context) {
        return getSharedPreferences(context).getLong(KEY_RESUME_PROVISION_TIME_MILLIS, 0L);
    }

    static void setResumeProvisionTimeMillis(Context context,
            @CurrentTimeMillisLong long resumeProvisionTime) {
        getSharedPreferences(context).edit().putLong(KEY_RESUME_PROVISION_TIME_MILLIS,
                resumeProvisionTime).apply();
    }

    @CurrentTimeMillisLong
    static long getNextProvisionFailedStepTimeMills(Context context) {
        return getSharedPreferences(context).getLong(KEY_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS,
                0L);
    }

    static void setNextProvisionFailedStepTimeMills(Context context,
            @CurrentTimeMillisLong long nextProvisionFailedStep) {
        getSharedPreferences(context).edit().putLong(KEY_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS,
                nextProvisionFailedStep).apply();
    }

    static void clear(Context context) {
        if (!Build.isDebuggable()) {
            throw new SecurityException("Clear is not allowed in non-debuggable build!");
        }
        getSharedPreferences(context).edit().clear().commit();
    }

    static void dump(Context context) {
        LogUtil.d(TAG, String.format(Locale.US,
                "Dumping GlobalParameters ...\n"
                        + "%s: %s\n"    // need_check_in:
                        + "%s: %s\n"    // registered_device_id:
                        + "%s: %s\n"    // forced_provision:
                        + "%s: %s\n"    // enrollment_token:
                        + "%s: %s\n"    // last-received-provision-state:
                        + "%s: %s\n"    // boot-time-mills:
                        + "%s: %s\n"    // next-check-in-time-millis:
                        + "%s: %s\n"    // resume-provision-time-millis:
                        + "%s: %s\n",    // next-provision-failed-step-time-millis:
                KEY_NEED_CHECK_IN, needCheckIn(context),
                KEY_REGISTERED_DEVICE_ID, getRegisteredDeviceId(context),
                KEY_FORCED_PROVISION, isProvisionForced(context),
                KEY_ENROLLMENT_TOKEN, getEnrollmentToken(context),
                KEY_LAST_RECEIVED_PROVISION_STATE, getLastReceivedProvisionState(context),
                KEY_BOOT_TIME_MILLS, getBootTimeMillis(context),
                KEY_NEXT_CHECK_IN_TIME_MILLIS, getNextCheckInTimeMillis(context),
                KEY_RESUME_PROVISION_TIME_MILLIS, getResumeProvisionTimeMillis(context),
                KEY_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS,
                getNextProvisionFailedStepTimeMills(context)
        ));
    }
}
