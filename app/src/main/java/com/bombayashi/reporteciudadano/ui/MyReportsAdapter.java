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
            binding.ivCategoryIcon.setImageResource(getCategoryDrawableId(categorySlug));

            String status = report.getStatus() != null ? report.getStatus() : "pending";
            binding.tvStatus.setText(getStatusLabel(status));
            binding.tvStatus.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor(getStatusColor(status))));
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

        private String getStatusColor(String status) {
            return switch (status) {
                case "pending" -> "#FF9800";
                case "verified" -> "#4CAF50";
                case "resolved" -> "#2196F3";
                case "archived" -> "#9E9E9E";
                default -> "#757575";
            };
        }

        private int getCategoryDrawableId(String categorySlug) {
            return switch (categorySlug) {
                case "bache", "vialidad" -> R.drawable.remove_road_24px;
                case "alumbrado-publico", "alumbrado" -> R.drawable.backlight_high_off_24px;
                case "fuga-de-agua", "agua" -> R.drawable.agua;
                case "semaforo-danado", "trafico" -> R.drawable.traffic_jam_24px;
                case "inseguridad", "seguridad" -> R.drawable.warning_24px;
                case "basura-acumulada", "parques", "basura" -> R.drawable.trash;
                default -> R.drawable.ic_category_otros;
            };
        }
    }
}
