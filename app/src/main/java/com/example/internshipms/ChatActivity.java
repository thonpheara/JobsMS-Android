package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.example.internshipms.repository.StudentRepository;
import com.example.internshipms.session.SessionManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONVO_ID = "extra_convo_id";
    public static final String EXTRA_APP_ID = "extra_app_id";
    public static final String EXTRA_PARTNER_NAME = "extra_partner_name";
    public static final String EXTRA_SUBJECT = "extra_subject";
    public static final String EXTRA_AVATAR_URL = "extra_avatar_url";
    public static final String EXTRA_ONLINE = "extra_online";

    private StudentRepository repository;
    private Call<JsonObject> loadCall;
    private Call<JsonObject> sendCall;
    private int conversationId;
    private int applicationId;
    private String partnerName = "Company";
    private String currentSubject = "Internship Position";
    private String partnerAvatarUrl;

    public static void launch(Context context, String partnerName, boolean online) {
        launch(context, 0, 0, partnerName, "Internship Position", null);
    }

    public static void launch(Context context, int conversationId, String partnerName,
                              @Nullable String subject, @Nullable String avatarUrl) {
        launch(context, conversationId, 0, partnerName, subject, avatarUrl);
    }

    public static void launch(Context context, int conversationId, int applicationId,
                              String partnerName, @Nullable String subject,
                              @Nullable String avatarUrl) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_CONVO_ID, conversationId);
        intent.putExtra(EXTRA_APP_ID, applicationId);
        intent.putExtra(EXTRA_PARTNER_NAME, partnerName);
        intent.putExtra(EXTRA_SUBJECT, subject);
        intent.putExtra(EXTRA_AVATAR_URL, avatarUrl);
        context.startActivity(intent);
    }

    public static void launchFromApplication(Context context, int applicationId,
                                             String partnerName, @Nullable String subject) {
        launch(context, 0, applicationId, partnerName, subject, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repository = new StudentRepository(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        Intent intent = getIntent();
        conversationId = intent != null ? intent.getIntExtra(EXTRA_CONVO_ID, 0) : 0;
        applicationId = intent != null ? intent.getIntExtra(EXTRA_APP_ID, 0) : 0;
        String name = intent != null ? intent.getStringExtra(EXTRA_PARTNER_NAME) : null;
        String subject = intent != null ? intent.getStringExtra(EXTRA_SUBJECT) : null;
        partnerAvatarUrl = intent != null ? intent.getStringExtra(EXTRA_AVATAR_URL) : null;

        if (name != null && !name.isEmpty()) partnerName = name;
        if (subject != null && !subject.isEmpty()) currentSubject = subject;
        updateHeader(partnerName, currentSubject, partnerAvatarUrl);

        findViewById(R.id.btnViewApplication).setOnClickListener(v ->
                ApplicationDetailActivity.launch(this, applicationId, currentSubject, partnerName, "Pending"));

        EditText etMessage = findViewById(R.id.etMessage);
        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String msg = etMessage.getText() == null ? "" : etMessage.getText().toString().trim();
            if (msg.isEmpty()) return;
            etMessage.setText("");
            sendNewMessage(msg);
        });

        if (conversationId > 0) {
            loadConversation(conversationId);
        } else if (applicationId > 0) {
            startAndLoadFromApplication(applicationId);
        } else {
            renderMessages(new JsonArray());
        }
    }

    private void updateHeader(String name, String subject, @Nullable String avatarUrl) {
        ((TextView) findViewById(R.id.tvHeaderTitle)).setText(name);
        ((TextView) findViewById(R.id.tvHeaderInitials)).setText(initials(name));

        String myName = SessionManager.get(this).getName();
        String sub = (myName != null && !myName.isEmpty())
                ? myName + "  •  " + subject
                : subject;
        ((TextView) findViewById(R.id.tvHeaderSubtitle)).setText(sub);

        AvatarLoader.bind(avatarUrl,
                findViewById(R.id.ivHeaderAvatar),
                findViewById(R.id.tvHeaderInitials));
        EditText etMessage = findViewById(R.id.etMessage);
        etMessage.setHint("Type your message to " + name + "...");
    }

    private void startAndLoadFromApplication(int appId) {
        setLoading(true);
        loadCall = repository.startConversation(appId, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject d = Json.data(body);
                conversationId = Json.integer(d, 0, "conversation_id", "id");
                String subject = Json.str(d, null, "subject");
                if (subject != null) {
                    currentSubject = subject;
                    updateHeader(partnerName, currentSubject, partnerAvatarUrl);
                }
                if (conversationId > 0) {
                    loadConversation(conversationId);
                } else {
                    setLoading(false);
                    renderMessages(new JsonArray());
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
                setLoading(false);
                Toast.makeText(ChatActivity.this, error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadConversation(int id) {
        if (loadCall != null) loadCall.cancel();
        setLoading(true);
        loadCall = repository.showConversation(id, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                setLoading(false);
                JsonObject d = Json.data(body);
                JsonObject convo = Json.obj(d, "conversation");
                if (convo != null) {
                    int appId = Json.integer(convo, applicationId, "application_id");
                    if (appId > 0) applicationId = appId;
                    JsonObject recipient = Json.obj(convo, "recipient");
                    String rName = Json.str(recipient, partnerName, "display_name", "name");
                    String rAvatar = AvatarLoader.extractUrl(recipient, null);
                    String subject = Json.str(convo, currentSubject, "post_title", "subject");
                    partnerName = rName;
                    currentSubject = subject;
                    partnerAvatarUrl = rAvatar;
                    updateHeader(rName, subject, rAvatar);
                }

                JsonArray messages = Json.arr(d, "messages");
                renderMessages(messages);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                setLoading(false);
                Toast.makeText(ChatActivity.this, error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderMessages(JsonArray messages) {
        LinearLayout container = findViewById(R.id.containerMessages);
        container.removeAllViews();

        if (messages.size() == 0) {
            container.setGravity(Gravity.CENTER);
            TextView title = new TextView(this);
            title.setText("No messages yet in this conversation.");
            title.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            title.setTextSize(13f);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(dp(16), dp(48), dp(16), dp(4));
            container.addView(title);

            TextView sub = new TextView(this);
            sub.setText("Send a message below to reach out to the recruiter.");
            sub.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            sub.setTextSize(12f);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(dp(16), 0, dp(16), dp(48));
            container.addView(sub);
            return;
        }

        container.setGravity(Gravity.TOP);
        for (JsonElement el : messages) {
            if (!el.isJsonObject()) continue;
            JsonObject m = el.getAsJsonObject();
            String body = Json.str(m, "", "body");
            String time = Json.str(m, "", "created_at_time");
            if (time.isEmpty()) {
                time = StudentDashboardActivity.relativeTime(Json.str(m, null, "created_at"));
            }
            boolean isMe = m.has("is_me") && !m.get("is_me").isJsonNull() && m.get("is_me").getAsBoolean();
            if (isMe) {
                appendOutgoingBubble(body, time);
            } else {
                appendIncomingBubble(body, time);
            }
        }
        ScrollView scroll = findViewById(R.id.scrollChat);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void sendNewMessage(String text) {
        LinearLayout container = findViewById(R.id.containerMessages);
        if (container.getChildCount() == 2
                && container.getChildAt(0) instanceof TextView) {
            // Clear the empty-state text views before adding first bubble
            container.removeAllViews();
            container.setGravity(Gravity.TOP);
        }

        String now = new SimpleDateFormat("hh:mm a", Locale.US).format(new Date());
        appendOutgoingBubble(text, now);

        if (conversationId <= 0) return;
        sendCall = repository.sendMessage(conversationId, text, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
            }

            @Override
            public void onError(@NonNull ApiError error) {
                Toast.makeText(ChatActivity.this, error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void appendOutgoingBubble(String messageText, String timeText) {
        LinearLayout container = findViewById(R.id.containerMessages);
        ScrollView scroll = findViewById(R.id.scrollChat);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        lp.leftMargin = dp(48);
        card.setLayoutParams(lp);
        card.setRadius(dp(18));
        card.setCardElevation(0f);
        card.setStrokeWidth(0);
        int green = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
        card.setCardBackgroundColor(green);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        inner.setPadding(pad, pad, pad, pad);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(messageText);
        tvMsg.setTextColor(0xFFFFFFFF);
        tvMsg.setTextSize(13f);
        inner.addView(tvMsg);

        TextView tvTime = new TextView(this);
        tvTime.setText(timeText + " ✓✓");
        tvTime.setTextColor(0xFFD1FAE5);
        tvTime.setTextSize(10f);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeLp.gravity = Gravity.END;
        timeLp.topMargin = dp(4);
        tvTime.setLayoutParams(timeLp);
        inner.addView(tvTime);

        card.addView(inner);
        container.addView(card);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void appendIncomingBubble(String messageText, String timeText) {
        LinearLayout container = findViewById(R.id.containerMessages);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        lp.rightMargin = dp(48);
        card.setLayoutParams(lp);
        card.setRadius(dp(18));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.stroke_soft));
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_card));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        inner.setPadding(pad, pad, pad, pad);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(messageText);
        tvMsg.setTextColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface));
        tvMsg.setTextSize(13f);
        inner.addView(tvMsg);

        TextView tvTime = new TextView(this);
        tvTime.setText(timeText);
        tvTime.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
        tvTime.setTextSize(10f);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeLp.gravity = Gravity.END;
        timeLp.topMargin = dp(4);
        tvTime.setLayoutParams(timeLp);
        inner.addView(tvTime);

        card.addView(inner);
        container.addView(card);
    }

    private void setLoading(boolean loading) {
        View p = findViewById(R.id.progressChat);
        if (p != null) p.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String initials(String text) {
        String[] words = text.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "C";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    @Override
    protected void onDestroy() {
        if (loadCall != null) loadCall.cancel();
        if (sendCall != null) sendCall.cancel();
        super.onDestroy();
    }
}
