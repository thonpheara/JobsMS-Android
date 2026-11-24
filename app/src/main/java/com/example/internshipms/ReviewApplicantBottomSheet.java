package com.example.internshipms;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.example.internshipms.network.ApiError;
import com.example.internshipms.network.AvatarLoader;
import com.example.internshipms.network.Json;
import com.example.internshipms.repository.CompanyRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDate;
import java.util.Locale;

public class ReviewApplicantBottomSheet extends BottomSheetDialogFragment {

    public interface OnApplicantUpdatedListener {
        void onApplicantUpdated();
    }

    private static final String ARG_NAME = "name";
    private static final String ARG_ROLE = "role";
    private static final String ARG_APP_JSON = "app_json";

    private OnApplicantUpdatedListener listener;
    private JsonObject appJson;
    private String selectedOption = "pending";

    private MaterialCardView cardOptPending, cardOptUnderReview, cardOptShortlisted,
            cardOptInterview, cardOptAccept, cardOptReject;
    private MaterialCardView radioOptPending, radioOptUnderReview, radioOptShortlisted,
            radioOptInterview, radioOptAccept, radioOptReject;
    private View checkOptPending, checkOptUnderReview, checkOptShortlisted,
            checkOptInterview, checkOptAccept, checkOptReject;
    private TextView tvInterviewSub;
    private EditText etCompanyNotes;
    private MaterialButton btnSaveStatus;

    public static void show(FragmentManager fm, String name, String role) {
        ReviewApplicantBottomSheet sheet = new ReviewApplicantBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putString(ARG_ROLE, role);
        sheet.setArguments(args);
        sheet.show(fm, "review_applicant_sheet");
    }

    public static void show(FragmentManager fm,
                            @NonNull JsonObject application,
                            @Nullable OnApplicantUpdatedListener listener) {
        ReviewApplicantBottomSheet sheet = new ReviewApplicantBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_APP_JSON, application.toString());
        sheet.setArguments(args);
        sheet.listener = listener;
        sheet.show(fm, "review_applicant_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_review_applicant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_APP_JSON)) {
            try {
                appJson = JsonParser.parseString(args.getString(ARG_APP_JSON, "{}")).getAsJsonObject();
            } catch (Exception ignored) {
            }
        }

        int appId = appJson != null ? Json.integer(appJson, 0, "id") : 0;

        // Resolve student & post details
        JsonObject student = appJson != null ? Json.obj(appJson, "student_profile", "student") : null;
        JsonObject studentUser = student != null ? Json.obj(student, "user") : null;
        JsonObject post = appJson != null ? Json.obj(appJson, "internship_post", "post") : null;

        String name = Json.str(studentUser, null, "name");
        if (name == null) name = Json.str(student, null, "full_name", "name");
        if (name == null && args != null) name = args.getString(ARG_NAME, "Sophea Chan");
        if (name == null || name.isEmpty()) name = "Sophea Chan";

        String role = Json.str(post, null, "title", "position");
        if (role == null && args != null) role = args.getString(ARG_ROLE, "Android Developer Intern");
        if (role == null || role.isEmpty()) role = "Android Developer Intern";

        String university = Json.str(student, "USEA", "university", "school", "institution");
        String uniShort = shortenUniversity(university);

        String status = appJson != null ? Json.str(appJson, "pending", "status") : "pending";
        String avatarUrl = AvatarLoader.extractUrl(studentUser, student);

        // Header & Candidate Summary Banner
        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        TextView tvInitials = view.findViewById(R.id.tvCandidateInitials);
        ImageView ivAvatar = view.findViewById(R.id.ivCandidateAvatar);
        TextView tvName = view.findViewById(R.id.tvCandidateName);
        TextView tvUni = view.findViewById(R.id.tvCandidateUni);
        TextView tvRole = view.findViewById(R.id.tvCandidateRole);
        TextView tvMeta = view.findViewById(R.id.tvCandidateMeta);
        View btnViewFullProfile = view.findViewById(R.id.btnViewFullProfile);
        View cardRescheduleAlert = view.findViewById(R.id.cardRescheduleAlert);
        TextView tvRescheduleReason = view.findViewById(R.id.tvRescheduleReason);
        etCompanyNotes = view.findViewById(R.id.etCompanyNotes);

        tvInitials.setText(initials(name));
        AvatarLoader.bind(avatarUrl, ivAvatar, tvInitials);
        tvName.setText(name);
        tvUni.setText(uniShort);
        tvRole.setText("Applied for " + role);

        // Candidate Major + GPA metadata line
        if (tvMeta != null && student != null) {
            String major = Json.str(student, null, "major", "department");
            String gpa = Json.str(student, null, "gpa", "cgpa");
            StringBuilder sb = new StringBuilder();
            if (major != null && !major.isEmpty()) sb.append(major);
            if (gpa != null && !gpa.isEmpty()) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append("★ GPA ").append(gpa);
            }
            if (sb.length() > 0) {
                tvMeta.setText(sb.toString());
                tvMeta.setVisibility(View.VISIBLE);
            }
        }

        // Open Full Student Profile button
        int studentId = appJson != null ? Json.integer(appJson, 0, "student_profile_id", "student_id") : 0;
        if (studentId == 0 && student != null) {
            studentId = Json.integer(student, 0, "id");
        }
        if (studentId == 0 && studentUser != null) {
            studentId = Json.integer(studentUser, 0, "id");
        }
        final int finalStudentId = studentId;
        final String finalStudentName = name;
        if (btnViewFullProfile != null) {
            if (finalStudentId > 0) {
                btnViewFullProfile.setVisibility(View.VISIBLE);
                btnViewFullProfile.setOnClickListener(v ->
                        CompanyStudentProfileActivity.launch(requireContext(), finalStudentId, finalStudentName));
            } else {
                btnViewFullProfile.setVisibility(View.GONE);
            }
        }

        // Populate existing Company Notes
        if (etCompanyNotes != null && appJson != null) {
            String existingNotes = Json.str(appJson, null, "company_notes");
            if (existingNotes != null && !existingNotes.isEmpty()) {
                etCompanyNotes.setText(existingNotes);
            }
        }

        // 6 Option Cards
        cardOptPending = view.findViewById(R.id.cardOptPending);
        radioOptPending = view.findViewById(R.id.radioOptPending);
        checkOptPending = view.findViewById(R.id.checkOptPending);

        cardOptUnderReview = view.findViewById(R.id.cardOptUnderReview);
        radioOptUnderReview = view.findViewById(R.id.radioOptUnderReview);
        checkOptUnderReview = view.findViewById(R.id.checkOptUnderReview);

        cardOptShortlisted = view.findViewById(R.id.cardOptShortlisted);
        radioOptShortlisted = view.findViewById(R.id.radioOptShortlisted);
        checkOptShortlisted = view.findViewById(R.id.checkOptShortlisted);

        cardOptInterview = view.findViewById(R.id.cardOptInterview);
        radioOptInterview = view.findViewById(R.id.radioOptInterview);
        checkOptInterview = view.findViewById(R.id.checkOptInterview);
        tvInterviewSub = view.findViewById(R.id.tvInterviewSub);

        cardOptAccept = view.findViewById(R.id.cardOptAccept);
        radioOptAccept = view.findViewById(R.id.radioOptAccept);
        checkOptAccept = view.findViewById(R.id.checkOptAccept);

        cardOptReject = view.findViewById(R.id.cardOptReject);
        radioOptReject = view.findViewById(R.id.radioOptReject);
        checkOptReject = view.findViewById(R.id.checkOptReject);

        btnSaveStatus = view.findViewById(R.id.btnSaveStatus);

        // Show existing scheduled interview subtitle if present
        JsonObject interview = appJson != null ? StudentApplicationsActivity.extractInterview(appJson) : null;
        if (interview != null) {
            String schedAt = Json.str(interview, null, "scheduled_at");
            if (schedAt != null && !schedAt.isEmpty()) {
                tvInterviewSub.setText("Scheduled: " + schedAt);
            }
            String intStatus = Json.str(interview, "", "status");
            if ("reschedule_requested".equalsIgnoreCase(intStatus) && cardRescheduleAlert != null) {
                cardRescheduleAlert.setVisibility(View.VISIBLE);
                String reason = Json.str(interview, "Candidate requested a different time slot.", "reschedule_reason");
                if (tvRescheduleReason != null) {
                    tvRescheduleReason.setText("Reason: " + reason);
                }
            }
        }

        // Pre-select current status among the 6 options
        selectOption(mapStatusToOption(status));

        cardOptPending.setOnClickListener(v -> selectOption("pending"));
        cardOptUnderReview.setOnClickListener(v -> selectOption("under_review"));
        cardOptShortlisted.setOnClickListener(v -> selectOption("shortlisted"));
        cardOptInterview.setOnClickListener(v -> selectOption("interviewed"));
        cardOptAccept.setOnClickListener(v -> selectOption("accepted"));
        cardOptReject.setOnClickListener(v -> selectOption("rejected"));

        final String finalName = name;
        final String defaultLoc = Json.str(post, "Phnom Penh Office", "location");

        btnSaveStatus.setOnClickListener(v -> {
            String msg;
            switch (selectedOption) {
                case "pending":
                    msg = finalName + " moved to Pending.";
                    break;
                case "under_review":
                    msg = finalName + " moved to Under Review.";
                    break;
                case "shortlisted":
                    msg = finalName + " marked as Shortlisted!";
                    break;
                case "interviewed":
                    msg = finalName + " marked as Interviewed!";
                    break;
                case "accepted":
                    msg = "Offer extended to " + finalName + "! 🎉";
                    break;
                case "rejected":
                    msg = "Application marked as Rejected.";
                    break;
                default:
                    msg = "Status updated.";
                    break;
            }
            saveStatusChange(appId, selectedOption, finalName, defaultLoc, msg);
        });
    }

    private String mapStatusToOption(String status) {
        if (status == null) return "pending";
        switch (status.toLowerCase(Locale.ROOT)) {
            case "pending":
                return "pending";
            case "under_review":
            case "reviewed":
                return "under_review";
            case "shortlisted":
                return "shortlisted";
            case "interview":
            case "interviewed":
            case "interview_scheduled":
                return "interviewed";
            case "accepted":
                return "accepted";
            case "rejected":
                return "rejected";
            default:
                return "pending";
        }
    }

    private void selectOption(String option) {
        selectedOption = option;
        styleOptionCard(cardOptPending, radioOptPending, checkOptPending, "pending".equals(option));
        styleOptionCard(cardOptUnderReview, radioOptUnderReview, checkOptUnderReview, "under_review".equals(option));
        styleOptionCard(cardOptShortlisted, radioOptShortlisted, checkOptShortlisted, "shortlisted".equals(option));
        styleOptionCard(cardOptInterview, radioOptInterview, checkOptInterview, "interviewed".equals(option));
        styleOptionCard(cardOptAccept, radioOptAccept, checkOptAccept, "accepted".equals(option));
        styleOptionCard(cardOptReject, radioOptReject, checkOptReject, "rejected".equals(option));
    }

    private void styleOptionCard(MaterialCardView card, MaterialCardView radio, View check, boolean selected) {
        int primaryGreen = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
        if (selected) {
            card.setCardBackgroundColor(Color.parseColor("#FFECFDF5"));
            card.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#FFA7F3D0")));
            card.setStrokeWidth(dp(1.5f));
            radio.setCardBackgroundColor(primaryGreen);
            radio.setStrokeColor(ColorStateList.valueOf(primaryGreen));
            check.setVisibility(View.VISIBLE);
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_card));
            card.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.stroke_soft)));
            card.setStrokeWidth(dp(1f));
            radio.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_card));
            radio.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#FFCBD5E1")));
            check.setVisibility(View.GONE);
        }
    }

    private void saveStatusChange(int appId, String newStatus, String candidateName,
                                  String defaultLocation, String successMsg) {
        if (appId <= 0) {
            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }
        btnSaveStatus.setEnabled(false);
        btnSaveStatus.setText("Saving…");

        String typedNotes = etCompanyNotes != null && etCompanyNotes.getText() != null
                ? etCompanyNotes.getText().toString().trim()
                : null;
        String existingNotes = (typedNotes != null && !typedNotes.isEmpty())
                ? typedNotes
                : (appJson != null ? Json.str(appJson, null, "company_notes") : null);
        CompanyRepository repo = new CompanyRepository(requireContext());
        repo.updateApplicantStatus(appId, newStatus, existingNotes, new CompanyRepository.JsonResult() {
            @Override
            public void onSuccess(@NonNull JsonObject body) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
                if (listener != null) listener.onApplicantUpdated();
                dismissAllowingStateLoss();
            }

            @Override
            public void onError(@NonNull ApiError error) {
                if (!isAdded()) return;
                // Fallback if backend validation still expects "reviewed" instead of "under_review"
                if (error.code == 422 && "under_review".equals(newStatus)) {
                    repo.updateApplicantStatus(appId, "reviewed", existingNotes, new CompanyRepository.JsonResult() {
                        @Override
                        public void onSuccess(@NonNull JsonObject body) {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
                            if (listener != null) listener.onApplicantUpdated();
                            dismissAllowingStateLoss();
                        }

                        @Override
                        public void onError(@NonNull ApiError err2) {
                            if (!isAdded()) return;
                            btnSaveStatus.setEnabled(true);
                            btnSaveStatus.setText("Save Status Change  →");
                            Toast.makeText(requireContext(), err2.message, Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                // Fallback if backend requires scheduling an interview to set "interviewed"
                if (error.code == 422 && "interviewed".equals(newStatus)) {
                    btnSaveStatus.setEnabled(true);
                    btnSaveStatus.setText("Save Status Change  →");
                    showScheduleInterviewDialog(appId, candidateName, defaultLocation);
                    return;
                }
                btnSaveStatus.setEnabled(true);
                btnSaveStatus.setText("Save Status Change  →");
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showScheduleInterviewDialog(int appId, String candidateName, String defaultLocation) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        DatePickerDialog dateDlg = new DatePickerDialog(
                requireContext(),
                (dp, year, month, day) -> {
                    TimePickerDialog timeDlg = new TimePickerDialog(
                            requireContext(),
                            (tp, hour, minute) -> {
                                String scheduledAt = String.format(Locale.US,
                                        "%04d-%02d-%02d %02d:%02d:00",
                                        year, month + 1, day, hour, minute);
                                showInterviewDetailsDialog(appId, candidateName, scheduledAt, defaultLocation);
                            },
                            10, 0, false);
                    timeDlg.setTitle("Select Interview Time");
                    timeDlg.show();
                },
                tomorrow.getYear(),
                tomorrow.getMonthValue() - 1,
                tomorrow.getDayOfMonth());
        dateDlg.getDatePicker().setMinDate(System.currentTimeMillis() + 3_600_000L);
        dateDlg.setTitle("Select Interview Date");
        dateDlg.show();
    }

    private void showInterviewDetailsDialog(int appId, String candidateName,
                                            String scheduledAt, String defaultLocation) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, (int) (12 * density), pad, 0);

        RadioGroup rgMode = new RadioGroup(requireContext());
        rgMode.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbInPerson = new RadioButton(requireContext());
        rbInPerson.setId(View.generateViewId());
        rbInPerson.setText("In-Person Office");
        rbInPerson.setChecked(true);
        RadioButton rbOnline = new RadioButton(requireContext());
        rbOnline.setId(View.generateViewId());
        rbOnline.setText("Online Video");
        rgMode.addView(rbInPerson);
        rgMode.addView(rbOnline);
        layout.addView(rgMode);

        EditText etLocLink = new EditText(requireContext());
        etLocLink.setHint("Office Location or Google Meet / Zoom Link");
        etLocLink.setText(defaultLocation);
        etLocLink.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(etLocLink);

        EditText etNotes = new EditText(requireContext());
        etNotes.setHint("Interview instructions / notes for student...");
        etNotes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        layout.addView(etNotes);

        rgMode.setOnCheckedChangeListener((g, id) -> {
            if (id == rbOnline.getId()) {
                etLocLink.setText("https://meet.google.com/new");
            } else {
                etLocLink.setText(defaultLocation);
            }
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Schedule Interview • " + scheduledAt.substring(0, 16))
                .setView(layout)
                .setPositiveButton("Send Invitation", (d, w) -> {
                    String mode = rgMode.getCheckedRadioButtonId() == rbOnline.getId()
                            ? "online" : "in_person";
                    String locOrLink = etLocLink.getText() == null ? "" : etLocLink.getText().toString().trim();
                    if (locOrLink.isEmpty()) locOrLink = defaultLocation;
                    String notes = etNotes.getText() == null ? "" : etNotes.getText().toString().trim();

                    JsonObject payload = new JsonObject();
                    payload.addProperty("scheduled_at", scheduledAt);
                    payload.addProperty("meeting_type", mode);
                    payload.addProperty("location_or_link", locOrLink);
                    if (!notes.isEmpty()) payload.addProperty("notes", notes);

                    btnSaveStatus.setEnabled(false);
                    btnSaveStatus.setText("Scheduling…");

                    new CompanyRepository(requireContext()).scheduleInterview(appId, payload,
                            new CompanyRepository.JsonResult() {
                                @Override
                                public void onSuccess(@NonNull JsonObject body) {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(),
                                            "Interview scheduled with " + candidateName + "! 🎉",
                                            Toast.LENGTH_SHORT).show();
                                    if (listener != null) listener.onApplicantUpdated();
                                    dismissAllowingStateLoss();
                                }

                                @Override
                                public void onError(@NonNull ApiError error) {
                                    if (!isAdded()) return;
                                    btnSaveStatus.setEnabled(true);
                                    btnSaveStatus.setText("Save Status Change  →");
                                    Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String shortenUniversity(String uni) {
        if (uni == null || uni.trim().isEmpty()) return "USEA";
        String trimmed = uni.trim();
        if (trimmed.length() <= 8) return trimmed.toUpperCase(Locale.ROOT);
        String[] parts = trimmed.split("\\s+");
        StringBuilder acronym = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && Character.isUpperCase(p.charAt(0)) && !"of".equalsIgnoreCase(p) && !"and".equalsIgnoreCase(p)) {
                acronym.append(p.charAt(0));
            }
        }
        return acronym.length() >= 2 ? acronym.toString() : trimmed.substring(0, Math.min(6, trimmed.length())).toUpperCase(Locale.ROOT);
    }

    private static String initials(String text) {
        String[] words = text.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "S";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }
}
