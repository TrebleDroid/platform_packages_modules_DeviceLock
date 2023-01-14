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

package com.android.devicelockcontroller.setup;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.android.devicelockcontroller.DeviceLockControllerApplication;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Class used to access Setup Parameters from secondary users.
 * Storage is hosted by user 0 and is accessed indirectly using a service.
 */
public final class SetupParametersClient extends DlcClient {
    @SuppressLint("StaticFieldLeak") // Only holds application context.
    private static SetupParametersClient sSetupParametersClient;

    private SetupParametersClient(@NonNull Context context,
            @NonNull ComponentName componentName) {
        super(context, componentName);
    }

    private SetupParametersClient(@NonNull Context context) {
        this(context, new ComponentName(context, SetupParametersService.class));
    }

    /**
     * Get the SetupParametersClient singleton instance.
     */
    @MainThread
    public static SetupParametersClient getInstance() {
        if (sSetupParametersClient == null) {
            final Context applicationContext = DeviceLockControllerApplication.getAppContext();
            sSetupParametersClient = new SetupParametersClient(applicationContext);
        }

        return sSetupParametersClient;
    }

    /**
     * Parse setup parameters from the extras bundle.
     *
     * @param bundle Bundle with provisioning parameters.
     */
    public ListenableFuture<Void> createPrefs(Bundle bundle) {
        return call(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Void call() throws Exception {
                ISetupParametersService.Stub.asInterface(mDlcService).createPrefs(bundle);
                return null;
            }
        });
    }

    /**
     * Get the name of the package implementing the kiosk app.
     *
     * @return kiosk app package name.
     */
    public ListenableFuture<String> getKioskPackage() {
        return call(new Callable<String>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public String call() throws Exception {
                return ISetupParametersService.Stub.asInterface(mDlcService)
                        .getKioskPackage();
            }
        });
    }

    /**
     * Get the kiosk app download URL.
     *
     * @return Kiosk app download URL.
     */
    public ListenableFuture<String> getKioskDownloadUrl() {
        return call(new Callable<String>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public String call() throws Exception {
                return ISetupParametersService.Stub.asInterface(mDlcService)
                        .getKioskDownloadUrl();
            }
        });
    }

    /**
     * Get the kiosk app signature checksum.
     *
     * @return Signature checksum.
     */
    public ListenableFuture<String> getKioskSignatureChecksum() {
        return call(new Callable<String>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public String call() throws Exception {
                return ISetupParametersService.Stub.asInterface(mDlcService)
                        .getKioskSignatureChecksum();
            }
        });
    }

    /**
     * Get the setup activity for the kiosk app.
     *
     * @return Setup activity.
     */
    public ListenableFuture<String> getKioskSetupActivity() {
        return call(new Callable<String>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public String call() throws Exception {
                return ISetupParametersService.Stub.asInterface(mDlcService)
                        .getKioskSetupActivity();
            }
        });
    }

    /**
     * Check if the configuration disables outgoing calls.
     *
     * @return True if outgoign calls are disabled.
     */
    public ListenableFuture<Boolean> getOutgoingCallsDisabled() {
        return call(new Callable<Boolean>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Boolean call() throws Exception {
                return ISetupParametersService.Stub.asInterface(mDlcService)
                        .getOutgoingCallsDisabled();
            }
        });
    }

    /**
     * Get package allowlist provisioned by the server.
     *
     * @return List of allowed packages.
     */
    public ListenableFuture<List<String>> getKioskAllowlist() {
        return call(new Callable<List<String>>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public List<String> call() throws Exception {
                return ISetupParametersService.Stub.asInterface(mDlcService)
                        .getKioskAllowlist();
            }
        });
    }

    /**
     * Check if notifications are enabled in lock task mode.
     *
     * @return True if notification are enabled.
     */
    public ListenableFuture<Boolean> isNotificationsInLockTaskModeEnabled() {
        return call(new Callable<Boolean>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Boolean call() throws Exception {
                return ISetupParametersService.Stub.asInterface(mDlcService)
                        .isNotificationsInLockTaskModeEnabled();
            }
        });
    }
}
