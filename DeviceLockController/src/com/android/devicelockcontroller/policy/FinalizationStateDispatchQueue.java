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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Dispatch queue for any changes to the finalization state.
 *
 * Guarantees serialization of any state changes so that callbacks happen sequentially in the
 * order the state changes occurred.
 */
final class FinalizationStateDispatchQueue {
    @GuardedBy("mSequentialExecutor")
    private final Executor mSequentialExecutor;
    private @Nullable StateChangeCallback mCallback;
    private @FinalizationState int mState = UNINITIALIZED;

    FinalizationStateDispatchQueue() {
        this(MoreExecutors.newSequentialExecutor(Executors.newSingleThreadExecutor()));
    }

    @VisibleForTesting
    FinalizationStateDispatchQueue(Executor sequentialExecutor) {
        mSequentialExecutor = sequentialExecutor;
    }

    /**
     * Initializes the queue.
     *
     * @param callback callback that runs atomically with any state changes
     */
    void init(@NonNull StateChangeCallback callback) {
        mCallback = callback;
    }

    /**
     * Enqueue a state change to be handled after all previous state change requests have resolved.
     *
     * Attempting to return to a previous state in the finalization process will no-op.
     *
     * @param newState new state to go to
     * @return future for when state change has completed
     */
    ListenableFuture<Void> enqueueStateChange(@FinalizationState int newState) {
        ListenableFuture<Void> stateChangeFuture = CallbackToFutureAdapter.getFuture(
                completer -> {
                    synchronized (mSequentialExecutor) {
                        mSequentialExecutor.execute(() -> {
                            try {
                                handleStateChange(newState);
                                completer.set(null);
                            } catch (Exception e) {
                                completer.setException(e);
                            }
                        });
                    }
                    return "Finalization state change future";
                }
        );
        return stateChangeFuture;
    }

    /**
     * Handles a state change.
     *
     * @param newState state to change to
     */
    private void handleStateChange(@FinalizationState int newState) {
        final int oldState = mState;
        if (oldState == newState) {
            return;
        }
        if (!isValidStateChange(oldState, newState)) {
            return;
        }
        mState = newState;
        if (mCallback != null) {
            mCallback.onStateChanged(newState);
        }
    }

    private static boolean isValidStateChange(
            @FinalizationState int oldState,
            @FinalizationState int newState) {
        if (oldState == UNINITIALIZED) {
            return true;
        }
        if (oldState == UNFINALIZED && newState == FINALIZED_UNREPORTED) {
            return true;
        }
        if (oldState == FINALIZED_UNREPORTED && newState == FINALIZED) {
            return true;
        }
        return false;
    }

    /**
     * Callback for when the state has changed. Runs on {@link #mSequentialExecutor},
     */
    interface StateChangeCallback {

        /**
         * Called when the state has changed
         *
         * @param newState the new state
         */
        void onStateChanged(@FinalizationState int newState);
    }
}
