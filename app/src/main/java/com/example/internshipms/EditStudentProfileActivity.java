package com.example.internshipms;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.internshipms.session.SessionManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EditStudentProfileActivity extends AppCompatActivity {

    public static final String PREF_PROFILE = "student_profile_prefs";
    public static final String KEY_HEADLINE = "headline";
    public static final String KEY_UNIVERSITY = "university";
    public static final String KEY_GPA = "gpa";
    public static final String KEY_FULL_NAME = "full_name";
    public static final String KEY_DOB = "dob";
    public static final String KEY_GENDER = "gender";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_GITHUB = "github";
    public static final String KEY_PHONE = "phone";
    public static final String KEY_NATIONALITY = "nationality";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_LANGUAGES = "languages";
    public static final String KEY_FRAMEWORKS = "frameworks";
    public static final String KEY_TRANSCRIPT = "transcript";

    private TextInputEditText etHeadline, etUniversity, etGpa, etFullName, etDob, etGender,
            etEmail, etGithub, etPhone, etNationality, etAddress, etLanguages, etFrameworks;
    private TextInputLayout tilFullName, tilEmail, tilGpa;
    private TextView tvTranscriptStatus, tvAvatarInitials;
    private ImageView ivAvatarPreview;
    private ChipGroup chipGroupPreviewLanguages, chipGroupPreviewFrameworks;

    private final ActivityResultLauncher<String> photoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoPicked);

    private final ActivityResultLauncher<String> pdfPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPdfPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_student_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        ivAvatarPreview = findViewById(R.id.ivAvatarPreview);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvTranscriptStatus = findViewById(R.id.tvTranscriptStatus);
        chipGroupPreviewLanguages = findViewById(R.id.chipGroupPreviewLanguages);
        chipGroupPreviewFrameworks = findViewById(R.id.chipGroupPreviewFrameworks);

        tilFullName = findViewById(R.id.tilFullName);
        tilEmail = findViewById(R.id.tilEmail);
        tilGpa = findViewById(R.id.tilGpa);

        etHeadline = findViewById(R.id.etHeadline);
        etUniversity = findViewById(R.id.etUniversity);
        etGpa = findViewById(R.id.etGpa);
        etFullName = findViewById(R.id.etFullName);
        etDob = findViewById(R.id.etDob);
        etGender = findViewById(R.id.etGender);
        etEmail = findViewById(R.id.etEmail);
        etGithub = findViewById(R.id.etGithub);
        etPhone = findViewById(R.id.etPhone);
        etNationality = findViewById(R.id.etNationality);
        etAddress = findViewById(R.id.etAddress);
        etLanguages = findViewById(R.id.etLanguages);
        etFrameworks = findViewById(R.id.etFrameworks);

        loadSavedFields();

        etFullName.addTextChangedListener(new SimpleWatcher(() ->
                tvAvatarInitials.setText(initials(text(etFullName)))));
        etLanguages.addTextChangedListener(new SimpleWatcher(() ->
                renderSkillPreview(chipGroupPreviewLanguages, text(etLanguages), true)));
        etFrameworks.addTextChangedListener(new SimpleWatcher(() ->
                renderSkillPreview(chipGroupPreviewFrameworks, text(etFrameworks), false)));

        etDob.setFocusable(false);
        etDob.setOnClickListener(v -> showDatePicker());

        etGender.setFocusable(false);
        etGender.setOnClickListener(v -> showGenderPicker());

        View.OnClickListener pickPhoto = v -> photoPicker.launch("image/*");
        findViewById(R.id.btnChangePhoto).setOnClickListener(pickPhoto);
        findViewById(R.id.btnUploadPhotoPill).setOnClickListener(pickPhoto);
        findViewById(R.id.btnUploadTranscript).setOnClickListener(v -> pdfPicker.launch("application/pdf"));
        findViewById(R.id.btnSave).setOnClickListener(v -> saveChanges());
        findViewById(R.id.btnTopSave).setOnClickListener(v -> saveChanges());
    }

    private void loadSavedFields() {
        SharedPreferences prefs = getSharedPreferences(PREF_PROFILE, MODE_PRIVATE);
        SessionManager session = SessionManager.get(this);
        String defaultEmail = session.getEmail() != null && !session.getEmail().isEmpty()
                ? session.getEmail() : "helloworld@gmail.com";

        String fullName = prefs.getString(KEY_FULL_NAME, "Tep Tida");
        String languages = prefs.getString(KEY_LANGUAGES,
                "Kotlin, Java, Python, JavaScript, TypeScript, SQL");
        String frameworks = prefs.getString(KEY_FRAMEWORKS,
                "Jetpack Compose, Android SDK, SQLite / Room, Retrofit, Coroutines");

        etHeadline.setText(prefs.getString(KEY_HEADLINE, "Full Stack Developer Intern"));
        etUniversity.setText(prefs.getString(KEY_UNIVERSITY, "University of South-East Asia (USEA)"));
        etGpa.setText(prefs.getString(KEY_GPA, "3.85"));
        etFullName.setText(fullName);
        etDob.setText(prefs.getString(KEY_DOB, "14 October 2004"));
        etGender.setText(prefs.getString(KEY_GENDER, "Female"));
        etEmail.setText(prefs.getString(KEY_EMAIL, defaultEmail));
        etGithub.setText(prefs.getString(KEY_GITHUB, "https://github.com"));
        etPhone.setText(prefs.getString(KEY_PHONE, "+855 12 890 123"));
        etNationality.setText(prefs.getString(KEY_NATIONALITY, "Cambodian"));
        etAddress.setText(prefs.getString(KEY_ADDRESS,
                "Sangkat Toul Tompoung 1, Khan Chamkarmon, Phnom Penh"));
        etLanguages.setText(languages);
        etFrameworks.setText(frameworks);

        tvAvatarInitials.setText(initials(fullName));
        com.example.internshipms.network.AvatarLoader.bind(
                session.getAvatarUrl(), ivAvatarPreview, tvAvatarInitials);
        renderSkillPreview(chipGroupPreviewLanguages, languages, true);
        renderSkillPreview(chipGroupPreviewFrameworks, frameworks, false);
    }

    private void renderSkillPreview(ChipGroup group, String rawCsv, boolean isGreen) {
        if (group == null) return;
        group.removeAllViews();
        int bgColor = ContextCompat.getColor(this,
                isGreen ? R.color.tint_green_bg : R.color.tint_indigo_bg);
        int textColor = isGreen
                ? MaterialColors.getColor(group, androidx.appcompat.R.attr.colorPrimary)
                : ContextCompat.getColor(this, R.color.tint_indigo);
        float radiusPx = 16f * getResources().getDisplayMetrics().density;

        for (String raw : rawCsv.split(",")) {
            String label = raw.trim();
            if (label.isEmpty()) continue;
            Chip chip = new Chip(this);
            chip.setText("✦ " + label);
            chip.setTextSize(11f);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
            chip.setTextColor(textColor);
            chip.setChipCornerRadius(radiusPx);
            chip.setChipStrokeWidth(0f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setClickable(false);
            group.addView(chip);
        }
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.set(2004, Calendar.OCTOBER, 14);
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, dayOfMonth);
            String formatted = new SimpleDateFormat("dd MMMM yyyy", Locale.US).format(picked.getTime());
            etDob.setText(formatted);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showGenderPicker() {
        String[] genders = {"Female", "Male", "Other"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Gender")
                .setItems(genders, (d, which) -> etGender.setText(genders[which]))
                .show();
    }

    private void onPhotoPicked(@Nullable Uri uri) {
        if (uri == null) return;
        ivAvatarPreview.setVisibility(View.VISIBLE);
        tvAvatarInitials.setVisibility(View.GONE);
        ivAvatarPreview.setImageURI(uri);

        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return;
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            String mime = getContentResolver().getType(uri);
            new com.example.internshipms.repository.StudentRepository(this)
                    .updateAvatar(buf.toByteArray(), "avatar.jpg", mime,
                            new com.example.internshipms.repository.StudentRepository.JsonResult() {
                                @Override
                                public void onSuccess(@androidx.annotation.NonNull com.google.gson.JsonObject body) {
                                    com.google.gson.JsonObject d = com.example.internshipms.network.Json.data(body);
                                    com.google.gson.JsonObject u = com.example.internshipms.network.Json.obj(d, "user");
                                    String url = com.example.internshipms.network.AvatarLoader.extractUrl(u, d);
                                    if (url != null) {
                                        com.example.internshipms.network.AvatarLoader.invalidateCache();
                                        SessionManager.get(EditStudentProfileActivity.this).setAvatarUrl(url);
                                    }
                                    Toast.makeText(EditStudentProfileActivity.this,
                                            "Avatar updated!", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(@androidx.annotation.NonNull com.example.internshipms.network.ApiError error) {
                                    Toast.makeText(EditStudentProfileActivity.this,
                                            error.message, Toast.LENGTH_LONG).show();
                                }
                            });
        } catch (Exception ignored) {
        }
    }

    private void onPdfPicked(@Nullable Uri uri) {
        if (uri == null) return;
        String displayName = "Official_Transcript.pdf";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = cursor.getString(idx);
                    if (n != null && !n.isEmpty()) displayName = n;
                }
            }
        } catch (Exception ignored) {
        }
        tvTranscriptStatus.setText("✓ Attached: " + displayName);
    }

    private void saveChanges() {
        tilFullName.setError(null);
        tilEmail.setError(null);
        tilGpa.setError(null);

        String fullName = text(etFullName);
        String email = text(etEmail);
        String gpaStr = text(etGpa);

        boolean ok = true;
        if (fullName.isEmpty()) {
            tilFullName.setError(getString(R.string.error_required));
            ok = false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_email));
            ok = false;
        }
        try {
            double g = Double.parseDouble(gpaStr);
            if (g < 0.0 || g > 4.0) {
                tilGpa.setError("0.00 - 4.00");
                ok = false;
            }
        } catch (NumberFormatException e) {
            tilGpa.setError("Invalid GPA");
            ok = false;
        }
        if (!ok) return;

        getSharedPreferences(PREF_PROFILE, MODE_PRIVATE).edit()
                .putString(KEY_HEADLINE, text(etHeadline))
                .putString(KEY_UNIVERSITY, text(etUniversity))
                .putString(KEY_GPA, gpaStr)
                .putString(KEY_FULL_NAME, fullName)
                .putString(KEY_DOB, text(etDob))
                .putString(KEY_GENDER, text(etGender))
                .putString(KEY_EMAIL, email)
                .putString(KEY_GITHUB, text(etGithub))
                .putString(KEY_PHONE, text(etPhone))
                .putString(KEY_NATIONALITY, text(etNationality))
                .putString(KEY_ADDRESS, text(etAddress))
                .putString(KEY_LANGUAGES, text(etLanguages))
                .putString(KEY_FRAMEWORKS, text(etFrameworks))
                .apply();

        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("name", fullName);
        payload.addProperty("major", text(etHeadline));
        payload.addProperty("department", text(etUniversity));
        payload.addProperty("gpa", gpaStr);
        payload.addProperty("phone", text(etPhone));
        String skillsCombined = text(etLanguages)
                + (text(etFrameworks).isEmpty() ? "" : ", " + text(etFrameworks));
        payload.addProperty("skills", skillsCombined);
        payload.addProperty("bio", text(etAddress));

        new com.example.internshipms.repository.StudentRepository(this).updateProfile(
                payload, new com.example.internshipms.repository.StudentRepository.JsonResult() {
                    @Override
                    public void onSuccess(@androidx.annotation.NonNull com.google.gson.JsonObject body) {
                        if (isFinishing()) return;
                        Toast.makeText(EditStudentProfileActivity.this,
                                "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onError(@androidx.annotation.NonNull com.example.internshipms.network.ApiError error) {
                        if (isFinishing()) return;
                        Toast.makeText(EditStudentProfileActivity.this,
                                error.message, Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
    }

    private static String initials(String text) {
        String[] words = text.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "S";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable callback;

        SimpleWatcher(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            callback.run();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
