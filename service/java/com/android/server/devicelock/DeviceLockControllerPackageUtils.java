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

package com.android.server.devicelock;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.UserHandle;

import java.util.List;

public final class DeviceLockControllerPackageUtils {
    private final Context mContext;

    private static final String SERVICE_ACTION =
            "android.app.action.DEVICE_LOCK_CONTROLLER_SERVICE";

    // resources.arsc still uses the original package name (b/147434671)
    private static final String RESOURCE_PACKAGE_NAME = "com.android.devicelockcontroller";

    private static final UserHandle USER_HANDLE_SYSTEM = UserHandle.of(0);

    public DeviceLockControllerPackageUtils(Context context) {
        mContext = context;
    }

    private ServiceInfo mServiceInfo;

    private int mDeviceIdTypeBitmap = -1;

    synchronized
    public @Nullable
    ServiceInfo findService(@NonNull StringBuilder errorMessage) {
        errorMessage.setLength(0);

        if (mServiceInfo == null) {
            mServiceInfo = findServiceInternal(errorMessage);
        }

        return mServiceInfo;
    }

    private @Nullable
    ServiceInfo findServiceInternal(@NonNull StringBuilder errorMessage) {
        final Intent intent = new Intent(SERVICE_ACTION);
        final PackageManager pm = mContext.getPackageManager();

        errorMessage.setLength(0);

        final List<ResolveInfo> resolveInfoList = pm.queryIntentServicesAsUser(intent,
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE, USER_HANDLE_SYSTEM);

        if (resolveInfoList == null || resolveInfoList.isEmpty()) {
            errorMessage.append("Service with " + SERVICE_ACTION + " not found.");

            return null;
        }

        ServiceInfo resultServiceInfo = null;

        for (ResolveInfo resolveInfo: resolveInfoList) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;

            if ((serviceInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                continue;
            }

            if (resultServiceInfo != null) {
                errorMessage.append("Multiple system services handle " + SERVICE_ACTION + ".");

                return null;
            }

            resultServiceInfo = serviceInfo;
        }

        return resultServiceInfo;
    }

    /* Get the allowed device id type bitmap or -1 if it cannot be determined */
    synchronized
    public int getDeviceIdTypeBitmap(@NonNull StringBuilder errorMessage) {
        errorMessage.setLength(0);

        if (mDeviceIdTypeBitmap < 0) {
            mDeviceIdTypeBitmap = getDeviceIdTypeBitmapInternal(errorMessage);
        }

        return mDeviceIdTypeBitmap;
    }

    private int getDeviceIdTypeBitmapInternal(@NonNull StringBuilder errorMessage) {
        ServiceInfo serviceInfo = findService(errorMessage);

        if (serviceInfo == null) {
            return -1;
        }

        final String packageName = serviceInfo.packageName;

        final PackageManager pm = mContext.getPackageManager();
        int deviceIdTypeBitmap = -1;
        errorMessage.setLength(0);

        try {
            final Resources resources = pm.getResourcesForApplication(packageName);
            final int resId = resources.getIdentifier("device_id_type_bitmap", "integer",
                    RESOURCE_PACKAGE_NAME);
            if (resId == 0) {
                errorMessage.append("Cannot get device_id_type_bitmap");

                return -1;
            }
            deviceIdTypeBitmap = resources.getInteger(resId);
        } catch (PackageManager.NameNotFoundException e) {
            errorMessage.append("Cannot get resources for package: " + packageName);
        }

        return deviceIdTypeBitmap;
    }
}
