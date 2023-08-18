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

import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_DEFERRED_PROVISIONING;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_PROVISIONING;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_SUBSIDY_DEFERRED_PROVISIONING;
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

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;
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
    @GuardedBy("this")
    private ListenableFuture<@ProvisionState Integer> mCurrentEnforcedProvisionStateFuture =
            Futures.immediateFuture(null);
    @GuardedBy("this")
    private ListenableFuture<@DeviceState Integer> mCurrentEnforcedDeviceStateFuture =
            Futures.immediateFuture(null);
    private final Executor mBgExecutor;
    private static final String ACTION_DEVICE_LOCK_KIOSK_SETUP =
            "com.android.devicelock.action.KIOSK_SETUP";

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
            mCurrentEnforcedProvisionStateFuture = Futures.transformAsync(
                    mCurrentEnforcedProvisionStateFuture,
                    enforcedState -> Futures.transformAsync(mProvisionStateController.getState(),
                            stateToEnforce -> enforcePoliciesForProvisionState(enforcedState,
                                    stateToEnforce), mBgExecutor), mBgExecutor);
            return Futures.transform(mCurrentEnforcedProvisionStateFuture, unused -> null,
                    MoreExecutors.directExecutor());
        }
    }

    private ListenableFuture<@ProvisionState Integer> enforcePoliciesForProvisionState(
            @ProvisionState Integer currentEnforcedState,
            @ProvisionState int stateToEnforce) {
        LogUtil.i(TAG, "Enforcing policies for provision state: " + stateToEnforce);
        if (stateToEnforce == UNPROVISIONED) {
            return Futures.immediateFuture(UNPROVISIONED);
        } else if (stateToEnforce == PROVISION_SUCCEEDED) {
            synchronized (this) {
                mCurrentEnforcedDeviceStateFuture = Futures.transformAsync(
                        mCurrentEnforcedDeviceStateFuture,
                        enforcedState -> Futures.transformAsync(
                                GlobalParametersClient.getInstance().getDeviceState(),
                                deviceState -> enforcePoliciesForDeviceState(enforcedState,
                                        deviceState),
                                mBgExecutor), mBgExecutor);
                return Futures.transform(mCurrentEnforcedDeviceStateFuture,
                        unused -> stateToEnforce, MoreExecutors.directExecutor());
            }
        }
        if (currentEnforcedState != null && currentEnforcedState == stateToEnforce) {
            return Futures.immediateFuture(stateToEnforce);
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
                        if (stateToEnforce == PROVISION_IN_PROGRESS
                                || stateToEnforce == KIOSK_PROVISIONED) {
                            StartLockTaskModeWorker.startLockTaskMode(
                                    WorkManager.getInstance(mContext));
                        }
                        return stateToEnforce;
                    } else {
                        throw new RuntimeException(
                                "Failed to enforce polices for provision state: "
                                        + stateToEnforce);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<@ProvisionState Integer> enforcePoliciesForDeviceState(
            @DeviceState Integer currentEnforcedState,
            @DeviceState int stateToEnforce) {
        LogUtil.i(TAG, "Enforcing policies for device state: " + stateToEnforce);
        if (currentEnforcedState != null && currentEnforcedState == stateToEnforce) {
            return Futures.immediateFuture(stateToEnforce);
        }
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
                        if (stateToEnforce == LOCKED) {
                            StartLockTaskModeWorker.startLockTaskMode(
                                    WorkManager.getInstance(mContext));
                        }
                        return stateToEnforce;
                    } else {
                        throw new RuntimeException(
                                "Failed to enforce policies for device state: "
                                        + stateToEnforce);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Intent> getLockScreenActivityIntent() {
        synchronized (this) {
            return Futures.transformAsync(mCurrentEnforcedDeviceStateFuture,
                    state -> {
                        if (state != LOCKED) return Futures.immediateFuture(null);
                        return Futures.transform(
                                SetupParametersClient.getInstance().getKioskPackage(),
                                kioskPackage -> {
                                    if (kioskPackage == null) {
                                        LogUtil.e(TAG, "Missing kiosk package parameter");
                                        return null;
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
                                    Intent launchIntent = pm.getLaunchIntentForPackage(
                                            kioskPackage);
                                    if (launchIntent == null) {
                                        LogUtil.e(TAG,
                                                "Failed to get launch intent for " + kioskPackage);
                                        return null;
                                    }
                                    return launchIntent;
                                }, mBgExecutor);
                    }, mBgExecutor);
        }
    }

    private ListenableFuture<Intent> getLandingActivityIntent() {
        SetupParametersClient client = SetupParametersClient.getInstance();
        ListenableFuture<@ProvisioningType Integer> provisioningType =
                client.getProvisioningType();
        ListenableFuture<Boolean> isMandatory = client.isProvisionMandatory();
        return Futures.whenAllSucceed(provisioningType, isMandatory).call(
                () -> {
                    Intent resultIntent = new Intent(mContext, LandingActivity.class);
                    switch (Futures.getDone(provisioningType)) {
                        case ProvisioningType.TYPE_FINANCED:
                            // TODO(b/288923554) this used to return an intent with action
                            // ACTION_START_DEVICE_FINANCING_SECONDARY_USER_PROVISIONING
                            // for secondary users. Rework once a decision has been made about
                            // what to show to users.
                            return resultIntent.setAction(
                                    Futures.getDone(isMandatory)
                                            ? ACTION_START_DEVICE_FINANCING_PROVISIONING
                                            : ACTION_START_DEVICE_FINANCING_DEFERRED_PROVISIONING);
                        case ProvisioningType.TYPE_SUBSIDY:
                            return resultIntent.setAction(
                                    Futures.getDone(isMandatory)
                                            ? ACTION_START_DEVICE_SUBSIDY_PROVISIONING
                                            : ACTION_START_DEVICE_SUBSIDY_DEFERRED_PROVISIONING);
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
                        LogUtil.e(TAG, "Kiosk package name is null");
                        return null;
                    }
                    final Intent kioskSetupIntent = new Intent(ACTION_DEVICE_LOCK_KIOSK_SETUP);
                    kioskSetupIntent.setPackage(kioskPackageName);
                    final ResolveInfo resolveInfo = mContext.getPackageManager()
                            .resolveActivity(kioskSetupIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolveInfo == null || resolveInfo.activityInfo == null) {
                        LogUtil.e(TAG, "Cannot find kiosk setup activity");
                        return null;
                    }
                    return kioskSetupIntent.setComponent(new ComponentName(kioskPackageName,
                            resolveInfo.activityInfo.name));
                }, mBgExecutor);
    }

    @Override
    public ListenableFuture<Intent> getLaunchIntentForCurrentState() {
        synchronized (this) {
            return Futures.transformAsync(mCurrentEnforcedProvisionStateFuture,
                    currentState -> {
                        switch (currentState) {
                            case PROVISION_IN_PROGRESS:
                                return getLandingActivityIntent();
                            case KIOSK_PROVISIONED:
                                return getKioskSetupActivityIntent();
                            case PROVISION_SUCCEEDED:
                                return getLockScreenActivityIntent();
                            case PROVISION_FAILED:
                            case PROVISION_PAUSED:
                            case UNPROVISIONED:
                                return Futures.immediateFuture(null);
                            default:
                                throw new IllegalStateException(
                                        "Invalid state: " + currentState);
                        }
                    }, mBgExecutor);
        }
    }
}
