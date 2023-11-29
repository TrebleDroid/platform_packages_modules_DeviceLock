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

import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.SpannableString;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
public final class ProvisionInfoViewHolderTest {

    private static final ProvisionInfo
            TEST_PROVISION_INFO_WITH_TERMS_AND_CONDITIONS_URL = new ProvisionInfo(
            R.drawable.ic_lock_outline_24px,
            R.string.restrict_device_if_dont_make_payment,
            /* termsAndConditionsLinkIncluded= */ true);
    private static final int TEST_URL_TEXT_ID =
            TEST_PROVISION_INFO_WITH_TERMS_AND_CONDITIONS_URL.getTextId();
    private static final ProvisionInfo
            TEST_PROVISION_INFO_WITHOUT_TERMS_AND_CONDITIONS_URL = new ProvisionInfo(
            R.drawable.ic_file_download_24px,
            R.string.download_kiosk_app,
            /* termsAndConditionsLinkIncluded= */ false);
    private static final String TEST_PROVIDER = "Test Provider";
    private static final String TEST_TERMS_AND_CONDITION_URL = "www.termsAndCondition.com";
    private TextView mTextView;
    private ProvisionInfoViewHolder mProvisionInfoViewHolder;
    private TestDeviceLockControllerApplication mTestApp;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        mTextView = new TextView(mTestApp);
        mTextView.setId(R.id.text_view_item_provision_info);
        mProvisionInfoViewHolder = new ProvisionInfoViewHolder(mTextView);
    }

    @Test
    public void bind_allInfoAvailable_withTermsAndConditionsURL_shouldSetTextViewWithUrl() {
        mProvisionInfoViewHolder.bind(TEST_PROVISION_INFO_WITH_TERMS_AND_CONDITIONS_URL,
                TEST_PROVIDER, TEST_TERMS_AND_CONDITION_URL);

        String expectedString =
                new SpannableString(
                        Html.fromHtml(
                                String.format(mTestApp.getString(TEST_URL_TEXT_ID), TEST_PROVIDER,
                                        TEST_TERMS_AND_CONDITION_URL),
                                Html.FROM_HTML_MODE_COMPACT)).toString();

        String actualString = mTextView.getText().toString();
        assertThat(actualString).isEqualTo(expectedString);
        Drawable[] compoundDrawablesRelatives = mTextView.getCompoundDrawablesRelative();
        assertThat(compoundDrawablesRelatives.length).isAtLeast(1);
        assertThat(Shadows.shadowOf(compoundDrawablesRelatives[0]).getCreatedFromResId()).isEqualTo(
                TEST_PROVISION_INFO_WITH_TERMS_AND_CONDITIONS_URL.getDrawableId());
    }

    @Test
    public void bind_allInfoAvailable_withoutTermsAndConditionsURL_shouldSetTextView() {
        mProvisionInfoViewHolder.bind(TEST_PROVISION_INFO_WITHOUT_TERMS_AND_CONDITIONS_URL,
                TEST_PROVIDER,  /* termsAndConditionsUrl= */ null);

        String text = String.format(mTestApp.getString(
                TEST_PROVISION_INFO_WITHOUT_TERMS_AND_CONDITIONS_URL.getTextId()), TEST_PROVIDER);
        assertThat(mTextView.getText().toString()).isEqualTo(text);
        Drawable[] compoundDrawablesRelative = mTextView.getCompoundDrawablesRelative();
        assertThat(compoundDrawablesRelative.length).isAtLeast(1);
        assertThat(Shadows.shadowOf(compoundDrawablesRelative[0]).getCreatedFromResId()).isEqualTo(
                TEST_PROVISION_INFO_WITHOUT_TERMS_AND_CONDITIONS_URL.getDrawableId());
    }

    @Test
    public void bind_providerUnavailable_shouldNotSetTextView() {
        mProvisionInfoViewHolder.bind(TEST_PROVISION_INFO_WITHOUT_TERMS_AND_CONDITIONS_URL,
                /* providerName= */ null, /* termsAndConditionsUrl= */ null);

        assertThat(mTextView.getText().toString()).isEmpty();
        Drawable[] compoundDrawablesRelatives = mTextView.getCompoundDrawablesRelative();
        assertThat(compoundDrawablesRelatives.length).isAtLeast(1);
        assertThat(compoundDrawablesRelatives[0]).isNull();
    }

    @Test
    public void bind_urlUnavailableButExpected_shouldNotSetTextView() {
        mProvisionInfoViewHolder.bind(TEST_PROVISION_INFO_WITH_TERMS_AND_CONDITIONS_URL,
                TEST_PROVIDER, /* termsAndConditionsUrl= */ null);

        assertThat(mTextView.getText().toString()).isEmpty();
        Drawable[] compoundDrawablesRelatives = mTextView.getCompoundDrawablesRelative();
        assertThat(compoundDrawablesRelatives.length).isAtLeast(1);
        assertThat(compoundDrawablesRelatives[0]).isNull();
    }
}
