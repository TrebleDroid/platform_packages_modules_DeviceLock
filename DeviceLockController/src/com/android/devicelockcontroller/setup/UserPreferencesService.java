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
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A class exposing User Preferences as a service.
 */
public final class UserPreferencesService extends Service {
    private static final String TAG = "UserPreferencesService";

    private Context mContext;

    private final IUserPreferencesService.Stub mBinder =
            new IUserPreferencesService.Stub() {
                @Override
                @DeviceState
                public int getDeviceState() {
                    return UserPreferences.getDeviceState(mContext);
                }

                @Override
                public void setDeviceState(@DeviceState int state) {
                    UserPreferences.setDeviceState(mContext, state);
                }

                @Override
                @Nullable
                public String getPackageOverridingHome() {
                    return UserPreferences.getPackageOverridingHome(mContext);
                }

                @Override
                public void setPackageOverridingHome(@Nullable String packageName) {
                    UserPreferences.setPackageOverridingHome(mContext, packageName);
                }

                @Override
                public List<String> getLockTaskAllowlist() {
                    return UserPreferences.getLockTaskAllowlist(mContext);
                }

                @Override
                public void setLockTaskAllowlist(List<String> allowlist) {
                    UserPreferences.setLockTaskAllowlist(mContext, new ArrayList<>(allowlist));
                }

                @Override
                public boolean needCheckIn() {
                    return UserPreferences.needCheckIn(mContext);
                }

                @Override
                public void setNeedCheckIn(boolean needCheckIn) {
                    UserPreferences.setNeedCheckIn(mContext, needCheckIn);
                }

                @Override
                public String getRegisteredDeviceId() {
                    return UserPreferences.getRegisteredDeviceId(mContext);
                }

                @Override
                public void setRegisteredDeviceId(String registeredDeviceId) {
                    UserPreferences.setRegisteredDeviceId(mContext, registeredDeviceId);
                }

                @Override
                public boolean isProvisionForced() {
                    return UserPreferences.isProvisionForced(mContext);
                }

                @Override
                public void setProvisionForced(boolean isForced) {
                    UserPreferences.setProvisionForced(mContext, isForced);
                }

                @Override
                public String getEnrollmentToken() {
                    return UserPreferences.getEnrollmentToken(mContext);
                }

                @Override
                public void setEnrollmentToken(String token) {
                    UserPreferences.setEnrollmentToken(mContext, token);
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
