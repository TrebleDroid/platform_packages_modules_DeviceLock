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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_IMEI;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_MEID;

import android.Manifest;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.devicelock.DeviceId.DeviceIdType;
import android.devicelock.IDeviceLockService;
import android.devicelock.IGetDeviceIdCallback;
import android.devicelock.IGetKioskAppsCallback;
import android.devicelock.IIsDeviceLockedCallback;
import android.devicelock.ILockUnlockDeviceCallback;
import android.os.Binder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Implementation of {@link android.devicelock.IDeviceLockService} binder service.
 */
final class DeviceLockServiceImpl extends IDeviceLockService.Stub {
    private static final String TAG = "DeviceLockServiceImpl";

    private final Context mContext;

    private final DeviceLockControllerConnector mDeviceLockControllerConnector;

    private final DeviceLockControllerPackageUtils mPackageUtils;

    // Last supported device id type
    private static final @DeviceIdType int LAST_DEVICE_ID_TYPE = DEVICE_ID_TYPE_MEID;

    DeviceLockServiceImpl(@NonNull Context context) {
        mContext = context;

        mPackageUtils = new DeviceLockControllerPackageUtils(context);

        final StringBuilder errorMessage = new StringBuilder();
        final ServiceInfo serviceInfo = mPackageUtils.findService(errorMessage);

        if (serviceInfo == null) {
            mDeviceLockControllerConnector = null;

            Slog.e(TAG, errorMessage.toString());

            return;
        }

        final ComponentName componentName = new ComponentName(serviceInfo.packageName,
                serviceInfo.name);

        mDeviceLockControllerConnector = new DeviceLockControllerConnector(context, componentName);
    }

    private boolean checkCallerPermission() {
        return mContext.checkCallingOrSelfPermission(Manifest.permission.MANAGE_DEVICE_LOCK_STATE)
                == PERMISSION_GRANTED;
    }

    private void reportDeviceLockedUnlocked(@NonNull ILockUnlockDeviceCallback callback,
            boolean success) {
        try {
            if (success) {
                callback.onDeviceLockedUnlocked();
            } else {
                callback.onError(ILockUnlockDeviceCallback.ERROR_UNKNOWN);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private OutcomeReceiver<Void, Exception>
            getLockUnlockOutcomeReceiver(@NonNull ILockUnlockDeviceCallback callback,
                @NonNull String successMessage) {
        return new OutcomeReceiver<>() {
            @Override
            public void onResult(Void ignored) {
                Slog.i(TAG, successMessage);
                reportDeviceLockedUnlocked(callback, true /* success */);
            }

            @Override
            public void onError(Exception ex) {
                Slog.e(TAG, "Exception: ", ex);
                reportDeviceLockedUnlocked(callback, false /* success */);
            }
        };
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

        mDeviceLockControllerConnector.lockDevice(
                getLockUnlockOutcomeReceiver(callback, "Device locked"));
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

        mDeviceLockControllerConnector.unlockDevice(
                getLockUnlockOutcomeReceiver(callback, "Device unlocked"));
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

        mDeviceLockControllerConnector.isDeviceLocked(
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean isLocked) {
                        Slog.i(TAG, "Device Locked ");
                        try {
                            callback.onIsDeviceLocked(isLocked);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "isDeviceLocked() - Unable to send result to the "
                                    + "callback", e);
                        }
                    }

                    @Override
                    public void onError(Exception ex) {
                            Slog.e(TAG, "Exception: ", ex);
                            try {
                                callback.onError(ILockUnlockDeviceCallback.ERROR_UNKNOWN);
                            } catch (RemoteException e) {
                                Slog.e(TAG, "isDeviceLocked() - Unable to send error to the "
                                        + "callback", e);
                            }
                        }
                });
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

        final StringBuilder errorBuilder = new StringBuilder();

        final long identity = Binder.clearCallingIdentity();
        final int deviceIdTypeBitmap = mPackageUtils.getDeviceIdTypeBitmap(errorBuilder);
        Binder.restoreCallingIdentity(identity);

        if (deviceIdTypeBitmap < 0) {
            Slog.e(TAG, "getDeviceId: " + errorBuilder);
        }

        getDeviceId(callback, deviceIdTypeBitmap);
    }

    @Override
    public void getKioskApps(@NonNull IGetKioskAppsCallback callback) {
        // Caller is not necessarily a kiosk app, and no particular permission enforcing is needed.

        // TODO: return proper kiosk app info.
        final ArrayMap kioskApps = new ArrayMap<Integer, String>();

        try {
            callback.onKioskAppsReceived(kioskApps);
        } catch (RemoteException e) {
            Slog.e(TAG, "getKioskApps() - Unable to send result to the callback", e);
        }
    }
}
