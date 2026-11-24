package com.example.internshipms;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompanyReportsActivity extends AppCompatActivity {

    public static void launch(Context context) {
        context.startActivity(new Intent(context, CompanyReportsActivity.class));
    }

    private CompanyRepository repo;
    private PullRefreshScrollView scrollRefresh;
    private LinearLayout containerReportItems;
    private LinearLayout loadingContainer;
    private LinearLayout emptyContainer;

    private TextView tvReportTotalApps;
    private TextView tvReportShortlisted;
    private TextView tvReportAccepted;
    private TextView tvReportHireRate;

    private EditText etSearchReports;
    private MaterialButton btnFilterRole;
    private MaterialButton btnFilterStatus;

    private final List<JsonObject> allReportApplications = new ArrayList<>();
    private final Map<String, String> rolesMap = new LinkedHashMap<>();

    private String selectedPostId = null;
    private String selectedStatus = null;
    private String searchQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_company_reports);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repo = new CompanyRepository(this);

        scrollRefresh = findViewById(R.id.scrollRefresh);
        containerReportItems = findViewById(R.id.containerReportItems);
        loadingContainer = findViewById(R.id.loadingContainer);
        emptyContainer = findViewById(R.id.emptyContainer);

        tvReportTotalApps = findViewById(R.id.tvReportTotalApps);
        tvReportShortlisted = findViewById(R.id.tvReportShortlisted);
        tvReportAccepted = findViewById(R.id.tvReportAccepted);
        tvReportHireRate = findViewById(R.id.tvReportHireRate);

        etSearchReports = findViewById(R.id.etSearchReports);
        btnFilterRole = findViewById(R.id.btnFilterRole);
        btnFilterStatus = findViewById(R.id.btnFilterStatus);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNotifications).setOnClickListener(v -> NotificationsActivity.launch(this));

        btnFilterRole.setOnClickListener(v -> showRoleFilterDialog());
        btnFilterStatus.setOnClickListener(v -> showStatusFilterDialog());

        etSearchReports.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim();
                renderReportItems();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (scrollRefresh != null) {
            scrollRefresh.setOnRefreshListener(this::loadReports);
        }

        loadReports();
    }

    private void loadReports() {
        if (loadingContainer != null) loadingContainer.setVisibility(View.VISIBLE);
        if (emptyContainer != null) emptyContainer.setVisibility(View.GONE);

        repo.hiringReports(selectedPostId, selectedStatus, null, null, null, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                JsonObject data = Json.data(body);

                // Parse KPIs
                JsonObject kpis = Json.obj(data, "kpis", "summary");
                int totalApps = Json.integer(kpis != null ? kpis : data, 0, "total_applications", "total");
                int shortlisted = Json.integer(kpis != null ? kpis : data, 0, "total_shortlisted", "shortlisted");
                int accepted = Json.integer(kpis != null ? kpis : data, 0, "total_accepted", "accepted", "hired");

                String hireRateStr = Json.str(kpis != null ? kpis : data, null, "hire_rate", "conversion_rate");
                if (hireRateStr == null) {
                    float rate = totalApps > 0 ? (accepted * 100f / totalApps) : 0f;
                    hireRateStr = String.format(Locale.US, "%.1f%%", rate);
                } else if (!hireRateStr.endsWith("%")) {
                    hireRateStr += "%";
                }

                tvReportTotalApps.setText(String.valueOf(totalApps));
                tvReportShortlisted.setText(String.valueOf(shortlisted));
                tvReportAccepted.setText(String.valueOf(accepted));
                tvReportHireRate.setText(hireRateStr);

                // Parse Applications
                JsonArray arr = Json.list(data, "applications", "report_data", "data");
                allReportApplications.clear();
                rolesMap.clear();
                rolesMap.put("", "All Roles");

                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        JsonObject app = el.getAsJsonObject();
                        allReportApplications.add(app);
                        JsonObject post = Json.obj(app, "internship_post", "post");
                        int pid = post != null ? Json.integer(post, 0, "id") : Json.integer(app, 0, "internship_post_id");
                        String title = post != null ? Json.str(post, null, "title") : Json.str(app, null, "post_title");
                        if (pid > 0 && title != null && !title.isEmpty()) {
                            rolesMap.put(String.valueOf(pid), title);
                        }
                    }
                }

                // If empty summary fallback, compute from applications array
                if (totalApps == 0 && !allReportApplications.isEmpty()) {
                    totalApps = allReportApplications.size();
                    for (JsonObject a : allReportApplications) {
                        String st = Json.str(a, "", "status").toLowerCase(Locale.ROOT);
                        if ("shortlisted".equals(st)) shortlisted++;
                        if ("accepted".equals(st)) accepted++;
                    }
                    float rate = totalApps > 0 ? (accepted * 100f / totalApps) : 0f;
                    tvReportTotalApps.setText(String.valueOf(totalApps));
                    tvReportShortlisted.setText(String.valueOf(shortlisted));
                    tvReportAccepted.setText(String.valueOf(accepted));
                    tvReportHireRate.setText(String.format(Locale.US, "%.1f%%", rate));
                }

                renderReportItems();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
                if (scrollRefresh != null) scrollRefresh.setRefreshing(false);
                Toast.makeText(CompanyReportsActivity.this, error.message, Toast.LENGTH_SHORT).show();
                renderReportItems();
            }
        });
    }

    private void showRoleFilterDialog() {
        if (rolesMap.isEmpty()) {
            Toast.makeText(this, "No vacancies found in report data", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<String> ids = new ArrayList<>(rolesMap.keySet());
        final String[] labels = new String[ids.size()];
        int checked = 0;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            labels[i] = rolesMap.get(id);
            if (id.equals(selectedPostId != null ? selectedPostId : "")) {
                checked = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Filter by Role")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String chosenId = ids.get(which);
                    selectedPostId = chosenId.isEmpty() ? null : chosenId;
                    btnFilterRole.setText(labels[which] + " ▾");
                    loadReports();
                    dialog.dismiss();
                })
                .show();
    }

    private void showStatusFilterDialog() {
        final String[] statuses = {"All Statuses", "Pending", "Review", "Shortlisted", "Accepted", "Declined"};
        final String[] values = {null, "pending", "review", "shortlisted", "accepted", "declined"};

        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if ((values[i] == null && selectedStatus == null) || (values[i] != null && values[i].equals(selectedStatus))) {
                checked = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Filter by Status")
                .setSingleChoiceItems(statuses, checked, (dialog, which) -> {
                    selectedStatus = values[which];
                    btnFilterStatus.setText(statuses[which] + " ▾");
                    loadReports();
                    dialog.dismiss();
                })
                .show();
    }

    private void renderReportItems() {
        containerReportItems.removeAllViews();
        List<JsonObject> filtered = new ArrayList<>();
        String q = searchQuery.toLowerCase(Locale.ROOT);

        for (JsonObject app : allReportApplications) {
            String st = Json.str(app, "pending", "status").toLowerCase(Locale.ROOT);
            if (selectedStatus != null && !st.contains(selectedStatus)) {
                continue;
            }

            JsonObject student = Json.obj(app, "student_profile", "student");
            JsonObject user = student != null ? Json.obj(student, "user") : null;
            String name = user != null ? Json.str(user, "", "name") : Json.str(student, "", "name");
            JsonObject post = Json.obj(app, "internship_post", "post");
            String role = post != null ? Json.str(post, "", "title") : "";

            if (!q.isEmpty()) {
                if (!name.toLowerCase(Locale.ROOT).contains(q) && !role.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
            }
            filtered.add(app);
        }

        if (filtered.isEmpty()) {
            if (emptyContainer != null) emptyContainer.setVisibility(View.VISIBLE);
        } else {
            if (emptyContainer != null) emptyContainer.setVisibility(View.GONE);
            for (JsonObject app : filtered) {
                addReportRow(app);
            }
        }
    }

    private void addReportRow(JsonObject app) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_company_report_row, containerReportItems, false);

        JsonObject student = Json.obj(app, "student_profile", "student");
        JsonObject user = student != null ? Json.obj(student, "user") : null;
        JsonObject post = Json.obj(app, "internship_post", "post");

        String name = user != null ? Json.str(user, null, "name") : null;
        if (name == null) name = Json.str(student, null, "full_name", "name");
        if (name == null) name = "Candidate";

        String role = post != null ? Json.str(post, "Internship Role", "title") : "Internship Role";
        String uni = student != null ? Json.str(student, "USEA", "university", "school") : "USEA";
        String status = Json.str(app, "pending", "status");
        String appliedAt = Json.str(app, "Recently", "applied_at", "created_at");

        TextView tvInitials = row.findViewById(R.id.tvReportInitials);
        ImageView ivAvatar = row.findViewById(R.id.ivReportAvatar);
        TextView tvName = row.findViewById(R.id.tvReportCandidateName);
        TextView tvRoleAndUni = row.findViewById(R.id.tvReportRoleAndUni);
        TextView tvDate = row.findViewById(R.id.tvReportAppliedDate);
        TextView tvStatusBadge = row.findViewById(R.id.tvReportStatusBadge);

        tvName.setText(name);
        tvInitials.setText(initials(name));
        AvatarLoader.bind(AvatarLoader.extractUrl(user, student), ivAvatar, tvInitials);

        tvRoleAndUni.setText(role + " • " + uni);
        tvDate.setText("Applied: " + (appliedAt.length() >= 10 ? appliedAt.substring(0, 10) : appliedAt));

        applyStatusBadge(tvStatusBadge, status);

        int studentId = student != null ? Json.integer(student, 0, "id", "user_id") : Json.integer(app, 0, "student_id");
        final int finalStudentId = studentId;
        final String finalName = name;
        row.setOnClickListener(v -> {
            if (finalStudentId > 0) {
                CompanyStudentProfileActivity.launch(this, finalStudentId, finalName);
            }
        });

        containerReportItems.addView(row);
    }

    private void applyStatusBadge(TextView tv, String status) {
        String s = status == null ? "pending" : status.toLowerCase(Locale.ROOT);
        tv.setText(s.toUpperCase(Locale.ROOT));
        if ("accepted".equals(s)) {
            tv.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_green_bg)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.tint_green));
        } else if ("rejected".equals(s) || "declined".equals(s)) {
            tv.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_red_bg)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.tint_red));
        } else if ("shortlisted".equals(s)) {
            tv.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_indigo_bg)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.tint_indigo));
        } else {
            tv.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tint_amber_bg)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.tint_amber));
        }
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
