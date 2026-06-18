package com.bombayashi.reporteciudadano.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.bombayashi.reporteciudadano.databinding.BottomSheetReportDetailBinding;
import com.bombayashi.reporteciudadano.model.CreateReportResponse;
import com.bombayashi.reporteciudadano.model.ReportDetailResponse;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.ui.vote.VoteState;
import com.bombayashi.reporteciudadano.ui.vote.VoteStateManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mapbox.geojson.Point;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.bombayashi.reporteciudadano.ui.SnackbarHelper.*;

public class ReportDetailBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetReportDetailBinding binding;
    private ReportResponse.ReportData report;
    private Point userLocation;
    private VoteStateManager voteStateManager;
    private OnReportStatusChangeListener statusChangeListener;
    private boolean isOwner = false;
    private TokenManager tokenManager;
    private Uri cameraPhotoUri;

    private final ActivityResultLauncher<String> galleryLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadPhoto(uri);
        });

    private final ActivityResultLauncher<Uri> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (Boolean.TRUE.equals(success) && cameraPhotoUri != null) uploadPhoto(cameraPhotoUri);
        });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) launchCamera();
            else if (getView() != null)
                SnackbarHelper.show(getView(), "Permiso de cámara denegado", SnackbarHelper.Variant.WARNING);
        });

    public interface OnReportStatusChangeListener {
        void onReportStatusChanged(int reportId, String newStatus, int confirmCount, int resolveCount);
        void onReportDataUpdated(ReportResponse.ReportData updatedReport);
        void onReportRetracted(int reportId);
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

        // FETCH PREVENTIVO: Antes de mostrar, pedimos los datos más recientes de la API
        fetchLatestReportData();

        displayReportInfo();
        setupOwnerControls();

        if (isGuest()) {
            setupGuestMode();
        } else {
            initializeVoteManager();
            setupVoteObservers();
            setupButtonListeners();
        }
    }

    private void fetchLatestReportData() {
        ApiClient.getInstance().getReportDetail(report.getId())
            .enqueue(new Callback<ReportDetailResponse>() {
                @Override
                public void onResponse(Call<ReportDetailResponse> call, Response<ReportDetailResponse> response) {
                    if (!isAdded() || getView() == null) return;
                    if (response.isSuccessful() && response.body() != null) {
                        ReportResponse.ReportData updated = response.body().getReport();
                        if (updated != null) {
                            // Actualizamos el objeto local
                            report = updated;
                            // Refrescamos la UI con los datos reales de la API
                            displayReportInfo();

                            // SINCRO: Notificar al mapa para que guarde esta info fresca
                            if (statusChangeListener != null) {
                                statusChangeListener.onReportDataUpdated(updated);
                            }

                            if (voteStateManager != null) {
                                // Si ya se inicializó, le pasamos los nuevos conteos
                                voteStateManager.updateCounts(
                                    updated.getVotes().getConfirm(),
                                    updated.getVotes().getResolve()
                                );
                            }
                        }
                    }
                }

                @Override
                public void onFailure(Call<ReportDetailResponse> call, Throwable t) {
                    // Si falla el fetch, seguimos con los datos cacheados
                }
            });
    }

    private boolean isGuest() {
        if (getContext() == null) return true;
        return !TokenManager.getInstance(getContext()).isLoggedIn();
    }

    private void setupGuestMode() {
        binding.llVoteButtons.setVisibility(View.GONE);
        binding.llDistanceWarning.setVisibility(View.GONE);
        binding.tvUserVoteStatus.setVisibility(View.GONE);
        binding.pbVoteLoading.setVisibility(View.GONE);
        binding.btnLoginToVote.setVisibility(View.VISIBLE);
        binding.btnLoginToVote.setOnClickListener(v -> {
            dismiss();
            startActivity(new android.content.Intent(requireContext(), com.bombayashi.reporteciudadano.LoginActivity.class));
        });
    }

    private void displayReportInfo() {
        String categoryName = (report.getCategory() != null) ? report.getCategory().getName() : "Sin categoría";
        String status = (report.getStatus() != null) ? report.getStatus().toUpperCase() : "DESCONOCIDO";
        String description = (report.getDescription() != null) ? report.getDescription() : "Sin descripción";
        String userName = (report.getUser() != null) ? report.getUser().getName() : "Anónimo";

        binding.tvCategory.setText(categoryName);
        binding.tvStatus.setText(status);
        int statusColor = switch (report.getStatus() != null ? report.getStatus() : "") {
            case "verified"  -> android.graphics.Color.parseColor("#4CAF50");
            case "resolved"  -> android.graphics.Color.parseColor("#2196F3");
            case "archived"  -> android.graphics.Color.parseColor("#9E9E9E");
            default          -> android.graphics.Color.parseColor("#FF9800"); // pending
        };
        binding.tvStatus.setTextColor(statusColor);
        binding.tvDescription.setText(description);
        binding.tvUser.setText("Reportado por: " + userName);
    }

    private void setupOwnerControls() {
        if (getContext() == null) return;
        tokenManager = TokenManager.getInstance(getContext());
        int currentUserId = tokenManager.getUserId();

        if (currentUserId != -1 && currentUserId == report.getUserId()) {
            isOwner = true;
            binding.btnEditDescription.setVisibility(View.VISIBLE);
            binding.btnEditDescription.setOnClickListener(v -> showEditDescriptionDialog());
            binding.llPhotoButtons.setVisibility(View.VISIBLE);
            binding.btnTakePhoto.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    launchCamera();
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                }
            });
            binding.btnPickPhoto.setOnClickListener(v -> galleryLauncher.launch("image/*"));
            setupRetractButton();
            // RF-14: Owner puede votar "Ya se resolvió" pero no "Sigue ahí"
            binding.btnConfirm.setVisibility(View.GONE);
        }

        // Mostrar foto actual si existe
        String existingPhotoUrl = report.getPhotoUrl();
        if (existingPhotoUrl != null) {
            binding.ivReportPhoto.setVisibility(View.VISIBLE);
            Glide.with(this).load(existingPhotoUrl).into(binding.ivReportPhoto);
            binding.ivReportPhoto.setOnClickListener(v -> showFullscreenPhoto(existingPhotoUrl));
        }
    }

    private void setupRetractButton() {
        if (!canRetract()) return;
        binding.btnRetractReport.setVisibility(View.VISIBLE);
        binding.btnRetractReport.setOnClickListener(v ->
            new AlertDialog.Builder(requireContext())
                .setTitle("Retirar reporte")
                .setMessage("¿Seguro que querés retirar este reporte? Esta acción no se puede deshacer.")
                .setPositiveButton("Retirar", (dialog, which) -> retractReport())
                .setNegativeButton("Cancelar", null)
                .show()
        );
    }

    private boolean canRetract() {
        if (report.getVotesConfirm() + report.getVotesResolve() >= 3) return false;

        String createdAt = report.getCreatedAt();
        if (createdAt == null) return false;
        try {
            java.time.Instant createdInstant = java.time.Instant.parse(createdAt);
            java.time.Duration age = java.time.Duration.between(createdInstant, java.time.Instant.now());
            return age.toMinutes() < 5;
        } catch (Exception e) {
            return false;
        }
    }

    private void retractReport() {
        if (tokenManager == null || getContext() == null) return;

        if (!com.bombayashi.reporteciudadano.util.ConnectivityHelper.isOnline(getContext())) {
            queueOfflineRetract();
            return;
        }

        String token = "Bearer " + tokenManager.getToken();
        binding.pbVoteLoading.setVisibility(View.VISIBLE);
        ApiClient.getInstance().deleteReport(report.getId(), token)
            .enqueue(new Callback<com.bombayashi.reporteciudadano.model.SimpleResponse>() {
                @Override
                public void onResponse(Call<com.bombayashi.reporteciudadano.model.SimpleResponse> call, Response<com.bombayashi.reporteciudadano.model.SimpleResponse> response) {
                    if (!isAdded() || getView() == null) return;
                    binding.pbVoteLoading.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        SnackbarHelper.show(getView(), "Reporte retirado", SnackbarHelper.Variant.SUCCESS);
                        if (statusChangeListener != null) {
                            statusChangeListener.onReportRetracted(report.getId());
                        }
                        dismiss();
                    } else if (response.code() == 401) {
                        clearTokenAndGoToLogin();
                    } else {
                        String msg = "No se pudo retirar el reporte";
                        if (response.errorBody() != null) {
                            try {
                                org.json.JSONObject err = new org.json.JSONObject(response.errorBody().string());
                                msg = err.optString("message", msg);
                            } catch (Exception ignored) {}
                        }
                        SnackbarHelper.show(getView(), msg, SnackbarHelper.Variant.ERROR);
                    }
                }

                @Override
                public void onFailure(Call<com.bombayashi.reporteciudadano.model.SimpleResponse> call, Throwable t) {
                    if (!isAdded() || getView() == null) return;
                    binding.pbVoteLoading.setVisibility(View.GONE);
                    SnackbarHelper.show(getView(), "Error de red", SnackbarHelper.Variant.ERROR);
                }
            });
    }

    /** RF-05: sin conexión, encola el retiro del reporte para enviarlo cuando vuelva la red. */
    private void queueOfflineRetract() {
        org.json.JSONObject payload = new org.json.JSONObject();
        try {
            payload.put("report_id", report.getId());
        } catch (org.json.JSONException e) {
            return;
        }

        com.bombayashi.reporteciudadano.db.AppDatabase db =
            com.bombayashi.reporteciudadano.db.AppDatabase.getInstance(requireContext());
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() ->
            db.pendingActionDao().insert(new com.bombayashi.reporteciudadano.db.PendingActionEntity(
                com.bombayashi.reporteciudadano.db.PendingActionEntity.TYPE_RETRACT_REPORT,
                payload.toString(),
                System.currentTimeMillis()
            ))
        );

        if (getView() != null) {
            SnackbarHelper.show(getView(), "Sin conexión: el reporte se retirará cuando vuelva la conexión",
                SnackbarHelper.Variant.INFO);
        }
        if (statusChangeListener != null) {
            statusChangeListener.onReportRetracted(report.getId());
        }
        dismiss();
    }

    private void showFullscreenPhoto(String url) {
        if (getContext() == null) return;
        android.widget.ImageView iv = new android.widget.ImageView(getContext());
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(0xFF000000);
        Glide.with(this).load(url).into(iv);

        android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(iv);
        iv.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditDescriptionDialog() {
        EditText input = new EditText(requireContext());
        input.setText(report.getDescription());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireContext())
            .setTitle("Editar descripción")
            .setView(input)
            .setPositiveButton("Guardar", (dialog, which) -> {
                String newDesc = input.getText().toString().trim();
                if (!newDesc.isEmpty()) {
                    saveDescription(newDesc);
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void clearTokenAndGoToLogin() {
        if (getContext() == null) return;
        TokenManager.getInstance(getContext()).clearAuth();
        dismiss();
        startActivity(new android.content.Intent(requireContext(), com.bombayashi.reporteciudadano.LoginActivity.class));
    }

    private void saveDescription(String newDesc) {
        String token = "Bearer " + tokenManager.getToken();
        RequestBody descBody = RequestBody.create(newDesc, MediaType.parse("text/plain"));
        ApiClient.getInstance().updateReport(report.getId(), token, descBody, null)
            .enqueue(new Callback<CreateReportResponse>() {
                @Override
                public void onResponse(Call<CreateReportResponse> call, Response<CreateReportResponse> response) {
                    if (!isAdded() || getView() == null) return;
                    if (response.isSuccessful()) {
                        report.setDescription(newDesc);
                        binding.tvDescription.setText(newDesc);
                        SnackbarHelper.show(getView(), "Descripción actualizada", SnackbarHelper.Variant.SUCCESS);
                    } else if (response.code() == 401) {
                        clearTokenAndGoToLogin();
                    } else {
                        SnackbarHelper.show(getView(), "Error al guardar: " + response.code(), SnackbarHelper.Variant.ERROR);
                    }
                }

                @Override
                public void onFailure(Call<CreateReportResponse> call, Throwable t) {
                    if (!isAdded() || getView() == null) return;
                    SnackbarHelper.show(getView(), "Error de red", SnackbarHelper.Variant.ERROR);
                }
            });
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

                case OFFLINE_QUEUED:
                    if (getView() != null) {
                        SnackbarHelper.show(getView(), event.getData(), SnackbarHelper.Variant.INFO);
                    }
                    break;

                case ERROR:
                    android.util.Log.e("ReportDetailBS", "❌ Error en voto: " + event.getData());

                    if (event.getData().contains("401")) {
                        clearTokenAndGoToLogin();
                    } else if (event.getData().contains("409") || event.getData().contains("Ya votaste")) {
                        android.util.Log.d("ReportDetailBS", "🔄 Voto duplicado detectado, refrescando estado...");
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

        binding.tvVoteCount.setText(state.getFormattedVoteCount());

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

        if (state.isLoading()) {
            binding.pbVoteLoading.setVisibility(View.VISIBLE);
            binding.btnConfirm.setEnabled(false);
            binding.btnResolve.setEnabled(false);
        } else {
            binding.pbVoteLoading.setVisibility(View.GONE);
            if (state.isWithinRadius()) {
                binding.btnConfirm.setEnabled(true);
                binding.btnResolve.setEnabled(true);
            }
        }

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

    private void launchCamera() {
        if (getContext() == null) return;
        try {
            File photoFile = File.createTempFile("report_photo", ".jpg", getContext().getCacheDir());
            cameraPhotoUri = FileProvider.getUriForFile(
                getContext(),
                getContext().getPackageName() + ".fileprovider",
                photoFile
            );
            cameraLauncher.launch(cameraPhotoUri);
        } catch (Exception e) {
            if (getView() != null)
                SnackbarHelper.show(getView(), "Error al iniciar cámara", SnackbarHelper.Variant.ERROR);
        }
    }

    private void uploadPhoto(Uri uri) {
        if (getContext() == null || tokenManager == null) return;
        try {
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            if (is == null) return;
            File tmpFile = File.createTempFile("report_photo", ".jpg", getContext().getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
            }
            is.close();

            RequestBody reqBody = RequestBody.create(tmpFile, MediaType.parse("image/jpeg"));
            MultipartBody.Part photoPart = MultipartBody.Part.createFormData("photo", tmpFile.getName(), reqBody);
            String token = "Bearer " + tokenManager.getToken();

            String currentDesc = report.getDescription() != null ? report.getDescription() : "";
            RequestBody descBody = RequestBody.create(currentDesc, MediaType.parse("text/plain"));

            binding.pbVoteLoading.setVisibility(View.VISIBLE);
            ApiClient.getInstance().updateReport(report.getId(), token, descBody, photoPart)
                .enqueue(new Callback<CreateReportResponse>() {
                    @Override
                    public void onResponse(Call<CreateReportResponse> call, Response<CreateReportResponse> response) {
                        if (!isAdded() || getView() == null) return;
                        binding.pbVoteLoading.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            // Actualizar objeto en memoria para próximas aperturas
                            if (response.body().getReport() != null && response.body().getReport().getPhoto() != null) {
                                report.setPhoto(response.body().getReport().getPhoto());
                            }
                            binding.ivReportPhoto.setVisibility(View.VISIBLE);
                            String photoUrl = report.getPhotoUrl();
                            Object photoSrc = photoUrl != null ? photoUrl : uri;
                            Glide.with(ReportDetailBottomSheet.this)
                                .load(photoSrc)
                                .into(binding.ivReportPhoto);
                            binding.ivReportPhoto.setOnClickListener(v ->
                                showFullscreenPhoto(photoUrl != null ? photoUrl : uri.toString()));
                            SnackbarHelper.show(getView(), "Foto subida correctamente", SnackbarHelper.Variant.SUCCESS);
                        } else if (response.code() == 401) {
                            clearTokenAndGoToLogin();
                        } else {
                            SnackbarHelper.show(getView(), "Error al subir foto: " + response.code(), SnackbarHelper.Variant.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(Call<CreateReportResponse> call, Throwable t) {
                        if (!isAdded() || getView() == null) return;
                        binding.pbVoteLoading.setVisibility(View.GONE);
                        SnackbarHelper.show(getView(), "Error de red", SnackbarHelper.Variant.ERROR);
                    }
                });
        } catch (Exception e) {
            if (getView() != null) {
                SnackbarHelper.show(getView(), "Error al procesar imagen", SnackbarHelper.Variant.ERROR);
            }
        }
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
        if (voteStateManager != null) {
            voteStateManager.destroy();
        }
        binding = null;
    }
}
