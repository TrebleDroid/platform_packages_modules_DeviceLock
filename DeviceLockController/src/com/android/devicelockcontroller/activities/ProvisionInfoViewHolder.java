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

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.devicelockcontroller.R;

/**
 * A {@link androidx.recyclerview.widget.RecyclerView.ViewHolder} class which describes the item
 * views used in the {@link RecyclerView}
 */
public final class ProvisionInfoViewHolder extends RecyclerView.ViewHolder {

    private final TextView mTextView;

    public ProvisionInfoViewHolder(@NonNull View itemView) {
        super(itemView);
        mTextView = itemView.findViewById(R.id.text_view_item_provision_info);
    }

    void bind(ProvisionInfo provisionInfo) {
        mTextView.setText(provisionInfo.getTextId());
        mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(provisionInfo.getDrawableId(),
                /* top=*/ 0,
                /* end=*/ 0,
                /* bottom=*/ 0);
    }
}
