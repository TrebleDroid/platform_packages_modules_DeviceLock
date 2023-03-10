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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.android.devicelockcontroller.R;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the {@link ProvisionInfo} to the
 * {@link com.android.internal.widget.RecyclerView.Recycler} via the
 * {@link ProvisionInfoListAdapter}.
 */
public final class ProvisionInfoViewModel extends ViewModel {

    private static final Integer[] DRAWABLE_IDS = new Integer[]{
            R.drawable.ic_file_download_24px, R.drawable.ic_lock_outline_24px,
    };

    private static final Integer[] TEXT_IDS = new Integer[]{
            R.string.download_kiosk_app, R.string.restrict_device_if_missing_payment,
    };

    private final MutableLiveData<List<ProvisionInfo>> mProvisionInfoListLiveData;

    public ProvisionInfoViewModel() {
        mProvisionInfoListLiveData = new MutableLiveData<>();

        List<ProvisionInfo> provisionInfoList = new ArrayList<>();
        for (int i = 0, size = DRAWABLE_IDS.length; i < size; ++i) {
            provisionInfoList.add(new ProvisionInfo(DRAWABLE_IDS[i], TEXT_IDS[i]));
        }

        mProvisionInfoListLiveData.setValue(provisionInfoList);
    }

    public LiveData<List<ProvisionInfo>> getProvisionInfoListLiveData() {
        return mProvisionInfoListLiveData;
    }
}
