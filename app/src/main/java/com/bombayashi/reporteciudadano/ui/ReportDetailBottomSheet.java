package com.bombayashi.reporteciudadano.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bombayashi.reporteciudadano.databinding.BottomSheetReportDetailBinding;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ReportDetailBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetReportDetailBinding binding;
    private ReportResponse.ReportData report;

    public static ReportDetailBottomSheet newInstance(ReportResponse.ReportData report) {
        ReportDetailBottomSheet fragment = new ReportDetailBottomSheet();
        fragment.report = report;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetReportDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (report != null) {
            String categoryName = (report.getCategory() != null) ? report.getCategory().getName() : "Sin categoría";
            String status = (report.getStatus() != null) ? report.getStatus().toUpperCase() : "DESCONOCIDO";
            String description = (report.getDescription() != null) ? report.getDescription() : "";
            String userName = (report.getUser() != null) ? report.getUser().getName() : "Anónimo";
            
            binding.tvCategory.setText(categoryName);
            binding.tvStatus.setText(status);
            binding.tvDescription.setText(description);
            binding.tvUser.setText("Reportado por: " + userName);

            if (report.getVotes() != null) {
                binding.btnConfirm.setText("Confirmar (" + report.getVotes().getConfirm() + ")");
                binding.btnResolve.setText("Resuelto (" + report.getVotes().getResolve() + ")");
            } else {
                binding.btnConfirm.setText("Confirmar (0)");
                binding.btnResolve.setText("Resuelto (0)");
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
