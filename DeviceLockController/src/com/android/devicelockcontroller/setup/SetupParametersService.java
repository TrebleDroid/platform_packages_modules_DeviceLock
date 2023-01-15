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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.android.devicelockcontroller.util.LogUtil;

import java.util.List;

/**
 * A class exposing Setup Parameters as a service.
 */
public final class SetupParametersService extends Service {
    private static final String TAG = "SetupParametersService";

    private Context mContext;

    private final ISetupParametersService.Stub mBinder =
            new ISetupParametersService.Stub() {
                @Override
                public void createPrefs(Bundle bundle) {
                    SetupParameters.createPrefs(mContext, bundle);
                }
                @Override
                public String getKioskPackage() {
                    return SetupParameters.getKioskPackage(mContext);
                }
                @Override
                public String getKioskDownloadUrl() {
                    return SetupParameters.getKioskDownloadUrl(mContext);
                }
                @Override
                public String getKioskSignatureChecksum() {
                    return SetupParameters.getKioskSignatureChecksum(mContext);
                }
                @Override
                public String getKioskSetupActivity() {
                    return SetupParameters.getKioskSetupActivity(mContext);
                }
                @Override
                public boolean getOutgoingCallsDisabled() {
                    return SetupParameters.getOutgoingCallsDisabled(mContext);
                }
                @Override
                public List<String> getKioskAllowlist() {
                    return SetupParameters.getKioskAllowlist(mContext);
                }
                @Override
                public boolean isNotificationsInLockTaskModeEnabled() {
                    return SetupParameters.isNotificationsInLockTaskModeEnabled(mContext);
                }
    };

    @Override
    public void onCreate() {
        LogUtil.d(TAG, "onCreate");
        mContext = this;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
