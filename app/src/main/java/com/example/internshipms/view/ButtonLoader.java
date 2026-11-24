package com.example.internshipms.view;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shows an inline spinner + loading label inside a MaterialButton
 * (e.g. "◌ Signing in...") and restores the original state afterwards.
 */
public final class ButtonLoader {

    private static final class State {
        CharSequence text;
        Drawable icon;
        int iconGravity;
        int iconPadding;
        int iconSize;
        ColorStateList iconTint;
    }

    private static final Map<MaterialButton, State> STATES = new WeakHashMap<>();

    private ButtonLoader() {
    }

    /** Replaces the button label with {@code loadingText} and shows a small spinner before it. */
    public static void start(@NonNull MaterialButton btn, @NonNull CharSequence loadingText) {
        if (STATES.containsKey(btn)) return;

        State s = new State();
        s.text = btn.getText();
        s.icon = btn.getIcon();
        s.iconGravity = btn.getIconGravity();
        s.iconPadding = btn.getIconPadding();
        s.iconSize = btn.getIconSize();
        s.iconTint = btn.getIconTint();
        STATES.put(btn, s);

        Context ctx = btn.getContext();
        int textColor = btn.getCurrentTextColor();

        CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(ctx, null);
        spec.indicatorSize = dp(ctx, 18);
        spec.trackThickness = dp(ctx, 2);
        spec.indicatorInset = 0;
        spec.indicatorColors = new int[]{textColor};

        IndeterminateDrawable<CircularProgressIndicatorSpec> spinner =
                IndeterminateDrawable.createCircularDrawable(ctx, spec);

        btn.setIcon(spinner);
        btn.setIconTint(ColorStateList.valueOf(textColor));
        btn.setIconSize(dp(ctx, 18));
        btn.setIconPadding(dp(ctx, 10));
        btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btn.setText(loadingText);
        // Keep the button visually "enabled" (brand color) but ignore extra taps
        btn.setClickable(false);
    }

    /** Restores the original label/icon and makes the button clickable again. */
    public static void stop(@NonNull MaterialButton btn) {
        State s = STATES.remove(btn);
        if (s == null) return;
        btn.setText(s.text);
        btn.setIcon(s.icon);
        btn.setIconTint(s.iconTint);
        btn.setIconSize(s.iconSize);
        btn.setIconPadding(s.iconPadding);
        btn.setIconGravity(s.iconGravity);
        btn.setClickable(true);
    }

    /** Small horizontal shake used to highlight an invalid field. */
    public static void shake(@Nullable View view) {
        if (view == null) return;
        float d = 8f * view.getResources().getDisplayMetrics().density;
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
                0f, d, -d, d * 0.6f, -d * 0.6f, d * 0.3f, -d * 0.3f, 0f);
        anim.setDuration(420);
        anim.start();
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}

