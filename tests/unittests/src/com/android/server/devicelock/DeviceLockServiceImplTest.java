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

package com.android.server.devicelock;

import static android.devicelock.DeviceId.DEVICE_ID_TYPE_IMEI;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_MEID;

import static com.android.server.devicelock.DeviceLockControllerPackageUtils.SERVICE_ACTION;
import static com.android.server.devicelock.TestUtils.eventually;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.devicelock.IGetDeviceIdCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteCallback;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.IDeviceLockControllerService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowTelephonyManager;

/**
 * Tests for {@link com.android.server.devicelock.DeviceLockServiceImpl}.
 */
@RunWith(RobolectricTestRunner.class)
public final class DeviceLockServiceImplTest {
    private static final String DLC_PACKAGE_NAME = "test.package";

    private static final String DLC_SERVICE_NAME = "test.service";

    private static final long ONE_SEC_MILLIS = 1000;

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private ShadowTelephonyManager mShadowTelephonyManager;

    @Mock
    private IDeviceLockControllerService mDeviceLockControllerService;

    private ShadowApplication mShadowApplication;

    private DeviceLockServiceImpl mService;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        mShadowApplication = shadowOf((Application) context);

        PackageManager packageManager = context.getPackageManager();
        ShadowPackageManager shadowPackageManager = shadowOf(packageManager);

        PackageInfo dlcPackageInfo = new PackageInfo();
        dlcPackageInfo.packageName = DLC_PACKAGE_NAME;
        shadowPackageManager.installPackage(dlcPackageInfo);

        Intent intent = new Intent(SERVICE_ACTION);
        ResolveInfo resolveInfo = makeDlcResolveInfo();
        shadowPackageManager.addResolveInfoForIntent(intent, resolveInfo);

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        mShadowTelephonyManager = shadowOf(telephonyManager);

        mService = new DeviceLockServiceImpl(context, telephonyManager);
    }

    @Test
    public void getDeviceId_withIMEIType_shouldReturnIMEI() throws Exception {
        // GIVEN an IMEI registered in telephony manager
        final String testImei = "983402979622353";
        mShadowTelephonyManager.setActiveModemCount(1);
        mShadowTelephonyManager.setImei(/* slotIndex= */ 0, testImei);

        // GIVEN a successful service call to DLC app
        doAnswer((Answer<Void>) invocation -> {
            RemoteCallback callback = invocation.getArgument(0);
            Bundle bundle = new Bundle();
            bundle.putString(IDeviceLockControllerService.KEY_RESULT, testImei);
            callback.sendResult(bundle);
            return null;
        }).when(mDeviceLockControllerService).getDeviceIdentifier(any(RemoteCallback.class));

        IGetDeviceIdCallback mockCallback = mock(IGetDeviceIdCallback.class);

        // WHEN the device id is requested with the IMEI device type
        mService.getDeviceId(mockCallback, 1 << DEVICE_ID_TYPE_IMEI);
        waitUntilConnected();

        // THEN the IMEI id is received
        verify(mockCallback, timeout(ONE_SEC_MILLIS)).onDeviceIdReceived(
                eq(DEVICE_ID_TYPE_IMEI), eq(testImei));
    }

    @Test
    public void getDeviceId_withMEIDType_shouldReturnMEID() throws Exception {
        // GIVEN an MEID registered in telephony manager
        final String testMeid = "354403064522046";
        mShadowTelephonyManager.setActiveModemCount(1);
        mShadowTelephonyManager.setMeid(/* slotIndex= */ 0, testMeid);

        // GIVEN a successful service call to DLC app
        doAnswer((Answer<Void>) invocation -> {
            RemoteCallback callback = invocation.getArgument(0);
            Bundle bundle = new Bundle();
            bundle.putString(IDeviceLockControllerService.KEY_RESULT, testMeid);
            callback.sendResult(bundle);
            return null;
        }).when(mDeviceLockControllerService).getDeviceIdentifier(any(RemoteCallback.class));

        IGetDeviceIdCallback mockCallback = mock(IGetDeviceIdCallback.class);

        // WHEN the device id is requested with the MEID device type
        mService.getDeviceId(mockCallback, 1 << DEVICE_ID_TYPE_MEID);
        waitUntilConnected();

        // THEN the MEID id is received
        verify(mockCallback, timeout(ONE_SEC_MILLIS)).onDeviceIdReceived(
                eq(DEVICE_ID_TYPE_MEID), eq(testMeid));
    }

    /**
     * Make the resolve info for the DLC package.
     */
    private ResolveInfo makeDlcResolveInfo() {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.privateFlags |= ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.name = DLC_SERVICE_NAME;
        serviceInfo.packageName = DLC_PACKAGE_NAME;
        serviceInfo.applicationInfo = appInfo;
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = serviceInfo;

        return resolveInfo;
    }

    /**
     * Set-up calls to mock the service being connected.
     */
    private void waitUntilConnected() {
        eventually(() -> {
            shadowOf(Looper.getMainLooper()).idle();
            ServiceConnection connection = mShadowApplication.getBoundServiceConnections().get(0);
            Binder binder = new Binder();
            binder.attachInterface(mDeviceLockControllerService,
                    IDeviceLockControllerService.class.getName());
            connection.onServiceConnected(new ComponentName(DLC_PACKAGE_NAME, DLC_SERVICE_NAME),
                    binder);
        }, ONE_SEC_MILLIS);
    }
}
