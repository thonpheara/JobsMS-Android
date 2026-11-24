package com.example.internshipms;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CompanyListingsActivity extends AppCompatActivity {

    private static final String FILTER_ALL = "all";
    private static final String FILTER_ACTIVE = "active";
    private static final String FILTER_PENDING = "pending";
    private static final String FILTER_CLOSED = "closed";
    private static final String FILTER_DRAFT = "draft";
    private static final String FILTER_REJECTED = "rejected";

    private static final int SORT_NEWEST = 0;
    private static final int SORT_MOST_APPLICANTS = 1;
    private static final int SORT_DEADLINE = 2;

    private CompanyRepository repo;
    private PullRefreshScrollView scrollRefresh;
    private LinearLayout containerListings;
    private LinearLayout loadingContainer;
    private LinearLayout emptyContainer;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private View dotUnread;

    private MaterialCardView pillAll, pillActive, pillPending, pillClosed, pillDraft, pillRejected;
    private TextView tvPillAll, tvPillActive, tvPillPending, tvPillClosed, tvPillDraft, tvPillRejected;
    private View rowActiveModeBadge;
    private View dotModeFilterActive;
    private TextView tvModeFilterLabel;
    private EditText etSearch;

    private final List<JsonObject> allPosts = new ArrayList<>();
    private String currentFilter = FILTER_ALL;
    private String selectedOpportunityType = null; // null = All, "internship", "job"
    private String selectedWorkMode = null;
    private String searchQuery = "";
    private int currentSort = SORT_NEWEST;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_company_listings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repo = new CompanyRepository(this);

        // ── Bind Views ───────────────────────────────────────────────────────
        scrollRefresh = findViewById(R.id.scrollRefresh);
        containerListings = findViewById(R.id.containerListings);
        loadingContainer = findViewById(R.id.loadingContainer);
        emptyContainer = findViewById(R.id.emptyContainer);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        dotUnread = findViewById(R.id.dotUnread);
        etSearch = findViewById(R.id.etSearch);

        pillAll = findViewById(R.id.pillAll);
        pillActive = findViewById(R.id.pillActive);
        pillPending = findViewById(R.id.pillPending);
        pillClosed = findViewById(R.id.pillClosed);
        pillDraft = findViewById(R.id.pillDraft);
        pillRejected = findViewById(R.id.pillRejected);

        tvPillAll = findViewById(R.id.tvPillAll);
        tvPillActive = findViewById(R.id.tvPillActive);
        tvPillPending = findViewById(R.id.tvPillPending);
        tvPillClosed = findViewById(R.id.tvPillClosed);
        tvPillDraft = findViewById(R.id.tvPillDraft);
        tvPillRejected = findViewById(R.id.tvPillRejected);

        rowActiveModeBadge = findViewById(R.id.rowActiveModeBadge);
        dotModeFilterActive = findViewById(R.id.dotModeFilterActive);
        tvModeFilterLabel = findViewById(R.id.tvModeFilterLabel);
        View btnClearModeFilter = findViewById(R.id.btnClearModeFilter);
        if (btnClearModeFilter != null) {
            btnClearModeFilter.setOnClickListener(v -> {
                selectedOpportunityType = null;
                selectedWorkMode = null;
                currentSort = SORT_NEWEST;
                updateActiveFilterBadge();
                applyFilterAndRender();
            });
        }

        // ── Header & Create Actions ──────────────────────────────────────────
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));

        View.OnClickListener createAction = v -> PostInternshipActivity.launch(this);
        findViewById(R.id.btnNewListing).setOnClickListener(createAction);
        findViewById(R.id.btnEmptyCreate).setOnClickListener(createAction);

        // ── Search & Sort ────────────────────────────────────────────────────
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

        findViewById(R.id.btnSortFilter).setOnClickListener(this::showSortMenu);

        // ── Filter Pills ─────────────────────────────────────────────────────
        pillAll.setOnClickListener(v -> selectFilter(FILTER_ALL));
        pillActive.setOnClickListener(v -> selectFilter(FILTER_ACTIVE));
        pillPending.setOnClickListener(v -> selectFilter(FILTER_PENDING));
        pillClosed.setOnClickListener(v -> selectFilter(FILTER_CLOSED));
        pillDraft.setOnClickListener(v -> selectFilter(FILTER_DRAFT));
        if (pillRejected != null) {
            pillRejected.setOnClickListener(v -> selectFilter(FILTER_REJECTED));
        }

        // ── Pull-to-Refresh ──────────────────────────────────────────────────
        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadListings);
        }

        // ── Bottom Navigation ────────────────────────────────────────────────
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_listings);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_listings) return true;
            if (id == R.id.nav_home) {
                Navigator.switchTab(this, CompanyDashboardActivity.class, false);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null && nav.getSelectedItemId() != R.id.nav_listings) {
            nav.setSelectedItemId(R.id.nav_listings);
        }
        loadListings();
    }

    // ── API Calls ────────────────────────────────────────────────────────────

    private void loadUnreadBadge() {
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
    }

    public void loadListings() {
        showLoading(true);
        loadUnreadBadge();
        repo.listPosts(null, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonArray list = Json.list(body, "posts", "data");
                allPosts.clear();
                for (JsonElement el : list) {
                    if (el.isJsonObject()) {
                        allPosts.add(el.getAsJsonObject());
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

    // ── Filter & Sort Logic ──────────────────────────────────────────────────

    private void selectFilter(String filter) {
        currentFilter = filter;
        styleFilterPill(pillAll, tvPillAll, FILTER_ALL.equals(filter));
        styleFilterPill(pillActive, tvPillActive, FILTER_ACTIVE.equals(filter));
        styleFilterPill(pillPending, tvPillPending, FILTER_PENDING.equals(filter));
        styleFilterPill(pillClosed, tvPillClosed, FILTER_CLOSED.equals(filter));
        styleFilterPill(pillDraft, tvPillDraft, FILTER_DRAFT.equals(filter));
        if (pillRejected != null && tvPillRejected != null) {
            styleFilterPill(pillRejected, tvPillRejected, FILTER_REJECTED.equals(filter));
        }
        applyFilterAndRender();
    }

    private void selectOpportunityType(String oppType) {
        selectedOpportunityType = oppType;
        updateActiveFilterBadge();
        applyFilterAndRender();
    }

    private void selectWorkMode(String mode) {
        selectedWorkMode = mode;
        updateActiveFilterBadge();
        applyFilterAndRender();
    }

    private void updateActiveFilterBadge() {
        boolean hasFilter = selectedOpportunityType != null || selectedWorkMode != null || currentSort != SORT_NEWEST;
        ImageView ivFilter = findViewById(R.id.btnSortFilter);
        if (hasFilter) {
            if (rowActiveModeBadge != null) rowActiveModeBadge.setVisibility(View.VISIBLE);
            if (dotModeFilterActive != null) dotModeFilterActive.setVisibility(View.VISIBLE);
            if (ivFilter != null) {
                ivFilter.setColorFilter(MaterialColors.getColor(ivFilter, androidx.appcompat.R.attr.colorPrimary));
            }
            StringBuilder label = new StringBuilder();
            if ("internship".equals(selectedOpportunityType)) label.append("🎓 Internships");
            else if ("job".equals(selectedOpportunityType)) label.append("💼 Jobs");

            if ("remote".equals(selectedWorkMode)) {
                if (label.length() > 0) label.append(" • ");
                label.append("Remote");
            } else if ("hybrid".equals(selectedWorkMode)) {
                if (label.length() > 0) label.append(" • ");
                label.append("Hybrid");
            } else if ("on_site".equals(selectedWorkMode)) {
                if (label.length() > 0) label.append(" • ");
                label.append("On-Site");
            }

            if (currentSort == SORT_MOST_APPLICANTS) {
                if (label.length() > 0) label.append(" • ");
                label.append("Most Applicants");
            } else if (currentSort == SORT_DEADLINE) {
                if (label.length() > 0) label.append(" • ");
                label.append("Closing Soon");
            }
            if (tvModeFilterLabel != null) {
                tvModeFilterLabel.setText(label.toString());
            }
        } else {
            if (rowActiveModeBadge != null) rowActiveModeBadge.setVisibility(View.GONE);
            if (dotModeFilterActive != null) dotModeFilterActive.setVisibility(View.GONE);
            if (ivFilter != null) {
                ivFilter.setColorFilter(ContextCompat.getColor(this, R.color.nav_unselected));
            }
        }
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

    private void updatePillCounts() {
        int total = allPosts.size();
        int active = 0;
        int pending = 0;
        int closed = 0;
        int draft = 0;
        int rejected = 0;

        for (JsonObject p : allPosts) {
            String st = normalizeStatus(Json.str(p, "pending_approval", "status"));
            switch (st) {
                case FILTER_ACTIVE:
                    active++;
                    break;
                case FILTER_PENDING:
                    pending++;
                    break;
                case FILTER_CLOSED:
                    closed++;
                    break;
                case FILTER_DRAFT:
                    draft++;
                    break;
                case FILTER_REJECTED:
                    rejected++;
                    break;
            }
        }

        tvPillAll.setText("All (" + total + ")");
        tvPillActive.setText("Active (" + active + ")");
        tvPillPending.setText("Pending (" + pending + ")");
        tvPillClosed.setText("Closed (" + closed + ")");
        tvPillDraft.setText("Drafts (" + draft + ")");
        pillDraft.setVisibility(draft > 0 || FILTER_DRAFT.equals(currentFilter)
                ? View.VISIBLE : View.GONE);
        if (pillRejected != null && tvPillRejected != null) {
            tvPillRejected.setText("Rejected (" + rejected + ")");
            pillRejected.setVisibility(rejected > 0 || FILTER_REJECTED.equals(currentFilter)
                    ? View.VISIBLE : View.GONE);
        }
    }

    private static String normalizeStatus(String raw) {
        if (raw == null) return FILTER_PENDING;
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "approved":
            case "active":
                return FILTER_ACTIVE;
            case "pending":
            case "pending_approval":
                return FILTER_PENDING;
            case "draft":
                return FILTER_DRAFT;
            case "rejected":
                return FILTER_REJECTED;
            case "closed":
            default:
                return FILTER_CLOSED;
        }
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 20, 0, (selectedOpportunityType == null ? "✓ " : "    ") + "Type: All Types");
        popup.getMenu().add(0, 21, 1, ("internship".equals(selectedOpportunityType) ? "✓ " : "    ") + "Type: 🎓 Internships");
        popup.getMenu().add(0, 22, 2, ("job".equals(selectedOpportunityType) ? "✓ " : "    ") + "Type: 💼 Jobs");

        popup.getMenu().add(1, 10, 3, (selectedWorkMode == null ? "✓ " : "    ") + "Mode: All Modes");
        popup.getMenu().add(1, 11, 4, ("remote".equals(selectedWorkMode) ? "✓ " : "    ") + "Mode: Remote");
        popup.getMenu().add(1, 12, 5, ("hybrid".equals(selectedWorkMode) ? "✓ " : "    ") + "Mode: Hybrid");
        popup.getMenu().add(1, 13, 6, ("on_site".equals(selectedWorkMode) ? "✓ " : "    ") + "Mode: On-Site");

        popup.getMenu().add(2, SORT_NEWEST, 7, (currentSort == SORT_NEWEST ? "✓ " : "    ") + "Sort: Latest First");
        popup.getMenu().add(2, SORT_MOST_APPLICANTS, 8, (currentSort == SORT_MOST_APPLICANTS ? "✓ " : "    ") + "Sort: Most Applicants");
        popup.getMenu().add(2, SORT_DEADLINE, 9, (currentSort == SORT_DEADLINE ? "✓ " : "    ") + "Sort: Closing Soon");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 20) selectOpportunityType(null);
            else if (id == 21) selectOpportunityType("internship");
            else if (id == 22) selectOpportunityType("job");
            else if (id == 10) selectWorkMode(null);
            else if (id == 11) selectWorkMode("remote");
            else if (id == 12) selectWorkMode("hybrid");
            else if (id == 13) selectWorkMode("on_site");
            else {
                currentSort = id;
                updateActiveFilterBadge();
                applyFilterAndRender();
            }
            return true;
        });
        popup.show();
    }

    private void applyFilterAndRender() {
        List<JsonObject> filtered = new ArrayList<>();
        String q = searchQuery.toLowerCase(Locale.ROOT);

        for (JsonObject post : allPosts) {
            String st = normalizeStatus(Json.str(post, "pending_approval", "status"));
            if (!FILTER_ALL.equals(currentFilter) && !currentFilter.equals(st)) {
                continue;
            }
            if (selectedOpportunityType != null) {
                String oppType = Json.str(post, "internship", "opportunity_type").toLowerCase(Locale.ROOT);
                boolean isInternship = "internship".equals(oppType);
                if ("internship".equals(selectedOpportunityType) && !isInternship) continue;
                if ("job".equals(selectedOpportunityType) && isInternship) continue;
            }
            if (selectedWorkMode != null) {
                String mode = Json.str(post, "", "work_mode", "type").toLowerCase(Locale.ROOT);
                if ("on_site".equals(selectedWorkMode)) {
                    if (!mode.contains("site") && !mode.contains("office")) continue;
                } else if (!mode.contains(selectedWorkMode)) {
                    continue;
                }
            }
            if (!q.isEmpty()) {
                String title = Json.str(post, "", "title").toLowerCase(Locale.ROOT);
                String cat = Json.str(post, "", "category").toLowerCase(Locale.ROOT);
                String loc = Json.str(post, "", "location").toLowerCase(Locale.ROOT);
                if (!title.contains(q) && !cat.contains(q) && !loc.contains(q)) {
                    continue;
                }
            }
            filtered.add(post);
        }

        if (currentSort == SORT_MOST_APPLICANTS) {
            Collections.sort(filtered, (a, b) -> Integer.compare(
                    Json.integer(b, 0, "applications_count"),
                    Json.integer(a, 0, "applications_count")));
        } else if (currentSort == SORT_DEADLINE) {
            Collections.sort(filtered, Comparator.comparing(
                    p -> Json.str(p, "9999-12-31", "deadline")));
        }

        containerListings.removeAllViews();

        if (filtered.isEmpty()) {
            emptyContainer.setVisibility(View.VISIBLE);
            if (!allPosts.isEmpty()) {
                tvEmptyTitle.setText("No matching jobs");
                tvEmptySubtitle.setText("Try adjusting your search or filter.");
            } else {
                tvEmptyTitle.setText("No job postings yet");
                tvEmptySubtitle.setText("Create your first internship cohort position to start receiving applications from students.");
            }
        } else {
            emptyContainer.setVisibility(View.GONE);
            for (JsonObject post : filtered) {
                addListingCard(post);
            }
        }
    }

    // ── Render Single Listing Card ───────────────────────────────────────────

    private void addListingCard(JsonObject post) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_company_listing, containerListings, false);

        TextView tvTitle = card.findViewById(R.id.tvTitle);
        TextView tvOpportunityTypeBadge = card.findViewById(R.id.tvOpportunityTypeBadge);
        TextView tvCategoryLocation = card.findViewById(R.id.tvCategoryLocation);
        TextView tvStatusBadge = card.findViewById(R.id.tvStatusBadge);
        View boxRejectionReason = card.findViewById(R.id.boxRejectionReason);
        TextView tvRejectionReason = card.findViewById(R.id.tvRejectionReason);
        TextView tvSlots = card.findViewById(R.id.tvSlots);
        ImageView ivStipendIcon = card.findViewById(R.id.ivStipendIcon);
        TextView tvStipend = card.findViewById(R.id.tvStipend);
        TextView tvWorkModeBadge = card.findViewById(R.id.tvWorkModeBadge);
        TextView tvApplicantsCount = card.findViewById(R.id.tvApplicantsCount);
        View btnViewApplicants = card.findViewById(R.id.btnViewApplicants);
        TextView tvDeadline = card.findViewById(R.id.tvDeadline);
        TextView btnQuickSubmitDraft = card.findViewById(R.id.btnQuickSubmitDraft);
        View btnMore = card.findViewById(R.id.btnMore);

        int postId = Json.integer(post, 0, "id");
        String title = Json.str(post, "Internship Position", "title");
        String category = Json.str(post, null, "category", "department");
        String location = Json.str(post, null, "location");
        String workMode = Json.str(post, "on_site", "work_mode", "type");
        String oppType = Json.str(post, "internship", "opportunity_type");
        String expLevel = Json.str(post, null, "experience_level");
        int durationWeeks = Json.integer(post, 12, "duration_weeks");
        String rawStatus = Json.str(post, "pending_approval", "status");
        String rejectionReason = Json.str(post, null, "rejection_reason", "admin_notes");
        int slots = Math.max(1, Json.integer(post, 1, "slots"));
        int appsCount = Math.max(0, Json.integer(post, 0, "applications_count"));
        int acceptedCount = Json.integer(post, -1, "accepted_count", "filled_slots");

        tvTitle.setText(title);

        boolean isInternship = "internship".equalsIgnoreCase(oppType);
        if (tvOpportunityTypeBadge != null) {
            if (isInternship) {
                tvOpportunityTypeBadge.setText("🎓 Internship");
                tvOpportunityTypeBadge.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_green_bg)));
                tvOpportunityTypeBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_green));
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
                tvOpportunityTypeBadge.setText(oppLabel);
                tvOpportunityTypeBadge.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                tvOpportunityTypeBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
            }
        }

        if (tvCategoryLocation != null) {
            String catPart = (category != null && !category.trim().isEmpty()) ? category.trim() : "General IT";
            String locPart = (location != null && !location.trim().isEmpty()) ? location.trim() : "Cambodia";
            String extraPart;
            if (isInternship) {
                extraPart = durationWeeks + " wks";
            } else {
                extraPart = formatExperienceLevel(expLevel);
            }
            tvCategoryLocation.setText(catPart + " • " + locPart + " • " + extraPart);
        }

        bindStatusBadge(tvStatusBadge, rawStatus);
        if (tvWorkModeBadge != null) {
            bindWorkModeBadge(tvWorkModeBadge, workMode);
        }

        // Rejection Reason banner if status is rejected
        if (boxRejectionReason != null && tvRejectionReason != null) {
            if ("rejected".equalsIgnoreCase(rawStatus) && rejectionReason != null && !rejectionReason.trim().isEmpty()) {
                boxRejectionReason.setVisibility(View.VISIBLE);
                tvRejectionReason.setText("⚠ Rejected: " + rejectionReason.trim());
            } else {
                boxRejectionReason.setVisibility(View.GONE);
            }
        }

        // Quick Submit Draft button (matching Web index.blade.php)
        if (btnQuickSubmitDraft != null) {
            if ("draft".equalsIgnoreCase(rawStatus) && postId > 0) {
                btnQuickSubmitDraft.setVisibility(View.VISIBLE);
                btnQuickSubmitDraft.setOnClickListener(v -> submitDraftPost(postId));
            } else {
                btnQuickSubmitDraft.setVisibility(View.GONE);
            }
        }

        // Slots label: "1 / 2 Accepted" or "2 / 2 Filled ✓"
        int filled = acceptedCount >= 0 ? acceptedCount : 0;
        if (filled >= slots) {
            tvSlots.setText(filled + " / " + slots + " Filled ✓");
            tvSlots.setTextColor(ContextCompat.getColor(this, R.color.tint_green));
            tvSlots.setTypeface(null, Typeface.BOLD);
        } else {
            tvSlots.setText(filled + " / " + slots + " Accepted");
            tvSlots.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            tvSlots.setTypeface(null, Typeface.NORMAL);
        }

        // Compensation / Stipend display (supports compensation_display & salary_max range)
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
            ivStipendIcon.setImageResource(disclosed ? R.drawable.ic_payments : R.drawable.ic_lock_outline);
            tvStipend.setText(compDisplay.trim());
        } else if (stipendVal > 0 && disclosed) {
            ivStipendIcon.setImageResource(R.drawable.ic_payments);
            String minFmt = stipendVal == (long) stipendVal
                    ? String.valueOf((long) stipendVal)
                    : String.format(Locale.US, "%.0f", stipendVal);
            if (salaryMaxVal > stipendVal) {
                String maxFmt = salaryMaxVal == (long) salaryMaxVal
                        ? String.valueOf((long) salaryMaxVal)
                        : String.format(Locale.US, "%.0f", salaryMaxVal);
                tvStipend.setText("$" + minFmt + " - $" + maxFmt + "/mo");
            } else {
                tvStipend.setText("$" + minFmt + " / mo");
            }
        } else if (!disclosed) {
            ivStipendIcon.setImageResource(R.drawable.ic_lock_outline);
            tvStipend.setText(isInternship ? "Unpaid / Experience" : "Negotiable");
        } else {
            ivStipendIcon.setImageResource(R.drawable.ic_payments);
            tvStipend.setText(isInternship ? "Unpaid / Experience" : "Competitive");
        }

        // Applicants count + Deadline with Expired / Expiring Soon coloring
        tvApplicantsCount.setText(appsCount + (appsCount == 1
                ? " Candidate" : " Candidates"));
        if (btnViewApplicants != null) {
            btnViewApplicants.setOnClickListener(v ->
                    CompanyApplicantsActivity.launchForPost(this, postId, title));
        }
        bindDeadlineLabel(tvDeadline, Json.str(post, null, "deadline"));

        // Overflow menu (Edit / Submit / Duplicate / Close / Delete)
        btnMore.setOnClickListener(v -> showPostActionsMenu(v, post));

        // Card click opens Vacancy Overview (Detail Post) screen
        card.setOnClickListener(v -> CompanyPostDetailActivity.launch(this, postId, post));

        containerListings.addView(card);
    }

    private static String formatExperienceLevel(String raw) {
        if (raw == null) return "All Levels";
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "no_experience":
                return "No Exp";
            case "entry_level":
                return "Entry Level";
            case "junior":
                return "Junior";
            case "mid_level":
                return "Mid-Level";
            default:
                return "All Levels";
        }
    }

    private void bindDeadlineLabel(TextView tv, String raw) {
        if (raw == null || raw.isEmpty()) {
            tv.setText("No deadline");
            tv.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            tv.setTypeface(null, Typeface.NORMAL);
            return;
        }
        try {
            String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            LocalDate date = LocalDate.parse(datePart);
            LocalDate today = LocalDate.now();
            String formatted = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US));
            if (date.isBefore(today)) {
                tv.setText("! Expired " + formatted);
                tv.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
                tv.setTypeface(null, Typeface.BOLD);
            } else if (!date.isAfter(today.plusDays(5))) {
                tv.setText("Due " + formatted);
                tv.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
                tv.setTypeface(null, Typeface.BOLD);
            } else {
                tv.setText("Due " + formatted);
                tv.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
                tv.setTypeface(null, Typeface.NORMAL);
            }
        } catch (Exception e) {
            tv.setText(raw);
            tv.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
        }
    }

    private void bindWorkModeBadge(TextView badge, String rawMode) {
        String m = rawMode == null ? "" : rawMode.toLowerCase(Locale.ROOT);
        int bgColor;
        int textColor;
        String label;
        if (m.contains("remote")) {
            label = "Remote";
            bgColor = ContextCompat.getColor(this, R.color.tint_green_bg);
            textColor = ContextCompat.getColor(this, R.color.tint_green);
        } else if (m.contains("hybrid")) {
            label = "Hybrid";
            bgColor = ContextCompat.getColor(this, R.color.tint_amber_bg);
            textColor = ContextCompat.getColor(this, R.color.tint_amber);
        } else {
            label = "On-Site";
            bgColor = ContextCompat.getColor(this, R.color.tint_indigo_bg);
            textColor = ContextCompat.getColor(this, R.color.tint_indigo);
        }
        badge.setText(label);
        badge.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        badge.setTextColor(textColor);
    }

    private void bindStatusBadge(TextView badge, String rawStatus) {
        String s = rawStatus == null ? "" : rawStatus.toLowerCase(Locale.ROOT);
        int bgColor;
        int textColor;
        String label;

        switch (s) {
            case "approved":
            case "active":
                label = "Published & Active";
                bgColor = ContextCompat.getColor(this, R.color.tint_green_bg);
                textColor = MaterialColors.getColor(badge, androidx.appcompat.R.attr.colorPrimary);
                break;
            case "draft":
                label = "Draft";
                bgColor = ContextCompat.getColor(this, R.color.tint_indigo_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_indigo);
                break;
            case "closed":
                label = "Closed";
                bgColor = ContextCompat.getColor(this, R.color.tint_red_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_red);
                break;
            case "rejected":
                label = "Rejected";
                bgColor = ContextCompat.getColor(this, R.color.tint_red_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_red);
                break;
            case "pending":
            case "pending_approval":
            default:
                label = "Pending Admin Review";
                bgColor = ContextCompat.getColor(this, R.color.tint_amber_bg);
                textColor = ContextCompat.getColor(this, R.color.tint_amber);
                break;
        }

        badge.setText(label);
        badge.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        badge.setTextColor(textColor);
    }

    private static String formatDeadline(String raw) {
        if (raw == null || raw.isEmpty()) return "No deadline";
        try {
            String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            LocalDate date = LocalDate.parse(datePart);
            return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US));
        } catch (Exception e) {
            return raw;
        }
    }

    // ── Overflow Actions Menu (Edit, Submit, Duplicate, Close, Delete) ───────

    private void showPostActionsMenu(View anchor, JsonObject post) {
        int postId = Json.integer(post, 0, "id");
        String status = Json.str(post, "", "status").toLowerCase(Locale.ROOT);
        String title = Json.str(post, "this job", "title");
        int appsCount = Math.max(0, Json.integer(post, 0, "applications_count"));

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(Menu.NONE, 0, 0, "View Details");
        popup.getMenu().add(Menu.NONE, 6, 1, "View Applicants (" + appsCount + ")");
        popup.getMenu().add(Menu.NONE, 1, 2, "Edit Job");
        if ("draft".equals(status)) {
            popup.getMenu().add(Menu.NONE, 2, 3, "Submit for Review");
        }
        popup.getMenu().add(Menu.NONE, 3, 4, "Duplicate as Draft");
        if ("closed".equals(status)) {
            popup.getMenu().add(Menu.NONE, 4, 5, "Reopen Job");
        } else if ("approved".equals(status) || "active".equals(status)) {
            popup.getMenu().add(Menu.NONE, 4, 5, "Close Job");
        }
        popup.getMenu().add(Menu.NONE, 5, 6, "Delete Job");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 0) {
                CompanyPostDetailActivity.launch(this, postId, post);
                return true;
            }
            if (id == 6) {
                CompanyApplicantsActivity.launchForPost(this, postId, title);
                return true;
            }
            if (id == 1) {
                PostInternshipActivity.launchEdit(this, post);
                return true;
            }
            if (id == 2 && postId > 0) {
                submitDraftPost(postId);
                return true;
            }
            if (id == 3 && postId > 0) {
                duplicatePost(postId);
                return true;
            }
            if (id == 4 && postId > 0) {
                toggleClosePost(postId);
                return true;
            }
            if (id == 5 && postId > 0) {
                confirmDeletePost(postId, title);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void submitDraftPost(int postId) {
        repo.submitPost(postId, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                toast("Submitted for admin review!");
                loadListings();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                toast(error.message);
            }
        });
    }

    private void duplicatePost(int postId) {
        repo.duplicatePost(postId, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                toast("Duplicated as draft!");
                loadListings();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                toast(error.message);
            }
        });
    }

    private void toggleClosePost(int postId) {
        repo.toggleClosePost(postId, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                toast(Json.str(body, "Listing status updated!", "message"));
                loadListings();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                toast(error.message);
            }
        });
    }

    private void confirmDeletePost(int postId, String title) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Listing")
                .setMessage("Are you sure you want to delete \"" + title + "\"? This action cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> repo.deletePost(postId, new CompanyRepository.JsonResult() {
                    @Override
                    public void onSuccess(@NonNull JsonObject body) {
                        toast("Listing deleted.");
                        loadListings();
                    }

                    @Override
                    public void onError(@NonNull ApiError error) {
                        toast(error.message);
                    }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
        if (containerListings != null) containerListings.setVisibility(View.VISIBLE);
        if (scrollRefresh != null) {
            scrollRefresh.setRefreshing(show);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

