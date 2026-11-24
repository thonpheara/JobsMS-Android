package com.example.internshipms.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ScrollView with a Material Circular Pull-Down Spinner Disc (Gmail / Telegram style).
 * Draws a floating circular card at top-center with a rotating emerald arc spinner.
 */
public class PullRefreshScrollView extends ScrollView {

    public interface OnRefreshListener {
        void onRefresh();
    }

    private final int touchSlop;
    private final float density;
    private final float triggerDistancePx;
    private final float maxPullDistancePx;
    private final float restOffsetPx;
    private final float discRadiusPx;
    private final float arcRadiusPx;

    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float startY = -1f;
    private float currentPullPx = 0f;
    private float discScale = 1f;
    private float spinAngle = 0f;
    private boolean isDragging = false;
    private boolean isRefreshing = false;
    private boolean thresholdHapticFired = false;

    private ValueAnimator spinAnimator;
    private ValueAnimator settleAnimator;
    private final Runnable autoHideRunnable = () -> setRefreshing(false);
    private OnRefreshListener onRefreshListener;

    public PullRefreshScrollView(@NonNull Context context) {
        this(context, null);
    }

    public PullRefreshScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PullRefreshScrollView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        density = context.getResources().getDisplayMetrics().density;
        triggerDistancePx = 64f * density;
        maxPullDistancePx = 128f * density;
        restOffsetPx = 52f * density;
        discRadiusPx = 20f * density;
        arcRadiusPx = 10.5f * density;

        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);

        discPaint.setStyle(Paint.Style.FILL);
        discPaint.setColor(Color.WHITE);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(26, 15, 23, 42));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f * density);
        borderPaint.setColor(Color.parseColor("#E2E8F0"));

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(2.6f * density);
        trackPaint.setColor(Color.parseColor("#D1FAE5"));

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStrokeWidth(2.8f * density);
        arcPaint.setColor(Color.parseColor("#059669"));
    }

    public void setOnRefreshListener(@Nullable OnRefreshListener listener) {
        this.onRefreshListener = listener;
    }

    /** Shows or hides the circular spinner disc programmatically. */
    public void setRefreshing(boolean refreshing) {
        removeCallbacks(autoHideRunnable);
        if (refreshing) {
            isRefreshing = true;
            discScale = 1f;
            animatePullTo(restOffsetPx, 180, null);
            startSpinLoop();
        } else {
            if (!isRefreshing && currentPullPx <= 0f) return;
            isRefreshing = false;
            ValueAnimator hide = ValueAnimator.ofFloat(1f, 0f);
            hide.setDuration(200);
            hide.setInterpolator(new DecelerateInterpolator());
            hide.addUpdateListener(a -> {
                discScale = (float) a.getAnimatedValue();
                View content = getChildCount() > 0 ? getChildAt(0) : null;
                if (content != null) {
                    content.setTranslationY(currentPullPx * discScale);
                }
                invalidate();
            });
            hide.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    currentPullPx = 0f;
                    discScale = 1f;
                    View content = getChildCount() > 0 ? getChildAt(0) : null;
                    if (content != null) content.setTranslationY(0f);
                    stopSpinLoop();
                    invalidate();
                }
            });
            hide.start();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (onRefreshListener == null || isRefreshing) {
            return super.onInterceptTouchEvent(ev);
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startY = ev.getY();
            isDragging = false;
            thresholdHapticFired = false;
        } else if (action == MotionEvent.ACTION_MOVE && getScrollY() == 0 && startY >= 0f) {
            float dy = ev.getY() - startY;
            if (dy > touchSlop) {
                isDragging = true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (onRefreshListener == null || isRefreshing) {
            return super.onTouchEvent(ev);
        }
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startY = ev.getY();
                isDragging = false;
                thresholdHapticFired = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (getScrollY() == 0) {
                    if (startY < 0f) startY = ev.getY();
                    float dy = ev.getY() - startY;
                    if (dy > touchSlop) {
                        isDragging = true;
                        discScale = 1f;
                        currentPullPx = Math.min(maxPullDistancePx, (dy - touchSlop) * 0.48f);
                        View content = getChildCount() > 0 ? getChildAt(0) : null;
                        if (content != null) {
                            content.setTranslationY(currentPullPx * 0.55f);
                        }
                        if (currentPullPx >= triggerDistancePx && !thresholdHapticFired) {
                            thresholdHapticFired = true;
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                        invalidate();
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    boolean triggered = currentPullPx >= triggerDistancePx && action == MotionEvent.ACTION_UP;
                    isDragging = false;
                    startY = -1f;
                    if (triggered) {
                        isRefreshing = true;
                        startSpinLoop();
                        animatePullTo(restOffsetPx, 200, () -> {
                            if (onRefreshListener != null) {
                                onRefreshListener.onRefresh();
                            }
                            postDelayed(autoHideRunnable, 750);
                        });
                    } else {
                        animatePullTo(0f, 220, null);
                    }
                    return true;
                }
                startY = -1f;
                break;
        }
        return super.onTouchEvent(ev);
    }

    private void animatePullTo(float targetPull, long duration, @Nullable Runnable onEnd) {
        if (settleAnimator != null) settleAnimator.cancel();
        settleAnimator = ValueAnimator.ofFloat(currentPullPx, targetPull);
        settleAnimator.setDuration(duration);
        settleAnimator.setInterpolator(new DecelerateInterpolator());
        settleAnimator.addUpdateListener(a -> {
            currentPullPx = (float) a.getAnimatedValue();
            View content = getChildCount() > 0 ? getChildAt(0) : null;
            if (content != null) {
                content.setTranslationY(currentPullPx * 0.55f);
            }
            invalidate();
        });
        if (onEnd != null) {
            settleAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
        }
        settleAnimator.start();
    }

    private void startSpinLoop() {
        if (spinAnimator != null && spinAnimator.isRunning()) return;
        spinAnimator = ValueAnimator.ofFloat(0f, 360f);
        spinAnimator.setDuration(720);
        spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        spinAnimator.setInterpolator(new LinearInterpolator());
        spinAnimator.addUpdateListener(a -> {
            spinAngle = (float) a.getAnimatedValue();
            invalidate();
        });
        spinAnimator.start();
    }

    private void stopSpinLoop() {
        if (spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (currentPullPx <= 2f && !isRefreshing) return;

        float progress = Math.min(1f, currentPullPx / triggerDistancePx);
        float scale = isRefreshing ? discScale : (0.55f + 0.45f * progress);
        float cx = getWidth() / 2f;
        float cy = getScrollY() + Math.min(restOffsetPx, currentPullPx * 0.78f) - (1f - progress) * 14f * density;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        // Soft drop shadow + circular white pill disc + border
        canvas.drawCircle(0f, 2.5f * density, discRadiusPx + 1.5f * density, shadowPaint);
        canvas.drawCircle(0f, 0f, discRadiusPx, discPaint);
        canvas.drawCircle(0f, 0f, discRadiusPx, borderPaint);

        // Inner circular track + emerald spinner arc
        arcBounds.set(-arcRadiusPx, -arcRadiusPx, arcRadiusPx, arcRadiusPx);
        canvas.drawOval(arcBounds, trackPaint);

        float startAngle = isRefreshing ? spinAngle : (-90f + progress * 210f);
        float sweepAngle = isRefreshing ? 250f : Math.max(28f, progress * 270f);
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, arcPaint);

        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(autoHideRunnable);
        stopSpinLoop();
        if (settleAnimator != null) settleAnimator.cancel();
        super.onDetachedFromWindow();
    }
}
