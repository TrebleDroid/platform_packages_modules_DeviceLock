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

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interface for the device lock controller state machine.
 */
@MainThread
public interface DeviceStateController {
    /**
     * Enforce all policies for the current device state.
     */
    ListenableFuture<Void> enforcePoliciesForCurrentState();

    /**
     * Moves the device to a new state based on the input event
     *
     * @return The next state {@link DeviceState} after the event, or an
     * {@code ImmediateFailedFuture} if the state transition failed.
     */
    @DeviceState
    ListenableFuture<Integer> setNextStateForEvent(@DeviceEvent int event);

    /**
     * Returns the current state of the device
     */
    @DeviceState
    int getState();

    /**
     * Returns true if the device is in locked state including {@link DeviceState#PSEUDO_LOCKED}
     */
    boolean isLocked();

    /**
     * Returns true if the device is in a state where no restrictions are applied which includes
     * following states:
     * - {@link DeviceState#UNPROVISIONED};
     * - {@link DeviceState#CLEARED};
     * - {@link DeviceState#PSEUDO_LOCKED};
     * - {@link DeviceState#PSEUDO_UNLOCKED};
     */
    boolean isUnrestrictedState();

    /**
     * Returns true if the device is in locked state excluding {@link DeviceState#PSEUDO_LOCKED}
     */
    boolean isLockedInternal();

    /**
     * Returns true if the device needs to check in with DeviceLock server
     */
    boolean isCheckInNeeded();

    /**
     * Returns true if the device is in provisioning flow.
     */
    boolean isInProvisioningState();

    /**
     * Register a callback to get notified on state change.
     */
    void addCallback(StateListener listener);

    /**
     * Remove a previously registered callback.
     */
    void removeCallback(StateListener listener);

    /**
     * Device state definitions
     */
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DeviceState.UNPROVISIONED,
            DeviceState.PROVISION_IN_PROGRESS,
            DeviceState.PROVISION_SUCCEEDED,
            DeviceState.PROVISION_PAUSED,
            DeviceState.PROVISION_FAILED,
            DeviceState.KIOSK_PROVISIONED,
            DeviceState.UNLOCKED,
            DeviceState.LOCKED,
            DeviceState.CLEARED,
            DeviceState.PSEUDO_LOCKED,
            DeviceState.PSEUDO_UNLOCKED,
    })
    @interface DeviceState {

        /* Not provisioned */
        int UNPROVISIONED = 0;

        /* Provisioning flow is in progress. This is where kiosk app will be installed. */
        int PROVISION_IN_PROGRESS = 1;

        /* Provisioning has succeeded */
        int PROVISION_SUCCEEDED = 2;

        /** Provisioning is paused */
        int PROVISION_PAUSED = 3;

        /* Provisioning has failed */
        int PROVISION_FAILED = 4;

        /* Kiosk app provisioned */
        int KIOSK_PROVISIONED = 5;

        /* Device is unlocked */
        int UNLOCKED = 6;

        /* Device is locked */
        int LOCKED = 7;

        /* Fully cleared from locking */
        int CLEARED = 8;

        /* Device appears to be locked. No Actual locking is performed. Used for testing */
        int PSEUDO_LOCKED = 9;

        /* Device appears to be unlocked. No Actual unlocking is performed. Used for testing */
        int PSEUDO_UNLOCKED = 10;
    }

    /**
     * Get the corresponding string for input {@link DeviceState}.
     */
    static String stateToString(@DeviceState int state) {
        switch (state) {
            case DeviceState.UNPROVISIONED:
                return "UNPROVISIONED";
            case DeviceState.PROVISION_IN_PROGRESS:
                return "PROVISION_IN_PROGRESS";
            case DeviceState.PROVISION_SUCCEEDED:
                return "PROVISION_SUCCEEDED";
            case DeviceState.PROVISION_PAUSED:
                return "PROVISION_PAUSED";
            case DeviceState.PROVISION_FAILED:
                return "PROVISION_FAILED";
            case DeviceState.KIOSK_PROVISIONED:
                return "KIOSK_PROVISIONED";
            case DeviceState.UNLOCKED:
                return "UNLOCKED";
            case DeviceState.LOCKED:
                return "LOCKED";
            case DeviceState.CLEARED:
                return "CLEARED";
            case DeviceState.PSEUDO_LOCKED:
                return "PSEUDO_LOCKED";
            case DeviceState.PSEUDO_UNLOCKED:
                return "PSEUDO_UNLOCKED";
            default:
                return "UNKNOWN_STATE";
        }
    }


    /**
     * Device event definitions
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DeviceEvent.PROVISION_READY,
            DeviceEvent.PROVISION_PAUSE,
            DeviceEvent.PROVISION_SUCCESS,
            DeviceEvent.PROVISION_FAILURE,
            DeviceEvent.PROVISION_KIOSK,
            DeviceEvent.LOCK_DEVICE,
            DeviceEvent.UNLOCK_DEVICE,
            DeviceEvent.CLEAR,
            DeviceEvent.PROVISION_RESUME,
            DeviceEvent.PROVISION_RETRY,
    })
    @interface DeviceEvent {

        /* Ready for provisioning */
        int PROVISION_READY = 0;

        /* Pause provisioning */
        int PROVISION_PAUSE = 1;

        /* Provisioning completed successfully */
        int PROVISION_SUCCESS = 2;

        /* Provisioning failed to complete */
        int PROVISION_FAILURE = 3;

        /* Provision Kiosk app */
        int PROVISION_KIOSK = 4;

        /* Lock device */
        int LOCK_DEVICE = 5;

        /* Unlock device */
        int UNLOCK_DEVICE = 6;

        /* Clear device lock restrictions */
        int CLEAR = 7;

        /* Resume provisioning */
        int PROVISION_RESUME = 8;

        /* Retry provision after failure */
        int PROVISION_RETRY = 9;
    }

    /**
     * Listener interface for state changes.
     */
    interface StateListener {
        /**
         * Notified after the device transitions to a new state
         */
        ListenableFuture<Void> onStateChanged(@DeviceState int newState);
    }


    /**
     * Get the corresponding string for the input {@link DeviceEvent}
     */
    static String eventToString(@DeviceEvent int event) {
        switch (event) {
            case DeviceEvent.PROVISION_READY:
                return "PROVISION_READY";
            case DeviceEvent.PROVISION_PAUSE:
                return "PROVISION_PAUSE";
            case DeviceEvent.PROVISION_SUCCESS:
                return "PROVISION_SUCCESS";
            case DeviceEvent.PROVISION_FAILURE:
                return "PROVISION_FAILURE";
            case DeviceEvent.PROVISION_KIOSK:
                return "PROVISION_KIOSK";
            case DeviceEvent.LOCK_DEVICE:
                return "LOCK_DEVICE";
            case DeviceEvent.UNLOCK_DEVICE:
                return "UNLOCK_DEVICE";
            case DeviceEvent.CLEAR:
                return "CLEAR";
            case DeviceEvent.PROVISION_RESUME:
                return "PROVISION_RESUME";
            case DeviceEvent.PROVISION_RETRY:
                return "PROVISION_RETRY";
            default:
                return "UNKNOWN_EVENT";
        }
    }
}
