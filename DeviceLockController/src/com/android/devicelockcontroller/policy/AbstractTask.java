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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.util.LogUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/** Base class for download, verify and install packages. */
public abstract class AbstractTask extends ListenableWorker {
    private static final String TAG = "AbstractTask";

    static final String TASK_RESULT_ERROR_CODE_KEY = "error-code";
    static final String TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY = "downloaded-file-location";

    // Error code for download package
    static final int ERROR_CODE_EMPTY_DOWNLOAD_URL = 0;
    static final int ERROR_CODE_CREATE_LOCAL_FILE_FAILED = 1;
    static final int ERROR_CODE_NETWORK_REQUEST_FAILED = 2;
    static final int ERROR_CODE_OPEN_FILE_CHANNEL_FAILED = 3;
    static final int ERROR_CODE_CLOSE_FILE_CHANNEL_FAILED = 4;
    static final int ERROR_CODE_NETWORK_REQUEST_CANCELLED = 5;
    static final int ERROR_CODE_TOO_MANY_REDIRECTS = 6;
    // Error code for verify package
    static final int ERROR_CODE_NO_PACKAGE_INFO = 10;
    static final int ERROR_CODE_PACKAGE_NAME_MISMATCH = 11;
    static final int ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH = 12;
    static final int ERROR_CODE_NO_VALID_SIGNING_INFO = 13;
    static final int ERROR_CODE_NO_VALID_DOWNLOADED_FILE = 14;
    static final int ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS = 15;
    // Error code for install package
    static final int ERROR_CODE_CREATE_SESSION_FAILED = 20;
    static final int ERROR_CODE_OPEN_SESSION_FAILED = 21;
    static final int ERROR_CODE_COPY_STREAM_FAILED = 22;
    static final int ERROR_CODE_INSTALLATION_FAILED = 23;
    static final int ERROR_CODE_GET_PENDING_INTENT_FAILED = 24;
    // Error code for cleanup
    static final int ERROR_CODE_DELETE_APK_FAILED = 30;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ERROR_CODE_EMPTY_DOWNLOAD_URL,
            ERROR_CODE_CREATE_LOCAL_FILE_FAILED,
            ERROR_CODE_NETWORK_REQUEST_FAILED,
            ERROR_CODE_OPEN_FILE_CHANNEL_FAILED,
            ERROR_CODE_CLOSE_FILE_CHANNEL_FAILED,
            ERROR_CODE_NETWORK_REQUEST_CANCELLED,
            ERROR_CODE_TOO_MANY_REDIRECTS,
            ERROR_CODE_NO_PACKAGE_INFO,
            ERROR_CODE_PACKAGE_NAME_MISMATCH,
            ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH,
            ERROR_CODE_NO_VALID_SIGNING_INFO,
            ERROR_CODE_NO_VALID_DOWNLOADED_FILE,
            ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS,
            ERROR_CODE_CREATE_SESSION_FAILED,
            ERROR_CODE_OPEN_SESSION_FAILED,
            ERROR_CODE_COPY_STREAM_FAILED,
            ERROR_CODE_INSTALLATION_FAILED,
            ERROR_CODE_GET_PENDING_INTENT_FAILED,
            ERROR_CODE_DELETE_APK_FAILED
    })
    @interface ErrorCode {}

    public AbstractTask(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    /**
     * Creates a {@link androidx.work.ListenableWorker.Result.Failure} result and puts the error
     * code in its output {@link Data} with the key {@link #TASK_RESULT_ERROR_CODE_KEY}.
     *
     * @param errorCode the error code that will be included in the result, it should be one of the
     *     values defined in {@link ErrorCode}.
     * @return a Failure result which contains error code.
     */
    static Result failure(@ErrorCode int errorCode) {
        LogUtil.e(TAG, String.format(Locale.US, "Task failed with error code %d", errorCode));
        Data data = new Data.Builder().putInt(TASK_RESULT_ERROR_CODE_KEY, errorCode).build();
        return Result.failure(data);
    }
}
