package com.example.internshipms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;

public class FilterCohortsBottomSheet extends BottomSheetDialogFragment {

    public interface OnFilterAppliedListener {
        void onFilterApplied(int minStipend);
    }

    public interface OnAdvancedFilterListener {
        void onAdvancedFilterApplied(String sort, String deadline, String stipendMode, int minStipend);
    }

    private OnFilterAppliedListener listener;
    private OnAdvancedFilterListener advancedListener;

    private String initialSort = "newest";
    private String initialDeadline = "open";
    private String initialStipendMode = "";
    private int initialMinStipend = 0;

    public static void show(FragmentManager fm, int currentMinStipend,
                            OnFilterAppliedListener listener) {
        FilterCohortsBottomSheet sheet = new FilterCohortsBottomSheet();
        sheet.initialMinStipend = currentMinStipend;
        sheet.initialStipendMode = currentMinStipend > 0 ? "min" : "";
        sheet.listener = listener;
        sheet.show(fm, "filter_cohorts_sheet");
    }

    public static void showAdvanced(FragmentManager fm,
                                    String sort,
                                    String deadline,
                                    String stipendMode,
                                    int minStipend,
                                    OnAdvancedFilterListener advancedListener) {
        FilterCohortsBottomSheet sheet = new FilterCohortsBottomSheet();
        sheet.initialSort = sort != null ? sort : "newest";
        sheet.initialDeadline = deadline != null ? deadline : "open";
        sheet.initialStipendMode = stipendMode != null ? stipendMode : "";
        sheet.initialMinStipend = minStipend;
        sheet.advancedListener = advancedListener;
        sheet.show(fm, "filter_cohorts_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_filter_cohorts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ChipGroup sortGroup = view.findViewById(R.id.chipGroupSort);
        ChipGroup deadlineGroup = view.findViewById(R.id.chipGroupDeadline);
        ChipGroup stipendGroup = view.findViewById(R.id.chipGroupStipend);

        if ("deadline".equals(initialSort)) {
            sortGroup.check(R.id.chipSortDeadline);
        } else if ("stipend".equals(initialSort)) {
            sortGroup.check(R.id.chipSortStipend);
        } else {
            sortGroup.check(R.id.chipSortNewest);
        }

        if ("soon".equals(initialDeadline)) {
            deadlineGroup.check(R.id.chipDeadlineSoon);
        } else {
            deadlineGroup.check(R.id.chipDeadlineOpen);
        }

        if ("has".equals(initialStipendMode)) {
            stipendGroup.check(R.id.chipStipendPaid);
        } else if ("none".equals(initialStipendMode)) {
            stipendGroup.check(R.id.chipStipendNone);
        } else if (initialMinStipend >= 200) {
            stipendGroup.check(R.id.chipStipend200);
        } else if (initialMinStipend >= 150) {
            stipendGroup.check(R.id.chipStipend150);
        } else {
            stipendGroup.check(R.id.chipStipendAll);
        }

        view.findViewById(R.id.btnResetFilters).setOnClickListener(v -> {
            sortGroup.check(R.id.chipSortNewest);
            deadlineGroup.check(R.id.chipDeadlineOpen);
            stipendGroup.check(R.id.chipStipendAll);
            if (listener != null) {
                listener.onFilterApplied(0);
            }
            if (advancedListener != null) {
                advancedListener.onAdvancedFilterApplied("newest", "open", "", 0);
            }
            dismiss();
        });

        view.findViewById(R.id.btnApplyFilters).setOnClickListener(v -> {
            String sort = "newest";
            int checkedSort = sortGroup.getCheckedChipId();
            if (checkedSort == R.id.chipSortDeadline) sort = "deadline";
            else if (checkedSort == R.id.chipSortStipend) sort = "stipend";

            String deadline = deadlineGroup.getCheckedChipId() == R.id.chipDeadlineSoon
                    ? "soon" : "open";

            String stipendMode = "";
            int minStipend = 0;
            int checkedStipend = stipendGroup.getCheckedChipId();
            if (checkedStipend == R.id.chipStipendPaid) {
                stipendMode = "has";
            } else if (checkedStipend == R.id.chipStipendNone) {
                stipendMode = "none";
            } else if (checkedStipend == R.id.chipStipend150) {
                stipendMode = "min";
                minStipend = 150;
            } else if (checkedStipend == R.id.chipStipend200) {
                stipendMode = "min";
                minStipend = 200;
            }

            if (listener != null) {
                listener.onFilterApplied(minStipend);
            }
            if (advancedListener != null) {
                advancedListener.onAdvancedFilterApplied(sort, deadline, stipendMode, minStipend);
            }
            dismiss();
        });
    }
}
