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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.R;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class LandingActivityTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Ignore("http://b/269463682")
    @Test
    public void landingActivity_showsCorrectText() {
        LandingActivity activity = Robolectric.buildActivity(LandingActivity.class).setup().get();

        TextView textView = activity.findViewById(R.id.landing_activity_text_view);
        String expectedString = mContext.getString(R.string.app_name);
        assertThat(textView.getText().toString()).isEqualTo(expectedString);
    }
}
