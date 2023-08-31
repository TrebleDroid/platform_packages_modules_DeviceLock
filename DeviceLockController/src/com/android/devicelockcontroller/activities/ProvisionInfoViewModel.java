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

import android.text.TextUtils;
import android.util.Pair;

import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

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
public abstract class ProvisionInfoViewModel extends ViewModel {

    public static final String TAG = "ProvisionInfoViewModel";
    int mHeaderDrawableId;
    int mMandatoryHeaderTextId;
    int mHeaderTextId;
    int mMandatorySubHeaderTextId;
    int mSubHeaderTextId;
    List<ProvisionInfo> mProvisionInfoList;
    final MutableLiveData<Boolean> mIsMandatoryLiveData = new MutableLiveData<>();
    final MutableLiveData<String> mProviderNameLiveData = new MutableLiveData<>();
    final MutableLiveData<String> mTermsAndConditionsUrlLiveData = new MutableLiveData<>();
    final MutableLiveData<Boolean> mIsProvisionForcedLiveData = new MutableLiveData<>();
    final MutableLiveData<Pair<Integer, String>> mHeaderTextLiveData = new MutableLiveData<>();
    final MutableLiveData<Pair<Integer, String>> mSubHeaderTextLiveData = new MutableLiveData<>();
    final MediatorLiveData<List<ProvisionInfo>> mProvisionInfoListLiveData =
            new MediatorLiveData<>();

    void retrieveData() {
        SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        ListenableFuture<String> kioskAppProviderNameFuture =
                setupParametersClient.getKioskAppProviderName();
        ListenableFuture<Boolean> isMandatoryFuture = setupParametersClient.isProvisionMandatory();
        ListenableFuture<String> termsAndConditionsUrlFuture =
                setupParametersClient.getTermsAndConditionsUrl();
        ListenableFuture<Boolean> isProvisionForcedFuture =
                GlobalParametersClient.getInstance().isProvisionForced();

        mProvisionInfoListLiveData.addSource(mProviderNameLiveData,
                unused -> mProvisionInfoListLiveData.setValue(mProvisionInfoList));
        mProvisionInfoListLiveData.addSource(mTermsAndConditionsUrlLiveData,
                unused -> mProvisionInfoListLiveData.setValue(mProvisionInfoList));
        ListenableFuture<Void> result = Futures.whenAllSucceed(
                        kioskAppProviderNameFuture,
                        isMandatoryFuture,
                        termsAndConditionsUrlFuture,
                        isProvisionForcedFuture)
                .call(() -> {
                    String providerName = Futures.getDone(kioskAppProviderNameFuture);
                    boolean isMandatory = Futures.getDone(isMandatoryFuture);
                    String termsAndConditionUrl = Futures.getDone(termsAndConditionsUrlFuture);
                    if (TextUtils.isEmpty(providerName)) {
                        LogUtil.w(TAG, "Device provider name is empty!");
                    }
                    if (TextUtils.isEmpty(termsAndConditionUrl)) {
                        LogUtil.w(TAG, "Terms and Conditions URL is empty!");
                    }
                    mProviderNameLiveData.postValue(providerName);
                    mIsMandatoryLiveData.postValue(isMandatory);
                    mHeaderTextLiveData.postValue(
                            new Pair<>(isMandatory ? mMandatoryHeaderTextId : mHeaderTextId,
                                    providerName));
                    mSubHeaderTextLiveData.postValue(
                            new Pair<>(isMandatory ? mMandatorySubHeaderTextId : mSubHeaderTextId,
                                    providerName));
                    mTermsAndConditionsUrlLiveData.postValue(termsAndConditionUrl);
                    mIsProvisionForcedLiveData.postValue(Futures.getDone(isProvisionForcedFuture));
                    return null;
                }, MoreExecutors.directExecutor());
        Futures.addCallback(result, new FutureCallback<>() {
            @Override
            public void onSuccess(Void result) {
                LogUtil.i(TAG, "Successfully updated live data");
            }

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);
            }
        }, MoreExecutors.directExecutor());
    }
}
