package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CompanyProfileActivity extends AppCompatActivity {

    public static void launch(Context context) {
        context.startActivity(new Intent(context, CompanyProfileActivity.class));
    }

    private CompanyRepository repo;
    private PullRefreshScrollView scrollRefresh;
    private LinearProgressIndicator progressProfile;
    private View dotUnread;

    private TextView tvLogoInitials;
    private ImageView ivCompanyLogo;
    private ImageView ivCompanyCover;
    private TextView tvCompanyName;
    private TextView tvIndustryLocation;
    private TextView tvVerifiedBadge;
    private TextView tvCompletenessPercent;
    private LinearProgressIndicator progressCompleteness;
    private ChipGroup chipGroupMissing;
    private TextView tvCompletenessDone;

    private MaterialCardView cardVerificationAlert;
    private MaterialCardView iconVerificationAlertBox;
    private ImageView ivVerificationAlertIcon;
    private TextView tvVerificationAlertTitle;
    private TextView tvVerificationAlertBody;

    private TextView tvStatActivePosts;
    private TextView tvStatTotalApplicants;
    private TextView tvStatAccepted;

    private TextView tvCompanyDescription;
    private TextView tvFieldIndustry;
    private TextView tvFieldAddress;
    private TextView tvFieldWebsite;
    private TextView tvFieldContact;
    private TextView tvFieldEmail;

    private JsonObject currentCompany;
    private String currentWebsiteUrl;
    private int currentActivePostsCount = 0;

    private final ActivityResultLauncher<String> logoPicker =
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
        setContentView(R.layout.activity_company_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        repo = new CompanyRepository(this);

        // ── Bind Views ───────────────────────────────────────────────────────
        scrollRefresh = findViewById(R.id.scrollRefresh);
        progressProfile = findViewById(R.id.progressProfile);
        dotUnread = findViewById(R.id.dotUnread);

        tvLogoInitials = findViewById(R.id.tvLogoInitials);
        ivCompanyLogo = findViewById(R.id.ivCompanyLogo);
        ivCompanyCover = findViewById(R.id.ivCompanyCover);
        tvCompanyName = findViewById(R.id.tvCompanyName);
        tvIndustryLocation = findViewById(R.id.tvIndustryLocation);
        tvVerifiedBadge = findViewById(R.id.tvVerifiedBadge);
        tvCompletenessPercent = findViewById(R.id.tvCompletenessPercent);
        progressCompleteness = findViewById(R.id.progressCompleteness);
        chipGroupMissing = findViewById(R.id.chipGroupMissing);
        tvCompletenessDone = findViewById(R.id.tvCompletenessDone);

        cardVerificationAlert = findViewById(R.id.cardVerificationAlert);
        iconVerificationAlertBox = findViewById(R.id.iconVerificationAlertBox);
        ivVerificationAlertIcon = findViewById(R.id.ivVerificationAlertIcon);
        tvVerificationAlertTitle = findViewById(R.id.tvVerificationAlertTitle);
        tvVerificationAlertBody = findViewById(R.id.tvVerificationAlertBody);

        tvStatActivePosts = findViewById(R.id.tvStatActivePosts);
        tvStatTotalApplicants = findViewById(R.id.tvStatTotalApplicants);
        tvStatAccepted = findViewById(R.id.tvStatAccepted);

        tvCompanyDescription = findViewById(R.id.tvCompanyDescription);
        tvFieldIndustry = findViewById(R.id.tvFieldIndustry);
        tvFieldAddress = findViewById(R.id.tvFieldAddress);
        tvFieldWebsite = findViewById(R.id.tvFieldWebsite);
        tvFieldContact = findViewById(R.id.tvFieldContact);
        tvFieldEmail = findViewById(R.id.tvFieldEmail);

        // Pre-populate from session while API loads
        String sessionName = session.getName();
        if (sessionName != null && !sessionName.isEmpty()) {
            tvCompanyName.setText(sessionName);
            tvLogoInitials.setText(initials(sessionName));
        }
        String sessionEmail = session.getEmail();
        if (sessionEmail != null && !sessionEmail.isEmpty()) {
            tvFieldEmail.setText(sessionEmail);
        }

        // ── Click Listeners ──────────────────────────────────────────────────
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                NotificationsActivity.launch(this));

        View.OnClickListener openEditPage = v -> {
            Intent intent = new Intent(this, EditCompanyProfileActivity.class);
            if (currentCompany != null) {
                intent.putExtra(EditCompanyProfileActivity.EXTRA_COMPANY_JSON, currentCompany.toString());
            }
            startActivity(intent);
        };

        findViewById(R.id.btnChangeLogo).setOnClickListener(v -> logoPicker.launch("image/*"));
        View btnChangeCover = findViewById(R.id.btnChangeCover);
        if (btnChangeCover != null) {
            btnChangeCover.setOnClickListener(v -> coverPicker.launch("image/*"));
        }
        findViewById(R.id.btnEditCompany).setOnClickListener(openEditPage);
        findViewById(R.id.btnEditAbout).setOnClickListener(openEditPage);

        findViewById(R.id.cardMetricPosts).setOnClickListener(v ->
                Navigator.switchTab(this, CompanyListingsActivity.class, false));
        findViewById(R.id.cardMetricApplicants).setOnClickListener(v ->
                Navigator.switchTab(this, CompanyApplicantsActivity.class, false));
        findViewById(R.id.cardMetricHired).setOnClickListener(v ->
                Navigator.switchTab(this, CompanyApplicantsActivity.class, false));

        findViewById(R.id.rowWebsite).setOnClickListener(v -> {
            if (currentWebsiteUrl != null && !currentWebsiteUrl.isEmpty()) {
                try {
                    String url = currentWebsiteUrl.startsWith("http")
                            ? currentWebsiteUrl : "https://" + currentWebsiteUrl;
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
            } else {
                openEditPage.onClick(v);
            }
        });

        findViewById(R.id.btnChangePassword).setOnClickListener(v ->
                ChangePasswordBottomSheet.show(getSupportFragmentManager()));

        findViewById(R.id.btnLogout).setOnClickListener(v ->
                Navigator.confirmLogout(this));

        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadProfileData);
        }

        // ── Bottom Navigation ────────────────────────────────────────────────
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_profile);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) return true;
            if (id == R.id.nav_home) {
                Navigator.switchTab(this, CompanyDashboardActivity.class, false);
                return false;
            }
            if (id == R.id.nav_listings) {
                Navigator.switchTab(this, CompanyListingsActivity.class, false);
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
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null && nav.getSelectedItemId() != R.id.nav_profile) {
            nav.setSelectedItemId(R.id.nav_profile);
        }
        loadProfileData();
    }

    // ── Load Profile + Dashboard Metrics + Auth Me ───────────────────────────

    private void loadProfileData() {
        if (progressProfile != null) progressProfile.setVisibility(View.GONE);
        if (scrollRefresh != null) scrollRefresh.setRefreshing(true);

        // 1. Load company profile from GET /api/company/profile
        repo.profile(new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (progressProfile != null) progressProfile.setVisibility(View.GONE);
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonObject company = Json.data(body);
                bindCompanyProfile(company);
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (progressProfile != null) progressProfile.setVisibility(View.GONE);
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
            }
        });

        // 2. Load dashboard metrics (Active Posts, Total Applicants, Interns Hired)
        repo.dashboard(new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject data = Json.data(body);
                JsonObject stats = Json.obj(data, "stats");
                if (stats != null) {
                    int activePosts = Json.integer(stats, 0, "active_posts", "active_posts_count", "total_posts");
                    int totalApplicants = Json.integer(stats, 0, "total_applicants");
                    int accepted = Json.integer(stats, 0, "accepted");
                    currentActivePostsCount = activePosts;
                    tvStatActivePosts.setText(String.valueOf(activePosts));
                    tvStatTotalApplicants.setText(String.valueOf(totalApplicants));
                    tvStatAccepted.setText(String.valueOf(accepted));
                }
                JsonObject comp = Json.obj(data, "company");
                if (comp != null && currentCompany == null) {
                    bindCompanyProfile(comp);
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });

        // 3. Load user email & unread notifications count from GET /api/auth/me
        repo.me(new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject data = Json.data(body);
                JsonObject user = Json.obj(data, "user");
                if (user != null) {
                    String email = Json.str(user, null, "email");
                    if (email != null && !email.isEmpty()) {
                        tvFieldEmail.setText(email);
                    }
                }
                int unread = Json.integer(data, 0, "unread_notifications");
                if (dotUnread != null) {
                    dotUnread.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    private void bindCompanyProfile(@NonNull JsonObject company) {
        currentCompany = company;

        String name = Json.str(company, null, "company_name", "name");
        if (name == null || name.isEmpty()) {
            name = SessionManager.get(this).getName();
        }
        if (name == null || name.isEmpty()) {
            name = "Company";
        }

        String industry = Json.str(company, null, "industry");
        String location = Json.str(company, null, "location");
        String address = Json.str(company, null, "address");
        String website = Json.str(company, null, "website");
        String contactPerson = Json.str(company, null, "contact_person");
        String contactPhone = Json.str(company, null, "contact_phone");
        String description = Json.str(company, null, "description");
        String verificationStatus = Json.str(company, "pending", "verification_status");
        String rejectionReason = Json.str(company, null, "rejection_reason");
        boolean isRejected = "rejected".equalsIgnoreCase(verificationStatus);
        boolean isVerified = "verified".equalsIgnoreCase(verificationStatus)
                || "approved".equalsIgnoreCase(verificationStatus)
                || (company.has("is_verified")
                && !company.get("is_verified").isJsonNull()
                && company.get("is_verified").getAsBoolean());

        int apiActivePosts = Json.integer(company, -1, "active_posts_count");
        if (apiActivePosts >= 0) {
            currentActivePostsCount = apiActivePosts;
        }

        String logoUrl = AvatarLoader.extractUrl(null, company);
        String coverUrl = AvatarLoader.extractCoverUrl(company);
        if (ivCompanyCover != null) {
            AvatarLoader.bind(coverUrl, ivCompanyCover, null);
        }

        // Header Identity
        SessionManager.get(this).setName(name);
        tvCompanyName.setText(name);
        tvLogoInitials.setText(initials(name));
        AvatarLoader.bind(logoUrl, ivCompanyLogo, tvLogoInitials);

        String indText = (industry != null && !industry.isEmpty()) ? industry : "Industry not set";
        String locText = (location != null && !location.isEmpty()) ? location : "Cambodia";
        tvIndustryLocation.setText(indText + " • " + locText);

        // Verification Badge + Alert Banner (matching Web profile.blade.php)
        if (isVerified) {
            tvVerifiedBadge.setText("✓ Verified Partner");
            tvVerifiedBadge.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_green_bg)));
            tvVerifiedBadge.setTextColor(MaterialColors.getColor(
                    tvVerifiedBadge, androidx.appcompat.R.attr.colorPrimary));
            if (cardVerificationAlert != null) cardVerificationAlert.setVisibility(View.GONE);
        } else if (isRejected) {
            tvVerifiedBadge.setText("✕ Verification Rejected");
            tvVerifiedBadge.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_red_bg)));
            tvVerifiedBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
            if (cardVerificationAlert != null) {
                cardVerificationAlert.setVisibility(View.VISIBLE);
                cardVerificationAlert.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_red_bg));
                cardVerificationAlert.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_red)));
                iconVerificationAlertBox.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_red));
                ivVerificationAlertIcon.setImageResource(R.drawable.ic_info);
                tvVerificationAlertTitle.setText("Verification Needs Attention (Rejected)");
                tvVerificationAlertTitle.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
                tvVerificationAlertBody.setText(notEmpty(rejectionReason)
                        ? "Admin Feedback: " + rejectionReason.trim()
                        : "Your company verification was not approved. Please review and update your organization details below.");
            }
        } else {
            tvVerifiedBadge.setText("⏳ Pending Verification");
            tvVerifiedBadge.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.tint_amber_bg)));
            tvVerifiedBadge.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
            if (cardVerificationAlert != null) {
                cardVerificationAlert.setVisibility(View.VISIBLE);
                cardVerificationAlert.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_amber_bg));
                cardVerificationAlert.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_amber)));
                iconVerificationAlertBox.setCardBackgroundColor(ContextCompat.getColor(this, R.color.tint_amber));
                ivVerificationAlertIcon.setImageResource(R.drawable.ic_hourglass);
                tvVerificationAlertTitle.setText("Account Pending University Verification");
                tvVerificationAlertTitle.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
                tvVerificationAlertBody.setText("Your organization profile is currently under review by the University Admin. Complete all company details below to speed up verification.");
            }
        }

        // About Organization
        if (description != null && !description.trim().isEmpty()) {
            tvCompanyDescription.setText(description.trim());
            tvCompanyDescription.setTextColor(MaterialColors.getColor(
                    tvCompanyDescription, com.google.android.material.R.attr.colorOnSurface));
        } else {
            tvCompanyDescription.setText("Add a description about your company culture, engineering teams, and mentorship programs for university interns.");
            tvCompanyDescription.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
        }

        // Corporate Fields
        tvFieldIndustry.setText(notEmptyOrDefault(industry, "Not specified"));

        String fullAddr;
        if (address != null && !address.isEmpty() && location != null && !location.isEmpty()) {
            fullAddr = address + ", " + location;
        } else if (address != null && !address.isEmpty()) {
            fullAddr = address;
        } else if (location != null && !location.isEmpty()) {
            fullAddr = location;
        } else {
            fullAddr = "Not specified";
        }
        tvFieldAddress.setText(fullAddr);

        currentWebsiteUrl = website;
        tvFieldWebsite.setText(notEmptyOrDefault(website, "Add company website →"));

        StringBuilder contactSb = new StringBuilder();
        if (contactPerson != null && !contactPerson.isEmpty()) {
            contactSb.append(contactPerson);
        }
        if (contactPhone != null && !contactPhone.isEmpty()) {
            if (contactSb.length() > 0) contactSb.append(" • ");
            contactSb.append(contactPhone);
        }
        tvFieldContact.setText(contactSb.length() > 0 ? contactSb.toString() : "Not specified");

        // Completeness Calculation (9 fields matching Web profile.blade.php)
        List<String> missingLabels = new ArrayList<>();
        if (!notEmpty(logoUrl)) missingLabels.add("Logo");
        if (!notEmpty(coverUrl)) missingLabels.add("Cover");
        if (!notEmpty(industry)) missingLabels.add("Industry");
        if (!notEmpty(location)) missingLabels.add("Location");
        if (!notEmpty(contactPerson)) missingLabels.add("Hiring Lead");
        if (!notEmpty(contactPhone)) missingLabels.add("Phone");
        if (!notEmpty(website)) missingLabels.add("Website");
        if (!notEmpty(address)) missingLabels.add("Address");
        if (!notEmpty(description)) missingLabels.add("About Us");

        int filled = 9 - missingLabels.size();
        int defaultPct = Math.round((filled / 9f) * 100f);
        int pct = Json.integer(company, defaultPct, "completeness_percent", "profile_completeness");
        tvCompletenessPercent.setText(pct + "%");
        progressCompleteness.setProgress(pct);
        renderMissingCompletenessChips(missingLabels);
    }

    private void renderMissingCompletenessChips(List<String> missingLabels) {
        if (chipGroupMissing == null || tvCompletenessDone == null) return;
        chipGroupMissing.removeAllViews();

        if (missingLabels.isEmpty()) {
            chipGroupMissing.setVisibility(View.GONE);
            tvCompletenessDone.setVisibility(View.VISIBLE);
            return;
        }

        tvCompletenessDone.setVisibility(View.GONE);
        chipGroupMissing.setVisibility(View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;

        for (String label : missingLabels) {
            Chip chip = new Chip(this);
            chip.setText("+ " + label);
            chip.setTextSize(10.5f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(24f * density);
            chip.setChipCornerRadius(8f * density);
            chip.setChipStartPadding(8f * density);
            chip.setChipEndPadding(8f * density);
            chip.setTextStartPadding(2f * density);
            chip.setTextEndPadding(2f * density);
            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.bg_card)));
            chip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_soft)));
            chip.setChipStrokeWidth(1f);
            chip.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            chip.setOnClickListener(v -> {
                if ("Logo".equals(label)) {
                    logoPicker.launch("image/*");
                } else if ("Cover".equals(label)) {
                    coverPicker.launch("image/*");
                } else {
                    Intent intent = new Intent(this, EditCompanyProfileActivity.class);
                    if (currentCompany != null) {
                        intent.putExtra(EditCompanyProfileActivity.EXTRA_COMPANY_JSON, currentCompany.toString());
                    }
                    startActivity(intent);
                }
            });
            chipGroupMissing.addView(chip);
        }
    }

    // ── Logo Upload via Multipart POST /api/company/profile ──────────────────

    private void onLogoPicked(@Nullable Uri uri) {
        if (uri == null) return;
        byte[] bytes = readUriBytes(uri);
        if (bytes == null || bytes.length == 0) {
            Toast.makeText(this, "Could not read selected image.", Toast.LENGTH_SHORT).show();
            return;
        }
        String mime = getContentResolver().getType(uri);
        String fileName = queryFileName(uri);
        String companyName = tvCompanyName.getText() != null
                ? tvCompanyName.getText().toString().trim() : "Company";

        progressProfile.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Uploading company logo…", Toast.LENGTH_SHORT).show();

        repo.uploadLogo(companyName, bytes, fileName, mime, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                progressProfile.setVisibility(View.GONE);
                Toast.makeText(CompanyProfileActivity.this,
                        "Company logo updated!", Toast.LENGTH_SHORT).show();
                bindCompanyProfile(Json.data(body));
            }

            @Override
            public void onError(@NonNull ApiError error) {
                progressProfile.setVisibility(View.GONE);
                Toast.makeText(CompanyProfileActivity.this,
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
        String mime = getContentResolver().getType(uri);
        String fileName = queryFileName(uri);
        String companyName = tvCompanyName.getText() != null
                ? tvCompanyName.getText().toString().trim() : "Company";

        progressProfile.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Uploading company cover…", Toast.LENGTH_SHORT).show();

        repo.uploadCover(companyName, bytes, fileName, mime, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                progressProfile.setVisibility(View.GONE);
                Toast.makeText(CompanyProfileActivity.this,
                        "Company cover updated!", Toast.LENGTH_SHORT).show();
                bindCompanyProfile(Json.data(body));
            }

            @Override
            public void onError(@NonNull ApiError error) {
                progressProfile.setVisibility(View.GONE);
                Toast.makeText(CompanyProfileActivity.this,
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

    private static boolean notEmpty(@Nullable String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String notEmptyOrDefault(@Nullable String s, String def) {
        return (s != null && !s.trim().isEmpty()) ? s.trim() : def;
    }

    private static String initials(String text) {
        String[] words = text.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "C";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }
}
