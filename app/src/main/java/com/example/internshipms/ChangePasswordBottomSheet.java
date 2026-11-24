package com.example.internshipms;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ChangePasswordBottomSheet extends BottomSheetDialogFragment {

    private static final int MIN_LENGTH = 8;

    public static void show(FragmentManager fm) {
        new ChangePasswordBottomSheet().show(fm, "change_password_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_change_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputLayout tilCurrent = view.findViewById(R.id.tilCurrentPassword);
        TextInputLayout tilNew = view.findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirm = view.findViewById(R.id.tilConfirmPassword);

        TextInputEditText etCurrent = view.findViewById(R.id.etCurrentPassword);
        TextInputEditText etNew = view.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = view.findViewById(R.id.etConfirmPassword);

        LinearProgressIndicator progressStrength = view.findViewById(R.id.progressStrength);
        TextView tvStrengthLabel = view.findViewById(R.id.tvStrengthLabel);

        etNew.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilNew.setError(null);
                updateStrength(s == null ? "" : s.toString(), progressStrength, tvStrengthLabel);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btnUpdatePassword).setOnClickListener(v -> {
            tilCurrent.setError(null);
            tilNew.setError(null);
            tilConfirm.setError(null);

            String current = text(etCurrent);
            String newPass = text(etNew);
            String confirm = text(etConfirm);

            boolean ok = true;
            if (current.isEmpty()) {
                tilCurrent.setError(getString(R.string.error_required));
                ok = false;
            }
            if (newPass.length() < MIN_LENGTH) {
                tilNew.setError(getString(R.string.error_password_short));
                ok = false;
            } else if (newPass.equals(current)) {
                tilNew.setError("New password must be different from current password.");
                ok = false;
            }
            if (!newPass.equals(confirm)) {
                tilConfirm.setError(getString(R.string.error_password_match));
                ok = false;
            }
            if (!ok) return;

            v.setEnabled(false);
            new com.example.internshipms.repository.StudentRepository(requireContext())
                    .updatePassword(current, newPass, confirm,
                            new com.example.internshipms.repository.StudentRepository.JsonResult() {
                                @Override
                                public void onSuccess(@NonNull com.google.gson.JsonObject body) {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(),
                                            "Password updated successfully!",
                                            Toast.LENGTH_SHORT).show();
                                    dismiss();
                                }

                                @Override
                                public void onError(@NonNull com.example.internshipms.network.ApiError error) {
                                    if (!isAdded()) return;
                                    v.setEnabled(true);
                                    String currErr = error.fieldErrors.get("current_password");
                                    String passErr = error.fieldErrors.get("password");
                                    if (currErr != null) tilCurrent.setError(currErr);
                                    if (passErr != null) tilNew.setError(passErr);
                                    if (currErr == null && passErr == null) {
                                        Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
        });
    }

    private void updateStrength(String pass, LinearProgressIndicator bar, TextView label) {
        if (pass.isEmpty()) {
            bar.setProgressCompat(0, true);
            label.setText("Enter new password");
            return;
        }
        int score = 0;
        if (pass.length() >= MIN_LENGTH) score += 35;
        if (pass.matches(".*[A-Z].*") && pass.matches(".*[a-z].*")) score += 25;
        if (pass.matches(".*\\d.*")) score += 20;
        if (pass.matches(".*[^A-Za-z0-9].*")) score += 20;

        bar.setProgressCompat(score, true);
        int color;
        String text;
        if (score < 40) {
            color = ContextCompat.getColor(requireContext(), R.color.tint_red);
            text = "Weak";
        } else if (score < 80) {
            color = ContextCompat.getColor(requireContext(), R.color.tint_amber);
            text = "Medium";
        } else {
            color = MaterialColors.getColor(bar, androidx.appcompat.R.attr.colorPrimary);
            text = "Strong";
        }
        bar.setIndicatorColor(color);
        label.setTextColor(color);
        label.setText(text);
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }
}

