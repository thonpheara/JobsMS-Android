package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.AvatarLoader;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.CompanyRepository;
import com.example.internshipms.session.SessionManager;
import com.example.internshipms.view.PullRefreshScrollView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CompanyInterviewsActivity extends AppCompatActivity {

    public static void launch(Context context) {
        context.startActivity(new Intent(context, CompanyInterviewsActivity.class));
    }

    private CompanyRepository repo;
    private PullRefreshScrollView scrollRefresh;
    private LinearLayout containerInterviews;
    private LinearLayout loadingContainer;
    private LinearLayout emptyContainer;
    private EditText etSearchInterviews;

    private TextView tvStatTotal;
    private TextView tvStatToday;
    private TextView tvStatUpcoming;
    private TextView tvStatReschedule;

    private MaterialCardView tabAll, tabScheduled, tabCompleted, tabCancelled, tabRescheduled;
    private TextView tvTabAll, tvTabScheduled, tvTabCompleted, tvTabCancelled, tvTabRescheduled;

    private final List<JsonObject> allInterviews = new ArrayList<>();
    private String selectedTab = null; // null = all, "scheduled", "completed", "cancelled", "rescheduled"
    private String searchQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_company_interviews);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repo = new CompanyRepository(this);

        scrollRefresh = findViewById(R.id.scrollRefresh);
        containerInterviews = findViewById(R.id.containerInterviews);
        loadingContainer = findViewById(R.id.loadingContainer);
        emptyContainer = findViewById(R.id.emptyContainer);
        etSearchInterviews = findViewById(R.id.etSearchInterviews);

        tvStatTotal = findViewById(R.id.tvStatTotal);
        tvStatToday = findViewById(R.id.tvStatToday);
        tvStatUpcoming = findViewById(R.id.tvStatUpcoming);
        tvStatReschedule = findViewById(R.id.tvStatReschedule);

        tabAll = findViewById(R.id.tabAll);
        tabScheduled = findViewById(R.id.tabScheduled);
        tabCompleted = findViewById(R.id.tabCompleted);
        tabCancelled = findViewById(R.id.tabCancelled);
        tabRescheduled = findViewById(R.id.tabRescheduled);

        tvTabAll = findViewById(R.id.tvTabAll);
        tvTabScheduled = findViewById(R.id.tvTabScheduled);
        tvTabCompleted = findViewById(R.id.tvTabCompleted);
        tvTabCancelled = findViewById(R.id.tvTabCancelled);
        tvTabRescheduled = findViewById(R.id.tvTabRescheduled);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNotifications).setOnClickListener(v -> NotificationsActivity.launch(this));

        tabAll.setOnClickListener(v -> selectTab(null));
        tabScheduled.setOnClickListener(v -> selectTab("scheduled"));
        tabCompleted.setOnClickListener(v -> selectTab("completed"));
        tabCancelled.setOnClickListener(v -> selectTab("cancelled"));
        tabRescheduled.setOnClickListener(v -> selectTab("rescheduled"));

        etSearchInterviews.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim();
                renderInterviews();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadInterviews);
        }

        loadInterviews();
    }

    private void selectTab(@Nullable String tab) {
        selectedTab = tab;
        styleTab(tabAll, tvTabAll, tab == null);
        styleTab(tabScheduled, tvTabScheduled, "scheduled".equals(tab));
        styleTab(tabCompleted, tvTabCompleted, "completed".equals(tab));
        styleTab(tabCancelled, tvTabCancelled, "cancelled".equals(tab));
        styleTab(tabRescheduled, tvTabRescheduled, "rescheduled".equals(tab));
        renderInterviews();
    }

    private void styleTab(MaterialCardView card, TextView tv, boolean selected) {
        int primary = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
        if (selected) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_green_bg));
            card.setStrokeColor(ColorStateList.valueOf(primary));
            tv.setTextColor(primary);
            tv.setTypeface(null, Typeface.BOLD);
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_card));
            card.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_soft)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            tv.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void loadInterviews() {
        if (loadingContainer != null) loadingContainer.setVisibility(View.VISIBLE);
        if (emptyContainer != null) emptyContainer.setVisibility(View.GONE);

        repo.listInterviews(null, null, null, null, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonArray arr = Json.list(body, "interviews", "data");
                allInterviews.clear();
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        allInterviews.add(el.getAsJsonObject());
                    }
                }
                updateKpis();
                renderInterviews();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                Toast.makeText(CompanyInterviewsActivity.this, error.message, Toast.LENGTH_SHORT).show();
                renderInterviews();
            }
        });
    }

    private void updateKpis() {
        int total = allInterviews.size();
        int todayCount = 0;
        int upcoming = 0;
        int reschedule = 0;

        String todayDateStr = LocalDate.now().toString();

        for (JsonObject iv : allInterviews) {
            String status = Json.str(iv, "scheduled", "status").toLowerCase(Locale.ROOT);
            String schedAt = Json.str(iv, "", "scheduled_at", "interview_date");
            if (schedAt.startsWith(todayDateStr)) {
                todayCount++;
            }
            if ("scheduled".equals(status)) {
                upcoming++;
            }
            if ("rescheduled".equals(status) || "reschedule_requested".equals(status)
                    || iv.has("reschedule_reason")) {
                reschedule++;
            }
        }

        tvStatTotal.setText(String.valueOf(total));
        tvStatToday.setText(String.valueOf(todayCount));
        tvStatUpcoming.setText(String.valueOf(upcoming));
        tvStatReschedule.setText(String.valueOf(reschedule));
    }

    private void renderInterviews() {
        containerInterviews.removeAllViews();
        List<JsonObject> filtered = new ArrayList<>();
        String q = searchQuery.toLowerCase(Locale.ROOT);

        for (JsonObject iv : allInterviews) {
            String status = Json.str(iv, "scheduled", "status").toLowerCase(Locale.ROOT);
            if (selectedTab != null) {
                if ("rescheduled".equals(selectedTab)) {
                    if (!"rescheduled".equals(status) && !"reschedule_requested".equals(status)) continue;
                } else if (!status.equals(selectedTab)) {
                    continue;
                }
            }

            JsonObject app = Json.obj(iv, "application");
            JsonObject student = app != null ? Json.obj(app, "student_profile", "student") : Json.obj(iv, "student_profile", "student");
            JsonObject user = student != null ? Json.obj(student, "user") : null;
            String name = user != null ? Json.str(user, "", "name") : Json.str(student, "", "name");
            JsonObject post = app != null ? Json.obj(app, "internship_post", "post") : Json.obj(iv, "internship_post", "post");
            String role = post != null ? Json.str(post, "", "title") : "";

            if (!q.isEmpty()) {
                if (!name.toLowerCase(Locale.ROOT).contains(q) && !role.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
            }
            filtered.add(iv);
        }

        if (filtered.isEmpty()) {
            if (emptyContainer != null) emptyContainer.setVisibility(View.VISIBLE);
        } else {
            if (emptyContainer != null) emptyContainer.setVisibility(View.GONE);
            for (JsonObject iv : filtered) {
                addInterviewCard(iv);
            }
        }
    }

    private void addInterviewCard(JsonObject iv) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_company_interview_card, containerInterviews, false);

        int interviewId = Json.integer(iv, 0, "id");
        JsonObject app = Json.obj(iv, "application");
        JsonObject student = app != null ? Json.obj(app, "student_profile", "student") : Json.obj(iv, "student_profile", "student");
        JsonObject user = student != null ? Json.obj(student, "user") : null;
        JsonObject post = app != null ? Json.obj(app, "internship_post", "post") : Json.obj(iv, "internship_post", "post");

        String name = user != null ? Json.str(user, null, "name") : null;
        if (name == null) name = Json.str(student, null, "full_name", "name");
        if (name == null) name = "Candidate";

        String role = post != null ? Json.str(post, "Internship Role", "title") : "Internship Role";
        String status = Json.str(iv, "scheduled", "status");
        String schedAt = Json.str(iv, "Scheduled", "scheduled_at", "interview_date");
        String mode = Json.str(iv, "online", "meeting_type", "type");
        String location = Json.str(iv, "meet.google.com/interview", "location", "meeting_link");

        TextView tvCandidateInitials = card.findViewById(R.id.tvCandidateInitials);
        ImageView ivCandidateAvatar = card.findViewById(R.id.ivCandidateAvatar);
        TextView tvCandidateName = card.findViewById(R.id.tvCandidateName);
        TextView tvPostTitle = card.findViewById(R.id.tvPostTitle);
        TextView tvInterviewStatusBadge = card.findViewById(R.id.tvInterviewStatusBadge);
        TextView tvInterviewDateTime = card.findViewById(R.id.tvInterviewDateTime);
        TextView tvConfirmationStatus = card.findViewById(R.id.tvConfirmationStatus);
        TextView tvMeetingMode = card.findViewById(R.id.tvMeetingMode);
        TextView tvMeetingLocation = card.findViewById(R.id.tvMeetingLocation);

        MaterialButton btnReschedule = card.findViewById(R.id.btnReschedule);
        MaterialButton btnComplete = card.findViewById(R.id.btnComplete);
        MaterialButton btnCancel = card.findViewById(R.id.btnCancelInterview);

        tvCandidateName.setText(name);
        tvCandidateInitials.setText(initials(name));
        AvatarLoader.bind(AvatarLoader.extractUrl(user, student), ivCandidateAvatar, tvCandidateInitials);

        tvPostTitle.setText(role);
        tvInterviewDateTime.setText(schedAt);
        tvMeetingMode.setText("online".equalsIgnoreCase(mode) ? "Online Video" : "In-Person");
        tvMeetingLocation.setText(location);

        boolean confirmed = iv.has("confirmed_by_student") && !iv.get("confirmed_by_student").isJsonNull() && iv.get("confirmed_by_student").getAsBoolean();
        tvConfirmationStatus.setText(confirmed ? "Confirmed ✓" : "Pending reply");
        tvConfirmationStatus.setTextColor(confirmed ? ContextCompat.getColor(this, R.color.tint_green) : ContextCompat.getColor(this, R.color.tint_amber));

        applyStatusBadge(tvInterviewStatusBadge, status);

        btnReschedule.setOnClickListener(v -> showRescheduleDialog(interviewId, schedAt));
        btnComplete.setOnClickListener(v -> updateStatus(interviewId, "completed"));
        btnCancel.setOnClickListener(v -> updateStatus(interviewId, "cancelled"));

        containerInterviews.addView(card);
    }

    private void applyStatusBadge(TextView tv, String status) {
        String s = status == null ? "scheduled" : status.toLowerCase(Locale.ROOT);
        tv.setText(s.toUpperCase(Locale.ROOT));
        if ("completed".equals(s)) {
            tv.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_green_bg)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.tint_green));
        } else if ("cancelled".equals(s)) {
            tv.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_red_bg)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
        } else if ("rescheduled".equals(s) || "reschedule_requested".equals(s)) {
            tv.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_amber_bg)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
        } else {
            tv.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_indigo_bg)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
        }
    }

    private void showRescheduleDialog(int interviewId, String currentDateTime) {
        final EditText input = new EditText(this);
        input.setHint("YYYY-MM-DD HH:MM (e.g. 2026-11-28 14:00)");
        input.setText(currentDateTime);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Reschedule Interview")
                .setMessage("Enter the new proposed interview date & time:")
                .setView(input)
                .setPositiveButton("Save & Notify", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        JsonObject payload = new JsonObject();
                        payload.addProperty("scheduled_at", val);
                        payload.addProperty("status", "rescheduled");
                        repo.updateInterview(interviewId, payload, new CompanyRepository.JsonResult() {
                            @Override
                            public void onSuccess(@NonNull JsonObject body) {
                                Toast.makeText(CompanyInterviewsActivity.this, "Interview rescheduled!", Toast.LENGTH_SHORT).show();
                                loadInterviews();
                            }

                            @Override
                            public void onError(@NonNull ApiError error) {
                                Toast.makeText(CompanyInterviewsActivity.this, error.message, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStatus(int interviewId, String targetStatus) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", targetStatus);
        repo.updateInterviewStatus(interviewId, payload, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                Toast.makeText(CompanyInterviewsActivity.this, "Interview marked as " + targetStatus, Toast.LENGTH_SHORT).show();
                loadInterviews();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                Toast.makeText(CompanyInterviewsActivity.this, error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String initials(String name) {
        if (name == null || name.isEmpty()) return "ST";
        String[] parts = name.trim().split("\\s+");
        if (parts.length > 1) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.ROOT);
    }
}
