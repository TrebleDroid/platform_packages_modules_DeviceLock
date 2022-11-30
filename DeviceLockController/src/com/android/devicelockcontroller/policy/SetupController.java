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
import androidx.lifecycle.LifecycleOwner;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Controller managing communication between setup tasks and UI layer. */
public interface SetupController {

    /** Definitions for status of the setup. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SETUP_NOT_STARTED, SETUP_IN_PROGRESS, SETUP_FAILED, SETUP_FINISHED})
    @interface SetupStatus {}
    /** Setup has not started and will be triggered from the activity. */
    int SETUP_NOT_STARTED = 0;
    /** Setup is in progress */
    int SETUP_IN_PROGRESS = 1;
    /** Setup has failed. */
    int SETUP_FAILED = 2;
    /** Setup has finished successfully. */
    int SETUP_FINISHED = 3;

    /** Registers a callback listener. */
    void addListener(SetupUpdatesCallbacks cb);

    /** Removes a callback listener. */
    void removeListener(SetupUpdatesCallbacks cb);

    /** Returns the status of Setup progress. */
    @SetupStatus
    int getSetupState();

    /** Triggers the setup flow process. */
    void startSetupFlow(LifecycleOwner owner);

    /**
     * Finishes the setup flow process. Triggers the appropriate actions based on whether setup was
     * successful.
     */
    void finishSetup();

    /** Callback interface for updates on setup tasks */
    interface SetupUpdatesCallbacks {

        /** Definitions for setup failure types. */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
                value = {
                        SETUP_FAILED,
                        DOWNLOAD_FAILED,
                        VERIFICATION_FAILED,
                        INSTALL_FAILED,
                        PACKAGE_DOES_NOT_EXIST
                })
        @interface FailureType {}

        /** Setup failed to complete */
        int SETUP_FAILED = 0;
        /** Failed to download the creditor apk. */
        int DOWNLOAD_FAILED = 1;
        /** Verification of the creditor apk failed. */
        int VERIFICATION_FAILED = 2;
        /** Failed to install the creditor apk. */
        int INSTALL_FAILED = 3;
        /** Pre-installed package not found */
        int PACKAGE_DOES_NOT_EXIST = 4;

        /** Method called when setup has failed. */
        void setupFailed(@FailureType int reason);

        /** Method called when setup tasks have completed successfully. */
        void setupCompleted();
    }
}
