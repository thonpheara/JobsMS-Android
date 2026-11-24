package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.StudentRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.gson.JsonObject;

import java.util.Locale;

import retrofit2.Call;

public class InternshipDetailActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "extra_post_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_COMPANY = "extra_company";
    public static final String EXTRA_LOCATION = "extra_location";
    public static final String EXTRA_DURATION = "extra_duration";
    public static final String EXTRA_STIPEND = "extra_stipend";
    public static final String EXTRA_CUTOFF = "extra_cutoff";
    public static final String EXTRA_DESC = "extra_desc";
    public static final String EXTRA_REQS = "extra_reqs";
    public static final String EXTRA_APPLIED = "extra_applied";
    public static final String EXTRA_BOOKMARKED = "extra_bookmarked";
    public static final String EXTRA_COMPANY_ID = "extra_company_id";

    private Call<JsonObject> detailCall;
    private int postId;
    private int companyId;
    private String title, company, location;
    private boolean hasApplied;
    private boolean isBookmarked;

    public static void launch(Context context, String title, String company,
                              String location, String duration, String stipend, String cutoff) {
        launch(context, 0, title, company, location, duration, stipend, cutoff, "", "", false, false, 0);
    }

    public static void launch(Context context, int postId, String title, String company,
                              String location, String duration, String stipend, String cutoff,
                              String description, String requirements, boolean hasApplied) {
        launch(context, postId, title, company, location, duration, stipend, cutoff,
                description, requirements, hasApplied, false, 0);
    }

    public static void launch(Context context, int postId, String title, String company,
                              String location, String duration, String stipend, String cutoff,
                              String description, String requirements, boolean hasApplied,
                              boolean isBookmarked, int companyId) {
        Intent intent = new Intent(context, InternshipDetailActivity.class);
        intent.putExtra(EXTRA_POST_ID, postId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_COMPANY, company);
        intent.putExtra(EXTRA_LOCATION, location);
        intent.putExtra(EXTRA_DURATION, duration);
        intent.putExtra(EXTRA_STIPEND, stipend);
        intent.putExtra(EXTRA_CUTOFF, cutoff);
        intent.putExtra(EXTRA_DESC, description);
        intent.putExtra(EXTRA_REQS, requirements);
        intent.putExtra(EXTRA_APPLIED, hasApplied);
        intent.putExtra(EXTRA_BOOKMARKED, isBookmarked);
        intent.putExtra(EXTRA_COMPANY_ID, companyId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_internship_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        Intent intent = getIntent();
        postId = intent != null ? intent.getIntExtra(EXTRA_POST_ID, 0) : 0;
        title = getOrDefault(intent, EXTRA_TITLE, "Internship Position");
        company = getOrDefault(intent, EXTRA_COMPANY, "Company");
        location = getOrDefault(intent, EXTRA_LOCATION, "Cambodia");
        String duration = getOrDefault(intent, EXTRA_DURATION, "Full-Time");
        String stipend = getOrDefault(intent, EXTRA_STIPEND, "Open");
        String cutoff = getOrDefault(intent, EXTRA_CUTOFF, "—");
        String desc = getOrDefault(intent, EXTRA_DESC, "");
        String reqs = getOrDefault(intent, EXTRA_REQS, "");
        hasApplied = intent != null && intent.getBooleanExtra(EXTRA_APPLIED, false);
        isBookmarked = intent != null && intent.getBooleanExtra(EXTRA_BOOKMARKED, false);
        companyId = intent != null ? intent.getIntExtra(EXTRA_COMPANY_ID, 0) : 0;

        renderHeader(title, company, location, duration, stipend, cutoff);
        renderBody(desc, reqs, null);
        updateApplyButton();
        updateBookmarkIcon();

        findViewById(R.id.btnBookmark).setOnClickListener(v -> toggleBookmark());
        View rowCompany = findViewById(R.id.rowCompanyProfile);
        if (rowCompany != null) {
            rowCompany.setOnClickListener(v -> {
                if (companyId > 0) {
                    CompanyProfileBottomSheet.show(getSupportFragmentManager(),
                            companyId, company, location);
                }
            });
        }

        if (postId > 0) {
            fetchLivePost(postId);
        }
    }

    private void toggleBookmark() {
        if (postId <= 0) return;
        boolean prev = isBookmarked;
        isBookmarked = !prev;
        updateBookmarkIcon();
        new StudentRepository(this).toggleBookmark(postId, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject d = Json.data(body);
                if (d.has("bookmarked") && !d.get("bookmarked").isJsonNull()) {
                    isBookmarked = d.get("bookmarked").getAsBoolean();
                }
                updateBookmarkIcon();
                Toast.makeText(InternshipDetailActivity.this,
                        isBookmarked ? "Saved to bookmarks ★" : "Removed from bookmarks",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                isBookmarked = prev;
                updateBookmarkIcon();
                Toast.makeText(InternshipDetailActivity.this, error.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBookmarkIcon() {
        ImageButton btn = findViewById(R.id.btnBookmark);
        if (btn == null) return;
        int primaryGreen = MaterialColors.getColor(btn, androidx.appcompat.R.attr.colorPrimary);
        if (isBookmarked) {
            btn.setImageResource(R.drawable.ic_bookmark_filled);
            btn.setColorFilter(primaryGreen);
        } else {
            btn.setImageResource(R.drawable.ic_bookmark_outline);
            btn.setColorFilter(ContextCompat.getColor(this, R.color.nav_unselected));
        }
    }

    private void fetchLivePost(int id) {
        StudentRepository repo = new StudentRepository(this);
        detailCall = repo.showPost(id, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject d = Json.data(body);
                JsonObject post = Json.obj(d, "post");
                if (post == null) post = d;

                title = Json.str(post, title, "title");
                company = StudentDashboardActivity.companyName(post, post);
                int liveCompId = StudentDashboardActivity.companyId(post, d);
                if (liveCompId > 0) companyId = liveCompId;
                location = Json.str(post, location, "location");
                String duration = StudentDashboardActivity.formatTypeOrCategory(post);
                String badge = StudentDashboardActivity.formatPostBadge(post);
                String deadline = StudentDashboardActivity.formatDeadlineDate(
                        Json.str(post, null, "deadline"));
                String desc = Json.str(post, "", "description");
                String reqs = Json.str(post, "", "requirements");

                if (d.has("has_applied") && !d.get("has_applied").isJsonNull()) {
                    hasApplied = d.get("has_applied").getAsBoolean();
                }
                if (d.has("is_bookmarked") && !d.get("is_bookmarked").isJsonNull()) {
                    isBookmarked = d.get("is_bookmarked").getAsBoolean();
                } else if (post.has("is_bookmarked") && !post.get("is_bookmarked").isJsonNull()) {
                    isBookmarked = post.get("is_bookmarked").getAsBoolean();
                }

                renderHeader(title, company, location, duration, badge, deadline);
                String logoUrl = com.example.internshipms.network.AvatarLoader
                        .extractCompanyLogo(post, d, company);
                com.example.internshipms.network.AvatarLoader.bind(
                        logoUrl,
                        findViewById(R.id.ivCompanyLogo),
                        findViewById(R.id.tvInitials));
                renderBody(desc, reqs, post);
                updateApplyButton();
                updateBookmarkIcon();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                // Keep intent data if offline
            }
        });
    }

    private void renderHeader(String title, String company, String location,
                              String duration, String stipend, String cutoff) {
        ((TextView) findViewById(R.id.tvTitle)).setText(title);
        ((TextView) findViewById(R.id.tvCompany)).setText(company);
        TextView tvInitials = findViewById(R.id.tvInitials);
        tvInitials.setText(initials(company));
        String cachedLogo = com.example.internshipms.network.AvatarLoader
                .extractCompanyLogo(null, null, company);
        com.example.internshipms.network.AvatarLoader.bind(
                cachedLogo,
                findViewById(R.id.ivCompanyLogo),
                tvInitials);
        ((TextView) findViewById(R.id.tvLocation)).setText(location);
        String safeDuration = (duration == null || StudentDashboardActivity.isWorkMode(duration))
                ? "Internship" : duration;
        ((TextView) findViewById(R.id.tvDuration)).setText(safeDuration);
        String shortBadge = stipend.contains("•") ? stipend.split("•")[0].trim() : stipend;
        if (StudentDashboardActivity.isWorkMode(shortBadge)) shortBadge = "Open";
        ((TextView) findViewById(R.id.tvStipend)).setText(
                shortBadge.replace(" / mo", "").replace("/mo", "").trim());
        ((TextView) findViewById(R.id.tvCutoff)).setText(cutoff);
    }

    private void renderBody(String description, String requirements, @Nullable JsonObject post) {
        ViewGroup bulletsContainer = findViewById(R.id.containerBullets);
        bulletsContainer.removeAllViews();
        if (description != null && !description.trim().isEmpty()) {
            String[] lines = description.split("\\r?\\n");
            for (String line : lines) {
                String clean = line.trim().replaceFirst("^[•\\-*]+\\s*", "");
                if (!clean.isEmpty()) addBullet(clean);
            }
        } else {
            addBullet("Hands-on internship opportunity with <b>" + company + "</b> in " + location + ".");
            addBullet("Collaborate with the engineering &amp; operations team on real-world projects.");
        }

        ViewGroup reqContainer = findViewById(R.id.containerRequirements);
        reqContainer.removeAllViews();
        if (requirements != null && !requirements.trim().isEmpty()) {
            String[] lines = requirements.split("\\r?\\n");
            int idx = 0;
            int[] icons = {R.drawable.ic_logo_cap, R.drawable.ic_code, R.drawable.ic_star};
            for (String line : lines) {
                String clean = line.trim().replaceFirst("^[•\\-*]+\\s*", "");
                if (!clean.isEmpty()) {
                    addRequirement(icons[idx % icons.length], "Requirement " + (idx + 1), clean);
                    idx++;
                }
            }
        } else {
            String category = post != null ? Json.str(post, "General", "category") : "General";
            if (StudentDashboardActivity.isWorkMode(category)) category = "General";
            addRequirement(R.drawable.ic_logo_cap, "Category & Field",
                    "Relevant academic background or project experience in " + category + ".");
            addRequirement(R.drawable.ic_location, "Placement Location",
                    "Internship placement based in " + location + ".");
        }
    }

    private void updateApplyButton() {
        View btn = findViewById(R.id.btnApply);
        if (btn instanceof MaterialButton) {
            MaterialButton mb = (MaterialButton) btn;
            if (hasApplied) {
                mb.setText("Already Applied ✓");
                mb.setEnabled(false);
                return;
            }
            mb.setText("Apply for This Internship");
            mb.setEnabled(true);
        }
        btn.setOnClickListener(v -> ApplyInternshipBottomSheet.show(
                getSupportFragmentManager(), postId, title, company, location, () -> {
                    hasApplied = true;
                    updateApplyButton();
                }));
    }

    private void addBullet(String htmlText) {
        ViewGroup container = findViewById(R.id.containerBullets);
        View view = LayoutInflater.from(this).inflate(R.layout.item_bullet, container, false);
        TextView tv = view.findViewById(R.id.tvBullet);
        tv.setText(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_COMPACT));
        container.addView(view);
    }

    private void addRequirement(@DrawableRes int iconRes, String title, String desc) {
        ViewGroup container = findViewById(R.id.containerRequirements);
        View view = LayoutInflater.from(this).inflate(R.layout.item_requirement, container, false);
        ((ImageView) view.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) view.findViewById(R.id.tvReqTitle)).setText(title);
        ((TextView) view.findViewById(R.id.tvReqDesc)).setText(desc);
        container.addView(view);
    }

    private static String getOrDefault(Intent intent, String key, String fallback) {
        if (intent == null) return fallback;
        String val = intent.getStringExtra(key);
        return (val == null || val.isEmpty()) ? fallback : val;
    }

    private static String initials(String text) {
        String[] words = text.trim().split("[\\s/(]+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "I";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    @Override
    protected void onDestroy() {
        if (detailCall != null) detailCall.cancel();
        super.onDestroy();
    }
}
