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

package com.android.devicelockcontroller.common;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Constants being used by more than one class in the Device Lock application. */
public final class DeviceLockConstants {
    // TODO: properly set to an activity. Additionally, package could be com.android... or
    // com.google.android... and should be determined dynamically.
    public static final String SETUP_FAILED_ACTIVITY =
            "com.android.devicelockcontroller/"
                    + "com.android.devicelockcontroller.SetupFailedActivity";

    // Constants related to unique device identifiers.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            DEVICE_ID_TYPE_UNSPECIFIED,
            DEVICE_ID_TYPE_IMEI,
            DEVICE_ID_TYPE_MEID,
    })
    public @interface DeviceIdType {}
    // The device id type is unspecified
    public static final int DEVICE_ID_TYPE_UNSPECIFIED = -1;
    // The device id is a IMEI
    public static final int DEVICE_ID_TYPE_IMEI = 0;
    // The device id is a MEID
    public static final int DEVICE_ID_TYPE_MEID = 1;
    @DeviceIdType
    private static final int LAST_DEVICE_ID_TYPE = DEVICE_ID_TYPE_MEID;
    public static final int TOTAL_DEVICE_ID_TYPES = LAST_DEVICE_ID_TYPE + 1;

    // Constants related to unique device identifiers.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            STATUS_UNSPECIFIED,
            READY_FOR_PROVISION,
            RETRY_CHECK_IN,
            STOP_CHECK_IN,
    })
    public @interface DeviceCheckInStatus {}

    public static final int STATUS_UNSPECIFIED = 0;
    public static final int READY_FOR_PROVISION = 1;
    public static final int RETRY_CHECK_IN = 2;
    public static final int STOP_CHECK_IN = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            REASON_UNSPECIFIED,
            USER_DEFERRED_DEVICE_PROVISIONING,
    })
    public @interface PauseDeviceProvisioningReason {}

    public static final int REASON_UNSPECIFIED = 0;
    public static final int USER_DEFERRED_DEVICE_PROVISIONING = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            TYPE_UNDEFINED,
            TYPE_FINANCED,
    })
    public @interface ProvisioningType {}

    public static final int TYPE_UNDEFINED = 0;
    public static final int TYPE_FINANCED = 1;

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
    public static final String EXTRA_KIOSK_DISABLE_OUTGOING_CALLS =
            "com.android.devicelockcontroller.KIOSK_DISABLE_OUTGOING_CALLS";
    /**
     * Used to control if notifications are enabled in lock task mode. The default value is false.
     *
     * @see android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_NOTIFICATIONS
     */
    public static final String EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE =
            "com.android.devicelockcontroller.KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE";
    public static final String EXTRA_KIOSK_ALLOWLIST =
            "com.android.devicelockcontroller.KIOSK_ALLOWLIST";
    public static final String EXTRA_PROVISIONING_TYPE =
            "com.android.devicelockcontroller.PROVISIONING_TYPE";
    public static final String EXTRA_MANDATORY_PROVISION =
            "com.android.devicelockcontroller.MANDATORY_PROVISION";

    /** Restrict instantiation. */
    private DeviceLockConstants() {}
}
