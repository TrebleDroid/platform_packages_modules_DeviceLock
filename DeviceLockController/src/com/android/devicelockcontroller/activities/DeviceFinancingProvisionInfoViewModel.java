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

import com.android.devicelockcontroller.R;

import java.util.List;

/**
 * This class provides resources and data used for the device financing use case.
 */
public class DeviceFinancingProvisionInfoViewModel extends ProvisionInfoViewModel {

    private static final int HEADER_DRAWABLE_ID = R.drawable.ic_info_24px;

    private static final int MANDATORY_HEADER_TEXT_ID = R.string.device_provided_by_provider;

    private static final ProvisionInfo[] PROVISION_INFOS = new ProvisionInfo[]{
            new ProvisionInfo(R.drawable.ic_file_download_24px,
                    R.string.download_kiosk_app, /* termsAndConditionsLinkIncluded= */ false),
            new ProvisionInfo(R.drawable.ic_lock_outline_24px,
                    R.string.restrict_device_if_missing_payment,
                    /* termsAndConditionsLinkIncluded= */ true)};
    private static final int HEADER_TEXT_ID = R.string.enroll_your_device_header;

    private static final int SUB_HEADER_TEXT_ID =
            R.string.enroll_your_device_financing_subheader;

    public DeviceFinancingProvisionInfoViewModel() {
        mHeaderDrawableId = HEADER_DRAWABLE_ID;
        mMandatoryHeaderTextId = MANDATORY_HEADER_TEXT_ID;
        mHeaderTextId = HEADER_TEXT_ID;
        mSubHeaderTextId = SUB_HEADER_TEXT_ID;
        mProvisionInfoList = List.of(PROVISION_INFOS);
        retrieveData();
    }
}
