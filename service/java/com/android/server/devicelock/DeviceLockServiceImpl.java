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

import static android.app.AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION;
import static android.app.role.RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP;
import static android.content.IntentFilter.SYSTEM_HIGH_PRIORITY;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_IMEI;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_MEID;

import android.Manifest;
import android.annotation.NonNull;
import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.ServiceInfo;
import android.devicelock.DeviceId.DeviceIdType;
import android.devicelock.DeviceLockManager;
import android.devicelock.IDeviceLockService;
import android.devicelock.IGetDeviceIdCallback;
import android.devicelock.IGetKioskAppsCallback;
import android.devicelock.IIsDeviceLockedCallback;
import android.devicelock.ILockUnlockDeviceCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link android.devicelock.IDeviceLockService} binder service.
 */
final class DeviceLockServiceImpl extends IDeviceLockService.Stub {
    private static final String TAG = "DeviceLockServiceImpl";

    private static final String ACTION_DEVICE_LOCK_KIOSK_KEEPALIVE =
            "com.android.devicelock.action.KEEPALIVE";

    // Workaround for timeout while adding the kiosk app as role holder for financing.
    private static final int MAX_ADD_ROLE_HOLDER_TRIES = 4;

    private final Context mContext;

    private final RoleManager mRoleManager;
    private final TelephonyManager mTelephonyManager;
    private final AppOpsManager mAppOpsManager;

    // Map user id -> DeviceLockControllerConnector
    private final ArrayMap<Integer, DeviceLockControllerConnector> mDeviceLockControllerConnectors;

    private final DeviceLockControllerPackageUtils mPackageUtils;

    private final ServiceInfo mServiceInfo;

    // Map user id -> ServiceConnection for kiosk keepalive.
    private final ArrayMap<Integer, KioskKeepaliveServiceConnection>
            mKioskKeepaliveServiceConnections;

    private final DeviceLockPersistentStore mPersistentStore;

    // The following should be a SystemApi on AppOpsManager.
    private static final String OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION =
            "android:system_exempt_from_activity_bg_start_restriction";

    // Stopgap: this receiver should be replaced by an API on DeviceLockManager.
    private final class DeviceLockClearReceiver extends BroadcastReceiver {
        static final String ACTION_CLEAR = "com.android.devicelock.intent.action.CLEAR";
        static final int CLEAR_SUCCEEDED = 0;
        static final int CLEAR_FAILED = 1;

        @Override
        public void onReceive(Context context, Intent intent) {
            Slog.i(TAG, "Received request to clear device");

            // This receiver should be the only one.
            // The result will still be sent to the 'resultReceiver' of 'sendOrderedBroadcast'.
            abortBroadcast();

            final UserHandle userHandle = getSendingUser();

            final PendingResult pendingResult = goAsync();

            getDeviceLockControllerConnector(userHandle)
                    .clearDeviceRestrictions(new OutcomeReceiver<>() {

                        private void setResult(int resultCode) {
                            pendingResult.setResultCode(resultCode);

                            pendingResult.finish();
                        }

                        @Override
                        public void onResult(Void ignored) {
                            Slog.i(TAG, "Device cleared ");

                            setResult(DeviceLockClearReceiver.CLEAR_SUCCEEDED);
                        }

                        @Override
                        public void onError(Exception ex) {
                            Slog.e(TAG, "Exception clearing device: ", ex);

                            setResult(DeviceLockClearReceiver.CLEAR_FAILED);
                        }
                    });
        }
    }

    // Last supported device id type
    private static final @DeviceIdType int LAST_DEVICE_ID_TYPE = DEVICE_ID_TYPE_MEID;

    private static final String MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER =
            "com.android.devicelockcontroller.permission."
                    + "MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER";

    @NonNull
    private DeviceLockControllerConnector getDeviceLockControllerConnector(UserHandle userHandle) {
        final int userId = userHandle.getIdentifier();

        synchronized (this) {
            DeviceLockControllerConnector deviceLockControllerConnector =
                    mDeviceLockControllerConnectors.get(userId);
            if (deviceLockControllerConnector == null) {
                final ComponentName componentName = new ComponentName(mServiceInfo.packageName,
                        mServiceInfo.name);
                deviceLockControllerConnector = new DeviceLockControllerConnector(mContext,
                        componentName, userHandle);
                mDeviceLockControllerConnectors.put(userId, deviceLockControllerConnector);
            }

            return deviceLockControllerConnector;
        }
    }

    @NonNull
    private DeviceLockControllerConnector getDeviceLockControllerConnector() {
        final UserHandle userHandle = Binder.getCallingUserHandle();
        return getDeviceLockControllerConnector(userHandle);
    }

    DeviceLockServiceImpl(@NonNull Context context) {
        mContext = context;

        mRoleManager = context.getSystemService(RoleManager.class);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);

        mDeviceLockControllerConnectors = new ArrayMap<>();

        mKioskKeepaliveServiceConnections = new ArrayMap<>();

        mPackageUtils = new DeviceLockControllerPackageUtils(context);

        mPersistentStore = new DeviceLockPersistentStore();

        final StringBuilder errorMessage = new StringBuilder();
        mServiceInfo = mPackageUtils.findService(errorMessage);

        if (mServiceInfo == null) {
            throw new RuntimeException(errorMessage.toString());
        }

        if (!mServiceInfo.applicationInfo.enabled) {
            enableDeviceLockControllerIfNeeded(UserHandle.SYSTEM);
        }

        final ComponentName componentName = new ComponentName(mServiceInfo.packageName,
                mServiceInfo.name);

        final IntentFilter intentFilter = new IntentFilter(DeviceLockClearReceiver.ACTION_CLEAR);
        // Run before any eventual app receiver (there should be none).
        intentFilter.setPriority(SYSTEM_HIGH_PRIORITY);
        context.registerReceiverForAllUsers(new DeviceLockClearReceiver(), intentFilter,
                Manifest.permission.MANAGE_DEVICE_LOCK_STATE, null /* scheduler */,
                Context.RECEIVER_EXPORTED);
    }

    void enableDeviceLockControllerIfNeeded(@NonNull UserHandle userHandle) {
        mPersistentStore.readFinalizedState(isFinalized -> {
            if (!isFinalized) {
                setDeviceLockControllerPackageDefaultEnabledState(userHandle);
            }
        }, mContext.getMainExecutor());
    }

    private void setDeviceLockControllerPackageDefaultEnabledState(UserHandle userHandle) {
        final String controllerPackageName = mServiceInfo.packageName;

        Context controllerContext;
        try {
            controllerContext = mContext.createPackageContextAsUser(controllerPackageName,
                    0 /* flags */, userHandle);
        } catch (NameNotFoundException e) {
            Slog.e(TAG, "Cannot create package context for: " + userHandle, e);

            return;
        }

        final PackageManager controllerPackageManager = controllerContext.getPackageManager();

        // We cannot check if user control is disabled since
        // DevicePolicyManager.getUserControlDisabledPackages() acts on the calling user.
        // Additionally, we would have to catch SecurityException anyways to avoid TOCTOU bugs
        // since checking and setting is not atomic.
        try {
            controllerPackageManager.setApplicationEnabledSetting(controllerPackageName,
                    COMPONENT_ENABLED_STATE_DEFAULT, DONT_KILL_APP);
        } catch (SecurityException ex) {
            // This exception is thrown when Device Lock Controller has already enabled
            // package protection for itself. This is an expected behaviour.
            // Note: the exception description thrown by
            // PackageManager.setApplicationEnabledSetting() is somehow misleading because it says
            // that a protected package cannot be disabled (but we're actually trying to enable it).
        }
    }

    void onUserStarting(@NonNull UserHandle userHandle) {
        getDeviceLockControllerConnector(userHandle).onUserStarting(
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void ignored) {
                        Slog.i(TAG, "User switching reported for: " + userHandle);
                    }

                    @Override
                    public void onError(Exception ex) {
                        Slog.e(TAG, "Exception reporting user switching for: " + userHandle, ex);
                    }
                });
    }

    void onUserSwitching(@NonNull UserHandle userHandle) {
        getDeviceLockControllerConnector(userHandle).onUserSwitching(
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void ignored) {
                        Slog.i(TAG, "User switching reported for: " + userHandle);
                    }

                    @Override
                    public void onError(Exception ex) {
                        Slog.e(TAG, "Exception reporting user switching for: " + userHandle, ex);
                    }
                });
    }

    void onUserUnlocked(@NonNull UserHandle userHandle) {
        getDeviceLockControllerConnector(userHandle).onUserUnlocked(
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void ignored) {
                        Slog.i(TAG, "User unlocked reported for: " + userHandle);
                    }

                    @Override
                    public void onError(Exception ex) {
                        Slog.e(TAG, "Exception reporting user unlocked for: " + userHandle, ex);
                    }
                });
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

    private OutcomeReceiver<Void, Exception> getLockUnlockOutcomeReceiver(
            @NonNull ILockUnlockDeviceCallback callback, @NonNull String successMessage) {
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

        getDeviceLockControllerConnector().lockDevice(
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

        getDeviceLockControllerConnector().unlockDevice(
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

        getDeviceLockControllerConnector().isDeviceLocked(new OutcomeReceiver<>() {
            @Override
            public void onResult(Boolean isLocked) {
                Slog.i(TAG, isLocked ? "Device is locked" : "Device is not locked");
                try {
                    callback.onIsDeviceLocked(isLocked);
                } catch (RemoteException e) {
                    Slog.e(TAG, "isDeviceLocked() - Unable to send result to the " + "callback", e);
                }
            }

            @Override
            public void onError(Exception ex) {
                Slog.e(TAG, "Exception: ", ex);
                try {
                    callback.onError(ILockUnlockDeviceCallback.ERROR_UNKNOWN);
                } catch (RemoteException e) {
                    Slog.e(TAG, "isDeviceLocked() - Unable to send error to the " + "callback", e);
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
        } catch (RemoteException e) {
            Slog.e(TAG, "getDeviceId() - Unable to send result to the callback", e);
        }

        int activeModemCount = mTelephonyManager.getActiveModemCount();
        List<String> imeiList = new ArrayList<String>();
        List<String> meidList = new ArrayList<String>();

        if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_IMEI)) != 0) {
            for (int i = 0; i < activeModemCount; i++) {
                String imei = mTelephonyManager.getImei(i);
                if (!TextUtils.isEmpty(imei)) {
                    imeiList.add(imei);
                }
            }
        }

        if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_MEID)) != 0) {
            for (int i = 0; i < activeModemCount; i++) {
                String meid = mTelephonyManager.getMeid(i);
                if (!TextUtils.isEmpty(meid)) {
                    meidList.add(meid);
                }
            }
        }

        getDeviceLockControllerConnector().getDeviceId(new OutcomeReceiver<>() {
            @Override
            public void onResult(String deviceId) {
                Slog.i(TAG, "Get Device ID ");
                try {
                    if (meidList.contains(deviceId)) {
                        callback.onDeviceIdReceived(DEVICE_ID_TYPE_MEID, deviceId);
                        return;
                    }
                    if (imeiList.contains(deviceId)) {
                        callback.onDeviceIdReceived(DEVICE_ID_TYPE_IMEI, deviceId);
                        return;
                    }
                    // When a device ID is returned from DLC App, but none of the IDs got from
                    // TelephonyManager matches that device ID.
                    //
                    // TODO(b/270392813): Send the device ID back to the callback with
                    //  UNSPECIFIED device ID type.
                    callback.onError(IGetDeviceIdCallback.ERROR_CANNOT_GET_DEVICE_ID);
                } catch (RemoteException e) {
                    Slog.e(TAG, "getDeviceId() - Unable to send result to the callback", e);
                }
            }

            @Override
            public void onError(Exception ex) {
                Slog.e(TAG, "Exception: ", ex);
                try {
                    callback.onError(IGetDeviceIdCallback.ERROR_CANNOT_GET_DEVICE_ID);
                } catch (RemoteException e) {
                    Slog.e(TAG,
                            "getDeviceId() - " + "Unable to send error to" + " the " + "callback",
                            e);
                }
            }
        });
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

        final ArrayMap kioskApps = new ArrayMap<Integer, String>();

        final UserHandle userHandle = Binder.getCallingUserHandle();
        final long identity = Binder.clearCallingIdentity();
        try {
            List<String> roleHolders = mRoleManager.getRoleHoldersAsUser(
                    RoleManager.ROLE_FINANCED_DEVICE_KIOSK, userHandle);

            if (!roleHolders.isEmpty()) {
                kioskApps.put(DeviceLockManager.DEVICE_LOCK_ROLE_FINANCING, roleHolders.get(0));
            }

            callback.onKioskAppsReceived(kioskApps);
        } catch (RemoteException e) {
            Slog.e(TAG, "getKioskApps() - Unable to send result to the callback", e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    // For calls from Controller to System Service.

    private void reportErrorToCaller(@NonNull RemoteCallback remoteCallback) {
        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, false);
        remoteCallback.sendResult(result);
    }

    private boolean checkDeviceLockControllerPermission(@NonNull RemoteCallback remoteCallback) {
        if (mContext.checkCallingOrSelfPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
                != PERMISSION_GRANTED) {
            reportErrorToCaller(remoteCallback);
            return false;
        }

        return true;
    }

    private void reportResult(boolean accepted, long identity,
            @NonNull RemoteCallback remoteCallback) {
        Binder.restoreCallingIdentity(identity);

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, accepted);
        remoteCallback.sendResult(result);
    }

    private void addFinancedDeviceKioskRoleInternal(@NonNull String packageName,
            @NonNull RemoteCallback remoteCallback, @NonNull UserHandle userHandle, long identity,
            int remainingTries) {
        mRoleManager.addRoleHolderAsUser(RoleManager.ROLE_FINANCED_DEVICE_KIOSK, packageName,
                MANAGE_HOLDERS_FLAG_DONT_KILL_APP, userHandle,
                mContext.getMainExecutor(), accepted -> {
                    if (accepted || remainingTries == 1) {
                        reportResult(accepted, identity, remoteCallback);
                    } else {
                        final int retryNumber = MAX_ADD_ROLE_HOLDER_TRIES - remainingTries + 1;
                        Slog.w(TAG, "Retrying adding financed device role to kiosk app (retry "
                                + retryNumber + ")");
                        addFinancedDeviceKioskRoleInternal(packageName, remoteCallback, userHandle,
                                identity, remainingTries - 1);
                    }
            });
    }

    @Override
    public void addFinancedDeviceKioskRole(@NonNull String packageName,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }

        final UserHandle userHandle = Binder.getCallingUserHandle();
        final long identity = Binder.clearCallingIdentity();

        addFinancedDeviceKioskRoleInternal(packageName, remoteCallback, userHandle,
                identity, MAX_ADD_ROLE_HOLDER_TRIES);

        Binder.restoreCallingIdentity(identity);
    }

    @Override
    public void removeFinancedDeviceKioskRole(@NonNull String packageName,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }

        final UserHandle userHandle = Binder.getCallingUserHandle();
        final long identity = Binder.clearCallingIdentity();

        mRoleManager.removeRoleHolderAsUser(RoleManager.ROLE_FINANCED_DEVICE_KIOSK, packageName,
                MANAGE_HOLDERS_FLAG_DONT_KILL_APP, userHandle, mContext.getMainExecutor(),
                accepted -> reportResult(accepted, identity, remoteCallback));

        Binder.restoreCallingIdentity(identity);
    }

    private void setExemption(String packageName, int uid, String appOp, boolean exempt,
            @NonNull RemoteCallback remoteCallback) {
        final long identity = Binder.clearCallingIdentity();

        final int mode = exempt ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_DEFAULT;

        mAppOpsManager.setMode(appOp, uid, packageName, mode);

        Binder.restoreCallingIdentity(identity);

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, true);
        remoteCallback.sendResult(result);
    }

    @Override
    public void setExemptFromActivityBackgroundStartRestriction(boolean exempt,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }

        setExemption(mServiceInfo.packageName, Binder.getCallingUid(),
                OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION, exempt, remoteCallback);
    }

    @Override
    public void setExemptFromHibernation(String packageName, boolean exempt,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }

        final UserHandle controllerUserHandle = Binder.getCallingUserHandle();
        final int controllerUserId = controllerUserHandle.getIdentifier();
        final PackageManager packageManager = mContext.getPackageManager();
        int kioskUid;
        final long identity = Binder.clearCallingIdentity();
        try {
            kioskUid = packageManager.getPackageUidAsUser(packageName, PackageInfoFlags.of(0),
                    controllerUserId);
        } catch (NameNotFoundException e) {
            Binder.restoreCallingIdentity(identity);
            Slog.e(TAG, "Failed to set hibernation appop", e);
            reportErrorToCaller(remoteCallback);
            return;
        }
        Binder.restoreCallingIdentity(identity);

        setExemption(packageName, kioskUid, OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION, exempt,
                remoteCallback);
    }

    private class KioskKeepaliveServiceConnection implements ServiceConnection {
        final UserHandle mUserHandle;

        final Intent mService;

        KioskKeepaliveServiceConnection(String packageName, UserHandle userHandle) {
            super();
            mUserHandle = userHandle;
            mService = new Intent(ACTION_DEVICE_LOCK_KIOSK_KEEPALIVE).setPackage(packageName);
        }

        private boolean bind() {
            return mContext.bindServiceAsUser(mService, this, Context.BIND_AUTO_CREATE,
                    mUserHandle);
        }

        private boolean rebind() {
            mContext.unbindService(this);
            boolean bound = bind();

            if (bound) {
                getDeviceLockControllerConnector(mUserHandle)
                        .onKioskAppCrashed(new OutcomeReceiver<>() {
                            @Override
                            public void onResult(Void result) {
                                Slog.i(TAG, "Notified controller about kiosk app crash");
                            }

                            @Override
                            public void onError(Exception ex) {
                                Slog.e(TAG, "On kiosk app crashed error: ", ex);
                            }
                });
            }

            return bound;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Slog.i(TAG, "Kiosk keepalive successful for user " + mUserHandle);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (rebind()) {
                Slog.i(TAG, "onServiceDisconnected rebind successful for user " + mUserHandle);
            } else {
                Slog.e(TAG, "onServiceDisconnected rebind failed for user " + mUserHandle);
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            ServiceConnection.super.onBindingDied(name);
            if (rebind()) {
                Slog.i(TAG, "onBindingDied rebind successful for user " + mUserHandle);
            } else {
                Slog.e(TAG, "onBindingDied rebind failed for user " + mUserHandle);
            }
        }
    }

    @Override
    public void enableKioskKeepalive(String packageName, @NonNull RemoteCallback remoteCallback) {
        final UserHandle controllerUserHandle = Binder.getCallingUserHandle();
        final int controllerUserId = controllerUserHandle.getIdentifier();
        boolean keepaliveEnabled = false;
        synchronized (this) {
            if (mKioskKeepaliveServiceConnections.get(controllerUserId) == null) {
                final KioskKeepaliveServiceConnection serviceConnection =
                        new KioskKeepaliveServiceConnection(packageName, controllerUserHandle);
                final long identity = Binder.clearCallingIdentity();
                if (serviceConnection.bind()) {
                    mKioskKeepaliveServiceConnections.put(controllerUserId, serviceConnection);
                    keepaliveEnabled = true;
                } else {
                    Slog.w(TAG, "enableKioskKeepalive: failed to bind to keepalive service for "
                            + "user " + controllerUserHandle);
                    mContext.unbindService(serviceConnection);
                }
                Binder.restoreCallingIdentity(identity);
            } else {
                // Consider success if we already have an entry for this user id.
                keepaliveEnabled = true;
            }
        }

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, keepaliveEnabled);
        remoteCallback.sendResult(result);
    }

    @Override
    public void disableKioskKeepalive(@NonNull RemoteCallback remoteCallback) {
        final UserHandle controllerUserHandle = Binder.getCallingUserHandle();
        final int controllerUserId = controllerUserHandle.getIdentifier();
        final KioskKeepaliveServiceConnection serviceConnection;

        synchronized (this) {
            serviceConnection = mKioskKeepaliveServiceConnections.remove(controllerUserId);
        }

        if (serviceConnection != null) {
            final long identity = Binder.clearCallingIdentity();
            mContext.unbindService(serviceConnection);
            Binder.restoreCallingIdentity(identity);
        } else {
            Slog.e(TAG, "disableKioskKeepalive: Service connection not found for user "
                    + controllerUserHandle);
        }

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, serviceConnection != null);
        remoteCallback.sendResult(result);
    }

    @Override
    public void setDeviceFinalized(boolean finalized, @NonNull RemoteCallback remoteCallback) {
        mPersistentStore.scheduleWrite(finalized);

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, true);
        remoteCallback.sendResult(result);
    }
}
