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

package com.android.devicelockcontroller.activities;

import android.annotation.NonNull;
import android.app.Application;
import android.text.TextUtils;
import android.util.Pair;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;

/**
 * This class provides the resources and {@link ProvisionInfo} to render the
 * {@link ProvisionInfoFragment}.
 */
public abstract class ProvisionInfoViewModel extends AndroidViewModel {

    public static final String TAG = "ProvisionInfoViewModel";
    int mHeaderDrawableId;
    int mHeaderTextId;
    int mSubHeaderTextId;
    List<ProvisionInfo> mProvisionInfoList;
    final MutableLiveData<String> mProviderNameLiveData;
    final MutableLiveData<String> mTermsAndConditionsUrlLiveData;
    final MutableLiveData<Boolean> mIsProvisionForcedLiveData;
    final MediatorLiveData<Pair<Integer, String>> mHeaderTextLiveData;
    final MediatorLiveData<Pair<Integer, String>> mSubHeaderTextLiveData;
    final MediatorLiveData<List<ProvisionInfo>> mProvisionInfoListLiveData;

    public ProvisionInfoViewModel(@NonNull Application application) {
        super(application);
        mProviderNameLiveData = new MutableLiveData<>();
        mTermsAndConditionsUrlLiveData = new MutableLiveData<>();
        mIsProvisionForcedLiveData = new MutableLiveData<>();
        mHeaderTextLiveData = new MediatorLiveData<>();
        mHeaderTextLiveData.addSource(mProviderNameLiveData,
                providerName -> mHeaderTextLiveData.setValue(
                        new Pair<>(mHeaderTextId, providerName)));
        mSubHeaderTextLiveData = new MediatorLiveData<>();
        mSubHeaderTextLiveData.addSource(mProviderNameLiveData,
                providerName -> mSubHeaderTextLiveData.setValue(
                        new Pair<>(mSubHeaderTextId, providerName)));
        mProvisionInfoListLiveData = new MediatorLiveData<>();

        SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        ListenableFuture<String> getKioskAppProviderNameFuture =
                setupParametersClient.getKioskAppProviderName();
        ListenableFuture<String> getTermsAndConditionsUrlFuture =
                setupParametersClient.getTermsAndConditionsUrl();

        Futures.addCallback(
                getKioskAppProviderNameFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(String providerName) {
                        if (TextUtils.isEmpty(providerName)) {
                            LogUtil.e(TAG, "Device provider name is empty, should not reach here.");
                            return;
                        }
                        mProviderNameLiveData.postValue(providerName);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to get Kiosk app provider name", t);
                    }
                }, MoreExecutors.directExecutor());

        Futures.addCallback(
                getTermsAndConditionsUrlFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(String termsAndConditionsUrl) {
                        if (TextUtils.isEmpty(termsAndConditionsUrl)) {
                            LogUtil.e(TAG,
                                    "Terms and Conditions URL is empty, should not reach here.");
                            return;
                        }
                        mTermsAndConditionsUrlLiveData.postValue(termsAndConditionsUrl);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to get Terms and Conditions URL", t);
                    }
                }, MoreExecutors.directExecutor());

        Futures.whenAllSucceed(getKioskAppProviderNameFuture, getTermsAndConditionsUrlFuture)
                .run(() -> mProvisionInfoListLiveData.postValue(mProvisionInfoList),
                        MoreExecutors.directExecutor());
        Futures.addCallback(GlobalParametersClient.getInstance().isProvisionForced(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Boolean isProvisionForced) {
                        mIsProvisionForcedLiveData.postValue(isProvisionForced);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to get if provision should be forced", t);
                    }
                }, MoreExecutors.directExecutor());
    }
}
