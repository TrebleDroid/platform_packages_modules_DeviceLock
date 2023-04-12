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

import static android.content.pm.PackageInstaller.EXTRA_STATUS;
import static android.content.pm.PackageInstaller.STATUS_FAILURE;
import static android.content.pm.PackageInstaller.STATUS_SUCCESS;
import static android.util.Log.VERBOSE;

import static androidx.work.WorkInfo.State.FAILED;
import static androidx.work.WorkInfo.State.SUCCEEDED;

import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_COPY_STREAM_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_CREATE_SESSION_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_GET_PENDING_INTENT_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_VALID_DOWNLOADED_FILE;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_OPEN_SESSION_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_ERROR_CODE_KEY;
import static com.android.devicelockcontroller.policy.InstallPackageTask.ACTION_INSTALL_APP_COMPLETE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller.Session;
import android.content.pm.PackageInstaller.SessionParams;
import android.os.Looper;

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

import com.android.devicelockcontroller.policy.InstallPackageTask.InstallPackageCompleteBroadcastReceiver;
import com.android.devicelockcontroller.policy.InstallPackageTask.PackageInstallPendingIntentProvider;
import com.android.devicelockcontroller.policy.InstallPackageTask.PackageInstallPendingIntentProviderImpl;
import com.android.devicelockcontroller.policy.InstallPackageTask.PackageInstallerWrapper;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowContextWrapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;

@RunWith(RobolectricTestRunner.class)
public final class InstallPackageTaskTest {
    private static final int TEST_INSTALL_SESSION_ID = 1;
    private static final byte[] TEST_PACKAGE_CONTENT = new byte[]{1, 2, 3, 4, 5};

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private PackageInstallerWrapper mMockPackageInstaller;
    @Mock
    private Session mMockSession;
    @Mock
    private PackageInstallPendingIntentProvider mMockPackageInstallPendingIntentProvider;

    private Context mContext;
    private WorkManager mWorkManager;
    private OutputStream mOutputStream;
    private final InstallPackageCompleteBroadcastReceiver mFakeBroadcastReceiver =
            new InstallPackageCompleteBroadcastReceiver();
    private ShadowApplication mShadowApplication;
    private String mFileLocation;

    @Before
    public void setup() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mShadowApplication = Shadows.shadowOf((Application) mContext);

        mFileLocation = mContext.getFilesDir() + "/TEST_FILE_NAME";
        createTestFile();
        when(mMockPackageInstaller.createSession(any(SessionParams.class)))
                .thenReturn(TEST_INSTALL_SESSION_ID);
        when(mMockPackageInstaller.openSession(TEST_INSTALL_SESSION_ID)).thenReturn(mMockSession);

        mOutputStream = new ByteArrayOutputStream();
        when(mMockSession.openWrite(anyString(), anyLong(), anyLong())).thenReturn(mOutputStream);
        when(mMockPackageInstallPendingIntentProvider.get(anyInt())).thenReturn(
                new PackageInstallPendingIntentProviderImpl(mContext).get(TEST_INSTALL_SESSION_ID));

        final Configuration config =
                new Configuration.Builder()
                        .setMinimumLoggingLevel(VERBOSE)
                        .setWorkerFactory(
                                new WorkerFactory() {
                                    @Override
                                    public ListenableWorker createWorker(
                                            Context context, String workerClassName,
                                            WorkerParameters workerParameters) {
                                        return new InstallPackageTask(
                                                context,
                                                workerParameters,
                                                MoreExecutors.newDirectExecutorService(),
                                                mFakeBroadcastReceiver,
                                                mMockPackageInstaller,
                                                mMockPackageInstallPendingIntentProvider);
                                    }
                                })
                        .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext, config);
        mWorkManager = WorkManager.getInstance(mContext);
    }

    @Test
    public void testInstallPackageCompleteBroadcastReceiver_StatusSuccess()
            throws ExecutionException, InterruptedException {
        // WHEN register the receiver and send an installation success broadcast
        registerReceiverWithStatus(STATUS_SUCCESS);

        // THEN the receiver should be unregistered
        assertThat(mShadowApplication.getRegisteredReceivers()).isEmpty();
        assertThat(mFakeBroadcastReceiver.getFuture().isDone()).isTrue();
        assertThat(mFakeBroadcastReceiver.getFuture().get()).isTrue();
    }

    @Test
    public void testInstallPackageCompleteBroadcastReceiver_STATUS_FAILURE()
            throws ExecutionException, InterruptedException {
        // WHEN an installation failure broadcast is sent
        registerReceiverWithStatus(STATUS_FAILURE);

        // THEN the BroadcastReceiver should be unregistered
        assertThat(mShadowApplication.getRegisteredReceivers()).isEmpty();
        assertThat(mFakeBroadcastReceiver.getFuture().isDone()).isTrue();
        assertThat(mFakeBroadcastReceiver.getFuture().get()).isFalse();
    }

    @Test
    public void testInstall_DownloadedFilePathIsNull() {
        // GIVEN the file location is null
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, /* fileLocation */ null);

        // THEN task failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
    }

    @Test
    public void testInstall_DownloadedFilePathIsEmpty() {
        // GIVEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, /* fileLocation */ "");

        // THEN task failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
    }

    @Test
    public void testInstall_Succeed() {
        // GIVEN
        mShadowApplication.clearRegisteredReceivers();
        // GIVEN the installation completes with success
        mFakeBroadcastReceiver.mFuture.set(true);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        // THEN
        verifyPackageInstalled();
        assertThat(workInfo.getState()).isEqualTo(SUCCEEDED);
    }

    @Test
    public void testInstall_BroadcastReceiverReturnedFalse_Failed() {
        // GIVEN
        mShadowApplication.clearRegisteredReceivers();
        // GIVEN the installation completes with failure
        mFakeBroadcastReceiver.mFuture.set(false);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        // THEN
        verifyPackageInstalled();
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_INSTALLATION_FAILED);
    }

    @Test
    public void testInstall_BroadcastReceiverReturnedNull_Failed() {
        // GIVEN;
        mShadowApplication.clearRegisteredReceivers();
        // GIVEN the installation completes with failure
        mFakeBroadcastReceiver.mFuture.set(null);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        // THEN
        verifyPackageInstalled();
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_INSTALLATION_FAILED);
    }

    @Test
    public void testInstall_CannotCreateSession() throws IOException {
        // GIVEN an exception is threw when creating session
        doThrow(new IOException("Cannot create session"))
                .when(mMockPackageInstaller)
                .createSession(any());

        // WHEN run install package task
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        // THEN
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_CREATE_SESSION_FAILED);
    }

    @Test
    public void testInstall_CannotOpenSession() throws IOException {
        // GIVEN an exception is threw when opening session
        doThrow(new IOException("Cannot open session"))
                .when(mMockPackageInstaller)
                .openSession(anyInt());

        // WHEN run install package task
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        // THEN
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_OPEN_SESSION_FAILED);
    }

    @Test
    public void testInstall_CannotFindPackage() {
        // GIVEN delete the created file
        File file = new File(mFileLocation);
        assertThat(file.delete()).isTrue();

        // WHEN run install package task
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        // THEN
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_COPY_STREAM_FAILED);
    }

    @Test
    public void testInstall_WriteSessionFailed() throws IOException {
        // GIVEN an exception is threw when writing session
        doThrow(new IOException("Write session failed"))
                .when(mMockSession)
                .openWrite(notNull(), anyLong(), anyLong());

        // WHEN run install package task
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        // THEN
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_COPY_STREAM_FAILED);
    }

    @Test
    public void testInstall_withNullPendingIntent_fails() {
        when(mMockPackageInstallPendingIntentProvider.get(anyInt())).thenReturn(null);

        // WHEN run install package task
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, mFileLocation);

        // THEN
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_GET_PENDING_INTENT_FAILED);
    }

    private void verifyPackageInstalled() {
        // THEN a BroadcastReceiver should be registered
        assertThat(mShadowApplication.getRegisteredReceivers()).hasSize(1);
        assertThat(mShadowApplication.getRegisteredReceivers().get(0).getBroadcastReceiver())
                .isEqualTo(mFakeBroadcastReceiver);

        // THEN the contents must be same
        final byte[] content = ((ByteArrayOutputStream) mOutputStream).toByteArray();
        assertThat(TEST_PACKAGE_CONTENT).isEqualTo(content);

        // THEN a status receiver is sent
        verify(mMockSession).commit(any(IntentSender.class));
    }

    private static WorkInfo buildTaskAndRun(WorkManager workManager, String fileLocation) {
        // GIVEN
        final Data inputData =
                new Data.Builder()
                        .putString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY, fileLocation)
                        .build();

        // WHEN
        final OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(InstallPackageTask.class).setInputData(inputData)
                        .build();
        workManager.enqueue(request);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        try {
            return workManager.getWorkInfoById(request.getId()).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new AssertionError("Exception", e);
        }
    }

    private void registerReceiverWithStatus(int status) {
        // GIVEN clear BroadcastReceivers and Broadcast intents
        mShadowApplication.clearRegisteredReceivers();
        final ShadowContextWrapper shadowContextWrapper =
                Shadows.shadowOf((ContextWrapper) mContext);
        shadowContextWrapper.clearBroadcastIntents();

        // WHEN
        mContext.registerReceiver(mFakeBroadcastReceiver,
                new IntentFilter(ACTION_INSTALL_APP_COMPLETE));

        // THEN
        assertThat(mShadowApplication.getRegisteredReceivers()).hasSize(1);
        assertThat(mShadowApplication.getRegisteredReceivers().get(0).getBroadcastReceiver())
                .isEqualTo(mFakeBroadcastReceiver);

        // WHEN send intent with status
        final Intent intent = new Intent(ACTION_INSTALL_APP_COMPLETE);
        intent.putExtra(EXTRA_STATUS, status);
        mContext.sendBroadcast(intent);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // THEN make sure broadcast intent has correct contents
        assertThat(shadowContextWrapper.getBroadcastIntents()).hasSize(1);
        final Intent receivedIntent = shadowContextWrapper.getBroadcastIntents().get(0);
        assertThat(receivedIntent.getAction()).isEqualTo(ACTION_INSTALL_APP_COMPLETE);
        assertThat(receivedIntent.getExtras().getInt(EXTRA_STATUS)).isEqualTo(status);
    }

    private void createTestFile() {
        try (FileOutputStream outputStream = new FileOutputStream(mFileLocation)) {
            outputStream.write(TEST_PACKAGE_CONTENT);
        } catch (IOException e) {
            throw new AssertionError("Exception", e);
        }
    }
}
