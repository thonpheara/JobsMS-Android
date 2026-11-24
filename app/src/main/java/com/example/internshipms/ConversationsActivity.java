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

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;

public class ConversationsActivity extends AppCompatActivity {

    private static final String FILTER_ALL = "all";
    private static final String FILTER_UNREAD = "unread";
    private static final String FILTER_INTERVIEWS = "interviews";

    private StudentRepository repository;
    private Call<JsonObject> convosCall;
    private final List<JsonObject> allConversations = new ArrayList<>();
    private String selectedFilter = FILTER_ALL;
    private EditText etSearch;
    private ChipGroup chipGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_conversations);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repository = new StudentRepository(this);
        boolean isCompany = "company".equalsIgnoreCase(session.getRole());

        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));

        View containerTopProfile = findViewById(R.id.containerTopProfile);
        if (isCompany) {
            if (containerTopProfile != null) {
                containerTopProfile.setVisibility(View.GONE);
            }
        } else {
            if (containerTopProfile != null) {
                containerTopProfile.setVisibility(View.VISIBLE);
            }
            View btnTopProfile = findViewById(R.id.btnTopProfile);
            if (btnTopProfile != null) {
                btnTopProfile.setOnClickListener(v ->
                        Navigator.switchTab(this, StudentProfileActivity.class, true));
            }
        }

        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadConversations);
        }

        etSearch = findViewById(R.id.etSearchConversations);
        chipGroup = findViewById(R.id.chipGroupConversations);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    render();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
        rebuildFilterChips();
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
                if (ids.isEmpty()) return;
                Chip chip = group.findViewById(ids.get(0));
                if (chip != null && chip.getTag() instanceof String) {
                    selectedFilter = (String) chip.getTag();
                    render();
                }
            });
        }

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.getMenu().clear();
        nav.inflateMenu(isCompany ? R.menu.menu_company_nav : R.menu.menu_student_nav);
        nav.setSelectedItemId(R.id.nav_messages);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Navigator.switchTab(this,
                        isCompany ? CompanyDashboardActivity.class : StudentDashboardActivity.class, true);
                return true;
            }
            if (id == R.id.nav_listings) {
                Navigator.switchTab(this, CompanyListingsActivity.class, true);
                return true;
            }
            if (id == R.id.nav_applicants) {
                Navigator.switchTab(this, CompanyApplicantsActivity.class, true);
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
            if (id == R.id.nav_profile) {
                if (isCompany) {
                    Navigator.switchTab(this, CompanyProfileActivity.class, true);
                    return true;
                }
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
        if (!"company".equalsIgnoreCase(session.getRole())) {
            AvatarLoader.bind(session.getAvatarUrl(),
                    findViewById(R.id.ivTopProfileAvatar),
                    findViewById(R.id.ivTopProfileIcon));
        }
        loadConversations();
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

    private void loadConversations() {
        if (convosCall != null) convosCall.cancel();
        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);

        convosCall = repository.conversations(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                allConversations.clear();
                JsonArray arr = Json.list(body, "conversations", "data");
                AvatarLoader.syncCompanyLogosFromConversations(arr);
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) allConversations.add(el.getAsJsonObject());
                }
                rebuildFilterChips();
                render();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                Snackbar.make(findViewById(R.id.main), error.message, Snackbar.LENGTH_LONG)
                        .setAction("Retry", v -> loadConversations())
                        .show();
            }
        });
    }

    private void rebuildFilterChips() {
        if (chipGroup == null) return;
        int total = allConversations.size();
        int unread = 0;
        int interviews = 0;
        for (JsonObject c : allConversations) {
            if (Json.integer(c, 0, "unread_count") > 0) unread++;
            if (isInterviewOrApplication(c)) interviews++;
        }

        String[][] defs = {
                {FILTER_ALL, "All Messages  " + total},
                {FILTER_UNREAD, "Unread  " + unread},
                {FILTER_INTERVIEWS, "Interviews  " + interviews}
        };

        chipGroup.removeAllViews();
        for (String[] def : defs) {
            Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.item_filter_chip, chipGroup, false);
            chip.setId(View.generateViewId());
            chip.setTag(def[0]);
            chip.setText(def[1]);
            chip.setChecked(def[0].equals(selectedFilter));
            chipGroup.addView(chip);
        }
    }

    private static boolean isInterviewOrApplication(JsonObject convo) {
        if (Json.integer(convo, 0, "application_id") > 0) return true;
        String subject = Json.str(convo, "", "post_title", "subject").toLowerCase(Locale.ROOT);
        JsonObject latestMsg = Json.obj(convo, "latest_message");
        String body = latestMsg != null
                ? Json.str(latestMsg, "", "body").toLowerCase(Locale.ROOT) : "";
        return subject.contains("interview") || subject.contains("intern")
                || body.contains("interview") || body.contains("schedule");
    }

    private void render() {
        TextView tvActive = findViewById(R.id.tvActiveBadge);
        if (tvActive != null) {
            tvActive.setText(allConversations.size() + " active");
        }

        String query = etSearch != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";

        ViewGroup container = findViewById(R.id.containerConversations);
        container.removeAllViews();

        int shown = 0;
        for (JsonObject convo : allConversations) {
            int unreadCount = Json.integer(convo, 0, "unread_count");
            if (FILTER_UNREAD.equals(selectedFilter) && unreadCount <= 0) continue;
            if (FILTER_INTERVIEWS.equals(selectedFilter) && !isInterviewOrApplication(convo)) continue;

            if (!query.isEmpty()) {
                JsonObject recipient = Json.obj(convo, "recipient");
                String name = Json.str(recipient, "", "display_name", "name").toLowerCase(Locale.ROOT);
                String subject = Json.str(convo, "", "post_title", "subject").toLowerCase(Locale.ROOT);
                JsonObject latestMsg = Json.obj(convo, "latest_message");
                String snippet = latestMsg != null
                        ? Json.str(latestMsg, "", "body").toLowerCase(Locale.ROOT) : "";
                if (!name.contains(query) && !subject.contains(query) && !snippet.contains(query)) {
                    continue;
                }
            }

            container.addView(buildRow(convo, container));
            shown++;
        }
        findViewById(R.id.tvEmpty).setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private View buildRow(JsonObject convo, ViewGroup parent) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_conversation, parent, false);

        int convoId = Json.integer(convo, 0, "id");
        int appId = Json.integer(convo, 0, "application_id");
        int unreadCount = Json.integer(convo, 0, "unread_count");
        JsonObject recipient = Json.obj(convo, "recipient");
        String name = Json.str(recipient, "Company", "display_name", "name");
        String avatarUrl = AvatarLoader.extractUrl(recipient, null);
        String subject = Json.str(convo, "Internship Application", "post_title", "subject");
        String lastAt = Json.str(convo, null, "last_message_at");

        JsonObject latestMsg = Json.obj(convo, "latest_message");
        String snippet = latestMsg != null
                ? Json.str(latestMsg, "Start this conversation...", "body")
                : "Start this conversation...";

        MaterialCardView avatarCard = row.findViewById(R.id.avatarCard);
        TextView tvInitials = row.findViewById(R.id.tvInitials);
        ImageView ivAvatar = row.findViewById(R.id.ivAvatar);
        int green = MaterialColors.getColor(row, androidx.appcompat.R.attr.colorPrimary);

        int hash = Math.abs(name.hashCode()) % 3;
        if (hash == 0) {
            avatarCard.setCardBackgroundColor(getColor(R.color.tint_green_bg));
            tvInitials.setTextColor(green);
        } else if (hash == 1) {
            avatarCard.setCardBackgroundColor(getColor(R.color.tint_amber_bg));
            tvInitials.setTextColor(getColor(R.color.tint_amber));
        } else {
            avatarCard.setCardBackgroundColor(getColor(R.color.tint_indigo_bg));
            tvInitials.setTextColor(getColor(R.color.tint_indigo));
        }

        tvInitials.setText(initials(name));
        AvatarLoader.bind(avatarUrl, ivAvatar, tvInitials);

        ((TextView) row.findViewById(R.id.tvName)).setText(name);
        ((TextView) row.findViewById(R.id.tvTag1)).setText(subject);

        TextView tvTime = row.findViewById(R.id.tvTime);
        tvTime.setText(shortRelativeTime(lastAt));
        if (unreadCount > 0) {
            tvTime.setTextColor(green);
            tvTime.setTypeface(tvTime.getTypeface(), android.graphics.Typeface.BOLD);
        }

        TextView tvSnippet = row.findViewById(R.id.tvSnippet);
        tvSnippet.setText(snippet);
        if (unreadCount > 0) {
            tvSnippet.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurface));
            tvSnippet.setTypeface(tvSnippet.getTypeface(), android.graphics.Typeface.BOLD);
        }

        View badge = row.findViewById(R.id.badgeUnread);
        if (unreadCount > 0) {
            badge.setVisibility(View.VISIBLE);
            ((TextView) row.findViewById(R.id.tvUnreadCount)).setText(String.valueOf(unreadCount));
        } else {
            badge.setVisibility(View.GONE);
        }

        row.setOnClickListener(v ->
                ChatActivity.launch(this, convoId, appId, name, subject, avatarUrl));
        return row;
    }

    /** Formats timestamps identically to the Web UI ("1mo", "2w", "3d", "5h", "10m", "now"). */
    public static String shortRelativeTime(@Nullable String iso) {
        Date d = StudentDashboardActivity.parseDate(iso);
        if (d == null) return "";
        long diffSec = Math.max(0L, (System.currentTimeMillis() - d.getTime()) / 1000L);
        if (diffSec < 60) return "now";
        long mins = diffSec / 60;
        if (mins < 60) return mins + "m";
        long hours = mins / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        if (days < 7) return days + "d";
        long weeks = days / 7;
        if (days < 30) return weeks + "w";
        long months = Math.max(1, days / 30);
        if (months < 12) return months + "mo";
        return (days / 365) + "y";
    }

    private static String initials(String text) {
        String[] words = text.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "U";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    @Override
    protected void onDestroy() {
        if (convosCall != null) convosCall.cancel();
        super.onDestroy();
    }
}
