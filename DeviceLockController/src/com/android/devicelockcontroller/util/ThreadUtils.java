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

package com.android.devicelockcontroller.util;

import android.os.Looper;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** An utility class related to multi-threading */
public final class ThreadUtils {
    private static volatile Executor sSequentialSchedulerExecutor;

    // Prevent instantiation
    private ThreadUtils() {
    }

    /**
     * Assert that calling thread is not the main thread. If the calling thread is main thread, an
     * {@link IllegalStateException} will be thrown.
     *
     * @param methodName The name of the method which will show up in the log if an exception is
     *                   thrown.
     */
    public static void assertWorkerThread(String methodName) {
        if (Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("Can not invoke " + methodName + " on the main thread");
        }
    }

    /**
     * Assert that calling thread is the main thread. If the calling thread is not the main thread,
     * an {@link IllegalStateException} will be thrown.
     *
     * @param methodName The name of the method which will show up in the log if an exception is
     *                   thrown.
     */
    public static void assertMainThread(String methodName) {
        if (!Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException(
                    "Can not invoke " + methodName + " on a background thread");
        }
    }

    /**
     * Get the sequential executor for
     * {@link com.android.devicelockcontroller.DeviceLockControllerScheduler}
     */
    public static synchronized Executor getSequentialSchedulerExecutor() {
        if (sSequentialSchedulerExecutor == null) {
            sSequentialSchedulerExecutor = MoreExecutors.newSequentialExecutor(
                    Executors.newSingleThreadExecutor());
        }
        return sSequentialSchedulerExecutor;
    }
}
