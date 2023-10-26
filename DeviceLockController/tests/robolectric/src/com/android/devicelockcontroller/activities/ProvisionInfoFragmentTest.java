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

import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_PROVISIONING;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;
import android.widget.ImageView;

import com.android.devicelockcontroller.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class ProvisionInfoFragmentTest {

    @Test
    public void onCreateView_viewIsInflated() {
        Intent intent = new Intent();
        intent.setAction(ACTION_START_DEVICE_FINANCING_PROVISIONING);
        LandingActivity activity = Robolectric.buildActivity(LandingActivity.class,
                intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        ImageView imageView = activity.findViewById(R.id.header_icon);
        assertThat(shadowOf(imageView.getDrawable()).getCreatedFromResId()).isEqualTo(
                R.drawable.ic_info_24px);
    }
}
