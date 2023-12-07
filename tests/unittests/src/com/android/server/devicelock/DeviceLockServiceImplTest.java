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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.devicelock.IGetDeviceIdCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.telephony.TelephonyManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.devicelockcontroller.IDeviceLockControllerService;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.List;

/**
 * Tests for {@link com.android.server.devicelock.DeviceLockServiceImpl}.
 */
public final class DeviceLockServiceImplTest {
    private static final String DLC_PACKAGE_NAME = "test.package";

    private static final String DLC_SERVICE_NAME = "test.service";

    private Context mMockContext;

    @Mock
    private PackageManager mPackageManager;

    @Mock
    private TelephonyManager mTelephonyManager;

    @Mock
    private IDeviceLockControllerService mDeviceLockControllerService;

    @Captor
    private ArgumentCaptor<ServiceConnection> mConnectionCaptor;

    private MockitoSession mSession;

    private DeviceLockServiceImpl mService;

    private static final long ONE_SEC_MILLIS = 1000;

    @Before
    public void setup() throws Exception {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .startMocking();

        mMockContext = Mockito.spy(InstrumentationRegistry.getInstrumentation().getContext());
        doNothing().when(mMockContext).enforceCallingPermission(anyString(), anyString());
        doReturn(new Intent()).when(mMockContext).registerReceiverForAllUsers(
                any(), any(), any(), any(), anyInt());
        doReturn(mMockContext).when(mMockContext).createPackageContextAsUser(
                any(), anyInt(), any());
        doReturn(true).when(mMockContext).bindServiceAsUser(any(), any(), anyInt(), any());
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);

        final List<ResolveInfo> resolveInfos = List.of(makeDlcResolveInfo());
        when(mPackageManager.queryIntentServicesAsUser(any(), anyInt(), any()))
                .thenReturn(resolveInfos);

        mService = new DeviceLockServiceImpl(mMockContext, mTelephonyManager);
    }

    @After
    public void teardown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }


    @Test
    public void getDeviceId_withIMEIType_shouldReturnIMEI() throws Exception {
        // GIVEN an IMEI registered in telephony manager
        final String testImei = "983402979622353";
        when(mTelephonyManager.getActiveModemCount()).thenReturn(1);
        when(mTelephonyManager.getImei(0)).thenReturn(testImei);

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
        mockServiceConnected();

        // THEN the IMEI id is received
        verify(mockCallback, timeout(ONE_SEC_MILLIS)).onDeviceIdReceived(
                eq(DEVICE_ID_TYPE_IMEI), eq(testImei));
    }

    @Test
    public void getDeviceId_withMEIDType_shouldReturnMEID() throws Exception {
        // GIVEN an MEID registered in telephony manager
        final String testMeid = "354403064522046";
        when(mTelephonyManager.getActiveModemCount()).thenReturn(1);
        when(mTelephonyManager.getMeid(0)).thenReturn(testMeid);

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
        mockServiceConnected();

        // THEN the MEID id is received
        verify(mockCallback, timeout(ONE_SEC_MILLIS)).onDeviceIdReceived(
                eq(DEVICE_ID_TYPE_MEID), eq(testMeid));
    }

    /**
     * Make the resolve info for the DLC package.
     */
    private ResolveInfo makeDlcResolveInfo() {
        ApplicationInfo appInfo = Mockito.spy(ApplicationInfo.class);
        doReturn(true).when(appInfo).isPrivilegedApp();
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
    private void mockServiceConnected() {
        verify(mMockContext, timeout(ONE_SEC_MILLIS)).bindServiceAsUser(
                any(), mConnectionCaptor.capture(), anyInt(), any());
        ServiceConnection connection = mConnectionCaptor.getValue();
        Binder binder = new Binder();
        binder.attachInterface(mDeviceLockControllerService,
                IDeviceLockControllerService.class.getName());
        connection.onServiceConnected(new ComponentName(DLC_PACKAGE_NAME, DLC_SERVICE_NAME),
                binder);
    }
}
