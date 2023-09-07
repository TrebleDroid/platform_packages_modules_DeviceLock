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

import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_PROVISIONING;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_SUBSIDY_PROVISIONING;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.CLEARED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNLOCKED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.KIOSK_PROVISIONED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;
import static com.android.devicelockcontroller.policy.StartLockTaskModeWorker.START_LOCK_TASK_MODE_WORKER_RETRY_INTERVAL_SECONDS;
import static com.android.devicelockcontroller.policy.StartLockTaskModeWorker.START_LOCK_TASK_MODE_WORK_NAME;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.SystemDeviceLockManagerImpl;
import com.android.devicelockcontroller.activities.LandingActivity;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * An implementation of {@link DevicePolicyController}. This class guarantees thread safety by
 * synchronizing policies enforcement on background threads in the order of when the API calls
 * happen. That is, a pre-exist enforcement request will always blocks a incoming enforcement
 * request until the former completes.
 */
public final class DevicePolicyControllerImpl implements DevicePolicyController {
    private static final String TAG = "DevicePolicyControllerImpl";

    private final List<PolicyHandler> mPolicyList = new ArrayList<>();
    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final ProvisionStateController mProvisionStateController;
    // A future that returns the current lock task type for the current provision/device state
    // after policies enforcement are done.
    @GuardedBy("this")
    private ListenableFuture<@LockTaskType Integer> mCurrentEnforcedLockTaskTypeFuture =
            Futures.immediateFuture(LockTaskType.UNDEFINED);
    private final Executor mBgExecutor;
    private static final String ACTION_DEVICE_LOCK_KIOSK_SETUP =
            "com.android.devicelock.action.KIOSK_SETUP";
    private final UserManager mUserManager;

    /**
     * Create a new policy controller.
     *
     * @param context                  The context used by this policy controller.
     * @param provisionStateController The user state controller.
     */
    public DevicePolicyControllerImpl(Context context,
            ProvisionStateController provisionStateController, Executor bgExecutor) {
        this(context, provisionStateController,
                SystemDeviceLockManagerImpl.getInstance(), bgExecutor);
    }

    @VisibleForTesting
    DevicePolicyControllerImpl(Context context,
            ProvisionStateController provisionStateController,
            SystemDeviceLockManager systemDeviceLockManager, Executor bgExecutor) {
        mContext = context;
        mProvisionStateController = provisionStateController;
        mBgExecutor = bgExecutor;
        mDpm = context.getSystemService(DevicePolicyManager.class);
        mUserManager = Objects.requireNonNull(mContext.getSystemService(UserManager.class));
        mPolicyList.add(new UserRestrictionsPolicyHandler(mDpm,
                context.getSystemService(UserManager.class), Build.isDebuggable(), bgExecutor));
        mPolicyList.add(new AppOpsPolicyHandler(systemDeviceLockManager, bgExecutor));
        mPolicyList.add(new LockTaskModePolicyHandler(context, mDpm, bgExecutor));
        mPolicyList.add(new PackagePolicyHandler(context, mDpm, bgExecutor));
        mPolicyList.add(new RolePolicyHandler(systemDeviceLockManager, bgExecutor));
        mPolicyList.add(new KioskKeepAlivePolicyHandler(systemDeviceLockManager, bgExecutor));
    }

    @Override
    public boolean wipeDevice() {
        LogUtil.i(TAG, "Wiping device");
        try {
            mDpm.wipeDevice(DevicePolicyManager.WIPE_SILENTLY
                    | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA);
        } catch (SecurityException e) {
            LogUtil.e(TAG, "Cannot wipe device", e);
            return false;
        }
        return true;
    }

    @Override
    public ListenableFuture<Void> enforceCurrentPolicies() {
        synchronized (this) {
            // current lock task type must be assigned to a local variable; otherwise, if
            // retrieved down the execution flow, it will be returning the new type after execution.
            ListenableFuture<@LockTaskType Integer> currentLockTaskType =
                    mCurrentEnforcedLockTaskTypeFuture;
            ListenableFuture<@LockTaskType Integer> policiesEnforcementFuture =
                    Futures.transformAsync(
                            currentLockTaskType,
                            unused -> Futures.transformAsync(
                                    mProvisionStateController.getState(),
                                    this::enforcePoliciesForProvisionState, mBgExecutor),
                            mBgExecutor);
            // To prevent exception propagate to future policies enforcement, catch any exceptions
            // that might happen during the execution and fallback to previous type if exception
            // happens.
            mCurrentEnforcedLockTaskTypeFuture = Futures.catchingAsync(policiesEnforcementFuture,
                    Exception.class, unused -> currentLockTaskType,
                    MoreExecutors.directExecutor());
            return Futures.transformAsync(policiesEnforcementFuture,
                    this::startLockTaskModeIfNeeded,
                    mBgExecutor);
        }
    }

    private ListenableFuture<@LockTaskType Integer> enforcePoliciesForProvisionState(
            @ProvisionState int stateToEnforce) {
        LogUtil.i(TAG,
                "Enforcing policies for provision state: " + stateToEnforce);
        if (stateToEnforce == UNPROVISIONED) {
            return Futures.immediateFuture(null);
        } else if (stateToEnforce == PROVISION_SUCCEEDED) {
            return Futures.transformAsync(
                    GlobalParametersClient.getInstance().getDeviceState(),
                    this::enforcePoliciesForDeviceState,
                    mBgExecutor);
        }
        List<ListenableFuture<Boolean>> futures = new ArrayList<>();
        for (int i = 0, policyLen = mPolicyList.size(); i < policyLen; i++) {
            PolicyHandler policy = mPolicyList.get(i);
            switch (stateToEnforce) {
                case PROVISION_IN_PROGRESS:
                    futures.add(policy.onProvisionInProgress());
                    break;
                case KIOSK_PROVISIONED:
                    futures.add(policy.onProvisioned());
                    break;
                case PROVISION_PAUSED:
                    futures.add(policy.onProvisionPaused());
                    break;
                case PROVISION_FAILED:
                    futures.add(policy.onProvisionFailed());
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid provision state to enforce: " + stateToEnforce);
            }
        }
        return Futures.transform(Futures.allAsList(futures),
                results -> {
                    if (results.stream().reduce(true, (a, r) -> a && r)) {
                        if (stateToEnforce == PROVISION_IN_PROGRESS) {
                            return LockTaskType.LANDING_ACTIVITY;
                        } else if (stateToEnforce == KIOSK_PROVISIONED) {
                            return LockTaskType.KIOSK_SETUP_ACTIVITY;
                        } else {
                            return LockTaskType.NOT_IN_LOCK_TASK;
                        }
                    } else {
                        throw new RuntimeException(
                                "Failed to enforce polices for provision state: "
                                        + stateToEnforce);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<@LockTaskType Integer> enforcePoliciesForDeviceState(
            @DeviceState int stateToEnforce) {
        LogUtil.i(TAG, "Enforcing policies for device state: " + stateToEnforce);
        List<ListenableFuture<Boolean>> futures = new ArrayList<>();
        for (int i = 0, policyLen = mPolicyList.size(); i < policyLen; i++) {
            PolicyHandler policy = mPolicyList.get(i);
            switch (stateToEnforce) {
                case UNLOCKED:
                    futures.add(policy.onUnlocked());
                    break;
                case LOCKED:
                    futures.add(policy.onLocked());
                    break;
                case CLEARED:
                    futures.add(policy.onCleared());
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid device state to enforce: " + stateToEnforce);
            }
        }
        return Futures.transform(Futures.allAsList(futures),
                results -> {
                    if (results.stream().reduce(true, (a, r) -> a && r)) {
                        return stateToEnforce == LOCKED ? LockTaskType.KIOSK_LOCK_ACTIVITY :
                                LockTaskType.NOT_IN_LOCK_TASK;
                    } else {
                        throw new RuntimeException(
                                "Failed to enforce policies for device state: "
                                        + stateToEnforce);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Intent> getLockScreenActivityIntent() {
        return Futures.transform(
                SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackage -> {
                    if (kioskPackage == null) {
                        throw new IllegalStateException("Missing kiosk package parameter");
                    }
                    Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setPackage(kioskPackage);
                    PackageManager pm = mContext.getPackageManager();
                    ResolveInfo resolvedInfo = pm.resolveActivity(homeIntent,
                            PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolvedInfo != null && resolvedInfo.activityInfo != null) {
                        return homeIntent.setComponent(
                                new ComponentName(kioskPackage,
                                        resolvedInfo.activityInfo.name));
                    }
                    // Kiosk app does not have an activity to handle the default
                    // home intent. Fall back to the launch activity.
                    // Note that in this case, Kiosk App can't be effectively set as
                    // the default home activity.
                    Intent launchIntent = pm.getLaunchIntentForPackage(kioskPackage);
                    if (launchIntent == null) {
                        throw new IllegalStateException(
                                "Failed to get launch intent for kiosk app");
                    }
                    return launchIntent;
                }, mBgExecutor);
    }

    private ListenableFuture<Intent> getLandingActivityIntent() {
        SetupParametersClient client = SetupParametersClient.getInstance();
        ListenableFuture<@ProvisioningType Integer> provisioningType =
                client.getProvisioningType();
        return Futures.transform(provisioningType,
                type -> {
                    Intent resultIntent = new Intent(mContext, LandingActivity.class);
                    switch (type) {
                        case ProvisioningType.TYPE_FINANCED:
                            // TODO(b/288923554) this used to return an intent with action
                            // ACTION_START_DEVICE_FINANCING_SECONDARY_USER_PROVISIONING
                            // for secondary users. Rework once a decision has been made about
                            // what to show to users.
                            return resultIntent.setAction(
                                    ACTION_START_DEVICE_FINANCING_PROVISIONING);
                        case ProvisioningType.TYPE_SUBSIDY:
                            return resultIntent.setAction(ACTION_START_DEVICE_SUBSIDY_PROVISIONING);
                        case ProvisioningType.TYPE_UNDEFINED:
                        default:
                            throw new IllegalArgumentException("Provisioning type is unknown!");
                    }
                }, mBgExecutor);
    }

    private ListenableFuture<Intent> getKioskSetupActivityIntent() {
        return Futures.transform(SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackageName -> {
                    if (kioskPackageName == null) {
                        throw new IllegalStateException("Missing kiosk package parameter");
                    }
                    final Intent kioskSetupIntent = new Intent(ACTION_DEVICE_LOCK_KIOSK_SETUP);
                    kioskSetupIntent.setPackage(kioskPackageName);
                    final ResolveInfo resolveInfo = mContext.getPackageManager()
                            .resolveActivity(kioskSetupIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolveInfo == null || resolveInfo.activityInfo == null) {
                        throw new IllegalStateException(
                                "Failed to get setup activity intent for kiosk app");
                    }
                    return kioskSetupIntent.setComponent(new ComponentName(kioskPackageName,
                            resolveInfo.activityInfo.name));
                }, mBgExecutor);
    }

    @Override
    public ListenableFuture<Intent> getLaunchIntentForCurrentState() {
        return Futures.transformAsync(getCurrentEnforcedLockTaskType(),
                type -> {
                    switch (type) {
                        case LockTaskType.NOT_IN_LOCK_TASK:
                            return Futures.immediateFuture(null);
                        case LockTaskType.LANDING_ACTIVITY:
                            return getLandingActivityIntent();
                        case LockTaskType.KIOSK_SETUP_ACTIVITY:
                            return getKioskSetupActivityIntent();
                        case LockTaskType.KIOSK_LOCK_ACTIVITY:
                            return getLockScreenActivityIntent();
                        default:
                            throw new IllegalArgumentException("Invalid lock task type!");
                    }
                }, mBgExecutor);
    }

    private ListenableFuture<@LockTaskType Integer> getCurrentEnforcedLockTaskType() {
        synchronized (this) {
            ListenableFuture<@LockTaskType Integer> currentLockTaskType =
                    mCurrentEnforcedLockTaskTypeFuture;
            ListenableFuture<@LockTaskType Integer> resultFuture = Futures.transformAsync(
                    currentLockTaskType,
                    type -> type == LockTaskType.UNDEFINED ? initializeLockTaskType()
                            : Futures.immediateFuture(type),
                    mBgExecutor);
            mCurrentEnforcedLockTaskTypeFuture = Futures.catching(resultFuture, Exception.class,
                    unused -> LockTaskType.UNDEFINED, mBgExecutor);
            return resultFuture;
        }
    }

    @NonNull
    private ListenableFuture<@LockTaskType Integer> initializeLockTaskType() {
        return Futures.transformAsync(
                mProvisionStateController.getState(),
                this::enforcePoliciesForProvisionState, mBgExecutor);
    }

    @Override
    public ListenableFuture<Void> onUserUnlocked() {
        return Futures.transformAsync(getCurrentEnforcedLockTaskType(),
                this::startLockTaskModeIfNeeded,
                mBgExecutor);
    }

    private ListenableFuture<Void> startLockTaskModeIfNeeded(@LockTaskType Integer type) {
        if (type == LockTaskType.NOT_IN_LOCK_TASK || !mUserManager.isUserUnlocked()) {
            return Futures.immediateVoidFuture();
        }
        WorkManager workManager = WorkManager.getInstance(mContext);
        OneTimeWorkRequest startLockTask = new OneTimeWorkRequest.Builder(
                StartLockTaskModeWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR,
                        START_LOCK_TASK_MODE_WORKER_RETRY_INTERVAL_SECONDS)
                .build();
        return Futures.transform(workManager.enqueueUniqueWork(
                START_LOCK_TASK_MODE_WORK_NAME, ExistingWorkPolicy.REPLACE,
                startLockTask).getResult(), unused -> null, MoreExecutors.directExecutor());
    }
}
