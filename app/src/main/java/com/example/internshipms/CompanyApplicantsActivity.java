package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompanyApplicantsActivity extends AppCompatActivity {

    private static final String EXTRA_POST_ID = "extra_post_id";
    private static final String EXTRA_POST_TITLE = "extra_post_title";

    private static final String FILTER_ALL = "all";
    private static final String FILTER_PENDING = "pending";
    private static final String FILTER_REVIEW = "review";
    private static final String FILTER_SHORTLISTED = "shortlisted";
    private static final String FILTER_INTERVIEW = "interview";
    private static final String FILTER_ACCEPTED = "accepted";
    private static final String FILTER_DECLINED = "declined";

    public static void launch(Context context) {
        Intent intent = new Intent(context, CompanyApplicantsActivity.class);
        context.startActivity(intent);
    }

    public static void launchForPost(Context context, int postId, @Nullable String postTitle) {
        Intent intent = new Intent(context, CompanyApplicantsActivity.class);
        intent.putExtra(EXTRA_POST_ID, postId);
        if (postTitle != null) intent.putExtra(EXTRA_POST_TITLE, postTitle);
        context.startActivity(intent);
    }

    private CompanyRepository repo;
    private PullRefreshScrollView scrollRefresh;
    private LinearLayout containerApplicants;
    private LinearLayout loadingContainer;
    private LinearLayout emptyContainer;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private View dotUnread;
    private TextView tvRoleFilterLabel;
    private EditText etSearch;

    private MaterialCardView pillAll, pillPending, pillReview, pillShortlisted, pillInterview, pillAccepted, pillDeclined;
    private TextView tvPillAll, tvPillPending, tvPillReview, tvPillShortlisted, tvPillInterview, tvPillAccepted, tvPillDeclined;

    private final List<JsonObject> allApplicants = new ArrayList<>();
    private final List<JsonObject> filteredApplicants = new ArrayList<>();
    private final java.util.Set<Integer> selectedApplicantIds = new java.util.HashSet<>();
    private boolean isSelectionMode = false;

    private View boxBulkActions;
    private TextView tvBulkCount;
    private TextView btnBulkSelectAll;
    private View btnBulkReview, btnBulkShortlist, btnBulkAccept, btnBulkDecline;

    private String currentFilter = FILTER_ALL;
    private String oppTypeFilter = "all"; // "all", "internship", "job"
    private String sortBy = "latest";     // "latest", "gpa_desc"
    private boolean rescheduleOnly = false;
    private int selectedPostId = 0;
    private String selectedPostTitle = "All Roles";
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
        setContentView(R.layout.activity_company_applicants);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repo = new CompanyRepository(this);

        selectedPostId = getIntent().getIntExtra(EXTRA_POST_ID, 0);
        String titleExtra = getIntent().getStringExtra(EXTRA_POST_TITLE);
        if (selectedPostId > 0 && titleExtra != null && !titleExtra.isEmpty()) {
            selectedPostTitle = titleExtra;
        }

        // ── Bind Views ───────────────────────────────────────────────────────
        scrollRefresh = findViewById(R.id.scrollRefresh);
        containerApplicants = findViewById(R.id.containerApplicants);
        loadingContainer = findViewById(R.id.loadingContainer);
        emptyContainer = findViewById(R.id.emptyContainer);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        dotUnread = findViewById(R.id.dotUnread);
        tvRoleFilterLabel = findViewById(R.id.tvRoleFilterLabel);
        etSearch = findViewById(R.id.etSearch);

        pillAll = findViewById(R.id.pillAll);
        pillPending = findViewById(R.id.pillPending);
        pillReview = findViewById(R.id.pillReview);
        pillShortlisted = findViewById(R.id.pillShortlisted);
        pillInterview = findViewById(R.id.pillInterview);
        pillAccepted = findViewById(R.id.pillAccepted);
        pillDeclined = findViewById(R.id.pillDeclined);

        tvPillAll = findViewById(R.id.tvPillAll);
        tvPillPending = findViewById(R.id.tvPillPending);
        tvPillReview = findViewById(R.id.tvPillReview);
        tvPillShortlisted = findViewById(R.id.tvPillShortlisted);
        tvPillInterview = findViewById(R.id.tvPillInterview);
        tvPillAccepted = findViewById(R.id.tvPillAccepted);
        tvPillDeclined = findViewById(R.id.tvPillDeclined);

        boxBulkActions = findViewById(R.id.boxBulkActions);
        tvBulkCount = findViewById(R.id.tvBulkCount);
        btnBulkSelectAll = findViewById(R.id.btnBulkSelectAll);

        updateActiveFilterBadgeStyle();

        // ── Bulk Action Listeners ────────────────────────────────────────────
        if (btnBulkSelectAll != null) {
            btnBulkSelectAll.setOnClickListener(v -> toggleSelectAllFiltered());
        }
        btnBulkReview = findViewById(R.id.btnBulkReview);
        if (btnBulkReview != null) btnBulkReview.setOnClickListener(v -> performBulkStatus("under_review"));
        btnBulkShortlist = findViewById(R.id.btnBulkShortlist);
        if (btnBulkShortlist != null) btnBulkShortlist.setOnClickListener(v -> performBulkStatus("shortlisted"));
        btnBulkAccept = findViewById(R.id.btnBulkAccept);
        if (btnBulkAccept != null) btnBulkAccept.setOnClickListener(v -> confirmAndPerformBulkStatus("accepted", "Accept"));
        btnBulkDecline = findViewById(R.id.btnBulkDecline);
        if (btnBulkDecline != null) btnBulkDecline.setOnClickListener(v -> confirmAndPerformBulkStatus("rejected", "Reject"));
        View btnBulkClear = findViewById(R.id.btnBulkClear);
        if (btnBulkClear != null) {
            btnBulkClear.setOnClickListener(v -> exitSelectionMode());
        }

        // ── Filter & Search Listeners ────────────────────────────────────────
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));

        findViewById(R.id.cardRoleFilter).setOnClickListener(v -> showUnifiedFilterDialog());

        View cardActiveRoleChip = findViewById(R.id.cardActiveRoleChip);
        if (cardActiveRoleChip != null) {
            cardActiveRoleChip.setOnClickListener(v -> showUnifiedFilterDialog());
        }

        View btnClearRoleFilter = findViewById(R.id.btnClearRoleFilter);
        if (btnClearRoleFilter != null) {
            btnClearRoleFilter.setOnClickListener(v -> {
                selectedPostId = 0;
                selectedPostTitle = "All Roles";
                oppTypeFilter = "all";
                sortBy = "latest";
                rescheduleOnly = false;
                updateActiveFilterBadgeStyle();
                updatePillCounts();
                applyFilterAndRender();
            });
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim();
                applyFilterAndRender();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        pillAll.setOnClickListener(v -> selectFilter(FILTER_ALL));
        pillPending.setOnClickListener(v -> selectFilter(FILTER_PENDING));
        pillReview.setOnClickListener(v -> selectFilter(FILTER_REVIEW));
        pillShortlisted.setOnClickListener(v -> selectFilter(FILTER_SHORTLISTED));
        pillInterview.setOnClickListener(v -> selectFilter(FILTER_INTERVIEW));
        pillAccepted.setOnClickListener(v -> selectFilter(FILTER_ACCEPTED));
        pillDeclined.setOnClickListener(v -> selectFilter(FILTER_DECLINED));

        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadApplicants);
        }

        // ── Bottom Navigation ────────────────────────────────────────────────
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_applicants);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_applicants) return true;
            if (id == R.id.nav_home) {
                Navigator.switchTab(this, CompanyDashboardActivity.class, false);
                return false;
            }
            if (id == R.id.nav_listings) {
                Navigator.switchTab(this, CompanyListingsActivity.class, false);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null && nav.getSelectedItemId() != R.id.nav_applicants) {
            nav.setSelectedItemId(R.id.nav_applicants);
        }
        loadApplicants();
    }

    // ── API Calls ────────────────────────────────────────────────────────────

    public void loadApplicants() {
        showLoading(true);
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

        repo.listApplicants(null, null, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonArray arr = Json.list(body, "applicants", "data");
                allApplicants.clear();
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        allApplicants.add(el.getAsJsonObject());
                    }
                }
                showLoading(false);
                updatePillCounts();
                applyFilterAndRender();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                showLoading(false);
                toast(error.message);
                applyFilterAndRender();
            }
        });
    }

    // ── Selection Mode Management ────────────────────────────────────────────

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedApplicantIds.clear();
        updateBulkActionBar();
        applyFilterAndRender();
    }

    // ── Unified Filter & Sort Dialog ─────────────────────────────────────────

    private void showUnifiedFilterDialog() {
        Map<Integer, String> rolesMap = new LinkedHashMap<>();
        rolesMap.put(0, "All Roles");
        for (JsonObject app : allApplicants) {
            JsonObject post = Json.obj(app, "internship_post", "post");
            int pid = post != null
                    ? Json.integer(post, 0, "id")
                    : Json.integer(app, 0, "internship_post_id");
            String title = post != null
                    ? Json.str(post, null, "title")
                    : Json.str(app, null, "post_title");
            if (pid > 0 && title != null && !title.isEmpty()) {
                rolesMap.put(pid, title);
            }
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(22);
        int padV = dp(10);
        root.setPadding(padH, padV, padH, padV);
        scroll.addView(root);

        // 1. Sort Order Section
        TextView lblSort = new TextView(this);
        lblSort.setText("SORT CANDIDATES BY");
        lblSort.setTextSize(11f);
        lblSort.setTypeface(null, Typeface.BOLD);
        lblSort.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
        root.addView(lblSort);

        RadioGroup rgSort = new RadioGroup(this);
        rgSort.setOrientation(RadioGroup.VERTICAL);
        RadioButton rbLatest = new RadioButton(this);
        rbLatest.setId(View.generateViewId());
        rbLatest.setText("Newest Applied First (Latest)");
        rbLatest.setChecked(!"gpa_desc".equals(sortBy));
        RadioButton rbGpa = new RadioButton(this);
        rbGpa.setId(View.generateViewId());
        rbGpa.setText("★ Highest GPA First");
        rbGpa.setChecked("gpa_desc".equals(sortBy));
        rgSort.addView(rbLatest);
        rgSort.addView(rbGpa);
        root.addView(rgSort);

        // 2. Opportunity Type Section
        TextView lblOpp = new TextView(this);
        lblOpp.setText("OPPORTUNITY TYPE");
        lblOpp.setTextSize(11f);
        lblOpp.setTypeface(null, Typeface.BOLD);
        lblOpp.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
        lblOpp.setPadding(0, dp(12), 0, 0);
        root.addView(lblOpp);

        RadioGroup rgOpp = new RadioGroup(this);
        rgOpp.setOrientation(RadioGroup.VERTICAL);
        RadioButton rbOppAll = new RadioButton(this);
        rbOppAll.setId(View.generateViewId());
        rbOppAll.setText("All Opportunity Types");
        rbOppAll.setChecked("all".equals(oppTypeFilter));
        RadioButton rbOppIntern = new RadioButton(this);
        rbOppIntern.setId(View.generateViewId());
        rbOppIntern.setText("🎓 Internships Only");
        rbOppIntern.setChecked("internship".equals(oppTypeFilter));
        RadioButton rbOppJob = new RadioButton(this);
        rbOppJob.setId(View.generateViewId());
        rbOppJob.setText("💼 Jobs Only (Full-Time / Part-Time / Contract)");
        rbOppJob.setChecked("job".equals(oppTypeFilter));
        rgOpp.addView(rbOppAll);
        rgOpp.addView(rbOppIntern);
        rgOpp.addView(rbOppJob);
        root.addView(rgOpp);

        // 3. Reschedule Requested Filter
        MaterialCheckBox cbResched = new MaterialCheckBox(this);
        cbResched.setText("⚠ Only candidates requesting interview reschedule");
        cbResched.setChecked(rescheduleOnly);
        root.addView(cbResched);

        // 4. Role / Posting Filter Section
        TextView lblRole = new TextView(this);
        lblRole.setText("FILTER BY JOB / INTERNSHIP POSTING");
        lblRole.setTextSize(11f);
        lblRole.setTypeface(null, Typeface.BOLD);
        lblRole.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
        lblRole.setPadding(0, dp(12), 0, 0);
        root.addView(lblRole);

        RadioGroup rgRole = new RadioGroup(this);
        rgRole.setOrientation(RadioGroup.VERTICAL);
        final Map<Integer, Integer> radioIdToPostId = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : rolesMap.entrySet()) {
            RadioButton rb = new RadioButton(this);
            int rbId = View.generateViewId();
            rb.setId(rbId);
            rb.setText(entry.getValue());
            rb.setChecked(entry.getKey() == selectedPostId);
            rgRole.addView(rb);
            radioIdToPostId.put(rbId, entry.getKey());
        }
        root.addView(rgRole);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Sort & Filter Applicants")
                .setView(scroll)
                .setPositiveButton("Apply", (dialog, which) -> {
                    sortBy = rgSort.getCheckedRadioButtonId() == rbGpa.getId() ? "gpa_desc" : "latest";
                    int checkedOpp = rgOpp.getCheckedRadioButtonId();
                    if (checkedOpp == rbOppIntern.getId()) oppTypeFilter = "internship";
                    else if (checkedOpp == rbOppJob.getId()) oppTypeFilter = "job";
                    else oppTypeFilter = "all";
                    rescheduleOnly = cbResched.isChecked();

                    Integer chosenPostId = radioIdToPostId.get(rgRole.getCheckedRadioButtonId());
                    selectedPostId = chosenPostId != null ? chosenPostId : 0;
                    selectedPostTitle = rolesMap.getOrDefault(selectedPostId, "All Roles");

                    updateActiveFilterBadgeStyle();
                    updatePillCounts();
                    applyFilterAndRender();
                })
                .setNeutralButton("Reset All", (dialog, which) -> {
                    selectedPostId = 0;
                    selectedPostTitle = "All Roles";
                    oppTypeFilter = "all";
                    sortBy = "latest";
                    rescheduleOnly = false;
                    updateActiveFilterBadgeStyle();
                    updatePillCounts();
                    applyFilterAndRender();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateActiveFilterBadgeStyle() {
        View rowActiveBadge = findViewById(R.id.rowActiveRoleBadge);
        View dotActive = findViewById(R.id.dotRoleFilterActive);
        ImageView ivFilter = findViewById(R.id.btnSortFilter);

        List<String> activeParts = new ArrayList<>();
        if (selectedPostId > 0) activeParts.add(selectedPostTitle);
        if ("internship".equals(oppTypeFilter)) activeParts.add("🎓 Internships");
        else if ("job".equals(oppTypeFilter)) activeParts.add("💼 Jobs");
        if ("gpa_desc".equals(sortBy)) activeParts.add("★ Highest GPA");
        if (rescheduleOnly) activeParts.add("⚠ Reschedule Req.");

        boolean hasActiveFilter = !activeParts.isEmpty();
        if (hasActiveFilter) {
            if (rowActiveBadge != null) rowActiveBadge.setVisibility(View.VISIBLE);
            if (dotActive != null) dotActive.setVisibility(View.VISIBLE);
            if (ivFilter != null) {
                ivFilter.setColorFilter(MaterialColors.getColor(ivFilter, androidx.appcompat.R.attr.colorPrimary));
            }
            if (tvRoleFilterLabel != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < activeParts.size(); i++) {
                    if (i > 0) sb.append("  •  ");
                    sb.append(activeParts.get(i));
                }
                tvRoleFilterLabel.setText(sb.toString());
            }
        } else {
            if (rowActiveBadge != null) rowActiveBadge.setVisibility(View.GONE);
            if (dotActive != null) dotActive.setVisibility(View.GONE);
            if (ivFilter != null) {
                ivFilter.setColorFilter(ContextCompat.getColor(this, R.color.nav_unselected));
            }
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void selectFilter(String filter) {
        currentFilter = filter;
        styleFilterPill(pillAll, tvPillAll, FILTER_ALL.equals(filter));
        styleFilterPill(pillPending, tvPillPending, FILTER_PENDING.equals(filter));
        styleFilterPill(pillReview, tvPillReview, FILTER_REVIEW.equals(filter));
        styleFilterPill(pillShortlisted, tvPillShortlisted, FILTER_SHORTLISTED.equals(filter));
        styleFilterPill(pillInterview, tvPillInterview, FILTER_INTERVIEW.equals(filter));
        styleFilterPill(pillAccepted, tvPillAccepted, FILTER_ACCEPTED.equals(filter));
        styleFilterPill(pillDeclined, tvPillDeclined, FILTER_DECLINED.equals(filter));
        applyFilterAndRender();
    }

    private void styleFilterPill(MaterialCardView card, TextView tv, boolean selected) {
        int primaryGreen = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
        if (selected) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_green_bg));
            card.setStrokeColor(ColorStateList.valueOf(primaryGreen));
            tv.setTextColor(primaryGreen);
            tv.setTypeface(null, Typeface.BOLD);
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_card));
            card.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.stroke_soft)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            tv.setTypeface(null, Typeface.NORMAL);
        }
    }

    private boolean matchesSelectedRole(JsonObject app) {
        if (selectedPostId > 0) {
            int pid = Json.integer(app, 0, "internship_post_id");
            JsonObject post = Json.obj(app, "internship_post", "post");
            int postObjId = post != null ? Json.integer(post, 0, "id") : 0;
            if (pid != selectedPostId && postObjId != selectedPostId) return false;
        }
        if (!"all".equals(oppTypeFilter)) {
            JsonObject post = Json.obj(app, "internship_post", "post");
            String oppType = post != null
                    ? Json.str(post, "internship", "opportunity_type")
                    : Json.str(app, "internship", "opportunity_type");
            boolean isIntern = "internship".equalsIgnoreCase(oppType);
            if ("internship".equals(oppTypeFilter) && !isIntern) return false;
            if ("job".equals(oppTypeFilter) && isIntern) return false;
        }
        if (rescheduleOnly) {
            JsonObject interview = StudentApplicationsActivity.extractInterview(app);
            String intStatus = interview != null ? Json.str(interview, "", "status") : "";
            if (!"reschedule_requested".equalsIgnoreCase(intStatus)) return false;
        }
        return true;
    }

    private void toggleSelectAllFiltered() {
        if (filteredApplicants.isEmpty()) return;
        boolean allSelected = true;
        for (JsonObject app : filteredApplicants) {
            int id = Json.integer(app, 0, "id");
            if (id > 0 && !selectedApplicantIds.contains(id)) {
                allSelected = false;
                break;
            }
        }
        if (allSelected) {
            for (JsonObject app : filteredApplicants) {
                selectedApplicantIds.remove(Json.integer(app, 0, "id"));
            }
        } else {
            isSelectionMode = true;
            for (JsonObject app : filteredApplicants) {
                int id = Json.integer(app, 0, "id");
                if (id > 0) selectedApplicantIds.add(id);
            }
        }
        updateBulkActionBar();
        applyFilterAndRender();
    }

    private static String normalizeApplicantStatus(String raw) {
        if (raw == null) return FILTER_PENDING;
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "shortlisted":
                return FILTER_SHORTLISTED;
            case "interview":
            case "interview_scheduled":
            case "interviewed":
                return FILTER_INTERVIEW;
            case "accepted":
                return FILTER_ACCEPTED;
            case "rejected":
            case "withdrawn":
                return FILTER_DECLINED;
            case "reviewed":
            case "under_review":
                return FILTER_REVIEW;
            case "pending":
            default:
                return FILTER_PENDING;
        }
    }

    private void updatePillCounts() {
        int total = 0;
        int pending = 0;
        int review = 0;
        int shortlisted = 0;
        int interview = 0;
        int accepted = 0;
        int declined = 0;

        for (JsonObject app : allApplicants) {
            if (!matchesSelectedRole(app)) continue;
            total++;
            String st = normalizeApplicantStatus(Json.str(app, "pending", "status"));
            switch (st) {
                case FILTER_PENDING:
                    pending++;
                    break;
                case FILTER_REVIEW:
                    review++;
                    break;
                case FILTER_SHORTLISTED:
                    shortlisted++;
                    break;
                case FILTER_INTERVIEW:
                    interview++;
                    break;
                case FILTER_ACCEPTED:
                    accepted++;
                    break;
                case FILTER_DECLINED:
                    declined++;
                    break;
            }
        }

        tvPillAll.setText("All (" + total + ")");
        tvPillPending.setText("Pending (" + pending + ")");
        tvPillReview.setText("Under Review (" + review + ")");
        tvPillShortlisted.setText("Shortlisted (" + shortlisted + ")");
        tvPillInterview.setText("Interviewed (" + interview + ")");
        tvPillAccepted.setText("Accepted (" + accepted + ")");
        tvPillDeclined.setText("Rejected (" + declined + ")");
    }

    private void applyFilterAndRender() {
        filteredApplicants.clear();
        String q = searchQuery.toLowerCase(Locale.ROOT);

        for (JsonObject app : allApplicants) {
            if (!matchesSelectedRole(app)) continue;
            String st = normalizeApplicantStatus(Json.str(app, "pending", "status"));
            if (!FILTER_ALL.equals(currentFilter) && !currentFilter.equals(st)) {
                continue;
            }

            if (!q.isEmpty()) {
                JsonObject student = Json.obj(app, "student_profile", "student");
                JsonObject studentUser = student != null ? Json.obj(student, "user") : null;
                JsonObject post = Json.obj(app, "internship_post", "post");
                String name = Json.str(studentUser, Json.str(student, "", "name"), "name").toLowerCase(Locale.ROOT);
                String uni = Json.str(student, "", "university", "school").toLowerCase(Locale.ROOT);
                String major = Json.str(student, "", "major", "department").toLowerCase(Locale.ROOT);
                String skills = extractSkillsText(student).toLowerCase(Locale.ROOT);
                String role = Json.str(post, "", "title").toLowerCase(Locale.ROOT);
                if (!name.contains(q) && !uni.contains(q) && !major.contains(q)
                        && !skills.contains(q) && !role.contains(q)) {
                    continue;
                }
            }
            filteredApplicants.add(app);
        }

        if ("gpa_desc".equals(sortBy)) {
            filteredApplicants.sort((a, b) -> Double.compare(extractGpa(b), extractGpa(a)));
        }

        updateBulkActionBar();
        containerApplicants.removeAllViews();

        if (filteredApplicants.isEmpty()) {
            emptyContainer.setVisibility(View.VISIBLE);
            if (!allApplicants.isEmpty()) {
                tvEmptyTitle.setText("No matching candidates");
                tvEmptySubtitle.setText("Try changing your search query or status filter.");
            } else {
                tvEmptyTitle.setText("No applications yet");
                tvEmptySubtitle.setText("When students apply to your published positions, their applications will appear here.");
            }
        } else {
            emptyContainer.setVisibility(View.GONE);
            for (JsonObject app : filteredApplicants) {
                addApplicantCard(app);
            }
        }
    }

    private static double extractGpa(JsonObject app) {
        JsonObject student = Json.obj(app, "student_profile", "student");
        if (student == null) return 0.0;
        String gpaStr = Json.str(student, null, "gpa", "cgpa");
        if (gpaStr == null || gpaStr.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(gpaStr.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String extractSkillsText(JsonObject student) {
        if (student == null || !student.has("skills") || student.get("skills").isJsonNull()) {
            return "";
        }
        JsonElement el = student.get("skills");
        List<String> list = new ArrayList<>();
        if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    String s = item.getAsString().trim();
                    if (!s.isEmpty()) list.add(s);
                } else if (item.isJsonObject()) {
                    String s = Json.str(item.getAsJsonObject(), null, "name", "skill");
                    if (s != null && !s.isEmpty()) list.add(s);
                }
            }
        } else if (el.isJsonPrimitive()) {
            String raw = el.getAsString().trim();
            if (!raw.isEmpty()) {
                for (String part : raw.split("[,;|]")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) list.add(trimmed);
                }
            }
        }
        if (list.isEmpty()) return "";
        int max = Math.min(3, list.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        if (list.size() > max) {
            sb.append(" +").append(list.size() - max);
        }
        return sb.toString();
    }

    // ── Render Applicant Card ────────────────────────────────────────────────

    private void addApplicantCard(JsonObject app) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_company_applicant, containerApplicants, false);

        int appId = Json.integer(app, 0, "id");
        JsonObject student = Json.obj(app, "student_profile", "student");
        JsonObject studentUser = student != null ? Json.obj(student, "user") : null;
        JsonObject post = Json.obj(app, "internship_post", "post");

        String name = Json.str(studentUser, null, "name");
        if (name == null) name = Json.str(student, null, "full_name", "name");
        if (name == null) name = "Candidate";

        String university = Json.str(student, null, "university", "school", "institution");
        String year = Json.str(student, null, "year_of_study", "year", "study_year");
        String uniYear = "🏛 " + (university != null && !university.isEmpty() ? university : "University")
                + (year != null && !year.isEmpty() ? " • Year " + year : "");

        String role = post != null ? Json.str(post, null, "title") : null;
        if (role == null) role = Json.str(app, "Internship Role", "post_title", "role");

        String status = Json.str(app, "pending", "status");
        String appliedAt = Json.str(app, null, "applied_at", "created_at");
        String avatarUrl = AvatarLoader.extractUrl(studentUser, student);

        MaterialCardView cardRoot = card.findViewById(R.id.cardApplicantRoot);
        TextView tvInitials = card.findViewById(R.id.tvInitials);
        ImageView ivAvatar = card.findViewById(R.id.ivAvatar);
        TextView tvName = card.findViewById(R.id.tvName);
        TextView tvUniversity = card.findViewById(R.id.tvUniversity);
        TextView tvGpaBadge = card.findViewById(R.id.tvGpaBadge);
        TextView tvOppTypeBadge = card.findViewById(R.id.tvOppTypeBadge);
        TextView tvRole = card.findViewById(R.id.tvRole);
        TextView tvMajorSkills = card.findViewById(R.id.tvMajorSkills);
        TextView tvStatus = card.findViewById(R.id.tvStatus);
        TextView tvAppliedTime = card.findViewById(R.id.tvAppliedTime);
        View boxInterviewInfo = card.findViewById(R.id.boxInterviewInfo);
        TextView tvInterviewInfo = card.findViewById(R.id.tvInterviewInfo);
        TextView tvInterviewConfirm = card.findViewById(R.id.tvInterviewConfirm);
        View btnQuickChat = card.findViewById(R.id.btnQuickChat);
        MaterialButton btnAction = card.findViewById(R.id.btnAction);

        String[] parts = name.trim().split("\\s+");
        String initials = parts.length > 1
                ? "" + parts[0].charAt(0) + parts[1].charAt(0)
                : name.substring(0, Math.min(2, name.length()));
        tvInitials.setText(initials.toUpperCase(Locale.ROOT));
        AvatarLoader.bind(avatarUrl, ivAvatar, tvInitials);

        tvName.setText(name);
        tvUniversity.setText(uniYear);
        tvRole.setText(role);
        tvAppliedTime.setText(formatRelativeTime(appliedAt));

        // GPA badge
        double gpa = extractGpa(app);
        if (tvGpaBadge != null) {
            if (gpa > 0) {
                tvGpaBadge.setVisibility(View.VISIBLE);
                tvGpaBadge.setText(String.format(Locale.US, "★ GPA %.2f", gpa));
            } else {
                tvGpaBadge.setVisibility(View.GONE);
            }
        }

        // Opportunity Type badge (Internship vs Job)
        if (tvOppTypeBadge != null) {
            String oppType = post != null
                    ? Json.str(post, "internship", "opportunity_type")
                    : Json.str(app, "internship", "opportunity_type");
            if ("internship".equalsIgnoreCase(oppType)) {
                tvOppTypeBadge.setText("🎓 Intern");
                tvOppTypeBadge.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                tvOppTypeBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
            } else {
                String label = "💼 Job";
                if ("full_time".equalsIgnoreCase(oppType)) label = "💼 Full-Time";
                else if ("part_time".equalsIgnoreCase(oppType)) label = "💼 Part-Time";
                else if ("contract".equalsIgnoreCase(oppType)) label = "💼 Contract";
                tvOppTypeBadge.setText(label);
                tvOppTypeBadge.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_amber_bg)));
                tvOppTypeBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            }
        }

        // Major & Top Skills chip
        if (tvMajorSkills != null) {
            String major = student != null ? Json.str(student, null, "major", "department") : null;
            String skillsSummary = extractSkillsText(student);
            StringBuilder meta = new StringBuilder();
            if (major != null && !major.trim().isEmpty()) {
                meta.append(major.trim());
            }
            if (!skillsSummary.isEmpty()) {
                if (meta.length() > 0) meta.append(" • ");
                meta.append(skillsSummary);
            }
            if (meta.length() > 0) {
                tvMajorSkills.setText(meta.toString());
                tvMajorSkills.setVisibility(View.VISIBLE);
            } else {
                tvMajorSkills.setVisibility(View.GONE);
            }
        }

        applyStatusBadge(tvStatus, status);
        applyActionButton(btnAction, status);

        // Selection Mode Checkbox & Highlighting
        MaterialCheckBox cbSelect = card.findViewById(R.id.cbSelectApplicant);
        boolean isChecked = selectedApplicantIds.contains(appId);
        if (cbSelect != null) {
            cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            cbSelect.setOnCheckedChangeListener(null);
            cbSelect.setChecked(isChecked);
            cbSelect.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    selectedApplicantIds.add(appId);
                } else {
                    selectedApplicantIds.remove(appId);
                }
                styleSelectedCard(cardRoot, checked);
                updateBulkActionBar();
            });
        }
        styleSelectedCard(cardRoot, isSelectionMode && isChecked);

        int studentId = Json.integer(app, 0, "student_profile_id", "student_id");
        if (studentId == 0 && student != null) {
            studentId = Json.integer(student, 0, "id");
        }
        if (studentId == 0 && studentUser != null) {
            studentId = Json.integer(studentUser, 0, "id");
        }

        final int finalStudentId = studentId;
        final String finalStudentName = name;
        View.OnClickListener openStudentProfile = v -> {
            if (isSelectionMode && cbSelect != null) {
                cbSelect.setChecked(!cbSelect.isChecked());
                return;
            }
            if (finalStudentId > 0) {
                CompanyStudentProfileActivity.launch(CompanyApplicantsActivity.this, finalStudentId, finalStudentName);
            }
        };

        tvName.setOnClickListener(openStudentProfile);
        View avatarView = card.findViewById(R.id.cardApplicantAvatar);
        if (avatarView != null) avatarView.setOnClickListener(openStudentProfile);
        ivAvatar.setOnClickListener(openStudentProfile);
        tvInitials.setOnClickListener(openStudentProfile);

        // Interview details banner if scheduled
        JsonObject interview = StudentApplicationsActivity.extractInterview(app);
        if (interview != null) {
            boxInterviewInfo.setVisibility(View.VISIBLE);
            String schedRaw = Json.str(interview, null, "scheduled_at", "interview_date");
            String modeRaw = Json.str(interview, "in_person", "meeting_type", "type");
            String intStatus = Json.str(interview, "scheduled", "status");
            String modeLabel = "online".equalsIgnoreCase(modeRaw) ? "Online Video" : "In-Person";
            String formattedDate = schedRaw != null
                    ? StudentDashboardActivity.formatDateTime(schedRaw) : "Scheduled";
            tvInterviewInfo.setText(formattedDate + " • " + modeLabel);

            boolean confirmed = interview.has("confirmed_by_student")
                    && !interview.get("confirmed_by_student").isJsonNull()
                    && interview.get("confirmed_by_student").getAsBoolean();
            if ("reschedule_requested".equalsIgnoreCase(intStatus)) {
                tvInterviewConfirm.setText("⚠ Reschedule Req.");
                tvInterviewConfirm.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
            } else if (confirmed || "confirmed".equalsIgnoreCase(intStatus)) {
                tvInterviewConfirm.setText("Confirmed ✓");
                tvInterviewConfirm.setTextColor(MaterialColors.getColor(
                        card, androidx.appcompat.R.attr.colorPrimary));
            } else {
                tvInterviewConfirm.setText("Pending reply");
                tvInterviewConfirm.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            }
        } else {
            boxInterviewInfo.setVisibility(View.GONE);
        }

        final String finalName = name;
        final String finalRole = role;

        btnQuickChat.setOnClickListener(v -> {
            if (appId > 0) {
                ChatActivity.launchFromApplication(this, appId, finalName, finalRole);
            } else {
                ChatActivity.launch(this, finalName, true);
            }
        });

        View.OnClickListener openReview = v -> {
            if (isSelectionMode && cbSelect != null) {
                cbSelect.setChecked(!cbSelect.isChecked());
                return;
            }
            ReviewApplicantBottomSheet.show(getSupportFragmentManager(), app, this::loadApplicants);
        };
        btnAction.setOnClickListener(v ->
                ReviewApplicantBottomSheet.show(getSupportFragmentManager(), app, this::loadApplicants));
        card.setOnClickListener(openReview);

        // Long-press on any card activates clean Selection Mode immediately
        card.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                if (appId > 0) selectedApplicantIds.add(appId);
                updateBulkActionBar();
                applyFilterAndRender();
                return true;
            }
            return false;
        });

        containerApplicants.addView(card);
    }

    private void styleSelectedCard(@Nullable MaterialCardView cardRoot, boolean selected) {
        if (cardRoot == null) return;
        int primaryGreen = MaterialColors.getColor(cardRoot, androidx.appcompat.R.attr.colorPrimary);
        if (selected) {
            cardRoot.setStrokeColor(ColorStateList.valueOf(primaryGreen));
            cardRoot.setStrokeWidth(dp(1.5f));
        } else {
            cardRoot.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_soft)));
            cardRoot.setStrokeWidth(dp(1f));
        }
    }

    private void updateBulkActionBar() {
        if (boxBulkActions == null) return;
        int count = selectedApplicantIds.size();
        if (isSelectionMode || count > 0) {
            boxBulkActions.setVisibility(View.VISIBLE);
            if (tvBulkCount != null) {
                if (count == 0) {
                    tvBulkCount.setText("0 Candidates Selected");
                    tvBulkCount.setAlpha(0.75f);
                } else {
                    tvBulkCount.setText(count + (count == 1 ? " Candidate Selected" : " Candidates Selected"));
                    tvBulkCount.setAlpha(1.0f);
                }
            }
            if (btnBulkSelectAll != null) {
                boolean allSelected = !filteredApplicants.isEmpty();
                for (JsonObject app : filteredApplicants) {
                    int id = Json.integer(app, 0, "id");
                    if (id <= 0 || !selectedApplicantIds.contains(id)) {
                        allSelected = false;
                        break;
                    }
                }
                btnBulkSelectAll.setText(allSelected ? "Deselect All" : "Select All (" + filteredApplicants.size() + ")");
                btnBulkSelectAll.setVisibility(filteredApplicants.isEmpty() ? View.GONE : View.VISIBLE);
            }
            boolean hasSelection = count > 0;
            setBulkButtonState(btnBulkReview, hasSelection);
            setBulkButtonState(btnBulkShortlist, hasSelection);
            setBulkButtonState(btnBulkAccept, hasSelection);
            setBulkButtonState(btnBulkDecline, hasSelection);
        } else {
            boxBulkActions.setVisibility(View.GONE);
        }
    }

    private void setBulkButtonState(@Nullable View btn, boolean enabled) {
        if (btn == null) return;
        btn.setEnabled(enabled);
        btn.setAlpha(enabled ? 1.0f : 0.42f);
    }

    private void confirmAndPerformBulkStatus(String targetStatus, String actionVerb) {
        int count = selectedApplicantIds.size();
        if (count == 0) {
            toast("Tap candidates to select them first.");
            return;
        }
        String candidateNoun = count == 1 ? "1 candidate" : (count + " candidates");
        new MaterialAlertDialogBuilder(this)
                .setTitle(actionVerb + " " + candidateNoun + "?")
                .setMessage("Are you sure you want to mark the selected " + candidateNoun + " as " + targetStatus + "?")
                .setPositiveButton("Yes, " + actionVerb, (dialog, which) -> performBulkStatus(targetStatus))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performBulkStatus(String targetStatus) {
        if (selectedApplicantIds.isEmpty()) {
            toast("Tap candidates to select them first.");
            return;
        }
        JsonObject body = new JsonObject();
        JsonArray ids = new JsonArray();
        for (Integer id : selectedApplicantIds) {
            ids.add(id);
        }
        body.add("application_ids", ids);
        body.add("applicant_ids", ids);
        body.addProperty("status", targetStatus);

        showLoading(true);
        repo.bulkUpdateApplicantStatus(body, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject res) {
                showLoading(false);
                toast("Status updated for " + selectedApplicantIds.size() + " applicants!");
                exitSelectionMode();
                loadApplicants();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                showLoading(false);
                toast(error.message);
            }
        });
    }

    private void applyStatusBadge(TextView tv, String status) {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        int bgColor;
        int textColor;
        String label;

        switch (s) {
            case "shortlisted":
                label = "Shortlisted";
                bgColor = ContextCompat.getColor(this, R.color.tint_indigo_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_indigo);
                break;
            case "interview":
            case "interview_scheduled":
            case "interviewed":
                label = "Interviewed";
                bgColor = ContextCompat.getColor(this, R.color.tint_indigo_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_indigo);
                break;
            case "accepted":
                label = "Accepted";
                bgColor = ContextCompat.getColor(this, R.color.tint_green_bg);
                textColor = MaterialColors.getColor(tv, androidx.appcompat.R.attr.colorPrimary);
                break;
            case "rejected":
            case "withdrawn":
                label = "Rejected";
                bgColor = ContextCompat.getColor(this, R.color.tint_red_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_red);
                break;
            case "under_review":
            case "reviewed":
                label = "Under Review";
                bgColor = ContextCompat.getColor(this, R.color.tint_amber_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_amber);
                break;
            case "pending":
            default:
                label = "Pending";
                bgColor = ContextCompat.getColor(this, R.color.tint_amber_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_amber);
                break;
        }
        tv.setText(label);
        tv.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        tv.setTextColor(textColor);
    }

    private void applyActionButton(MaterialButton btn, String status) {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        switch (s) {
            case "shortlisted":
                btn.setText("Schedule");
                btn.setIconResource(R.drawable.ic_event);
                int indigo = ContextCompat.getColor(this, R.color.tint_indigo);
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                btn.setTextColor(indigo);
                btn.setIconTint(ColorStateList.valueOf(indigo));
                break;
            case "interview":
            case "interview_scheduled":
            case "interviewed":
                btn.setText("Interview");
                btn.setIconResource(R.drawable.ic_event);
                int indigoInt = ContextCompat.getColor(this, R.color.tint_indigo);
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                btn.setTextColor(indigoInt);
                btn.setIconTint(ColorStateList.valueOf(indigoInt));
                break;
            case "accepted":
            case "rejected":
            case "withdrawn":
                btn.setText("Details");
                btn.setIconResource(R.drawable.ic_visibility);
                int slate = ContextCompat.getColor(this, R.color.nav_unselected);
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.bg_page)));
                btn.setTextColor(slate);
                btn.setIconTint(ColorStateList.valueOf(slate));
                break;
            default:
                btn.setText("Review");
                btn.setIconResource(R.drawable.ic_visibility);
                int green = ContextCompat.getColor(this, R.color.tint_green);
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_green_bg)));
                btn.setTextColor(green);
                btn.setIconTint(ColorStateList.valueOf(green));
                break;
        }
    }

    private static String formatRelativeTime(String isoDate) {
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

    private void showLoading(boolean show) {
        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
        if (containerApplicants != null) containerApplicants.setVisibility(View.VISIBLE);
        if (scrollRefresh != null) {
            scrollRefresh.setRefreshing(show);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
