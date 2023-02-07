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
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.policy.CronetDownloadHandler.DownloadPackageException;
import com.android.devicelockcontroller.setup.SetupParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Locale;

/**
 * Download the apk from the {@link SetupParameters#getKioskDownloadUrl(Context)}. The location
 * of the downloaded file will be included in the output {@link Data} with key {@link
 * AbstractTask#TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY}.
 *
 * <p>The task is supposed to be used with {@link androidx.work.OneTimeWorkRequest} and {@link
 * androidx.work.WorkManager}. And at most one instance is supposed to be running at any time.
 */
public final class DownloadPackageTask extends AbstractTask {
    private static final String TAG = "DownloadPackageTask";

    private static final String DOWNLOADED_FILE_NAME = "downloaded_kiosk_app.apk";

    private final Context mContext;
    private final ListeningExecutorService mExecutorService;
    private final Downloader mDownloader;
    private final String mFileLocation;

    public DownloadPackageTask(
            Context context,
            WorkerParameters workerParameters,
            ListeningExecutorService executorService) {
        this(
                context,
                workerParameters,
                executorService,
                new CronetDownloader(
                        context,
                        SetupParameters.getKioskDownloadUrl(context),
                        context.getFilesDir() + "/" + DOWNLOADED_FILE_NAME));
    }

    @VisibleForTesting
    DownloadPackageTask(
            Context context,
            WorkerParameters workerParameters,
            ListeningExecutorService executorService,
            Downloader downloader) {
        super(context, workerParameters);
        mContext = context;
        mExecutorService = executorService;
        mDownloader = downloader;
        mFileLocation = downloader.getFileLocation();
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return mExecutorService.submit(
                () -> {
                    LogUtil.i(TAG, "Starts to run");
                    if (TextUtils.isEmpty(SetupParameters.getKioskDownloadUrl(mContext))) {
                        LogUtil.e(TAG, "Download URL not valid");
                        return failure(ERROR_CODE_EMPTY_DOWNLOAD_URL);
                    }

                    return FluentFuture.from(mDownloader.startDownload())
                            .transform(
                                    result -> {
                                        if (result == null || !result) {
                                            LogUtil.i(TAG, String.format(Locale.US,
                                                    "Downloader returned result: %b", result));
                                            return failure(ERROR_CODE_NETWORK_REQUEST_FAILED);
                                        } else {
                                            LogUtil.i(TAG, "Downloader returned result: true");
                                            Data data = new Data.Builder()
                                                    .putString(
                                                        TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY,
                                                        getFileLocation())
                                                    .build();
                                            return Result.success(data);
                                        }
                                    },
                                    MoreExecutors.directExecutor())
                            .catching(
                                    DownloadPackageException.class,
                                    (exception) -> {
                                        LogUtil.e(TAG, String.format(Locale.US,
                                                "Downloader returned DownloadPackageException, "
                                                + "error code %d", exception.getErrorCode()),
                                                exception);
                                        return failure(exception.getErrorCode());
                                    },
                                    MoreExecutors.directExecutor())
                            .catching(
                                    Exception.class,
                                    (exception) -> {
                                        LogUtil.e(TAG, "Downloader returned Exception", exception);
                                        return failure(ERROR_CODE_NETWORK_REQUEST_FAILED);
                                    },
                                    MoreExecutors.directExecutor())
                            .get();
                });
    }

    String getFileLocation() {
        return mFileLocation;
    }
}
