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

import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_DELETE_APK_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_VALID_DOWNLOADED_FILE;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_ERROR_CODE_KEY;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

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

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@RunWith(RobolectricTestRunner.class)
public class CleanupTaskTest {

    private WorkManager mWorkManager;
    private String mFileLocation;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        Configuration config =
                new Configuration.Builder()
                        .setWorkerFactory(
                                new WorkerFactory() {
                                    @Override
                                    public ListenableWorker createWorker(
                                            Context context, String workerClassName,
                                            WorkerParameters workerParameters) {
                                        return new CleanupTask(
                                                context, workerParameters,
                                                MoreExecutors.newDirectExecutorService());
                                    }
                                })
                        .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
        mWorkManager = WorkManager.getInstance(context);
        mFileLocation = context.getFilesDir() + "/TEST_FILE_NAME";
    }

    @Test
    public void cleanup_downloadedFilePathIsNull() {
        WorkInfo workInfo = buildTaskAndRun(mWorkManager, /* fileLocation= */ null);

        assertWorkFailedWithErrorCode(workInfo, ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
    }

    @Test
    public void cleanup_downloadedFilePathIsEmpty() {
        WorkInfo workInfo = buildTaskAndRun(mWorkManager, /* fileLocation= */ "");

        assertWorkFailedWithErrorCode(workInfo, ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
    }

    @Test
    public void cleanup_fileDoesNotExist_failure() {
        WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        assertWorkFailedWithErrorCode(workInfo, ERROR_CODE_DELETE_APK_FAILED);
    }

    @Test
    public void cleanup_success() throws IOException {
        FileOutputStream outputStream = new FileOutputStream(mFileLocation);
        outputStream.write(new byte[]{1, 2, 3, 4});

        WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        assertThat(workInfo.getState()).isEqualTo(SUCCEEDED);
    }

    private static WorkInfo buildTaskAndRun(WorkManager workManager, String fileLocation) {
        Data inputData =
                new Data.Builder()
                        .putString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY, fileLocation)
                        .build();

        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(InstallPackageTask.class).setInputData(
                        inputData).build();
        workManager.enqueue(request);

        try {
            return workManager.getWorkInfoById(request.getId()).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new AssertionError("Exception", e);
        }
    }

    private static void assertWorkFailedWithErrorCode(WorkInfo workInfo, int errorCode) {
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(
                workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY, /* defaultValue= */ -1))
                .isEqualTo(errorCode);
    }
}
