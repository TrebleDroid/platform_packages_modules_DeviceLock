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

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_FAILURE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_KIOSK;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_PAUSE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_READY;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_RESUME;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_RETRY;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_SUCCESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.KIOSK_PROVISIONED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * An implementation of the {@link ProvisionStateController}. This class guarantees thread safety
 * by synchronizing read/write operations of the state value on background threads in the order of
 * when the API calls happen. That is, a pre-exist state value read/write operation will always
 * blocks a incoming read/write request until the former completes.
 */
public final class ProvisionStateControllerImpl implements ProvisionStateController {

    public static final String TAG = "ProvisionStateControllerImpl";
    private final Context mContext;
    private final DevicePolicyController mPolicyController;
    private final DeviceStateController mDeviceStateController;
    private final Executor mBgExecutor;

    @GuardedBy("this")
    private ListenableFuture<@ProvisionState Integer> mCurrentStateFuture;

    public ProvisionStateControllerImpl(Context context) {
        mContext = context;
        mBgExecutor = Executors.newCachedThreadPool();
        mPolicyController = new DevicePolicyControllerImpl(context, this, mBgExecutor);
        mDeviceStateController = new DeviceStateControllerImpl(mPolicyController, this,
                mBgExecutor);
    }

    @VisibleForTesting
    ProvisionStateControllerImpl(Context context, DevicePolicyController policyController,
            DeviceStateController stateController, Executor bgExecutor) {
        mContext = context;
        mPolicyController = policyController;
        mDeviceStateController = stateController;
        mBgExecutor = bgExecutor;
    }

    @Override
    public ListenableFuture<@ProvisionState Integer> getState() {
        synchronized (this) {
            if (mCurrentStateFuture == null) {
                mCurrentStateFuture = Futures.submit(
                        () -> UserParameters.getProvisionState(mContext),
                        mBgExecutor);
            }
            return mCurrentStateFuture;
        }
    }

    @Override
    public void postSetNextStateForEventRequest(@ProvisionEvent int event) {
        Futures.addCallback(setNextStateForEvent(event),
                getFutureCallback("Set state for event: " + event),
                MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Void> setNextStateForEvent(@ProvisionEvent int event) {
        synchronized (this) {
            // getState() must be called here and assigned to a local variable, otherwise, if
            // retrieved down the execution flow, it will be returning the new state after
            // execution.
            ListenableFuture<@ProvisionState Integer> currentStateFuture = getState();
            ListenableFuture<@ProvisionState Integer> stateTransitionFuture =
                    Futures.transform(
                            currentStateFuture,
                            currentState -> {
                                int newState = getNextState(currentState, event);
                                UserParameters.setProvisionState(mContext, newState);
                                return newState;
                            }, mBgExecutor);
            // To prevent exception propagate to future state transitions, catch any exceptions
            // that might happen during the execution and fallback to previous state if exception
            // happens.
            mCurrentStateFuture = Futures.catchingAsync(stateTransitionFuture, Exception.class,
                    input -> currentStateFuture, mBgExecutor);
            return Futures.transformAsync(stateTransitionFuture,
                    newState -> mPolicyController.enforceCurrentPolicies(), mBgExecutor);
        }
    }

    @Override
    public void notifyProvisioningReady() {
        if (isUserSetupComplete()) {
            postSetNextStateForEventRequest(PROVISION_READY);
        } else {
            registerUserSetupCompleteListener(
                    () -> postSetNextStateForEventRequest(PROVISION_READY));
        }
    }

    @NonNull
    private FutureCallback<Void> getFutureCallback(String message) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(Void unused) {
                LogUtil.i(TAG, message);
            }

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }

    @VisibleForTesting
    @ProvisionState
    static int getNextState(@ProvisionState int state, @ProvisionEvent int event) {
        switch (event) {
            case PROVISION_READY:
                if (state == UNPROVISIONED) {
                    return PROVISION_IN_PROGRESS;
                }
                throw new StateTransitionException(state, event);
            case ProvisionEvent.PROVISION_PAUSE:
                if (state == PROVISION_IN_PROGRESS) {
                    return PROVISION_PAUSED;
                }
                throw new StateTransitionException(state, event);
            case PROVISION_RESUME:
                if (state == PROVISION_PAUSED) {
                    return PROVISION_IN_PROGRESS;
                }
                throw new StateTransitionException(state, event);
            case ProvisionEvent.PROVISION_KIOSK:
                if (state == PROVISION_IN_PROGRESS) {
                    return KIOSK_PROVISIONED;
                }
                throw new StateTransitionException(state, event);
            case ProvisionEvent.PROVISION_FAILURE:
                if (state == PROVISION_IN_PROGRESS) {
                    return PROVISION_FAILED;
                }
                throw new StateTransitionException(state, event);
            case ProvisionEvent.PROVISION_RETRY:
                if (state == PROVISION_FAILED) {
                    return PROVISION_IN_PROGRESS;
                }
                throw new StateTransitionException(state, event);
            case ProvisionEvent.PROVISION_SUCCESS:
                if (state == KIOSK_PROVISIONED) {
                    return PROVISION_SUCCEEDED;
                }
                throw new StateTransitionException(state, event);
            default:
                throw new IllegalArgumentException("Input state is invalid");
        }
    }

    @Override
    public DeviceStateController getDeviceStateController() {
        return mDeviceStateController;
    }

    @Override
    public DevicePolicyController getDevicePolicyController() {
        return mPolicyController;
    }

    @Override
    public ListenableFuture<Void> onUserStarting() {
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        return Futures.transformAsync(getState(),
                state -> {
                    if (state == UNPROVISIONED) {
                        return Futures.transformAsync(globalParametersClient.isProvisionReady(),
                                isReady -> {
                                    if (isReady) {
                                        notifyProvisioningReady();
                                    }
                                    return Futures.immediateVoidFuture();
                                },
                                mBgExecutor);
                    } else {
                        return mPolicyController.enforceCurrentPolicies();
                    }
                },
                mBgExecutor);
    }

    private void registerUserSetupCompleteListener(Runnable listener) {
        Uri setupCompleteUri = Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE);
        mContext.getContentResolver().registerContentObserver(setupCompleteUri,
                false /* notifyForDescendants */, new ContentObserver(null /* handler */) {
                    @Override
                    public void onChange(boolean selfChange, @Nullable Uri uri) {
                        if (setupCompleteUri.equals(uri) && isUserSetupComplete()) {
                            mContext.getContentResolver().unregisterContentObserver(this);
                            listener.run();
                        }
                    }
                });
    }

    private boolean isUserSetupComplete() {
        return Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    /**
     * A RuntimeException thrown when state transition is  not allowed
     */
    public static class StateTransitionException extends RuntimeException {
        public StateTransitionException(@ProvisionState int currentState,
                @ProvisionEvent int event) {
            super("Can not handle event: " + eventToString(event)
                    + " in state: " + stateToString(currentState));
        }

        private static String stateToString(@ProvisionState int state) {
            switch (state) {
                case UNPROVISIONED:
                    return "UNPROVISIONED";
                case PROVISION_IN_PROGRESS:
                    return "PROVISION_IN_PROGRESS";
                case PROVISION_PAUSED:
                    return "PROVISION_PAUSED";
                case PROVISION_FAILED:
                    return "PROVISION_FAILED";
                case PROVISION_SUCCEEDED:
                    return "PROVISION_SUCCEEDED";
                case KIOSK_PROVISIONED:
                    return "KIOSK_PROVISIONED";
                default:
                    return "UNKNOWN_STATE";
            }
        }

        private static String eventToString(@ProvisionEvent int event) {
            switch (event) {
                case PROVISION_READY:
                    return "PROVISION_READY";
                case PROVISION_PAUSE:
                    return "PROVISION_PAUSE";
                case PROVISION_SUCCESS:
                    return "PROVISION_SUCCESS";
                case PROVISION_FAILURE:
                    return "PROVISION_FAILURE";
                case PROVISION_KIOSK:
                    return "PROVISION_KIOSK";
                case PROVISION_RESUME:
                    return "PROVISION_RESUME";
                case PROVISION_RETRY:
                    return "PROVISION_RETRY";
                default:
                    return "UNKNOWN_EVENT";
            }
        }

    }

}
