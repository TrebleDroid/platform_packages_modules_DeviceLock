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

import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_TOO_MANY_REDIRECTS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.http.HttpEngine;
import android.net.http.UrlRequest;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.policy.CronetDownloadHandler.DownloadPackageException;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

@RunWith(RobolectricTestRunner.class)
public class CronetDownloaderTest {
    private static final String TEST_DOWNLOAD_URL = "https://www.example.com";
    private static final String TEST_FILE_LOCATION = "/test/file/location";

    @Rule public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock private DownloadRetryPolicy mMockDownloadRetryPolicy;
    @Mock private HttpEngine mMockHttpEngine;
    @Mock private UrlRequest.Builder mMockUrlRequestBuilder;
    @Mock private UrlRequest mMockUrlRequest;
    @Captor private ArgumentCaptor<CronetDownloadHandler> mDownloadHandlerArgumentCaptor;

    private Context mContext;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();

        when(mMockHttpEngine.newUrlRequestBuilder(
                anyString(), any(Executor.class), any(UrlRequest.Callback.class)))
                .thenReturn(mMockUrlRequestBuilder);
        when(mMockUrlRequestBuilder.build()).thenReturn(mMockUrlRequest);
        // retry is not allowed by default
        when(mMockDownloadRetryPolicy.needToRetry()).thenReturn(false);
    }

    @Test
    public void testStartDownload_CalledMultipleTimes() {
        // GIVEN
        final CronetDownloader downloader =
                new CronetDownloader(
                        TEST_DOWNLOAD_URL, TEST_FILE_LOCATION, mMockHttpEngine,
                        mMockDownloadRetryPolicy);

        // WHEN startDownload is called multiple times when the previous download request is not
        // complete
        final ListenableFuture<Boolean> future = downloader.startDownload();
        final ListenableFuture<Boolean> anotherFuture = downloader.startDownload();

        // THEN will not create new download request
        assertThat(future.isDone()).isFalse();
        assertThat(future).isEqualTo(anotherFuture);

        // WHEN the first download attempt succeed
        CronetDownloadHandler downloadHandler = captureDownloadHandler();
        downloadHandler.mListener.onSuccess();
        assertThat(future.isDone()).isTrue();

        // WHEN startDownload is called after the previous download request succeeds
        final ListenableFuture<Boolean> thirdFuture = downloader.startDownload();

        // THEN a new download request will be created
        assertThat(future).isNotEqualTo(thirdFuture);

        // WHEN call startDownload again
        final ListenableFuture<Boolean> fourthFuture = downloader.startDownload();

        // THEN will not create new download request
        assertThat(thirdFuture.isDone()).isFalse();
        assertThat(thirdFuture).isEqualTo(fourthFuture);

        // WHEN the second download attempt failed
        downloadHandler = mDownloadHandlerArgumentCaptor.getValue();
        downloadHandler.mListener.onFailure(
                new DownloadPackageException(ERROR_CODE_TOO_MANY_REDIRECTS, "Too many redirects"));
        assertThat(thirdFuture.isDone()).isTrue();

        // WHEN startDownload is called after the previous download request fails
        final ListenableFuture<Boolean> fifthFuture = downloader.startDownload();

        // THEN a new download request will be created.
        assertThat(thirdFuture).isNotEqualTo(fifthFuture);
    }

    @Test
    public void testStartDownload_Success() throws Exception {
        // GIVEN
        final CronetDownloader downloader =
                new CronetDownloader(
                        TEST_DOWNLOAD_URL, TEST_FILE_LOCATION, mMockHttpEngine,
                        mMockDownloadRetryPolicy);

        // WHEN
        final ListenableFuture<Boolean> future = downloader.startDownload();

        // THEN
        final CronetDownloadHandler downloadHandler = captureDownloadHandler();

        // WHEN handler returns success
        downloadHandler.mListener.onSuccess();

        // THEN
        assertThat(future.get()).isTrue();
    }

    @Test
    public void testStartDownload_NullDownloadUrl() throws Exception {
        // GIVEN
        final CronetDownloader downloader =
                new CronetDownloader(
                        /* downloadUrl */ null, TEST_FILE_LOCATION, mMockHttpEngine,
                        mMockDownloadRetryPolicy);

        // WHEN
        final ListenableFuture<Boolean> future = downloader.startDownload();

        // THEN
        assertThat(future.isDone()).isTrue();
        assertThat(future.get()).isFalse();
    }

    @Test
    public void testStartDownload_EmptyDownloadUrl() throws Exception {
        // GIVEN
        final CronetDownloader downloader =
                new CronetDownloader(
                        /* downloadUrl */ "", TEST_FILE_LOCATION, mMockHttpEngine,
                        mMockDownloadRetryPolicy);

        // WHEN
        final ListenableFuture<Boolean> future = downloader.startDownload();

        assertThat(future.isDone()).isTrue();
        assertThat(future.get()).isFalse();
    }

    @Test
    public void testStartDownload_CannotRemoveLocalFile() throws Exception {
        // GIVEN the target file path is not deletable
        final String undeletableFilePath = mContext.getFilesDir() + "/"
                + "downloaded_kiosk_app.apk";
        createUndeletableFile(undeletableFilePath);
        final CronetDownloader downloader =
                new CronetDownloader(
                        TEST_DOWNLOAD_URL, undeletableFilePath, mMockHttpEngine,
                        mMockDownloadRetryPolicy);

        // WHEN
        final ListenableFuture<Boolean> future = downloader.startDownload();

        // THEN an exception is thrown
        try {
            future.get();
            fail("Should have thrown an exception");
        } catch (Exception ex) {
            assertThat(ex.getCause().getClass()).isEqualTo(IllegalArgumentException.class);
        }
    }

    @Test
    public void testStartDownload_FirstAttemptFailedRetrySucceed() throws Exception {
        // GIVEN retry is allowed
        when(mMockDownloadRetryPolicy.needToRetry()).thenReturn(true);
        final CronetDownloader downloader =
                new CronetDownloader(
                        TEST_DOWNLOAD_URL, TEST_FILE_LOCATION, mMockHttpEngine,
                        mMockDownloadRetryPolicy);

        // WHEN
        final ListenableFuture<Boolean> future = downloader.startDownload();

        // THEN
        final CronetDownloadHandler downloadHandler = captureDownloadHandler();

        // WHEN handler returns failure for the first attempt
        downloadHandler.mListener.onFailure(
                new DownloadPackageException(ERROR_CODE_TOO_MANY_REDIRECTS, "Too many redirects"));

        // THEN the task is not finished yet
        assertThat(future.isDone()).isFalse();

        // WHEN handler returns success for the second attempt
        downloadHandler.mListener.onSuccess();

        // THEN
        assertThat(future.get()).isTrue();
    }

    private static void createUndeletableFile(String fileLocation) throws IOException {
        // WHEN create a non-empty directory at the expected location
        final File file = new File(fileLocation);
        assertThat(file.mkdirs()).isTrue();
        final File child = new File(file, "child");
        assertThat(child.createNewFile()).isTrue();

        // THEN the file is not deletable
        assertThat(new File(fileLocation).delete()).isFalse();
    }

    private CronetDownloadHandler captureDownloadHandler() {
        verify(mMockHttpEngine)
                .newUrlRequestBuilder(
                        eq(TEST_DOWNLOAD_URL), any(), mDownloadHandlerArgumentCaptor.capture());
        verify(mMockUrlRequest).start();
        return mDownloadHandlerArgumentCaptor.getValue();
    }
}
