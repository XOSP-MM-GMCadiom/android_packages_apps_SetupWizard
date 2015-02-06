/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.cyanogenmod.setupwizard.setup;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.IWindowManager;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.widget.CheckBox;
import android.widget.TextView;

import com.cyanogenmod.setupwizard.R;
import com.cyanogenmod.setupwizard.ui.SetupPageFragment;
import com.cyanogenmod.setupwizard.ui.WebViewDialogFragment;
import com.cyanogenmod.setupwizard.util.SetupWizardUtils;
import com.cyanogenmod.setupwizard.util.WhisperPushUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import org.cyanogenmod.hardware.KeyDisabler;

public class CyanogenSettingsPage extends SetupPage {

    public static final String TAG = "CyanogenSettingsPage";

    public static final String KEY_SEND_METRICS = "send_metrics";
    public static final String KEY_REGISTER_WHISPERPUSH = "register";
    public static final String KEY_ENABLE_NAV_KEYS = "enable_nav_keys";

    public static final String SETTING_METRICS = "settings.cyanogen.allow_metrics";
    public static final String PRIVACY_POLICY_URI = "https://cyngn.com/legal/privacy-policy";

    public CyanogenSettingsPage(Context context, SetupDataCallbacks callbacks) {
        super(context, callbacks);
    }

    @Override
    public Fragment getFragment(FragmentManager fragmentManager, int action) {
        Fragment fragment = fragmentManager.findFragmentByTag(getKey());
        if (fragment == null) {
            Bundle args = new Bundle();
            args.putString(Page.KEY_PAGE_ARGUMENT, getKey());
            args.putInt(Page.KEY_PAGE_ACTION, action);
            fragment = new CyanogenSettingsFragment();
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public String getKey() {
        return TAG;
    }

    @Override
    public int getTitleResId() {
        return R.string.setup_services;
    }

    private static void writeDisableNavkeysOption(Context context, boolean enabled) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int defaultBrightness = context.getResources().getInteger(
                com.android.internal.R.integer.config_buttonBrightnessSettingDefault);

        Settings.System.putInt(context.getContentResolver(),
                Settings.System.DEV_FORCE_SHOW_NAVBAR, enabled ? 1 : 0);
        KeyDisabler.setActive(enabled);

        /* Save/restore button timeouts to disable them in softkey mode */
        SharedPreferences.Editor editor = prefs.edit();

        if (enabled) {
            int currentBrightness = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.BUTTON_BRIGHTNESS, defaultBrightness);
            if (!prefs.contains("pre_navbar_button_backlight")) {
                editor.putInt("pre_navbar_button_backlight", currentBrightness);
            }
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.BUTTON_BRIGHTNESS, 0);
        } else {
            int oldBright = prefs.getInt("pre_navbar_button_backlight", -1);
            if (oldBright != -1) {
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.BUTTON_BRIGHTNESS, oldBright);
                editor.remove("pre_navbar_button_backlight");
            }
        }
        editor.commit();
    }

    @Override
    public void onFinishSetup() {
        if (getData().containsKey(KEY_ENABLE_NAV_KEYS)) {
            writeDisableNavkeysOption(mContext, getData().getBoolean(KEY_ENABLE_NAV_KEYS));
        }
        handleWhisperPushRegistration();
        handleEnableMetrics();
    }

    private void handleWhisperPushRegistration() {
        Bundle privacyData = getData();
        if (privacyData != null &&
                privacyData.containsKey(CyanogenSettingsPage.KEY_REGISTER_WHISPERPUSH) &&
                privacyData.getBoolean(CyanogenSettingsPage.KEY_REGISTER_WHISPERPUSH)) {
            Log.i(TAG, "Registering with WhisperPush");
            WhisperPushUtils.startRegistration(mContext);
        }
    }

    private void handleEnableMetrics() {
        Bundle privacyData = getData();
        if (privacyData != null
                && privacyData.containsKey(CyanogenSettingsPage.KEY_SEND_METRICS)) {
            Settings.System.putInt(mContext.getContentResolver(), CyanogenSettingsPage.SETTING_METRICS,
                    privacyData.getBoolean(CyanogenSettingsPage.KEY_SEND_METRICS) ? 1 : 0);
        }
    }

    private static boolean hideKeyDisabler() {
        try {
            return !KeyDisabler.isSupported();
        } catch (NoClassDefFoundError e) {
            // Hardware abstraction framework not installed
            return true;
        }
    }

    private static boolean isKeyDisablerActive() {
        try {
            return KeyDisabler.isActive();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hideWhisperPush(Context context) {
        final int playServicesAvailable = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(context);
        return playServicesAvailable != ConnectionResult.SUCCESS
                || (SetupWizardUtils.isGSMPhone(context) && SetupWizardUtils.isSimMissing(context));
    }

    public static class CyanogenSettingsFragment extends SetupPageFragment {

        private View mMetricsRow;
        private View mNavKeysRow;
        private View mSecureSmsRow;
        private CheckBox mMetrics;
        private CheckBox mNavKeys;
        private CheckBox mSecureSms;


        private View.OnClickListener mMetricsClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean checked = !mMetrics.isChecked();
                mMetrics.setChecked(checked);
                mPage.getData().putBoolean(KEY_SEND_METRICS, checked);
            }
        };

        private View.OnClickListener mNavKeysClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean checked = !mNavKeys.isChecked();
                mNavKeys.setChecked(checked);
                mPage.getData().putBoolean(KEY_ENABLE_NAV_KEYS, checked);
            }
        };

        private View.OnClickListener mSecureSmsClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean checked = !mSecureSms.isChecked();
                mSecureSms.setChecked(checked);
                mPage.getData().putBoolean(KEY_REGISTER_WHISPERPUSH, checked);
            }
        };

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            final Activity activity = getActivity();
            activity.getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
        }

        @Override
        protected void initializePage() {
            String privacy_policy = getString(R.string.services_privacy_policy);
            String summary = getString(R.string.services_explanation, privacy_policy);
            SpannableString ss = new SpannableString(summary);
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(View textView) {
                    WebViewDialogFragment.newInstance()
                            .setUri(PRIVACY_POLICY_URI)
                            .show(getActivity().getFragmentManager(), WebViewDialogFragment.TAG);
                }
            };
            ss.setSpan(clickableSpan,
                    summary.length() - privacy_policy.length() - 1,
                    summary.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView textView = (TextView) mRootView.findViewById(R.id.privacy_policy);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            textView.setText(ss);
            mMetricsRow = mRootView.findViewById(R.id.metrics);
            mMetricsRow.setOnClickListener(mMetricsClickListener);
            mMetrics = (CheckBox) mRootView.findViewById(R.id.enable_metrics_checkbox);
            boolean metricsChecked =
                    !mPage.getData().containsKey(KEY_SEND_METRICS) || mPage.getData()
                            .getBoolean(KEY_SEND_METRICS);
            mMetrics.setChecked(metricsChecked);
            mPage.getData().putBoolean(KEY_SEND_METRICS, metricsChecked);
            mNavKeysRow = mRootView.findViewById(R.id.nav_keys);
            mNavKeysRow.setOnClickListener(mNavKeysClickListener);
            mNavKeys = (CheckBox) mRootView.findViewById(R.id.nav_keys_checkbox);
            boolean needsNavBar = true;
            try {
                IWindowManager windowManager = WindowManagerGlobal.getWindowManagerService();
                needsNavBar = windowManager.needsNavigationBar();
            } catch (RemoteException e) {
            }
            if (hideKeyDisabler() || needsNavBar) {
                mNavKeysRow.setVisibility(View.GONE);
            } else {
                boolean navKeysDisabled =
                        isKeyDisablerActive();
                mNavKeys.setChecked(navKeysDisabled);
            }
            mSecureSmsRow = mRootView.findViewById(R.id.secure_sms);
            mSecureSmsRow.setOnClickListener(mSecureSmsClickListener);
            if (hideWhisperPush(getActivity())) {
                mSecureSmsRow.setVisibility(View.GONE);
            }
            mSecureSms = (CheckBox) mRootView.findViewById(R.id.secure_sms_checkbox);
            boolean smsChecked = mPage.getData().containsKey(KEY_REGISTER_WHISPERPUSH) ?
                    mPage.getData().getBoolean(KEY_REGISTER_WHISPERPUSH) :
                    false;
            mSecureSms.setChecked(smsChecked);
            mPage.getData().putBoolean(KEY_REGISTER_WHISPERPUSH, smsChecked);
        }

        @Override
        protected int getLayoutResource() {
            return R.layout.setup_cyanogen_services;
        }

        @Override
        public void onResume() {
            super.onResume();
            updateDisableNavkeysOption();
        }

        private void updateDisableNavkeysOption() {
            boolean enabled = Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.DEV_FORCE_SHOW_NAVBAR, 0) != 0;
            boolean checked = mPage.getData().containsKey(KEY_ENABLE_NAV_KEYS) ?
                    mPage.getData().getBoolean(KEY_ENABLE_NAV_KEYS) :
                    enabled;
            mNavKeys.setChecked(checked);
        }

    }
}
