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
import android.net.http.HttpEngine;
import android.net.http.UrlRequest;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.policy.CronetDownloadHandler.DownloadListener;
import com.android.devicelockcontroller.policy.CronetDownloadHandler.DownloadPackageException;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A Cronet based downloader used to download items from a provided URL to a given local location.
 */
public final class CronetDownloader implements Downloader {
    private static final String TAG = "CronetDownloader";

    @Nullable private final String mDownloadUrl;
    private final String mFileLocation;
    private final File mFile;
    private final HttpEngine mHttpEngine;
    private final DownloadRetryPolicy mRetryPolicy;

    private SettableFuture<Boolean> mFuture;
    private final DownloadListener mListener = new CronetDownloadListener();

    private static final int MAX_RETRY_ATTEMPTS = 3;

    CronetDownloader(Context context, @Nullable String downloadUrl, String fileLocation) {
        this(downloadUrl, fileLocation, new HttpEngine.Builder(context).build(),
                new SimpleDownloadRetryPolicy(MAX_RETRY_ATTEMPTS));
    }

    @VisibleForTesting
    CronetDownloader(@Nullable String downloadUrl, String fileLocation, HttpEngine httpEngine,
            DownloadRetryPolicy retryPolicy) {
        mDownloadUrl = downloadUrl;
        mFileLocation = fileLocation;
        mFile = new File(fileLocation);
        mHttpEngine = httpEngine;
        mRetryPolicy = retryPolicy;
    }

    /**
     * Checks if the previous download request is done or not before creating a new {@link
     * UrlRequest}. It returns the same ListenableFuture if not done, otherwise create a new
     * ListenableFuture and {@link UrlRequest}.
     *
     * @return a ListenableFuture which holds the download result.
     */
    @Override
    public ListenableFuture<Boolean> startDownload() {
        if (mFuture != null && !mFuture.isDone()) {
            LogUtil.v(TAG, "startDownload is called but the previous request is not finished yet");
            return mFuture;
        }
        mFuture = SettableFuture.create();
        startDownloadInternal();
        return mFuture;
    }

    private void startDownloadInternal() {
        if (TextUtils.isEmpty(mDownloadUrl)) {
            LogUtil.e(TAG, "The provided URL is null or empty, abort download");
            mFuture.set(false);
            return;
        }
        if (mFile.exists() && !mFile.delete()) {
            LogUtil.e(TAG, "File exists and cannot be deleted, abort download");
            mFuture.setException(
                    new IllegalArgumentException(mFileLocation + " exists and cannot be deleted"));
            return;
        }

        // Start network request
        Executor executor = Executors.newSingleThreadExecutor();
        UrlRequest.Builder requestBuilder =
                mHttpEngine.newUrlRequestBuilder(
                        mDownloadUrl, executor, new CronetDownloadHandler(mFile, mListener));
        UrlRequest request = requestBuilder.build();
        request.start();
    }

    @Override
    public String getFileLocation() {
        return mFileLocation;
    }

    private final class CronetDownloadListener implements DownloadListener {
        @Override
        public void onSuccess() {
            mFuture.set(true);
        }

        @Override
        public void onFailure(DownloadPackageException exception) {
            if (mRetryPolicy.needToRetry()) {
                LogUtil.v(TAG, String.format(Locale.US, "Download file attempt %d",
                        (1 + mRetryPolicy.getCurrentRetryCount())));
                startDownloadInternal();
            } else {
                mFuture.setException(exception);
            }
        }
    }
}
