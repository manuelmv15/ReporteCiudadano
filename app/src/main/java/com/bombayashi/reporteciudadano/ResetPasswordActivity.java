package com.bombayashi.reporteciudadano;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.bombayashi.reporteciudadano.databinding.ActivityResetPasswordBinding;
import com.bombayashi.reporteciudadano.model.ResetPasswordRequest;
import com.bombayashi.reporteciudadano.model.SimpleResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.ui.SnackbarHelper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {

    private ActivityResetPasswordBinding binding;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        email = getIntent().getStringExtra("email");

        binding.btnReset.setOnClickListener(v -> doReset());
        binding.tvBack.setOnClickListener(v -> finish());
    }

    private void doReset() {
        String token = binding.etToken.getText() != null ? binding.etToken.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";
        String confirm = binding.etPasswordConfirm.getText() != null ? binding.etPasswordConfirm.getText().toString() : "";

        if (token.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            SnackbarHelper.show(findViewById(android.R.id.content), "Completá todos los campos", SnackbarHelper.Variant.ERROR);
            return;
        }
        if (!password.equals(confirm)) {
            SnackbarHelper.show(findViewById(android.R.id.content), "Las contraseñas no coinciden", SnackbarHelper.Variant.ERROR);
            return;
        }
        if (password.length() < 6) {
            SnackbarHelper.show(findViewById(android.R.id.content), "Mínimo 6 caracteres", SnackbarHelper.Variant.ERROR);
            return;
        }

        setLoading(true);
        ApiClient.getInstance().resetPassword(new ResetPasswordRequest(email, token, password))
                .enqueue(new Callback<SimpleResponse>() {
                    @Override
                    public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            SnackbarHelper.show(findViewById(android.R.id.content), "Contraseña actualizada", SnackbarHelper.Variant.SUCCESS);
                            binding.getRoot().postDelayed(() -> {
                                Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            }, 1500);
                        } else {
                            SnackbarHelper.show(findViewById(android.R.id.content), "Código inválido o expirado", SnackbarHelper.Variant.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(Call<SimpleResponse> call, Throwable t) {
                        setLoading(false);
                        SnackbarHelper.show(findViewById(android.R.id.content), "Error de red", SnackbarHelper.Variant.ERROR);
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnReset.setEnabled(!loading);
    }
}
