/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;

import androidx.annotation.NonNull;

import com.android.settingslib.Utils;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetail.Callback;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusBarWindowView;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.Clock;

import java.util.List;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout {

    private boolean mExpanded;
    private boolean mQsDisabled;

    private TouchAnimator mAlphaAnimator;
    private TouchAnimator mTranslationAnimator;
    private TouchAnimator mIconsAlphaAnimator;
    private TouchAnimator mIconsAlphaAnimatorFixed;

    protected QuickQSPanel mHeaderQsPanel;
    private View mDatePrivacyView;
    private View mDateView;
    private View mSecurityHeaderView;
    private View mClockIconsView;
    private View mContainer;

    private View mQSCarriers;
    private Clock mClockView;
    private Space mDatePrivacySeparator;
    private View mClockIconsSeparator;
    private boolean mShowClockIconsSeparator;
    private View mRightLayout;
    private View mDateContainer;
    private View mPrivacyContainer;

    private BatteryMeterView mBatteryRemainingIcon;
    private StatusIconContainer mIconContainer;
    private View mPrivacyChip;

    private TintedIconManager mTintedIconManager;
    private QSExpansionPathInterpolator mQSExpansionPathInterpolator;

    private int mRoundedCornerPadding = 0;
    private int mWaterfallTopInset;
    private int mCutOutPaddingLeft;
    private int mCutOutPaddingRight;
    private float mViewAlpha = 1.0f;
    private float mKeyguardExpansionFraction;
    private int mTextColorPrimary = Color.TRANSPARENT;
    private int mTopViewMeasureHeight;

    @NonNull
    private List<String> mRssiIgnoredSlots;
    private boolean mIsSingleCarrier;

    private boolean mHasCenterCutout;
    private boolean mConfigShowBatteryEstimate;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * How much the view containing the clock and QQS will translate down when QS is fully expanded.
     *
     * This matches the measured height of the view containing the date and privacy icons.
     */
    public int getOffsetTranslation() {
        return mTopViewMeasureHeight;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mDatePrivacyView = findViewById(R.id.quick_status_bar_date_privacy);
        mClockIconsView = findViewById(R.id.quick_qs_status_icons);
        mQSCarriers = findViewById(R.id.carrier_group);
        mContainer = findViewById(R.id.qs_container);
        mIconContainer = findViewById(R.id.statusIcons);
        mPrivacyChip = findViewById(R.id.privacy_chip);
        mDateView = findViewById(R.id.date);
        mSecurityHeaderView = findViewById(R.id.header_text_container);
        mClockIconsSeparator = findViewById(R.id.separator);
        mRightLayout = findViewById(R.id.rightLayout);
        mDateContainer = findViewById(R.id.date_container);
        mPrivacyContainer = findViewById(R.id.privacy_container);

        mClockView = findViewById(R.id.clock);
        mClockView.setQsHeader();
        mDatePrivacySeparator = findViewById(R.id.space);
        // Tint for the battery icons are handled in setupHost()
        mBatteryRemainingIcon = findViewById(R.id.batteryRemainingIcon);

        updateResources();

        mBatteryRemainingIcon.setIsQsHeader(true);
        // QS will always show the estimate, and BatteryMeterView handles the case where
        // it's unavailable or charging
        mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);

        mIconsAlphaAnimatorFixed = new TouchAnimator.Builder()
                .addFloat(mIconContainer, "alpha", 0, 1)
                .addFloat(mBatteryRemainingIcon, "alpha", 0, 1)
                .build();
    }

    void onAttach(TintedIconManager iconManager,
            QSExpansionPathInterpolator qsExpansionPathInterpolator,
            List<String> rssiIgnoredSlots) {
        mTintedIconManager = iconManager;
        mRssiIgnoredSlots = rssiIgnoredSlots;
        int fillColor = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.textColorPrimary);

        // Set the correct tint for the status icons so they contrast
        iconManager.setTint(fillColor);

        mQSExpansionPathInterpolator = qsExpansionPathInterpolator;
        updateAnimators();
    }

    void setIsSingleCarrier(boolean isSingleCarrier) {
        mIsSingleCarrier = isSingleCarrier;
        updateAlphaAnimator();
    }

    public QuickQSPanel getHeaderQsPanel() {
        return mHeaderQsPanel;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mDatePrivacyView.getMeasuredHeight() != mTopViewMeasureHeight) {
            mTopViewMeasureHeight = mDatePrivacyView.getMeasuredHeight();
            updateAnimators();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
        setDatePrivacyContainersWidth(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    private void setDatePrivacyContainersWidth(boolean landscape) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mDateContainer.getLayoutParams();
        lp.width = landscape ? WRAP_CONTENT : 0;
        lp.weight = landscape ? 0f : 1f;
        mDateContainer.setLayoutParams(lp);

        lp = (LinearLayout.LayoutParams) mPrivacyContainer.getLayoutParams();
        lp.width = landscape ? WRAP_CONTENT : 0;
        lp.weight = landscape ? 0f : 1f;
        mPrivacyContainer.setLayoutParams(lp);
    }

    private void updateBatteryMode() {
        if (mConfigShowBatteryEstimate && !mHasCenterCutout) {
            mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);
        } else {
            mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ON);
        }
    }

    void updateResources() {
        Resources resources = mContext.getResources();

        mConfigShowBatteryEstimate = resources.getBoolean(R.bool.config_showBatteryEstimateQSBH);

        mRoundedCornerPadding = resources.getDimensionPixelSize(
                R.dimen.rounded_corner_content_padding);

        int qsOffsetHeight = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height);

        mDatePrivacyView.getLayoutParams().height =
                Math.max(qsOffsetHeight, mDatePrivacyView.getMinimumHeight());
        mDatePrivacyView.setLayoutParams(mDatePrivacyView.getLayoutParams());

        mClockIconsView.getLayoutParams().height =
                Math.max(qsOffsetHeight, mClockIconsView.getMinimumHeight());
        mClockIconsView.setLayoutParams(mClockIconsView.getLayoutParams());

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = mClockIconsView.getLayoutParams().height;
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);

        int textColor = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimary);
        if (textColor != mTextColorPrimary) {
            int textColorSecondary = Utils.getColorAttrDefaultColor(mContext,
                    android.R.attr.textColorSecondary);
            mTextColorPrimary = textColor;
            mClockView.setTextColor(textColor);
            if (mTintedIconManager != null) {
                mTintedIconManager.setTint(textColor);
            }
            mBatteryRemainingIcon.updateColors(mTextColorPrimary, textColorSecondary,
                    mTextColorPrimary);
        }

        MarginLayoutParams qqsLP = (MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        qqsLP.topMargin = mContext.getResources()
                .getDimensionPixelSize(R.dimen.qqs_layout_margin_top);
        mHeaderQsPanel.setLayoutParams(qqsLP);

        updateBatteryMode();
        updateHeadersPadding();
        updateAnimators();
    }

    private void updateAnimators() {
        updateAlphaAnimator();
        int offset = mTopViewMeasureHeight;

        mTranslationAnimator = new TouchAnimator.Builder()
                .addFloat(mContainer, "translationY", 0, offset)
                .setInterpolator(mQSExpansionPathInterpolator != null
                        ? mQSExpansionPathInterpolator.getYInterpolator()
                        : null)
                .build();
    }

    private void updateAlphaAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mSecurityHeaderView, "alpha", 0, 1)
                // These views appear on expanding down
                .addFloat(mClockView, "alpha", 0, 1)
                .addFloat(mQSCarriers, "alpha", 0, 1)
                .setListener(new TouchAnimator.ListenerAdapter() {
                    @Override
                    public void onAnimationAtEnd() {
                        super.onAnimationAtEnd();
                        if (!mIsSingleCarrier) {
                            mIconContainer.addIgnoredSlots(mRssiIgnoredSlots);
                        }
                    }

                    @Override
                    public void onAnimationStarted() {
                        setSeparatorVisibility(false);
                        if (!mIsSingleCarrier) {
                            mIconContainer.addIgnoredSlots(mRssiIgnoredSlots);
                        }
                    }

                    @Override
                    public void onAnimationAtStart() {
                        super.onAnimationAtStart();
                        setSeparatorVisibility(mShowClockIconsSeparator);
                        // In QQS we never ignore RSSI.
                        mIconContainer.removeIgnoredSlots(mRssiIgnoredSlots);
                    }
                });
        mAlphaAnimator = builder.build();
    }

    void setChipVisibility(boolean visibility) {
        mPrivacyChip.setVisibility(visibility ? View.VISIBLE : View.GONE);
        if (visibility) {
            // Animates the icons and battery indicator from alpha 0 to 1, when the chip is visible
            mIconsAlphaAnimator = mIconsAlphaAnimatorFixed;
            mIconsAlphaAnimator.setPosition(mKeyguardExpansionFraction);
        } else {
            mIconsAlphaAnimator = null;
            mIconContainer.setAlpha(1);
            mBatteryRemainingIcon.setAlpha(1);
        }

    }

    /** */
    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        quickQSPanelController.setExpanded(expanded);
	mDateView.setVisibility(mClockView.isClockDateEnabled() ? View.INVISIBLE : View.VISIBLE);
        updateEverything();
    }

    /**
     * Animates the inner contents based on the given expansion details.
     *
     * @param forceExpanded whether we should show the state expanded forcibly
     * @param expansionFraction how much the QS panel is expanded/pulled out (up to 1f)
     * @param panelTranslationY how much the panel has physically moved down vertically (required
     *                          for keyguard animations only)
     */
    public void setExpansion(boolean forceExpanded, float expansionFraction,
                             float panelTranslationY) {
        final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;

        if (mAlphaAnimator != null) {
            mAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mTranslationAnimator != null) {
            mTranslationAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mIconsAlphaAnimator != null) {
            mIconsAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        // If forceExpanded (we are opening QS from lockscreen), the animators have been set to
        // position = 1f.
        if (forceExpanded) {
            setTranslationY(panelTranslationY);
        } else {
            setTranslationY(0);
        }

        mKeyguardExpansionFraction = keyguardExpansionFraction;
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        mClockIconsView.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        updateResources();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Handle padding of the views
        DisplayCutout cutout = insets.getDisplayCutout();
        Pair<Integer, Integer> cornerCutoutPadding = StatusBarWindowView.cornerCutoutMargins(
                cutout, getDisplay());
        Pair<Integer, Integer> padding =
                StatusBarWindowView.paddingNeededForCutoutAndRoundedCorner(
                        cutout, cornerCutoutPadding, -1);
        mDatePrivacyView.setPadding(padding.first, 0, padding.second, 0);
        if (cutout == null) {
            mDateContainer.setPadding(0, 0, 0, 0);
        }
        LinearLayout.LayoutParams datePrivacySeparatorLayoutParams =
                (LinearLayout.LayoutParams) mDatePrivacySeparator.getLayoutParams();
        LinearLayout.LayoutParams mClockIconsSeparatorLayoutParams =
                (LinearLayout.LayoutParams) mClockIconsSeparator.getLayoutParams();
        boolean cornerCutout = cornerCutoutPadding != null
                && (cornerCutoutPadding.first == 0 || cornerCutoutPadding.second == 0);
        if (cutout != null) {
            Rect topCutout = cutout.getBoundingRectTop();
            if (topCutout.isEmpty() || cornerCutout) {
                datePrivacySeparatorLayoutParams.width = 0;
                mDatePrivacySeparator.setVisibility(View.GONE);
                mClockIconsSeparatorLayoutParams.width = 0;
                setSeparatorVisibility(false);
                mShowClockIconsSeparator = false;
                mHasCenterCutout = false;
            } else {
                datePrivacySeparatorLayoutParams.width = topCutout.width();
                mDatePrivacySeparator.setVisibility(View.VISIBLE);
                mClockIconsSeparatorLayoutParams.width = topCutout.width();
                mShowClockIconsSeparator = true;
                setSeparatorVisibility(mKeyguardExpansionFraction == 0f);
                mHasCenterCutout = true;
            }
        }
        mDatePrivacySeparator.setLayoutParams(datePrivacySeparatorLayoutParams);
        mClockIconsSeparator.setLayoutParams(mClockIconsSeparatorLayoutParams);
        mCutOutPaddingLeft = padding.first;
        mCutOutPaddingRight = padding.second;
        mWaterfallTopInset = cutout == null ? 0 : cutout.getWaterfallInsets().top;

        updateBatteryMode();
        updateHeadersPadding();
        return super.onApplyWindowInsets(insets);
    }

    /**
     * Sets the visibility of the separator between clock and icons.
     *
     * This separator is "visible" when there is a center cutout, to block that space. In that
     * case, the clock and the layout on the right (containing the icons and the battery meter) are
     * set to weight 1 to take the available space.
     * @param visible whether the separator between clock and icons should be visible.
     */
    private void setSeparatorVisibility(boolean visible) {
        int newVisibility = visible ? View.VISIBLE : View.GONE;
        if (mClockIconsSeparator.getVisibility() == newVisibility) return;

        mClockIconsSeparator.setVisibility(visible ? View.VISIBLE : View.GONE);
        mQSCarriers.setVisibility(visible ? View.GONE : View.VISIBLE);

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mClockView.getLayoutParams();
        lp.width = visible ? 0 : WRAP_CONTENT;
        lp.weight = visible ? 1f : 0f;
        mClockView.setLayoutParams(lp);

        lp = (LinearLayout.LayoutParams) mRightLayout.getLayoutParams();
        lp.width = visible ? 0 : WRAP_CONTENT;
        lp.weight = visible ? 1f : 0f;
        mRightLayout.setLayoutParams(lp);
    }

    private void updateHeadersPadding() {
        setContentMargins(mDatePrivacyView, 0, 0);
        setContentMargins(mClockIconsView, 0, 0);
        int paddingLeft = 0;
        int paddingRight = 0;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int leftMargin = lp.leftMargin;
        int rightMargin = lp.rightMargin;

        // The clock might collide with cutouts, let's shift it out of the way.
        // We only do that if the inset is bigger than our own padding, since it's nicer to
        // align with
        if (mCutOutPaddingLeft > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingLeft, mRoundedCornerPadding);
            paddingLeft = Math.max(cutoutPadding - leftMargin, 0);
        }
        if (mCutOutPaddingRight > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingRight, mRoundedCornerPadding);
            paddingRight = Math.max(cutoutPadding - rightMargin, 0);
        }

        mDatePrivacyView.setPadding(paddingLeft,
                mWaterfallTopInset,
                paddingRight,
                0);
    }

    public void updateEverything() {
        post(() -> setClickable(!mExpanded));
    }

    public void setCallback(Callback qsPanelCallback) {
        mHeaderQsPanel.setCallback(qsPanelCallback);
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }

    /**
     * Scroll the headers away.
     *
     * @param scrollY the scroll of the QSPanel container
     */
    public void setExpandedScrollAmount(int scrollY) {
        mClockIconsView.setScrollY(scrollY);
        mDatePrivacyView.setScrollY(scrollY);
    }
}
