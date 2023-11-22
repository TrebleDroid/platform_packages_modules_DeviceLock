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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProvisionInfoTest {

    @Test
    public void equals_withDifferentFieldValues_returnsFalse() {
        ProvisionInfo p1 = new ProvisionInfo(/* drawableId= */0, /* textId= */
                1, /* termsAndConditionsLinkIncluded= */false);
        ProvisionInfo p2 = new ProvisionInfo(/* drawableId= */100, /* textId= */
                101, /* termsAndConditionsLinkIncluded= */true);

        assertThat(p1.equals(p2)).isFalse();
    }

    @Test
    public void equals_withSameFieldValues_returnsTrue() {
        ProvisionInfo p1 = new ProvisionInfo(/* drawableId= */0, /* textId= */
                1, /* termsAndConditionsLinkIncluded= */true);
        ProvisionInfo p2 = new ProvisionInfo(/* drawableId= */0, /* textId= */
                1, /* termsAndConditionsLinkIncluded= */true);

        assertThat(p1.equals(p2)).isTrue();
    }
}
