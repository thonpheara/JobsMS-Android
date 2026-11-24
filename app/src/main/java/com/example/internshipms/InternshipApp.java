package com.example.internshipms;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/** Applies Material You dynamic colors on Android 12+; falls back to the static M3 scheme. */
public class InternshipApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}

