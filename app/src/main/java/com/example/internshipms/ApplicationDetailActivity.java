package com.example.internshipms;

import android.content.Context;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

import retrofit2.Call;

public class ApplicationDetailActivity extends AppCompatActivity {

    public static final String EXTRA_APP_ID = "extra_app_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_COMPANY = "extra_company";
    public static final String EXTRA_STATUS = "extra_status";

    private enum StepState {
        COMPLETED, CURRENT, PENDING
    }

    private Call<JsonObject> detailCall;
    private int currentAppId;
    private String currentTitle = "Internship Position";
    private String currentCompany = "Company";

    public static void launch(Context context, String title, String company, String status) {
        launch(context, 0, title, company, status);
    }

    public static void launch(Context context, int appId, String title, String company, String status) {
        Intent intent = new Intent(context, ApplicationDetailActivity.class);
        intent.putExtra(EXTRA_APP_ID, appId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_COMPANY, company);
        intent.putExtra(EXTRA_STATUS, status);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_application_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        Intent intent = getIntent();
        currentAppId = intent != null ? intent.getIntExtra(EXTRA_APP_ID, 0) : 0;
        currentTitle = getOrDefault(intent, EXTRA_TITLE, "Internship Position");
        currentCompany = getOrDefault(intent, EXTRA_COMPANY, "Company");
        String status = getOrDefault(intent, EXTRA_STATUS, "Pending");

        renderHeader(currentTitle, currentCompany, status, null);
        renderTimeline(status, null);
        updateWithdrawButton(status);

        findViewById(R.id.btnViewPdf).setOnClickListener(v ->
                Toast.makeText(this, "Opening CV PDF...", Toast.LENGTH_SHORT).show());

        if (currentAppId > 0) {
            fetchApplication(currentAppId);
        }
    }

    private void fetchApplication(int appId) {
        StudentRepository repo = new StudentRepository(this);
        detailCall = repo.showApplication(appId, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject app = Json.data(body);
                JsonObject post = Json.obj(app, "internship_post", "post");
                currentTitle = Json.str(post, currentTitle, "title");
                currentCompany = StudentDashboardActivity.companyName(post, app);
                String statusRaw = Json.str(app, "pending", "status").toLowerCase(Locale.ROOT);
                String statusLabel = StudentDashboardActivity.statusLabel(statusRaw);
                String appliedAt = StudentDashboardActivity.formatDeadlineDate(
                        Json.str(app, null, "applied_at", "created_at"));
                String resumePath = Json.str(app, null, "custom_resume_path");

                renderHeader(currentTitle, currentCompany, statusLabel, resumePath);
                String logoUrl = com.example.internshipms.network.AvatarLoader
                        .extractCompanyLogo(post, app, currentCompany);
                com.example.internshipms.network.AvatarLoader.bind(
                        logoUrl,
                        findViewById(R.id.ivCompanyLogo),
                        findViewById(R.id.tvInitials));
                renderInterviewCard(app);
                renderTimeline(statusLabel, appliedAt);
                updateWithdrawButton(statusRaw);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                // Keep initial data
            }
        });
    }

    private void updateWithdrawButton(String status) {
        MaterialButton btn = findViewById(R.id.btnSubmitUpdates);
        if (btn == null) return;
        String lower = status == null ? "" : status.replace("•", "").trim().toLowerCase(Locale.ROOT);
        if ("pending".equals(lower) || "applied".equals(lower)) {
            btn.setVisibility(View.VISIBLE);
            btn.setText("Withdraw Application");
            btn.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_red_bg)));
            btn.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
            btn.setOnClickListener(v -> confirmWithdraw());
        } else {
            btn.setVisibility(View.GONE);
        }
    }

    private void confirmWithdraw() {
        if (currentAppId <= 0) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Withdraw Application?")
                .setMessage("Are you sure you want to withdraw your application for \""
                        + currentTitle + "\" at " + currentCompany + "?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Withdraw", (d, w) ->
                        new StudentRepository(this).withdrawApplication(currentAppId,
                                new StudentRepository.JsonResult() {
                                    @Override
                                    public void onSuccess(@NonNull JsonObject body) {
                                        Toast.makeText(ApplicationDetailActivity.this,
                                                "Application withdrawn successfully.",
                                                Toast.LENGTH_SHORT).show();
                                        fetchApplication(currentAppId);
                                    }

                                    @Override
                                    public void onError(@NonNull ApiError error) {
                                        Toast.makeText(ApplicationDetailActivity.this,
                                                error.message, Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    private void renderInterviewCard(JsonObject app) {
        View card = findViewById(R.id.cardInterview);
        if (card == null) return;

        JsonObject interview = StudentApplicationsActivity.extractInterview(app);
        String appStatus = Json.str(app, "pending", "status").toLowerCase(Locale.ROOT);
        boolean statusHasInterview = "interview".equals(appStatus)
                || "interviewed".equals(appStatus)
                || "interviewing".equals(appStatus)
                || "interview_scheduled".equals(appStatus);

        if (interview == null && !statusHasInterview) {
            card.setVisibility(View.GONE);
            return;
        }

        card.setVisibility(View.VISIBLE);
        JsonObject post = Json.obj(app, "internship_post", "post");
        String postLoc = Json.str(post, "To be announced by recruiter", "location");

        int interviewId = interview != null ? Json.integer(interview, 0, "id") : 0;
        String schedRaw = interview != null
                ? Json.str(interview, null, "scheduled_at", "interview_date", "date")
                : Json.str(app, null, "interview_scheduled_at", "interview_date", "reviewed_at");
        String modeRaw = interview != null
                ? Json.str(interview, "In-Person Office", "type", "mode", "format")
                : "In-Person Office";
        String schedFormatted = schedRaw != null
                ? StudentDashboardActivity.formatDateTime(schedRaw)
                + " • " + StudentApplicationsActivity.formatInterviewMode(modeRaw)
                : "Scheduled by Host Company • " + StudentApplicationsActivity.formatInterviewMode(modeRaw);
        String locationOrLink = interview != null
                ? Json.str(interview, postLoc, "location", "meeting_link", "venue")
                : postLoc;
        String notes = interview != null
                ? Json.str(interview, null, "notes", "instructions", "reschedule_reason")
                : Json.str(app, null, "company_notes");
        String status = interview != null
                ? Json.str(interview, "scheduled", "status").toLowerCase(Locale.ROOT)
                : "scheduled";
        boolean confirmed = interview != null
                && interview.has("confirmed_by_student")
                && !interview.get("confirmed_by_student").isJsonNull()
                && interview.get("confirmed_by_student").getAsBoolean();

        TextView tvStatus = findViewById(R.id.tvInterviewStatus);
        int primaryGreen = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
        if (confirmed || "confirmed".equals(status)) {
            tvStatus.setText("Confirmed ✓");
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_green_bg)));
            tvStatus.setTextColor(primaryGreen);
        } else if ("reschedule_requested".equals(status)) {
            tvStatus.setText("Reschedule Requested ↺");
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_amber_bg)));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
        } else {
            tvStatus.setText("Action Required");
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_indigo_bg)));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
        }

        ((TextView) findViewById(R.id.tvInterviewDate)).setText("📅 " + schedFormatted);
        ((TextView) findViewById(R.id.tvInterviewLocation)).setText("📍 Location: " + locationOrLink);

        TextView tvNotes = findViewById(R.id.tvInterviewNotes);
        if (notes != null && !notes.isEmpty()) {
            tvNotes.setVisibility(View.VISIBLE);
            tvNotes.setText("Notes: \"" + notes + "\"");
        } else {
            tvNotes.setVisibility(View.GONE);
        }

        MaterialButton btnConfirm = findViewById(R.id.btnConfirmInterview);
        MaterialButton btnReschedule = findViewById(R.id.btnRescheduleInterview);

        if (confirmed || "confirmed".equals(status)) {
            btnConfirm.setText("Confirmed ✓");
            btnConfirm.setEnabled(false);
        } else {
            btnConfirm.setText("✓ Confirm");
            btnConfirm.setEnabled(interviewId > 0);
            btnConfirm.setOnClickListener(v -> confirmInterviewAttendance(interviewId));
        }

        btnReschedule.setEnabled(interviewId > 0);
        btnReschedule.setOnClickListener(v -> promptRescheduleInterview(interviewId));
    }

    private void confirmInterviewAttendance(int interviewId) {
        if (interviewId <= 0) return;
        new StudentRepository(this).confirmInterview(interviewId, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                Toast.makeText(ApplicationDetailActivity.this,
                        "Interview attendance confirmed ✓", Toast.LENGTH_SHORT).show();
                if (currentAppId > 0) fetchApplication(currentAppId);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                Toast.makeText(ApplicationDetailActivity.this,
                        error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void promptRescheduleInterview(int interviewId) {
        if (interviewId <= 0) return;
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
                    new StudentRepository(this).rescheduleInterview(interviewId, reason,
                            new StudentRepository.JsonResult() {
                                @Override
                                public void onSuccess(@NonNull JsonObject body) {
                                    Toast.makeText(ApplicationDetailActivity.this,
                                            "Reschedule request sent to recruiter.",
                                            Toast.LENGTH_SHORT).show();
                                    if (currentAppId > 0) fetchApplication(currentAppId);
                                }

                                @Override
                                public void onError(@NonNull ApiError error) {
                                    Toast.makeText(ApplicationDetailActivity.this,
                                            error.message, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .show();
    }

    private void renderHeader(String title, String company, String status, String resumePath) {
        ((TextView) findViewById(R.id.tvTitle)).setText(title);
        ((TextView) findViewById(R.id.tvCompany)).setText(company);
        TextView tvInitials = findViewById(R.id.tvInitials);
        tvInitials.setText(initials(company));
        String cachedLogo = com.example.internshipms.network.AvatarLoader
                .extractCompanyLogo(null, null, company);
        com.example.internshipms.network.AvatarLoader.bind(
                cachedLogo,
                findViewById(R.id.ivCompanyLogo),
                tvInitials);
        ((TextView) findViewById(R.id.tvStatusBadge)).setText(status.replace("•", "").trim());

        TextView tvCv = findViewById(R.id.tvCvFilename);
        if (resumePath != null && !resumePath.isEmpty()) {
            int slash = resumePath.lastIndexOf('/');
            tvCv.setText(slash >= 0 ? resumePath.substring(slash + 1) : resumePath);
        } else {
            String studentName = SessionManager.get(this).getName();
            if (studentName != null && !studentName.trim().isEmpty()) {
                String safeName = studentName.trim().replaceAll("\\s+", "_");
                tvCv.setText(safeName + "_Resume.pdf");
            }
        }
    }

    private void renderTimeline(String statusLabel, String appliedDate) {
        ViewGroup container = findViewById(R.id.containerTimeline);
        container.removeAllViews();

        String clean = statusLabel.replace("•", "").trim().toLowerCase(Locale.ROOT);
        boolean isReviewed = clean.contains("review");
        boolean isShortlisted = clean.contains("shortlist") || clean.contains("interview");
        boolean isInterviewed = clean.contains("interview");
        boolean isAccepted = clean.contains("accept") || clean.contains("offer");
        boolean isRejected = clean.contains("reject") || clean.contains("withdrawn");

        addTimelineStep(
                StepState.COMPLETED,
                R.drawable.ic_check,
                "Applied",
                appliedDate != null ? appliedDate : "Submitted",
                "Your resume and cover letter have been sent to the employer.",
                false);

        addTimelineStep(
                (isShortlisted || isAccepted || isRejected) ? StepState.COMPLETED
                        : (isReviewed ? StepState.CURRENT : StepState.CURRENT),
                (isShortlisted || isAccepted || isRejected) ? R.drawable.ic_check : R.drawable.ic_schedule,
                "Under Review",
                null,
                "Recruiting team is reviewing your profile and qualifications.",
                false);

        addTimelineStep(
                (isInterviewed || isAccepted) ? StepState.COMPLETED
                        : (isShortlisted ? StepState.CURRENT : StepState.PENDING),
                (isInterviewed || isAccepted) ? R.drawable.ic_check : R.drawable.ic_check,
                "Shortlisted",
                null,
                "Candidate shortlisted by the company for further evaluation.",
                false);

        addTimelineStep(
                isAccepted ? StepState.COMPLETED
                        : (isInterviewed ? StepState.CURRENT : StepState.PENDING),
                isAccepted ? R.drawable.ic_check : R.drawable.ic_event,
                "Interviewed",
                null,
                "Interview session scheduled with the host company.",
                false);

        addTimelineStep(
                (isAccepted || isRejected) ? StepState.COMPLETED : StepState.PENDING,
                isAccepted ? R.drawable.ic_check_circle : R.drawable.ic_schedule,
                isRejected ? "Application Not Selected" : "Accepted",
                null,
                isAccepted ? "Congratulations! Your application has been accepted."
                        : (isRejected ? "Thank you for applying. Keep exploring other open positions."
                        : "Awaiting final placement decision from employer."),
                true);
    }

    private void addTimelineStep(StepState state, @DrawableRes int iconRes, String title,
                                 String time, String desc, boolean isLast) {
        ViewGroup container = findViewById(R.id.containerTimeline);
        View step = LayoutInflater.from(this).inflate(R.layout.item_timeline_step, container, false);

        MaterialCardView outerRing = step.findViewById(R.id.outerRing);
        MaterialCardView stepCircle = step.findViewById(R.id.stepCircle);
        ImageView ivIcon = step.findViewById(R.id.ivStepIcon);
        View stepLine = step.findViewById(R.id.stepLine);
        TextView tvTitle = step.findViewById(R.id.tvStepTitle);
        TextView tvBadge = step.findViewById(R.id.tvStepBadge);
        TextView tvTime = step.findViewById(R.id.tvStepTime);
        TextView tvDesc = step.findViewById(R.id.tvStepDesc);

        tvTitle.setText(title);
        ivIcon.setImageResource(iconRes);

        int indigo = ContextCompat.getColor(this, R.color.tint_indigo);
        int indigoBg = ContextCompat.getColor(this, R.color.tint_indigo_bg);
        int strokeSoft = ContextCompat.getColor(this, R.color.stroke_soft);
        int pageBg = ContextCompat.getColor(this, R.color.bg_page);

        switch (state) {
            case COMPLETED:
                outerRing.setCardBackgroundColor(Color.TRANSPARENT);
                stepCircle.setCardBackgroundColor(indigo);
                ivIcon.setColorFilter(Color.WHITE);
                stepLine.setBackgroundColor(indigo);
                break;

            case CURRENT:
                outerRing.setCardBackgroundColor(indigoBg);
                stepCircle.setCardBackgroundColor(indigo);
                ivIcon.setColorFilter(Color.WHITE);
                stepLine.setBackgroundColor(strokeSoft);
                tvBadge.setVisibility(View.VISIBLE);
                tvTime.setTextColor(indigo);
                tvTime.setTypeface(tvTime.getTypeface(), android.graphics.Typeface.BOLD);
                break;

            case PENDING:
                outerRing.setCardBackgroundColor(Color.TRANSPARENT);
                stepCircle.setCardBackgroundColor(pageBg);
                ivIcon.setColorFilter(Color.parseColor("#64748B"));
                tvTitle.setTextColor(Color.parseColor("#64748B"));
                break;
        }

        if (isLast) stepLine.setVisibility(View.INVISIBLE);

        if (time == null || time.isEmpty()) {
            tvTime.setVisibility(View.GONE);
        } else {
            tvTime.setVisibility(View.VISIBLE);
            tvTime.setText(time);
        }

        if (desc == null || desc.isEmpty()) {
            tvDesc.setVisibility(View.GONE);
        } else {
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText(desc);
        }

        container.addView(step);
    }

    private static String getOrDefault(Intent intent, String key, String fallback) {
        if (intent == null) return fallback;
        String val = intent.getStringExtra(key);
        return (val == null || val.isEmpty()) ? fallback : val;
    }

    private static String initials(String text) {
        String[] words = text.trim().split("[\\s/(]+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "I";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    @Override
    protected void onDestroy() {
        if (detailCall != null) detailCall.cancel();
        super.onDestroy();
    }
}
