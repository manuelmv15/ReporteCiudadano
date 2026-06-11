package com.bombayashi.reporteciudadano.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.databinding.ItemMyVoteBinding;
import com.bombayashi.reporteciudadano.model.MyVotesResponse;

import java.util.ArrayList;
import java.util.List;

public class MyVotesAdapter extends RecyclerView.Adapter<MyVotesAdapter.ViewHolder> {

    private final List<MyVotesResponse.VoteData> votes = new ArrayList<>();

    public void setVotes(List<MyVotesResponse.VoteData> newVotes) {
        votes.clear();
        if (newVotes != null) votes.addAll(newVotes);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMyVoteBinding binding = ItemMyVoteBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(votes.get(position));
    }

    @Override
    public int getItemCount() {
        return votes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemMyVoteBinding binding;

        ViewHolder(ItemMyVoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MyVotesResponse.VoteData vote) {
            boolean isConfirm = "confirm".equals(vote.getType());
            binding.tvVoteType.setText(isConfirm ? "Sigue ahí" : "Ya se resolvió");
            binding.ivVoteType.setImageResource(isConfirm ? R.drawable.ic_thumb_up : R.drawable.ic_check);

            MyVotesResponse.ReportInfo report = vote.getReport();
            String description = (report != null && report.getDescription() != null)
                    ? report.getDescription() : "Sin descripción";
            binding.tvVoteReportDescription.setText(description);

            binding.tvVoteDate.setText(formatDate(vote.getCreatedAt()));

            Boolean correct = vote.isCorrect();
            if (correct == null) {
                binding.tvVoteAccuracy.setText("Pendiente");
                binding.tvVoteAccuracy.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9E9E9E")));
            } else if (correct) {
                binding.tvVoteAccuracy.setText("Acertado");
                binding.tvVoteAccuracy.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            } else {
                binding.tvVoteAccuracy.setText("Errado");
                binding.tvVoteAccuracy.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336")));
            }
        }

        private String formatDate(String createdAt) {
            if (createdAt == null || createdAt.length() < 10) return "";
            return createdAt.substring(0, 10);
        }
    }
}
