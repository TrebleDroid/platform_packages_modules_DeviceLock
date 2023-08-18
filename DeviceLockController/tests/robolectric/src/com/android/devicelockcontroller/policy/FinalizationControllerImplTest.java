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

import static com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker.REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.receivers.LockedBootCompletedReceiver;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public final class FinalizationControllerImplTest {

    private static final int TIMEOUT_MS = 1000;

    private Context mContext;
    private FinalizationControllerImpl mFinalizationController;
    private FinalizationStateDispatchQueue mDispatchQueue;
    private Executor mSequentialExecutor =
            MoreExecutors.newSequentialExecutor(Executors.newSingleThreadExecutor());
    private Executor mLightweightExecutor = Executors.newCachedThreadPool();

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext);

        mDispatchQueue = new FinalizationStateDispatchQueue(mSequentialExecutor);
        mFinalizationController = new FinalizationControllerImpl(
                mContext, mDispatchQueue, mLightweightExecutor, TestWorker.class);
    }

    @Test
    public void notifyRestrictionsCleared_startsReportingWork() throws Exception {
        // WHEN restrictions are cleared
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN work manager has work scheduled to report the device is finalized
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mContext)
                .getWorkInfosForUniqueWork(REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isNotEmpty();
    }

    @Test
    public void reportingFinished_disablesApplication() throws Exception {
        // WHEN restrictions are cleared and the work is reported successfully
        Futures.getChecked(mFinalizationController.notifyRestrictionsCleared(),
                Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);
        // Wait for all executors to finish
        shadowOf(Looper.getMainLooper()).idle();
        waitForAllExecution(mSequentialExecutor);
        waitForAllExecution(mLightweightExecutor);

        // THEN the application is disabled
        PackageManager pm = mContext.getPackageManager();
        assertThat(pm.getComponentEnabledSetting(
                new ComponentName(mContext, LockedBootCompletedReceiver.class)))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        // TODO(279517666): Assert checks that application itself is disabled when implemented
    }

    private static void waitForAllExecution(Executor executor) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> latch.countDown());
        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Fake test worker that just finishes work immediately
     */
    private static final class TestWorker extends ListenableWorker {

        TestWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
            super(appContext, workerParams);
        }

        @NonNull
        @Override
        public ListenableFuture<Result> startWork() {
            return Futures.immediateFuture(Result.success());
        }
    }
}
