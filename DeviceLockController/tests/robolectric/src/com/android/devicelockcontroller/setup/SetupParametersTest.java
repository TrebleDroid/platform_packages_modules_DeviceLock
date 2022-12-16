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

import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_ALLOWLIST;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_DOWNLOAD_URL;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_READ_IMEI_ALLOWED;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_SETUP_ACTIVITY;
import static com.android.devicelockcontroller.setup.SetupParameters.EXTRA_KIOSK_SIGNATURE_CHECKSUM;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class SetupParametersTest {
    private static final String KIOSK_PACKAGE = "package";
    private static final String KIOSK_OVERRIDE_PACKAGE = "override.package";
    private static final String DOWNLOAD_URL = "https://www.example.com/apk";
    private static final String SIGNATURE_CHECKSUM = "12345678";
    private static final String SETUP_ACTIVITY = "setup-activity";
    private static final boolean DISABLE_OUTGOING_CALLS = true;
    private static final boolean READ_IMEI_ALLOWED = true;
    private static final boolean ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE = true;
    private static final String KIOSK_ALLOWLIST_PACKAGE_0 = "package.name.0";
    private static final String KIOSK_ALLOWLIST_PACKAGE_1 = "package.name.1";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void createPrefs_shouldPopulatePreferences() {
        Bundle bundle = createParamsBundle();
        SetupParameters.createPrefs(mContext, bundle);

        assertThat(SetupParameters.getKioskPackage(mContext)).isEqualTo(KIOSK_PACKAGE);
        assertThat(SetupParameters.getKioskDownloadUrl(mContext)).isEqualTo(DOWNLOAD_URL);
        assertThat(SetupParameters.getKioskSignatureChecksum(mContext)).isEqualTo(
                SIGNATURE_CHECKSUM);
        assertThat(SetupParameters.getKioskSetupActivity(mContext)).isEqualTo(SETUP_ACTIVITY);
        assertThat(SetupParameters.getOutgoingCallsDisabled(mContext)).isTrue();
        assertThat(SetupParameters.isNotificationsInLockTaskModeEnabled(mContext)).isTrue();

        List<String> expectedKioskAllowlist = new ArrayList<>();
        expectedKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_0);
        expectedKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_1);
        assertThat(SetupParameters.getKioskAllowlist(mContext))
                .containsExactlyElementsIn(expectedKioskAllowlist);
    }

    @Test
    public void createPrefs_whenCalledTwice_doesNotOverwrite() {
        Bundle bundle = createParamsBundle();
        SetupParameters.createPrefs(mContext, bundle);

        assertThat(SetupParameters.getKioskPackage(mContext)).isEqualTo(KIOSK_PACKAGE);

        Bundle newBundle = createParamsOverrideBundle();
        SetupParameters.createPrefs(mContext, newBundle);
        assertThat(SetupParameters.getKioskPackage(mContext)).isEqualTo(KIOSK_PACKAGE);
    }

    private static Bundle createParamsBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, KIOSK_PACKAGE);
        bundle.putString(EXTRA_KIOSK_DOWNLOAD_URL, DOWNLOAD_URL);
        bundle.putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, SIGNATURE_CHECKSUM);
        bundle.putString(EXTRA_KIOSK_SETUP_ACTIVITY, SETUP_ACTIVITY);
        bundle.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, DISABLE_OUTGOING_CALLS);
        bundle.putBoolean(EXTRA_KIOSK_READ_IMEI_ALLOWED, READ_IMEI_ALLOWED);
        bundle.putBoolean(
                EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE,
                ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE);
        ArrayList<String> actualKioskAllowlist = new ArrayList<>();
        actualKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_0);
        actualKioskAllowlist.add(KIOSK_ALLOWLIST_PACKAGE_1);
        bundle.putStringArrayList(EXTRA_KIOSK_ALLOWLIST, actualKioskAllowlist);
        return bundle;
    }

    private static Bundle createParamsOverrideBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, KIOSK_OVERRIDE_PACKAGE);
        return bundle;
    }
}
