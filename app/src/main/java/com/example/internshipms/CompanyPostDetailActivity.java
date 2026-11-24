package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.CompanyRepository;
import com.example.internshipms.session.SessionManager;
import com.example.internshipms.view.PullRefreshScrollView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CompanyPostDetailActivity extends AppCompatActivity {

    private static final String EXTRA_POST_ID = "extra_post_id";
    private static final String EXTRA_POST_JSON = "extra_post_json";

    public static void launch(Context context, int postId, @Nullable JsonObject post) {
        Intent intent = new Intent(context, CompanyPostDetailActivity.class);
        intent.putExtra(EXTRA_POST_ID, postId);
        if (post != null) {
            intent.putExtra(EXTRA_POST_JSON, post.toString());
        }
        context.startActivity(intent);
    }

    private CompanyRepository repo;
    private int postId;
    private JsonObject currentPost = new JsonObject();

    private PullRefreshScrollView scrollRefresh;
    private TextView tvStatusPill;
    private TextView tvOpportunityTypePill;
    private TextView tvWorkModePill;
    private TextView tvRoleTitle;
    private TextView tvCategoryLocation;
    private View boxRejectionBanner;
    private TextView tvRejectionReasonText;
    private TextView tvStipendChip;
    private TextView tvDurationChip;
    private TextView tvDeadlineChip;
    private TextView tvSlotsAccepted;
    private LinearProgressIndicator progressSlots;
    private TextView tvCapacityPercent;
    private TextView tvSpotsRemaining;

    private TextView tvCandidatesApplied;
    private TextView tvFunnelReview;
    private TextView tvFunnelInterviewed;
    private TextView tvFunnelHired;
    private TextView tvFunnelPipeline;

    private TextView tvDistReview;
    private TextView tvDistShortlisted;
    private TextView tvDistOffered;
    private TextView tvDistDeclined;

    private TextView tvRoleDescription;
    private ImageView ivDescChevron;
    private LinearLayout containerRequirements;
    private ImageView ivReqChevron;
    private LinearLayout containerDuties;
    private ImageView ivDutiesChevron;

    private MaterialButton btnCloseVacancy;
    private TextView tvCloseCaption;

    private boolean descExpanded = true;
    private boolean reqExpanded = true;
    private boolean dutiesExpanded = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_company_post_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repo = new CompanyRepository(this);
        postId = getIntent().getIntExtra(EXTRA_POST_ID, 0);
        String rawJson = getIntent().getStringExtra(EXTRA_POST_JSON);
        if (rawJson != null && !rawJson.isEmpty()) {
            try {
                currentPost = JsonParser.parseString(rawJson).getAsJsonObject();
                if (postId <= 0) {
                    postId = Json.integer(currentPost, 0, "id");
                }
            } catch (Exception ignored) {
            }
        }

        // ── Bind Views ───────────────────────────────────────────────────────
        scrollRefresh = findViewById(R.id.scrollRefresh);
        tvStatusPill = findViewById(R.id.tvStatusPill);
        tvOpportunityTypePill = findViewById(R.id.tvOpportunityTypePill);
        tvWorkModePill = findViewById(R.id.tvWorkModePill);
        tvRoleTitle = findViewById(R.id.tvRoleTitle);
        tvCategoryLocation = findViewById(R.id.tvCategoryLocation);
        boxRejectionBanner = findViewById(R.id.boxRejectionBanner);
        tvRejectionReasonText = findViewById(R.id.tvRejectionReasonText);
        tvStipendChip = findViewById(R.id.tvStipendChip);
        tvDurationChip = findViewById(R.id.tvDurationChip);
        tvDeadlineChip = findViewById(R.id.tvDeadlineChip);
        tvSlotsAccepted = findViewById(R.id.tvSlotsAccepted);
        progressSlots = findViewById(R.id.progressSlots);
        tvCapacityPercent = findViewById(R.id.tvCapacityPercent);
        tvSpotsRemaining = findViewById(R.id.tvSpotsRemaining);

        tvCandidatesApplied = findViewById(R.id.tvCandidatesApplied);
        tvFunnelReview = findViewById(R.id.tvFunnelReview);
        tvFunnelInterviewed = findViewById(R.id.tvFunnelInterviewed);
        tvFunnelHired = findViewById(R.id.tvFunnelHired);
        tvFunnelPipeline = findViewById(R.id.tvFunnelPipeline);

        tvDistReview = findViewById(R.id.tvDistReview);
        tvDistShortlisted = findViewById(R.id.tvDistShortlisted);
        tvDistOffered = findViewById(R.id.tvDistOffered);
        tvDistDeclined = findViewById(R.id.tvDistDeclined);

        tvRoleDescription = findViewById(R.id.tvRoleDescription);
        ivDescChevron = findViewById(R.id.ivDescChevron);
        containerRequirements = findViewById(R.id.containerRequirements);
        ivReqChevron = findViewById(R.id.ivReqChevron);
        containerDuties = findViewById(R.id.containerDuties);
        ivDutiesChevron = findViewById(R.id.ivDutiesChevron);

        btnCloseVacancy = findViewById(R.id.btnCloseVacancy);
        tvCloseCaption = findViewById(R.id.tvCloseCaption);

        // ── Static Click Listeners ───────────────────────────────────────────
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEdit).setOnClickListener(v ->
                PostInternshipActivity.launchEdit(this, currentPost));
        View btnMoreActions = findViewById(R.id.btnMoreActions);
        if (btnMoreActions != null) {
            btnMoreActions.setOnClickListener(this::showMoreActionsMenu);
        }

        View.OnClickListener openApplicantsForPost = v -> {
            String roleTitle = Json.str(currentPost, "Internship Position", "title");
            CompanyApplicantsActivity.launchForPost(this, postId, roleTitle);
        };
        findViewById(R.id.btnOpenPipeline).setOnClickListener(openApplicantsForPost);
        View cardDistReview = findViewById(R.id.cardDistReview);
        if (cardDistReview != null) cardDistReview.setOnClickListener(openApplicantsForPost);
        View cardDistShortlisted = findViewById(R.id.cardDistShortlisted);
        if (cardDistShortlisted != null) cardDistShortlisted.setOnClickListener(openApplicantsForPost);
        View cardDistOffered = findViewById(R.id.cardDistOffered);
        if (cardDistOffered != null) cardDistOffered.setOnClickListener(openApplicantsForPost);
        View cardDistDeclined = findViewById(R.id.cardDistDeclined);
        if (cardDistDeclined != null) cardDistDeclined.setOnClickListener(openApplicantsForPost);

        View headerDescription = findViewById(R.id.headerDescription);
        if (headerDescription != null) {
            headerDescription.setOnClickListener(v -> {
                descExpanded = !descExpanded;
                if (tvRoleDescription != null) {
                    tvRoleDescription.setVisibility(descExpanded ? View.VISIBLE : View.GONE);
                }
                if (ivDescChevron != null) {
                    ivDescChevron.setRotation(descExpanded ? 0f : 180f);
                }
            });
        }

        findViewById(R.id.headerRequirements).setOnClickListener(v -> {
            reqExpanded = !reqExpanded;
            containerRequirements.setVisibility(reqExpanded ? View.VISIBLE : View.GONE);
            ivReqChevron.setRotation(reqExpanded ? 0f : 180f);
        });

        findViewById(R.id.headerDuties).setOnClickListener(v -> {
            dutiesExpanded = !dutiesExpanded;
            containerDuties.setVisibility(dutiesExpanded ? View.VISIBLE : View.GONE);
            ivDutiesChevron.setRotation(dutiesExpanded ? 0f : 180f);
        });

        btnCloseVacancy.setOnClickListener(v -> handleBottomAction());

        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadPostAndApplicants);
        }

        if (currentPost.size() > 0) {
            bindPost(currentPost);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPostAndApplicants();
    }

    // ── API Calls ────────────────────────────────────────────────────────────

    private void loadPostAndApplicants() {
        if (postId <= 0) {
            if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
            return;
        }
        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);

        repo.showPost(postId, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonObject post = Json.data(body);
                if (post.size() > 0) {
                    currentPost = post;
                    bindPost(currentPost);
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                toast(error.message);
            }
        });

        repo.listApplicants(postId, null, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonArray applicants = Json.list(body, "applicants", "data");
                bindApplicantMetrics(applicants);
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    // ── Bind Post Details ────────────────────────────────────────────────────

    private void bindPost(@NonNull JsonObject post) {
        String title = Json.str(post, "Android Mobile App Developer Intern", "title");
        String category = Json.str(post, "Software & Technology", "category", "department");
        String location = Json.str(post, "Phnom Penh", "location");
        String oppType = Json.str(post, "internship", "opportunity_type");
        String workMode = Json.str(post, "on_site", "work_mode", "type");
        String expLevel = Json.str(post, null, "experience_level");
        String status = Json.str(post, "pending_approval", "status");
        String rejectionReason = Json.str(post, null, "rejection_reason", "admin_notes");
        int slots = Math.max(1, Json.integer(post, 5, "slots"));
        int weeks = Math.max(1, Json.integer(post, 12, "duration_weeks"));
        int appsCount = Math.max(0, Json.integer(post, 0, "applications_count"));

        tvRoleTitle.setText(title);
        if (tvCategoryLocation != null) {
            tvCategoryLocation.setText(category + " • " + location);
        }
        bindStatusPill(status);

        boolean isInternship = "internship".equalsIgnoreCase(oppType);
        if (tvOpportunityTypePill != null) {
            if (isInternship) {
                tvOpportunityTypePill.setText("🎓 Internship");
                tvOpportunityTypePill.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_green_bg)));
                tvOpportunityTypePill.setTextColor(ContextCompat.getColor(this, R.color.tint_green));
            } else {
                String oppLabel = Json.str(post, null, "opportunity_type_label");
                if (oppLabel == null || oppLabel.isEmpty()) {
                    if ("full_time".equalsIgnoreCase(oppType)) oppLabel = "💼 Full-Time";
                    else if ("part_time".equalsIgnoreCase(oppType)) oppLabel = "🕒 Part-Time";
                    else if ("contract".equalsIgnoreCase(oppType)) oppLabel = "📄 Contract";
                    else oppLabel = "💼 Job";
                } else if (!oppLabel.startsWith("💼") && !oppLabel.startsWith("🕒") && !oppLabel.startsWith("📄")) {
                    oppLabel = "💼 " + oppLabel;
                }
                tvOpportunityTypePill.setText(oppLabel);
                tvOpportunityTypePill.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                tvOpportunityTypePill.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
            }
        }

        if (tvWorkModePill != null) {
            String m = workMode == null ? "" : workMode.toLowerCase(Locale.ROOT);
            if (m.contains("remote")) {
                tvWorkModePill.setText("🏠 Remote");
                tvWorkModePill.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_green_bg)));
                tvWorkModePill.setTextColor(ContextCompat.getColor(this, R.color.tint_green));
            } else if (m.contains("hybrid")) {
                tvWorkModePill.setText("🔄 Hybrid");
                tvWorkModePill.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_amber_bg)));
                tvWorkModePill.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            } else {
                tvWorkModePill.setText("🏢 On-Site");
                tvWorkModePill.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                tvWorkModePill.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
            }
        }

        if (boxRejectionBanner != null && tvRejectionReasonText != null) {
            if ("rejected".equalsIgnoreCase(status) && rejectionReason != null && !rejectionReason.trim().isEmpty()) {
                boxRejectionBanner.setVisibility(View.VISIBLE);
                tvRejectionReasonText.setText(rejectionReason.trim());
            } else {
                boxRejectionBanner.setVisibility(View.GONE);
            }
        }

        // Compensation / Stipend chip (supports compensation_display & salary_max)
        String compDisplay = Json.str(post, null, "compensation_display");
        double stipendVal = 0;
        double salaryMaxVal = 0;
        try {
            String s = Json.str(post, null, "stipend");
            if (s != null) stipendVal = Double.parseDouble(s.replaceAll("[^0-9.]", ""));
        } catch (Exception ignored) {
        }
        try {
            String sm = Json.str(post, null, "salary_max");
            if (sm != null) salaryMaxVal = Double.parseDouble(sm.replaceAll("[^0-9.]", ""));
        } catch (Exception ignored) {
        }
        boolean disclosed = !post.has("is_stipend_disclosed")
                || post.get("is_stipend_disclosed").isJsonNull()
                || (post.get("is_stipend_disclosed").isJsonPrimitive()
                && ("1".equals(post.get("is_stipend_disclosed").getAsString())
                || post.get("is_stipend_disclosed").getAsBoolean()));

        if (compDisplay != null && !compDisplay.trim().isEmpty()) {
            tvStipendChip.setText(compDisplay.trim());
        } else if (stipendVal > 0 && disclosed) {
            String minFmt = stipendVal == (long) stipendVal
                    ? String.valueOf((long) stipendVal)
                    : String.format(Locale.US, "%.0f", stipendVal);
            if (salaryMaxVal > stipendVal) {
                String maxFmt = salaryMaxVal == (long) salaryMaxVal
                        ? String.valueOf((long) salaryMaxVal)
                        : String.format(Locale.US, "%.0f", salaryMaxVal);
                tvStipendChip.setText("$" + minFmt + " - $" + maxFmt + "/mo");
            } else {
                tvStipendChip.setText("$" + minFmt + " / month");
            }
        } else {
            tvStipendChip.setText(isInternship ? "Unpaid / Experience" : "Negotiable");
        }

        // Duration chip (for Internship) or Experience Level chip (for Job)
        if (isInternship) {
            if (weeks % 4 == 0) {
                int months = weeks / 4;
                tvDurationChip.setText(months + (months == 1 ? " Month" : " Months") + " (" + weeks + " wks)");
            } else {
                tvDurationChip.setText(weeks + " Weeks");
            }
        } else {
            String expLabel = Json.str(post, null, "experience_level_label");
            if (expLabel == null || expLabel.isEmpty()) {
                if ("no_experience".equalsIgnoreCase(expLevel)) expLabel = "No Experience";
                else if ("entry_level".equalsIgnoreCase(expLevel)) expLabel = "Entry Level";
                else if ("junior".equalsIgnoreCase(expLevel)) expLabel = "Junior (1-2 yrs)";
                else if ("mid_level".equalsIgnoreCase(expLevel)) expLabel = "Mid-Level (3+ yrs)";
                else expLabel = "All Levels";
            }
            tvDurationChip.setText(expLabel);
        }

        // Deadline chip with Expired / Expiring Soon coloring
        bindDeadlineChip(Json.str(post, null, "deadline"));

        // Initial slot allocation before applicants list finishes loading
        int accepted = Math.max(0, Json.integer(post, 0, "accepted_count", "filled_slots"));
        updateSlotAllocation(accepted, slots);

        // Initial funnel total from applications_count
        tvCandidatesApplied.setText(appsCount + (appsCount == 1
                ? " Candidate Applied" : " Candidates Applied"));

        // Role Overview / Description
        if (tvRoleDescription != null) {
            String descText = Json.str(post, "", "description");
            if (descText.trim().isEmpty()) {
                descText = "Join our engineering team for hands-on project mentorship, practical deliverables, and real-world software production experience.";
            }
            tvRoleDescription.setText(descText.trim());
        }

        // Requirements & Duties bullets
        String reqText = Json.str(post, "", "requirements");
        List<String> reqList = parseBullets(reqText);
        if (reqList.isEmpty()) {
            reqList.add("Strong foundational knowledge in " + Json.str(post, "Software Engineering", "category") + ".");
            reqList.add("Currently enrolled as a 3rd or 4th-year university student.");
            reqList.add("Proficient verbal and written communication in English and Khmer.");
        }
        renderDotBullets(containerRequirements, reqList,
                MaterialColors.getColor(tvRoleTitle, androidx.appcompat.R.attr.colorPrimary));

        String dutiesText = Json.str(post, "", "responsibilities");
        if (dutiesText.isEmpty()) {
            dutiesText = Json.str(post, "", "description");
        }
        List<String> dutiesList = parseBullets(dutiesText);
        if (dutiesList.isEmpty()) {
            dutiesList.add("Collaborate closely with senior engineers to build and refine production modules.");
            dutiesList.add("Participate actively in agile daily standups, sprint retrospectives, and design syncs.");
        }
        renderDotBullets(containerDuties, dutiesList,
                ContextCompat.getColor(this, R.color.tint_indigo));

        // Bottom button state based on status
        bindBottomActionButton(status);
    }

    private void bindStatusPill(String rawStatus) {
        String s = rawStatus == null ? "" : rawStatus.toLowerCase(Locale.ROOT);
        int bgColor;
        int textColor;
        String label;

        switch (s) {
            case "approved":
            case "active":
                label = "● Published";
                bgColor = ContextCompat.getColor(this, R.color.tint_green_bg);
                textColor = MaterialColors.getColor(tvStatusPill, androidx.appcompat.R.attr.colorPrimary);
                break;
            case "draft":
                label = "● Draft";
                bgColor = ContextCompat.getColor(this, R.color.tint_indigo_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_indigo);
                break;
            case "closed":
                label = "● Closed";
                bgColor = ContextCompat.getColor(this, R.color.tint_red_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_red);
                break;
            case "rejected":
                label = "● Rejected";
                bgColor = ContextCompat.getColor(this, R.color.tint_red_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_red);
                break;
            default:
                label = "● Pending Review";
                bgColor = ContextCompat.getColor(this, R.color.tint_amber_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_amber);
                break;
        }
        tvStatusPill.setText(label);
        tvStatusPill.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        tvStatusPill.setTextColor(textColor);
    }

    private void updateSlotAllocation(int accepted, int slots) {
        int safeSlots = Math.max(1, slots);
        int acceptedCount = Json.integer(currentPost, accepted, "accepted_count", "filled_slots");
        int clampedAccepted = Math.min(safeSlots, Math.max(0, acceptedCount));
        int percent = Math.round((clampedAccepted * 100f) / safeSlots);
        int remaining = Math.max(0, safeSlots - clampedAccepted);

        tvSlotsAccepted.setText(clampedAccepted + " / " + safeSlots + " Interns Accepted");
        progressSlots.setProgress(percent);
        tvCapacityPercent.setText(percent + "% cohort capacity filled");
        tvSpotsRemaining.setText(remaining + (remaining == 1 ? " spot remaining" : " spots remaining"));
    }

    // ── Bind Live Applicant Funnel & Distribution ────────────────────────────

    private void bindApplicantMetrics(@NonNull JsonArray applicants) {
        int review = 0;
        int shortlisted = 0;
        int interviewed = 0;
        int offered = 0;
        int declined = 0;

        for (JsonElement el : applicants) {
            if (!el.isJsonObject()) continue;
            JsonObject app = el.getAsJsonObject();
            String st = Json.str(app, "pending", "status").toLowerCase(Locale.ROOT);
            switch (st) {
                case "shortlisted":
                    shortlisted++;
                    break;
                case "interview":
                case "interview_scheduled":
                case "interviewed":
                    interviewed++;
                    break;
                case "accepted":
                    offered++;
                    break;
                case "rejected":
                case "withdrawn":
                    declined++;
                    break;
                case "pending":
                case "reviewed":
                case "under_review":
                default:
                    review++;
                    break;
            }
        }

        int total = applicants.size();
        int inPipeline = review + shortlisted + interviewed;
        int slots = Math.max(1, Json.integer(currentPost, 5, "slots"));

        tvCandidatesApplied.setText(total + (total == 1
                ? " Candidate Applied" : " Candidates Applied"));
        tvFunnelReview.setText(review + " Under Review");
        tvFunnelInterviewed.setText(interviewed + " Interviewed");
        tvFunnelHired.setText(offered + " Hired");
        tvFunnelPipeline.setText(inPipeline + " In Pipeline");

        tvDistReview.setText(String.valueOf(review));
        tvDistShortlisted.setText(String.valueOf(shortlisted + interviewed));
        tvDistOffered.setText(String.valueOf(offered));
        tvDistDeclined.setText(String.valueOf(declined));

        updateSlotAllocation(offered, slots);
    }

    // ── Render Bullet Lists ──────────────────────────────────────────────────

    private static List<String> parseBullets(String text) {
        List<String> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line.replaceFirst("^[•\\-*\\d.]+\\s*", "").trim();
            if (!cleaned.isEmpty()) {
                list.add(cleaned);
            }
        }
        return list;
    }

    private void renderDotBullets(LinearLayout container, List<String> items, int dotColor) {
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int bottomMargin = (int) (10 * density);

        for (int i = 0; i < items.size(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < items.size() - 1) {
                rowLp.bottomMargin = bottomMargin;
            }
            row.setLayoutParams(rowLp);

            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextColor(dotColor);
            dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dotLp.topMargin = (int) (3 * density);
            dotLp.rightMargin = (int) (10 * density);
            dot.setLayoutParams(dotLp);

            TextView body = new TextView(this);
            body.setText(items.get(i));
            body.setTextColor(Color.parseColor("#FF334155"));
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            body.setLineSpacing(2 * density, 1f);
            body.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(dot);
            row.addView(body);
            container.addView(row);
        }
    }

    // ── Bottom Action (Close / Submit / Duplicate) ───────────────────────────

    private void bindBottomActionButton(String rawStatus) {
        String s = rawStatus == null ? "" : rawStatus.toLowerCase(Locale.ROOT);
        if ("draft".equals(s)) {
            btnCloseVacancy.setText("Submit for Admin Approval");
            btnCloseVacancy.setIconResource(R.drawable.ic_send);
            int primary = MaterialColors.getColor(btnCloseVacancy, androidx.appcompat.R.attr.colorPrimary);
            btnCloseVacancy.setBackgroundTintList(ColorStateList.valueOf(primary));
            btnCloseVacancy.setTextColor(Color.WHITE);
            btnCloseVacancy.setIconTint(ColorStateList.valueOf(Color.WHITE));
            btnCloseVacancy.setStrokeWidth(0);
            tvCloseCaption.setText("Submitting this draft will send it to university moderators for review.");
        } else if ("rejected".equals(s)) {
            btnCloseVacancy.setText("Edit & Resubmit for Approval");
            btnCloseVacancy.setIconResource(R.drawable.ic_edit);
            int primary = MaterialColors.getColor(btnCloseVacancy, androidx.appcompat.R.attr.colorPrimary);
            btnCloseVacancy.setBackgroundTintList(ColorStateList.valueOf(primary));
            btnCloseVacancy.setTextColor(Color.WHITE);
            btnCloseVacancy.setIconTint(ColorStateList.valueOf(Color.WHITE));
            btnCloseVacancy.setStrokeWidth(0);
            tvCloseCaption.setText("Review the admin moderation feedback above, update the job details, and resubmit.");
        } else if ("closed".equals(s)) {
            btnCloseVacancy.setText("Reopen Job");
            btnCloseVacancy.setIconResource(R.drawable.ic_check_circle);
            int primary = MaterialColors.getColor(btnCloseVacancy, androidx.appcompat.R.attr.colorPrimary);
            btnCloseVacancy.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_green_bg)));
            btnCloseVacancy.setTextColor(primary);
            btnCloseVacancy.setIconTint(ColorStateList.valueOf(primary));
            tvCloseCaption.setText("This job is currently closed. Click to reopen and accept candidate applications.");
        } else {
            btnCloseVacancy.setText("Close Job");
            btnCloseVacancy.setIconResource(R.drawable.ic_cancel);
            int red = ContextCompat.getColor(this, R.color.tint_red);
            btnCloseVacancy.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_red_bg)));
            btnCloseVacancy.setTextColor(red);
            btnCloseVacancy.setIconTint(ColorStateList.valueOf(red));
            tvCloseCaption.setText("Closing this job will notify pending applicants and remove it from university student boards.");
        }
    }

    private void handleBottomAction() {
        if (postId <= 0) return;
        String status = Json.str(currentPost, "", "status").toLowerCase(Locale.ROOT);
        if ("rejected".equals(status)) {
            PostInternshipActivity.launchEdit(this, currentPost);
            return;
        }
        if ("draft".equals(status)) {
            repo.submitPost(postId, new CompanyRepository.JsonResult() {
                @Override
                public void onSuccess(@NonNull JsonObject body) {
                    toast("Submitted for admin approval! 🎉");
                    loadPostAndApplicants();
                }

                @Override
                public void onError(@NonNull ApiError error) {
                    toast(error.message);
                }
            });
            return;
        }

        if ("closed".equals(status)) {
            repo.toggleClosePost(postId, new CompanyRepository.JsonResult() {
                @Override
                public void onSuccess(@NonNull JsonObject body) {
                    toast(Json.str(body, "Job reopened!", "message"));
                    loadPostAndApplicants();
                }

                @Override
                public void onError(@NonNull ApiError error) {
                    toast(error.message);
                }
            });
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Close Job")
                .setMessage("Are you sure you want to close this job? Pending applicants will be notified and new submissions will be disabled.")
                .setPositiveButton("Close Job", (d, w) -> {
                    repo.toggleClosePost(postId, new CompanyRepository.JsonResult() {
                        @Override
                        public void onSuccess(@NonNull JsonObject body) {
                            toast("Job closed.");
                            loadPostAndApplicants();
                        }

                        @Override
                        public void onError(@NonNull ApiError error) {
                            toast(error.message);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMoreActionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        int appsCount = Math.max(0, Json.integer(currentPost, 0, "applications_count"));
        String title = Json.str(currentPost, "this job", "title");
        popup.getMenu().add(Menu.NONE, 1, 0, "View Applicants (" + appsCount + ")");
        popup.getMenu().add(Menu.NONE, 2, 1, "Duplicate as Draft");
        popup.getMenu().add(Menu.NONE, 3, 2, "Delete Job");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                CompanyApplicantsActivity.launchForPost(this, postId, title);
                return true;
            }
            if (id == 2 && postId > 0) {
                repo.duplicatePost(postId, new CompanyRepository.JsonResult() {
                    @Override
                    public void onSuccess(@NonNull JsonObject body) {
                        toast("Duplicated as draft!");
                        finish();
                    }

                    @Override
                    public void onError(@NonNull ApiError error) {
                        toast(error.message);
                    }
                });
                return true;
            }
            if (id == 3 && postId > 0) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Job")
                        .setMessage("Are you sure you want to delete \"" + title + "\"? This action cannot be undone.")
                        .setPositiveButton("Delete", (d, w) -> repo.deletePost(postId, new CompanyRepository.JsonResult() {
                            @Override
                            public void onSuccess(@NonNull JsonObject body) {
                                toast("Job deleted.");
                                finish();
                            }

                            @Override
                            public void onError(@NonNull ApiError error) {
                                toast(error.message);
                            }
                        }))
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void bindDeadlineChip(String raw) {
        if (tvDeadlineChip == null) return;
        if (raw == null || raw.isEmpty()) {
            tvDeadlineChip.setText("No deadline");
            tvDeadlineChip.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            return;
        }
        try {
            String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            LocalDate date = LocalDate.parse(datePart);
            LocalDate today = LocalDate.now();
            String formatted = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US));
            if (date.isBefore(today)) {
                tvDeadlineChip.setText("! Expired: " + formatted);
                tvDeadlineChip.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
            } else if (!date.isAfter(today.plusDays(5))) {
                tvDeadlineChip.setText("Closing Soon: " + formatted);
                tvDeadlineChip.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            } else {
                tvDeadlineChip.setText("Deadline: " + formatted);
                tvDeadlineChip.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            }
        } catch (Exception e) {
            tvDeadlineChip.setText("Deadline: " + raw);
            tvDeadlineChip.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
        }
    }

    private static String formatDeadline(String raw) {
        if (raw == null || raw.isEmpty()) return "Nov 30, 2026";
        try {
            String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            LocalDate date = LocalDate.parse(datePart);
            return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US));
        } catch (Exception e) {
            return raw;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

