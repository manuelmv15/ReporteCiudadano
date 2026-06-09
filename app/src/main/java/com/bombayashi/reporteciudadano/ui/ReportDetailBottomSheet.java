package com.bombayashi.reporteciudadano.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.databinding.BottomSheetReportDetailBinding;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.ui.vote.VoteState;
import com.bombayashi.reporteciudadano.ui.vote.VoteStateManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mapbox.geojson.Point;

// Imports que estaban faltando
import static com.bombayashi.reporteciudadano.ui.SnackbarHelper.*;

public class ReportDetailBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetReportDetailBinding binding;
    private ReportResponse.ReportData report;
    private Point userLocation;
    private VoteStateManager voteStateManager;
    private OnReportStatusChangeListener statusChangeListener;

    public interface OnReportStatusChangeListener {
        void onReportStatusChanged(int reportId, String newStatus, int confirmCount, int resolveCount);
    }

    public void setStatusChangeListener(OnReportStatusChangeListener listener) {
        this.statusChangeListener = listener;
    }

    public static ReportDetailBottomSheet newInstance(ReportResponse.ReportData report, Point userLocation) {
        ReportDetailBottomSheet fragment = new ReportDetailBottomSheet();
        fragment.report = report;
        fragment.userLocation = userLocation;
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

        if (report == null || userLocation == null) return;

        displayReportInfo();
        initializeVoteManager();
        setupVoteObservers();
        setupButtonListeners();
    }

    private void displayReportInfo() {
        String categoryName = (report.getCategory() != null) ? report.getCategory().getName() : "Sin categoría";
        String status = (report.getStatus() != null) ? report.getStatus().toUpperCase() : "DESCONOCIDO";
        String description = (report.getDescription() != null) ? report.getDescription() : "Sin descripción";
        String userName = (report.getUser() != null) ? report.getUser().getName() : "Anónimo";

        binding.tvCategory.setText(categoryName);
        binding.tvStatus.setText(status);
        binding.tvDescription.setText(description);
        binding.tvUser.setText("Reportado por: " + userName);
    }

    private void initializeVoteManager() {
        if (getContext() == null) return;

        android.util.Log.d("ReportDetailBS", "🔧 Inicializando VoteStateManager para reporteId=" + report.getId());
        voteStateManager = new VoteStateManager(getContext(), report, userLocation);
        // ⚠️ CRÍTICO: initialize() debe ser llamado para cargar el estado inicial
        voteStateManager.initialize();
        android.util.Log.d("ReportDetailBS", "✓ VoteStateManager inicializado");
    }

    private void setupVoteObservers() {
        if (voteStateManager == null) return;

        // Observar cambios en el estado de votación
        voteStateManager.getVoteState().observe(getViewLifecycleOwner(), this::updateVoteUI);

        // Observar eventos de votación
        voteStateManager.getVoteEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;

            switch (event.getType()) {
                case VOTE_SUCCESS:
                    android.util.Log.d("ReportDetailBS", "✓ Voto enviado correctamente");
                    // El UI se actualiza automáticamente a través del observer de VoteState
                    break;

                case CONFIRM_CHANGE_VOTE:
                    showChangeVoteDialog(event.getData());
                    break;

                case ERROR:
                    android.util.Log.e("ReportDetailBS", "❌ Error en voto: " + event.getData());

                    // Detectar voto duplicado (409)
                    if (event.getData().contains("409") || event.getData().contains("Ya votaste")) {
                        android.util.Log.d("ReportDetailBS", "🔄 Voto duplicado detectado, refrescando estado...");
                        // Recargar estado del reporte para mostrar que ya votó
                        if (voteStateManager != null) {
                            voteStateManager.refreshVoteState();
                        }
                        if (getView() != null) {
                            SnackbarHelper.show(
                                getView(),
                                "Ya has votado en este reporte",
                                SnackbarHelper.Variant.WARNING
                            );
                        }
                    } else {
                        // Otro error
                        if (getView() != null) {
                            SnackbarHelper.show(
                                getView(),
                                event.getData(),
                                SnackbarHelper.Variant.ERROR
                            );
                        }
                    }
                    break;
            }
        });

        // Observar cambios de estado del reporte
        voteStateManager.getReportStatusUpdate().observe(getViewLifecycleOwner(), statusUpdate -> {
            if (statusUpdate == null || statusChangeListener == null) return;

            android.util.Log.d("ReportDetailBS", "📊 Cambio de estado detectado: " + statusUpdate);
            statusChangeListener.onReportStatusChanged(
                statusUpdate.getReportId(),
                statusUpdate.getNewStatus().name(),
                statusUpdate.getConfirmCount(),
                statusUpdate.getResolveCount()
            );
        });
    }

    private void updateVoteUI(VoteState state) {
        if (state == null) return;

        // Actualizar conteo de votos
        binding.tvVoteCount.setText(state.getFormattedVoteCount());

        // Mostrar/ocultar aviso de distancia
        if (state.isWithinRadius()) {
            binding.llDistanceWarning.setVisibility(View.GONE);
            binding.btnConfirm.setEnabled(true);
            binding.btnResolve.setEnabled(true);
        } else {
            binding.llDistanceWarning.setVisibility(View.VISIBLE);
            binding.tvDistance.setText(
                String.format("Debes estar a 500m del reporte (Estás a %.0fm)", state.getUserDistance())
            );
            binding.btnConfirm.setEnabled(false);
            binding.btnResolve.setEnabled(false);
        }

        // Mostrar estado del voto del usuario
        if (state.hasUserVoted()) {
            binding.tvUserVoteStatus.setVisibility(View.VISIBLE);
            String voteText = state.getCurrentUserVoteType().equals("confirm")
                ? "Tu voto: Sigue ahí"
                : "Tu voto: Ya se resolvió";

            if (state.canEditVote()) {
                long secondsRemaining = state.getTimeRemainingToEdit() / 1000;
                voteText += " (Editable por " + secondsRemaining + "s)";
            } else if (state.getVoteEditableUntil() > 0) {
                voteText += " (No editable)";
            }

            binding.tvUserVoteStatus.setText(voteText);
        } else {
            binding.tvUserVoteStatus.setVisibility(View.GONE);
        }

        // Mostrar/ocultar loading spinner
        if (state.isLoading()) {
            binding.pbVoteLoading.setVisibility(View.VISIBLE);
            binding.btnConfirm.setEnabled(false);
            binding.btnResolve.setEnabled(false);
        } else {
            binding.pbVoteLoading.setVisibility(View.GONE);
            // Re-habilitar botones si están dentro de rango
            if (state.isWithinRadius()) {
                binding.btnConfirm.setEnabled(true);
                binding.btnResolve.setEnabled(true);
            }
        }

        // Cambiar estilo de botones según voto actual
        updateButtonStyles(state);
    }

    private void updateButtonStyles(VoteState state) {
        if (state.getCurrentUserVoteType() != null) {
            if (state.getCurrentUserVoteType().equals("confirm")) {
                // Botón "Sigue ahí" está votado
                binding.btnConfirm.setStrokeWidth(3);
                binding.btnConfirm.setStrokeColorResource(R.color.md_theme_primary);
                binding.btnResolve.setStrokeWidth(0);
            } else if (state.getCurrentUserVoteType().equals("resolve")) {
                // Botón "Ya se resolvió" está votado
                binding.btnResolve.setStrokeWidth(3);
                binding.btnResolve.setStrokeColorResource(R.color.md_theme_primary);
                binding.btnConfirm.setStrokeWidth(0);
            }
        } else {
            // Sin voto
            binding.btnConfirm.setStrokeWidth(0);
            binding.btnResolve.setStrokeWidth(0);
        }
    }

    private void setupButtonListeners() {
        binding.btnConfirm.setOnClickListener(v -> {
            android.util.Log.d("ReportDetailBS", "👆 Click en btnConfirm");
            if (voteStateManager != null) {
                android.util.Log.d("ReportDetailBS", "✓ VoteStateManager existe, llamando submitVote(confirm)");
                voteStateManager.submitVote("confirm");
            } else {
                android.util.Log.e("ReportDetailBS", "❌ voteStateManager es null");
            }
        });

        binding.btnResolve.setOnClickListener(v -> {
            android.util.Log.d("ReportDetailBS", "👆 Click en btnResolve");
            if (voteStateManager != null) {
                android.util.Log.d("ReportDetailBS", "✓ VoteStateManager existe, llamando submitVote(resolve)");
                voteStateManager.submitVote("resolve");
            } else {
                android.util.Log.e("ReportDetailBS", "❌ voteStateManager es null");
            }
        });
    }

    private void showChangeVoteDialog(String newVoteType) {
        VoteState currentState = voteStateManager.getVoteState().getValue();
        if (currentState == null) return;

        String currentVoteText = currentState.getCurrentUserVoteType().equals("confirm")
            ? "Sigue ahí"
            : "Ya se resolvió";

        String newVoteText = newVoteType.equals("confirm")
            ? "Sigue ahí"
            : "Ya se resolvió";

        new AlertDialog.Builder(requireContext())
            .setTitle("Cambiar voto")
            .setMessage("Tu voto actual: \"" + currentVoteText + "\"\n\n¿Cambiar a \"" + newVoteText + "\"?")
            .setPositiveButton("Cambiar", (dialog, which) -> {
                // Realizar el cambio de voto
                if (voteStateManager != null) {
                    voteStateManager.submitVote(newVoteType);
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
