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
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_PACKAGE_INFO;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_VALID_SIGNING_INFO;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_PACKAGE_NAME_MISMATCH;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_ERROR_CODE_KEY;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.PackageInfoBuilder;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.setup.SetupParameters;
import com.android.devicelockcontroller.setup.UserPreferences;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.concurrent.ExecutionException;


@RunWith(RobolectricTestRunner.class)
public final class VerifyInstalledPackageTaskTest {

    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String TEST_SIGNATURE = "1234";
    private static final String TEST_ANOTHER_SIGNATURE = "abcd";

    private Context mContext;
    private PackageInfo mPackageInfo;
    private WorkManager mWorkManager;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mPackageInfo = PackageInfoBuilder.newBuilder().setPackageName(TEST_PACKAGE_NAME).build();
        Configuration config =
                new Configuration.Builder()
                        .setWorkerFactory(
                                new WorkerFactory() {
                                    @Override
                                    public ListenableWorker createWorker(
                                            Context context, String workerClassName,
                                            WorkerParameters workerParameters) {
                                        return new VerifyInstalledPackageTask(
                                                context, workerParameters,
                                                MoreExecutors.newDirectExecutorService());
                                    }
                                })
                        .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext, config);
        mWorkManager = WorkManager.getInstance(mContext);
    }

    @Test
    public void verifySetup_InvalidCreditorPackageName_SetupFails() {
        WorkInfo workInfo = buildTaskAndRun(mWorkManager);

        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(
                workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY, /* defaultValue */ -1))
                .isEqualTo(ERROR_CODE_PACKAGE_NAME_MISMATCH);
        assertThat(UserPreferences.getKioskSignature(mContext)).isNull();
    }

    @Test
    public void verifySetup_PackageNameNotPresent_SetupFails() {
        setupCreditorPackageName();
        WorkInfo workInfo = buildTaskAndRun(mWorkManager);

        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(
                workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY, /* defaultValue */ -1))
                .isEqualTo(ERROR_CODE_NO_PACKAGE_INFO);
        assertThat(UserPreferences.getKioskSignature(mContext)).isNull();
    }

    @Test
    public void verifySetup_SigningInfoNotPresent_SetupFails() {
        setupCreditorPackageName();
        addPackageInfoToPackageManager();
        WorkInfo workInfo = buildTaskAndRun(mWorkManager);

        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(
                workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY, /* defaultValue */ -1))
                .isEqualTo(ERROR_CODE_NO_VALID_SIGNING_INFO);
        assertThat(UserPreferences.getKioskSignature(mContext)).isNull();
    }

    @Test
    public void verifySetup_SetupSucceeds() {
        setupCreditorPackageName();

        mPackageInfo.signingInfo = new SigningInfo();
        Signature signature = new Signature(TEST_SIGNATURE);
        Shadows.shadowOf(mPackageInfo.signingInfo).setSignatures(new Signature[]{signature});
        addPackageInfoToPackageManager();

        WorkInfo workInfo = buildTaskAndRun(mWorkManager);

        assertThat(workInfo.getState()).isEqualTo(SUCCEEDED);
        assertThat(UserPreferences.getKioskSignature(mContext)).isEqualTo(TEST_SIGNATURE);
    }

    @Test
    public void verifySetup_MultipleSigners_SetupFails() {
        setupCreditorPackageName();

        mPackageInfo.signingInfo = new SigningInfo();
        Shadows.shadowOf(mPackageInfo.signingInfo)
                .setSignatures(
                        new Signature[]{new Signature(TEST_SIGNATURE),
                                new Signature(TEST_ANOTHER_SIGNATURE)});
        addPackageInfoToPackageManager();

        WorkInfo workInfo = buildTaskAndRun(mWorkManager);

        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(
                workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY, /* defaultValue */ -1))
                .isEqualTo(ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS);
        assertThat(UserPreferences.getKioskSignature(mContext)).isNull();
    }

    private void addPackageInfoToPackageManager() {
        Shadows.shadowOf(mContext.getPackageManager()).installPackage(mPackageInfo);
    }

    private void setupCreditorPackageName() {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE_NAME);
        SetupParameters.createPrefs(mContext, bundle);
    }

    private static WorkInfo buildTaskAndRun(WorkManager workManager) {
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(VerifyInstalledPackageTask.class).build();
        workManager.enqueue(request);

        try {
            return workManager.getWorkInfoById(request.getId()).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new AssertionError("Exception", e);
        }
    }
}
