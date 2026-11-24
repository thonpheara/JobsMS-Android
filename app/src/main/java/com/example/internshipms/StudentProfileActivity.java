package com.example.internshipms;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.StudentRepository;
import com.example.internshipms.session.SessionManager;
import com.example.internshipms.view.PullRefreshScrollView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

import retrofit2.Call;

public class StudentProfileActivity extends AppCompatActivity {

    private StudentRepository repository;
    private Call<JsonObject> profileCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repository = new StudentRepository(this);

        String sessionName = session.getName();
        if (sessionName != null && !sessionName.isEmpty()) {
            ((TextView) findViewById(R.id.tvHeaderName)).setText(sessionName);
        }

        View.OnClickListener openEditor = v ->
                startActivity(new Intent(this, EditStudentProfileActivity.class));
        View.OnClickListener openResume = v ->
                startActivity(new Intent(this, StudentResumeActivity.class));

        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));
        findViewById(R.id.btnBottomLogout).setOnClickListener(v -> Navigator.confirmLogout(this));
        findViewById(R.id.btnChangeAvatar).setOnClickListener(openEditor);
        findViewById(R.id.btnEditProfile).setOnClickListener(openEditor);
        findViewById(R.id.cardResume).setOnClickListener(openResume);
        findViewById(R.id.cardTranscript).setOnClickListener(openResume);
        findViewById(R.id.btnDownloadTranscript).setOnClickListener(openResume);
        findViewById(R.id.btnChangePassword).setOnClickListener(v ->
                ChangePasswordBottomSheet.show(getSupportFragmentManager()));

        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(() -> {
                fetchLiveProfile();
                loadUnreadDot();
            });
        }

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_profile);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Navigator.switchTab(this, StudentDashboardActivity.class, true);
                return true;
            }
            if (id == R.id.nav_explore) {
                Navigator.switchTab(this, StudentExploreActivity.class, true);
                return true;
            }
            if (id == R.id.nav_applications) {
                Navigator.switchTab(this, StudentApplicationsActivity.class, true);
                return true;
            }
            if (id == R.id.nav_messages) {
                Navigator.switchTab(this, ConversationsActivity.class, true);
                return true;
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfileData();
        fetchLiveProfile();
        loadUnreadDot();
    }

    private void loadUnreadDot() {
        View dot = findViewById(R.id.dotUnread);
        if (dot == null || repository == null) return;
        repository.unreadNotificationCount(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject d = Json.data(body);
                int count = Json.integer(d, 0, "unread_count", "count", "unread");
                if (count == 0 && body.has("unread_count")) {
                    count = Json.integer(body, 0, "unread_count");
                }
                dot.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                dot.setVisibility(View.GONE);
            }
        });
    }

    private void fetchLiveProfile() {
        if (profileCall != null) profileCall.cancel();
        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);
        profileCall = repository.profile(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonObject d = Json.data(body);
                JsonObject user = Json.obj(d, "user");
                JsonObject student = Json.obj(d, "student");
                if (student == null) student = d;

                SessionManager session = SessionManager.get(StudentProfileActivity.this);
                String name = Json.str(user, session.getName(), "name");
                String email = Json.str(user, session.getEmail(), "email");
                String avatarUrl = com.example.internshipms.network.AvatarLoader.extractUrl(user, student);
                if (name != null) session.setName(name);
                if (avatarUrl != null) session.setAvatarUrl(avatarUrl);
                String studentId = Json.str(student, "—", "student_id_number");
                String department = Json.str(student, "—", "department");
                String major = Json.str(student, "Student Intern", "major");
                String cohortYear = Json.str(student, "—", "cohort_year");
                String gpa = Json.str(student, "0.00", "gpa");
                String phone = Json.str(student, "—", "phone");
                String bio = Json.str(student, "—", "bio");
                String skillsCsv = extractSkillsCsv(student);

                getSharedPreferences(EditStudentProfileActivity.PREF_PROFILE, MODE_PRIVATE)
                        .edit()
                        .putString(EditStudentProfileActivity.KEY_FULL_NAME, name)
                        .putString(EditStudentProfileActivity.KEY_EMAIL, email)
                        .putString(EditStudentProfileActivity.KEY_HEADLINE, major)
                        .putString(EditStudentProfileActivity.KEY_UNIVERSITY, department)
                        .putString(EditStudentProfileActivity.KEY_GPA, gpa)
                        .putString(EditStudentProfileActivity.KEY_PHONE, phone)
                        .putString(EditStudentProfileActivity.KEY_LANGUAGES, skillsCsv)
                        .putString("student_id_number", studentId)
                        .putString("cohort_year", cohortYear)
                        .putString("bio", bio)
                        .apply();

                refreshProfileData();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                // Keep cached display
            }
        });
    }

    public static String extractSkillsCsv(@Nullable JsonObject student) {
        if (student == null || !student.has("skills") || student.get("skills").isJsonNull()) {
            return "";
        }
        JsonElement el = student.get("skills");
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (JsonElement item : arr) {
                if (!item.isJsonPrimitive()) continue;
                String s = item.getAsString().trim();
                if (s.isEmpty()) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(s);
            }
            return sb.toString();
        }
        return "";
    }

    private void refreshProfileData() {
        SharedPreferences prefs = getSharedPreferences(
                EditStudentProfileActivity.PREF_PROFILE, MODE_PRIVATE);
        SessionManager session = SessionManager.get(this);
        String defaultName = session.getName() != null && !session.getName().isEmpty()
                ? session.getName() : "Student";
        String defaultEmail = session.getEmail() != null && !session.getEmail().isEmpty()
                ? session.getEmail() : "—";

        String fullName = prefs.getString(EditStudentProfileActivity.KEY_FULL_NAME, defaultName);
        String major = prefs.getString(EditStudentProfileActivity.KEY_HEADLINE, "Student Intern");
        String department = prefs.getString(EditStudentProfileActivity.KEY_UNIVERSITY, "—");
        String gpa = prefs.getString(EditStudentProfileActivity.KEY_GPA, "0.00");
        String email = prefs.getString(EditStudentProfileActivity.KEY_EMAIL, defaultEmail);
        String phone = prefs.getString(EditStudentProfileActivity.KEY_PHONE, "—");
        String studentId = prefs.getString("student_id_number", "—");
        String cohortYear = prefs.getString("cohort_year", "—");
        String bio = prefs.getString("bio", "—");
        String skills = prefs.getString(EditStudentProfileActivity.KEY_LANGUAGES, "");

        ((TextView) findViewById(R.id.tvHeaderName)).setText(fullName);
        ((TextView) findViewById(R.id.tvAvatarInitials)).setText(initials(fullName));
        com.example.internshipms.network.AvatarLoader.bind(
                session.getAvatarUrl(),
                findViewById(R.id.ivAvatar),
                findViewById(R.id.tvAvatarInitials));
        ((TextView) findViewById(R.id.tvHeaderRole)).setText(major);
        ((TextView) findViewById(R.id.tvHeaderUniversity)).setText(
                "—".equals(cohortYear) ? department : department + " • Cohort " + cohortYear);
        ((TextView) findViewById(R.id.tvGpaScore)).setText(gpa);

        LinearProgressIndicator progressGpa = findViewById(R.id.progressGpa);
        if (progressGpa != null) {
            try {
                float gpaVal = Float.parseFloat(gpa);
                int pct = Math.max(0, Math.min(100, Math.round((gpaVal / 4.0f) * 100f)));
                progressGpa.setProgress(pct);
            } catch (NumberFormatException ignored) {
                progressGpa.setProgress(0);
            }
        }

        ViewGroup container = findViewById(R.id.containerPersonalFields);
        container.removeAllViews();
        addPersonalField(R.drawable.ic_profile, "Full Name", fullName, false, false, null);
        addPersonalField(R.drawable.ic_logo_cap, "Student ID Number", studentId, false, false, null);
        addPersonalField(R.drawable.ic_work, "Department", department, false, false, null);
        addPersonalField(R.drawable.ic_logo_cap, "Major", major, false, false, null);
        addPersonalField(R.drawable.ic_event, "Cohort Year", cohortYear, false, false, null);
        addPersonalField(R.drawable.ic_mail, "Email", email, true, false,
                v -> openSafeUri("mailto:" + email, "Email: " + email));
        addPersonalField(R.drawable.ic_phone, "Phone Number", phone, false, false,
                v -> openSafeUri("tel:" + phone.replaceAll("\\s+", ""), "Dialing " + phone));
        addPersonalField(R.drawable.ic_applications, "Bio", bio, false, true, null);

        populateSkillChips(findViewById(R.id.chipGroupLanguages),
                skills.isEmpty() ? new String[]{"No skills listed yet"} : skills.split(","));
        populateSkillChips(findViewById(R.id.chipGroupFrameworks), new String[0]);
    }

    private void addPersonalField(@DrawableRes int iconRes, String label, String value,
                                  boolean isLink, boolean isLast,
                                  @Nullable View.OnClickListener clickListener) {
        ViewGroup container = findViewById(R.id.containerPersonalFields);
        View row = LayoutInflater.from(this).inflate(R.layout.item_profile_field, container, false);
        ((ImageView) row.findViewById(R.id.ivFieldIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.tvFieldLabel)).setText(label);
        TextView tvValue = row.findViewById(R.id.tvFieldValue);
        tvValue.setText(value);
        if (isLink) {
            tvValue.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
        }
        View divider = row.findViewById(R.id.fieldDivider);
        if (divider != null) {
            divider.setVisibility(isLast ? View.GONE : View.VISIBLE);
        }
        if (clickListener != null) {
            row.setOnClickListener(clickListener);
        } else {
            row.setClickable(false);
            row.setFocusable(false);
        }
        container.addView(row);
    }

    private void openSafeUri(String uriString, String fallbackMsg) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
            startActivity(intent);
        } catch (Exception e) {
            toast(fallbackMsg);
        }
    }

    private void populateSkillChips(ChipGroup group, String[] labels) {
        group.removeAllViews();
        int greenBg = ContextCompat.getColor(this, R.color.tint_green_bg);
        int greenText = MaterialColors.getColor(group, androidx.appcompat.R.attr.colorPrimary);
        float radiusPx = 16f * getResources().getDisplayMetrics().density;
        for (String raw : labels) {
            String label = raw.trim();
            if (label.isEmpty()) continue;
            Chip chip = new Chip(this);
            chip.setText("✦ " + label);
            chip.setTextSize(11.5f);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setChipBackgroundColor(ColorStateList.valueOf(greenBg));
            chip.setTextColor(greenText);
            chip.setChipCornerRadius(radiusPx);
            chip.setChipStrokeWidth(0f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setClickable(false);
            group.addView(chip);
        }
    }

    private static String initials(String text) {
        String[] words = text.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "S";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (profileCall != null) profileCall.cancel();
        super.onDestroy();
    }
}
