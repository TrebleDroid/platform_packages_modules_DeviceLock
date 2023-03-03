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

package com.android.devicelockcontroller.provision.grpc;

/**
 * A data structure class that contains information necessary to device provisioning for DeviceLock
 * program.
 */
public final class ProvisioningConfiguration {

    // The URL to download the kiosk app for non-GMS devices.
    private final String mKioskAppDownloadUrl;
    // The package name of the kiosk app, e.g. "com.foo.bar".
    private final String mKioskAppProviderName;
    // The name of the provider of the kiosk app, e.g. "Foo Bar Inc".

    private final String mKioskAppPackageName;
    // The checksum used to sign the kiosk app for verifying the validity of the kiosk app.
    private final String mKioskAppSignatureChecksum;
    // The package component of the activity of the kiosk app that the user
    // would interact when the device is locked (i.e. this activity allows the
    // user to make a payment), e.g. "com.foo.bar/com.foo.bar.MainActivity".
    private final String mKioskAppMainActivity;
    // The list of apps that a user can use when the device is locked.
    private final String mKioskAppAllowlistPackages;
    // Whether the user can make phone calls when the device is locked.
    private final boolean mKioskAppEnableOutgoingCalls;

    // Whether notifications are shown to the user when the device is locked.
    private final boolean mKioskAppEnableEnableNotifications;

    public ProvisioningConfiguration(
            String kioskAppDownloadUrl, String kioskAppProviderName,
            String kioskAppPackageName, String kioskAppSignatureChecksum,
            String kioskAppMainActivity, String kioskAppAllowlistPackages,
            boolean kioskAppEnableOutgoingCalls, boolean kioskAppEnableEnableNotifications) {
        mKioskAppDownloadUrl = kioskAppDownloadUrl;
        mKioskAppProviderName = kioskAppProviderName;
        mKioskAppPackageName = kioskAppPackageName;
        mKioskAppSignatureChecksum = kioskAppSignatureChecksum;
        mKioskAppMainActivity = kioskAppMainActivity;
        mKioskAppAllowlistPackages = kioskAppAllowlistPackages;
        mKioskAppEnableOutgoingCalls = kioskAppEnableOutgoingCalls;
        mKioskAppEnableEnableNotifications = kioskAppEnableEnableNotifications;
    }

    public String getKioskAppDownloadUrl() {
        return mKioskAppDownloadUrl;
    }

    public String getKioskAppProviderName() {
        return mKioskAppProviderName;
    }

    public String getKioskAppPackageName() {
        return mKioskAppPackageName;
    }

    public String getKioskAppSignatureChecksum() {
        return mKioskAppSignatureChecksum;
    }

    public String getKioskAppMainActivity() {
        return mKioskAppMainActivity;
    }

    public String getKioskAppAllowlistPackages() {
        return mKioskAppAllowlistPackages;
    }

    public boolean isKioskAppEnableOutgoingCalls() {
        return mKioskAppEnableOutgoingCalls;
    }

    public boolean isKioskAppEnableEnableNotifications() {
        return mKioskAppEnableEnableNotifications;
    }
}
