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
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.setup.SetupParameters;
import com.android.devicelockcontroller.setup.UserPreferences;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Verify the apk specified at {@code
 * getInputData().getString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY)}. It extracts package info
 * from the apk using {@link PackageManager#getPackageArchiveInfo} and compares the package name and
 * signature checksum against the provided
 * {@link SetupParameters#getKioskSignatureChecksum(Context)}.
 */
public final class VerifyDownloadedPackageTask extends AbstractTask {
    private static final String TAG = "VerifyPackageTask";

    private final Context mContext;
    private final ListeningExecutorService mExecutorService;
    private final PackageManager mPackageManager;

    public VerifyDownloadedPackageTask(
            Context context,
            WorkerParameters workerParameters,
            ListeningExecutorService executorService) {
        super(context, workerParameters);
        mContext = context;
        mExecutorService = executorService;
        mPackageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return mExecutorService.submit(
                () -> {
                    LogUtil.i(TAG, "Starts to run");
                    final String fileLocation =
                            getInputData().getString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY);
                    if (TextUtils.isEmpty(fileLocation)) {
                        LogUtil.e(TAG, String.format(Locale.US,
                                "The downloaded file location is %s", fileLocation));
                        return failure(ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
                    }

                    final String packageName = SetupParameters.getKioskPackage(mContext);
                    if (TextUtils.isEmpty(packageName)) {
                        LogUtil.e(TAG, String.format(Locale.US,
                                "The expected package name of the kiosk app is %s", packageName));
                        return failure(ERROR_CODE_PACKAGE_NAME_MISMATCH);
                    }

                    final String signatureChecksum =
                            SetupParameters.getKioskSignatureChecksum(mContext);
                    if (TextUtils.isEmpty(signatureChecksum)) {
                        LogUtil.e(TAG, String.format(Locale.US,
                                "The expected signature checksum of the kiosk app is %s",
                                signatureChecksum));
                        return failure(ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH);
                    }

                    final PackageInfo packageInfo =
                            mPackageManager.getPackageArchiveInfo(fileLocation,
                                    PackageManager.GET_SIGNATURES
                                            | PackageManager.GET_SIGNING_CERTIFICATES);

                    if (packageInfo == null) {
                        LogUtil.e(TAG, "Cannot find package info");
                        return failure(ERROR_CODE_NO_PACKAGE_INFO);
                    }

                    if (!TextUtils.equals(packageName, packageInfo.packageName)) {
                        LogUtil.e(TAG, "Package name mismatch");
                        return failure(ERROR_CODE_PACKAGE_NAME_MISMATCH);
                    }

                    if (packageInfo.signingInfo == null) {
                        LogUtil.e(TAG, "Signing certificate not found");
                        return failure(ERROR_CODE_NO_VALID_SIGNING_INFO);
                    }

                    final SigningInfo signingInfo = packageInfo.signingInfo;
                    Signature[] signatures;
                    if (signingInfo.hasMultipleSigners()) {
                        LogUtil.e(TAG, "Packages with multiple signers are not supported");
                        return failure(ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS);
                    }

                    signatures = signingInfo.getSigningCertificateHistory();
                    if (!compareChecksum(signatureChecksum, signatures)) {
                        LogUtil.e(TAG, "Signature checksum mismatch");
                        return failure(ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH);
                    }

                    // Only one of signatures from the apk will be saved into the local storage
                    // before the apk is installed, if we need to verify the package later, we need
                    // to retrieve all signatures and compare each of them with the one we saved
                    // here.
                    UserPreferences.setKioskSignature(mContext, signatures[0].toCharsString());

                    // need to pass the file location to next task
                    final Data data = new Data.Builder()
                            .putString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY, fileLocation)
                            .build();
                    return Result.success(data);
                });
    }

    private static boolean compareChecksum(String signatureChecksum, Signature[] signatures) {
        if (signatures == null) {
            return false;
        }
        final byte[] decodedSignatureChecksum = Base64.decode(signatureChecksum, Base64.URL_SAFE);
        for (Signature signature : signatures) {
            if (Arrays.equals(decodedSignatureChecksum,
                    computeHashValue(signature.toByteArray()))) {
                return true;
            }
        }
        return false;
    }

    /** Computes SHA-256 hash of a supplied byte array. */
    @Nullable
    @VisibleForTesting
    static byte[] computeHashValue(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(TAG, "Hash algorithm SHA-256 is not supported", e);
            return null;
        }
    }
}
