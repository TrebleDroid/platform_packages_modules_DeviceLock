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

import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NETWORK_REQUEST_CANCELLED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NETWORK_REQUEST_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_OPEN_FILE_CHANNEL_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_TOO_MANY_REDIRECTS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.http.HttpException;
import android.net.http.UrlRequest;
import android.net.http.UrlResponseInfo;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.policy.CronetDownloadHandler.DownloadListener;
import com.android.devicelockcontroller.policy.CronetDownloadHandler.DownloadPackageException;

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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Random;

@RunWith(RobolectricTestRunner.class)
public class CronetDownloadHandlerTest {
    private static final String TEST_FILE_PATH = "/test/file/path";
    private static final int TEST_SUCCESS_HTTP_STATUS_CODE = 200;
    private static final int TEST_FAILURE_HTTP_STATUS_CODE = 404;

    @Rule public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock private File mMockFile;
    @Mock private DownloadListener mMockDownloadListener;
    @Mock private UrlRequest mMockUrlRequest;
    @Mock private UrlResponseInfo mMockUrlResponseInfo;
    @Captor private ArgumentCaptor<DownloadPackageException>
            mDownloadPackageExceptionArgumentCaptor;

    private Context mContext;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        when(mMockFile.getPath()).thenReturn(TEST_FILE_PATH);
        // status code is success by default
        when(mMockUrlResponseInfo.getHttpStatusCode()).thenReturn(TEST_SUCCESS_HTTP_STATUS_CODE);
    }

    @Test
    public void testOnRedirectReceived_Success() throws DownloadPackageException {
        // GIVEN
        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(mMockFile, mMockDownloadListener);

        // WHEN
        cronetDownloadHandler.onRedirectReceived(
                mMockUrlRequest, mMockUrlResponseInfo, "TEST_REDIRECT_URL");

        // THEN
        verify(mMockUrlRequest).followRedirect();
    }

    @Test
    public void testOnRedirectReceived_TooManyRedirects() throws DownloadPackageException {
        // GIVEN
        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(mMockFile, mMockDownloadListener);

        // WHEN
        cronetDownloadHandler.onRedirectReceived(
                mMockUrlRequest, mMockUrlResponseInfo, "TEST_REDIRECT_URL_1");
        cronetDownloadHandler.onRedirectReceived(
                mMockUrlRequest, mMockUrlResponseInfo, "TEST_REDIRECT_URL_2");
        cronetDownloadHandler.onRedirectReceived(
                mMockUrlRequest, mMockUrlResponseInfo, "TEST_REDIRECT_URL_3");
        cronetDownloadHandler.onRedirectReceived(
                mMockUrlRequest, mMockUrlResponseInfo, "TEST_REDIRECT_URL_4");

        // THEN
        final DownloadPackageException expectedException =
                assertThrows(
                        DownloadPackageException.class,
                        () -> cronetDownloadHandler.onRedirectReceived(
                                mMockUrlRequest, mMockUrlResponseInfo, "TEST_REDIRECT_URL_5"));

        // THEN an exception should be thrown
        assertThat(expectedException.getErrorCode()).isEqualTo(ERROR_CODE_TOO_MANY_REDIRECTS);
    }

    @Test
    public void testOnResponseStarted_Success() throws DownloadPackageException {
        // GIVEN
        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(mMockFile, mMockDownloadListener);

        // WHEN
        cronetDownloadHandler.onResponseStarted(mMockUrlRequest, mMockUrlResponseInfo);

        // THEN
        verify(mMockUrlRequest).read(any());
    }

    @Test
    public void testOnResponseStarted_ErrorStatusCode() {
        // GIVEN
        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(mMockFile, mMockDownloadListener);
        when(mMockUrlResponseInfo.getHttpStatusCode()).thenReturn(TEST_FAILURE_HTTP_STATUS_CODE);

        // WHEN
        final DownloadPackageException expectedException =
                assertThrows(
                        DownloadPackageException.class,
                        () -> cronetDownloadHandler.onResponseStarted(mMockUrlRequest,
                                mMockUrlResponseInfo));

        // THEN an exception should be thrown
        assertThat(expectedException.getErrorCode()).isEqualTo(ERROR_CODE_NETWORK_REQUEST_FAILED);
    }

    @Test
    public void testOnReadCompleted_Success() throws DownloadPackageException, IOException {
        // GIVEN
        final String fileLocation = mContext.getFilesDir() + "/TEST_FILE_NAME";
        final File file = createFile(fileLocation);

        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(file, mMockDownloadListener);

        // GIVEN some data
        final int expectedCapacity = 10;
        final ByteBuffer expectedContent = ByteBuffer.allocate(expectedCapacity);
        expectedContent.put(generateRandomByteArray(expectedCapacity));

        // WHEN
        cronetDownloadHandler.onReadCompleted(mMockUrlRequest, mMockUrlResponseInfo,
                expectedContent);

        // THEN the ByteBuffer is cleared after read
        assertThat(expectedContent.limit()).isEqualTo(expectedCapacity);
        assertThat(expectedContent.position()).isEqualTo(0);
        verify(mMockUrlRequest).read(any());
        verifyDownloadedFile(expectedContent.array(), fileLocation);
    }

    @Test
    public void testOnReadCompleted_MultipleRead_Success()
            throws IOException, DownloadPackageException {
        // GIVEN
        final String fileLocation = mContext.getFilesDir() + "/TEST_FILE_NAME";
        final File file = createFile(fileLocation);

        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(file, mMockDownloadListener);

        // GIVEN a large response data
        final int expectedChunk1Capacity = 32 * 1024;
        final ByteBuffer expectedChunk1 = ByteBuffer.allocate(expectedChunk1Capacity);
        expectedChunk1.put(generateRandomByteArray(expectedChunk1Capacity));

        final int expectedChunk2Capacity = 1024;
        final ByteBuffer expectedChunk2 = ByteBuffer.allocate(expectedChunk2Capacity);
        expectedChunk2.put(generateRandomByteArray(expectedChunk2Capacity));

        final int length = expectedChunk1Capacity + expectedChunk2Capacity;
        final byte[] bytes1 = expectedChunk1.array();
        final byte[] bytes2 = expectedChunk2.array();
        final byte[] bytes = Arrays.copyOf(bytes1, length);
        System.arraycopy(bytes2, 0, bytes, bytes1.length, bytes2.length);

        // WHEN
        cronetDownloadHandler.onReadCompleted(mMockUrlRequest, mMockUrlResponseInfo,
                expectedChunk1);
        cronetDownloadHandler.onReadCompleted(mMockUrlRequest, mMockUrlResponseInfo,
                expectedChunk2);

        // THEN the ByteBuffer is cleared after read
        assertThat(expectedChunk1.limit()).isEqualTo(expectedChunk1Capacity);
        assertThat(expectedChunk1.position()).isEqualTo(0);
        assertThat(expectedChunk2.limit()).isEqualTo(expectedChunk2Capacity);
        assertThat(expectedChunk2.position()).isEqualTo(0);
        verify(mMockUrlRequest, times(2)).read(any());
        verifyDownloadedFile(bytes, fileLocation);
    }

    @Test
    public void testOnReadCompleted_CannotOpenFileChannel() throws IOException {
        // GIVEN the file is read only
        final File file = createFile(mContext.getFilesDir() + "/TEST_FILE_NAME");
        assertThat(file.setReadOnly()).isTrue();

        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(file, mMockDownloadListener);

        // WHEN
        final DownloadPackageException expectedException =
                assertThrows(
                        DownloadPackageException.class,
                        () -> cronetDownloadHandler.onReadCompleted(mMockUrlRequest,
                                mMockUrlResponseInfo, ByteBuffer.allocate(0)));

        // THEN an exception should be thrown
        assertThat(expectedException.getErrorCode()).isEqualTo(ERROR_CODE_OPEN_FILE_CHANNEL_FAILED);
    }

    @Test
    public void testOnSucceed_EmptyResponse() throws DownloadPackageException {
        // GIVEN
        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(mMockFile, mMockDownloadListener);

        // WHEN no response is read
        cronetDownloadHandler.onResponseStarted(mMockUrlRequest, mMockUrlResponseInfo);
        cronetDownloadHandler.onSucceeded(mMockUrlRequest, mMockUrlResponseInfo);

        // THEN
        verify(mMockDownloadListener).onFailure(mDownloadPackageExceptionArgumentCaptor.capture());
        final DownloadPackageException exception =
                mDownloadPackageExceptionArgumentCaptor.getValue();
        assertThat(exception.getErrorCode()).isEqualTo(ERROR_CODE_NETWORK_REQUEST_FAILED);
    }

    @Test
    public void testOnSucceed_Success() throws DownloadPackageException, IOException {
        // GIVEN
        final String fileLocation = mContext.getFilesDir() + "/TEST_FILE_NAME";
        final File file = createFile(fileLocation);

        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(file, mMockDownloadListener);

        // GIVEN some data
        final int length = 10;
        final ByteBuffer expectedContent = ByteBuffer.allocate(length);
        expectedContent.put(generateRandomByteArray(length));

        // WHEN
        cronetDownloadHandler.onResponseStarted(mMockUrlRequest, mMockUrlResponseInfo);
        cronetDownloadHandler.onReadCompleted(mMockUrlRequest, mMockUrlResponseInfo,
                expectedContent);
        cronetDownloadHandler.onSucceeded(mMockUrlRequest, mMockUrlResponseInfo);

        // THEN
        verify(mMockDownloadListener).onSuccess();
    }

    @Test
    public void testOnFailed_CausedByDownloadPackageException() {
        // GIVEN
        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(mMockFile, mMockDownloadListener);

        // WHEN the request failed with DownloadPackageException as the root cause
        final int expectedErrorCode = ERROR_CODE_OPEN_FILE_CHANNEL_FAILED;
        cronetDownloadHandler.onFailed(
                mMockUrlRequest,
                mMockUrlResponseInfo,
                new HttpException(
                        "Network request failed",
                        new DownloadPackageException(expectedErrorCode, "TEST MESSAGE")));

        verify(mMockDownloadListener).onFailure(mDownloadPackageExceptionArgumentCaptor.capture());
        final DownloadPackageException exception =
                mDownloadPackageExceptionArgumentCaptor.getValue();
        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);
    }

    @Test
    public void testOnFailed_CausedByOtherException() {
        // GIVEN
        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(mMockFile, mMockDownloadListener);

        // WHEN
        cronetDownloadHandler.onFailed(
                mMockUrlRequest,
                mMockUrlResponseInfo,
                new HttpException(
                        "Network request failed", new NullPointerException("Mock exception")));

        // THEN
        verify(mMockDownloadListener).onFailure(mDownloadPackageExceptionArgumentCaptor.capture());
        final DownloadPackageException exception =
                mDownloadPackageExceptionArgumentCaptor.getValue();
        assertThat(exception.getErrorCode()).isEqualTo(ERROR_CODE_NETWORK_REQUEST_FAILED);
    }

    @Test
    public void testOnCanceled_Success() {
        // GIVEN
        final CronetDownloadHandler cronetDownloadHandler =
                new CronetDownloadHandler(mMockFile, mMockDownloadListener);

        cronetDownloadHandler.onCanceled(mMockUrlRequest, mMockUrlResponseInfo);

        // THEN
        verify(mMockDownloadListener).onFailure(mDownloadPackageExceptionArgumentCaptor.capture());
        final DownloadPackageException exception =
                mDownloadPackageExceptionArgumentCaptor.getValue();
        assertThat(exception.getErrorCode()).isEqualTo(ERROR_CODE_NETWORK_REQUEST_CANCELLED);
    }

    private static File createFile(String fileLocation) throws IOException {
        final File file = new File(fileLocation);
        if (!file.exists()) {
            assertThat(file.createNewFile()).isTrue();
        }
        return file;
    }

    private static byte[] generateRandomByteArray(int length) {
        final byte[] array = new byte[length];
        final Random random = new Random();
        random.nextBytes(array);
        return array;
    }

    private static void verifyDownloadedFile(byte[] expectedContent, String fileLocation)
            throws IOException {
        final File downloadedFile = new File(fileLocation);
        assertThat(downloadedFile.exists()).isTrue();

        final ByteBuffer byteBuffer = ByteBuffer.allocate(expectedContent.length);
        final FileChannel fileChannel = new FileInputStream(downloadedFile).getChannel();
        fileChannel.read(byteBuffer);
        assertThat(byteBuffer.array()).isEqualTo(expectedContent);
    }
}
