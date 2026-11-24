package com.example.internshipms;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.CompanyRepository;
import com.example.internshipms.session.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PostInternshipActivity extends AppCompatActivity {

    private static final String EXTRA_POST_JSON = "extra_post_json";

    private static final String[] CATEGORIES = {
            "IT Support & Helpdesk",
            "Web Development",
            "Mobile Development",
            "Java / Spring Boot",
            "Backend Engineering",
            "Frontend Engineering",
            "Full-Stack Development",
            "Data Science & Analytics",
            "Cybersecurity",
            "DevOps & Cloud",
            "UI/UX Design",
            "Network Engineering",
            "Database Administration",
            "Quality Assurance",
            "Business Analysis",
            "General IT"
    };

    private static final String[] OPP_TYPE_VALUES = {
            "internship", "full_time", "part_time", "contract"
    };

    private static final String[] OPP_TYPE_LABELS = {
            "🎓 Internship (Student Practicum)",
            "💼 Full-Time Job (Permanent Role)",
            "🕒 Part-Time Job (Flexible Hours)",
            "📄 Contract Job (Project Based)"
    };

    private static final String[] EXP_LEVEL_VALUES = {
            "no_experience", "entry_level", "junior", "mid_level"
    };

    private static final String[] EXP_LEVEL_LABELS = {
            "🌱 No Experience (Any Student / Beginner)",
            "🎓 Entry Level (0 - 1 yr)",
            "⚡ Junior (1 - 2 yrs)",
            "🚀 Mid-Level (3+ yrs)"
    };

    public static void launch(Context context) {
        Intent intent = new Intent(context, PostInternshipActivity.class);
        context.startActivity(intent);
    }

    public static void launchEdit(Context context, @NonNull JsonObject post) {
        Intent intent = new Intent(context, PostInternshipActivity.class);
        intent.putExtra(EXTRA_POST_JSON, post.toString());
        context.startActivity(intent);
    }

    private CompanyRepository repo;
    private JsonObject editingPost;
    private String companyHqAddress = null;

    private TextView tvHeaderTitle;
    private TextView btnSaveDraft;
    private TextView tvSelectedOppType;
    private EditText etJobTitle;
    private TextView tvSelectedCategory;
    private EditText etLocation;
    private MaterialCardView cardModeOnSite, cardModeRemote, cardModeHybrid;
    private TextView tvModeOnSite, tvModeRemote, tvModeHybrid;
    private TextView tvSlotsCount;
    private TextView tvDeadlineDisplay;
    private View boxDurationSection;
    private View boxExperienceLevelSection;
    private TextView tvSelectedExpLevel;
    private MaterialCardView cardDur3Months, cardDur6Months, cardDurFlexible;
    private TextView tvDur3Months, tvDur6Months, tvDurFlexible;
    private EditText etStipend;
    private EditText etSalaryMax;
    private MaterialSwitch switchDiscloseStipend;
    private EditText etDescription;
    private LinearLayout containerResponsibilities;
    private LinearLayout containerQualifications;
    private MaterialButton btnSubmitApproval;

    private String selectedOpportunityType = "internship";
    private String selectedCategory = "General IT";
    private String selectedWorkMode = "on_site";
    private String selectedExperienceLevel = "entry_level";
    private int openSlots = 1;
    private int durationWeeks = 12; // 3 Months = 12w, 6 Months = 24w, Flexible = 16w
    private LocalDate selectedDeadline = LocalDate.now().plusMonths(1);

    private final List<String> responsibilities = new ArrayList<>();
    private final List<String> qualifications = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = SessionManager.get(this);
        if (!session.isLoggedIn()) {
            Navigator.openLogin(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_post_internship);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repo = new CompanyRepository(this);

        // Ensure default deadline is in the future
        if (!selectedDeadline.isAfter(LocalDate.now())) {
            selectedDeadline = LocalDate.now().plusDays(30);
        }

        // ── Bind Views ───────────────────────────────────────────────────────
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        tvSelectedOppType = findViewById(R.id.tvSelectedOppType);
        etJobTitle = findViewById(R.id.etJobTitle);
        tvSelectedCategory = findViewById(R.id.tvSelectedCategory);
        etLocation = findViewById(R.id.etLocation);
        cardModeOnSite = findViewById(R.id.cardModeOnSite);
        cardModeRemote = findViewById(R.id.cardModeRemote);
        cardModeHybrid = findViewById(R.id.cardModeHybrid);
        tvModeOnSite = findViewById(R.id.tvModeOnSite);
        tvModeRemote = findViewById(R.id.tvModeRemote);
        tvModeHybrid = findViewById(R.id.tvModeHybrid);
        tvSlotsCount = findViewById(R.id.tvSlotsCount);
        tvDeadlineDisplay = findViewById(R.id.tvDeadlineDisplay);
        boxDurationSection = findViewById(R.id.boxDurationSection);
        boxExperienceLevelSection = findViewById(R.id.boxExperienceLevelSection);
        tvSelectedExpLevel = findViewById(R.id.tvSelectedExpLevel);
        cardDur3Months = findViewById(R.id.cardDur3Months);
        cardDur6Months = findViewById(R.id.cardDur6Months);
        cardDurFlexible = findViewById(R.id.cardDurFlexible);
        tvDur3Months = findViewById(R.id.tvDur3Months);
        tvDur6Months = findViewById(R.id.tvDur6Months);
        tvDurFlexible = findViewById(R.id.tvDurFlexible);
        etStipend = findViewById(R.id.etStipend);
        etSalaryMax = findViewById(R.id.etSalaryMax);
        switchDiscloseStipend = findViewById(R.id.switchDiscloseStipend);
        etDescription = findViewById(R.id.etDescription);
        containerResponsibilities = findViewById(R.id.containerResponsibilities);
        containerQualifications = findViewById(R.id.containerQualifications);
        btnSubmitApproval = findViewById(R.id.btnSubmitApproval);

        tvSelectedCategory.setText(selectedCategory);

        // ── Check if Editing ─────────────────────────────────────────────────
        String rawJson = getIntent().getStringExtra(EXTRA_POST_JSON);
        if (rawJson != null && !rawJson.isEmpty()) {
            try {
                editingPost = JsonParser.parseString(rawJson).getAsJsonObject();
            } catch (Exception ignored) {
            }
        }

        if (editingPost != null) {
            populateFromExistingPost(editingPost);
        } else {
            // Keep empty, waiting for company input
            responsibilities.clear();
            qualifications.clear();
            selectOpportunityType("internship");
            selectWorkMode("on_site");
        }

        updateDeadlineLabel();
        renderBullets(containerResponsibilities, responsibilities, "Responsibility");
        renderBullets(containerQualifications, qualifications, "Qualification");

        // ── Wire Interactions ────────────────────────────────────────────────
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        View cardOppTypePicker = findViewById(R.id.cardOppTypePicker);
        if (cardOppTypePicker != null) {
            cardOppTypePicker.setOnClickListener(v -> showOppTypePicker());
        }

        findViewById(R.id.cardCategoryPicker).setOnClickListener(v -> showCategoryPicker());

        findViewById(R.id.tvUseCompanyHq).setOnClickListener(v -> useCompanyHqAddress());

        if (cardModeOnSite != null) cardModeOnSite.setOnClickListener(v -> selectWorkMode("on_site"));
        if (cardModeRemote != null) cardModeRemote.setOnClickListener(v -> selectWorkMode("remote"));
        if (cardModeHybrid != null) cardModeHybrid.setOnClickListener(v -> selectWorkMode("hybrid"));

        View cardExpLevelPicker = findViewById(R.id.cardExpLevelPicker);
        if (cardExpLevelPicker != null) {
            cardExpLevelPicker.setOnClickListener(v -> showExpLevelPicker());
        }

        findViewById(R.id.btnSlotsMinus).setOnClickListener(v -> {
            if (openSlots > 1) {
                openSlots--;
                tvSlotsCount.setText(String.valueOf(openSlots));
            }
        });

        findViewById(R.id.btnSlotsPlus).setOnClickListener(v -> {
            if (openSlots < 100) {
                openSlots++;
                tvSlotsCount.setText(String.valueOf(openSlots));
            }
        });

        findViewById(R.id.cardDeadlinePicker).setOnClickListener(v -> showDeadlinePicker());

        cardDur3Months.setOnClickListener(v -> selectDuration(12));
        cardDur6Months.setOnClickListener(v -> selectDuration(24));
        cardDurFlexible.setOnClickListener(v -> selectDuration(16));

        findViewById(R.id.tvAddResponsibility).setOnClickListener(v ->
                showAddBulletDialog("Add Responsibility", responsibilities, containerResponsibilities));

        findViewById(R.id.tvAddQualification).setOnClickListener(v ->
                showAddBulletDialog("Add Qualification", qualifications, containerQualifications));

        btnSaveDraft.setOnClickListener(v -> submitListing(true));
        btnSubmitApproval.setOnClickListener(v -> submitListing(false));

        // Fetch company profile in background so "+ Use Company HQ Address" has real data
        preloadCompanyProfile();
    }

    // ── Pre-populate when editing ────────────────────────────────────────────

    private void populateFromExistingPost(@NonNull JsonObject post) {
        String existingStatus = Json.str(post, "", "status").toLowerCase(Locale.ROOT);
        tvHeaderTitle.setText("Edit Opportunity");
        if ("rejected".equals(existingStatus) || "draft".equals(existingStatus)) {
            btnSubmitApproval.setText("Save & Submit for Approval");
            btnSaveDraft.setVisibility(View.VISIBLE);
        } else {
            btnSubmitApproval.setText("Save Changes");
            btnSaveDraft.setVisibility(View.GONE);
        }

        etJobTitle.setText(Json.str(post, "", "title"));
        selectedCategory = Json.str(post, "General IT", "category");
        tvSelectedCategory.setText(selectedCategory);
        etLocation.setText(Json.str(post, "Phnom Penh, Cambodia", "location"));

        String oppType = Json.str(post, "internship", "opportunity_type");
        selectOpportunityType(oppType);

        String workMode = Json.str(post, "on_site", "type", "work_mode");
        selectWorkMode(workMode);

        String expLevel = Json.str(post, "entry_level", "experience_level");
        selectExperienceLevel(expLevel);

        openSlots = Math.max(1, Json.integer(post, 3, "slots"));
        tvSlotsCount.setText(String.valueOf(openSlots));

        durationWeeks = Math.max(1, Json.integer(post, 12, "duration_weeks"));
        if (durationWeeks >= 20) {
            selectDuration(24);
        } else if (durationWeeks != 12) {
            selectDuration(16);
        } else {
            selectDuration(12);
        }

        String rawDeadline = Json.str(post, null, "deadline");
        if (rawDeadline != null && rawDeadline.length() >= 10) {
            try {
                selectedDeadline = LocalDate.parse(rawDeadline.substring(0, 10));
            } catch (Exception ignored) {
            }
        }

        String rawStipend = Json.str(post, "", "stipend").replaceAll("[^0-9.]", "");
        if (rawStipend.endsWith(".00")) {
            rawStipend = rawStipend.substring(0, rawStipend.length() - 3);
        }
        etStipend.setText(rawStipend);

        if (etSalaryMax != null) {
            String rawMax = Json.str(post, "", "salary_max").replaceAll("[^0-9.]", "");
            if (rawMax.endsWith(".00")) {
                rawMax = rawMax.substring(0, rawMax.length() - 3);
            }
            etSalaryMax.setText(rawMax);
        }

        if (switchDiscloseStipend != null) {
            boolean disclosed = !post.has("is_stipend_disclosed")
                    || post.get("is_stipend_disclosed").isJsonNull()
                    || (post.get("is_stipend_disclosed").isJsonPrimitive()
                    && ("1".equals(post.get("is_stipend_disclosed").getAsString())
                    || post.get("is_stipend_disclosed").getAsBoolean()));
            switchDiscloseStipend.setChecked(disclosed);
        }

        etDescription.setText(Json.str(post, "", "description"));

        parseBulletLines(Json.str(post, "", "responsibilities"), responsibilities);
        parseBulletLines(Json.str(post, "", "requirements"), qualifications);
    }

    private static void parseBulletLines(String text, List<String> target) {
        target.clear();
        if (text == null || text.trim().isEmpty()) return;
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line.replaceFirst("^[•\\-*\\d.]+\\s*", "").trim();
            if (!cleaned.isEmpty()) {
                target.add(cleaned);
            }
        }
    }

    // ── Category, HQ Address, Deadline, Duration ──────────────────────────────

    private void showOppTypePicker() {
        int checked = 0;
        for (int i = 0; i < OPP_TYPE_VALUES.length; i++) {
            if (OPP_TYPE_VALUES[i].equalsIgnoreCase(selectedOpportunityType)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Listing Type")
                .setSingleChoiceItems(OPP_TYPE_LABELS, checked, (dialog, which) -> {
                    selectOpportunityType(OPP_TYPE_VALUES[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void selectOpportunityType(String oppType) {
        if (oppType == null || oppType.isEmpty()) oppType = "internship";
        selectedOpportunityType = oppType.toLowerCase(Locale.ROOT);
        int idx = 0;
        for (int i = 0; i < OPP_TYPE_VALUES.length; i++) {
            if (OPP_TYPE_VALUES[i].equals(selectedOpportunityType)) {
                idx = i;
                break;
            }
        }
        if (tvSelectedOppType != null) {
            tvSelectedOppType.setText(OPP_TYPE_LABELS[idx]);
        }
        boolean isInternship = "internship".equals(selectedOpportunityType);
        if (boxDurationSection != null) {
            boxDurationSection.setVisibility(isInternship ? View.VISIBLE : View.GONE);
        }
        if (boxExperienceLevelSection != null) {
            boxExperienceLevelSection.setVisibility(isInternship ? View.GONE : View.VISIBLE);
        }
    }

    private void selectWorkMode(String mode) {
        if (mode == null || mode.isEmpty()) mode = "on_site";
        selectedWorkMode = mode.toLowerCase(Locale.ROOT);
        if (cardModeOnSite != null && tvModeOnSite != null) {
            styleDurationCard(cardModeOnSite, tvModeOnSite, "on_site".equals(selectedWorkMode));
        }
        if (cardModeRemote != null && tvModeRemote != null) {
            styleDurationCard(cardModeRemote, tvModeRemote, "remote".equals(selectedWorkMode));
        }
        if (cardModeHybrid != null && tvModeHybrid != null) {
            styleDurationCard(cardModeHybrid, tvModeHybrid, "hybrid".equals(selectedWorkMode));
        }
    }

    private void showExpLevelPicker() {
        int checked = 1;
        for (int i = 0; i < EXP_LEVEL_VALUES.length; i++) {
            if (EXP_LEVEL_VALUES[i].equalsIgnoreCase(selectedExperienceLevel)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Experience Level")
                .setSingleChoiceItems(EXP_LEVEL_LABELS, checked, (dialog, which) -> {
                    selectExperienceLevel(EXP_LEVEL_VALUES[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void selectExperienceLevel(String level) {
        if (level == null || level.isEmpty()) level = "entry_level";
        selectedExperienceLevel = level.toLowerCase(Locale.ROOT);
        int idx = 1;
        for (int i = 0; i < EXP_LEVEL_VALUES.length; i++) {
            if (EXP_LEVEL_VALUES[i].equals(selectedExperienceLevel)) {
                idx = i;
                break;
            }
        }
        if (tvSelectedExpLevel != null) {
            tvSelectedExpLevel.setText(EXP_LEVEL_LABELS[idx]);
        }
    }

    private void showCategoryPicker() {
        int checked = 0;
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equalsIgnoreCase(selectedCategory)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Position Category")
                .setSingleChoiceItems(CATEGORIES, checked, (dialog, which) -> {
                    selectedCategory = CATEGORIES[which];
                    tvSelectedCategory.setText(selectedCategory);
                    dialog.dismiss();
                })
                .show();
    }

    private void preloadCompanyProfile() {
        repo.profile(new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                JsonObject data = Json.data(body);
                String addr = Json.str(data, null, "address", "location");
                if (addr != null && !addr.isEmpty()) {
                    companyHqAddress = addr;
                }
            }

            @Override
            public void onError(@NonNull ApiError error) {
            }
        });
    }

    private void useCompanyHqAddress() {
        String addr = companyHqAddress != null && !companyHqAddress.isEmpty()
                ? companyHqAddress : "Phnom Penh, Cambodia";
        etLocation.setText(addr);
        etLocation.setSelection(addr.length());
        toast("Applied company HQ address");
    }

    private void showDeadlinePicker() {
        DatePickerDialog dlg = new DatePickerDialog(
                this,
                (picker, y, m, d) -> {
                    selectedDeadline = LocalDate.of(y, m + 1, d);
                    updateDeadlineLabel();
                },
                selectedDeadline.getYear(),
                selectedDeadline.getMonthValue() - 1,
                selectedDeadline.getDayOfMonth());
        dlg.getDatePicker().setMinDate(System.currentTimeMillis() + 86_400_000L);
        dlg.show();
    }

    private void updateDeadlineLabel() {
        tvDeadlineDisplay.setText(selectedDeadline.format(
                DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)));
    }

    private void selectDuration(int weeks) {
        durationWeeks = weeks;
        styleDurationCard(cardDur3Months, tvDur3Months, weeks == 12);
        styleDurationCard(cardDur6Months, tvDur6Months, weeks == 24);
        styleDurationCard(cardDurFlexible, tvDurFlexible, weeks == 16);
    }

    private void styleDurationCard(MaterialCardView card, TextView tv, boolean selected) {
        int primaryGreen = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
        int onSurface = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface);
        if (selected) {
            card.setCardBackgroundColor(primaryGreen);
            card.setStrokeColor(ColorStateList.valueOf(primaryGreen));
            tv.setTextColor(Color.WHITE);
        } else {
            card.setCardBackgroundColor(Color.parseColor("#FFF8FAFC"));
            card.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.stroke_soft)));
            tv.setTextColor(onSurface);
        }
    }

    // ── Dynamic Responsibilities & Qualifications Lists ──────────────────────

    private void renderBullets(LinearLayout container, List<String> items, String label) {
        container.removeAllViews();
        if (items.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No " + label.toLowerCase(Locale.ROOT) + "s added yet. Tap '+ Add " + label + "' above.");
            emptyView.setTextColor(ContextCompat.getColor(this, R.color.nav_unselected));
            emptyView.setTextSize(12f);
            int padV = (int) (6 * getResources().getDisplayMetrics().density);
            emptyView.setPadding(0, padV, 0, padV);
            container.addView(emptyView);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            String text = items.get(i);
            View row = inflater.inflate(R.layout.item_post_bullet, container, false);
            TextView tvText = row.findViewById(R.id.tvBulletText);
            View btnRemove = row.findViewById(R.id.btnRemoveBullet);

            tvText.setText(text);
            btnRemove.setOnClickListener(v -> {
                items.remove(index);
                renderBullets(container, items, label);
            });
            row.setOnClickListener(v -> showEditBulletDialog("Edit " + label, items, index, container, label));
            container.addView(row);
        }
    }

    private void showAddBulletDialog(String title, List<String> items, LinearLayout container) {
        EditText input = createDialogInput("");
        FrameLayout wrapper = wrapDialogInput(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(wrapper)
                .setPositiveButton("Add", (d, w) -> {
                    String val = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        items.add(val);
                        renderBullets(container, items, title.replace("Add ", ""));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditBulletDialog(String title, List<String> items, int index,
                                      LinearLayout container, String label) {
        EditText input = createDialogInput(items.get(index));
        FrameLayout wrapper = wrapDialogInput(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(wrapper)
                .setPositiveButton("Save", (d, w) -> {
                    String val = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        items.set(index, val);
                        renderBullets(container, items, label);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText createDialogInput(String initialText) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        input.setText(initialText);
        input.setSelection(initialText.length());
        input.setHint("Enter details...");
        return input;
    }

    private FrameLayout wrapDialogInput(EditText input) {
        FrameLayout container = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, pad / 2, pad, 0);
        input.setLayoutParams(lp);
        container.addView(input);
        return container;
    }

    // ── Submit to CompanyApiController@storePost / updatePost ────────────────

    private void submitListing(boolean asDraft) {
        String title = textOf(etJobTitle);
        String location = textOf(etLocation);
        String stipendRaw = textOf(etStipend).replaceAll("[^0-9.]", "");
        String description = textOf(etDescription);

        if (title.isEmpty()) {
            etJobTitle.setError("Job title is required");
            etJobTitle.requestFocus();
            return;
        }
        if (!asDraft) {
            if (location.isEmpty()) {
                etLocation.setError("Work location is required");
                etLocation.requestFocus();
                return;
            }
            if (description.length() < 10) {
                etDescription.setError("Please enter a job description");
                etDescription.requestFocus();
                return;
            }
            if (qualifications.isEmpty()) {
                toast("Please add at least one qualification");
                return;
            }
        }

        StringBuilder respBuilder = new StringBuilder();
        for (int i = 0; i < responsibilities.size(); i++) {
            if (i > 0) respBuilder.append("\n");
            respBuilder.append("• ").append(responsibilities.get(i));
        }

        StringBuilder reqBuilder = new StringBuilder();
        for (int i = 0; i < qualifications.size(); i++) {
            if (i > 0) reqBuilder.append("\n");
            reqBuilder.append("• ").append(qualifications.get(i));
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("title", title);
        payload.addProperty("category", selectedCategory);
        payload.addProperty("opportunity_type", selectedOpportunityType);
        payload.addProperty("type", selectedWorkMode);
        payload.addProperty("location", location);
        if ("internship".equals(selectedOpportunityType)) {
            payload.addProperty("duration_weeks", durationWeeks);
        } else {
            payload.addProperty("experience_level", selectedExperienceLevel);
        }
        payload.addProperty("slots", openSlots);
        payload.addProperty("deadline", selectedDeadline.toString());
        payload.addProperty("description", description);
        payload.addProperty("responsibilities", respBuilder.toString());
        payload.addProperty("requirements", reqBuilder.toString());

        boolean disclosed = switchDiscloseStipend == null || switchDiscloseStipend.isChecked();
        payload.addProperty("is_stipend_disclosed", disclosed);

        if (!stipendRaw.isEmpty()) {
            try {
                payload.addProperty("stipend", Double.parseDouble(stipendRaw));
            } catch (Exception ignored) {
            }
        }

        String salaryMaxRaw = etSalaryMax != null ? textOf(etSalaryMax).replaceAll("[^0-9.]", "") : "";
        if (!salaryMaxRaw.isEmpty()) {
            try {
                payload.addProperty("salary_max", Double.parseDouble(salaryMaxRaw));
            } catch (Exception ignored) {
            }
        }

        if (editingPost == null) {
            payload.addProperty("status", asDraft ? "draft" : "pending_approval");
        } else if (asDraft) {
            payload.addProperty("status", "draft");
        }

        btnSubmitApproval.setEnabled(false);
        btnSaveDraft.setEnabled(false);
        btnSubmitApproval.setText(editingPost != null
                ? "Saving Changes…"
                : (asDraft ? "Saving Draft…" : "Submitting for Review…"));

        CompanyRepository.JsonResult callback = new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                String msg = editingPost != null
                        ? "Internship listing updated!"
                        : (asDraft ? "Saved as draft!" : "Submitted for admin approval! 🎉");
                toast(msg);
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                btnSubmitApproval.setEnabled(true);
                btnSaveDraft.setEnabled(true);
                btnSubmitApproval.setText(editingPost != null
                        ? "Save Changes" : "Submit for Admin Approval");
                toast(error.message);
            }
        };

        if (editingPost != null && editingPost.has("id")) {
            repo.updatePost(editingPost.get("id").getAsInt(), payload, callback);
        } else {
            repo.storePost(payload, callback);
        }
    }

    private static String textOf(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

