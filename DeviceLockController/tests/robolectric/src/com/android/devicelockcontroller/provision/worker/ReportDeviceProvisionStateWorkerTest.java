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

package com.android.devicelockcontroller.provision.worker;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.KEY_LAST_RECEIVED_STATE;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.UNEXPECTED_PROVISION_STATE_ERROR_MESSAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestWorkerBuilder;

import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class ReportDeviceProvisionStateWorkerTest {
    public static final int UNEXPECTED_VALUE = -1;
    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private DeviceCheckInClient mClient;
    @Mock
    private ReportDeviceProvisionStateGrpcResponse mResponse;
    private ReportDeviceProvisionStateWorker mWorker;

    @Before
    public void setUp() throws Exception {
        final Context context = ApplicationProvider.getApplicationContext();
        final Executor executor = Executors.newSingleThreadExecutor();
        when(mClient.reportDeviceProvisionState(anyInt(), anyInt(), anyBoolean())).thenReturn(
                mResponse);
        mWorker = TestWorkerBuilder.from(
                        context, ReportDeviceProvisionStateWorker.class, executor)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(
                                        ReportDeviceProvisionStateWorker.class.getName())
                                        ? new ReportDeviceProvisionStateWorker(context,
                                        workerParameters, mClient)
                                        : null;
                            }
                        }).build();
    }

    @Test
    public void doWork_responseHasRecoverableError_returnRetry() {
        when(mResponse.hasRecoverableError()).thenReturn(true);

        assertThat(mWorker.doWork()).isEqualTo(Result.retry());
    }

    @Test
    public void doWork_responseHasFatalError_returnFailure() {
        when(mResponse.hasFatalError()).thenReturn(true);

        assertThat(mWorker.doWork()).isEqualTo(Result.failure());
    }

    @Test
    public void doWork_nextProvisionStateUnExpected_shouldThrowException() {
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getNextClientProvisionState()).thenReturn(UNEXPECTED_VALUE);
        try {
            mWorker.doWork();
        } catch (IllegalStateException actualException) {
            assertThat(actualException).hasMessageThat().isEqualTo(
                    UNEXPECTED_PROVISION_STATE_ERROR_MESSAGE);
            return;
        }
        throw new AssertionError("Expected exception is not thrown!");
    }

    @Test
    public void doWork_nextProvisionStateUnspecified_shouldReturnSuccess() {
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getNextClientProvisionState()).thenReturn(PROVISION_STATE_UNSPECIFIED);
        Result expectedResult = Result.success(new Data.Builder().putInt(KEY_LAST_RECEIVED_STATE,
                PROVISION_STATE_UNSPECIFIED).build());

        Result actualResult = mWorker.doWork();

        assertThat(actualResult).isEqualTo(expectedResult);
    }
}
