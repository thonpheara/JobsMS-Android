package com.example.internshipms;

import android.app.Activity;
import android.content.Intent;

import com.example.internshipms.session.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Role-based navigation helpers shared by the auth and dashboard screens. */
public final class Navigator {
    private Navigator() {
    }

    public static void openDashboard(Activity from, String role) {
        Class<? extends Activity> target = "company".equalsIgnoreCase(role)
                ? CompanyDashboardActivity.class : StudentDashboardActivity.class;
        Intent intent = new Intent(from, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(intent);
        from.finish();
    }

    public static void openLogin(Activity from) {
        Intent intent = new Intent(from, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(intent);
        from.finish();
    }

    /** Switches bottom-navigation tabs instantaneously without window slide animation. */
    public static void switchTab(Activity from, Class<? extends Activity> target, boolean finishCurrent) {
        Intent intent = new Intent(from, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        from.startActivity(intent);
        from.overridePendingTransition(0, 0);
        if (finishCurrent) {
            from.finish();
            from.overridePendingTransition(0, 0);
        }
    }

    public static void confirmLogout(Activity from) {
        new MaterialAlertDialogBuilder(from)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.logout, (d, w) -> {
                    new com.example.internshipms.repository.StudentRepository(from)
                            .logout(new com.example.internshipms.repository.StudentRepository.JsonResult() {
                                @Override
                                public void onSuccess(@androidx.annotation.NonNull com.google.gson.JsonObject body) {
                                }

                                @Override
                                public void onError(@androidx.annotation.NonNull com.example.internshipms.network.ApiError error) {
                                }
                            });
                    com.example.internshipms.network.AvatarLoader.invalidateCache();
                    SessionManager.get(from).logout();
                    openLogin(from);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
