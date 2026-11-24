package com.example.internshipms;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CompanyStudentProfileActivity extends AppCompatActivity {

    public static final String EXTRA_STUDENT_ID = "extra_student_id";
    public static final String EXTRA_STUDENT_NAME = "extra_student_name";
    private static final int SKILLS_PREVIEW_LIMIT = 8;

    public static void launch(Context context, int studentId, @Nullable String candidateName) {
        Intent intent = new Intent(context, CompanyStudentProfileActivity.class);
        intent.putExtra(EXTRA_STUDENT_ID, studentId);
        if (candidateName != null) {
            intent.putExtra(EXTRA_STUDENT_NAME, candidateName);
        }
        context.startActivity(intent);
    }

    private CompanyRepository repo;
    private int studentId;
    private String candidateName;

    private PullRefreshScrollView scrollRefresh;

    private TextView tvStudentInitials;
    private ImageView ivStudentAvatar;
    private TextView tvStudentName;
    private TextView tvStudentUniversity;
    private TextView tvStudentCohortBadge;

    private TextView tvStatGpa;
    private TextView tvStatSkillsCount;
    private TextView tvStatAppsCount;
    private TextView tvStatResumeStatus;

    private MaterialButton btnViewResume;
    private MaterialButton btnMessageCandidate;

    private TextView tvStudentIdNumber;
    private TextView tvStudentEmail;
    private TextView tvStudentPhone;
    private TextView tvStudentBio;
    private TextView tvSkillsHeader;
    private TextView btnToggleSkills;
    private TextView tvApplicationsHeader;

    private ChipGroup chipGroupSkills;
    private LinearLayout containerStudentApplications;

    private final List<String> allSkillsList = new ArrayList<>();
    private boolean skillsExpanded = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_company_student_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repo = new CompanyRepository(this);

        studentId = getIntent().getIntExtra(EXTRA_STUDENT_ID, 0);
        candidateName = getIntent().getStringExtra(EXTRA_STUDENT_NAME);

        scrollRefresh = findViewById(R.id.scrollRefresh);

        tvStudentInitials = findViewById(R.id.tvStudentInitials);
        ivStudentAvatar = findViewById(R.id.ivStudentAvatar);
        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentUniversity = findViewById(R.id.tvStudentUniversity);
        tvStudentCohortBadge = findViewById(R.id.tvStudentCohortBadge);

        tvStatGpa = findViewById(R.id.tvStatGpa);
        tvStatSkillsCount = findViewById(R.id.tvStatSkillsCount);
        tvStatAppsCount = findViewById(R.id.tvStatAppsCount);
        tvStatResumeStatus = findViewById(R.id.tvStatResumeStatus);

        btnViewResume = findViewById(R.id.btnViewResume);
        btnMessageCandidate = findViewById(R.id.btnMessageCandidate);

        tvStudentIdNumber = findViewById(R.id.tvStudentIdNumber);
        tvStudentEmail = findViewById(R.id.tvStudentEmail);
        tvStudentPhone = findViewById(R.id.tvStudentPhone);
        tvStudentBio = findViewById(R.id.tvStudentBio);
        tvSkillsHeader = findViewById(R.id.tvSkillsHeader);
        btnToggleSkills = findViewById(R.id.btnToggleSkills);
        tvApplicationsHeader = findViewById(R.id.tvApplicationsHeader);

        chipGroupSkills = findViewById(R.id.chipGroupSkills);
        containerStudentApplications = findViewById(R.id.containerStudentApplications);

        if (candidateName != null && !candidateName.isEmpty()) {
            tvStudentName.setText(candidateName);
            tvStudentInitials.setText(initials(candidateName));
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        if (btnMessageCandidate != null) {
            btnMessageCandidate.setOnClickListener(v -> {
                String name = tvStudentName.getText() != null ? tvStudentName.getText().toString() : "Candidate";
                ChatActivity.launch(this, name, true);
            });
        }

        if (btnToggleSkills != null) {
            btnToggleSkills.setOnClickListener(v -> {
                skillsExpanded = !skillsExpanded;
                renderSkillsChips();
            });
        }

        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadStudentProfile);
        }

        loadStudentProfile();
    }

    private void loadStudentProfile() {
        if (studentId <= 0) {
            Toast.makeText(this, "Invalid candidate ID", Toast.LENGTH_SHORT).show();
            return;
        }

        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);
        repo.showStudentProfile(studentId, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonObject data = Json.data(body);
                bindStudentData(data);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                Toast.makeText(CompanyStudentProfileActivity.this, error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindStudentData(@NonNull JsonObject data) {
        JsonObject student = Json.obj(data, "student_profile", "student");
        if (student == null) student = data;
        JsonObject user = Json.obj(student, "user");
        if (user == null) user = Json.obj(data, "user");

        String name = Json.str(user, null, "name");
        if (name == null) name = Json.str(student, null, "full_name", "name");
        if (name == null && candidateName != null) name = candidateName;
        if (name == null) name = "Candidate";

        String major = Json.str(student, "Major not specified", "major", "field_of_study");
        String department = Json.str(student, null, "department", "faculty");
        String studentIdNum = Json.str(student, "Not specified", "student_id_number", "student_code");
        String gpaRaw = Json.str(student, null, "gpa", "cgpa");
        String cohortYear = Json.str(student, "N/A", "cohort_year", "cohort", "graduation_year");
        String email = Json.str(user, "N/A", "email");
        String phone = Json.str(student, "Not provided", "phone", "phone_number", "contact_number");
        String bio = Json.str(student, "No professional summary provided by the candidate.", "bio", "about");
        String avatarUrl = AvatarLoader.extractUrl(user, student);

        tvStudentName.setText(name);
        tvStudentInitials.setText(initials(name));
        AvatarLoader.bind(avatarUrl, ivStudentAvatar, tvStudentInitials);

        String subtitle = major + (department != null && !department.isEmpty() ? " · " + department : "");
        tvStudentUniversity.setText(subtitle);

        if (tvStudentIdNumber != null) tvStudentIdNumber.setText(studentIdNum);
        tvStudentEmail.setText(email);
        tvStudentPhone.setText(phone);
        tvStudentBio.setText(bio);

        // GPA displayed cleanly in the Quick Stats Strip with dynamic color coding
        String gpaDisplayShort = "N/A";
        double parsedGpa = -1.0;
        if (gpaRaw != null && !gpaRaw.isEmpty()) {
            try {
                parsedGpa = Double.parseDouble(gpaRaw);
                gpaDisplayShort = String.format(Locale.US, "★ %.2f", parsedGpa);
            } catch (Exception e) {
                gpaDisplayShort = "★ " + gpaRaw;
            }
        }
        if (tvStatGpa != null) {
            tvStatGpa.setText(gpaDisplayShort);
            if (parsedGpa >= 3.50) {
                tvStatGpa.setTextColor(MaterialColors.getColor(tvStatGpa, androidx.appcompat.R.attr.colorPrimary));
            } else if (parsedGpa >= 2.50) {
                tvStatGpa.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
            } else if (parsedGpa >= 0) {
                tvStatGpa.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            } else {
                tvStatGpa.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            }
        }
        if (tvStudentCohortBadge != null) {
            tvStudentCohortBadge.setText("Cohort " + cohortYear);
        }

        // Copy & Contact Intents
        View boxStudentIdCopy = findViewById(R.id.boxStudentIdCopy);
        if (boxStudentIdCopy != null) {
            boxStudentIdCopy.setOnClickListener(v -> {
                if (!"Not specified".equalsIgnoreCase(studentIdNum)) {
                    copyToClipboard("Student ID", studentIdNum);
                }
            });
        }

        View btnCopyEmail = findViewById(R.id.btnCopyEmail);
        if (btnCopyEmail != null) {
            btnCopyEmail.setOnClickListener(v -> {
                if (!"N/A".equalsIgnoreCase(email)) copyToClipboard("Email", email);
            });
        }

        View btnCopyPhone = findViewById(R.id.btnCopyPhone);
        if (btnCopyPhone != null) {
            btnCopyPhone.setOnClickListener(v -> {
                if (!"Not provided".equalsIgnoreCase(phone)) copyToClipboard("Phone", phone);
            });
        }

        View rowEmail = findViewById(R.id.rowEmailContact);
        if (rowEmail != null) {
            rowEmail.setOnClickListener(v -> {
                if (!"N/A".equalsIgnoreCase(email) && email.contains("@")) {
                    Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
                    try {
                        startActivity(emailIntent);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        View rowPhone = findViewById(R.id.rowPhoneContact);
        if (rowPhone != null) {
            rowPhone.setOnClickListener(v -> {
                if (!"Not provided".equalsIgnoreCase(phone) && !phone.isEmpty()) {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                    try {
                        startActivity(dialIntent);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        // Parse & Render Collapsible Skills Chips
        allSkillsList.clear();
        JsonArray skillsArr = Json.list(student, "skills", "skills_list");
        if (skillsArr.isEmpty() && student.has("skills") && student.get("skills").isJsonPrimitive()) {
            String raw = student.get("skills").getAsString();
            for (String s : raw.split("[,;|]")) {
                if (!s.trim().isEmpty()) allSkillsList.add(s.trim());
            }
        } else if (!skillsArr.isEmpty()) {
            for (JsonElement el : skillsArr) {
                if (el.isJsonPrimitive()) {
                    String s = el.getAsString().trim();
                    if (!s.isEmpty()) allSkillsList.add(s);
                } else if (el.isJsonObject()) {
                    String s = Json.str(el.getAsJsonObject(), null, "name", "skill");
                    if (s != null && !s.trim().isEmpty()) allSkillsList.add(s.trim());
                }
            }
        }

        int skillsCount = allSkillsList.size();
        if (tvStatSkillsCount != null) tvStatSkillsCount.setText(String.valueOf(skillsCount));
        if (tvSkillsHeader != null) {
            tvSkillsHeader.setText(skillsCount > 0
                    ? "Technical Skills (" + skillsCount + ")"
                    : "Technical Skills & Competencies");
        }
        renderSkillsChips();

        // Render applications to this company + wire primary application Resume & Chat
        JsonArray apps = Json.list(data, "applications", "applied_jobs");
        if (tvApplicationsHeader != null) {
            tvApplicationsHeader.setText("Applications to Your Company (" + apps.size() + ")");
        }
        if (tvStatAppsCount != null) {
            tvStatAppsCount.setText(String.valueOf(apps.size()));
        }

        final String finalStudentName = name;
        String primaryResumeUrl = extractResumeUrl(null, student);
        int primaryAppId = 0;
        String primaryRoleTitle = "Position";

        if (!apps.isEmpty() && apps.get(0).isJsonObject()) {
            JsonObject primaryApp = apps.get(0).getAsJsonObject();
            primaryAppId = Json.integer(primaryApp, 0, "id");
            JsonObject pPost = Json.obj(primaryApp, "internship_post", "post");
            if (pPost != null) {
                primaryRoleTitle = Json.str(pPost, "Position", "title");
            }
            String appResume = extractResumeUrl(primaryApp, student);
            if (appResume != null) primaryResumeUrl = appResume;
        }

        boolean hasAnyResume = (primaryResumeUrl != null && !primaryResumeUrl.isEmpty());
        if (tvStatResumeStatus != null) {
            if (hasAnyResume) {
                tvStatResumeStatus.setText("PDF ✓");
                tvStatResumeStatus.setTextColor(MaterialColors.getColor(tvStatResumeStatus, androidx.appcompat.R.attr.colorPrimary));
            } else {
                tvStatResumeStatus.setText("None");
                tvStatResumeStatus.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            }
        }

        final int finalPrimaryAppId = primaryAppId;
        final String finalPrimaryRoleTitle = primaryRoleTitle;
        final String finalPrimaryResumeUrl = primaryResumeUrl;

        View.OnClickListener chatWithPrimary = v -> {
            if (finalPrimaryAppId > 0) {
                ChatActivity.launchFromApplication(this, finalPrimaryAppId, finalStudentName, finalPrimaryRoleTitle);
            } else {
                ChatActivity.launch(this, finalStudentName, true);
            }
        };
        if (btnMessageCandidate != null) {
            btnMessageCandidate.setOnClickListener(chatWithPrimary);
        }

        if (btnViewResume != null) {
            if (hasAnyResume || finalPrimaryAppId > 0) {
                btnViewResume.setVisibility(View.VISIBLE);
                btnViewResume.setOnClickListener(v ->
                        showResumeOptionsDialog(finalPrimaryAppId, finalPrimaryResumeUrl, finalStudentName));
            } else {
                btnViewResume.setVisibility(View.GONE);
            }
        }

        if (containerStudentApplications != null) {
            containerStudentApplications.removeAllViews();
            if (apps.isEmpty()) {
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText("No previous applications on record.");
                tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
                tvEmpty.setTextSize(12.5f);
                containerStudentApplications.addView(tvEmpty);
            } else {
                for (JsonElement el : apps) {
                    if (!el.isJsonObject()) continue;
                    JsonObject app = el.getAsJsonObject();
                    if (!app.has("student_profile")) {
                        app.add("student_profile", student);
                    }
                    addApplicationRow(app, student, finalStudentName);
                }
            }
        }
    }

    private void renderSkillsChips() {
        if (chipGroupSkills == null) return;
        chipGroupSkills.removeAllViews();

        if (allSkillsList.isEmpty()) {
            if (btnToggleSkills != null) btnToggleSkills.setVisibility(View.GONE);
            TextView tvNoSkill = new TextView(this);
            tvNoSkill.setText("No technical skills listed.");
            tvNoSkill.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            tvNoSkill.setTextSize(12f);
            chipGroupSkills.addView(tvNoSkill);
            return;
        }

        int total = allSkillsList.size();
        boolean hasMore = total > SKILLS_PREVIEW_LIMIT;
        if (btnToggleSkills != null) {
            btnToggleSkills.setVisibility(hasMore ? View.VISIBLE : View.GONE);
            btnToggleSkills.setText(skillsExpanded ? "Show Less" : "Show All (" + total + ")");
        }

        int countToRender = (hasMore && !skillsExpanded) ? SKILLS_PREVIEW_LIMIT : total;
        for (int i = 0; i < countToRender; i++) {
            addSkillChip(allSkillsList.get(i), false);
        }

        if (hasMore && !skillsExpanded) {
            int hiddenCount = total - SKILLS_PREVIEW_LIMIT;
            addSkillChip("+" + hiddenCount + " More", true);
        } else if (hasMore) {
            addSkillChip("Show Less ↑", true);
        }
    }

    private void addSkillChip(String label, boolean isToggleChip) {
        float density = getResources().getDisplayMetrics().density;
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(28f * density);
        chip.setChipCornerRadius(10f * density);
        chip.setChipStartPadding(10f * density);
        chip.setChipEndPadding(10f * density);
        chip.setTextStartPadding(2f * density);
        chip.setTextEndPadding(2f * density);
        if (isToggleChip) {
            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_indigo_bg)));
            chip.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
            chip.setChipStrokeWidth(0f);
            chip.setOnClickListener(v -> {
                skillsExpanded = !skillsExpanded;
                renderSkillsChips();
            });
        } else {
            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_green_bg)));
            chip.setTextColor(MaterialColors.getColor(chip, androidx.appcompat.R.attr.colorPrimary));
            chip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_soft)));
            chip.setChipStrokeWidth(1f);
        }
        chip.setTextSize(11.5f);
        chipGroupSkills.addView(chip);
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, label + " copied: " + value, Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private static String extractResumeUrl(@Nullable JsonObject app, @Nullable JsonObject student) {
        String raw = null;
        if (app != null) {
            raw = Json.str(app, null, "resume_url", "resume_path", "cv_path", "resume");
        }
        if ((raw == null || raw.isEmpty()) && student != null) {
            raw = Json.str(student, null, "resume_url", "resume_path", "cv_path", "resume");
        }
        return AvatarLoader.normalizeUrl(raw);
    }

    private void showResumeOptionsDialog(int appId, @Nullable String directUrl, String studentName) {
        String safeFileName = studentName.replaceAll("[^a-zA-Z0-9._-]", "_") + "_Resume.pdf";
        if (appId > 0 && directUrl != null && !directUrl.isEmpty()) {
            String[] options = {
                    "🌐 Open Resume in Browser / PDF Viewer",
                    "⬇ Download Official PDF to Device"
            };
            new MaterialAlertDialogBuilder(this)
                    .setTitle(studentName + "'s Resume")
                    .setItems(options, (d, which) -> {
                        if (which == 0) {
                            openExternalUrl(directUrl);
                        } else {
                            downloadApplicationResumeToDevice(appId, safeFileName, directUrl);
                        }
                    })
                    .show();
        } else if (appId > 0) {
            downloadApplicationResumeToDevice(appId, safeFileName, directUrl);
        } else if (directUrl != null && !directUrl.isEmpty()) {
            openExternalUrl(directUrl);
        } else {
            Toast.makeText(this, "No resume uploaded by candidate.", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadApplicationResumeToDevice(int appId, String fileName, @Nullable String fallbackUrl) {
        Toast.makeText(this, "Downloading " + fileName + "...", Toast.LENGTH_SHORT).show();
        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);

        repo.downloadApplicationResume(appId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                if (!response.isSuccessful() || response.body() == null) {
                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        openExternalUrl(fallbackUrl);
                    } else {
                        Toast.makeText(CompanyStudentProfileActivity.this,
                                "No resume PDF attached to this application.", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                Uri savedUri = savePdfToDownloads(response.body(), fileName);
                if (savedUri != null) {
                    Toast.makeText(CompanyStudentProfileActivity.this,
                            "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
                    try {
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                        viewIntent.setDataAndType(savedUri, "application/pdf");
                        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(viewIntent);
                    } catch (Exception ignored) {
                    }
                } else {
                    Toast.makeText(CompanyStudentProfileActivity.this,
                            "Failed to save PDF file.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                    openExternalUrl(fallbackUrl);
                } else {
                    Toast.makeText(CompanyStudentProfileActivity.this,
                            "Network error while downloading resume.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Nullable
    private Uri savePdfToDownloads(ResponseBody body, String fileName) {
        try (InputStream in = body.byteStream()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return null;
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) return null;
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    return uri;
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outFile = new File(dir, fileName);
                try (OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    return Uri.fromFile(outFile);
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void openExternalUrl(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void addApplicationRow(JsonObject app, JsonObject student, String studentName) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_student_application_row, containerStudentApplications, false);
        TextView tvOppBadge = row.findViewById(R.id.tvAppOppBadge);
        TextView tvRole = row.findViewById(R.id.tvAppRole);
        TextView tvDate = row.findViewById(R.id.tvAppDate);
        TextView tvInterview = row.findViewById(R.id.tvAppInterview);
        TextView tvRescheduleAlert = row.findViewById(R.id.tvAppRescheduleAlert);
        View boxCoverLetter = row.findViewById(R.id.boxAppCoverLetter);
        TextView tvCoverLetter = row.findViewById(R.id.tvAppCoverLetter);
        View boxCompanyNotes = row.findViewById(R.id.boxAppCompanyNotes);
        TextView tvCompanyNotes = row.findViewById(R.id.tvAppCompanyNotes);
        TextView tvStatus = row.findViewById(R.id.tvAppStatus);
        TextView btnAppJoinMeeting = row.findViewById(R.id.btnAppJoinMeeting);
        TextView btnAppSchedule = row.findViewById(R.id.btnAppSchedule);
        TextView btnAppResume = row.findViewById(R.id.btnAppResume);
        TextView btnAppChat = row.findViewById(R.id.btnAppChat);
        TextView btnAppUpdateStatus = row.findViewById(R.id.btnAppUpdateStatus);

        int appId = Json.integer(app, 0, "id");
        JsonObject post = Json.obj(app, "internship_post", "post");
        String role = post != null ? Json.str(post, "Internship Role", "title") : Json.str(app, "Internship Role", "title", "role");
        String defaultLocation = post != null ? Json.str(post, "Phnom Penh Office", "location") : "Phnom Penh Office";
        String oppType = post != null ? Json.str(post, "internship", "opportunity_type") : "internship";
        String date = Json.str(app, "Recently", "applied_at", "created_at");
        String status = Json.str(app, "pending", "status");
        String coverLetter = Json.str(app, null, "cover_letter");
        String companyNotes = Json.str(app, null, "company_notes");

        if (tvOppBadge != null) {
            if ("internship".equalsIgnoreCase(oppType)) {
                tvOppBadge.setText("🎓 Intern");
                tvOppBadge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_indigo_bg)));
                tvOppBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
            } else {
                String lbl = "💼 Job";
                if ("full_time".equalsIgnoreCase(oppType)) lbl = "💼 Full-Time";
                else if ("part_time".equalsIgnoreCase(oppType)) lbl = "💼 Part-Time";
                else if ("contract".equalsIgnoreCase(oppType)) lbl = "💼 Contract";
                tvOppBadge.setText(lbl);
                tvOppBadge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_amber_bg)));
                tvOppBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            }
        }

        if (tvRole != null) tvRole.setText(role);
        if (tvDate != null) {
            String formattedDate = date.length() >= 10 ? date.substring(0, 10) : date;
            tvDate.setText("Applied " + formattedDate);
        }

        // Cover Letter Box (Expandable on tap)
        if (boxCoverLetter != null && tvCoverLetter != null) {
            if (coverLetter != null && !coverLetter.trim().isEmpty()) {
                boxCoverLetter.setVisibility(View.VISIBLE);
                tvCoverLetter.setText(coverLetter.trim());
                boxCoverLetter.setOnClickListener(v -> {
                    boolean expanded = tvCoverLetter.getMaxLines() > 2;
                    tvCoverLetter.setMaxLines(expanded ? 2 : 30);
                });
            } else {
                boxCoverLetter.setVisibility(View.GONE);
            }
        }

        // Company Internal Notes Box
        if (boxCompanyNotes != null && tvCompanyNotes != null) {
            if (companyNotes != null && !companyNotes.trim().isEmpty()) {
                boxCompanyNotes.setVisibility(View.VISIBLE);
                tvCompanyNotes.setText(companyNotes.trim());
            } else {
                boxCompanyNotes.setVisibility(View.GONE);
            }
        }

        // Interview schedule line, Reschedule Alert, and Join Meeting Link
        JsonObject interview = StudentApplicationsActivity.extractInterview(app);
        int interviewId = interview != null ? Json.integer(interview, 0, "id") : 0;
        if (interview != null) {
            String schedRaw = Json.str(interview, null, "scheduled_at", "interview_date");
            String modeRaw = Json.str(interview, "in_person", "meeting_type", "type");
            String locOrLink = Json.str(interview, null, "location_or_link");
            String intStatus = Json.str(interview, "scheduled", "status");
            boolean confirmed = interview.has("confirmed_by_student")
                    && !interview.get("confirmed_by_student").isJsonNull()
                    && interview.get("confirmed_by_student").getAsBoolean();

            if (tvInterview != null && schedRaw != null && !schedRaw.isEmpty()) {
                tvInterview.setVisibility(View.VISIBLE);
                String modeLbl = "online".equalsIgnoreCase(modeRaw) ? "Online Video" : "In-Person";
                String confLbl = confirmed ? " (Confirmed ✓)" : "";
                tvInterview.setText("📅 Interview: " + StudentDashboardActivity.formatDateTime(schedRaw)
                        + " • " + modeLbl + confLbl);
            }

            if (tvRescheduleAlert != null) {
                if ("reschedule_requested".equalsIgnoreCase(intStatus)) {
                    String reason = Json.str(interview, "Candidate requested a different time.", "reschedule_reason");
                    tvRescheduleAlert.setVisibility(View.VISIBLE);
                    tvRescheduleAlert.setText("⚠ Reschedule Requested: " + reason);
                } else {
                    tvRescheduleAlert.setVisibility(View.GONE);
                }
            }

            if (btnAppSchedule != null) {
                btnAppSchedule.setText("🔄 Reschedule");
            }

            if (btnAppJoinMeeting != null) {
                if (locOrLink != null && (locOrLink.startsWith("http://") || locOrLink.startsWith("https://"))) {
                    btnAppJoinMeeting.setVisibility(View.VISIBLE);
                    btnAppJoinMeeting.setOnClickListener(v -> openExternalUrl(locOrLink));
                } else {
                    btnAppJoinMeeting.setVisibility(View.GONE);
                }
            }
        } else {
            if (tvInterview != null) tvInterview.setVisibility(View.GONE);
            if (tvRescheduleAlert != null) tvRescheduleAlert.setVisibility(View.GONE);
            if (btnAppJoinMeeting != null) btnAppJoinMeeting.setVisibility(View.GONE);
            if (btnAppSchedule != null) btnAppSchedule.setText("📅 Schedule");
        }

        if (tvStatus != null) {
            applyStatusBadge(tvStatus, status);
        }

        // Schedule / Reschedule Interview Button
        if (btnAppSchedule != null && appId > 0) {
            final int finalInterviewId = interviewId;
            btnAppSchedule.setOnClickListener(v ->
                    showScheduleOrRescheduleDialog(appId, finalInterviewId, studentName, defaultLocation));
        }

        // CV button
        String resumeUrl = extractResumeUrl(app, student);
        if (btnAppResume != null) {
            if ((resumeUrl != null && !resumeUrl.isEmpty()) || appId > 0) {
                btnAppResume.setVisibility(View.VISIBLE);
                btnAppResume.setOnClickListener(v ->
                        showResumeOptionsDialog(appId, resumeUrl, studentName));
            } else {
                btnAppResume.setVisibility(View.GONE);
            }
        }

        // Chat button
        if (btnAppChat != null) {
            btnAppChat.setOnClickListener(v -> {
                if (appId > 0) {
                    ChatActivity.launchFromApplication(this, appId, studentName, role);
                } else {
                    ChatActivity.launch(this, studentName, true);
                }
            });
        }

        // Update Status button & row click
        View.OnClickListener openReview = v ->
                ReviewApplicantBottomSheet.show(getSupportFragmentManager(), app, this::loadStudentProfile);
        if (btnAppUpdateStatus != null) {
            btnAppUpdateStatus.setOnClickListener(openReview);
        }
        row.setOnClickListener(openReview);

        containerStudentApplications.addView(row);
    }

    private void showScheduleOrRescheduleDialog(int appId, int existingInterviewId,
                                                String candidateName, String defaultLocation) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        DatePickerDialog dateDlg = new DatePickerDialog(
                this,
                (dp, year, month, day) -> {
                    TimePickerDialog timeDlg = new TimePickerDialog(
                            this,
                            (tp, hour, minute) -> {
                                String scheduledAt = String.format(Locale.US,
                                        "%04d-%02d-%02d %02d:%02d:00",
                                        year, month + 1, day, hour, minute);
                                showInterviewDetailsDialog(appId, existingInterviewId,
                                        candidateName, scheduledAt, defaultLocation);
                            },
                            10, 0, false);
                    timeDlg.setTitle("Select Interview Time");
                    timeDlg.show();
                },
                tomorrow.getYear(),
                tomorrow.getMonthValue() - 1,
                tomorrow.getDayOfMonth());
        dateDlg.getDatePicker().setMinDate(System.currentTimeMillis() + 3_600_000L);
        dateDlg.setTitle(existingInterviewId > 0 ? "Select New Interview Date" : "Select Interview Date");
        dateDlg.show();
    }

    private void showInterviewDetailsDialog(int appId, int existingInterviewId,
                                            String candidateName, String scheduledAt,
                                            String defaultLocation) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, (int) (12 * density), pad, 0);

        RadioGroup rgMode = new RadioGroup(this);
        rgMode.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbInPerson = new RadioButton(this);
        rbInPerson.setId(View.generateViewId());
        rbInPerson.setText("In-Person Office");
        rbInPerson.setChecked(true);
        RadioButton rbOnline = new RadioButton(this);
        rbOnline.setId(View.generateViewId());
        rbOnline.setText("Online Video");
        rgMode.addView(rbInPerson);
        rgMode.addView(rbOnline);
        layout.addView(rgMode);

        EditText etLocLink = new EditText(this);
        etLocLink.setHint("Office Location or Google Meet / Zoom Link");
        etLocLink.setText(defaultLocation);
        etLocLink.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(etLocLink);

        EditText etNotes = new EditText(this);
        etNotes.setHint("Interview instructions / notes for student...");
        etNotes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        layout.addView(etNotes);

        rgMode.setOnCheckedChangeListener((g, id) -> {
            if (id == rbOnline.getId()) {
                etLocLink.setText("https://meet.google.com/new");
            } else {
                etLocLink.setText(defaultLocation);
            }
        });

        String dialogTitle = (existingInterviewId > 0 ? "Reschedule • " : "Schedule • ")
                + scheduledAt.substring(0, 16);

        new MaterialAlertDialogBuilder(this)
                .setTitle(dialogTitle)
                .setView(layout)
                .setPositiveButton(existingInterviewId > 0 ? "Update Schedule" : "Send Invitation", (d, w) -> {
                    String mode = rgMode.getCheckedRadioButtonId() == rbOnline.getId()
                            ? "online" : "in_person";
                    String locOrLink = etLocLink.getText() == null ? "" : etLocLink.getText().toString().trim();
                    if (locOrLink.isEmpty()) locOrLink = defaultLocation;
                    String notes = etNotes.getText() == null ? "" : etNotes.getText().toString().trim();

                    JsonObject payload = new JsonObject();
                    payload.addProperty("scheduled_at", scheduledAt);
                    payload.addProperty("meeting_type", mode);
                    payload.addProperty("location_or_link", locOrLink);
                    if (!notes.isEmpty()) payload.addProperty("notes", notes);

                    if (scrollRefresh != null) scrollRefresh.setRefreshing(true);

                    CompanyRepository.JsonResult cb = new CompanyRepository.JsonResult() {
                        @Override
                        public void onSuccess(@NonNull JsonObject body) {
                            if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                            Toast.makeText(CompanyStudentProfileActivity.this,
                                    "Interview scheduled with " + candidateName + "! 🎉",
                                    Toast.LENGTH_SHORT).show();
                            loadStudentProfile();
                        }

                        @Override
                        public void onError(@NonNull ApiError error) {
                            if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                            Toast.makeText(CompanyStudentProfileActivity.this,
                                    error.message, Toast.LENGTH_LONG).show();
                        }
                    };

                    if (existingInterviewId > 0) {
                        repo.updateInterview(existingInterviewId, payload, cb);
                    } else {
                        repo.scheduleInterview(appId, payload, cb);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    private static String initials(String name) {
        if (name == null || name.isEmpty()) return "ST";
        String[] parts = name.trim().split("\\s+");
        if (parts.length > 1) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.ROOT);
    }
}
