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

import static com.android.devicelockcontroller.policy.DevicePolicyControllerImpl.START_LOCK_TASK_MODE_WORK_NAME;

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

import androidx.annotation.VisibleForTesting;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.setup.SetupParametersClient;
import com.android.devicelockcontroller.setup.UserPreferences;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Handles lock task mode features. */
final class LockTaskModePolicyHandler implements PolicyHandler {
    @VisibleForTesting
    static final int DEFAULT_LOCK_TASK_FEATURES =
            (DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                    | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                    | DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                    | DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS);
    private static final String TAG = "LockTaskModePolicyHandler";
    private final Context mContext;
    private final DevicePolicyManager mDpm;

    LockTaskModePolicyHandler(Context context, DevicePolicyManager dpm) {
        mContext = context;
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
    public int setPolicyForState(@DeviceState int state) {
        if (state == DeviceState.PSEUDO_UNLOCKED || state == DeviceState.PSEUDO_LOCKED) {
            return SUCCESS;
        }

        if (state == DeviceState.SETUP_SUCCEEDED) {
            composeAllowlist();
        }

        if (state == DeviceState.UNLOCKED || state == DeviceState.CLEARED) {
            disableLockTaskMode();
        } else {
            enableLockTaskMode();
        }
        return SUCCESS;
    }

    @Override
    public boolean isCompliant(@DeviceState int state) {
        // TODO (b/147291511): On boot, the policies need to be verified.
        return true;
    }

    /**
     * Sets the activity as the preferred activity for home intent. Activity is cleared when the
     * device leaves lock task mode.
     */
    public boolean setPreferredActivityForHome(ComponentName activity) {
        if (!mDpm.isLockTaskPermitted(activity.getPackageName())) {
            LogUtil.e(TAG, String.format(Locale.US, "%s is not permitted in lock task mode",
                    activity.getPackageName()));

            return false;
        }

        final String currentPackage = UserPreferences.getPackageOverridingHome(mContext);
        if (currentPackage != null) {
            mDpm.clearPackagePersistentPreferredActivities(null /* admin */, currentPackage);
        }
        mDpm.addPersistentPreferredActivity(null /* admin */, getHomeIntentFilter(), activity);
        UserPreferences.setPackageOverridingHome(mContext, activity.getPackageName());

        return true;
    }

    private void updateAllowlist() {
        ArrayList<String> allowlist = UserPreferences.getLockTaskAllowlist(mContext);
        if (allowlist.isEmpty()) {
            allowlist = new ArrayList<>(
                    Arrays.asList(
                            mContext.getResources().getStringArray(R.array.lock_task_allowlist)));
        }

        final TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
        final String defaultDialer = telecomManager.getDefaultDialerPackage();
        if (defaultDialer != null && !allowlist.contains(defaultDialer)) {
            LogUtil.i(TAG, String.format(Locale.US, "Adding default dialer %s to allowlist",
                    defaultDialer));
            allowlist.add(defaultDialer);
        }
        final String[] allowlistPackages = allowlist.toArray(new String[allowlist.size()]);
        mDpm.setLockTaskPackages(null /* admin */, allowlistPackages);
        LogUtil.i(TAG, String.format(Locale.US, "Update Lock task allowlist %s",
                Arrays.toString(allowlistPackages)));
    }

    private void enableLockTaskMode() {
        int lockTaskFeatures = DEFAULT_LOCK_TASK_FEATURES;
        if (Futures.getUnchecked(
                SetupParametersClient.getInstance().isNotificationsInLockTaskModeEnabled())) {
            lockTaskFeatures |= DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;
        }
        // updateAllowlist() calls setLockTaskPackages(), which must be called before
        // setLockTaskFeatures().
        updateAllowlist();
        mDpm.setLockTaskFeatures(null /* admin */, lockTaskFeatures);
    }

    private void disableLockTaskMode() {
        WorkManager.getInstance(mContext).cancelUniqueWork(START_LOCK_TASK_MODE_WORK_NAME);

        final String currentPackage = UserPreferences.getPackageOverridingHome(mContext);
        // This will stop the lock task mode
        mDpm.setLockTaskPackages(null /* admin */, new String[0]);
        LogUtil.i(TAG, "Clear Lock task allowlist");
        if (currentPackage != null) {
            mDpm.clearPackagePersistentPreferredActivities(null /* admin */, currentPackage);
            UserPreferences.setPackageOverridingHome(mContext, null /* packageName */);
        }
    }

    /*
     * The allowlist for lock task mode is composed by the following rules.
     *   1. Add the packages from lock_task_allowlist array from config.xml. These packages are
     *      required for essential services to work.
     *   2. Find the default app used as dialer (should be a System App).
     *   3. Find the default app used for Settings (should be a System App).
     *   4. Find the default InputMethod.
     *   4. Kiosk app
     *   5. Append the packages allowlisted through setup parameters.
     */
    private void composeAllowlist() {
        final String[] allowlistArray =
                mContext.getResources().getStringArray(R.array.lock_task_allowlist);
        final ArrayList<String> allowlistPackages = new ArrayList<>(Arrays.asList(allowlistArray));
        allowlistSystemAppForAction(Intent.ACTION_DIAL, allowlistPackages);
        allowlistSystemAppForAction(Settings.ACTION_SETTINGS, allowlistPackages);
        allowlistInputMethod(allowlistPackages);
        allowlistCellBroadcastReceiver(allowlistPackages);
        final String kioskPackage = Futures.getUnchecked(
                SetupParametersClient.getInstance().getKioskPackage());
        if (kioskPackage != null) allowlistPackages.add(kioskPackage);
        allowlistPackages.addAll(
                Futures.getUnchecked(SetupParametersClient.getInstance().getKioskAllowlist()));
        UserPreferences.setLockTaskAllowlist(mContext, allowlistPackages);
    }

    private void allowlistSystemAppForAction(String action, List<String> allowlistPackages) {
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

    private void allowlistInputMethod(List<String> allowlistPackages) {
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

    private void allowlistCellBroadcastReceiver(List<String> allowlistPackages) {
        final String packageName =
                CellBroadcastUtils.getDefaultCellBroadcastReceiverPackageName(mContext);
        if (packageName == null) {
            LogUtil.e(TAG, "Could not find the default cell broadcast receiver");

            return;
        }
        allowlistPackages.add(packageName);
    }
}
