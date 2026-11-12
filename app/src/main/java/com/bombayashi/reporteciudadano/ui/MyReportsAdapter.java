package com.bombayashi.reporteciudadano.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.databinding.ItemMyReportBinding;
import com.bombayashi.reporteciudadano.model.ReportResponse;

import java.util.ArrayList;
import java.util.List;

public class MyReportsAdapter extends RecyclerView.Adapter<MyReportsAdapter.ViewHolder> {

    private final List<ReportResponse.ReportData> reports = new ArrayList<>();

    public void setReports(List<ReportResponse.ReportData> newReports) {
        reports.clear();
        if (newReports != null) reports.addAll(newReports);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMyReportBinding binding = ItemMyReportBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(reports.get(position));
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemMyReportBinding binding;

        ViewHolder(ItemMyReportBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ReportResponse.ReportData report) {
            String categorySlug = report.getCategory() != null ? report.getCategory().getSlug() : "";
            String categoryName = report.getCategory() != null ? report.getCategory().getName() : "Sin categoría";

            binding.tvCategory.setText(categoryName);
            binding.tvDescription.setText(
                    report.getDescription() != null ? report.getDescription() : "Sin descripción");
            binding.tvDate.setText(formatDate(report.getCreatedAt()));
            binding.tvConfirmCount.setText(String.valueOf(report.getVotesConfirm()));
            binding.tvResolveCount.setText(String.valueOf(report.getVotesResolve()));
            // Ícono en círculo con el color de la categoría, igual que en el menú radial y los pines
            binding.ivCategoryIcon.setImageResource(MarkerRenderer.getCategoryDrawableId(categorySlug));
            binding.ivCategoryIcon.setBackgroundTintList(ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(),
                            MarkerRenderer.getCategoryColor(categorySlug))));

            // Píldora tonal: texto en el color del estado y fondo del mismo color al ~15%
            String status = report.getStatus() != null ? report.getStatus() : "pending";
            int statusColor = getStatusColor(status);
            binding.tvStatus.setText(getStatusLabel(status));
            binding.tvStatus.setTextColor(statusColor);
            binding.tvStatus.setBackgroundTintList(
                    ColorStateList.valueOf((statusColor & 0x00FFFFFF) | 0x26000000));
        }

        private String formatDate(String createdAt) {
            if (createdAt == null || createdAt.length() < 10) return "";
            return createdAt.substring(0, 10);
        }

        private String getStatusLabel(String status) {
            return switch (status) {
                case "pending" -> "Pendiente";
                case "verified" -> "Verificado";
                case "resolved" -> "Resuelto";
                case "archived" -> "Archivado";
                default -> status;
            };
        }

        private int getStatusColor(String status) {
            int res = switch (status) {
                case "pending" -> R.color.status_pending;
                case "verified" -> R.color.status_verified;
                case "resolved" -> R.color.status_resolved;
                default -> R.color.status_archived;
            };
            return androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), res);
        }
    }
}
