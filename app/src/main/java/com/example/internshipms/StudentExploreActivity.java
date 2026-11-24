package com.example.internshipms;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
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
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;

public class StudentExploreActivity extends AppCompatActivity {

    private static final String ALL = "All Roles";
    private static final String SAVED = "★ Saved";

    private static final int[] BG_PALETTE = {
            R.color.tint_green_bg, R.color.tint_indigo_bg, R.color.tint_amber_bg
    };
    private static final int[] TEXT_PALETTE = {
            0, R.color.tint_indigo, R.color.tint_amber
    };

    private StudentRepository repository;
    private Call<JsonObject> postsCall;
    private final List<JsonObject> allPosts = new ArrayList<>();
    private final Set<String> knownCategories = new LinkedHashSet<>();
    private String selectedCategory = ALL;
    private String sortFilter = "newest";
    private String deadlineFilter = "open";
    private String stipendModeFilter = "";
    private int minStipendFilter = 0;
    private EditText etSearch;
    private ChipGroup chipGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.get(this).isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_explore);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repository = new StudentRepository(this);
        etSearch = findViewById(R.id.etSearch);
        chipGroup = findViewById(R.id.chipGroup);

        String initialSearch = getIntent() != null ? getIntent().getStringExtra("search_query") : null;
        if (initialSearch != null && !initialSearch.isEmpty()) {
            etSearch.setText(initialSearch);
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        rebuildCategoryChips(knownCategories);
        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null) {
                String picked = chip.getText().toString();
                boolean switchedSaved = SAVED.equals(picked) != SAVED.equals(selectedCategory);
                selectedCategory = picked;
                if (switchedSaved) {
                    loadPosts();
                } else {
                    render();
                }
            }
        });

        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));
        findViewById(R.id.btnSearchFilter).setOnClickListener(v ->
                FilterCohortsBottomSheet.showAdvanced(getSupportFragmentManager(),
                        sortFilter, deadlineFilter, stipendModeFilter, minStipendFilter,
                        (sort, deadline, stipendMode, minStipend) -> {
                            sortFilter = sort;
                            deadlineFilter = deadline;
                            stipendModeFilter = stipendMode;
                            minStipendFilter = minStipend;
                            loadPosts();
                        }));
        findViewById(R.id.btnTopProfile).setOnClickListener(v ->
                Navigator.switchTab(this, StudentProfileActivity.class, true));

        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadPosts);
        }

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_explore);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Navigator.switchTab(this, StudentDashboardActivity.class, true);
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
        loadPosts();
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

    private void loadPosts() {
        if (postsCall != null) postsCall.cancel();
        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);

        StudentRepository.JsonResult callback = new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                allPosts.clear();
                JsonArray arr = Json.list(body, "posts", "data");
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) continue;
                    JsonObject p = e.getAsJsonObject();
                    allPosts.add(p);
                    String cat = Json.str(p, null, "category");
                    if (cat != null && !StudentDashboardActivity.isWorkMode(cat)) {
                        knownCategories.add(cat);
                    }
                }
                rebuildCategoryChips(knownCategories);
                render();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                Snackbar.make(findViewById(R.id.main), error.message, Snackbar.LENGTH_LONG)
                        .setAction("Retry", v -> loadPosts())
                        .show();
            }
        };

        if (SAVED.equals(selectedCategory)) {
            postsCall = repository.bookmarks(callback);
        } else {
            postsCall = repository.postsAdvanced(
                    null,
                    null,
                    deadlineFilter,
                    stipendModeFilter,
                    "min".equals(stipendModeFilter) && minStipendFilter > 0 ? minStipendFilter : null,
                    sortFilter,
                    callback);
        }

        repository.conversations(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonArray arr = Json.list(body, "conversations", "data");
                if (AvatarLoader.syncCompanyLogosFromConversations(arr) && !allPosts.isEmpty()) {
                    render();
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    private void rebuildCategoryChips(Set<String> dynamicCategories) {
        chipGroup.removeAllViews();
        List<String> list = new ArrayList<>();
        list.add(ALL);
        list.add(SAVED);
        list.addAll(dynamicCategories);
        if (!list.contains(selectedCategory)) selectedCategory = ALL;

        for (String category : list) {
            Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.item_filter_chip, chipGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(category);
            chip.setChecked(category.equals(selectedCategory));
            chipGroup.addView(chip);
        }
    }

    private void render() {
        ViewGroup container = findViewById(R.id.containerCohorts);
        container.removeAllViews();
        String query = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);

        int shown = 0;
        for (int i = 0; i < allPosts.size(); i++) {
            JsonObject post = allPosts.get(i);
            String title = Json.str(post, "Internship Position", "title");
            String company = StudentDashboardActivity.companyName(post, post);
            String location = Json.str(post, "Cambodia", "location");
            String category = Json.str(post, "General", "category");
            String description = Json.str(post, "", "description");
            boolean isBookmarked = post.has("is_bookmarked")
                    && !post.get("is_bookmarked").isJsonNull()
                    && post.get("is_bookmarked").getAsBoolean();

            boolean categoryOk = selectedCategory.equals(ALL)
                    || (selectedCategory.equals(SAVED) && isBookmarked)
                    || selectedCategory.equalsIgnoreCase(category);
            boolean queryOk = query.isEmpty()
                    || title.toLowerCase(Locale.ROOT).contains(query)
                    || company.toLowerCase(Locale.ROOT).contains(query)
                    || location.toLowerCase(Locale.ROOT).contains(query)
                    || category.toLowerCase(Locale.ROOT).contains(query)
                    || description.toLowerCase(Locale.ROOT).contains(query);

            if (categoryOk && queryOk) {
                container.addView(buildCard(post, i, container));
                shown++;
            }
        }
        TextView tvCount = findViewById(R.id.tvCohortCount);
        if (tvCount != null) {
            tvCount.setText(shown + (SAVED.equals(selectedCategory) ? " Saved" : " Available"));
        }
        findViewById(R.id.tvEmpty).setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private View buildCard(JsonObject post, int index, ViewGroup parent) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_cohort, parent, false);

        int postId = Json.integer(post, 0, "id");
        int compId = StudentDashboardActivity.companyId(post, post);
        String title = Json.str(post, "Internship Position", "title");
        String company = StudentDashboardActivity.companyName(post, post);
        String location = Json.str(post, "Cambodia", "location");
        String typeOrDuration = StudentDashboardActivity.formatTypeOrCategory(post);
        String categoryOrPay = Json.str(post, null, "stipend", "salary", "allowance", "category");
        if (categoryOrPay == null || StudentDashboardActivity.isWorkMode(categoryOrPay)) {
            categoryOrPay = StudentDashboardActivity.formatPostBadge(post);
        }
        String openings = formatOpenings(post);
        String deadline = StudentDashboardActivity.formatDeadlineDate(
                Json.str(post, "—", "deadline"));
        String description = Json.str(post, "", "description");
        String requirements = Json.str(post, "", "requirements");
        boolean hasApplied = post.has("has_applied")
                && !post.get("has_applied").isJsonNull()
                && post.get("has_applied").getAsBoolean();
        boolean isBookmarked = post.has("is_bookmarked")
                && !post.get("is_bookmarked").isJsonNull()
                && post.get("is_bookmarked").getAsBoolean();

        @ColorRes int badgeBg = BG_PALETTE[index % BG_PALETTE.length];
        @ColorRes int badgeText = TEXT_PALETTE[index % TEXT_PALETTE.length];

        MaterialCardView logoCard = item.findViewById(R.id.logoCard);
        TextView tvInitial = item.findViewById(R.id.tvInitial);
        ImageView ivCompanyLogo = item.findViewById(R.id.ivCompanyLogo);
        int primaryGreen = MaterialColors.getColor(item, androidx.appcompat.R.attr.colorPrimary);

        logoCard.setCardBackgroundColor(ContextCompat.getColor(this, badgeBg));
        tvInitial.setText(StudentDashboardActivity.initials(company));
        tvInitial.setTextColor(badgeText == 0
                ? primaryGreen : ContextCompat.getColor(this, badgeText));
        String logoUrl = AvatarLoader.extractCompanyLogo(post, post, company);
        AvatarLoader.bind(logoUrl, ivCompanyLogo, tvInitial);

        ((TextView) item.findViewById(R.id.tvTitle)).setText(title);
        TextView tvCompany = item.findViewById(R.id.tvCompany);
        tvCompany.setText(company + "  •  " + location);
        if (compId > 0) {
            View.OnClickListener openCompany = v ->
                    CompanyProfileBottomSheet.show(getSupportFragmentManager(), compId, company, location);
            tvCompany.setOnClickListener(openCompany);
            logoCard.setOnClickListener(openCompany);
        }

        MaterialCardView btnBookmark = item.findViewById(R.id.btnBookmark);
        ImageView ivBookmark = item.findViewById(R.id.ivBookmark);
        styleBookmarkButton(btnBookmark, ivBookmark, isBookmarked, primaryGreen);
        btnBookmark.setOnClickListener(v -> {
            if (postId <= 0) return;
            boolean current = post.has("is_bookmarked")
                    && !post.get("is_bookmarked").isJsonNull()
                    && post.get("is_bookmarked").getAsBoolean();
            boolean next = !current;
            post.addProperty("is_bookmarked", next);
            styleBookmarkButton(btnBookmark, ivBookmark, next, primaryGreen);
            repository.toggleBookmark(postId, new StudentRepository.JsonResult() {
                @Override
                public void onSuccess(@NonNull JsonObject body) {
                    JsonObject d = Json.data(body);
                    boolean saved = d.has("bookmarked") && !d.get("bookmarked").isJsonNull()
                            ? d.get("bookmarked").getAsBoolean() : next;
                    post.addProperty("is_bookmarked", saved);
                    styleBookmarkButton(btnBookmark, ivBookmark, saved, primaryGreen);
                    Toast.makeText(StudentExploreActivity.this,
                            saved ? "Saved to bookmarks ★" : "Removed from bookmarks",
                            Toast.LENGTH_SHORT).show();
                    if (SAVED.equals(selectedCategory) && !saved) {
                        render();
                    }
                }

                @Override
                public void onError(@NonNull ApiError error) {
                    post.addProperty("is_bookmarked", current);
                    styleBookmarkButton(btnBookmark, ivBookmark, current, primaryGreen);
                    Toast.makeText(StudentExploreActivity.this, error.message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        TextView tvWeeks = item.findViewById(R.id.tvWeeks);
        String explicitDuration = Json.str(post, null, "duration", "duration_text");
        if (explicitDuration != null && !StudentDashboardActivity.isWorkMode(explicitDuration)) {
            tvWeeks.setVisibility(View.VISIBLE);
            tvWeeks.setText(explicitDuration);
        } else {
            tvWeeks.setVisibility(View.GONE);
        }
        ((TextView) item.findViewById(R.id.tvPay)).setText(categoryOrPay);
        ((TextView) item.findViewById(R.id.tvOpenings)).setText(openings);
        ((TextView) item.findViewById(R.id.tvDeadline)).setText(deadline);

        final String finalBadge = categoryOrPay;
        MaterialButton btnApply = item.findViewById(R.id.btnApply);
        if (hasApplied) {
            btnApply.setText("Applied ✓");
            btnApply.setEnabled(false);
        } else {
            btnApply.setText("Apply Now");
            btnApply.setEnabled(true);
            btnApply.setOnClickListener(v ->
                    ApplyInternshipBottomSheet.show(getSupportFragmentManager(),
                            postId, title, company, location, this::loadPosts));
        }

        item.setOnClickListener(v -> InternshipDetailActivity.launch(
                this, postId, title, company, location, typeOrDuration, finalBadge,
                deadline, description, requirements, hasApplied,
                post.has("is_bookmarked") && !post.get("is_bookmarked").isJsonNull()
                        && post.get("is_bookmarked").getAsBoolean(),
                compId));
        return item;
    }

    private void styleBookmarkButton(MaterialCardView card, ImageView icon,
                                     boolean bookmarked, int primaryGreen) {
        if (bookmarked) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_green_bg));
            card.setStrokeColor(primaryGreen);
            icon.setImageResource(R.drawable.ic_bookmark_filled);
            icon.setColorFilter(primaryGreen);
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_page));
            card.setStrokeColor(ContextCompat.getColor(this, R.color.stroke_soft));
            icon.setImageResource(R.drawable.ic_bookmark_outline);
            icon.setColorFilter(ContextCompat.getColor(this, R.color.nav_unselected));
        }
    }

    private static String formatOpenings(JsonObject post) {
        int slots = Json.integer(post, -1, "positions", "openings", "slots", "vacancies");
        if (slots > 0) return slots + (slots == 1 ? " Opening" : " Openings");
        String cat = Json.str(post, null, "category");
        if (cat != null && !StudentDashboardActivity.isWorkMode(cat)) return cat;
        return "Open";
    }

    @Override
    protected void onDestroy() {
        if (postsCall != null) postsCall.cancel();
        super.onDestroy();
    }
}
