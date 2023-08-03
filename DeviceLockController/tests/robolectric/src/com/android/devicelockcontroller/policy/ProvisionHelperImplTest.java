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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_KIOSK;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle.State;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.activities.ProvisioningProgress;
import com.android.devicelockcontroller.activities.ProvisioningProgressController;
import com.android.devicelockcontroller.shadows.ShadowBuild;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("UnusedMethod") //TODO: Increase test coverage with the existing helper methods.
@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowBuild.class})
public final class ProvisionHelperImplTest {

    private static final String TEST_PACKAGE_NAME = "test.package.name";

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();

    private ProvisionStateController mMockStateController;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private ProvisioningProgressController mProgressController;
    private TestDeviceLockControllerApplication mTestApplication;
    private SetupParametersClient mSetupParametersClient;
    private TestWorkFactory mTestWorkFactory;
    private ProvisionHelper mProvisionHelper;

    @Before
    public void setUp() {
        mTestApplication = ApplicationProvider.getApplicationContext();
        mMockStateController = mTestApplication.getProvisionStateController();
        Shadows.shadowOf(mTestApplication).setComponentNameAndServiceForBindService(
                new ComponentName(mTestApplication, SetupParametersService.class),
                Robolectric.setupService(SetupParametersService.class).onBind(null));
        mSetupParametersClient = SetupParametersClient.getInstance(
                mTestApplication, TestingExecutors.sameThreadScheduledExecutor());
        mProvisionHelper = new ProvisionHelperImpl(mTestApplication, mMockStateController);
        mTestWorkFactory = new TestWorkFactory();
        Configuration config =
                new Configuration.Builder().setWorkerFactory(mTestWorkFactory).build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApplication, config);
    }

    @Ignore // Fix this test
    @Test
    public void startProvisionFlow_isKioskAppPreinstalled_debuggableBuild() {
        // GIVEN build is debuggable build and kiosk app is installed.
        ShadowBuild.setIsDebuggable(true);
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE_NAME);
        createParameters(bundle);
        ShadowPackageManager pm = Shadows.shadowOf(mTestApplication.getPackageManager());
        PackageInfo kioskPackageInfo = new PackageInfo();
        kioskPackageInfo.packageName = TEST_PACKAGE_NAME;
        pm.installPackage(kioskPackageInfo);
        setupLifecycle();

        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner, mProgressController);

        ArgumentCaptor<ProvisioningProgress> argumentCaptor = ArgumentCaptor.forClass(
                ProvisioningProgress.class);
        verify(mProgressController, times(2)).setProvisioningProgress(argumentCaptor.capture());
        assertEquals(argumentCaptor.getAllValues(),
                Arrays.asList(ProvisioningProgress.INSTALLING_KIOSK_APP,
                        ProvisioningProgress.OPENING_KIOSK_APP));
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));
    }

    @Ignore // TODO: Figure out how to test play install path.
    @Test
    public void startProvisionFlow_nonDebuggableBuild_playInstall() {
    }

    @Ignore // TODO: Figure out how to test play install path.
    @Test
    public void startProvisionFlow_debuggableBuild_kioskNotInstalled_playInstall() {
    }

    private void createParameters(Bundle b) {
        try {
            Futures.getChecked(mSetupParametersClient.createPrefs(b), ExecutionException.class);
        } catch (ExecutionException e) {
            throw new AssertionError("Failed to create setup parameters!", e);
        }
    }

    private void setupLifecycle() {
        LifecycleRegistry mockLifecycle = new LifecycleRegistry(mMockLifecycleOwner);
        mockLifecycle.setCurrentState(State.RESUMED);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mockLifecycle);
    }

    private static final class TestWorkFactory extends WorkerFactory {
        private final ArrayMap<String, Integer> mResultMap =
                new ArrayMap<>();

        @Nullable
        @Override
        public ListenableWorker createWorker(
                @NonNull Context context,
                @NonNull String workerClassName,
                @NonNull WorkerParameters workerParameters) {
            return new ListenableWorker(context, workerParameters) {
                @NonNull
                @Override
                public ListenableFuture<Result> startWork() {
                    return MoreExecutors.listeningDecorator(
                                    TestingExecutors.sameThreadScheduledExecutor())
                            .submit(() -> {
                                final Integer resultCode = mResultMap.get(workerClassName);
                                return resultCode == null || resultCode < 0
                                        ? Result.success() : Result.failure();
                            });
                }
            };
        }
    }
}
