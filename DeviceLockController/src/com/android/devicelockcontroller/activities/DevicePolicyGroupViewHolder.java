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

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.setup.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;

/**
 * A {@link androidx.recyclerview.widget.RecyclerView.ViewHolder} class which describes the item
 * views used in the {@link RecyclerView}
 */
final class DevicePolicyGroupViewHolder extends RecyclerView.ViewHolder {

    private static final String TAG = "DevicePolicyGroupViewHolder";

    private final TextView mGroupTitleTextView;
    private final ViewGroup mDevicePolicyItems;

    DevicePolicyGroupViewHolder(View iteView) {
        super(iteView);
        mGroupTitleTextView = iteView.findViewById(R.id.text_view_device_policy_group_title);
        mDevicePolicyItems = iteView.findViewById(R.id.device_policy_items);
    }

    void bind(DevicePolicyGroup devicePolicyGroup, int maxDevicePolicy) {
        Context context = itemView.getContext();
        String providerName = Futures.getUnchecked(
                SetupParametersClient.getInstance().getKioskAppProviderName());
        if (TextUtils.isEmpty(providerName)) {
            LogUtil.e(TAG, "Device provider name is empty, should not reach here.");
            return;
        }
        mGroupTitleTextView.setText(
                context.getString(devicePolicyGroup.getGroupTitleTextId(), providerName));
        for (int i = 0; i < devicePolicyGroup.getDevicePolicyList().size(); ++i) {
            TextView devicePolicyItemView = (TextView) mDevicePolicyItems.getChildAt(i);
            DevicePolicy devicePolicy = devicePolicyGroup.getDevicePolicyList().get(i);
            devicePolicyItemView.setText(devicePolicy.getTextId());
            devicePolicyItemView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    devicePolicy.getDrawableId(),
                    /* top=*/ 0,
                    /* end=*/ 0,
                    /* bottom=*/ 0);
            devicePolicyItemView.setVisibility(View.VISIBLE);
        }
        // not every DevicePolicyGroup has the maximum number of DevicePolicy, hide the extra views
        for (int i = devicePolicyGroup.getDevicePolicyList().size(); i < maxDevicePolicy; ++i) {
            TextView devicePolicyItemView = (TextView) mDevicePolicyItems.getChildAt(i);
            devicePolicyItemView.setVisibility(View.GONE);
        }
    }
}
