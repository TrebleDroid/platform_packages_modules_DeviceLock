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

import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;

final class AppOpsPolicyHandler implements PolicyHandler {
    private static final String TAG = "AppOpsPolicyHandler";
    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final Executor mBgExecutor;

    AppOpsPolicyHandler(SystemDeviceLockManager systemDeviceLockManager, Executor bgExecutor) {
        mSystemDeviceLockManager = systemDeviceLockManager;
        mBgExecutor = bgExecutor;
    }

    private ListenableFuture<Boolean> getExemptFromBackgroundStartRestrictionsFuture(
            boolean exempt) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.setExemptFromActivityBackgroundStartRestriction(exempt,
                            mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void unused) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception error) {
                                    LogUtil.e(TAG, "Cannot set background start exemption", error);
                                    completer.set(false);
                                }
                            });
                    // Used only for debugging.
                    return "getExemptFromBackgroundStartRestrictionFuture";
                });
    }

    private ListenableFuture<Boolean> getExemptFromHibernationFuture(boolean exempt) {
        return Futures.transformAsync(SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackageName -> kioskPackageName == null
                        ? Futures.immediateFuture(true)
                        : CallbackToFutureAdapter.getFuture(
                                completer -> {
                                    mSystemDeviceLockManager.setExemptFromHibernation(
                                            kioskPackageName, exempt,
                                            mBgExecutor,
                                            new OutcomeReceiver<>() {
                                                @Override
                                                public void onResult(Void unused) {
                                                    completer.set(true);
                                                }

                                                @Override
                                                public void onError(Exception error) {
                                                    LogUtil.e(TAG,
                                                            "Cannot set exempt from hibernation",
                                                            error);
                                                    completer.set(false);
                                                }
                                            });
                                    // Used only for debugging.
                                    return "setExemptFromHibernationFuture";
                                }), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> getExemptFromBackgroundStartAndHibernationFuture(
            boolean exempt) {
        final ListenableFuture<Boolean> backgroundFuture =
                getExemptFromBackgroundStartRestrictionsFuture(exempt /* exempt */);
        final ListenableFuture<Boolean> hibernationFuture =
                getExemptFromHibernationFuture(exempt /* exempt */);
        return Futures.whenAllSucceed(backgroundFuture, hibernationFuture)
                .call(() -> Futures.getDone(backgroundFuture)
                                && Futures.getDone(hibernationFuture),
                        MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Boolean> onProvisioned() {
        return getExemptFromBackgroundStartAndHibernationFuture(/* exempt= */ true);
    }

    @Override
    public ListenableFuture<Boolean> onProvisionInProgress() {
        return getExemptFromBackgroundStartRestrictionsFuture(/* exempt= */ true);
    }

    @Override
    public ListenableFuture<Boolean> onProvisionFailed() {
        return getExemptFromBackgroundStartRestrictionsFuture(/* exempt= */ false);
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        return getExemptFromBackgroundStartAndHibernationFuture(/* exempt= */ false);
    }

    // Due to some reason, AppOpsManager does not persist exemption after reboot. Therefore we
    // need to always set them from our end.
    @Override
    public ListenableFuture<Boolean> onLocked() {
        return getExemptFromBackgroundStartAndHibernationFuture(/* exempt= */ true);
    }

    // Due to some reason, AppOpsManager does not persist exemption after reboot. Therefore we
    // need to always set them from our end.
    @Override
    public ListenableFuture<Boolean> onUnlocked() {
        return getExemptFromHibernationFuture(/* exempt= */ true);
    }
}
