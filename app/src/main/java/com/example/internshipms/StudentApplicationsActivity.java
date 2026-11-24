package com.example.internshipms;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.internshipms.repository.StudentRepository;
import com.example.internshipms.session.SessionManager;
import com.example.internshipms.view.PullRefreshScrollView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;

public class StudentApplicationsActivity extends AppCompatActivity {

    private static final String KEY_ALL = "all";
    private static final String KEY_PENDING = "pending";
    private static final String KEY_SHORTLISTED = "shortlisted";
    private static final String KEY_ACCEPTED = "accepted";
    private static final String KEY_REJECTED = "rejected";

    private StudentRepository repository;
    private Call<JsonObject> appsCall;
    private final List<JsonObject> allApps = new ArrayList<>();
    private String selectedKey = KEY_ALL;
    private ChipGroup chipGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.get(this).isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_applications);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repository = new StudentRepository(this);
        chipGroup = findViewById(R.id.chipGroup);

        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));
        findViewById(R.id.btnTopProfile).setOnClickListener(v ->
                Navigator.switchTab(this, StudentProfileActivity.class, true));

        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadApplications);
        }

        rebuildFilterChips();
        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof String) {
                selectedKey = (String) chip.getTag();
                render();
            }
        });

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_applications);
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
            if (id == R.id.nav_messages) {
                Navigator.switchTab(this, ConversationsActivity.class, true);
                return true;
            }
            if (id == R.id.nav_profile) {
                Navigator.switchTab(this, StudentProfileActivity.class, true);
                return true;
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SessionManager session = SessionManager.get(this);
        AvatarLoader.bind(session.getAvatarUrl(),
                findViewById(R.id.ivTopProfileAvatar),
                findViewById(R.id.ivTopProfileIcon));
        loadApplications();
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

    private void loadApplications() {
        if (appsCall != null) appsCall.cancel();
        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);
        appsCall = repository.applications(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                allApps.clear();
                JsonArray arr = Json.list(body, "applications", "data");
                for (JsonElement e : arr) {
                    if (e.isJsonObject()) allApps.add(e.getAsJsonObject());
                }
                rebuildFilterChips();
                render();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                Snackbar.make(findViewById(R.id.main), error.message, Snackbar.LENGTH_LONG)
                        .setAction("Retry", v -> loadApplications())
                        .show();
            }
        });

        repository.conversations(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonArray arr = Json.list(body, "conversations", "data");
                if (AvatarLoader.syncCompanyLogosFromConversations(arr) && !allApps.isEmpty()) {
                    render();
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    private static boolean isInterviewOrShortlisted(String s, @Nullable JsonObject app) {
        if ("shortlisted".equals(s) || "interview".equals(s)
                || "interviewed".equals(s) || "interviewing".equals(s)
                || "interview_scheduled".equals(s)) {
            return true;
        }
        return app != null && extractInterview(app) != null
                && !"accepted".equals(s) && !"approved".equals(s)
                && !"rejected".equals(s) && !"withdrawn".equals(s);
    }

    private void rebuildFilterChips() {
        int total = allApps.size(), pending = 0, shortlisted = 0, accepted = 0, rejected = 0;
        for (JsonObject app : allApps) {
            String s = Json.str(app, "pending", "status").toLowerCase(Locale.ROOT);
            if (isInterviewOrShortlisted(s, app)) shortlisted++;
            else if ("accepted".equals(s) || "approved".equals(s)) accepted++;
            else if ("rejected".equals(s) || "withdrawn".equals(s)) rejected++;
            else pending++;
        }

        String[][] defs = {
                {KEY_ALL, "All (" + total + ")"},
                {KEY_PENDING, "Pending (" + pending + ")"},
                {KEY_SHORTLISTED, "Shortlisted / Interview (" + shortlisted + ")"},
                {KEY_ACCEPTED, "Accepted (" + accepted + ")"},
                {KEY_REJECTED, "Rejected (" + rejected + ")"}
        };

        chipGroup.removeAllViews();
        for (String[] def : defs) {
            Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.item_filter_chip, chipGroup, false);
            chip.setId(View.generateViewId());
            chip.setTag(def[0]);
            chip.setText(def[1]);
            chip.setChecked(def[0].equals(selectedKey));
            chipGroup.addView(chip);
        }
    }

    private void render() {
        ViewGroup container = findViewById(R.id.containerApplications);
        container.removeAllViews();

        int shown = 0;
        for (int i = 0; i < allApps.size(); i++) {
            JsonObject app = allApps.get(i);
            String status = Json.str(app, "pending", "status").toLowerCase(Locale.ROOT);
            if (matchesFilter(status, app)) {
                container.addView(buildCard(app, i, container));
                shown++;
            }
        }
        findViewById(R.id.tvEmpty).setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private boolean matchesFilter(String status, JsonObject app) {
        switch (selectedKey) {
            case KEY_PENDING:
                return !isInterviewOrShortlisted(status, app)
                        && !"accepted".equals(status) && !"approved".equals(status)
                        && !"rejected".equals(status) && !"withdrawn".equals(status);
            case KEY_SHORTLISTED:
                return isInterviewOrShortlisted(status, app);
            case KEY_ACCEPTED:
                return "accepted".equals(status) || "approved".equals(status);
            case KEY_REJECTED:
                return "rejected".equals(status) || "withdrawn".equals(status);
            default:
                return true;
        }
    }

    @Nullable
    public static JsonObject extractInterview(@Nullable JsonObject app) {
        if (app == null) return null;
        JsonObject interview = Json.obj(app, "interview", "latest_interview", "latestInterview");
        if (interview != null) return interview;
        JsonArray arr = Json.arr(app, "interviews");
        JsonObject latest = null;
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                latest = el.getAsJsonObject();
            }
        }
        return latest;
    }

    private View buildCard(JsonObject app, int index, ViewGroup parent) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_application, parent, false);

        int appId = Json.integer(app, 0, "id");
        JsonObject post = Json.obj(app, "internship_post", "post");
        int compId = StudentDashboardActivity.companyId(post, app);
        String title = Json.str(post, Json.str(app, "Internship Position", "title"), "title");
        String company = StudentDashboardActivity.companyName(post, app);
        String location = Json.str(post, "Cambodia", "location");
        String status = Json.str(app, "pending", "status").toLowerCase(Locale.ROOT);
        JsonObject interview = extractInterview(app);
        boolean hasInterview = interview != null
                || "interview".equals(status)
                || "interviewed".equals(status)
                || "interviewing".equals(status)
                || "interview_scheduled".equals(status);

        String effectiveStatus = status;
        if (interview != null && ("pending".equals(status) || "reviewed".equals(status)
                || "under_review".equals(status) || "shortlisted".equals(status))) {
            effectiveStatus = "interviewed";
        }
        String label = StudentDashboardActivity.statusLabel(effectiveStatus);

        String appliedRaw = Json.str(app, null, "applied_at", "created_at");
        String appliedPretty = "Applied " + StudentDashboardActivity.formatDeadlineDate(appliedRaw)
                + " • " + StudentDashboardActivity.relativeTime(appliedRaw);

        // Resume badge
        String resumePath = Json.str(app, null, "custom_resume_path", "resume_path");
        TextView tvCvBadge = card.findViewById(R.id.tvCvBadge);
        if (resumePath != null && !resumePath.isEmpty()) {
            int slash = resumePath.lastIndexOf('/');
            String name = slash >= 0 ? resumePath.substring(slash + 1) : resumePath;
            // Strip numeric timestamp prefix if present (e.g. 176234_CV.pdf -> CV.pdf)
            name = name.replaceFirst("^\\d+_", "");
            tvCvBadge.setText("📄 " + name);
        } else {
            tvCvBadge.setText("📄 Resume.pdf");
        }
        tvCvBadge.setOnClickListener(v ->
                startActivity(new Intent(this, StudentResumeActivity.class)));

        // Company Logo
        int[] bgColors = {
                Color.parseColor("#059669"),
                Color.parseColor("#312E81"),
                Color.parseColor("#16A34A")
        };
        int[] icons = {R.drawable.ic_code, R.drawable.ic_work, R.drawable.ic_chart};

        MaterialCardView logoBox = card.findViewById(R.id.logoBox);
        logoBox.setCardBackgroundColor(bgColors[index % bgColors.length]);
        ImageView ivLogo = card.findViewById(R.id.ivLogo);
        ImageView ivCompanyLogo = card.findViewById(R.id.ivCompanyLogo);
        ivLogo.setImageResource(icons[index % icons.length]);
        String logoUrl = AvatarLoader.extractCompanyLogo(post, app, company);
        AvatarLoader.bind(logoUrl, ivCompanyLogo, ivLogo);
        if (compId > 0) {
            logoBox.setOnClickListener(v ->
                    CompanyProfileBottomSheet.show(getSupportFragmentManager(), compId, company, location));
        }

        ((TextView) card.findViewById(R.id.tvTitle)).setText(title);
        ((TextView) card.findViewById(R.id.tvCompany)).setText(company + " • " + location);
        ((TextView) card.findViewById(R.id.tvAppliedDate)).setText(appliedPretty);

        // Status Pill
        TextView tvStatus = card.findViewById(R.id.tvStatusPill);
        int primaryGreen = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
        tvStatus.setText("• " + label);
        if ("accepted".equals(effectiveStatus) || "approved".equals(effectiveStatus)) {
            tvStatus.setText("Accepted 🎉");
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_green_bg)));
            tvStatus.setTextColor(primaryGreen);
        } else if ("rejected".equals(effectiveStatus) || "withdrawn".equals(effectiveStatus)) {
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_red_bg)));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
        } else if (isInterviewOrShortlisted(effectiveStatus, app)) {
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_indigo_bg)));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
        } else {
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_amber_bg)));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
        }

        // 2. Bind 5-Step Progress Stepper
        bindStepper(card, effectiveStatus, primaryGreen);

        // 3. Bind INTERVIEW INVITATION Box (when interview exists or status is interview/interviewed)
        bindInterviewBox(card, app, interview, hasInterview, location, appId, title, company, label, primaryGreen);

        // 4. Bind RECRUITER FEEDBACK Box
        String feedback = Json.str(app, null,
                "company_notes", "recruiter_feedback", "feedback", "reviewer_notes", "notes");
        if (feedback == null || feedback.isEmpty()) {
            feedback = defaultFeedbackForStatus(effectiveStatus);
        }
        ((TextView) card.findViewById(R.id.tvUpdateTitle)).setText("RECRUITER FEEDBACK");
        ((TextView) card.findViewById(R.id.tvUpdateBody)).setText(feedback);

        // 5. Bind Bottom Action Row
        View btnWithdraw = card.findViewById(R.id.btnWithdraw);
        if ("pending".equals(status) || "applied".equals(status)) {
            btnWithdraw.setVisibility(View.VISIBLE);
            btnWithdraw.setOnClickListener(v ->
                    confirmWithdrawApplication(appId, title, company));
        } else {
            btnWithdraw.setVisibility(View.GONE);
        }

        card.findViewById(R.id.btnViewDetails).setOnClickListener(v ->
                ApplicationDetailActivity.launch(this, appId, title, company, label));
        card.findViewById(R.id.btnChat).setOnClickListener(v ->
                ChatActivity.launchFromApplication(this, appId, company, title));
        card.findViewById(R.id.headerRow).setOnClickListener(v ->
                ApplicationDetailActivity.launch(this, appId, title, company, label));

        return card;
    }

    private void bindStepper(View card, String status, int primaryGreen) {
        int stepReached;
        boolean isRejectedOrWithdrawn = "rejected".equals(status) || "withdrawn".equals(status);
        switch (status) {
            case "accepted":
            case "approved":
                stepReached = 5;
                break;
            case "interview":
            case "interviewed":
            case "interviewing":
            case "interview_scheduled":
                stepReached = 4;
                break;
            case "shortlisted":
                stepReached = 3;
                break;
            case "reviewed":
            case "reviewing":
            case "under_review":
                stepReached = 2;
                break;
            default:
                stepReached = 1;
                break;
        }

        int[] circleIds = {
                R.id.stepCircle1, R.id.stepCircle2, R.id.stepCircle3,
                R.id.stepCircle4, R.id.stepCircle5
        };
        int[] iconIds = {
                R.id.stepIcon1, R.id.stepIcon2, R.id.stepIcon3,
                R.id.stepIcon4, R.id.stepIcon5
        };
        int[] labelIds = {
                R.id.stepLabel1, R.id.stepLabel2, R.id.stepLabel3,
                R.id.stepLabel4, R.id.stepLabel5
        };
        int[] lineIds = {
                R.id.stepLine1, R.id.stepLine2, R.id.stepLine3, R.id.stepLine4
        };

        int pageBg = ContextCompat.getColor(this, R.color.bg_page);
        int strokeSoft = ContextCompat.getColor(this, R.color.stroke_soft);
        int mutedText = ContextCompat.getColor(this, R.color.nav_unselected);
        int onSurface = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface);

        for (int i = 0; i < 5; i++) {
            MaterialCardView circle = card.findViewById(circleIds[i]);
            ImageView icon = card.findViewById(iconIds[i]);
            TextView tvLabel = card.findViewById(labelIds[i]);
            int stepNum = i + 1;

            if (stepNum <= stepReached && !isRejectedOrWithdrawn) {
                circle.setCardBackgroundColor(primaryGreen);
                circle.setStrokeWidth(0);
                icon.setColorFilter(Color.WHITE);
                if (stepNum < stepReached || stepReached == 5) {
                    icon.setImageResource(R.drawable.ic_check);
                }
                tvLabel.setTextColor(stepNum == stepReached ? primaryGreen : onSurface);
                tvLabel.setTypeface(null, stepNum == stepReached
                        ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            } else if (isRejectedOrWithdrawn && stepNum == 5) {
                int red = ContextCompat.getColor(this, R.color.tint_red);
                circle.setCardBackgroundColor(red);
                circle.setStrokeWidth(0);
                icon.setImageResource(R.drawable.ic_cancel);
                icon.setColorFilter(Color.WHITE);
                tvLabel.setText("withdrawn".equals(status) ? "Withdrawn" : "Rejected");
                tvLabel.setTextColor(red);
                tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (stepNum == 1) {
                circle.setCardBackgroundColor(primaryGreen);
                circle.setStrokeWidth(0);
                icon.setImageResource(R.drawable.ic_check);
                icon.setColorFilter(Color.WHITE);
                tvLabel.setTextColor(onSurface);
            } else {
                circle.setCardBackgroundColor(pageBg);
                circle.setStrokeColor(strokeSoft);
                circle.setStrokeWidth(Math.round(getResources().getDisplayMetrics().density));
                icon.setColorFilter(mutedText);
                tvLabel.setTextColor(mutedText);
                tvLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }

        for (int i = 0; i < 4; i++) {
            View line = card.findViewById(lineIds[i]);
            boolean done = !isRejectedOrWithdrawn && (i + 1) < stepReached;
            line.setBackgroundColor(done ? primaryGreen : strokeSoft);
        }
    }

    private void bindInterviewBox(View card, JsonObject app, @Nullable JsonObject interview,
                                  boolean hasInterview, String fallbackLocation,
                                  int appId, String title, String company, String statusLabel,
                                  int primaryGreen) {
        View box = card.findViewById(R.id.cardInterviewInvitation);
        if (!hasInterview) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);

        int interviewId = interview != null ? Json.integer(interview, 0, "id") : 0;
        String schedRaw = interview != null
                ? Json.str(interview, null, "scheduled_at", "interview_date", "date")
                : Json.str(app, null, "interview_scheduled_at", "interview_date", "reviewed_at");
        String schedText = schedRaw != null
                ? StudentDashboardActivity.formatDateTime(schedRaw)
                : "Scheduled by Host Company";

        String modeRaw = interview != null
                ? Json.str(interview, "In-Person Office", "type", "mode", "format")
                : "In-Person Office";
        String modeLabel = formatInterviewMode(modeRaw);

        String loc = interview != null
                ? Json.str(interview, fallbackLocation, "location", "meeting_link", "venue")
                : fallbackLocation;
        String notes = interview != null
                ? Json.str(interview, null, "notes", "instructions", "reschedule_reason")
                : null;
        String intStatus = interview != null
                ? Json.str(interview, "scheduled", "status").toLowerCase(Locale.ROOT)
                : "scheduled";
        boolean confirmed = interview != null
                && interview.has("confirmed_by_student")
                && !interview.get("confirmed_by_student").isJsonNull()
                && interview.get("confirmed_by_student").getAsBoolean();

        ((TextView) card.findViewById(R.id.tvInterviewDateTime))
                .setText("📅 " + schedText + "  •  " + modeLabel);
        ((TextView) card.findViewById(R.id.tvInterviewLocationLine))
                .setText("Location:  " + loc);

        TextView tvNotes = card.findViewById(R.id.tvInterviewNotesLine);
        if (notes != null && !notes.isEmpty()) {
            tvNotes.setVisibility(View.VISIBLE);
            tvNotes.setText("Notes:  \"" + notes + "\"");
        } else {
            tvNotes.setVisibility(View.GONE);
        }

        TextView badge = card.findViewById(R.id.tvInterviewActionBadge);
        MaterialButton btnConfirm = card.findViewById(R.id.btnCardConfirmInterview);
        MaterialButton btnReschedule = card.findViewById(R.id.btnCardRescheduleInterview);

        if (confirmed || "confirmed".equals(intStatus)) {
            badge.setText("✓ Confirmed");
            badge.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_green_bg)));
            badge.setTextColor(primaryGreen);
            btnConfirm.setText("Confirmed ✓");
            btnConfirm.setEnabled(false);
        } else if ("reschedule_requested".equals(intStatus)) {
            badge.setText("↺ Reschedule Requested");
            badge.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_amber_bg)));
            badge.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            btnConfirm.setText("✓ Confirm");
            btnConfirm.setEnabled(true);
        } else {
            badge.setText("• Action Required");
            badge.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_indigo_bg)));
            badge.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
            btnConfirm.setText("✓ Confirm");
            btnConfirm.setEnabled(true);
        }

        btnConfirm.setOnClickListener(v -> {
            if (interviewId > 0) {
                confirmInterviewAttendance(interviewId);
            } else {
                ApplicationDetailActivity.launch(this, appId, title, company, statusLabel);
            }
        });

        btnReschedule.setOnClickListener(v -> {
            if (interviewId > 0) {
                promptRescheduleInterview(interviewId);
            } else {
                ApplicationDetailActivity.launch(this, appId, title, company, statusLabel);
            }
        });
    }

    public static String formatInterviewMode(@Nullable String mode) {
        if (mode == null || mode.isEmpty()) return "In-Person Office";
        String lower = mode.toLowerCase(Locale.ROOT);
        if (lower.contains("online") || lower.contains("video") || lower.contains("virtual")
                || lower.contains("remote")) {
            return "Online Video";
        }
        if (lower.contains("phone") || lower.contains("call")) {
            return "Phone Interview";
        }
        return "In-Person Office";
    }

    public static String defaultFeedbackForStatus(String status) {
        switch (status) {
            case "accepted":
            case "approved":
                return "Congratulations! Your application has been accepted by the recruitment team.";
            case "rejected":
                return "Thank you for applying. The recruitment team has decided not to move forward.";
            case "withdrawn":
                return "You withdrew this application.";
            case "reviewed":
            case "reviewing":
            case "under_review":
            case "shortlisted":
            case "interview":
            case "interviewed":
            case "interviewing":
            case "interview_scheduled":
                return "Application under evaluation by recruitment team.";
            default:
                return "Application submitted and awaiting initial review by the recruitment team.";
        }
    }

    private void confirmInterviewAttendance(int interviewId) {
        repository.confirmInterview(interviewId, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                Toast.makeText(StudentApplicationsActivity.this,
                        "Interview attendance confirmed ✓", Toast.LENGTH_SHORT).show();
                loadApplications();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                Toast.makeText(StudentApplicationsActivity.this,
                        error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void promptRescheduleInterview(int interviewId) {
        EditText input = new EditText(this);
        input.setHint("Enter reason & your available dates/times...");
        input.setMinLines(3);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Request Interview Reschedule")
                .setMessage("Please share your reason and preferred alternative dates/times with the recruiter:")
                .setView(wrapper)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Submit Request", (d, w) -> {
                    String reason = input.getText() == null ? "" : input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Please enter a reason for rescheduling.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.rescheduleInterview(interviewId, reason,
                            new StudentRepository.JsonResult() {
                                @Override
                                public void onSuccess(@NonNull JsonObject body) {
                                    Toast.makeText(StudentApplicationsActivity.this,
                                            "Reschedule request sent to recruiter.",
                                            Toast.LENGTH_SHORT).show();
                                    loadApplications();
                                }

                                @Override
                                public void onError(@NonNull ApiError error) {
                                    Toast.makeText(StudentApplicationsActivity.this,
                                            error.message, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .show();
    }

    private void confirmWithdrawApplication(int appId, String title, String company) {
        if (appId <= 0) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Withdraw Application?")
                .setMessage("Are you sure you want to withdraw your application for \""
                        + title + "\" at " + company + "?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Withdraw", (d, w) ->
                        repository.withdrawApplication(appId, new StudentRepository.JsonResult() {
                            @Override
                            public void onSuccess(@NonNull JsonObject body) {
                                Toast.makeText(StudentApplicationsActivity.this,
                                        "Application withdrawn successfully.",
                                        Toast.LENGTH_SHORT).show();
                                loadApplications();
                            }

                            @Override
                            public void onError(@NonNull ApiError error) {
                                Toast.makeText(StudentApplicationsActivity.this,
                                        error.message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    @Override
    protected void onDestroy() {
        if (appsCall != null) appsCall.cancel();
        super.onDestroy();
    }
}
