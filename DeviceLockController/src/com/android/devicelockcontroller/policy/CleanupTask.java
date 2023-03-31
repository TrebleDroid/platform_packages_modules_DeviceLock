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

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.File;

/**
 * Delete the apk specified at {@code
 * getInputData().getString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY)}.
 */
public final class CleanupTask extends AbstractTask {

    public static final String TAG = "CleanupTask";
    private final ListeningExecutorService mExecutorService;

    public CleanupTask(
            Context appContext, WorkerParameters workerParams,
            ListeningExecutorService executorService) {
        super(appContext, workerParams);
        this.mExecutorService = executorService;
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return mExecutorService.submit(this::doCleanUpWork);
    }

    private Result doCleanUpWork() {
        String fileLocation = getInputData().getString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY);
        if (TextUtils.isEmpty(fileLocation)) {
            LogUtil.w(TAG, "The downloaded file path is null or empty");
            return failure(ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
        }
        File file = new File(fileLocation);
        if (file.delete()) {
            return Result.success();
        } else {
            LogUtil.w(TAG, "Unable to delete the downloaded file");
            return failure(ERROR_CODE_DELETE_APK_FAILED);
        }
    }
}
