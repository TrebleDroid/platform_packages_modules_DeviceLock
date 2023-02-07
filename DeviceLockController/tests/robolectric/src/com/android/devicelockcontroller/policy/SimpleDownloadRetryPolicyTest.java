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

package com.android.devicelockcontroller.policy;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleDownloadRetryPolicyTest {
    @Test
    public void testRetryNotAllowed() {
        // GIVEN max retry attempt is 0
        final SimpleDownloadRetryPolicy retryPolicy =
                new SimpleDownloadRetryPolicy(0 /*maxRetryAttempt*/);
        assertThat(retryPolicy.needToRetry()).isFalse();
        assertThat(retryPolicy.getCurrentRetryCount()).isEqualTo(0);
    }

    @Test
    public void testRandomMaxRetryAttempt() {
        // GIVEN a random max retry attempt
        final int maxRetryAttempt = 3;
        final SimpleDownloadRetryPolicy retryPolicy =
                new SimpleDownloadRetryPolicy(maxRetryAttempt);

        // WHEN retry attempt is less than the limit
        for (int i = 0; i < maxRetryAttempt; i++) {
            // THEN retry should be allowed
            assertThat(retryPolicy.needToRetry()).isTrue();
            assertThat(retryPolicy.getCurrentRetryCount()).isEqualTo(i + 1);
        }

        // WHEN max attempt is reached
        // THEN retry should NOT be allowed
        assertThat(retryPolicy.needToRetry()).isFalse();
        assertThat(retryPolicy.getCurrentRetryCount()).isEqualTo(maxRetryAttempt);
    }
}
