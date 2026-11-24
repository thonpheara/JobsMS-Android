package com.example.internshipms;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.StudentRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

import retrofit2.Call;

public class NotificationsBottomSheet extends BottomSheetDialogFragment {

    private StudentRepository repository;
    private Call<JsonObject> listCall;
    private Call<JsonObject> actionCall;
    private TextView tvBadge;
    private View progress;
    private View emptyView;
    private ViewGroup container;

    public static void show(FragmentManager fm) {
        new NotificationsBottomSheet().show(fm, "notifications_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = new StudentRepository(requireContext());
        tvBadge = view.findViewById(R.id.tvUnreadBadge);
        progress = view.findViewById(R.id.progressNotifications);
        emptyView = view.findViewById(R.id.tvNotificationsEmpty);
        container = view.findViewById(R.id.containerNotifications);

        view.findViewById(R.id.btnMarkAllRead).setOnClickListener(v -> markAllRead());
        view.findViewById(R.id.btnClearAll).setOnClickListener(v -> clearAll());

        loadNotifications();
    }

    private void loadNotifications() {
        if (listCall != null) listCall.cancel();
        progress.setVisibility(View.VISIBLE);
        listCall = repository.notifications(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                JsonObject d = Json.data(body);
                int unread = Json.integer(d, 0, "unread_count");
                updateBadge(unread);

                JsonArray arr = Json.arr(d, "notifications", "data");
                renderList(arr);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBadge(int unread) {
        tvBadge.setText(unread > 0 ? unread + " New" : "All Read");
        if (getActivity() != null) {
            View dot = getActivity().findViewById(R.id.dotUnread);
            if (dot != null) {
                dot.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void renderList(JsonArray notifications) {
        container.removeAllViews();
        int shown = 0;
        for (JsonElement el : notifications) {
            if (!el.isJsonObject()) continue;
            addNotificationCard(el.getAsJsonObject());
            shown++;
        }
        emptyView.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private void addNotificationCard(JsonObject notif) {
        View item = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_notification, container, false);

        String id = Json.str(notif, "", "id");
        boolean isUnread = !notif.has("read_at") || notif.get("read_at").isJsonNull();
        String createdAt = Json.str(notif, null, "created_at");

        JsonObject payload = Json.obj(notif, "data");
        if (payload == null) payload = notif;

        String title = Json.str(payload, "Notification", "title", "subject");
        String message = Json.str(payload, "", "message", "body", "description");
        String color = Json.str(payload, "green", "color").toLowerCase(Locale.ROOT);
        String actionUrl = Json.str(payload, "", "action_url", "actionUrl", "url");

        ((TextView) item.findViewById(R.id.tvNotifTitle)).setText(title);
        ((TextView) item.findViewById(R.id.tvNotifBody)).setText(message);
        ((TextView) item.findViewById(R.id.tvNotifTime)).setText(
                StudentDashboardActivity.relativeTime(createdAt));
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

        item.setOnLongClickListener(v -> {
            if (!id.isEmpty()) {
                deleteOne(id);
            }
            return true;
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

        if (color.contains("blue") || color.contains("indigo") || lowerTitle.contains("interview")
                || lowerTitle.contains("shortlist")) {
            bgRes = R.color.tint_indigo_bg;
            tintColor = ContextCompat.getColor(requireContext(), R.color.tint_indigo);
            iconRes = R.drawable.ic_event;
        } else if (color.contains("amber") || color.contains("yellow") || lowerTitle.contains("review")) {
            bgRes = R.color.tint_amber_bg;
            tintColor = ContextCompat.getColor(requireContext(), R.color.tint_amber);
            iconRes = R.drawable.ic_schedule;
        } else if (color.contains("red") || lowerTitle.contains("reject")) {
            bgRes = R.color.tint_red_bg;
            tintColor = ContextCompat.getColor(requireContext(), R.color.tint_red);
            iconRes = R.drawable.ic_notifications;
        } else {
            bgRes = R.color.tint_green_bg;
            tintColor = primaryGreen;
            iconRes = R.drawable.ic_check_circle;
        }

        iconBox.setCardBackgroundColor(ContextCompat.getColor(requireContext(), bgRes));
        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(tintColor);
    }

    private void openDestination(String actionUrl, String title) {
        dismiss();
        if (!isAdded()) return;
        String lower = (actionUrl + " " + title).toLowerCase(Locale.ROOT);
        if (lower.contains("message") || lower.contains("conversation") || lower.contains("chat")) {
            startActivity(new Intent(requireContext(), ConversationsActivity.class));
        } else if (lower.contains("profile") || lower.contains("resume")) {
            startActivity(new Intent(requireContext(), StudentProfileActivity.class));
        } else if (lower.contains("post") || lower.contains("explore")) {
            startActivity(new Intent(requireContext(), StudentExploreActivity.class));
        } else {
            startActivity(new Intent(requireContext(), StudentApplicationsActivity.class));
        }
    }

    private void markAllRead() {
        if (actionCall != null) actionCall.cancel();
        actionCall = repository.markAllNotificationsRead(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (!isAdded()) return;
                updateBadge(0);
                loadNotifications();
                Toast.makeText(requireContext(), "All notifications marked as read.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteOne(String id) {
        if (actionCall != null) actionCall.cancel();
        actionCall = repository.deleteNotification(id, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (!isAdded()) return;
                loadNotifications();
                Toast.makeText(requireContext(), "Notification deleted.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearAll() {
        if (actionCall != null) actionCall.cancel();
        actionCall = repository.clearAllNotifications(new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (!isAdded()) return;
                updateBadge(0);
                container.removeAllViews();
                emptyView.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), "All notifications cleared.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (listCall != null) listCall.cancel();
        if (actionCall != null) actionCall.cancel();
        super.onDestroyView();
    }
}
