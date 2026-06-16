package com.bombayashi.reporteciudadano;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.bombayashi.reporteciudadano.databinding.ActivityForgotPasswordBinding;
import com.bombayashi.reporteciudadano.model.ForgotPasswordRequest;
import com.bombayashi.reporteciudadano.model.SimpleResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.ui.SnackbarHelper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnSend.setOnClickListener(v -> sendResetLink());
        binding.tvBack.setOnClickListener(v -> finish());
    }

    private void sendResetLink() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        if (email.isEmpty()) {
            SnackbarHelper.show(findViewById(android.R.id.content), "Ingresá tu correo", SnackbarHelper.Variant.ERROR);
            return;
        }

        setLoading(true);
        ApiClient.getInstance().forgotPassword(new ForgotPasswordRequest(email))
                .enqueue(new Callback<SimpleResponse>() {
                    @Override
                    public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                        setLoading(false);
                        Intent intent = new Intent(ForgotPasswordActivity.this, ResetPasswordActivity.class);
                        intent.putExtra("email", email);
                        startActivity(intent);
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
        binding.btnSend.setEnabled(!loading);
    }
}
