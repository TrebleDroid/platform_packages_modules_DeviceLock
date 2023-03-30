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

package com.android.devicelockcontroller.policy;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.setup.SetupParameters;
import com.android.devicelockcontroller.setup.UserPreferences;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;

/**
 * Verify the package name specified by {@link SetupParameters#getKioskPackage(Context)}. Verify
 * if the package exists and save the signature for authenticating messages on DeviceLockService.
 */
public final class VerifyInstalledPackageTask extends AbstractTask {

    public static final String TAG = "verifyInstalledPackageTask";
    private final Context mContext;
    private final ListeningExecutorService mExecutorService;
    private final PackageManager mPackageManager;

    public VerifyInstalledPackageTask(
            Context context,
            WorkerParameters workerParameters,
            ListeningExecutorService executorService) {
        super(context, workerParameters);
        this.mContext = context;
        this.mExecutorService = executorService;
        mPackageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return mExecutorService.submit(
                () -> {
                    LogUtil.i(TAG, "Starts to run");
                    String packageName = SetupParameters.getKioskPackage(mContext);
                    if (TextUtils.isEmpty(packageName)) {
                        LogUtil.e(TAG,
                                "The expected package name of the creditor app does not exist!");
                        return failure(ERROR_CODE_PACKAGE_NAME_MISMATCH);
                    }

                    PackageInfo packageInfo;
                    try {
                        packageInfo =
                                mPackageManager.getPackageInfo(
                                        packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                    } catch (PackageManager.NameNotFoundException e) {
                        LogUtil.e(TAG,
                                String.format("Package %s does not exist! ", packageName), e);
                        return failure(ERROR_CODE_NO_PACKAGE_INFO);
                    }

                    if (packageInfo.signingInfo == null) {
                        LogUtil.e(TAG, "Signing certificate not found");
                        return failure(ERROR_CODE_NO_VALID_SIGNING_INFO);
                    }

                    SigningInfo signingInfo = packageInfo.signingInfo;
                    Signature[] signatures;
                    if (signingInfo.hasMultipleSigners()) {
                        LogUtil.e(TAG, "Package with multiple signers is not supported");
                        return failure(ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS);
                    }

                    signatures = signingInfo.getSigningCertificateHistory();

                    // Only one of signatures from the apk will be saved into the local storage
                    // before the apk
                    // is installed, if we need to verify the package later, we need to retrieve all
                    // signatures and compare each of them with the one we saved here.
                    UserPreferences.setKioskSignature(mContext,
                            Objects.requireNonNull(signatures)[0].toCharsString());

                    return Result.success();
                });
    }
}
