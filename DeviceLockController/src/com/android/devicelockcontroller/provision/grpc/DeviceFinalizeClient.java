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

package com.android.devicelockcontroller.provision.grpc;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import io.grpc.Status;

/**
 * An abstract class that's intended for implementation of class that manages communication with
 * Device finalize service.
 */
public abstract class DeviceFinalizeClient {
    private static final Object sInstanceLock = new Object();
    @GuardedBy("sInstanceLock")
    private static DeviceFinalizeClient sClient;
    protected static String sEnrollmentToken = "";
    protected static String sRegisteredId = "";
    protected static String sHostName = "";
    protected static int sPortNumber = 0;

    /**
     * Get a instance of {@link DeviceFinalizeClient} object.
     * Note that, the arguments will be ignored after first initialization.
     */
    public static DeviceFinalizeClient getInstance(
            String className,
            String hostName,
            int portNumber,
            String registeredId,
            String enrollmentToken) {
        synchronized (sInstanceLock) {
            if (sClient == null) {
                sHostName = hostName;
                sPortNumber = portNumber;
                sRegisteredId = registeredId;
                sEnrollmentToken = enrollmentToken;
                try {
                    Class<?> clazz = Class.forName(className);
                    sClient = (DeviceFinalizeClient) clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get DeviceFinalizeClient instance", e);
                }
            }
            return sClient;
        }
    }

    /**
     * Reports that a device completed a Device Lock program.
     */
    public abstract ReportDeviceProgramCompleteResponse reportDeviceProgramComplete();

    /**
     * Class that used to indicate the successfulness / failure status of the response.
     */
    public static final class ReportDeviceProgramCompleteResponse extends
            DeviceCheckInGrpcResponse {
        public ReportDeviceProgramCompleteResponse() {
            super();
        }

        public ReportDeviceProgramCompleteResponse(@NonNull Status status) {
            super(status);
        }
    }
}
