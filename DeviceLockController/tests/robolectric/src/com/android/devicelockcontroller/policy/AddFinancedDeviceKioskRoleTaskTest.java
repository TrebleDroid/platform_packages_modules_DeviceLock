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

import static androidx.work.WorkInfo.State.FAILED;
import static androidx.work.WorkInfo.State.SUCCEEDED;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_ADD_FINANCED_DEVICE_KIOSK_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_PACKAGE_NAME;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_ERROR_CODE_KEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.SystemDeviceLockManager;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@RunWith(RobolectricTestRunner.class)
public class AddFinancedDeviceKioskRoleTaskTest {
    private Context mContext;
    private WorkManager mWorkManager;
    private static final String KIOSK_PACKAGE_NAME = "com.example.kiosk";

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();

    @Mock
    private SystemDeviceLockManager mSystemDeviceLockManagerMock;

    private static WorkInfo buildTaskAndRun(WorkManager workManager, @Nullable String packageName) {
        final Data inputData =
                new Data.Builder()
                        .putString(EXTRA_KIOSK_PACKAGE, packageName)
                        .build();

        return getWorkInfo(inputData, workManager);
    }

    private static WorkInfo getWorkInfo(Data inputData, WorkManager workManager) {
        final OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(AddFinancedDeviceKioskRoleTask.class)
                        .setInputData(inputData)
                        .build();
        workManager.enqueue(request);

        try {
            return workManager.getWorkInfoById(request.getId()).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new AssertionError("Exception", e);
        }
    }

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();

        final Configuration config =
                new Configuration.Builder().setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(Context context,
                                    String workerClassName, WorkerParameters workerParameters) {
                                if (!workerClassName.equals(
                                        AddFinancedDeviceKioskRoleTask.class.getName())) {
                                    return null;
                                }

                                return new AddFinancedDeviceKioskRoleTask(context, workerParameters,
                                        MoreExecutors.newDirectExecutorService(),
                                        mSystemDeviceLockManagerMock);
                            }
                        }).build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext, config);
        mWorkManager = WorkManager.getInstance(mContext);
    }

    @Test
    public void testAddRole_expectedPackageNameIsEmptyReturnsError() {
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, "");

        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_NO_PACKAGE_NAME);
    }

    @Test
    public void testAddRole_systemDeviceLockManagerReturnsError() {
        doAnswer((Answer<Void>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(2 /* callback */);
            callback.onError(new Exception());

            return null;
        }).when(mSystemDeviceLockManagerMock).addFinancedDeviceKioskRole(eq(KIOSK_PACKAGE_NAME),
                any(Executor.class), ArgumentMatchers.<OutcomeReceiver<Void, Exception>>any());

        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, KIOSK_PACKAGE_NAME);

        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_ADD_FINANCED_DEVICE_KIOSK_FAILED);
    }

    @Test
    public void testAddRole_systemDeviceLockManagerReturnsSuccess() {
        doAnswer((Answer<Void>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(2 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mSystemDeviceLockManagerMock).addFinancedDeviceKioskRole(eq(KIOSK_PACKAGE_NAME),
                any(Executor.class), ArgumentMatchers.<OutcomeReceiver<Void, Exception>>any());

        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, KIOSK_PACKAGE_NAME);

        assertThat(workInfo.getState()).isEqualTo(SUCCEEDED);
    }
}
