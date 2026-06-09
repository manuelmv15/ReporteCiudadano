package com.bombayashi.reporteciudadano.ui.vote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bombayashi.reporteciudadano.LoginActivity;
import com.bombayashi.reporteciudadano.model.ReportDetailResponse;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.model.VoteRequest;
import com.bombayashi.reporteciudadano.model.VoteResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.util.LocationUtil;
import com.mapbox.geojson.Point;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VoteStateManager {
    private static final String TAG = "VoteStateManager";
    private static final long VOTE_EDIT_WINDOW_MS = 5 * 60 * 1000;  // 5 minutos

    private ReportResponse.ReportData reportData;
    private Point userLocation;
    private SharedPreferences preferences;

    private MutableLiveData<VoteState> voteStateLiveData = new MutableLiveData<>();
    private MutableLiveData<VoteEvent> voteEventLiveData = new MutableLiveData<>();
    private MutableLiveData<ReportStatusUpdate> reportStatusUpdateLiveData = new MutableLiveData<>();

    public VoteStateManager(Context context, ReportResponse.ReportData reportData, Point userLocation) {
        this.reportData = reportData;
        this.userLocation = userLocation;
        // Usar el mismo SharedPreferences que LoginActivity
        this.preferences = context.getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE);

        initialize();
    }

    public LiveData<VoteState> getVoteState() {
        return voteStateLiveData;
    }

    public LiveData<VoteEvent> getVoteEvent() {
        return voteEventLiveData;
    }

    public LiveData<ReportStatusUpdate> getReportStatusUpdate() {
        return reportStatusUpdateLiveData;
    }

    private void initialize() {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "Inicializando VoteStateManager para reporte ID: " + reportData.getId());
        Log.d(TAG, "User: " + (reportData.getUser() != null ? reportData.getUser().getName() : "null"));
        Log.d(TAG, "User Location: " + userLocation.latitude() + ", " + userLocation.longitude());
        Log.d(TAG, "Report Location: " + reportData.getLatitude() + ", " + reportData.getLongitude());

        // Calcular distancia
        double distance = LocationUtil.calculateDistance(
            userLocation.latitude(),
            userLocation.longitude(),
            reportData.getLatitude(),
            reportData.getLongitude()
        );

        Log.d(TAG, "📍 Distancia calculada: " + String.format("%.2f", distance) + "m");

        boolean isWithinRadius = distance <= LocationUtil.VOTE_RADIUS_METERS;
        Log.d(TAG, "✓ Dentro de radio (500m): " + isWithinRadius);

        // Calcular timestamp de edición
        long voteEditableUntil = 0;
        if (reportData.getUserVotedAt() != null) {
            // Parse timestamp y agregar 5 minutos
            // Por ahora asumimos que el servidor nos da el timestamp en ms
            voteEditableUntil = System.currentTimeMillis() + VOTE_EDIT_WINDOW_MS;
        }

        VoteState state = new VoteState.Builder()
            .currentUserVoteType(reportData.getUserVote())
            .confirmCount(reportData.getVotes() != null ? reportData.getVotes().getConfirm() : 0)
            .resolveCount(reportData.getVotes() != null ? reportData.getVotes().getResolve() : 0)
            .isLoading(false)
            .voteEditableUntil(voteEditableUntil)
            .isWithinRadius(isWithinRadius)
            .userDistance(distance)
            .build();

        Log.d(TAG, "Estado inicial: " + state);
        voteStateLiveData.setValue(state);
    }

    public void submitVote(String voteType) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "🗳️ submitVote() llamado con tipo: " + voteType);

        VoteState currentState = voteStateLiveData.getValue();
        if (currentState == null) {
            Log.e(TAG, "❌ currentState es null");
            return;
        }

        Log.d(TAG, "✓ currentState existe");
        Log.d(TAG, "  - isWithinRadius: " + currentState.isWithinRadius());
        Log.d(TAG, "  - userDistance: " + String.format("%.2f", currentState.getUserDistance()) + "m");

        if (!currentState.isWithinRadius()) {
            Log.w(TAG, "⚠️ Usuario fuera de rango (distancia: " + String.format("%.2f", currentState.getUserDistance()) + "m)");
            voteEventLiveData.setValue(
                new VoteEvent(VoteEvent.Type.ERROR,
                    "Debes estar a menos de 500m del reporte para votar")
            );
            return;
        }

        // Obtener token guardado usando LoginActivity.PREFS_NAME y LoginActivity.KEY_TOKEN
        String token = preferences.getString(LoginActivity.KEY_TOKEN, "");
        Log.d(TAG, "🔑 Token obtenido: " + (token.isEmpty() ? "VACÍO" : "✓ " + token.substring(0, Math.min(20, token.length())) + "..."));
        Log.d(TAG, "   - SharedPreferences: " + LoginActivity.PREFS_NAME);
        Log.d(TAG, "   - Key: " + LoginActivity.KEY_TOKEN);

        if (token.isEmpty()) {
            Log.e(TAG, "❌ Token no encontrado en SharedPreferences");
            voteEventLiveData.setValue(
                new VoteEvent(VoteEvent.Type.ERROR, "Debes iniciar sesión para votar")
            );
            return;
        }

        Log.d(TAG, "✓ Token encontrado, procediendo con voto");

        // Si el usuario ya votó este tipo, mostrar diálogo de confirmación
        if (currentState.getCurrentUserVoteType() != null &&
            currentState.getCurrentUserVoteType().equals(voteType)) {
            Log.d(TAG, "⚠️ Usuario ya votó este tipo, mostrar diálogo de confirmación");
            voteEventLiveData.setValue(
                new VoteEvent(VoteEvent.Type.CONFIRM_CHANGE_VOTE, voteType)
            );
            return;
        }

        // Si votó algo diferente, deletear el voto anterior primero
        if (currentState.getCurrentUserVoteType() != null) {
            Log.d(TAG, "🔄 Usuario votó diferente, cambiar de " + currentState.getCurrentUserVoteType() + " a " + voteType);
            deleteVoteThenSubmit(currentState.getCurrentUserVoteType(), voteType, token);
        } else {
            Log.d(TAG, "➕ Nuevo voto");
            submitVoteInternal(voteType, token);
        }
    }

    private void deleteVoteThenSubmit(String oldVoteType, String newVoteType, String token) {
        VoteState state = voteStateLiveData.getValue();
        if (state == null) return;

        VoteState loadingState = new VoteState.Builder()
            .currentUserVoteType(state.getCurrentUserVoteType())
            .confirmCount(state.getConfirmCount())
            .resolveCount(state.getResolveCount())
            .isLoading(true)
            .isWithinRadius(state.isWithinRadius())
            .userDistance(state.getUserDistance())
            .build();
        voteStateLiveData.setValue(loadingState);

        ApiClient.getInstance()
            .deleteVote(reportData.getId(), oldVoteType, "Bearer " + token)
            .enqueue(new Callback<VoteResponse>() {
                @Override
                public void onResponse(Call<VoteResponse> call, Response<VoteResponse> response) {
                    if (response.isSuccessful()) {
                        // Proceder a votar el nuevo tipo
                        submitVoteInternal(newVoteType, token);
                    } else {
                        handleVoteError("Error al cambiar voto: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<VoteResponse> call, Throwable t) {
                    handleVoteError("Error de conexión: " + t.getMessage());
                }
            });
    }

    private void submitVoteInternal(String voteType, String token) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "📤 submitVoteInternal()");

        VoteState state = voteStateLiveData.getValue();
        if (state == null) {
            Log.e(TAG, "❌ state es null");
            return;
        }

        VoteState loadingState = new VoteState.Builder()
            .currentUserVoteType(state.getCurrentUserVoteType())
            .confirmCount(state.getConfirmCount())
            .resolveCount(state.getResolveCount())
            .isLoading(true)
            .isWithinRadius(state.isWithinRadius())
            .userDistance(state.getUserDistance())
            .build();
        voteStateLiveData.setValue(loadingState);
        Log.d(TAG, "✓ Loading state activado");

        VoteRequest request = new VoteRequest(
            voteType,
            userLocation.latitude(),
            userLocation.longitude()
        );

        Log.d(TAG, "📝 VoteRequest creado:");
        Log.d(TAG, "  - type: " + voteType);
        Log.d(TAG, "  - lat: " + userLocation.latitude());
        Log.d(TAG, "  - lng: " + userLocation.longitude());
        Log.d(TAG, "  - reportId: " + reportData.getId());
        Log.d(TAG, "  - token (primeros 20 chars): Bearer " + token.substring(0, Math.min(20, token.length())) + "...");

        Log.d(TAG, "🌐 Llamando a API.submitVote()...");

        ApiClient.getInstance()
            .submitVote(reportData.getId(), "Bearer " + token, request)
            .enqueue(new Callback<VoteResponse>() {
                @Override
                public void onResponse(Call<VoteResponse> call, Response<VoteResponse> response) {
                    Log.d(TAG, "═══════════════════════════════════════════");
                    Log.d(TAG, "📥 onResponse() - Status: " + response.code());

                    if (response.isSuccessful() && response.body() != null) {
                        VoteResponse voteResponse = response.body();
                        Log.d(TAG, "✓ Response exitoso");
                        Log.d(TAG, "  - success: " + voteResponse.isSuccess());
                        Log.d(TAG, "  - message: " + voteResponse.getMessage());
                        Log.d(TAG, "  - data: " + (voteResponse.getData() != null ? "✓ existe" : "null (se construirá desde raíz)"));

                        if (voteResponse.isSuccess()) {
                            Log.d(TAG, "✅ Voto aceptado por servidor");
                            handleVoteSuccess(voteResponse);
                        } else {
                            Log.e(TAG, "❌ success=false en response");
                            handleVoteError(voteResponse.getMessage());
                        }
                    } else {
                        String errorBody = "desconocido";
                        try {
                            errorBody = response.errorBody() != null ? response.errorBody().string() : "vacío";
                        } catch (Exception e) {
                            Log.e(TAG, "Error leyendo errorBody", e);
                        }
                        Log.e(TAG, "❌ Response no exitoso");
                        Log.e(TAG, "  - code: " + response.code());
                        Log.e(TAG, "  - errorBody: " + errorBody);
                        handleVoteError("Error del servidor: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<VoteResponse> call, Throwable t) {
                    Log.e(TAG, "═══════════════════════════════════════════");
                    Log.e(TAG, "❌ onFailure() - " + t.getMessage(), t);
                    handleVoteError("Error de conexión: " + t.getMessage());
                }
            });
    }

    private void handleVoteSuccess(VoteResponse response) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "✅ handleVoteSuccess()");
        Log.d(TAG, "⚠️ La API no retorna datos de votos, recargar reporte para obtener conteos actualizados...");

        // La API retorna solo {success, message} sin datos de votos
        // Necesitamos recargar el reporte para obtener los conteos actualizados
        refreshReportVotes();
    }

    private void refreshReportVotes() {
        Log.d(TAG, "🔄 refreshReportVotes() - Obteniendo conteos actualizados del servidor...");

        ApiClient.getInstance()
            .getReportDetail(reportData.getId())
            .enqueue(new Callback<ReportDetailResponse>() {
                @Override
                public void onResponse(Call<ReportDetailResponse> call, Response<ReportDetailResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        ReportResponse.ReportData updatedReport = response.body().getReport();
                        if (updatedReport != null && updatedReport.getVotes() != null) {
                            Log.d(TAG, "✓ Reporte actualizado obtenido:");
                            Log.d(TAG, "  - confirm: " + updatedReport.getVotes().getConfirm());
                            Log.d(TAG, "  - resolve: " + updatedReport.getVotes().getResolve());
                            Log.d(TAG, "  - userVote: " + updatedReport.getUserVote());

                            long voteEditableUntil = System.currentTimeMillis() + VOTE_EDIT_WINDOW_MS;

                            VoteState currentState = voteStateLiveData.getValue();
                            int newConfirmCount = updatedReport.getVotes().getConfirm();
                            int newResolveCount = updatedReport.getVotes().getResolve();

                            // Verificar cambio de estado
                            ReportStatusUpdate.Status newStatus = determineStatus(newConfirmCount, newResolveCount);
                            Log.d(TAG, "📊 Estado determinado: " + newStatus);

                            VoteState newState = new VoteState.Builder()
                                .currentUserVoteType(updatedReport.getUserVote())
                                .confirmCount(newConfirmCount)
                                .resolveCount(newResolveCount)
                                .isLoading(false)
                                .voteEditableUntil(voteEditableUntil)
                                .isWithinRadius(currentState != null && currentState.isWithinRadius())
                                .userDistance(currentState != null ? currentState.getUserDistance() : 0)
                                .build();

                            Log.d(TAG, "✓ Nuevo VoteState actualizado: " + newState);
                            voteStateLiveData.setValue(newState);
                            voteEventLiveData.setValue(new VoteEvent(VoteEvent.Type.VOTE_SUCCESS, updatedReport.getUserVote()));

                            // Notificar cambio de estado al mapa
                            ReportStatusUpdate statusUpdate = new ReportStatusUpdate(
                                reportData.getId(),
                                newStatus,
                                newConfirmCount,
                                newResolveCount
                            );
                            reportStatusUpdateLiveData.setValue(statusUpdate);
                            Log.d(TAG, "📢 ReportStatusUpdate emitido: " + statusUpdate);
                        }
                    } else {
                        Log.e(TAG, "❌ Error refrescando reporte: " + response.code());
                        handleVoteError("Error al actualizar votos");
                    }
                }

                @Override
                public void onFailure(Call<ReportDetailResponse> call, Throwable t) {
                    Log.e(TAG, "❌ Error de conexión al refrescar reporte: " + t.getMessage(), t);
                    handleVoteError("Error de conexión");
                }
            });
    }

    private ReportStatusUpdate.Status determineStatus(int confirmCount, int resolveCount) {
        int totalVotes = confirmCount + resolveCount;

        // Verificado: 5+ votos "Sigue ahí"
        if (confirmCount >= 5) {
            return ReportStatusUpdate.Status.VERIFIED;
        }

        // Resuelto: 70%+ votos "Ya se resolvió" (mínimo 3 votos totales)
        if (totalVotes >= 3 && (resolveCount / (double) totalVotes) >= 0.7) {
            return ReportStatusUpdate.Status.RESOLVED;
        }

        return ReportStatusUpdate.Status.PENDING;
    }

    private void handleVoteError(String errorMessage) {
        Log.e(TAG, "═══════════════════════════════════════════");
        Log.e(TAG, "❌ handleVoteError(): " + errorMessage);

        VoteState state = voteStateLiveData.getValue();
        if (state == null) return;

        VoteState errorState = new VoteState.Builder()
            .currentUserVoteType(state.getCurrentUserVoteType())
            .confirmCount(state.getConfirmCount())
            .resolveCount(state.getResolveCount())
            .isLoading(false)
            .error(errorMessage)
            .isWithinRadius(state.isWithinRadius())
            .userDistance(state.getUserDistance())
            .build();

        voteStateLiveData.setValue(errorState);
        voteEventLiveData.setValue(new VoteEvent(VoteEvent.Type.ERROR, errorMessage));
    }

    public void updateCounts(int confirmCount, int resolveCount) {
        VoteState state = voteStateLiveData.getValue();
        if (state == null) return;

        VoteState updated = new VoteState.Builder()
            .currentUserVoteType(state.getCurrentUserVoteType())
            .confirmCount(confirmCount)
            .resolveCount(resolveCount)
            .isLoading(state.isLoading())
            .error(state.getError())
            .voteEditableUntil(state.getVoteEditableUntil())
            .isWithinRadius(state.isWithinRadius())
            .userDistance(state.getUserDistance())
            .build();

        voteStateLiveData.setValue(updated);
    }

    public void setLoading(boolean loading) {
        VoteState state = voteStateLiveData.getValue();
        if (state == null) return;

        VoteState updated = new VoteState.Builder()
            .currentUserVoteType(state.getCurrentUserVoteType())
            .confirmCount(state.getConfirmCount())
            .resolveCount(state.getResolveCount())
            .isLoading(loading)
            .error(state.getError())
            .voteEditableUntil(state.getVoteEditableUntil())
            .isWithinRadius(state.isWithinRadius())
            .userDistance(state.getUserDistance())
            .build();

        voteStateLiveData.setValue(updated);
    }

    // Evento para comunicar cambios a la UI
    public static class VoteEvent {
        public enum Type {
            VOTE_SUCCESS,
            CONFIRM_CHANGE_VOTE,
            ERROR
        }

        private Type type;
        private String data;

        public VoteEvent(Type type, String data) {
            this.type = type;
            this.data = data;
        }

        public Type getType() { return type; }
        public String getData() { return data; }
    }

    // Evento para notificar cambios de estado del reporte al mapa
    public static class ReportStatusUpdate {
        public enum Status {
            PENDING,      // Sin cambios
            VERIFIED,     // 5+ votos "Sigue ahí"
            RESOLVED      // 70%+ votos "Ya se resolvió"
        }

        private int reportId;
        private Status newStatus;
        private int confirmCount;
        private int resolveCount;

        public ReportStatusUpdate(int reportId, Status newStatus, int confirmCount, int resolveCount) {
            this.reportId = reportId;
            this.newStatus = newStatus;
            this.confirmCount = confirmCount;
            this.resolveCount = resolveCount;
        }

        public int getReportId() { return reportId; }
        public Status getNewStatus() { return newStatus; }
        public int getConfirmCount() { return confirmCount; }
        public int getResolveCount() { return resolveCount; }

        @Override
        public String toString() {
            return "ReportStatusUpdate{" +
                    "reportId=" + reportId +
                    ", newStatus=" + newStatus +
                    ", confirm=" + confirmCount +
                    ", resolve=" + resolveCount +
                    '}';
        }
    }
}
