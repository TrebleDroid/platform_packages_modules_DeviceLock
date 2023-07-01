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
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClientInterface;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

final class AppOpsPolicyHandler implements PolicyHandler {
    private static final String TAG = "AppOpsPolicyHandler";
    private final Context mContext;
    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final SetupParametersClientInterface mSetupParametersClient;

    AppOpsPolicyHandler(Context context, SystemDeviceLockManager systemDeviceLockManager) {
        this(context, systemDeviceLockManager, SetupParametersClient.getInstance());
    }

    AppOpsPolicyHandler(Context context, SystemDeviceLockManager systemDeviceLockManager,
            SetupParametersClientInterface setupParametersClient) {
        mContext = context;
        mSystemDeviceLockManager = systemDeviceLockManager;
        mSetupParametersClient = setupParametersClient;
    }

    private ListenableFuture<@ResultType Integer>
            getExemptFromBackgroundStartRestrictionsFuture(boolean exempt) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.setExemptFromActivityBackgroundStartRestriction(exempt,
                            mContext.getMainExecutor(),
                            new OutcomeReceiver<Void, Exception>() {
                                @Override
                                public void onResult(Void unused) {
                                    completer.set(SUCCESS);
                                }

                                @Override
                                public void onError(Exception error) {
                                    LogUtil.e(TAG, "Cannot set background start exemption", error);
                                    completer.set(FAILURE);
                                }
                            });
                    // Used only for debugging.
                    return "getExemptFromBackgroundStartRestrictionFuture";
                });
    }

    private ListenableFuture<@ResultType Integer> getExemptFromHibernationFuture(boolean exempt) {
        return Futures.transformAsync(mSetupParametersClient.getKioskPackage(),
                kioskPackageName -> kioskPackageName == null
                        ? Futures.immediateFuture(SUCCESS)
                        : CallbackToFutureAdapter.getFuture(
                            completer -> {
                                mSystemDeviceLockManager.setExemptFromHibernation(
                                        kioskPackageName, exempt,
                                        mContext.getMainExecutor(),
                                        new OutcomeReceiver<Void, Exception>() {
                                            @Override
                                            public void onResult(Void unused) {
                                                completer.set(SUCCESS);
                                            }

                                            @Override
                                            public void onError(Exception error) {
                                                LogUtil.e(TAG, "Cannot set exempt from hibernation",
                                                        error);
                                                completer.set(FAILURE);
                                            }
                                        });
                                // Used only for debugging.
                                return "setExemptFromHibernationFuture";
                            }), MoreExecutors.directExecutor());
    }

    private ListenableFuture<@ResultType Integer>
            getExemptFromBackgroundStartAndHibernationFuture(boolean exempt) {
        final ListenableFuture<@ResultType Integer> backgroundFuture =
                getExemptFromBackgroundStartRestrictionsFuture(exempt /* exempt */);
        final ListenableFuture<@ResultType Integer> hibernationFuture =
                getExemptFromHibernationFuture(exempt /* exempt */);
        return Futures.whenAllSucceed(backgroundFuture, hibernationFuture)
                .call(() -> (Futures.getDone(backgroundFuture) == SUCCESS
                                && Futures.getDone(hibernationFuture) == SUCCESS)
                                ? SUCCESS : FAILURE,
                        MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<@ResultType Integer> setPolicyForState(@DeviceState int state) {
        switch (state) {
            case DeviceState.PSEUDO_LOCKED:
            case DeviceState.PSEUDO_UNLOCKED:
                return Futures.immediateFuture(SUCCESS);
            case DeviceState.PROVISION_IN_PROGRESS:
            case DeviceState.PROVISION_PAUSED:
            case DeviceState.PROVISION_SUCCEEDED:
            case DeviceState.PROVISION_FAILED:
            case DeviceState.KIOSK_PROVISIONED:
                return getExemptFromBackgroundStartRestrictionsFuture(true /* exempt */);
            case DeviceState.UNLOCKED:
            case DeviceState.LOCKED:
                return getExemptFromBackgroundStartAndHibernationFuture(true /* exempt */);
            case DeviceState.UNPROVISIONED:
            case DeviceState.CLEARED:
                return getExemptFromBackgroundStartAndHibernationFuture(false /* exempt */);
            default:
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }
    }
}
