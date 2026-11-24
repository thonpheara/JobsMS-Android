package com.example.internshipms;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.repository.StudentRepository;
import com.example.internshipms.session.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

import retrofit2.Call;

public class ApplyInternshipBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_POST_ID = "arg_post_id";
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_COMPANY = "arg_company";
    private static final String ARG_LOCATION = "arg_location";
    private static final int MIN_CHARS = 20;

    private TextView tvCvFilename;
    private TextView tvCvBadge;
    private TextView tvCvMeta;
    private byte[] attachedPdfBytes;
    private String attachedPdfName;
    private Call<JsonObject> applyCall;
    @Nullable
    private Runnable onAppliedListener;

    private final ActivityResultLauncher<String> pdfPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPdfPicked);

    public static void show(FragmentManager fm, String title, String company, String location) {
        show(fm, 0, title, company, location, null);
    }

    public static void show(FragmentManager fm, int postId, String title, String company,
                            String location, @Nullable Runnable onApplied) {
        ApplyInternshipBottomSheet sheet = new ApplyInternshipBottomSheet();
        sheet.onAppliedListener = onApplied;
        Bundle args = new Bundle();
        args.putInt(ARG_POST_ID, postId);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_COMPANY, company);
        args.putString(ARG_LOCATION, location);
        sheet.setArguments(args);
        sheet.show(fm, "apply_internship_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_apply_internship, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        int postId = args != null ? args.getInt(ARG_POST_ID, 0) : 0;
        String title = args != null ? args.getString(ARG_TITLE, "Internship Position")
                : "Internship Position";
        String company = args != null ? args.getString(ARG_COMPANY, "Company")
                : "Company";
        String location = args != null ? args.getString(ARG_LOCATION, "Cambodia")
                : "Cambodia";

        TextView tvInitials = view.findViewById(R.id.tvInitials);
        tvInitials.setText(initials(company));
        String logoUrl = com.example.internshipms.network.AvatarLoader
                .extractCompanyLogo(null, null, company);
        com.example.internshipms.network.AvatarLoader.bind(
                logoUrl,
                view.findViewById(R.id.ivCompanyLogo),
                tvInitials);
        ((TextView) view.findViewById(R.id.tvRoleTitle)).setText(title);
        ((TextView) view.findViewById(R.id.tvCompanyInfo)).setText(company + " • " + location);

        TextView tvProTip = view.findViewById(R.id.tvProTip);
        tvProTip.setText(Html.fromHtml(
                "<b>Pro-tip:</b> Highlight specific GitHub repo links or semester coursework.",
                Html.FROM_HTML_MODE_COMPACT));

        ((TextView) view.findViewById(R.id.tvPrivacyNotice)).setText(
                "Shared exclusively with " + company
                        + " recruiting team under Internship Data Privacy standards.");

        tvCvFilename = view.findViewById(R.id.tvCvFilename);
        tvCvBadge = view.findViewById(R.id.tvCvBadge);
        tvCvMeta = view.findViewById(R.id.tvCvMeta);

        String studentName = SessionManager.get(requireContext()).getName();
        if (studentName != null && !studentName.trim().isEmpty()) {
            String safeName = studentName.trim().replaceAll("\\s+", "_");
            tvCvFilename.setText(safeName + "_Resume.pdf");
        }

        TextView tvCharCounter = view.findViewById(R.id.tvCharCounter);
        TextInputLayout tilCoverLetter = view.findViewById(R.id.tilCoverLetter);
        TextInputEditText etCoverLetter = view.findViewById(R.id.etCoverLetter);
        tvCharCounter.setText("0 / " + MIN_CHARS + " chars min");

        etCoverLetter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int len = s == null ? 0 : s.toString().trim().length();
                tvCharCounter.setText(len + " / " + MIN_CHARS + " chars min");
                if (len >= MIN_CHARS) {
                    tilCoverLetter.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        view.findViewById(R.id.btnAttachCv).setOnClickListener(v -> pdfPicker.launch("application/pdf"));
        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        MaterialButton btnSubmit = view.findViewById(R.id.btnSubmitApplication);
        btnSubmit.setOnClickListener(v -> {
            String letter = etCoverLetter.getText() == null ? "" : etCoverLetter.getText().toString().trim();
            if (letter.length() < MIN_CHARS) {
                tilCoverLetter.setError("Please enter at least " + MIN_CHARS + " characters.");
                return;
            }
            if (postId <= 0) {
                Toast.makeText(requireContext(), "Application sent to " + company + "!", Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Submitting...");
            StudentRepository repo = new StudentRepository(requireContext());
            applyCall = repo.applyPost(postId, letter, attachedPdfBytes, attachedPdfName,
                    new StudentRepository.JsonResult() {
                        @Override
                        public void onSuccess(@NonNull JsonObject body) {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(),
                                    "Application submitted to " + company + "!",
                                    Toast.LENGTH_SHORT).show();
                            if (onAppliedListener != null) onAppliedListener.run();
                            dismiss();
                        }

                        @Override
                        public void onError(@NonNull ApiError error) {
                            if (!isAdded()) return;
                            btnSubmit.setEnabled(true);
                            btnSubmit.setText("Confirm & Send Application");
                            Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void onPdfPicked(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return;
        String displayName = "Custom_Resume.pdf";
        long sizeBytes = -1;
        try (Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String n = cursor.getString(nameIndex);
                    if (n != null && !n.isEmpty()) displayName = n;
                }
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
        }

        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                }
                attachedPdfBytes = buffer.toByteArray();
                attachedPdfName = displayName;
            }
        } catch (Exception ignored) {
        }

        tvCvFilename.setText(displayName);
        tvCvBadge.setText("✓ Custom attached");
        String sizeText = sizeBytes > 0
                ? String.format(Locale.US, "%.1f MB • Just attached", sizeBytes / (1024f * 1024f))
                : "PDF • Just attached";
        tvCvMeta.setText(sizeText);
    }

    @Override
    public void onDestroyView() {
        if (applyCall != null) applyCall.cancel();
        super.onDestroyView();
    }

    private static String initials(String text) {
        String[] words = text.trim().split("[\\s/(]+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "I";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }
}
