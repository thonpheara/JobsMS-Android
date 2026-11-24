package com.example.internshipms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.CompanyRepository;
import com.example.internshipms.session.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class EditCompanyProfileBottomSheet extends BottomSheetDialogFragment {

    public interface OnCompanyProfileSavedListener {
        void onCompanyProfileSaved(@NonNull JsonObject updatedCompany);
    }

    private static final String ARG_COMPANY_JSON = "arg_company_json";

    private OnCompanyProfileSavedListener listener;
    private JsonObject companyJson;

    public static void show(FragmentManager fm,
                            @Nullable JsonObject company,
                            @Nullable OnCompanyProfileSavedListener listener) {
        EditCompanyProfileBottomSheet sheet = new EditCompanyProfileBottomSheet();
        if (company != null) {
            Bundle args = new Bundle();
            args.putString(ARG_COMPANY_JSON, company.toString());
            sheet.setArguments(args);
        }
        sheet.listener = listener;
        sheet.show(fm, "edit_company_profile_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_edit_company_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_COMPANY_JSON)) {
            try {
                companyJson = JsonParser.parseString(args.getString(ARG_COMPANY_JSON, "{}")).getAsJsonObject();
            } catch (Exception ignored) {
            }
        }

        view.findViewById(R.id.btnCloseSheet).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnCancelEdit).setOnClickListener(v -> dismiss());

        TextInputEditText etCompanyName = view.findViewById(R.id.etCompanyName);
        TextInputEditText etIndustry = view.findViewById(R.id.etIndustry);
        TextInputEditText etLocation = view.findViewById(R.id.etLocation);
        TextInputEditText etAddress = view.findViewById(R.id.etAddress);
        TextInputEditText etWebsite = view.findViewById(R.id.etWebsite);
        TextInputEditText etContactPerson = view.findViewById(R.id.etContactPerson);
        TextInputEditText etContactPhone = view.findViewById(R.id.etContactPhone);
        TextInputEditText etDescription = view.findViewById(R.id.etDescription);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveProfile);

        String fallbackName = SessionManager.get(requireContext()).getName();
        etCompanyName.setText(Json.str(companyJson, fallbackName != null ? fallbackName : "", "company_name", "name"));
        etIndustry.setText(Json.str(companyJson, "", "industry"));
        etLocation.setText(Json.str(companyJson, "", "location"));
        etAddress.setText(Json.str(companyJson, "", "address"));
        etWebsite.setText(Json.str(companyJson, "", "website"));
        etContactPerson.setText(Json.str(companyJson, "", "contact_person"));
        etContactPhone.setText(Json.str(companyJson, "", "contact_phone"));
        etDescription.setText(Json.str(companyJson, "", "description"));

        btnSave.setOnClickListener(v -> {
            String companyName = text(etCompanyName);
            if (companyName.isEmpty()) {
                etCompanyName.setError("Company name is required");
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

            btnSave.setEnabled(false);
            btnSave.setText("Saving…");

            new CompanyRepository(requireContext()).updateProfile(payload, new CompanyRepository.JsonResult() {
                @Override
                public void onSuccess(@NonNull JsonObject body) {
                    if (!isAdded()) return;
                    JsonObject updated = Json.data(body);
                    Toast.makeText(requireContext(),
                            "Company profile updated successfully!",
                            Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onCompanyProfileSaved(updated);
                    dismissAllowingStateLoss();
                }

                @Override
                public void onError(@NonNull ApiError error) {
                    if (!isAdded()) return;
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Profile Changes");
                    Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}

