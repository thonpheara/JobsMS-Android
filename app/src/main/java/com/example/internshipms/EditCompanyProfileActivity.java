package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.AvatarLoader;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.CompanyRepository;
import com.example.internshipms.session.SessionManager;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class EditCompanyProfileActivity extends AppCompatActivity {

    public static final String EXTRA_COMPANY_JSON = "extra_company_json";

    public static void launch(Context context, @Nullable JsonObject company) {
        Intent intent = new Intent(context, EditCompanyProfileActivity.class);
        if (company != null) {
            intent.putExtra(EXTRA_COMPANY_JSON, company.toString());
        }
        context.startActivity(intent);
    }

    private CompanyRepository repo;
    private LinearProgressIndicator progressLoading;

    private TextView tvLogoInitials;
    private ImageView ivLogoPreview;
    private ImageView ivCoverPreview;

    private TextInputLayout tilCompanyName;
    private TextInputEditText etCompanyName;
    private TextInputEditText etIndustry;
    private TextInputEditText etLocation;
    private TextInputEditText etAddress;
    private TextInputEditText etWebsite;
    private TextInputEditText etContactPerson;
    private TextInputEditText etContactPhone;
    private TextInputEditText etEmail;
    private TextInputEditText etDescription;

    private JsonObject companyJson;

    private final ActivityResultLauncher<String> photoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onLogoPicked);

    private final ActivityResultLauncher<String> coverPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onCoverPicked);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_company_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repo = new CompanyRepository(this);

        // ── Bind Views ───────────────────────────────────────────────────────
        progressLoading = findViewById(R.id.progressLoading);

        tvLogoInitials = findViewById(R.id.tvLogoInitials);
        ivLogoPreview = findViewById(R.id.ivLogoPreview);
        ivCoverPreview = findViewById(R.id.ivCoverPreview);

        tilCompanyName = findViewById(R.id.tilCompanyName);
        etCompanyName = findViewById(R.id.etCompanyName);
        etIndustry = findViewById(R.id.etIndustry);
        etLocation = findViewById(R.id.etLocation);
        etAddress = findViewById(R.id.etAddress);
        etWebsite = findViewById(R.id.etWebsite);
        etContactPerson = findViewById(R.id.etContactPerson);
        etContactPhone = findViewById(R.id.etContactPhone);
        etEmail = findViewById(R.id.etEmail);
        etDescription = findViewById(R.id.etDescription);

        // Pre-fill email from session
        String sessionEmail = session.getEmail();
        if (sessionEmail != null && !sessionEmail.isEmpty()) {
            etEmail.setText(sessionEmail);
        }

        // Top Navigation buttons
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        findViewById(R.id.btnTopSave).setOnClickListener(v -> saveProfileChanges());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveProfileChanges());

        // Logo Picker triggers
        View.OnClickListener pickLogo = v -> photoPicker.launch("image/*");
        findViewById(R.id.btnChangeLogo).setOnClickListener(pickLogo);
        findViewById(R.id.btnUploadLogoPill).setOnClickListener(pickLogo);

        // Cover Picker triggers
        View.OnClickListener pickCover = v -> coverPicker.launch("image/*");
        View btnChangeCover = findViewById(R.id.btnChangeCover);
        if (btnChangeCover != null) btnChangeCover.setOnClickListener(pickCover);
        View btnUploadCoverPill = findViewById(R.id.btnUploadCoverPill);
        if (btnUploadCoverPill != null) btnUploadCoverPill.setOnClickListener(pickCover);

        // Dynamic initials on company name change
        etCompanyName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ivLogoPreview.getVisibility() != View.VISIBLE) {
                    tvLogoInitials.setText(initials(s == null ? "" : s.toString()));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Parse passed company json if present
        String jsonExtra = getIntent().getStringExtra(EXTRA_COMPANY_JSON);
        if (jsonExtra != null && !jsonExtra.isEmpty()) {
            try {
                companyJson = JsonParser.parseString(jsonExtra).getAsJsonObject();
            } catch (Exception ignored) {
            }
        }

        if (companyJson != null) {
            bindFields(companyJson);
        } else {
            loadFromApi();
        }
    }

    private void loadFromApi() {
        progressLoading.setVisibility(View.VISIBLE);
        repo.profile(new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                progressLoading.setVisibility(View.GONE);
                companyJson = Json.data(body);
                bindFields(companyJson);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                progressLoading.setVisibility(View.GONE);
            }
        });

        repo.me(new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject data = Json.data(body);
                JsonObject user = Json.obj(data, "user");
                if (user != null) {
                    String email = Json.str(user, null, "email");
                    if (email != null && !email.isEmpty()) {
                        etEmail.setText(email);
                    }
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    private void bindFields(@NonNull JsonObject company) {
        String fallbackName = SessionManager.get(this).getName();
        String name = Json.str(company, fallbackName != null ? fallbackName : "", "company_name", "name");
        etCompanyName.setText(name);
        etIndustry.setText(Json.str(company, "", "industry"));
        etLocation.setText(Json.str(company, "", "location"));
        etAddress.setText(Json.str(company, "", "address"));
        etWebsite.setText(Json.str(company, "", "website"));
        etContactPerson.setText(Json.str(company, "", "contact_person"));
        etContactPhone.setText(Json.str(company, "", "contact_phone"));
        etDescription.setText(Json.str(company, "", "description"));

        tvLogoInitials.setText(initials(name));
        String logoUrl = AvatarLoader.extractUrl(null, company);
        AvatarLoader.bind(logoUrl, ivLogoPreview, tvLogoInitials);

        String coverUrl = AvatarLoader.extractCoverUrl(company);
        if (ivCoverPreview != null) {
            AvatarLoader.bind(coverUrl, ivCoverPreview, null);
        }
    }

    private void saveProfileChanges() {
        tilCompanyName.setError(null);

        String companyName = text(etCompanyName);
        if (companyName.isEmpty()) {
            tilCompanyName.setError(getString(R.string.error_required));
            etCompanyName.requestFocus();
            return;
        }

        String website = text(etWebsite);
        if (!website.isEmpty() && !website.startsWith("http://") && !website.startsWith("https://")) {
            website = "https://" + website;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("company_name", companyName);
        payload.addProperty("industry", text(etIndustry));
        payload.addProperty("location", text(etLocation));
        payload.addProperty("address", text(etAddress));
        if (!website.isEmpty()) {
            payload.addProperty("website", website);
        }
        payload.addProperty("contact_person", text(etContactPerson));
        payload.addProperty("contact_phone", text(etContactPhone));
        payload.addProperty("description", text(etDescription));

        progressLoading.setVisibility(View.VISIBLE);
        findViewById(R.id.btnSave).setEnabled(false);
        findViewById(R.id.btnTopSave).setEnabled(false);

        repo.updateProfile(payload, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (isFinishing()) return;
                progressLoading.setVisibility(View.GONE);
                SessionManager.get(EditCompanyProfileActivity.this).setName(companyName);
                Toast.makeText(EditCompanyProfileActivity.this,
                        "Company profile updated successfully!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (isFinishing()) return;
                progressLoading.setVisibility(View.GONE);
                findViewById(R.id.btnSave).setEnabled(true);
                findViewById(R.id.btnTopSave).setEnabled(true);
                Toast.makeText(EditCompanyProfileActivity.this,
                        error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onLogoPicked(@Nullable Uri uri) {
        if (uri == null) return;
        byte[] bytes = readUriBytes(uri);
        if (bytes == null || bytes.length == 0) {
            Toast.makeText(this, "Could not read selected image.", Toast.LENGTH_SHORT).show();
            return;
        }

        ivLogoPreview.setVisibility(View.VISIBLE);
        tvLogoInitials.setVisibility(View.GONE);
        ivLogoPreview.setImageURI(uri);

        String mime = getContentResolver().getType(uri);
        String fileName = queryFileName(uri);
        String companyName = text(etCompanyName);
        if (companyName.isEmpty()) companyName = SessionManager.get(this).getName();

        progressLoading.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Uploading company logo…", Toast.LENGTH_SHORT).show();

        repo.uploadLogo(companyName, bytes, fileName, mime, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (isFinishing()) return;
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(EditCompanyProfileActivity.this,
                        "Company logo updated!", Toast.LENGTH_SHORT).show();
                JsonObject updated = Json.data(body);
                String newUrl = AvatarLoader.extractUrl(null, updated);
                if (newUrl != null) {
                    AvatarLoader.invalidateCache();
                    SessionManager.get(EditCompanyProfileActivity.this).setAvatarUrl(newUrl);
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (isFinishing()) return;
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(EditCompanyProfileActivity.this,
                        error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onCoverPicked(@Nullable Uri uri) {
        if (uri == null) return;
        byte[] bytes = readUriBytes(uri);
        if (bytes == null || bytes.length == 0) {
            Toast.makeText(this, "Could not read selected image.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ivCoverPreview != null) {
            ivCoverPreview.setImageURI(uri);
        }

        String mime = getContentResolver().getType(uri);
        String fileName = queryFileName(uri);
        String companyName = text(etCompanyName);
        if (companyName.isEmpty()) companyName = SessionManager.get(this).getName();

        progressLoading.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Uploading company cover…", Toast.LENGTH_SHORT).show();

        repo.uploadCover(companyName, bytes, fileName, mime, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (isFinishing()) return;
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(EditCompanyProfileActivity.this,
                        "Company cover updated!", Toast.LENGTH_SHORT).show();
                JsonObject updated = Json.data(body);
                String newCover = AvatarLoader.extractCoverUrl(updated);
                if (newCover != null && ivCoverPreview != null) {
                    AvatarLoader.bind(newCover, ivCoverPreview, null);
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (isFinishing()) return;
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(EditCompanyProfileActivity.this,
                        error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Nullable
    private byte[] readUriBytes(@NonNull Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return null;
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private String queryFileName(@NonNull Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        }
        return "company_logo.jpg";
    }

    private static String initials(String text) {
        String[] words = text.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "C";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}

