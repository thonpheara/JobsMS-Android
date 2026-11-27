package com.example.internshipms;

import android.content.res.ColorStateList;
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
import com.google.android.material.button.MaterialButton;
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
        TextView tvReqLength = view.findViewById(R.id.tvReqLength);
        TextView tvReqCase = view.findViewById(R.id.tvReqCase);
        TextView tvReqSymbol = view.findViewById(R.id.tvReqSymbol);
        MaterialButton btnUpdate = view.findViewById(R.id.btnUpdatePassword);

        etCurrent.addTextChangedListener(new SimpleWatcher(() -> tilCurrent.setError(null)));

        Runnable syncConfirmMatch = () -> {
            tilConfirm.setError(null);
            String np = text(etNew);
            String cp = text(etConfirm);
            if (!cp.isEmpty() && cp.equals(np)) {
                tilConfirm.setHelperText("✓ Passwords match");
                tilConfirm.setHelperTextColor(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.tint_green)));
            } else {
                tilConfirm.setHelperText(null);
            }
        };

        etNew.addTextChangedListener(new SimpleWatcher(() -> {
            tilNew.setError(null);
            String np = text(etNew);
            updateStrength(np, progressStrength, tvStrengthLabel);
            updateChecklist(np, tvReqLength, tvReqCase, tvReqSymbol);
            syncConfirmMatch.run();
        }));

        etConfirm.addTextChangedListener(new SimpleWatcher(syncConfirmMatch));

        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        btnUpdate.setOnClickListener(v -> {
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

            btnUpdate.setEnabled(false);
            btnUpdate.setText("Updating…");
            setCancelable(false);

            new com.example.internshipms.repository.StudentRepository(requireContext())
                    .updatePassword(current, newPass, confirm,
                            new com.example.internshipms.repository.StudentRepository.JsonResult() {
                                @Override
                                public void onSuccess(@NonNull com.google.gson.JsonObject body) {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(),
                                            "Password updated successfully!",
                                            Toast.LENGTH_SHORT).show();
                                    dismissAllowingStateLoss();
                                }

                                @Override
                                public void onError(@NonNull com.example.internshipms.network.ApiError error) {
                                    if (!isAdded()) return;
                                    setCancelable(true);
                                    btnUpdate.setEnabled(true);
                                    btnUpdate.setText("Update Password");
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

    private void updateChecklist(String pass, TextView tvLen, TextView tvCase, TextView tvSym) {
        boolean hasLen = pass.length() >= MIN_LENGTH;
        boolean hasCase = pass.matches(".*[A-Z].*") && pass.matches(".*[a-z].*");
        boolean hasSym = pass.matches(".*\\d.*") || pass.matches(".*[^A-Za-z0-9].*");
        styleCheckItem(tvLen, hasLen, "At least 8 characters");
        styleCheckItem(tvCase, hasCase, "Uppercase & lowercase letters");
        styleCheckItem(tvSym, hasSym, "At least one number or symbol");
    }

    private void styleCheckItem(@Nullable TextView tv, boolean met, String text) {
        if (tv == null) return;
        tv.setText((met ? "✓  " : "○  ") + text);
        tv.setTextColor(ContextCompat.getColor(requireContext(),
                met ? R.color.tint_green : R.color.nav_unselected));
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

