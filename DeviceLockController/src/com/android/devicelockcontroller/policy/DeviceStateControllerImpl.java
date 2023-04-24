/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.devicelockcontroller.setup.UserPreferences;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * State machine for device lock controller.
 */
public final class DeviceStateControllerImpl implements DeviceStateController {
    private static final String TAG = "DeviceStateControllerImpl";
    private final Context mContext;
    private final ArrayList<StateListener> mListeners = new ArrayList<>();
    private int mState;

    /**
     * Create a new state machine.
     *
     * @param context The context used for the state machine.
     */
    public DeviceStateControllerImpl(Context context) {
        mState = UserPreferences.getDeviceState(context);
        LogUtil.i(TAG, String.format(Locale.US, "Starting state is %d", mState));
        mContext = context;
    }

    @Override
    public ListenableFuture<Void> setNextStateForEvent(@DeviceEvent int event)
            throws StateTransitionException {
        updateState(getNextState(event));
        LogUtil.i(TAG, String.format(Locale.US, "handleEvent %d, newState %d", event, mState));
        final List<ListenableFuture<Void>> onStateChangedTasks = new ArrayList<>();
        synchronized (mListeners) {
            for (StateListener listener : mListeners) {
                onStateChangedTasks.add(listener.onStateChanged(mState));
            }
        }
        return Futures.whenAllSucceed(onStateChangedTasks).call((() -> null),
                MoreExecutors.directExecutor());
    }

    @Override
    public int getState() {
        return mState;
    }

    @Override
    public boolean isLocked() {
        return mState == DeviceState.SETUP_IN_PROGRESS
                || mState == DeviceState.SETUP_SUCCEEDED
                || mState == DeviceState.SETUP_FAILED
                || mState == DeviceState.KIOSK_SETUP
                || mState == DeviceState.LOCKED
                || mState == DeviceState.PSEUDO_LOCKED;
    }

    @Override
    public boolean isCheckInNeeded() {
        return mState == DeviceState.UNPROVISIONED && UserPreferences.needCheckIn(mContext);
    }

    @Override
    public boolean isInSetupState() {
        return mState == DeviceState.SETUP_IN_PROGRESS
                || mState == DeviceState.SETUP_SUCCEEDED
                || mState == DeviceState.SETUP_FAILED;
    }

    @Override
    public void addCallback(StateListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    @Override
    public void removeCallback(StateListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    @DeviceState
    private int getNextState(@DeviceEvent int event) throws StateTransitionException {
        // TODO: remove the following once state transitions for the LOCK_DEVICE/UNLOCK_DEVICE
        // events are finalized.
        final boolean forceLockUnlock = true;
        // TODO: return proper next state.
        switch (event) {
            case DeviceEvent.PROVISIONING_SUCCESS:
                break;
            case DeviceEvent.SETUP_SUCCESS:
                break;
            case DeviceEvent.SETUP_FAILURE:
                break;
            case DeviceEvent.SETUP_COMPLETE:
                break;
            case DeviceEvent.LOCK_DEVICE:
                if (mState == DeviceState.UNPROVISIONED || mState == DeviceState.PSEUDO_UNLOCKED) {
                    return DeviceState.PSEUDO_LOCKED;
                }
                if (mState == DeviceState.UNLOCKED || forceLockUnlock) {
                    return DeviceState.LOCKED;
                }
                break;
            case DeviceEvent.UNLOCK_DEVICE:
                if (mState == DeviceState.PSEUDO_LOCKED) {
                    return DeviceState.PSEUDO_UNLOCKED;
                }
                if (mState == DeviceState.LOCKED || mState == DeviceState.KIOSK_SETUP
                        || forceLockUnlock) {
                    return DeviceState.UNLOCKED;
                }
                break;
            case DeviceEvent.CLEAR:
                if (mState == DeviceState.LOCKED
                        || mState == DeviceState.UNLOCKED
                        || mState == DeviceState.KIOSK_SETUP) {
                    return DeviceState.CLEARED;
                }
                break;
            case DeviceEvent.RESET:
                if (mState == DeviceState.PSEUDO_LOCKED
                        || mState == DeviceState.PSEUDO_UNLOCKED) {
                    return DeviceState.UNPROVISIONED;
                }
                break;
            default:
                break;
        }

        throw new StateTransitionException(mState, event);
    }

    private void updateState(@DeviceState int newState) {
        UserPreferences.setDeviceState(mContext, newState);
        mState = newState;
    }
}
