/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.devicelock;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.devicelock.DeviceId;
import android.devicelock.DeviceLockManager;

import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Test system DeviceLockManager APIs.
 */
@RunWith(JUnit4.class)
public final class DeviceLockManagerTest {
    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    private final DeviceLockManager mDeviceLockManager =
            (DeviceLockManager) InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(Context.DEVICE_LOCK_SERVICE);

    private static final int TIMEOUT = 1;

    public ListenableFuture<Boolean> getIsDeviceLockedFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockManager.isDeviceLocked(mExecutorService,
                            new OutcomeReceiver<Boolean, Exception>() {
                                @Override
                                public void onResult(Boolean locked) {
                                    completer.set(locked);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "isDeviceLocked operation";
                });
    }

    public ListenableFuture<Void> getLockDeviceFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockManager.lockDevice(mExecutorService,
                            new OutcomeReceiver<Void, Exception>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "lockDevice operation";
                });
    }

    public ListenableFuture<Void> getUnlockDeviceFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockManager.unlockDevice(mExecutorService,
                            new OutcomeReceiver<Void, Exception>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "unlockDevice operation";
                });
    }

    public ListenableFuture<DeviceId> getDeviceIdFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockManager.getDeviceId(mExecutorService,
                            new OutcomeReceiver<DeviceId, Exception>() {
                                @Override
                                public void onResult(DeviceId deviceId) {
                                    completer.set(deviceId);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "getDeviceId operation";
                });
    }

    public ListenableFuture<Map<Integer, String>> getKioskAppsFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockManager.getKioskApps(mExecutorService,
                            new OutcomeReceiver<Map<Integer, String>, Exception>() {
                                @Override
                                public void onResult(Map<Integer, String> result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "getKioskApps operation";
                });
    }

    @BeforeClass
    public static void setupClass() {
        dropShellPermissions();
    }

    @Test
    public void lockDevicePermissionCheck() {
        ListenableFuture<Void> lockDeviceFuture = getLockDeviceFuture();

        Exception lockDeviceResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            lockDeviceFuture.get(TIMEOUT, TimeUnit.SECONDS);
                        });
        assertThat(lockDeviceResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void unlockDevicePermissionCheck() {
        ListenableFuture<Void> unlockDeviceFuture = getUnlockDeviceFuture();

        Exception lockDeviceResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            unlockDeviceFuture.get(TIMEOUT, TimeUnit.SECONDS);
                        });
        assertThat(lockDeviceResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void isDeviceLockedPermissionCheck() {
        ListenableFuture<Boolean> isDeviceLockedFuture = getIsDeviceLockedFuture();

        Exception isDeviceLockedResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            isDeviceLockedFuture.get(TIMEOUT, TimeUnit.SECONDS);
                        });
        assertThat(isDeviceLockedResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void getDeviceIdPermissionCheck() {
        ListenableFuture<DeviceId> deviceIdFuture = getDeviceIdFuture();

        Exception isDeviceLockedResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            deviceIdFuture.get(TIMEOUT, TimeUnit.SECONDS);
                        });
        assertThat(isDeviceLockedResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void deviceShouldLockAndUnlock() throws InterruptedException, ExecutionException,
            TimeoutException {
        adoptShellPermissions();

        getUnlockDeviceFuture().get(TIMEOUT, TimeUnit.SECONDS);

        boolean locked = getIsDeviceLockedFuture().get(TIMEOUT, TimeUnit.SECONDS);
        assertThat(locked).isFalse();

        getLockDeviceFuture().get(TIMEOUT, TimeUnit.SECONDS);

        locked = getIsDeviceLockedFuture().get(TIMEOUT, TimeUnit.SECONDS);
        assertThat(locked).isTrue();

        getUnlockDeviceFuture().get(TIMEOUT, TimeUnit.SECONDS);

        locked = getIsDeviceLockedFuture().get(TIMEOUT, TimeUnit.SECONDS);
        assertThat(locked).isFalse();

        dropShellPermissions();
    }

    @Test
    public void getDeviceIdShouldReturnAnId()
            throws ExecutionException, InterruptedException, TimeoutException {
        adoptShellPermissions();

        DeviceId deviceId = getDeviceIdFuture().get(TIMEOUT, TimeUnit.SECONDS);
        assertThat(deviceId.getType()).isAnyOf(DeviceId.DEVICE_ID_TYPE_IMEI,
                DeviceId.DEVICE_ID_TYPE_MEID);
        assertThat(deviceId.getId()).isNotEmpty();

        dropShellPermissions();
    }

    @Test
    public void getKioskAppShouldReturnMapping()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<Integer, String> kioskAppsMap = getKioskAppsFuture().get(TIMEOUT, TimeUnit.SECONDS);
        // TODO: update test once we have the service returning the correct mappings
        assertThat(kioskAppsMap).isEmpty();
    }

    private static void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
    }

    private static void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }
}
