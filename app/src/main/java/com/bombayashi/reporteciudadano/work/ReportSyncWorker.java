package com.bombayashi.reporteciudadano.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;
import androidx.work.Worker;

import com.bombayashi.reporteciudadano.db.AppDatabase;
import com.bombayashi.reporteciudadano.db.PendingActionDao;
import com.bombayashi.reporteciudadano.db.PendingActionEntity;
import com.bombayashi.reporteciudadano.model.ReportRequest;
import com.bombayashi.reporteciudadano.model.VoteRequest;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.util.TokenManager;

import org.json.JSONObject;

import retrofit2.Response;

/**
 * Procesa la cola de {@link PendingActionEntity} (creación de reportes, votos, retiros)
 * cuando hay conexión disponible. Encolado con constraint NetworkType.CONNECTED.
 */
public class ReportSyncWorker extends Worker {

    private static final int MAX_RETRIES = 5;

    public ReportSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String token = "Bearer " + TokenManager.getInstance(context).getToken();
        AppDatabase db = AppDatabase.getInstance(context);
        PendingActionDao dao = db.pendingActionDao();

        boolean anyFailedDueToNetwork = false;

        for (PendingActionEntity action : dao.getPending()) {
            try {
                boolean handled = switch (action.type) {
                    case PendingActionEntity.TYPE_CREATE_REPORT -> syncCreateReport(token, action);
                    case PendingActionEntity.TYPE_VOTE -> syncVote(token, action);
                    case PendingActionEntity.TYPE_RETRACT_REPORT -> syncRetractReport(token, action);
                    default -> true; // tipo desconocido: descartar
                };

                if (handled) {
                    dao.delete(action);
                } else {
                    action.retryCount++;
                    if (action.retryCount >= MAX_RETRIES) {
                        action.status = PendingActionEntity.STATUS_FAILED;
                    }
                    dao.update(action);
                }
            } catch (Exception e) {
                // Error de red: reintentar más tarde, mantener PENDING
                anyFailedDueToNetwork = true;
            }
        }

        return anyFailedDueToNetwork ? Result.retry() : Result.success();
    }

    /** @return true si la acción fue procesada (exito o rechazo definitivo del server) */
    private boolean syncCreateReport(String token, PendingActionEntity action) throws Exception {
        JSONObject payload = new JSONObject(action.payload);
        ReportRequest request = new ReportRequest(
                payload.getInt("category_id"),
                payload.getDouble("latitude"),
                payload.getDouble("longitude"),
                payload.getString("description")
        );

        Response<?> response = ApiClient.getInstance().createReport(token, request).execute();
        return response.isSuccessful() || isDefinitiveRejection(response.code());
    }

    private boolean syncVote(String token, PendingActionEntity action) throws Exception {
        JSONObject payload = new JSONObject(action.payload);
        int reportId = payload.getInt("report_id");
        VoteRequest request = new VoteRequest(
                payload.getString("type"),
                payload.getDouble("latitude"),
                payload.getDouble("longitude")
        );

        Response<?> response = ApiClient.getInstance().submitVote(reportId, token, request).execute();
        // 409 = voto duplicado, no reintentar
        return response.isSuccessful() || isDefinitiveRejection(response.code());
    }

    private boolean syncRetractReport(String token, PendingActionEntity action) throws Exception {
        JSONObject payload = new JSONObject(action.payload);
        int reportId = payload.getInt("report_id");

        Response<?> response = ApiClient.getInstance().deleteReport(reportId, token).execute();
        // 403 = ventana de 5min/votos expirada, no reintentar
        return response.isSuccessful() || isDefinitiveRejection(response.code());
    }

    private boolean isDefinitiveRejection(int code) {
        return code == 403 || code == 409 || code == 422;
    }
}
