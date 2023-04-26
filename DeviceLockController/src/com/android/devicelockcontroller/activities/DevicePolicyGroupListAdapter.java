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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.setup.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An Adapter which provides the binding between {@link DevicePolicyGroup} and the
 * {@link androidx.recyclerview.widget.RecyclerView}.
 */
final class DevicePolicyGroupListAdapter extends
        ListAdapter<DevicePolicyGroup, DevicePolicyGroupViewHolder> {

    private static final String TAG = "DevicePoliciesFragment";

    /**
     * The maximum number of {@link DevicePolicy} in the {@link DevicePolicyGroup}, since the number
     * of {@link DevicePolicy} in each {@link DevicePolicyGroup} may variate.
     */
    private int mMaxDevicePolicy;
    private String mProviderName;

    DevicePolicyGroupListAdapter() {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(DevicePolicyGroup oldItem,
                    DevicePolicyGroup newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(DevicePolicyGroup oldItem,
                    DevicePolicyGroup newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    @Override
    public DevicePolicyGroupViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View devicePolicyGroupView = inflater.inflate(R.layout.item_device_policy_group, parent,
                false);
        ViewGroup devicePolicyItems = devicePolicyGroupView.findViewById(R.id.device_policy_items);
        // each ViewHolder will have the same number of item views, so that they can be reused
        for (int i = 0; i < mMaxDevicePolicy; ++i) {
            View devicePolicyItemView = inflater.inflate(R.layout.item_device_policy,
                    devicePolicyItems, false);
            devicePolicyItems.addView(devicePolicyItemView);
        }
        return new DevicePolicyGroupViewHolder(devicePolicyGroupView);
    }

    @Override
    public void onBindViewHolder(DevicePolicyGroupViewHolder devicePolicyGroupViewHolder,
            int position) {
        if (!TextUtils.isEmpty(mProviderName)) {
            devicePolicyGroupViewHolder.bind(getItem(position), mMaxDevicePolicy, mProviderName);
        } else {
            Futures.addCallback(SetupParametersClient.getInstance().getKioskAppProviderName(),
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(String result) {
                            mProviderName = result;
                            notifyItemChanged(position);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            LogUtil.e(TAG, "Failed to get device provider name!", t);
                        }
                    }, MoreExecutors.directExecutor());
        }
    }

    @Override
    public void submitList(@Nullable List<DevicePolicyGroup> list) {
        super.submitList(list);
        mMaxDevicePolicy = 0;
        if (list == null) {
            return;
        }
        for (DevicePolicyGroup devicePolicyGroup : list) {
            mMaxDevicePolicy = Math.max(mMaxDevicePolicy,
                    devicePolicyGroup.getDevicePolicyList().size());
        }
    }
}
