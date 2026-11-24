package com.example.internshipms;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.AvatarLoader;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.StudentRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import retrofit2.Call;

public class CompanyProfileBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_COMPANY_ID = "arg_company_id";
    private static final String ARG_COMPANY_NAME = "arg_company_name";
    private static final String ARG_LOCATION = "arg_location";

    private Call<JsonObject> companyCall;

    public static void show(FragmentManager fm, int companyId,
                            @Nullable String companyName) {
        show(fm, companyId, companyName, null);
    }

    public static void show(FragmentManager fm, int companyId,
                            @Nullable String companyName,
                            @Nullable String location) {
        CompanyProfileBottomSheet sheet = new CompanyProfileBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_COMPANY_ID, companyId);
        args.putString(ARG_COMPANY_NAME, companyName);
        args.putString(ARG_LOCATION, location);
        sheet.setArguments(args);
        sheet.show(fm, "company_profile_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_company_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());

        Bundle args = getArguments();
        int companyId = args != null ? args.getInt(ARG_COMPANY_ID, 0) : 0;
        String fallbackName = args != null ? args.getString(ARG_COMPANY_NAME, "Company") : "Company";
        String fallbackLoc = args != null ? args.getString(ARG_LOCATION, "Cambodia") : "Cambodia";

        TextView tvName = view.findViewById(R.id.tvCompanyName);
        TextView tvInitials = view.findViewById(R.id.tvCompanyInitials);
        ImageView ivLogo = view.findViewById(R.id.ivCompanyLogo);
        TextView tvLoc = view.findViewById(R.id.tvLocation);

        tvName.setText(fallbackName);
        tvInitials.setText(StudentDashboardActivity.initials(fallbackName));
        tvLoc.setText(fallbackLoc);
        String cachedLogo = AvatarLoader.extractCompanyLogo(null, null, fallbackName);
        AvatarLoader.bind(cachedLogo, ivLogo, tvInitials);

        if (companyId > 0) {
            fetchCompany(companyId, view);
        }
    }

    private void fetchCompany(int companyId, View root) {
        StudentRepository repo = new StudentRepository(requireContext());
        companyCall = repo.showCompany(companyId, new StudentRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (!isAdded()) return;
                JsonObject d = Json.data(body);
                JsonObject company = Json.obj(d, "company");
                if (company == null) company = d;

                String name = Json.str(company, "Company", "company_name", "name");
                String industry = Json.str(company, "Technology & Operations", "industry", "sector");
                String address = Json.str(company, "Cambodia", "address", "location", "city");
                String description = Json.str(company,
                        "Registered host organization offering hands-on internship placements.",
                        "description", "bio", "about");
                String website = Json.str(company, null, "website", "website_url");
                boolean isVerified = d.has("is_verified") && !d.get("is_verified").isJsonNull()
                        ? d.get("is_verified").getAsBoolean()
                        : "verified".equalsIgnoreCase(Json.str(company, "verified", "verification_status"));
                int totalPositions = Json.integer(d, 0, "total_positions");

                ((TextView) root.findViewById(R.id.tvCompanyName)).setText(name);
                TextView tvInitials = root.findViewById(R.id.tvCompanyInitials);
                ImageView ivLogo = root.findViewById(R.id.ivCompanyLogo);
                tvInitials.setText(StudentDashboardActivity.initials(name));

                String logoUrl = AvatarLoader.extractUrl(Json.obj(company, "user"), company);
                if (logoUrl != null) {
                    AvatarLoader.rememberCompanyLogo(name, logoUrl);
                } else {
                    logoUrl = AvatarLoader.extractCompanyLogo(null, null, name);
                }
                AvatarLoader.bind(logoUrl, ivLogo, tvInitials);

                root.findViewById(R.id.tvVerifiedBadge)
                        .setVisibility(isVerified ? View.VISIBLE : View.GONE);
                ((TextView) root.findViewById(R.id.tvIndustry)).setText(industry);
                ((TextView) root.findViewById(R.id.tvLocation)).setText("📍 " + address);
                ((TextView) root.findViewById(R.id.tvCompanyBio)).setText(description);

                TextView tvWebsite = root.findViewById(R.id.tvWebsite);
                if (website != null && !website.isEmpty()) {
                    tvWebsite.setVisibility(View.VISIBLE);
                    tvWebsite.setText("🌐 " + website);
                    tvWebsite.setOnClickListener(v -> {
                        try {
                            String url = website.startsWith("http") ? website : "https://" + website;
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception ignored) {
                        }
                    });
                }

                JsonArray activePosts = Json.arr(d, "active_posts");
                ((TextView) root.findViewById(R.id.tvActiveCount))
                        .setText(String.valueOf(activePosts.size()));
                ((TextView) root.findViewById(R.id.tvTotalPositions))
                        .setText(String.valueOf(Math.max(totalPositions, activePosts.size())));

                ViewGroup container = root.findViewById(R.id.containerCompanyPosts);
                container.removeAllViews();
                for (JsonElement el : activePosts) {
                    if (!el.isJsonObject()) continue;
                    JsonObject post = el.getAsJsonObject();
                    addOpeningRow(post, name, container);
                }
                root.findViewById(R.id.tvNoOpenings)
                        .setVisibility(activePosts.size() == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    private void addOpeningRow(JsonObject post, String companyName, ViewGroup container) {
        View item = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_recent_application, container, false);

        int postId = Json.integer(post, 0, "id");
        String title = Json.str(post, "Internship Position", "title");
        String location = Json.str(post, "Cambodia", "location");
        String badge = StudentDashboardActivity.formatPostBadge(post);
        String duration = StudentDashboardActivity.formatTypeOrCategory(post);
        String deadline = StudentDashboardActivity.formatDeadlineDate(Json.str(post, "—", "deadline"));
        String desc = Json.str(post, "", "description");
        String reqs = Json.str(post, "", "requirements");
        boolean hasApplied = post.has("has_applied") && !post.get("has_applied").isJsonNull()
                && post.get("has_applied").getAsBoolean();
        boolean isBookmarked = post.has("is_bookmarked") && !post.get("is_bookmarked").isJsonNull()
                && post.get("is_bookmarked").getAsBoolean();

        TextView tvInitials = item.findViewById(R.id.tvInitials);
        ImageView ivLogo = item.findViewById(R.id.ivCompanyLogo);
        tvInitials.setText(StudentDashboardActivity.initials(companyName));
        AvatarLoader.bind(AvatarLoader.extractCompanyLogo(post, null, companyName), ivLogo, tvInitials);

        ((TextView) item.findViewById(R.id.tvAppTitle)).setText(title);
        ((TextView) item.findViewById(R.id.tvAppCompany)).setText(location + " • Due " + deadline);
        ((TextView) item.findViewById(R.id.tvAppDate)).setText(duration);
        TextView tvStatus = item.findViewById(R.id.tvAppStatus);
        tvStatus.setText(hasApplied ? "Applied ✓" : badge);

        item.setOnClickListener(v -> {
            dismiss();
            InternshipDetailActivity.launch(requireContext(), postId, title, companyName,
                    location, duration, badge, deadline, desc, reqs, hasApplied, isBookmarked,
                    StudentDashboardActivity.companyId(post, null));
        });
        container.addView(item);
    }

    @Override
    public void onDestroyView() {
        if (companyCall != null) companyCall.cancel();
        super.onDestroyView();
    }
}

