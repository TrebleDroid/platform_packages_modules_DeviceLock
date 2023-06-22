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

import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.CLEARED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.KIOSK_SETUP;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PSEUDO_LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PSEUDO_UNLOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.SETUP_FAILED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.SETUP_IN_PROGRESS;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.SETUP_PAUSED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.SETUP_SUCCEEDED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNLOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNPROVISIONED;
import static com.android.devicelockcontroller.policy.StartLockTaskModeWorker.START_LOCK_TASK_MODE_WORK_NAME;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.telecom.TelecomManager;
import android.util.ArraySet;

import androidx.annotation.VisibleForTesting;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/** Handles lock task mode features. */
final class LockTaskModePolicyHandler implements PolicyHandler {
    @VisibleForTesting
    static final int DEFAULT_LOCK_TASK_FEATURES =
            (DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                    | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                    | DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                    | DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                    | DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK);
    private static final String TAG = "LockTaskModePolicyHandler";
    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final DevicePolicyController mPolicyController;

    LockTaskModePolicyHandler(Context context, DevicePolicyManager dpm,
            DevicePolicyController policyController) {
        mContext = context;
        mPolicyController = policyController;
        mDpm = dpm;
    }

    private static IntentFilter getHomeIntentFilter() {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        return filter;
    }

    @Override
    @ResultType
    public ListenableFuture<@ResultType Integer> setPolicyForState(@DeviceState int state) {
        switch (state) {
            case PSEUDO_UNLOCKED:
            case PSEUDO_LOCKED:
            case UNPROVISIONED:
                return Futures.immediateFuture(SUCCESS);
            case SETUP_FAILED:
            case SETUP_PAUSED:
            case UNLOCKED:
            case CLEARED:
                return disableLockTaskMode();
            case SETUP_IN_PROGRESS:
            case SETUP_SUCCEEDED:
                return enableLockTaskMode(/* includeController= */ true);
            case KIOSK_SETUP:
            case LOCKED:
                return enableLockTaskMode(/* includeController= */ false);
            default:
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }
    }

    /**
     * Sets the activity as the preferred activity for home intent. Activity is cleared when the
     * device leaves lock task mode.
     */
    private boolean setPreferredActivityForHome(ComponentName activity) {
        if (!mDpm.isLockTaskPermitted(activity.getPackageName())) {
            LogUtil.e(TAG, String.format(Locale.US, "%s is not permitted in lock task mode",
                    activity.getPackageName()));

            return false;
        }

        final String currentPackage = UserParameters.getPackageOverridingHome(mContext);
        if (currentPackage != null) {
            mDpm.clearPackagePersistentPreferredActivities(null /* admin */, currentPackage);
        }
        mDpm.addPersistentPreferredActivity(null /* admin */, getHomeIntentFilter(), activity);
        UserParameters.setPackageOverridingHome(mContext, activity.getPackageName());

        return true;
    }

    private ListenableFuture<Void> updateAllowlist(boolean includeController) {
        return Futures.transform(composeAllowlist(includeController),
                allowlist -> {
                    TelecomManager telecomManager = mContext.getSystemService(
                            TelecomManager.class);
                    String defaultDialer = telecomManager.getDefaultDialerPackage();
                    if (defaultDialer != null && !allowlist.contains(defaultDialer)) {
                        LogUtil.i(TAG,
                                String.format(Locale.US, "Adding default dialer %s to allowlist",
                                        defaultDialer));
                        allowlist.add(defaultDialer);
                    }
                    String[] allowlistPackages = allowlist.toArray(new String[0]);
                    mDpm.setLockTaskPackages(null /* admin */, allowlistPackages);
                    LogUtil.i(TAG, String.format(Locale.US, "Update Lock task allowlist %s",
                            Arrays.toString(allowlistPackages)));
                    return null;
                }, Executors.newSingleThreadExecutor());
    }

    private @ResultType ListenableFuture<@ResultType Integer> enableLockTaskMode(
            boolean includeController) {
        ListenableFuture<Boolean> notificationsInLockTaskModeEnabled =
                SetupParametersClient.getInstance().isNotificationsInLockTaskModeEnabled();
        ListenableFuture<Intent> launchIntent =
                mPolicyController.getLaunchIntentForCurrentLockedActivity();
        return Futures.whenAllSucceed(
                        launchIntent,
                        notificationsInLockTaskModeEnabled,
                        updateAllowlist(includeController))
                .call(
                        () -> {
                            int flags = DEFAULT_LOCK_TASK_FEATURES;
                            if (Futures.getDone(notificationsInLockTaskModeEnabled)) {
                                flags |= LOCK_TASK_FEATURE_NOTIFICATIONS;
                            }
                            mDpm.setLockTaskFeatures(null, flags);
                            setPreferredActivityForHome(
                                    Futures.getDone(launchIntent).getComponent());
                            return SUCCESS;
                        }, mContext.getMainExecutor());
    }

    private @ResultType ListenableFuture<@ResultType Integer> disableLockTaskMode() {
        WorkManager.getInstance(mContext).cancelUniqueWork(START_LOCK_TASK_MODE_WORK_NAME);

        final String currentPackage = UserParameters.getPackageOverridingHome(mContext);
        // Device Policy Engine treats lock task features and packages as one policy and
        // therefore we need to set both lock task features (to LOCK_TASK_FEATURE_NONE) and
        // lock task packages (to an empty string array).
        mDpm.setLockTaskFeatures(null /* admin */, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
        // This will stop the lock task mode
        mDpm.setLockTaskPackages(null /* admin */, new String[0]);
        LogUtil.i(TAG, "Clear Lock task allowlist");
        if (currentPackage != null) {
            mDpm.clearPackagePersistentPreferredActivities(null /* admin */, currentPackage);
            UserParameters.setPackageOverridingHome(mContext, null /* packageName */);
        }
        return Futures.immediateFuture(SUCCESS);
    }

    /*
     * The allowlist for lock task mode is composed by the following rules.
     *   1. Add the packages from lock_task_allowlist array from config.xml. These packages are
     *      required for essential services to work.
     *   2. Find the default app used as dialer (should be a System App).
     *   3. Find the default app used for Settings (should be a System App).
     *   4. Find the default InputMethod.
     *   5. DLC or Kiosk app depending on the input.
     *   6. Append the packages allow-listed through setup parameters if applicable.
     */
    private ListenableFuture<ArraySet<String>> composeAllowlist(boolean includeController) {
        String[] allowlistArray =
                mContext.getResources().getStringArray(R.array.lock_task_allowlist);
        ArraySet<String> allowlistPackages = new ArraySet<>(allowlistArray);
        allowlistSystemAppForAction(Intent.ACTION_DIAL, allowlistPackages);
        allowlistSystemAppForAction(Settings.ACTION_SETTINGS, allowlistPackages);
        allowlistInputMethod(allowlistPackages);
        allowlistCellBroadcastReceiver(allowlistPackages);
        if (includeController) {
            allowlistPackages.add(mContext.getPackageName());
            return Futures.immediateFuture(allowlistPackages);

        } else {
            // Explicitly remove DLC package since it can be added from the config resource.
            // If DLC is included incorrectly in the allowlist, kiosk app setup activity may not
            // successfully be launched.
            allowlistPackages.remove(mContext.getPackageName());
            ListenableFuture<String> kioskPackageTask =
                    SetupParametersClient.getInstance().getKioskPackage();
            ListenableFuture<List<String>> kioskAllowlistTask =
                    SetupParametersClient.getInstance().getKioskAllowlist();
            return Futures.whenAllSucceed(kioskPackageTask, kioskAllowlistTask).call(
                    () -> {
                        allowlistPackages.add(Futures.getDone(kioskPackageTask));
                        allowlistPackages.addAll(Futures.getDone(kioskAllowlistTask));
                        return allowlistPackages;
                    }, MoreExecutors.directExecutor());
        }
    }

    private void allowlistSystemAppForAction(String action, ArraySet<String> allowlistPackages) {
        final PackageManager pm = mContext.getPackageManager();
        final Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        final List<ResolveInfo> resolveInfoList =
                pm.queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY);
        if (resolveInfoList.isEmpty()) {
            LogUtil.e(TAG,
                    String.format(Locale.US, "Could not find the system app for %s", action));

            return;
        }
        final String packageName = resolveInfoList.get(0).activityInfo.packageName;
        LogUtil.i(TAG, String.format(Locale.US, "Using %s for %s", packageName, action));
        allowlistPackages.add(packageName);
    }

    private void allowlistInputMethod(ArraySet<String> allowlistPackages) {
        final String defaultIme = Secure.getString(mContext.getContentResolver(),
                Secure.DEFAULT_INPUT_METHOD);
        if (defaultIme == null) {
            LogUtil.e(TAG, "Could not find the default IME");

            return;
        }

        final ComponentName imeComponent = ComponentName.unflattenFromString(defaultIme);
        if (imeComponent == null) {
            LogUtil.e(TAG, String.format(Locale.US, "Invalid input method: %s", defaultIme));

            return;
        }
        allowlistPackages.add(imeComponent.getPackageName());
    }

    private void allowlistCellBroadcastReceiver(ArraySet<String> allowlistPackages) {
        final String packageName =
                CellBroadcastUtils.getDefaultCellBroadcastReceiverPackageName(mContext);
        if (packageName == null) {
            LogUtil.e(TAG, "Could not find the default cell broadcast receiver");

            return;
        }
        allowlistPackages.add(packageName);
    }
}
