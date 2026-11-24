package com.example.internshipms;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.ColorRes;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import retrofit2.Call;

public class StudentDashboardActivity extends AppCompatActivity {

    private static final int MAX_FEATURED = 5;

    private StudentRepository repository;
    private Call<JsonObject> dashboardCall;
    private Call<JsonObject> unreadCall;
    private final Set<Integer> appliedPostIds = new HashSet<>();
    private JsonObject lastDashboardData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repository = new StudentRepository(this);
        setGreeting(session.getName());

        // Stat cards matching StudentApiController@dashboard:
        // total_applications, pending, shortlisted, accepted
        bindStat(R.id.statApps, "TOTAL APPS", "–", "Submitted positions",
                R.drawable.ic_send, R.color.tint_green_bg, 0, false);
        bindStat(R.id.statReview, "PENDING", "–", "Awaiting review",
                R.drawable.ic_hourglass, R.color.tint_amber_bg, R.color.tint_amber, false);
        bindStat(R.id.statInterviews, "SHORTLISTED", "–", "Shortlisted roles",
                R.drawable.ic_event, R.color.tint_indigo_bg, R.color.tint_indigo, false);
        bindStat(R.id.statOffers, "ACCEPTED", "–", "Confirmed place",
                R.drawable.ic_check_circle, R.color.tint_green_bg, 0, true);

        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                Intent i = new Intent(this, StudentExploreActivity.class);
                i.putExtra("search_query", etSearch.getText().toString().trim());
                startActivity(i);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });

        findViewById(R.id.cardHeaderAvatar).setOnClickListener(v ->
                Navigator.switchTab(this, StudentProfileActivity.class, false));
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));
        findViewById(R.id.btnSearchFilter).setOnClickListener(v ->
                FilterCohortsBottomSheet.show(getSupportFragmentManager(), 0,
                        minStipend -> Navigator.switchTab(this, StudentExploreActivity.class, false)));
        findViewById(R.id.tvViewAll).setOnClickListener(v ->
                Navigator.switchTab(this, StudentApplicationsActivity.class, false));
        findViewById(R.id.tvSeeMore).setOnClickListener(v ->
                Navigator.switchTab(this, StudentExploreActivity.class, false));

        com.example.internshipms.view.PullRefreshScrollView scrollRefresh =
                findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadDashboard);
        }

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                Navigator.switchTab(this, StudentProfileActivity.class, false);
                return false;
            }
            if (id == R.id.nav_explore) {
                Navigator.switchTab(this, StudentExploreActivity.class, false);
                return false;
            }
            if (id == R.id.nav_applications) {
                Navigator.switchTab(this, StudentApplicationsActivity.class, false);
                return false;
            }
            if (id == R.id.nav_messages) {
                Navigator.switchTab(this, ConversationsActivity.class, false);
                return false;
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SessionManager session = SessionManager.get(this);
        setGreeting(session.getName());
        com.example.internshipms.network.AvatarLoader.bind(
                session.getAvatarUrl(),
                findViewById(R.id.ivHeaderAvatar),
                findViewById(R.id.ivHeaderAvatarIcon));
        if (repository != null) loadDashboard();
    }

    // ------------------------------------------------------------------ API

    public void loadDashboard() {
        if (dashboardCall != null) dashboardCall.cancel();
        setLoading(true);
        dashboardCall = repository.dashboard(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                setLoading(false);
                lastDashboardData = Json.data(body);
                renderDashboard(lastDashboardData);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                setLoading(false);
                handleError(error);
            }
        });

        repository.conversations(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonArray arr = Json.list(body, "conversations", "data");
                if (com.example.internshipms.network.AvatarLoader
                        .syncCompanyLogosFromConversations(arr)
                        && lastDashboardData != null) {
                    renderDashboard(lastDashboardData);
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });

        if (unreadCall != null) unreadCall.cancel();
        unreadCall = repository.unreadNotificationCount(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject d = Json.data(body);
                int count = Json.integer(d, 0, "unread_count", "count", "unread");
                if (count == 0 && body.has("unread_count")) {
                    count = Json.integer(body, 0, "unread_count");
                }
                findViewById(R.id.dotUnread).setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                findViewById(R.id.dotUnread).setVisibility(View.GONE);
            }
        });

        repository.profile(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject d = Json.data(body);
                JsonObject user = Json.obj(d, "user");
                JsonObject student = Json.obj(d, "student");
                SessionManager s = SessionManager.get(StudentDashboardActivity.this);
                String name = Json.str(user, null, "name");
                String avatarUrl = com.example.internshipms.network.AvatarLoader.extractUrl(user, student);
                if (name != null) {
                    s.setName(name);
                    setGreeting(name);
                }
                if (avatarUrl != null) {
                    s.setAvatarUrl(avatarUrl);
                    com.example.internshipms.network.AvatarLoader.bind(
                            avatarUrl,
                            findViewById(R.id.ivHeaderAvatar),
                            findViewById(R.id.ivHeaderAvatarIcon));
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    private void handleError(ApiError error) {
        if (error.code == 401) {
            SessionManager.get(this).logout();
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            Navigator.openLogin(this);
            return;
        }
        Snackbar.make(findViewById(R.id.main), error.message, Snackbar.LENGTH_INDEFINITE)
                .setAction("Retry", v -> loadDashboard())
                .show();
    }

    private void setLoading(boolean loading) {
        View bar = findViewById(R.id.progressHome);
        if (bar != null) bar.setVisibility(View.GONE);
        com.example.internshipms.view.PullRefreshScrollView scrollRefresh =
                findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) {
            scrollRefresh.setRefreshing(loading);
        }
    }

    // ------------------------------------------------------------- Rendering

    private void renderDashboard(JsonObject data) {
        // ---- Stats (exact keys from StudentApiController@dashboard) ----
        JsonObject stats = Json.obj(data, "stats");
        if (stats == null) stats = data;
        setStatValue(R.id.statApps, Json.integer(stats, 0, "total_applications", "total"));
        setStatValue(R.id.statReview, Json.integer(stats, 0, "pending"));
        setStatValue(R.id.statInterviews, Json.integer(stats, 0, "shortlisted"));
        setStatValue(R.id.statOffers, Json.integer(stats, 0, "accepted"));

        // ---- Recent Applications (recent_applications with internshipPost.companyProfile) ----
        appliedPostIds.clear();
        JsonArray recentApps = Json.arr(data, "recent_applications", "applications");
        ViewGroup appsContainer = findViewById(R.id.containerRecentApps);
        appsContainer.removeAllViews();
        int appsShown = 0;
        for (JsonElement e : recentApps) {
            if (!e.isJsonObject()) continue;
            JsonObject app = e.getAsJsonObject();
            int postId = Json.integer(app, 0, "internship_post_id");
            if (postId > 0) appliedPostIds.add(postId);
            addRecentApplication(app, appsContainer);
            appsShown++;
        }
        findViewById(R.id.tvTrackerEmpty).setVisibility(appsShown == 0 ? View.VISIBLE : View.GONE);

        // ---- Featured / Smart Recommended Posts (recommended_posts / featured_posts) ----
        JsonArray posts = Json.arr(data, "recommended_posts", "featured_posts", "posts");
        ViewGroup jobsContainer = findViewById(R.id.containerJobs);
        jobsContainer.removeAllViews();
        int jobsShown = 0;
        for (JsonElement e : posts) {
            if (!e.isJsonObject()) continue;
            addJob(e.getAsJsonObject(), jobsShown);
            if (++jobsShown >= MAX_FEATURED) break;
        }
        findViewById(R.id.tvJobsEmpty).setVisibility(jobsShown == 0 ? View.VISIBLE : View.GONE);
    }

    private void addRecentApplication(JsonObject app, ViewGroup container) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_recent_application, container, false);

        int appId = Json.integer(app, 0, "id");
        JsonObject post = Json.obj(app, "internship_post", "post");
        String title = Json.str(post, Json.str(app, "Internship Position", "title"), "title");
        String company = companyName(post, app);
        String location = Json.str(post, "", "location");
        String status = Json.str(app, "pending", "status").toLowerCase(Locale.ROOT);
        String label = statusLabel(status);
        String appliedAt = Json.str(app, null, "applied_at", "created_at");

        TextView tvInitials = item.findViewById(R.id.tvInitials);
        ImageView ivCompanyLogo = item.findViewById(R.id.ivCompanyLogo);
        tvInitials.setText(initials(company));
        String logoUrl = com.example.internshipms.network.AvatarLoader
                .extractCompanyLogo(post, app, company);
        com.example.internshipms.network.AvatarLoader.bind(logoUrl, ivCompanyLogo, tvInitials);

        ((TextView) item.findViewById(R.id.tvAppTitle)).setText(title);
        ((TextView) item.findViewById(R.id.tvAppCompany)).setText(
                location.isEmpty() ? company : company + " • " + location);
        ((TextView) item.findViewById(R.id.tvAppDate)).setText(
                "Applied " + relativeTime(appliedAt));

        TextView tvStatus = item.findViewById(R.id.tvAppStatus);
        tvStatus.setText(label);
        styleStatusPill(tvStatus, status);

        item.setOnClickListener(v ->
                ApplicationDetailActivity.launch(this, appId, title, company, label));
        container.addView(item);
    }

    private void styleStatusPill(TextView pill, String status) {
        int bgRes;
        int textColor;
        switch (status) {
            case "accepted":
            case "approved":
                bgRes = R.color.tint_green_bg;
                textColor = MaterialColors.getColor(pill, androidx.appcompat.R.attr.colorPrimary);
                break;
            case "shortlisted":
            case "interview":
            case "interviewed":
            case "interviewing":
            case "interview_scheduled":
                bgRes = R.color.tint_indigo_bg;
                textColor = ContextCompat.getColor(this, R.color.tint_indigo);
                break;
            case "rejected":
                bgRes = R.color.tint_red_bg;
                textColor = ContextCompat.getColor(this, R.color.tint_red);
                break;
            default:
                bgRes = R.color.tint_amber_bg;
                textColor = ContextCompat.getColor(this, R.color.tint_amber);
                break;
        }
        pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, bgRes)));
        pill.setTextColor(textColor);
    }

    private void addJob(JsonObject post, int index) {
        ViewGroup container = findViewById(R.id.containerJobs);
        View item = LayoutInflater.from(this).inflate(R.layout.item_job, container, false);

        int postId = Json.integer(post, 0, "id");
        int compId = companyId(post, post);
        String title = Json.str(post, "Internship Position", "title");
        String company = companyName(post, post);
        String location = Json.str(post, "Cambodia", "location");
        String badgeText = formatPostBadge(post);
        String duration = formatTypeOrCategory(post);
        String deadline = formatDeadlineDate(Json.str(post, "—", "deadline"));
        String description = Json.str(post, "", "description");
        String requirements = Json.str(post, "", "requirements");
        boolean hasApplied = appliedPostIds.contains(postId)
                || (post.has("has_applied") && !post.get("has_applied").isJsonNull()
                && post.get("has_applied").getAsBoolean());
        boolean isBookmarked = post.has("is_bookmarked")
                && !post.get("is_bookmarked").isJsonNull()
                && post.get("is_bookmarked").getAsBoolean();

        TextView tvSmartMatch = item.findViewById(R.id.tvSmartMatch);
        String matchLabel = formatSmartMatchBadge(post);
        if (matchLabel != null) {
            tvSmartMatch.setVisibility(View.VISIBLE);
            tvSmartMatch.setText(matchLabel);
        } else {
            tvSmartMatch.setVisibility(View.GONE);
        }

        boolean indigo = index % 2 == 0;
        ((MaterialCardView) item.findViewById(R.id.jobIconBox)).setCardBackgroundColor(
                ContextCompat.getColor(this, indigo ? R.color.tint_indigo_bg : R.color.tint_green_bg));
        ImageView ivJob = item.findViewById(R.id.ivJobIcon);
        TextView tvInitial = item.findViewById(R.id.tvJobInitial);
        ImageView ivCompanyLogo = item.findViewById(R.id.ivCompanyLogo);
        ivJob.setVisibility(View.GONE);
        tvInitial.setVisibility(View.VISIBLE);
        tvInitial.setText(initials(company));
        tvInitial.setTextColor(indigo
                ? ContextCompat.getColor(this, R.color.tint_indigo)
                : MaterialColors.getColor(item, androidx.appcompat.R.attr.colorPrimary));
        String logoUrl = com.example.internshipms.network.AvatarLoader
                .extractCompanyLogo(post, post, company);
        com.example.internshipms.network.AvatarLoader.bind(logoUrl, ivCompanyLogo, tvInitial);

        ((TextView) item.findViewById(R.id.tvJobTitle)).setText(title);
        TextView tvJobCompany = item.findViewById(R.id.tvJobCompany);
        tvJobCompany.setText(company + " • " + location);
        if (compId > 0) {
            View.OnClickListener openCompany = v ->
                    CompanyProfileBottomSheet.show(getSupportFragmentManager(), compId, company, location);
            tvJobCompany.setOnClickListener(openCompany);
            item.findViewById(R.id.jobIconBox).setOnClickListener(openCompany);
        }
        ((TextView) item.findViewById(R.id.tvSalary)).setText(badgeText);

        MaterialButton btnApply = item.findViewById(R.id.btnApply);
        if (hasApplied) {
            btnApply.setText("Applied ✓");
            btnApply.setEnabled(false);
        } else {
            btnApply.setText("Apply Now");
            btnApply.setEnabled(true);
            btnApply.setOnClickListener(v ->
                    ApplyInternshipBottomSheet.show(getSupportFragmentManager(),
                            postId, title, company, location, this::loadDashboard));
        }

        item.setOnClickListener(v -> InternshipDetailActivity.launch(
                this, postId, title, company, location, duration, badgeText, deadline,
                description, requirements, hasApplied, isBookmarked, compId));
        container.addView(item);
    }

    // --------------------------------------------------------------- Helpers

    private void setGreeting(@Nullable String name) {
        String first = name == null || name.trim().isEmpty() ? "Student" : name.trim().split("\\s+")[0];
        ((TextView) findViewById(R.id.tvGreeting)).setText("Hello, " + first + " 👋");
    }

    private void setStatValue(int includeId, int value) {
        ((TextView) findViewById(includeId).findViewById(R.id.tvValue)).setText(String.valueOf(value));
    }

    public static String companyName(@Nullable JsonObject post, @Nullable JsonObject fallback) {
        JsonObject company = Json.obj(post, "company_profile", "company");
        String name = Json.str(company, null, "company_name", "name");
        if (name == null) name = Json.str(post, null, "company_name");
        if (name == null) name = Json.str(fallback, "Company", "company_name", "company");
        return name;
    }

    public static int companyId(@Nullable JsonObject post, @Nullable JsonObject fallback) {
        JsonObject company = Json.obj(post, "company_profile", "company");
        int id = Json.integer(company, 0, "id");
        if (id == 0) id = Json.integer(post, 0, "company_profile_id", "company_id");
        if (id == 0 && fallback != null) {
            JsonObject compFallback = Json.obj(fallback, "company_profile", "company");
            id = Json.integer(compFallback, 0, "id");
            if (id == 0) id = Json.integer(fallback, 0, "company_profile_id", "company_id");
        }
        return id;
    }

    @Nullable
    public static String formatSmartMatchBadge(@Nullable JsonObject post) {
        if (post == null) return null;
        boolean hasMatch = post.has("has_smart_match")
                && !post.get("has_smart_match").isJsonNull()
                && post.get("has_smart_match").getAsBoolean();
        boolean majorMatch = post.has("is_major_match")
                && !post.get("is_major_match").isJsonNull()
                && post.get("is_major_match").getAsBoolean();
        JsonArray skillsArr = Json.arr(post, "matched_skills");

        if (!hasMatch && !majorMatch && skillsArr.size() == 0) return null;

        StringBuilder sb = new StringBuilder("✨ ");
        if (majorMatch) {
            sb.append("Major Match");
        }
        if (skillsArr.size() > 0) {
            if (majorMatch) sb.append(" • ");
            else sb.append("Matched: ");
            int count = 0;
            for (JsonElement el : skillsArr) {
                if (!el.isJsonPrimitive()) continue;
                String s = el.getAsString().trim();
                if (s.isEmpty()) continue;
                if (count > 0) sb.append(", ");
                sb.append(s);
                if (++count >= 3) break;
            }
        }
        return sb.length() > 3 ? sb.toString() : "✨ Recommended for You";
    }

    public static boolean isWorkMode(@Nullable String s) {
        if (s == null) return false;
        String lower = s.trim().toLowerCase(Locale.ROOT);
        return lower.contains("onsite")
                || lower.contains("on-site")
                || lower.contains("on site")
                || lower.contains("remote")
                || lower.contains("remode")
                || lower.contains("hybrid");
    }

    public static String formatPostBadge(JsonObject post) {
        String stipend = Json.str(post, null, "stipend", "salary", "allowance", "stipend_amount");
        if (stipend != null && !isWorkMode(stipend)) {
            if (stipend.matches("^\\d+(\\.\\d+)?$")) {
                return "$" + stipend.replaceAll("\\.0+$", "") + "/mo";
            }
            return stipend;
        }
        String category = Json.str(post, null, "category");
        if (isWorkMode(category)) category = null;
        String deadline = formatDeadlineDate(Json.str(post, null, "deadline"));
        if (category != null && deadline != null && !"—".equals(deadline)) {
            return category + " • Due " + deadline;
        }
        if (category != null) return category;
        if (deadline != null && !"—".equals(deadline)) return "Due " + deadline;
        return "Open";
    }

    public static String formatTypeOrCategory(JsonObject post) {
        String duration = Json.str(post, null, "duration", "duration_text");
        if (duration != null && !isWorkMode(duration)) return duration;
        String category = Json.str(post, null, "category");
        if (category != null && !isWorkMode(category)) return category;
        return "Internship";
    }

    public static String capitalizeWords(String s) {
        if (s == null || s.isEmpty()) return "";
        String[] parts = s.replace('_', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }

    public static String statusLabel(String status) {
        if (status == null) return "Pending";
        switch (status.toLowerCase(Locale.ROOT)) {
            case "reviewed":
            case "reviewing":
            case "under_review":
                return "Under Review";
            case "shortlisted":
                return "Shortlisted";
            case "interview":
            case "interviewed":
            case "interviewing":
            case "interview_scheduled":
                return "Interviewed";
            case "accepted":
            case "approved":
                return "Accepted";
            case "rejected":
                return "Rejected";
            case "withdrawn":
                return "Withdrawn";
            default:
                return "Pending";
        }
    }

    public static String formatDateTime(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) return "Date & Time TBD";
        Date d = parseDate(iso);
        if (d == null) return iso;
        return new SimpleDateFormat("EEE, MMM dd, yyyy 'at' h:mm a", Locale.US).format(d);
    }

    @Nullable
    public static Date parseDate(@Nullable String iso) {
        if (iso == null) return null;
        try {
            return Date.from(OffsetDateTime.parse(iso).toInstant());
        } catch (Exception ignored) {
        }
        try {
            return Date.from(Instant.parse(iso));
        } catch (Exception ignored) {
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso);
        } catch (Exception ignored) {
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String relativeTime(@Nullable String iso) {
        Date d = parseDate(iso);
        if (d == null) return "recently";
        return DateUtils.getRelativeTimeSpanString(d.getTime(), System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString().toLowerCase(Locale.ROOT);
    }

    public static String formatDeadlineDate(@Nullable String iso) {
        if (iso == null) return "—";
        Date d = parseDate(iso);
        if (d == null) return iso;
        return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(d);
    }

    public static String initials(String text) {
        String[] words = text.trim().split("[\\s/(]+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "C";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    private void bindStat(int includeId, String label, String value, String sub,
                          @DrawableRes int icon, @ColorRes int boxColor,
                          @ColorRes int iconColor, boolean highlightSubGreen) {
        View root = findViewById(includeId);
        ((TextView) root.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) root.findViewById(R.id.tvValue)).setText(value);
        TextView tvSub = root.findViewById(R.id.tvSub);
        tvSub.setText(sub);
        int primaryGreen = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary);
        if (highlightSubGreen) {
            tvSub.setTextColor(primaryGreen);
            tvSub.setTypeface(tvSub.getTypeface(), android.graphics.Typeface.BOLD);
        }
        ImageView iv = root.findViewById(R.id.icon);
        iv.setImageResource(icon);
        iv.setColorFilter(iconColor == 0 ? primaryGreen : ContextCompat.getColor(this, iconColor));
        ((MaterialCardView) root.findViewById(R.id.iconBox))
                .setCardBackgroundColor(ContextCompat.getColor(this, boxColor));
        root.setOnClickListener(v ->
                Navigator.switchTab(this, StudentApplicationsActivity.class, false));
    }

    @Override
    protected void onDestroy() {
        if (dashboardCall != null) dashboardCall.cancel();
        if (unreadCall != null) unreadCall.cancel();
        super.onDestroy();
    }
}
