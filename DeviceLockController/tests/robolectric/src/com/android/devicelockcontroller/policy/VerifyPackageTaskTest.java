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

import static androidx.work.WorkInfo.State.FAILED;
import static androidx.work.WorkInfo.State.SUCCEEDED;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_SIGNATURE_CHECKSUM;
import static com.android.devicelockcontroller.common.DeviceLockConstants.KEY_KIOSK_APP_INSTALLED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_PACKAGE_INFO;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_VALID_DOWNLOADED_FILE;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_VALID_SIGNING_INFO;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_PACKAGE_NAME_MISMATCH;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_ERROR_CODE_KEY;
import static com.android.devicelockcontroller.policy.VerifyPackageTask.computeHashValue;
import static com.android.devicelockcontroller.setup.UserPreferences.getKioskSignature;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Bundle;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.setup.SetupParameters;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.concurrent.ExecutionException;

/** Unit tests for {@link VerifyPackageTask}. */
@RunWith(RobolectricTestRunner.class)
public final class VerifyPackageTaskTest {
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String TEST_DIFFERENT_PACKAGE_NAME = "test.different.package.name";
    private static final String TEST_FILE_LOCATION = "test/file/location";
    private static final byte[] TEST_SIGNATURE = new byte[]{1, 2, 3, 4};
    private static final String TEST_SIGNATURE_CHECKSUM =
            "n2SnR-G5fxMfq7a0Rylsm28CAeefs8U1bmx36JtqgGo=";
    private static final byte[] TEST_ANOTHER_SIGNATURE = new byte[]{5, 6, 7, 8};
    private static final String TEST_ANOTHER_SIGNATURE_CHECKSUM =
            "VeVQn4BSmYKUJm7ltQy1kpOBkftdZ_c8rC5gsCdrG90=";

    private Context mContext;
    private PackageInfo mPackageInfo;
    private WorkManager mWorkManager;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mPackageInfo = new PackageInfo();
        mPackageInfo.packageName = TEST_PACKAGE_NAME;
        final Configuration config =
                new Configuration.Builder().setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(Context context,
                                    String workerClassName, WorkerParameters workerParameters) {
                                return workerClassName.equals(VerifyPackageTask.class.getName())
                                        ? new VerifyPackageTask(context, workerParameters,
                                        MoreExecutors.newDirectExecutorService())
                                        : null;
                            }
                        }).build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext, config);
        mWorkManager = WorkManager.getInstance(mContext);
    }

    @Test
    public void testComputeHashValue() {
        final byte[] actualHashValue = computeHashValue(TEST_SIGNATURE);
        final byte[] expectedHashValue = Base64.decode(TEST_SIGNATURE_CHECKSUM, Base64.URL_SAFE);
        assertThat(actualHashValue).isEqualTo(expectedHashValue);
    }

    @Test
    public void testVerify_DownloadedFilePathIsNull() {
        // GIVEN
        createParameters(TEST_PACKAGE_NAME, TEST_SIGNATURE_CHECKSUM);
        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, /* fileLocation */ null);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
    }

    @Test
    public void testVerify_DownloadedFilePathIsEmpty() {
        // GIVEN
        createParameters(TEST_PACKAGE_NAME, TEST_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, /* fileLocation */ "");

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_NO_VALID_DOWNLOADED_FILE);
    }

    @Test
    public void testVerify_ExpectedPackageNameIsNull() {
        // GIVEN
        createParameters(null, TEST_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_PACKAGE_NAME_MISMATCH);
    }

    @Test
    public void testVerify_ExpectedPackageNameIsEmpty() {
        // GIVEN
        createParameters("", TEST_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_PACKAGE_NAME_MISMATCH);
    }

    @Test
    public void testVerify_ExpectedSignatureChecksumIsNull() {
        // GIVEN
        createParameters(TEST_PACKAGE_NAME, null);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH);
    }

    @Test
    public void testVerify_ExpectedSignatureChecksumIsEmpty() {
        // GIVEN
        createParameters(TEST_PACKAGE_NAME, "");

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH);
    }

    @Test
    public void testVerify_NoPackageInfo() {
        // GIVEN cannot find package in the file location
        createPackageInfo(/* packageInfo */ null);
        createParameters(TEST_PACKAGE_NAME, TEST_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_NO_PACKAGE_INFO);
    }

    @Test
    public void testVerify_PackageNameMismatch() {
        // GIVEN package returns a different name
        mPackageInfo.packageName = TEST_DIFFERENT_PACKAGE_NAME;
        createPackageInfo(mPackageInfo);
        createParameters(TEST_PACKAGE_NAME, TEST_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_PACKAGE_NAME_MISMATCH);
    }

    @Test
    public void testVerify_SignatureChecksumMismatch() {
        // GIVEN a different signature
        final SigningInfo signingInfo = new SigningInfo();
        mPackageInfo.signingInfo = signingInfo;
        final Signature signature = new Signature(TEST_ANOTHER_SIGNATURE);
        Shadows.shadowOf(signingInfo).setSignatures(new Signature[]{signature});
        createPackageInfo(mPackageInfo);
        createParameters(TEST_PACKAGE_NAME, TEST_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH);
    }

    @Test
    public void testVerify_SigningInfoNull_NoValidSigningInfoError() {
        // GIVEN the signing info is null
        mPackageInfo.signingInfo = null;
        createPackageInfo(mPackageInfo);
        createParameters(TEST_PACKAGE_NAME, TEST_ANOTHER_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_NO_VALID_SIGNING_INFO);
    }

    @Test
    public void testVerify_SigningCertificateHistoryNull_ChecksumMismatchError() {
        // GIVEN a signing info without signing certificates
        mPackageInfo.signingInfo = new SigningInfo();
        createPackageInfo(mPackageInfo);
        createParameters(TEST_PACKAGE_NAME, TEST_ANOTHER_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH);
        assertThat(mPackageInfo.signingInfo).isNotNull();
        assertThat(mPackageInfo.signingInfo.getSigningCertificateHistory()).isNull();
    }

    @Test
    public void testVerify_SigningCertificateHistoryEmpty_ChecksumMismatchError() {
        // GIVEN a signing info with empty signing certificate
        final SigningInfo signingInfo = new SigningInfo();
        mPackageInfo.signingInfo = signingInfo;
        Shadows.shadowOf(signingInfo).setSignatures(new Signature[]{});
        createPackageInfo(mPackageInfo);
        createParameters(TEST_PACKAGE_NAME, TEST_ANOTHER_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_SIGNATURE_CHECKSUM_MISMATCH);
        assertThat(mPackageInfo.signingInfo).isNotNull();
        assertThat(mPackageInfo.signingInfo.getSigningCertificateHistory()).isNotNull();
        assertThat(mPackageInfo.signingInfo.getSigningCertificateHistory()).hasLength(0);
    }

    @Test
    public void testVerify_notInstalled_success() {
        // GIVEN a signing info with a valid signing certificate
        final SigningInfo signingInfo = new SigningInfo();
        mPackageInfo.signingInfo = signingInfo;
        final Signature signature = new Signature(TEST_SIGNATURE);
        Shadows.shadowOf(signingInfo).setSignatures(new Signature[]{signature});
        createPackageInfo(mPackageInfo);
        createParameters(TEST_PACKAGE_NAME, TEST_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is succeed
        assertThat(workInfo.getState()).isEqualTo(SUCCEEDED);

        // THEN the signingInfo is written into SharedPreferences
        assertThat(getKioskSignature(mContext)).isEqualTo(signature.toCharsString());
    }

    @Test
    public void testVerify_isInstalled_success() {
        // GIVEN a signing info with a valid signing certificate
        mPackageInfo.signingInfo = new SigningInfo();
        final Signature signature = new Signature(TEST_SIGNATURE);
        Shadows.shadowOf(mPackageInfo.signingInfo).setSignatures(new Signature[]{signature});
        createPackageInfo(mPackageInfo, true);
        createParameters(TEST_PACKAGE_NAME, TEST_SIGNATURE_CHECKSUM);

        // WHEN
        final WorkInfo workInfo = buildTaskAndRun(mWorkManager);

        // THEN task is succeed
        assertThat(workInfo.getState()).isEqualTo(SUCCEEDED);

        // THEN the signingInfo is written into SharedPreferences
        assertThat(getKioskSignature(mContext)).isEqualTo(signature.toCharsString());
    }

    @Test
    public void testVerify_MultipleSigners_failure() {
        // GIVEN a signing info with multiple signing certificates
        final SigningInfo signingInfo = new SigningInfo();
        final Signature signature = new Signature(TEST_SIGNATURE);
        final Signature anotherSignature = new Signature(TEST_ANOTHER_SIGNATURE);
        Shadows.shadowOf(signingInfo).setSignatures(new Signature[]{signature, anotherSignature});
        mPackageInfo.signingInfo = signingInfo;
        assertThat(mPackageInfo.signingInfo.hasMultipleSigners()).isTrue();
        createPackageInfo(mPackageInfo);
        createParameters(TEST_PACKAGE_NAME, TEST_ANOTHER_SIGNATURE_CHECKSUM);

        // WHEN
        WorkInfo workInfo = buildTaskAndRun(mWorkManager, TEST_FILE_LOCATION);

        // THEN task is failed
        assertThat(workInfo.getState()).isEqualTo(FAILED);
        assertThat(workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY,
                /* defaultValue */ -1)).isEqualTo(ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS);

        // THEN the signingInfo is not written into SharedPreferences
        assertThat(getKioskSignature(mContext)).isNull();
    }

    private void createPackageInfo(PackageInfo packageInfo) {
        createPackageInfo(packageInfo, false);
    }

    private void createPackageInfo(PackageInfo packageInfo, boolean isInstalled) {
        final ShadowPackageManager shadowPackageManager =
                Shadows.shadowOf(mContext.getPackageManager());
        if (isInstalled) {
            shadowPackageManager.installPackage(packageInfo);
        } else {
            shadowPackageManager.setPackageArchiveInfo(TEST_FILE_LOCATION, packageInfo);
        }
    }

    private void createParameters(String packageName, String signatureChecksum) {
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, packageName);
        bundle.putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, signatureChecksum);
        SetupParameters.createPrefs(mContext, bundle);
    }

    private static WorkInfo buildTaskAndRun(WorkManager workManager,
            @Nullable String fileLocation) {
        // GIVEN
        final Data inputData =
                new Data.Builder()
                        .putString(TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY, fileLocation)
                        .build();

        return getWorkInfo(inputData, workManager);
    }

    private static WorkInfo buildTaskAndRun(WorkManager workManager) {
        // GIVEN
        final Data inputData =
                new Data.Builder()
                        .putBoolean(KEY_KIOSK_APP_INSTALLED, true)
                        .build();

        // WHEN
        return getWorkInfo(inputData, workManager);
    }

    private static WorkInfo getWorkInfo(Data inputData, WorkManager workManager) {
        // WHEN
        final OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(VerifyPackageTask.class)
                        .setInputData(inputData)
                        .build();
        workManager.enqueue(request);

        try {
            return workManager.getWorkInfoById(request.getId()).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new AssertionError("Exception", e);
        }
    }
}
