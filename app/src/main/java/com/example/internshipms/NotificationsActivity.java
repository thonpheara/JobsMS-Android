package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.StudentRepository;
import com.example.internshipms.session.SessionManager;
import com.example.internshipms.view.PullRefreshScrollView;
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

public class NotificationsActivity extends AppCompatActivity {

    private static final String KEY_ALL = "all";
    private static final String KEY_UNREAD = "unread";

    private StudentRepository repository;
    private Call<JsonObject> listCall;
    private Call<JsonObject> actionCall;
    private final List<JsonObject> allNotifications = new ArrayList<>();
    private String selectedFilter = KEY_ALL;

    private TextView tvUnreadBadge;
    private View progress;
    private View layoutEmpty;
    private ViewGroup container;
    private ChipGroup chipGroupFilter;

    public static void launch(Context context) {
        context.startActivity(new Intent(context, NotificationsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.get(this).isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repository = new StudentRepository(this);
        tvUnreadBadge = findViewById(R.id.tvUnreadBadge);
        progress = findViewById(R.id.progressNotifications);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        container = findViewById(R.id.containerNotifications);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMarkAllRead).setOnClickListener(v -> markAllRead());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> confirmClearAll());

        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadNotifications);
        }

        rebuildFilterChips(0);
        chipGroupFilter.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof String) {
                selectedFilter = (String) chip.getTag();
                render();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void loadNotifications() {
        if (listCall != null) listCall.cancel();
        PullRefreshScrollView scrollRefresh = findViewById(R.id.scrollRefresh);
        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);
        listCall = repository.notifications(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonObject d = Json.data(body);
                int unread = Json.integer(d, 0, "unread_count");

                allNotifications.clear();
                JsonArray arr = Json.arr(d, "notifications", "data");
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) allNotifications.add(el.getAsJsonObject());
                }

                updateBadge(unread);
                rebuildFilterChips(unread);
                render();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                Snackbar.make(findViewById(R.id.main), error.message, Snackbar.LENGTH_LONG)
                        .setAction("Retry", v -> loadNotifications())
                        .show();
            }
        });
    }

    private void updateBadge(int unread) {
        tvUnreadBadge.setText(unread > 0 ? unread + " Unread" : "All Read");
        findViewById(R.id.btnMarkAllRead).setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnClearAll).setVisibility(
                allNotifications.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void rebuildFilterChips(int unreadCount) {
        String[][] defs = {
                {KEY_ALL, "All (" + allNotifications.size() + ")"},
                {KEY_UNREAD, "Unread (" + unreadCount + ")"}
        };

        chipGroupFilter.removeAllViews();
        for (String[] def : defs) {
            Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.item_filter_chip, chipGroupFilter, false);
            chip.setId(View.generateViewId());
            chip.setTag(def[0]);
            chip.setText(def[1]);
            chip.setChecked(def[0].equals(selectedFilter));
            chipGroupFilter.addView(chip);
        }
    }

    private void render() {
        container.removeAllViews();
        int shown = 0;
        for (JsonObject notif : allNotifications) {
            boolean isUnread = !notif.has("read_at") || notif.get("read_at").isJsonNull();
            if (KEY_UNREAD.equals(selectedFilter) && !isUnread) {
                continue;
            }
            addNotificationCard(notif, isUnread);
            shown++;
        }
        layoutEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private void addNotificationCard(JsonObject notif, boolean isUnread) {
        View item = LayoutInflater.from(this)
                .inflate(R.layout.item_notification, container, false);

        String id = Json.str(notif, "", "id");
        String createdAt = Json.str(notif, null, "created_at");

        JsonObject payload = Json.obj(notif, "data");
        if (payload == null) payload = notif;

        String title = Json.str(payload, "Notification", "title", "subject");
        String message = Json.str(payload, "", "message", "body", "description");
        String color = Json.str(payload, "green", "color").toLowerCase(Locale.ROOT);
        String actionUrl = Json.str(payload, "", "action_url", "actionUrl", "url");

        MaterialCardView card = item.findViewById(R.id.cardNotification);
        if (isUnread) {
            int green = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
            card.setStrokeColor(green);
            card.setStrokeWidth(Math.round(1.5f * getResources().getDisplayMetrics().density));
        }

        ((TextView) item.findViewById(R.id.tvNotifTitle)).setText(title);
        ((TextView) item.findViewById(R.id.tvNotifBody)).setText(message);
        ((TextView) item.findViewById(R.id.tvNotifTime)).setText(
                ConversationsActivity.shortRelativeTime(createdAt));
        item.findViewById(R.id.dotUnreadItem)
                .setVisibility(isUnread ? View.VISIBLE : View.GONE);

        styleIcon(item, color, title);

        item.setOnClickListener(v -> {
            if (isUnread && !id.isEmpty()) {
                repository.markNotificationRead(id, new StudentRepository.JsonResult() {
                    @Override
                    public void onSuccess(@NonNull JsonObject body) {
                    }

                    @Override
                    public void onError(@NonNull ApiError error) {
                    }
                });
            }
            openDestination(actionUrl, title);
        });

        item.findViewById(R.id.btnDeleteItem).setOnClickListener(v -> {
            if (!id.isEmpty()) deleteOne(id);
        });

        container.addView(item);
    }

    private void styleIcon(View item, String color, String title) {
        MaterialCardView iconBox = item.findViewById(R.id.iconBox);
        ImageView ivIcon = item.findViewById(R.id.ivIcon);
        String lowerTitle = title.toLowerCase(Locale.ROOT);

        @ColorRes int bgRes;
        int tintColor;
        @DrawableRes int iconRes;
        int primaryGreen = MaterialColors.getColor(item, androidx.appcompat.R.attr.colorPrimary);

        if (color.contains("blue") || color.contains("indigo")
                || lowerTitle.contains("interview") || lowerTitle.contains("shortlist")
                || lowerTitle.contains("message")) {
            bgRes = R.color.tint_indigo_bg;
            tintColor = ContextCompat.getColor(this, R.color.tint_indigo);
            iconRes = lowerTitle.contains("message") ? R.drawable.ic_messages : R.drawable.ic_event;
        } else if (color.contains("amber") || color.contains("yellow")
                || lowerTitle.contains("review") || lowerTitle.contains("pending")) {
            bgRes = R.color.tint_amber_bg;
            tintColor = ContextCompat.getColor(this, R.color.tint_amber);
            iconRes = R.drawable.ic_schedule;
        } else if (color.contains("red") || lowerTitle.contains("reject")) {
            bgRes = R.color.tint_red_bg;
            tintColor = ContextCompat.getColor(this, R.color.tint_red);
            iconRes = R.drawable.ic_notifications;
        } else {
            bgRes = R.color.tint_green_bg;
            tintColor = primaryGreen;
            iconRes = R.drawable.ic_check_circle;
        }

        iconBox.setCardBackgroundColor(ContextCompat.getColor(this, bgRes));
        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(tintColor);
    }

    private void openDestination(String actionUrl, String title) {
        String lower = (actionUrl + " " + title).toLowerCase(Locale.ROOT);
        if (lower.contains("application") || lower.contains("interview")
                || lower.contains("shortlist") || lower.contains("accept")
                || lower.contains("reject") || lower.contains("status")) {
            startActivity(new Intent(this, StudentApplicationsActivity.class));
        } else if (lower.contains("message") || lower.contains("conversation") || lower.contains("chat")) {
            startActivity(new Intent(this, ConversationsActivity.class));
        } else if (lower.contains("profile") || lower.contains("resume")) {
            startActivity(new Intent(this, StudentProfileActivity.class));
        } else if (lower.contains("post") || lower.contains("explore")) {
            startActivity(new Intent(this, StudentExploreActivity.class));
        } else {
            startActivity(new Intent(this, StudentApplicationsActivity.class));
        }
        finish();
    }

    private void markAllRead() {
        if (actionCall != null) actionCall.cancel();
        actionCall = repository.markAllNotificationsRead(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                loadNotifications();
                Toast.makeText(NotificationsActivity.this,
                        "All notifications marked as read.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                Toast.makeText(NotificationsActivity.this, error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteOne(String id) {
        if (actionCall != null) actionCall.cancel();
        actionCall = repository.deleteNotification(id, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                loadNotifications();
                Toast.makeText(NotificationsActivity.this,
                        "Notification removed.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                Toast.makeText(NotificationsActivity.this, error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmClearAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Notifications")
                .setMessage("Are you sure you want to clear all notifications?")
                .setPositiveButton("Clear All", (d, w) -> {
                    if (actionCall != null) actionCall.cancel();
                    actionCall = repository.clearAllNotifications(new StudentRepository.JsonResult() {
                        @Override
                        public void onSuccess(@NonNull JsonObject body) {
                            allNotifications.clear();
                            updateBadge(0);
                            rebuildFilterChips(0);
                            render();
                            Toast.makeText(NotificationsActivity.this,
                                    "All notifications cleared.", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(@NonNull ApiError error) {
                            Toast.makeText(NotificationsActivity.this,
                                    error.message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (listCall != null) listCall.cancel();
        if (actionCall != null) actionCall.cancel();
        super.onDestroy();
    }
}
