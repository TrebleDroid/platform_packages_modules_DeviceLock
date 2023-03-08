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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;

/** A factory which produces {@link AbstractTask}s with parameters. */
public final class TaskWorkerFactory extends WorkerFactory {
    private final ListeningExecutorService mExecutorService;

    public TaskWorkerFactory() {
        mExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    }

    @Nullable
    @Override
    public ListenableWorker createWorker(
            @NonNull Context context,
            @NonNull String workerClassName,
            @NonNull WorkerParameters workerParameters) {
        try {
            Class<?> clazz = Class.forName(workerClassName);
            if (clazz == DownloadPackageTask.class) {
                return new DownloadPackageTask(context, workerParameters, mExecutorService);
            } else if (clazz == VerifyPackageTask.class) {
                return new VerifyPackageTask(context, workerParameters, mExecutorService);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Task not found " + workerClassName, e);
        }
        return null;
    }
}
