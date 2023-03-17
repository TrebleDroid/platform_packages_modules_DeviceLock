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

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.setup.SetupParameters;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * The screen that provides information about the provision.
 */
public final class ProvisionInfoFragment extends Fragment {

    private static final String TAG = "ProvisionInfoFragment";
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_provision_info, container, false);
        TextView headerTextView = view.findViewById(R.id.header_text);
        String providerName = SetupParameters.getKioskAppProviderName(getActivity());
        if (TextUtils.isEmpty(providerName)) {
            LogUtil.e(TAG, "Device provider name is empty, should not reach here.");
            return view;
        }
        if (headerTextView == null) {
            LogUtil.e(TAG, "Could not find header TextView, should not reach here.");
            return view;
        }
        headerTextView.setText(getString(R.string.device_provided_by_creditor, providerName));

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerview_provision_info);
        ProvisionInfoListAdapter adapter = new ProvisionInfoListAdapter();

        ProvisionInfoViewModel viewModel = new ViewModelProvider(this).get(
                ProvisionInfoViewModel.class);
        viewModel.getProvisionInfoListLiveData().observe(getViewLifecycleOwner(),
                adapter::submitList);
        recyclerView.setAdapter(adapter);
    }
}
