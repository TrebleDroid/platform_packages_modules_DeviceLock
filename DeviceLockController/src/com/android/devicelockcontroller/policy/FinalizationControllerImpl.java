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

import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED;
import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED_UNREPORTED;
import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.UNFINALIZED;
import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.UNINITIALIZED;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker.REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME;

import android.annotation.IntDef;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient.ReportDeviceProgramCompleteResponse;
import com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker;
import com.android.devicelockcontroller.receivers.LockedBootCompletedReceiver;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Implementation of {@link FinalizationController} that finalizes the device by reporting the
 * state to the server and effectively disabling this application entirely.
 */
public final class FinalizationControllerImpl implements FinalizationController {

    private static final String TAG = FinalizationControllerImpl.class.getSimpleName();

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            UNFINALIZED,
            FINALIZED_UNREPORTED,
            FINALIZED,
            UNINITIALIZED
    })
    @interface FinalizationState {
        /* Not finalized */
        int UNFINALIZED = 0;

        /* Device is finalized but still needs to report finalization to server */
        int FINALIZED_UNREPORTED = 1;

        /* Fully finalized. All bookkeeping is finished and okay to disable app. */
        int FINALIZED = 2;

        /* State has yet to be initialized */
        int UNINITIALIZED = -1;
    }

    /** Dispatch queue to guarantee state changes occur sequentially */
    private final FinalizationStateDispatchQueue mDispatchQueue;
    private final Executor mLightweightExecutor;
    private final Context mContext;
    private final Class<? extends ListenableWorker> mReportDeviceFinalizedWorkerClass;

    public FinalizationControllerImpl(Context context) {
        this(context,
                new FinalizationStateDispatchQueue(),
                Executors.newCachedThreadPool(),
                ReportDeviceLockProgramCompleteWorker.class);
    }

    @VisibleForTesting
    public FinalizationControllerImpl(
            Context context,
            FinalizationStateDispatchQueue dispatchQueue,
            Executor lightweightExecutor,
            Class<? extends ListenableWorker> reportDeviceFinalizedWorkerClass) {
        mContext = context;
        mDispatchQueue = dispatchQueue;
        mDispatchQueue.init(this::onStateChanged);
        mLightweightExecutor = lightweightExecutor;
        mReportDeviceFinalizedWorkerClass = reportDeviceFinalizedWorkerClass;

        // Set the initial state
        // TODO(279517666): Pull state from disk here instead of a constant
        Futures.addCallback(
                mDispatchQueue.enqueueStateChange(UNFINALIZED),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        // no-op
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    @Override
    public ListenableFuture<Void> notifyRestrictionsCleared() {
        return mDispatchQueue.enqueueStateChange(FINALIZED_UNREPORTED);
    }

    @Override
    public ListenableFuture<Void> notifyFinalizationReportResult(
            ReportDeviceProgramCompleteResponse response) {
        if (response.isSuccessful()) {
            return mDispatchQueue.enqueueStateChange(FINALIZED);
        } else {
            // TODO(279517666): Determine how to handle an unrecoverable failure
            // response from the server
            LogUtil.e(TAG, "Unrecoverable failure in reporting finalization state: " + response);
            return Futures.immediateVoidFuture();
        }
    }

    @WorkerThread
    private void onStateChanged(@FinalizationState int newState) {
        // TODO(279517666): Write the new state to disk.
        switch (newState) {
            case UNFINALIZED:
                // no-op
                break;
            case FINALIZED_UNREPORTED:
                requestWorkToReportFinalized();
                break;
            case FINALIZED:
                disableEntireApplication();
                break;
            case UNINITIALIZED:
                throw new IllegalArgumentException("Tried to set state back to uninitialized!");
            default:
                throw new IllegalArgumentException("Unknown state " + newState);
        }
    }

    /**
     * Request work to report device is finalized.
     */
    private void requestWorkToReportFinalized() {
        WorkManager workManager =
                WorkManager.getInstance(mContext);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(mReportDeviceFinalizedWorkerClass)
                        .setConstraints(constraints)
                        .build();
        ListenableFuture<Operation.State.SUCCESS> result =
                workManager.enqueueUniqueWork(REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME,
                        ExistingWorkPolicy.REPLACE, work).getResult();
        Futures.addCallback(result,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Operation.State.SUCCESS result) {
                        // no-op
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    /**
     * Disables the entire device lock controller application.
     *
     * This will remove any work, alarms, receivers, etc., and this application should never run
     * on the device again after this point.
     */
    private void disableEntireApplication() {
        PackageManager pm = mContext.getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(mContext,
                        LockedBootCompletedReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0 /* flags */);
        // TODO(279517666): Disable application and persist a boolean so that DeviceLockService
        // does not re-enable application on boot / user switch
    }
}
