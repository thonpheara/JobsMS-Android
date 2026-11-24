package com.example.internshipms;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.CompanyRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDate;
import java.util.Locale;

public class PostCohortBottomSheet extends BottomSheetDialogFragment {

    public interface OnPostSavedListener {
        void onPostSaved(@NonNull JsonObject post);
    }

    private static final String ARG_POST_JSON = "arg_post_json";

    private OnPostSavedListener listener;
    private JsonObject editingPost;

    public static void show(FragmentManager fm) {
        show(fm, (OnPostSavedListener) null);
    }

    public static void show(FragmentManager fm, @Nullable OnPostSavedListener listener) {
        PostCohortBottomSheet sheet = new PostCohortBottomSheet();
        sheet.listener = listener;
        sheet.show(fm, "post_cohort_sheet");
    }

    public static void showEdit(FragmentManager fm,
                                @NonNull JsonObject post,
                                @Nullable OnPostSavedListener listener) {
        PostCohortBottomSheet sheet = new PostCohortBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_POST_JSON, post.toString());
        sheet.setArguments(args);
        sheet.listener = listener;
        sheet.show(fm, "edit_cohort_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_post_cohort, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null && getArguments().containsKey(ARG_POST_JSON)) {
            try {
                editingPost = JsonParser.parseString(
                        getArguments().getString(ARG_POST_JSON, "{}")).getAsJsonObject();
            } catch (Exception ignored) {
            }
        }

        TextView tvTitle = view.findViewById(R.id.tvSheetTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSheetSubtitle);

        TextInputLayout tilRole = view.findViewById(R.id.tilRoleTitle);
        TextInputLayout tilCategory = view.findViewById(R.id.tilCategory);
        TextInputLayout tilLocation = view.findViewById(R.id.tilLocation);
        TextInputLayout tilDeadline = view.findViewById(R.id.tilDeadline);
        TextInputLayout tilDescription = view.findViewById(R.id.tilDescription);
        TextInputLayout tilRequirements = view.findViewById(R.id.tilRequirements);

        TextInputEditText etRole = view.findViewById(R.id.etRoleTitle);
        TextInputEditText etCategory = view.findViewById(R.id.etCategory);
        TextInputEditText etLocation = view.findViewById(R.id.etLocation);
        TextInputEditText etStipend = view.findViewById(R.id.etStipend);
        TextInputEditText etSlots = view.findViewById(R.id.etSlots);
        TextInputEditText etDuration = view.findViewById(R.id.etDuration);
        TextInputEditText etDeadline = view.findViewById(R.id.etDeadline);
        TextInputEditText etDescription = view.findViewById(R.id.etDescription);
        TextInputEditText etRequirements = view.findViewById(R.id.etRequirements);

        ChipGroup chipGroupType = view.findViewById(R.id.chipGroupType);
        MaterialCheckBox cbDisclose = view.findViewById(R.id.cbDiscloseStipend);
        MaterialButton btnDraftOrCancel = view.findViewById(R.id.btnCancel);
        MaterialButton btnPublish = view.findViewById(R.id.btnPublish);

        // Default deadline: 30 days ahead
        LocalDate defaultDeadline = LocalDate.now().plusDays(30);
        etDeadline.setText(defaultDeadline.toString());

        etDeadline.setOnClickListener(v -> {
            LocalDate current = defaultDeadline;
            try {
                String txt = textOf(etDeadline);
                if (!txt.isEmpty()) current = LocalDate.parse(txt.substring(0, Math.min(10, txt.length())));
            } catch (Exception ignored) {
            }
            DatePickerDialog dlg = new DatePickerDialog(
                    requireContext(),
                    (picker, y, m, d) -> etDeadline.setText(
                            String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                    current.getYear(),
                    current.getMonthValue() - 1,
                    current.getDayOfMonth());
            dlg.getDatePicker().setMinDate(System.currentTimeMillis() + 86_400_000L);
            dlg.show();
        });

        // Pre-populate if editing an existing listing
        if (editingPost != null) {
            tvTitle.setText("Edit Internship Listing");
            tvSubtitle.setText("Update cohort details and requirements");
            btnDraftOrCancel.setText("Cancel");
            btnPublish.setText("Save Changes");

            etRole.setText(Json.str(editingPost, "", "title"));
            etCategory.setText(Json.str(editingPost, "Software Engineering", "category"));
            etLocation.setText(Json.str(editingPost, "Phnom Penh", "location"));

            String stipend = Json.str(editingPost, "", "stipend");
            etStipend.setText(stipend.replaceAll("[^0-9.]", ""));
            etSlots.setText(String.valueOf(Json.integer(editingPost, 3, "slots")));
            etDuration.setText(String.valueOf(Json.integer(editingPost, 12, "duration_weeks")));

            String rawDeadline = Json.str(editingPost, defaultDeadline.toString(), "deadline");
            if (rawDeadline.length() >= 10) rawDeadline = rawDeadline.substring(0, 10);
            etDeadline.setText(rawDeadline);

            etDescription.setText(Json.str(editingPost, "", "description"));
            etRequirements.setText(Json.str(editingPost, "", "requirements"));

            boolean disclosed = !editingPost.has("is_stipend_disclosed")
                    || editingPost.get("is_stipend_disclosed").isJsonNull()
                    || (editingPost.get("is_stipend_disclosed").isJsonPrimitive()
                    && (editingPost.get("is_stipend_disclosed").getAsString().equals("1")
                    || editingPost.get("is_stipend_disclosed").getAsBoolean()));
            cbDisclose.setChecked(disclosed);

            String type = Json.str(editingPost, "on_site", "type");
            if ("remote".equalsIgnoreCase(type)) {
                chipGroupType.check(R.id.chipRemote);
            } else if ("hybrid".equalsIgnoreCase(type)) {
                chipGroupType.check(R.id.chipHybrid);
            } else {
                chipGroupType.check(R.id.chipOnSite);
            }
        }

        btnDraftOrCancel.setOnClickListener(v -> {
            if (editingPost != null) {
                dismiss();
            } else {
                submitForm(true, tilRole, tilCategory, tilLocation, tilDeadline,
                        tilDescription, tilRequirements,
                        etRole, etCategory, etLocation, etStipend, etSlots,
                        etDuration, etDeadline, etDescription, etRequirements,
                        chipGroupType, cbDisclose, btnDraftOrCancel, btnPublish);
            }
        });

        btnPublish.setOnClickListener(v ->
                submitForm(false, tilRole, tilCategory, tilLocation, tilDeadline,
                        tilDescription, tilRequirements,
                        etRole, etCategory, etLocation, etStipend, etSlots,
                        etDuration, etDeadline, etDescription, etRequirements,
                        chipGroupType, cbDisclose, btnDraftOrCancel, btnPublish));
    }

    private void submitForm(boolean asDraft,
                            TextInputLayout tilRole,
                            TextInputLayout tilCategory,
                            TextInputLayout tilLocation,
                            TextInputLayout tilDeadline,
                            TextInputLayout tilDescription,
                            TextInputLayout tilRequirements,
                            TextInputEditText etRole,
                            TextInputEditText etCategory,
                            TextInputEditText etLocation,
                            TextInputEditText etStipend,
                            TextInputEditText etSlots,
                            TextInputEditText etDuration,
                            TextInputEditText etDeadline,
                            TextInputEditText etDescription,
                            TextInputEditText etRequirements,
                            ChipGroup chipGroupType,
                            MaterialCheckBox cbDisclose,
                            MaterialButton btnSecondary,
                            MaterialButton btnPrimary) {
        tilRole.setError(null);
        tilCategory.setError(null);
        tilLocation.setError(null);
        tilDeadline.setError(null);
        tilDescription.setError(null);
        tilRequirements.setError(null);

        String title = textOf(etRole);
        String category = textOf(etCategory);
        String location = textOf(etLocation);
        String stipendStr = textOf(etStipend).replaceAll("[^0-9.]", "");
        String slotsStr = textOf(etSlots).replaceAll("[^0-9]", "");
        String durationStr = textOf(etDuration).replaceAll("[^0-9]", "");
        String deadline = textOf(etDeadline);
        String description = textOf(etDescription);
        String requirements = textOf(etRequirements);

        if (title.isEmpty()) {
            tilRole.setError("Please enter a role title");
            return;
        }
        if (category.isEmpty()) {
            tilCategory.setError("Required");
            return;
        }
        if (location.isEmpty()) {
            tilLocation.setError("Required");
            return;
        }
        if (deadline.isEmpty()) {
            tilDeadline.setError("Select a deadline");
            return;
        }
        if (description.isEmpty()) {
            description = "Join our engineering & product team as a " + title
                    + " in " + location + ". Gain hands-on mentorship and real project experience.";
        } else if (description.length() < 30) {
            description = description + " — Hands-on mentorship and real project experience included.";
        }
        if (requirements.isEmpty()) {
            requirements = "Motivated university student with foundational knowledge in " + category + ".";
        }

        int slots = 3;
        try {
            if (!slotsStr.isEmpty()) slots = Math.max(1, Integer.parseInt(slotsStr));
        } catch (Exception ignored) {
        }

        int durationWeeks = 12;
        try {
            if (!durationStr.isEmpty()) durationWeeks = Math.max(1, Integer.parseInt(durationStr));
        } catch (Exception ignored) {
        }

        String type = "on_site";
        int checkedId = chipGroupType.getCheckedChipId();
        if (checkedId == R.id.chipRemote) type = "remote";
        else if (checkedId == R.id.chipHybrid) type = "hybrid";

        JsonObject payload = new JsonObject();
        payload.addProperty("title", title);
        payload.addProperty("category", category);
        payload.addProperty("type", type);
        payload.addProperty("location", location);
        payload.addProperty("duration_weeks", durationWeeks);
        payload.addProperty("slots", slots);
        payload.addProperty("deadline", deadline);
        payload.addProperty("description", description);
        payload.addProperty("requirements", requirements);
        payload.addProperty("is_stipend_disclosed", cbDisclose.isChecked());

        if (!stipendStr.isEmpty()) {
            try {
                payload.addProperty("stipend", Double.parseDouble(stipendStr));
            } catch (Exception ignored) {
            }
        }

        if (editingPost == null) {
            payload.addProperty("status", asDraft ? "draft" : "pending_approval");
        }

        btnSecondary.setEnabled(false);
        btnPrimary.setEnabled(false);
        btnPrimary.setText(editingPost != null ? "Saving…" : (asDraft ? "Saving…" : "Publishing…"));

        CompanyRepository repo = new CompanyRepository(requireContext());
        CompanyRepository.JsonResult callback = new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (!isAdded()) return;
                JsonObject saved = Json.data(body);
                String msg = editingPost != null
                        ? "Listing updated successfully!"
                        : (asDraft ? "Saved as draft!" : "Submitted for admin review! 🎉");
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                if (listener != null) listener.onPostSaved(saved);
                dismissAllowingStateLoss();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (!isAdded()) return;
                btnSecondary.setEnabled(true);
                btnPrimary.setEnabled(true);
                btnPrimary.setText(editingPost != null ? "Save Changes" : "Publish Cohort");
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show();
            }
        };

        if (editingPost != null && editingPost.has("id")) {
            repo.updatePost(editingPost.get("id").getAsInt(), payload, callback);
        } else {
            repo.storePost(payload, callback);
        }
    }

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
