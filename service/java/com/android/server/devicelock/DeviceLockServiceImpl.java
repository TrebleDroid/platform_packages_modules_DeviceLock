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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.devicelock.DeviceId.DeviceIdType;
import android.devicelock.IDeviceLockService;
import android.devicelock.IGetKioskAppsCallback;
import android.devicelock.IGetDeviceIdCallback;
import android.devicelock.IIsDeviceLockedCallback;
import android.devicelock.ILockUnlockDeviceCallback;
import android.os.Binder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Slog;

import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static android.devicelock.DeviceId.DEVICE_ID_TYPE_IMEI;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_MEID;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Implementation of {@link android.devicelock.IDeviceLockService} binder service.
 */
final class DeviceLockServiceImpl extends IDeviceLockService.Stub {
    private static final String TAG = "DeviceLockServiceImpl";

    private final Context mContext;

    private final ServiceInfo mServiceInfo;

    @SuppressWarnings("unused") // TODO: remove annotation once field is used
    private final DeviceLockControllerConnector mDeviceLockControllerConnector;

    private static final String SERVICE_ACTION =
            "android.app.action.DEVICE_LOCK_CONTROLLER_SERVICE";

    // resources.arsc still uses the original package name (b/147434671)
    private static final String RESOURCE_PACKAGE_NAME = "com.android.devicelockcontroller";

    private static final UserHandle USER_HANDLE_SYSTEM = UserHandle.of(0);

    private volatile boolean mIsDeviceLocked = false;

    // Last supported device id type
    private static final @DeviceIdType int LAST_DEVICE_ID_TYPE = DEVICE_ID_TYPE_MEID;

    private static @Nullable ServiceInfo findService(@NonNull Context context,
            @NonNull String serviceAction,
            @NonNull StringBuilder errorMessage) {
        final Intent intent = new Intent(serviceAction);

        final PackageManager pm = context.getPackageManager();

        errorMessage.setLength(0);
        final List<ResolveInfo> resolveInfoList = pm.queryIntentServicesAsUser(intent,
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE, USER_HANDLE_SYSTEM);

        if (resolveInfoList == null || resolveInfoList.isEmpty()) {
            errorMessage.append("Service with " + serviceAction + " not found.");

            return null;
        }

        ServiceInfo resultServiceInfo = null;

        for (ResolveInfo resolveInfo: resolveInfoList) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;

            if ((serviceInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                continue;
            }

            if (resultServiceInfo != null) {
                errorMessage.append("Multiple system services handle " + serviceAction + ".");

                return null;
            }

            resultServiceInfo = serviceInfo;
        }

        return resultServiceInfo;
    }

    DeviceLockServiceImpl(@NonNull Context context) {
        mContext = context;

        StringBuilder errorMessage = new StringBuilder();
        mServiceInfo = findService(context, SERVICE_ACTION, errorMessage);

        if (mServiceInfo == null) {
            mDeviceLockControllerConnector = null;

            Slog.e(TAG, errorMessage.toString());

            return;
        }

        ComponentName componentName = new ComponentName(mServiceInfo.packageName,
                mServiceInfo.name);

        mDeviceLockControllerConnector = new DeviceLockControllerConnector(context, componentName);
    }

    /* Get the allowed device id type bitmap or -1 if it cannot be determined */
    private int getDeviceIdTypeBitmap() {
        final long identity = Binder.clearCallingIdentity();
        final PackageManager pm = mContext.getPackageManager();
        int deviceIdType = -1;
        try {
            final Resources resources = pm.getResourcesForApplication(mServiceInfo.packageName);
            final int resId = resources.getIdentifier("device_id_type_bitmap", "integer",
                    RESOURCE_PACKAGE_NAME);
            if (resId == 0) {
                Slog.e(TAG, "Cannot get device_id_type_bitmap");

                return -1;
            }
            deviceIdType = resources.getInteger(resId);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Cannot get resources for package: " + mServiceInfo.packageName);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }

        return deviceIdType;
    }

    private boolean checkCallerPermission() {
        return mContext.checkCallingOrSelfPermission(Manifest.permission.MANAGE_DEVICE_LOCK_STATE)
                == PERMISSION_GRANTED;
    }

    @Override
    public void lockDevice(@NonNull ILockUnlockDeviceCallback callback) {
        if (!checkCallerPermission()) {
            try {
                callback.onError(ILockUnlockDeviceCallback.ERROR_SECURITY);
            } catch (RemoteException e) {
                Slog.e(TAG, "lockDevice() - Unable to send error to the callback", e);
            }
            return;
        }

        // TODO: lock the device.
        mIsDeviceLocked = true;
        final boolean lockSuccessful = true;

        try {
            if (lockSuccessful) {
                callback.onDeviceLockedUnlocked();
            } else {
                callback.onError(ILockUnlockDeviceCallback.ERROR_UNKNOWN);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "lockDevice() - Unable to send result to the callback", e);
        }
    }

    @Override
    public void unlockDevice(@NonNull ILockUnlockDeviceCallback callback) {
        if (!checkCallerPermission()) {
            try {
                callback.onError(ILockUnlockDeviceCallback.ERROR_SECURITY);
            } catch (RemoteException e) {
                Slog.e(TAG, "unlockDevice() - Unable to send error to the callback", e);
            }
            return;
        }
        // TODO: unlock the device.
        mIsDeviceLocked = false;
        final boolean unlockSuccessful = true;

        try {
            if (unlockSuccessful) {
                callback.onDeviceLockedUnlocked();
            } else {
                callback.onError(ILockUnlockDeviceCallback.ERROR_UNKNOWN);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "unlockDevice() - Unable to send result to the callback", e);
        }
    }

    @Override
    public void isDeviceLocked(@NonNull IIsDeviceLockedCallback callback) {
        if (!checkCallerPermission()) {
            try {
                callback.onError(IIsDeviceLockedCallback.ERROR_SECURITY);
            } catch (RemoteException e) {
                Slog.e(TAG, "isDeviceLocked() - Unable to send error to the callback", e);
            }
            return;
        }

        // TODO: report the correct state.
        final boolean isLocked = mIsDeviceLocked;

        try {
            callback.onIsDeviceLocked(isLocked);
        } catch (RemoteException e) {
            Slog.e(TAG, "isDeviceLocked() - Unable to send result to the callback", e);
        }
    }

    @VisibleForTesting
    void getDeviceId(@NonNull IGetDeviceIdCallback callback, int deviceIdTypeBitmap) {
        try {
            if (deviceIdTypeBitmap < 0 || deviceIdTypeBitmap >= (1 << (LAST_DEVICE_ID_TYPE + 1))) {
                callback.onError(IGetDeviceIdCallback.ERROR_INVALID_DEVICE_ID_TYPE_BITMAP);

                return;
            }

            final TelephonyManager telephonyManager =
                    mContext.getSystemService(TelephonyManager.class);
            if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_IMEI)) != 0) {
                final String imei = telephonyManager.getImei();

                if (imei != null) {
                    callback.onDeviceIdReceived(DEVICE_ID_TYPE_IMEI, imei);

                    return;
                }
            }

            if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_MEID)) != 0) {
                final String meid = telephonyManager.getMeid();

                if (meid != null) {
                    callback.onDeviceIdReceived(DEVICE_ID_TYPE_MEID, meid);

                    return;
                }
            }

            callback.onError(IGetDeviceIdCallback.ERROR_CANNOT_GET_DEVICE_ID);

        } catch (RemoteException e) {
            Slog.e(TAG, "getDeviceId() - Unable to send result to the callback", e);
        }
    }

    @Override
    public void getDeviceId(@NonNull IGetDeviceIdCallback callback) {
        if (!checkCallerPermission()) {
            try {
                callback.onError(IGetDeviceIdCallback.ERROR_SECURITY);
            } catch (RemoteException e) {
                Slog.e(TAG, "getDeviceId() - Unable to send error to the callback", e);
            }
            return;
        }

        final int deviceIdTypeBitmap = getDeviceIdTypeBitmap();

        getDeviceId(callback, deviceIdTypeBitmap);
    }

    @Override
    public void getKioskApps(@NonNull IGetKioskAppsCallback callback) {
        // Caller is not necessarily a kiosk app, and no particular permission enforcing is needed.

        // TODO: return proper kiosk app info.
        ArrayMap kioskApps = new ArrayMap<Integer, String>();

        try {
            callback.onKioskAppsReceived(kioskApps);
        } catch (RemoteException e) {
            Slog.e(TAG, "getKioskApps() - Unable to send result to the callback", e);
        }
    }
}
