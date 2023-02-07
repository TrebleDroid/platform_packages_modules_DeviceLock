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

/**
 * A simple implementation of {@link DownloadRetryPolicy}. Retry is allowed only if has not reached
 * the {@link #mMaxRetryAttempts} maximum attempts.
 */
public final class SimpleDownloadRetryPolicy implements DownloadRetryPolicy {
    private final int mMaxRetryAttempts;
    private int mCurrentRetryCount;

    SimpleDownloadRetryPolicy(int maxRetryAttempts) {
        mMaxRetryAttempts = maxRetryAttempts;
    }

    @Override
    public boolean needToRetry() {
        if (mCurrentRetryCount < mMaxRetryAttempts) {
            mCurrentRetryCount++;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getCurrentRetryCount() {
        return mCurrentRetryCount;
    }
}
