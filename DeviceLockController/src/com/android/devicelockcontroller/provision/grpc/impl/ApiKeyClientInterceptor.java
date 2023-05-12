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

package com.android.devicelockcontroller.provision.grpc.impl;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.util.LogUtil;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Add api key metadata to a gRPC {@link io.grpc.stub.AbstractStub} for authentication.
 */
final class ApiKeyClientInterceptor implements ClientInterceptor {
    private static final String TAG = "SpatulaClientInterceptor";
    private final Context mContext;

    ApiKeyClientInterceptor(Context context) {
        mContext = context;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel channel) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                channel.newCall(method, options)) {
            @Override
            public void start(Listener<RespT> listener, Metadata headers) {
                Resources resources = mContext.getResources();
                String name = resources.getString(R.string.api_key_name);
                String value = resources.getString(R.string.api_key_value);
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(value)) {
                    LogUtil.i(TAG, "api key is not available, skip api key authentication.");
                    return;
                }

                headers.put(Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER), value);
                super.start(listener, headers);
            }
        };
    }
}
