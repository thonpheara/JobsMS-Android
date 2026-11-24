package com.example.internshipms;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class CompanyDashboardActivity extends AppCompatActivity {

    private CompanyRepository repo;
    private PullRefreshScrollView scrollRefresh;
    private LinearLayout containerSubmissions;
    private LinearLayout loadingContainer;
    private LinearLayout emptyContainer;
    private TextView tvNewBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_company_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repo = new CompanyRepository(this);

        // ── View references ──────────────────────────────────────────────────
        scrollRefresh = findViewById(R.id.scrollRefresh);
        containerSubmissions = findViewById(R.id.containerSubmissions);
        loadingContainer = findViewById(R.id.loadingContainer);
        emptyContainer = findViewById(R.id.emptyContainer);
        tvNewBadge = findViewById(R.id.tvNewBadge);

        // Set company name from session while API loads
        String sessionName = session.getName();
        if (sessionName != null && !sessionName.isEmpty()) {
            bindHeader(sessionName, false, null);
        }

        // ── Wire static interactions ─────────────────────────────────────────
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));
        View avatarView = findViewById(R.id.cardCompanyAvatar);
        if (avatarView != null) {
            avatarView.setOnClickListener(v ->
                    Navigator.switchTab(this, CompanyProfileActivity.class, false));
        }
        findViewById(R.id.cardPost).setOnClickListener(v ->
                PostInternshipActivity.launch(this));
        findViewById(R.id.statListings).setOnClickListener(v ->
                Navigator.switchTab(this, CompanyListingsActivity.class, false));
        findViewById(R.id.statApplicants).setOnClickListener(v ->
                Navigator.switchTab(this, CompanyApplicantsActivity.class, false));
        findViewById(R.id.statPending).setOnClickListener(v ->
                Navigator.switchTab(this, CompanyApplicantsActivity.class, false));
        findViewById(R.id.tvViewPipeline).setOnClickListener(v ->
                Navigator.switchTab(this, CompanyApplicantsActivity.class, false));

        View cardGoInterviews = findViewById(R.id.cardGoInterviews);
        if (cardGoInterviews != null) {
            cardGoInterviews.setOnClickListener(v -> CompanyInterviewsActivity.launch(this));
        }

        View cardGoReports = findViewById(R.id.cardGoReports);
        if (cardGoReports != null) {
            cardGoReports.setOnClickListener(v -> CompanyReportsActivity.launch(this));
        }

        // ── Bottom navigation ────────────────────────────────────────────────
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_listings) {
                Navigator.switchTab(this, CompanyListingsActivity.class, false);
                return false;
            }
            if (id == R.id.nav_applicants) {
                Navigator.switchTab(this, CompanyApplicantsActivity.class, false);
                return false;
            }
            if (id == R.id.nav_messages) {
                Navigator.switchTab(this, ConversationsActivity.class, false);
                return false;
            }
            if (id == R.id.nav_profile) {
                Navigator.switchTab(this, CompanyProfileActivity.class, false);
                return false;
            }
            return false;
        });

        // ── Pull-to-refresh ─────────────────────────────────────────────────
        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadDashboard);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null && nav.getSelectedItemId() != R.id.nav_home) {
            nav.setSelectedItemId(R.id.nav_home);
        }
        if (repo != null) {
            loadDashboard();
        }
    }

    // ── API Load ─────────────────────────────────────────────────────────────

    private void loadDashboard() {
        showLoading(true);
        View dotUnread = findViewById(R.id.dotUnread);
        repo.unreadNotificationCount(new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                int count = Json.integer(Json.data(body), 0, "unread_count");
                if (dotUnread != null) {
                    dotUnread.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });

        repo.dashboard(new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                bindDashboard(body);
            }

            @Override
            public void onError(ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                showLoading(false);
                toast("Failed to load: " + error.message);
                // Fall back to static placeholders so the screen is not empty
                bindStaticPlaceholders();
            }
        });
    }

    // ── Bind data from /api/company/dashboard ────────────────────────────────

    private void bindDashboard(JsonObject body) {
        // Extract nested data key (Laravel sendResponse wraps in "data")
        JsonObject data = body.has("data") && body.get("data").isJsonObject()
                ? body.getAsJsonObject("data") : body;

        // Company profile / name
        JsonObject profile = Json.obj(data, "company_profile");
        if (profile == null) profile = Json.obj(data, "company");
        if (profile == null) profile = Json.obj(data, "profile");
        String companyName = null;
        boolean verified = false;
        String logoUrl = null;

        if (profile != null) {
            companyName = Json.str(profile, null, "company_name", "name");
            String verStatus = Json.str(profile, "", "verification_status");
            verified = "verified".equalsIgnoreCase(verStatus)
                    || "approved".equalsIgnoreCase(verStatus)
                    || (profile.has("is_verified") && !profile.get("is_verified").isJsonNull()
                    && profile.get("is_verified").getAsBoolean());
            logoUrl = AvatarLoader.extractUrl(null, profile);
        }
        if (companyName == null) {
            companyName = Json.str(data, null, "company_name", "name");
        }
        if (companyName == null) companyName = SessionManager.get(this).getName();
        bindHeader(companyName, verified, logoUrl);

        // Stats (check nested "stats" object from CompanyApiController@dashboard first)
        JsonObject stats = Json.obj(data, "stats");
        JsonObject statSrc = stats != null ? stats : data;
        int postsCount = Json.integer(statSrc, 0,
                "active_posts", "total_posts", "posts_count", "active_listings", "listings_count");
        int totalApplicants = Json.integer(statSrc, 0,
                "total_applicants", "applicants_count");
        int pendingCount = Json.integer(statSrc, 0,
                "pending_review", "pending_count", "pending_reviews", "pending");
        int acceptedCount = Json.integer(statSrc, 0,
                "accepted", "accepted_count", "offers_accepted");

        bindStat(R.id.statListings, "ACTIVE JOBS",
                String.valueOf(postsCount), "● Vetted & Published",
                R.drawable.ic_work, R.color.tint_indigo_bg, R.color.tint_indigo, 0);
        bindStat(R.id.statApplicants, "TOTAL APPLICANTS",
                String.valueOf(totalApplicants), "↗ Received candidacies",
                R.drawable.ic_group, R.color.tint_indigo_bg, R.color.tint_indigo, 0);
        bindStat(R.id.statPending, "PENDING REVIEWS",
                String.valueOf(pendingCount), "Action req.",
                R.drawable.ic_applications, R.color.tint_amber_bg, R.color.tint_amber, 0);
        bindStat(R.id.statOffers, "OFFERS ACCEPTED",
                String.valueOf(acceptedCount), "Confirmed",
                R.drawable.ic_check_circle, R.color.tint_green_bg,
                0, 0);  // green icon = colorPrimary default

        // Recent applicants
        JsonArray applicants = null;
        if (data.has("recent_applicants") && data.get("recent_applicants").isJsonArray()) {
            applicants = data.getAsJsonArray("recent_applicants");
        } else if (data.has("recent_applications") && data.get("recent_applications").isJsonArray()) {
            applicants = data.getAsJsonArray("recent_applications");
        } else if (data.has("applicants") && data.get("applicants").isJsonArray()) {
            applicants = data.getAsJsonArray("applicants");
        }

        containerSubmissions.removeAllViews();
        showLoading(false);

        if (applicants == null || applicants.size() == 0) {
            emptyContainer.setVisibility(View.VISIBLE);
            tvNewBadge.setVisibility(View.GONE);
        } else {
            emptyContainer.setVisibility(View.GONE);
            int pendingNew = 0;
            int limit = Math.min(applicants.size(), 5);
            for (int i = 0; i < limit; i++) {
                JsonElement el = applicants.get(i);
                if (!el.isJsonObject()) continue;
                JsonObject app = el.getAsJsonObject();
                if ("pending".equalsIgnoreCase(Json.str(app, null, "status"))) pendingNew++;
                addSubmissionCard(app);
            }
            if (pendingNew > 0) {
                tvNewBadge.setText(pendingNew + " new");
                tvNewBadge.setVisibility(View.VISIBLE);
            } else {
                tvNewBadge.setVisibility(View.GONE);
            }
        }
    }

    // ── Header binding ────────────────────────────────────────────────────────

    private void bindHeader(String name, boolean verified, String logoUrl) {
        TextView tvName = findViewById(R.id.tvCompanyName);
        TextView tvVerified = findViewById(R.id.tvVerifiedBadge);
        TextView tvInitials = findViewById(R.id.tvCompanyInitials);
        ImageView ivAvatar = findViewById(R.id.ivCompanyAvatar);

        if (name != null && !name.isEmpty()) {
            tvName.setText(name);
            // Build initials (up to 2 chars)
            String[] parts = name.trim().split("\\s+");
            String initials = parts.length > 1
                    ? "" + parts[0].charAt(0) + parts[1].charAt(0)
                    : name.substring(0, Math.min(2, name.length()));
            tvInitials.setText(initials.toUpperCase(Locale.ROOT));
        }
        tvVerified.setVisibility(verified ? View.VISIBLE : View.GONE);
        AvatarLoader.bind(logoUrl, ivAvatar, tvInitials);
    }

    // ── Stat card binding ─────────────────────────────────────────────────────

    private void bindStat(int includeId, String label, String value, String sub,
                          @DrawableRes int icon,
                          @ColorRes int boxColor, @ColorRes int iconColor,
                          @ColorRes int valueColor) {
        View root = findViewById(includeId);
        ((TextView) root.findViewById(R.id.tvLabel)).setText(label);

        TextView tvValue = root.findViewById(R.id.tvValue);
        tvValue.setText(value);
        if (valueColor != 0) {
            tvValue.setTextColor(ContextCompat.getColor(this, valueColor));
        }

        TextView tvSub = root.findViewById(R.id.tvSub);
        tvSub.setText(sub);
        if ("Confirmed".equals(sub)) {
            tvSub.setTextColor(MaterialColors.getColor(root,
                    androidx.appcompat.R.attr.colorPrimary));
            tvSub.setTypeface(tvSub.getTypeface(), android.graphics.Typeface.BOLD);
        }

        ImageView iv = root.findViewById(R.id.icon);
        iv.setImageResource(icon);
        iv.setColorFilter(iconColor == 0
                ? MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary)
                : ContextCompat.getColor(this, iconColor));

        ((MaterialCardView) root.findViewById(R.id.iconBox))
                .setCardBackgroundColor(ContextCompat.getColor(this, boxColor));
    }

    // ── Applicant card inflation ───────────────────────────────────────────────

    private void addSubmissionCard(JsonObject app) {
        // Resolve student name
        JsonObject student = Json.obj(app, "student_profile");
        if (student == null) student = Json.obj(app, "student");
        JsonObject studentUser = student != null ? Json.obj(student, "user") : null;
        String name = Json.str(studentUser, null, "name");
        if (name == null) name = Json.str(student, null, "full_name", "name");
        if (name == null) name = Json.str(app, null, "student_name", "applicant_name", "name");
        if (name == null) name = "Applicant";

        // University / year
        String university = Json.str(student, null, "university", "school", "institution");
        String year = Json.str(student, null, "year", "study_year", "current_year");
        String universityYear = (university != null ? university : "")
                + (year != null ? " Year " + year : "");
        if (universityYear.trim().isEmpty()) universityYear = "Student";

        // Role / internship position
        JsonObject post = Json.obj(app, "internship_post");
        if (post == null) post = Json.obj(app, "post");
        if (post == null) post = Json.obj(app, "internship");
        String role = post != null ? Json.str(post, null, "title", "position") : null;
        if (role == null) role = Json.str(app, null, "post_title", "position", "role");
        if (role == null) role = "Internship Position";

        // Applied time
        String createdAt = Json.str(app, null, "created_at", "applied_at");
        String timeLabel = formatRelativeTime(createdAt);

        // Status
        String status = Json.str(app, null, "status");
        if (status == null) status = "pending";

        // Avatar
        String avatarUrl = AvatarLoader.extractUrl(studentUser, student);

        // Application ID
        int appId = app.has("id") ? app.get("id").getAsInt() : 0;
        String companyName = name; // capture for lambda

        // Inflate
        View item = LayoutInflater.from(this)
                .inflate(R.layout.item_submission, containerSubmissions, false);

        // Avatar / initials
        String[] nameParts = name.trim().split("\\s+");
        String initials = nameParts.length > 1
                ? "" + nameParts[0].charAt(0) + nameParts[1].charAt(0)
                : name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.ROOT);
        TextView tvInitials = item.findViewById(R.id.tvInitials);
        tvInitials.setText(initials.toUpperCase(Locale.ROOT));
        ImageView ivAvatar = item.findViewById(R.id.ivAvatar);
        AvatarLoader.bind(avatarUrl, ivAvatar, tvInitials);

        ((TextView) item.findViewById(R.id.tvName)).setText(name);
        ((TextView) item.findViewById(R.id.tvUniversity)).setText(universityYear);
        ((TextView) item.findViewById(R.id.tvRole)).setText(role);
        ((TextView) item.findViewById(R.id.tvTime)).setText(timeLabel);

        // Status badge colour
        TextView tvStatus = item.findViewById(R.id.tvStatus);
        applyStatusBadge(tvStatus, status);

        // Action button
        MaterialButton btn = item.findViewById(R.id.btnAction);
        applyActionButton(btn, status);

        View.OnClickListener openReview = v ->
                ReviewApplicantBottomSheet.show(getSupportFragmentManager(), app, this::loadDashboard);
        btn.setOnClickListener(openReview);
        item.setOnClickListener(openReview);

        containerSubmissions.addView(item);
    }

    // ── Status badge styling ──────────────────────────────────────────────────

    private void applyStatusBadge(TextView tv, String status) {
        int bgColor, textColor;
        String label;
        switch (status.toLowerCase(Locale.ROOT)) {
            case "shortlisted":
                bgColor = R.color.tint_indigo_bg;
                textColor = R.color.tint_indigo;
                label = "Shortlisted";
                break;
            case "interview":
            case "interview_scheduled":
            case "interviewed":
                bgColor = R.color.tint_indigo_bg;
                textColor = R.color.tint_indigo;
                label = "Interview";
                break;
            case "accepted":
                bgColor = R.color.tint_green_bg;
                textColor = 0; // primary green
                label = "Accepted";
                break;
            case "rejected":
                bgColor = R.color.tint_red_bg;
                textColor = R.color.tint_red;
                label = "Rejected";
                break;
            case "under_review":
                bgColor = R.color.tint_amber_bg;
                textColor = R.color.tint_amber;
                label = "Under Review";
                break;
            default: // pending
                bgColor = R.color.tint_amber_bg;
                textColor = R.color.tint_amber;
                label = "Pending";
                break;
        }
        tv.setText(label);
        tv.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, bgColor)));
        if (textColor == 0) {
            tv.setTextColor(MaterialColors.getColor(tv,
                    androidx.appcompat.R.attr.colorPrimary));
        } else {
            tv.setTextColor(ContextCompat.getColor(this, textColor));
        }
    }

    // ── Action button styling ─────────────────────────────────────────────────

    private void applyActionButton(MaterialButton btn, String status) {
        switch (status.toLowerCase(Locale.ROOT)) {
            case "shortlisted":
                // Dark filled "Schedule" button
                btn.setText("Schedule");
                btn.setIconResource(R.drawable.ic_event);
                btn.setBackgroundTintList(
                        ColorStateList.valueOf(Color.parseColor("#FF0F172A")));
                btn.setTextColor(Color.WHITE);
                btn.setIconTint(ColorStateList.valueOf(Color.WHITE));
                break;
            case "interview":
            case "interview_scheduled":
            case "interviewed":
                // Outline "Details" button
                btn.setText("Details");
                btn.setIconResource(R.drawable.ic_visibility);
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                btn.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
                btn.setIconTint(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo)));
                break;
            default:
                // Outline "Review CV" button (pending / under_review)
                btn.setText("Review CV");
                btn.setIconResource(R.drawable.ic_visibility);
                int onSurface = MaterialColors.getColor(btn,
                        com.google.android.material.R.attr.colorOnSurface);
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                btn.setTextColor(onSurface);
                btn.setIconTint(ColorStateList.valueOf(onSurface));
                break;
        }
    }

    // ── Relative time helper ──────────────────────────────────────────────────

    private String formatRelativeTime(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "Applied recently";
        try {
            Instant then = Instant.parse(isoDate.replace(" ", "T")
                    .replaceAll("(\\d)(\\+|Z).*", "$1Z"));
            long hours = ChronoUnit.HOURS.between(then, Instant.now());
            if (hours < 1) return "Applied just now";
            if (hours < 24) return "Applied " + hours + "h ago";
            long days = hours / 24;
            if (days == 1) return "Applied 1d ago";
            return "Applied " + days + "d ago";
        } catch (Exception e) {
            return "Applied recently";
        }
    }

    // ── Static placeholders (shown on API error) ──────────────────────────────

    private void bindStaticPlaceholders() {
        bindStat(R.id.statListings, "ACTIVE JOBS", "—", "Unavailable",
                R.drawable.ic_work, R.color.tint_indigo_bg, R.color.tint_indigo, 0);
        bindStat(R.id.statApplicants, "TOTAL APPLICANTS", "—", "Unavailable",
                R.drawable.ic_group, R.color.tint_indigo_bg, R.color.tint_indigo, 0);
        bindStat(R.id.statPending, "PENDING REVIEWS", "—", "Unavailable",
                R.drawable.ic_applications, R.color.tint_amber_bg, R.color.tint_amber, 0);
        bindStat(R.id.statOffers, "OFFERS ACCEPTED", "—", "Unavailable",
                R.drawable.ic_check_circle, R.color.tint_green_bg, 0, 0);
        emptyContainer.setVisibility(View.VISIBLE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
        if (containerSubmissions != null) containerSubmissions.setVisibility(View.VISIBLE);
        if (scrollRefresh != null) {
            scrollRefresh.setRefreshing(show);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
