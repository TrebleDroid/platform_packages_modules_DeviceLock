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

import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_CLOSE_FILE_CHANNEL_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NETWORK_REQUEST_CANCELLED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NETWORK_REQUEST_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_OPEN_FILE_CHANNEL_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_TOO_MANY_REDIRECTS;

import android.net.http.HttpException;
import android.net.http.UrlRequest;
import android.net.http.UrlResponseInfo;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.policy.AbstractTask.ErrorCode;
import com.android.devicelockcontroller.util.LogUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

/** The callback which handles the response of the download request. */
public class CronetDownloadHandler extends UrlRequest.Callback {
    private static final String TAG = "CronetDownloadHandler";

    private static final int BYTE_BUFFER_SIZE = 32 * 1024;
    private static final int MAX_REDIRECT = 4;

    private final File mDownloadedFile;
    @VisibleForTesting final DownloadListener mListener;
    private int mRedirectCount;
    private FileChannel mFileChannel;

    CronetDownloadHandler(File file, DownloadListener listener) {
        mDownloadedFile = file;
        mListener = listener;
    }

    @Override
    public void onRedirectReceived(
            UrlRequest request, UrlResponseInfo responseInfo, String newLocationUrl)
            throws DownloadPackageException {
        LogUtil.v(TAG, String.format(Locale.US, "Follow redirect to %s", newLocationUrl));
        if (mRedirectCount < MAX_REDIRECT) {
            mRedirectCount++;
            request.followRedirect();
        } else {
            throw new DownloadPackageException(ERROR_CODE_TOO_MANY_REDIRECTS, "Too many redirects");
        }
    }

    @Override
    public void onResponseStarted(UrlRequest request, UrlResponseInfo responseInfo)
            throws DownloadPackageException {
        int httpStatusCode = responseInfo.getHttpStatusCode();
        // status code 2XX will be considered as success
        if (httpStatusCode > 299 || httpStatusCode < 200) {
            throw new DownloadPackageException(
                    ERROR_CODE_NETWORK_REQUEST_FAILED,
                    String.format("Network request failed with status code %d", httpStatusCode));
        }
        request.read(ByteBuffer.allocateDirect(BYTE_BUFFER_SIZE));
    }

    @Override
    public void onReadCompleted(UrlRequest request, UrlResponseInfo responseInfo,
            ByteBuffer byteBuffer) throws IOException, DownloadPackageException {
        if (mFileChannel == null) {
            try {
                mFileChannel = new FileOutputStream(mDownloadedFile).getChannel();
            } catch (FileNotFoundException e) {
                throw new DownloadPackageException(ERROR_CODE_OPEN_FILE_CHANNEL_FAILED,
                        e.getMessage(), e);
            }
        }
        byteBuffer.flip();
        mFileChannel.write(byteBuffer);
        byteBuffer.clear();
        request.read(byteBuffer);
    }

    @Override
    public void onSucceeded(UrlRequest request, UrlResponseInfo responseInfo) {
        cleanupAndNotifyDownloadResult(null /*exception*/);
    }

    @Override
    public void onFailed(UrlRequest request, UrlResponseInfo responseInfo, HttpException e) {
        LogUtil.e(TAG, "Download kiosk app failed", e);
        if (e.getCause() instanceof DownloadPackageException) {
            cleanupAndNotifyDownloadResult((DownloadPackageException) e.getCause());
        } else {
            cleanupAndNotifyDownloadResult(
                    new DownloadPackageException(ERROR_CODE_NETWORK_REQUEST_FAILED, e.getMessage(),
                            e));
        }
    }

    @Override
    public void onCanceled(UrlRequest request, UrlResponseInfo responseInfo) {
        LogUtil.i(TAG, "Download kiosk app is canceled");
        cleanupAndNotifyDownloadResult(
                new DownloadPackageException(ERROR_CODE_NETWORK_REQUEST_CANCELLED,
                        "Request cancelled"));
    }

    /**
     * Close the FileChannel and notify the download request is succeed or not.
     *
     * @param exception reason why the request failed, null if the request is succeed
     */
    private void cleanupAndNotifyDownloadResult(@Nullable DownloadPackageException exception) {
        if (mFileChannel == null) {
            if (exception == null) {
                // This could happen when onSucceeded is invoked right after onResponseStarted,
                // no responses have been received in this case.
                LogUtil.e(TAG, "Download kiosk app - onSucceeded is called, "
                        + "but file channel is null");
                mListener.onFailure(
                        new DownloadPackageException(
                                ERROR_CODE_NETWORK_REQUEST_FAILED, "No responses received"));
            } else {
                mListener.onFailure(exception);
            }
            return;
        }
        try {
            mFileChannel.close();
        } catch (IOException e) {
            LogUtil.e(TAG, "Failed to close file channel", e);
            mListener.onFailure(
                    new DownloadPackageException(ERROR_CODE_CLOSE_FILE_CHANNEL_FAILED,
                            e.getMessage(), e));
            return;
        }
        if (exception == null) {
            LogUtil.i(TAG, "Download kiosk app succeed");
            mListener.onSuccess();
        } else {
            mListener.onFailure(exception);
        }
    }

    /** An exception thrown during the process of handling responses of the download request. */
    static final class DownloadPackageException extends Exception {

        private final int mErrorCode;

        DownloadPackageException(@ErrorCode int errorCode, @Nullable String message) {
            this(errorCode, message, null /*cause*/);
        }

        DownloadPackageException(
                @ErrorCode int errorCode, @Nullable String message, @Nullable Throwable cause) {
            super(message + String.format(Locale.US, " error code %d reported", errorCode), cause);
            this.mErrorCode = errorCode;
        }

        @ErrorCode
        int getErrorCode() {
            return mErrorCode;
        }
    }

    /** An interface for listening the result of handling responses of the download request. */
    interface DownloadListener {

        /** This will be called when all responses are read successfully. */
        void onSuccess();

        /**
         * This will be called when there was an error when handling responses.
         *
         * @param exception contains the detailed explanation of the error.
         */
        void onFailure(DownloadPackageException exception);
    }
}
