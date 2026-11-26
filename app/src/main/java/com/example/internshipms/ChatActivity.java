package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Linkify;
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
import com.google.android.material.button.MaterialButton;
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
    private int studentProfileId;
    private int companyProfileId;
    private String applicationStatus = "pending";
    private String companyNotes = "";
    private String studentMajor = "";
    private String studentGpa = "";
    private String postLocation = "Phnom Penh Office";
    private String partnerName = "Company";
    private String currentSubject = "Internship Position";
    private String partnerAvatarUrl;
    private JsonObject activeInterview;
    private boolean isCompanyRole = false;

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

        SessionManager session = SessionManager.get(this);
        isCompanyRole = "company".equalsIgnoreCase(session.getRole());

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
        renderQuickReplyChips();

        View cardHeaderAction = findViewById(R.id.cardHeaderAction);
        TextView btnViewApp = findViewById(R.id.btnViewApplication);
        View.OnClickListener headerActionClick = v -> {
            if (isCompanyRole) {
                openCompanyStageSheet();
            } else if (applicationId > 0) {
                ApplicationDetailActivity.launch(this, applicationId, currentSubject, partnerName,
                        StudentDashboardActivity.statusLabel(applicationStatus));
            }
        };
        if (cardHeaderAction != null) cardHeaderAction.setOnClickListener(headerActionClick);
        if (btnViewApp != null) btnViewApp.setOnClickListener(headerActionClick);

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

        StringBuilder sub = new StringBuilder();
        if (isCompanyRole) {
            if (studentMajor != null && !studentMajor.isEmpty()) {
                sub.append(studentMajor);
            }
            if (studentGpa != null && !studentGpa.isEmpty()) {
                if (sub.length() > 0) sub.append(" • ");
                try {
                    sub.append(String.format(Locale.US, "GPA %.2f", Double.parseDouble(studentGpa)));
                } catch (Exception e) {
                    sub.append("GPA ").append(studentGpa);
                }
            }
            if (subject != null && !subject.isEmpty()) {
                if (sub.length() > 0) sub.append(" • ");
                sub.append(subject);
            }
        } else {
            if (subject != null && !subject.isEmpty()) {
                sub.append(subject);
            }
        }
        ((TextView) findViewById(R.id.tvHeaderSubtitle)).setText(
                sub.length() > 0 ? sub.toString() : subject);

        AvatarLoader.bind(avatarUrl,
                findViewById(R.id.ivHeaderAvatar),
                findViewById(R.id.tvHeaderInitials));
        EditText etMessage = findViewById(R.id.etMessage);
        etMessage.setHint("Type your message to " + name + "...");

        // Header Avatar & Name click opens full Student / Company Profile
        View cardHeaderAvatar = findViewById(R.id.cardHeaderAvatar);
        View boxHeaderInfo = findViewById(R.id.boxHeaderInfo);

        if (isCompanyRole && studentProfileId > 0) {
            View.OnClickListener openProfile = v ->
                    CompanyStudentProfileActivity.launch(this, studentProfileId, partnerName);
            if (cardHeaderAvatar != null) cardHeaderAvatar.setOnClickListener(openProfile);
            if (boxHeaderInfo != null) boxHeaderInfo.setOnClickListener(openProfile);
        } else if (!isCompanyRole && companyProfileId > 0) {
            View.OnClickListener openComp = v ->
                    CompanyProfileBottomSheet.show(getSupportFragmentManager(), companyProfileId, partnerName, postLocation);
            if (cardHeaderAvatar != null) cardHeaderAvatar.setOnClickListener(openComp);
            if (boxHeaderInfo != null) boxHeaderInfo.setOnClickListener(openComp);
        }

        // Update Stage / View Application pill label
        MaterialCardView cardAction = findViewById(R.id.cardHeaderAction);
        TextView tvAction = findViewById(R.id.btnViewApplication);
        if (cardAction != null && tvAction != null) {
            if (applicationId <= 0) {
                cardAction.setVisibility(View.GONE);
            } else {
                cardAction.setVisibility(View.VISIBLE);
                if (isCompanyRole) {
                    styleCompanyStagePill(cardAction, tvAction, applicationStatus);
                } else {
                    tvAction.setText("Application");
                }
            }
        }
    }

    private void styleCompanyStagePill(MaterialCardView card, TextView tv, String status) {
        String s = status == null ? "pending" : status.toLowerCase(Locale.ROOT);
        int bgColor;
        int strokeAndTextColor;
        String label;

        switch (s) {
            case "shortlisted":
                label = "Shortlisted ▾";
                bgColor = ContextCompat.getColor(this, R.color.tint_indigo_bg);
                strokeAndTextColor = ContextCompat.getColor(this, R.color.tint_indigo);
                break;
            case "interview":
            case "interviewed":
            case "interview_scheduled":
                label = "Interviewing ▾";
                bgColor = ContextCompat.getColor(this, R.color.tint_indigo_bg);
                strokeAndTextColor = ContextCompat.getColor(this, R.color.tint_indigo);
                break;
            case "accepted":
                label = "Accepted ✓ ▾";
                bgColor = ContextCompat.getColor(this, R.color.tint_green_bg);
                strokeAndTextColor = ContextCompat.getColor(this, R.color.tint_green);
                break;
            case "rejected":
                label = "Rejected ▾";
                bgColor = ContextCompat.getColor(this, R.color.tint_red_bg);
                strokeAndTextColor = ContextCompat.getColor(this, R.color.tint_red);
                break;
            case "under_review":
            case "reviewed":
                label = "Under Review ▾";
                bgColor = ContextCompat.getColor(this, R.color.tint_amber_bg);
                strokeAndTextColor = ContextCompat.getColor(this, R.color.tint_amber);
                break;
            case "pending":
            default:
                label = "Pending ▾";
                bgColor = ContextCompat.getColor(this, R.color.tint_amber_bg);
                strokeAndTextColor = ContextCompat.getColor(this, R.color.tint_amber);
                break;
        }
        tv.setText(label);
        tv.setTextColor(strokeAndTextColor);
        card.setCardBackgroundColor(bgColor);
        card.setStrokeColor(ColorStateList.valueOf(strokeAndTextColor));
    }

    private void openCompanyStageSheet() {
        if (applicationId <= 0) return;
        JsonObject appPayload = new JsonObject();
        appPayload.addProperty("id", applicationId);
        appPayload.addProperty("status", applicationStatus);
        appPayload.addProperty("company_notes", companyNotes);
        appPayload.addProperty("student_profile_id", studentProfileId);

        JsonObject studentObj = new JsonObject();
        studentObj.addProperty("id", studentProfileId);
        studentObj.addProperty("name", partnerName);
        if (studentMajor != null && !studentMajor.isEmpty()) studentObj.addProperty("major", studentMajor);
        if (studentGpa != null && !studentGpa.isEmpty()) studentObj.addProperty("gpa", studentGpa);
        if (partnerAvatarUrl != null) studentObj.addProperty("avatar_url", partnerAvatarUrl);
        appPayload.add("student_profile", studentObj);

        JsonObject postObj = new JsonObject();
        postObj.addProperty("title", currentSubject);
        postObj.addProperty("location", postLocation);
        appPayload.add("internship_post", postObj);

        if (activeInterview != null) {
            appPayload.add("interview", activeInterview);
        }

        ReviewApplicantBottomSheet.show(getSupportFragmentManager(), appPayload, () -> {
            if (conversationId > 0) {
                loadConversation(conversationId);
            }
        });
    }

    private void bindPinnedInterviewBanner() {
        View box = findViewById(R.id.boxPinnedInterview);
        View divider = findViewById(R.id.dividerPinnedInterview);
        if (box == null || divider == null) return;

        if (activeInterview == null) {
            box.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
            return;
        }

        box.setVisibility(View.VISIBLE);
        divider.setVisibility(View.VISIBLE);

        MaterialCardView iconBox = findViewById(R.id.iconPinnedInterviewBox);
        TextView tvDate = findViewById(R.id.tvPinnedInterviewDate);
        TextView tvBadge = findViewById(R.id.tvPinnedInterviewBadge);
        TextView tvSub = findViewById(R.id.tvPinnedInterviewSub);
        MaterialButton btnJoin = findViewById(R.id.btnJoinInterviewCall);

        String schedHuman = Json.str(activeInterview, null, "scheduled_human");
        if (schedHuman == null || schedHuman.isEmpty()) {
            schedHuman = StudentDashboardActivity.formatDateTime(
                    Json.str(activeInterview, "", "scheduled_at"));
        }
        String meetingType = Json.str(activeInterview, "in_person", "meeting_type");
        String locOrLink = Json.str(activeInterview, "", "location_or_link");
        String intStatus = Json.str(activeInterview, "scheduled", "status");
        boolean confirmed = activeInterview.has("confirmed_by_student")
                && !activeInterview.get("confirmed_by_student").isJsonNull()
                && activeInterview.get("confirmed_by_student").getAsBoolean();
        String reschedReason = Json.str(activeInterview, "", "reschedule_reason");

        tvDate.setText("Interview: " + schedHuman);

        if ("reschedule_requested".equalsIgnoreCase(intStatus)) {
            box.setBackgroundColor(ContextCompat.getColor(this, R.color.tint_amber_bg));
            iconBox.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_amber));
            tvBadge.setText("Reschedule Req.");
            tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            if (!reschedReason.isEmpty()) {
                tvSub.setVisibility(View.VISIBLE);
                tvSub.setText("Reason: “" + reschedReason + "”");
                tvSub.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            } else {
                tvSub.setVisibility(View.GONE);
            }
        } else {
            box.setBackgroundColor(Color.parseColor("#FFF5F7FF"));
            iconBox.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_indigo));
            if (confirmed || "confirmed".equalsIgnoreCase(intStatus)) {
                tvBadge.setText("Confirmed ✓");
                tvBadge.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.tint_green_bg)));
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_green));
            } else {
                tvBadge.setText("Awaiting Reply");
                tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            }
            String modeLabel = "online".equalsIgnoreCase(meetingType) ? "Online Video" : "In-Person";
            if (!locOrLink.isEmpty()) {
                tvSub.setVisibility(View.VISIBLE);
                tvSub.setText(modeLabel + " • " + locOrLink);
                tvSub.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            } else {
                tvSub.setVisibility(View.GONE);
            }
        }

        if ("online".equalsIgnoreCase(meetingType)
                && (locOrLink.startsWith("http://") || locOrLink.startsWith("https://"))) {
            btnJoin.setVisibility(View.VISIBLE);
            btnJoin.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(locOrLink)));
                } catch (Exception ignored) {
                }
            });
        } else {
            btnJoin.setVisibility(View.GONE);
        }
    }

    private void renderQuickReplyChips() {
        LinearLayout container = findViewById(R.id.containerQuickReplies);
        if (container == null) return;
        container.removeAllViews();

        String firstName = partnerName != null && !partnerName.trim().isEmpty()
                ? partnerName.trim().split("\\s+")[0]
                : "there";

        String[][] templates;
        if (isCompanyRole) {
            String schedLinkText = null;
            if (activeInterview != null) {
                String link = Json.str(activeInterview, "", "location_or_link");
                String human = Json.str(activeInterview, "", "scheduled_human", "scheduled_at");
                if (!link.isEmpty()) {
                    schedLinkText = "Hi " + firstName + ", here is the meeting link/location for our scheduled interview"
                            + (!human.isEmpty() ? " on " + human : "") + ": " + link;
                }
            }
            if (schedLinkText != null) {
                templates = new String[][]{
                        {"📅 Schedule Interview", "Hi " + firstName + ", we would love to invite you for an interview. Could you share your available times this week?"},
                        {"🔗 Send Interview Details", schedLinkText},
                        {"📄 Request Portfolio", "Hi " + firstName + ", thank you for applying! Could you share your portfolio or GitHub link for our team to review?"},
                        {"🎉 Send Offer", "Hi " + firstName + ", congratulations! Following our review, we are pleased to offer you the position. Please let us know your confirmation."},
                        {"✅ Status Update", "Hi " + firstName + ", we are currently reviewing your application and will update you shortly."}
                };
            } else {
                templates = new String[][]{
                        {"📅 Schedule Interview", "Hi " + firstName + ", we would love to invite you for an interview. Could you share your available times this week?"},
                        {"📄 Request Portfolio", "Hi " + firstName + ", thank you for applying! Could you share your portfolio or GitHub link for our team to review?"},
                        {"🎉 Send Offer", "Hi " + firstName + ", congratulations! Following our review, we are pleased to offer you the position. Please let us know your confirmation."},
                        {"✅ Status Update", "Hi " + firstName + ", we are currently reviewing your application and will update you shortly."}
                };
            }
        } else {
            templates = new String[][]{
                    {"👋 Follow Up", "Hello " + partnerName + ", I wanted to follow up on my application for " + currentSubject + ". Thank you for your time!"},
                    {"📅 Confirm Interview", "Hello, thank you for the interview invitation! I confirm my attendance and look forward to speaking with you."},
                    {"📄 Share Portfolio", "Hello, I would like to share my latest portfolio and project links for your review: "},
                    {"🙏 Thank You", "Thank you very much for the update and opportunity!"}
            };
        }

        EditText etMessage = findViewById(R.id.etMessage);
        for (int i = 0; i < templates.length; i++) {
            String label = templates[i][0];
            String fillText = templates[i][1];

            MaterialCardView pill = new MaterialCardView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(28));
            if (i > 0) lp.leftMargin = dp(6);
            pill.setLayoutParams(lp);
            pill.setRadius(dp(14));
            pill.setCardElevation(0f);
            pill.setClickable(true);
            pill.setFocusable(true);

            if (i == 0) {
                pill.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_green_bg));
                pill.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_green)));
                pill.setStrokeWidth(dp(1));
            } else {
                pill.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_page));
                pill.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_soft)));
                pill.setStrokeWidth(dp(1));
            }

            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(11f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(10), 0, dp(10), 0);
            tv.setTextColor(i == 0
                    ? ContextCompat.getColor(this, R.color.tint_green)
                    : MaterialColors.getColor(pill, com.google.android.material.R.attr.colorOnSurface));
            pill.addView(tv);

            pill.setOnClickListener(v -> {
                if (etMessage != null) {
                    etMessage.setText(fillText);
                    etMessage.setSelection(fillText.length());
                    etMessage.requestFocus();
                }
            });
            container.addView(pill);
        }
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
                    studentProfileId = Json.integer(convo, studentProfileId, "student_profile_id");
                    companyProfileId = Json.integer(convo, companyProfileId, "company_profile_id");
                    applicationStatus = Json.str(convo, applicationStatus, "application_status");
                    companyNotes = Json.str(convo, "", "company_notes");
                    studentMajor = Json.str(convo, "", "student_major");
                    studentGpa = Json.str(convo, "", "student_gpa");
                    postLocation = Json.str(convo, postLocation, "post_location");
                    activeInterview = Json.obj(convo, "interview");

                    JsonObject recipient = Json.obj(convo, "recipient");
                    String rName = Json.str(recipient, partnerName, "display_name", "name");
                    String rAvatar = AvatarLoader.extractUrl(recipient, null);
                    String subject = Json.str(convo, currentSubject, "post_title", "subject");
                    partnerName = rName;
                    currentSubject = subject;
                    partnerAvatarUrl = rAvatar;

                    updateHeader(rName, subject, rAvatar);
                    bindPinnedInterviewBanner();
                    renderQuickReplyChips();
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
            title.setText(isCompanyRole
                    ? "No messages yet in this candidate conversation."
                    : "No messages yet in this conversation.");
            title.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            title.setTextSize(13f);
            title.setTypeface(title.getTypeface(), Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(dp(16), dp(48), dp(16), dp(4));
            container.addView(title);

            TextView sub = new TextView(this);
            sub.setText(isCompanyRole
                    ? "Tap one of the Quick Replies below to start the conversation."
                    : "Tap a Quick Reply or type a message below to reach out to the recruiter.");
            sub.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            sub.setTextSize(12f);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(dp(16), 0, dp(16), dp(48));
            container.addView(sub);
            return;
        }

        container.setGravity(Gravity.TOP);
        String lastDateLabel = null;
        for (JsonElement el : messages) {
            if (!el.isJsonObject()) continue;
            JsonObject m = el.getAsJsonObject();
            String body = Json.str(m, "", "body");
            String time = Json.str(m, "", "created_at_time");
            if (time.isEmpty()) {
                time = StudentDashboardActivity.relativeTime(Json.str(m, null, "created_at"));
            }
            String dateLabel = Json.str(m, null, "date_label");
            if (dateLabel != null && !dateLabel.isEmpty() && !dateLabel.equals(lastDateLabel)) {
                appendDateSeparator(dateLabel);
                lastDateLabel = dateLabel;
            }

            boolean isMe = m.has("is_me") && !m.get("is_me").isJsonNull() && m.get("is_me").getAsBoolean();
            boolean isRead = m.has("is_read") && !m.get("is_read").isJsonNull() && m.get("is_read").getAsBoolean();
            if (isMe) {
                appendOutgoingBubble(body, time, isRead);
            } else {
                appendIncomingBubble(body, time);
            }
        }
        ScrollView scroll = findViewById(R.id.scrollChat);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void appendDateSeparator(String label) {
        LinearLayout container = findViewById(R.id.containerMessages);
        TextView pill = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(2);
        pill.setLayoutParams(lp);
        pill.setText(label);
        pill.setTextSize(10f);
        pill.setTypeface(null, Typeface.BOLD);
        pill.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
        pill.setBackgroundResource(R.drawable.bg_chip);
        pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_soft)));
        pill.setPadding(dp(10), dp(3), dp(10), dp(3));
        container.addView(pill);
    }

    private void sendNewMessage(String text) {
        LinearLayout container = findViewById(R.id.containerMessages);
        if (container.getChildCount() == 2
                && container.getChildAt(0) instanceof TextView
                && container.getChildAt(1) instanceof TextView) {
            container.removeAllViews();
            container.setGravity(Gravity.TOP);
            appendDateSeparator("Today");
        }

        String now = new SimpleDateFormat("hh:mm a", Locale.US).format(new Date());
        appendOutgoingBubble(text, now, false);

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

    private void appendOutgoingBubble(String messageText, String timeText, boolean isRead) {
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
        tvMsg.setLinkTextColor(0xFFD1FAE5);
        tvMsg.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        tvMsg.setTextSize(13f);
        inner.addView(tvMsg);

        TextView tvTime = new TextView(this);
        tvTime.setText(timeText + (isRead ? " • Read ✓✓" : " • Sent ✓"));
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
        tvMsg.setLinkTextColor(ContextCompat.getColor(this, R.color.tint_green));
        tvMsg.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
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
