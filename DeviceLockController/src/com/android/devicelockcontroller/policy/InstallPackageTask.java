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

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.Session;
import android.content.pm.PackageInstaller.SessionParams;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Install the apk from {@code getInputData().getString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY)}.
 */
public final class InstallPackageTask extends AbstractTask {
    private static final String TAG = "InstallPackageTask";

    @VisibleForTesting
    static final String ACTION_INSTALL_APP_COMPLETE =
            "com.android.devicelockcontroller.policy.ACTION_INSTALL_APP_COMPLETE";

    private static final String INSTALLATION_SESSION_FILE_NAME = "INSTALLATION_SESSION_FILE";

    private final Context mContext;
    private final ListeningExecutorService mExecutorService;
    private final InstallPackageCompleteBroadcastReceiver mBroadcastReceiver;
    private final PackageInstallerWrapper mPackageInstaller;
    private final PackageInstallPendingIntentProvider mPackageInstallPendingIntentProvider;

    public InstallPackageTask(Context context, WorkerParameters workerParameters,
            ListeningExecutorService executorService) {
        this(context, workerParameters, executorService,
                new InstallPackageCompleteBroadcastReceiver(),
                new PackageInstallerWrapper(context.getPackageManager().getPackageInstaller()),
                new PackageInstallPendingIntentProviderImpl(context));
    }

    @VisibleForTesting
    InstallPackageTask(Context context, WorkerParameters workerParameters,
            ListeningExecutorService executorService,
            InstallPackageCompleteBroadcastReceiver broadcastReceiver,
            PackageInstallerWrapper packageInstaller,
            PackageInstallPendingIntentProvider packageInstallPendingIntentProvider) {
        super(context, workerParameters);

        mContext = context;
        mExecutorService = executorService;
        mBroadcastReceiver = broadcastReceiver;
        mPackageInstaller = packageInstaller;
        mPackageInstallPendingIntentProvider = packageInstallPendingIntentProvider;
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return mExecutorService.submit(
                () -> {
                    LogUtil.i(TAG, "Starts to run");
                    final String fileLocation =
                            getInputData().getString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY);
                    if (TextUtils.isEmpty(fileLocation)) {
                        LogUtil.e(TAG, "The downloaded file path is null or empty");
                        return failure(ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
                    }
                    final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);

                    int sessionId;
                    try {
                        sessionId = mPackageInstaller.createSession(params);
                    } catch (IOException e) {
                        LogUtil.e(TAG, "Failed to create installation session", e);
                        return failure(ERROR_CODE_CREATE_SESSION_FAILED);
                    }

                    try (Session session = mPackageInstaller.openSession(sessionId)) {
                        try (InputStream inputStream =
                                     new BufferedInputStream(new FileInputStream(fileLocation));
                             OutputStream outputStream =
                                     session.openWrite(INSTALLATION_SESSION_FILE_NAME,
                                             0 /*offsetBytes*/, -1 /*lengthBytes*/)) {
                            ByteStreams.copy(inputStream, outputStream);
                        } catch (IOException e) {
                            LogUtil.e(TAG, String.format(Locale.US, "Failed to write %s from %s",
                                    INSTALLATION_SESSION_FILE_NAME, fileLocation), e);
                            mPackageInstaller.abandonSession(sessionId);
                            return failure(ERROR_CODE_COPY_STREAM_FAILED);
                        }

                        mContext.registerReceiver(mBroadcastReceiver,
                                new IntentFilter(ACTION_INSTALL_APP_COMPLETE),
                                Context.RECEIVER_NOT_EXPORTED);

                        final PendingIntent pendingIntent =
                                mPackageInstallPendingIntentProvider.get(sessionId);
                        if (pendingIntent != null) {
                            session.commit(pendingIntent.getIntentSender());
                        } else {
                            LogUtil.e(TAG, "Unable to get pending intent");
                            mPackageInstaller.abandonSession(sessionId);
                            return failure(ERROR_CODE_GET_PENDING_INTENT_FAILED);
                        }
                        return FluentFuture
                                .from(mBroadcastReceiver.getFuture())
                                .transform(success -> {
                                    if (success == null || !success) {
                                        LogUtil.e(TAG, String.format(Locale.US,
                                                "InstallPackageCompleteBroadcastReceiver "
                                                        + "returned result: %b", success));
                                        return failure(ERROR_CODE_INSTALLATION_FAILED);
                                    } else {
                                        LogUtil.i(TAG,
                                                "InstallPackageCompleteBroadcastReceiver "
                                                        + "returned result: true");
                                        // need to pass the file location to next task
                                        final Data data = new Data.Builder().putString(
                                                TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY,
                                                fileLocation).build();
                                        return Result.success(data);
                                    }
                                }, MoreExecutors.directExecutor()).get();
                    } catch (IOException e) {
                        LogUtil.e(TAG, "Open session failed", e);
                        return failure(ERROR_CODE_OPEN_SESSION_FAILED);
                    }
                });
    }

    /** Provides a pending intent for a given sessionId from PackageInstaller. */
    interface PackageInstallPendingIntentProvider {
        /**
         * Returns a pending intent for a given sessionId from PackageInstaller.
         *
         * @param sessionId The unique ID that represents the created session from PackageInstaller.
         */
        @Nullable
        PendingIntent get(int sessionId);
    }

    /** Default implementation which returns pending intent for package install. */
    static final class PackageInstallPendingIntentProviderImpl
            implements PackageInstallPendingIntentProvider {
        private final Context mContext;

        PackageInstallPendingIntentProviderImpl(Context context) {
            mContext = context;
        }

        @Nullable
        @Override
        public PendingIntent get(int sessionId) {
            // In general, a PendingIntent is preferred to be immutable, but FLAG_MUTABLE is used
            // here to let PackageInstaller pass the app installation status back.
            return PendingIntent.getBroadcast(mContext, sessionId,
                    new Intent(ACTION_INSTALL_APP_COMPLETE).setPackage(mContext.getPackageName()),
                    FLAG_MUTABLE | FLAG_ONE_SHOT | FLAG_UPDATE_CURRENT);
        }
    }

    /**
     * A broadcast receiver which handles the broadcast intent when package installation is
     * complete.
     * The broadcast receiver will use the {@link PackageInstaller#EXTRA_STATUS} field to determine
     * if the installation is successful or not.
     */
    static final class InstallPackageCompleteBroadcastReceiver extends BroadcastReceiver {
        @VisibleForTesting final SettableFuture<Boolean> mFuture = SettableFuture.create();

        @Override
        public void onReceive(Context context, Intent intent) {
            final int status =
                    intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE);

            context.unregisterReceiver(this);
            if (status == PackageInstaller.STATUS_SUCCESS) {
                LogUtil.i(TAG, "Package installation succeed");
                mFuture.set(true);
            } else {
                LogUtil.e(TAG, String.format(Locale.US,
                        "Package installation failed: status= %d, status message= %s",
                        status, intent.getStringExtra(EXTRA_STATUS_MESSAGE)));
                mFuture.set(false);
            }
        }

        ListenableFuture<Boolean> getFuture() {
            return mFuture;
        }
    }

    /**
     * Wrapper for {@link PackageInstaller}, used for testing purpose, especially for failure
     * testing.
     */
    static class PackageInstallerWrapper {
        private final PackageInstaller mPackageInstaller;

        PackageInstallerWrapper(PackageInstaller packageInstaller) {
            mPackageInstaller = packageInstaller;
        }

        int createSession(SessionParams params) throws IOException {
            return mPackageInstaller.createSession(params);
        }

        Session openSession(int sessionId) throws IOException {
            return mPackageInstaller.openSession(sessionId);
        }

        void abandonSession(int sessionId) {
            mPackageInstaller.abandonSession(sessionId);
        }
    }
}
