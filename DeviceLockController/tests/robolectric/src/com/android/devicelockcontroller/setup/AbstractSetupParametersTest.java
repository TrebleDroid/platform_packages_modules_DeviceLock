/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_ALLOWLIST;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_DOWNLOAD_URL;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_READ_IMEI_ALLOWED;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_SETUP_ACTIVITY;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_SIGNATURE_CHECKSUM;

import android.os.Bundle;

import java.util.ArrayList;

abstract class AbstractSetupParametersTest {
    protected static final String KIOSK_PACKAGE = "package";
    protected static final String KIOSK_OVERRIDE_PACKAGE = "override.package";
    protected static final String DOWNLOAD_URL = "https://www.example.com/apk";
    protected static final String SIGNATURE_CHECKSUM = "12345678";
    protected static final String SETUP_ACTIVITY = "setup-activity";
    protected static final boolean DISABLE_OUTGOING_CALLS = true;
    protected static final boolean READ_IMEI_ALLOWED = true;
    protected static final boolean ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE = true;
    protected static final String KIOSK_ALLOWLIST_PACKAGE_0 = "package.name.0";
    protected static final String KIOSK_ALLOWLIST_PACKAGE_1 = "package.name.1";

    protected static Bundle createParamsBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, KIOSK_PACKAGE);
        bundle.putString(EXTRA_KIOSK_DOWNLOAD_URL, DOWNLOAD_URL);
        bundle.putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, SIGNATURE_CHECKSUM);
        bundle.putString(EXTRA_KIOSK_SETUP_ACTIVITY, SETUP_ACTIVITY);
        bundle.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, DISABLE_OUTGOING_CALLS);
        bundle.putBoolean(EXTRA_KIOSK_READ_IMEI_ALLOWED, READ_IMEI_ALLOWED);
        bundle.putBoolean(
                EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE,
                ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE);
        final ArrayList<String> actualKioskAllowlist = new ArrayList<>();
        actualKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_0);
        actualKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_1);
        bundle.putStringArrayList(EXTRA_KIOSK_ALLOWLIST, actualKioskAllowlist);
        return bundle;
    }

    protected static Bundle createParamsOverrideBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, KIOSK_OVERRIDE_PACKAGE);
        return bundle;
    }
}
