package com.bombayashi.reporteciudadano;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.bombayashi.reporteciudadano.databinding.ActivityRegisterBinding;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.bombayashi.reporteciudadano.model.AuthResponse;
import com.bombayashi.reporteciudadano.model.RegisterRequest;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.ui.SnackbarHelper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnRegister.setOnClickListener(v -> doRegister());
        binding.tvGoLogin.setOnClickListener(v -> finish());
    }

    private void doRegister() {
        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";
        String confirm = binding.etPasswordConfirm.getText() != null ? binding.etPasswordConfirm.getText().toString() : "";

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Completá todos los campos");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Las contraseñas no coinciden");
            return;
        }
        if (password.length() < 8) {
            showError("La contraseña debe tener al menos 8 caracteres");
            return;
        }

        setLoading(true);
        ApiClient.getInstance().register(new RegisterRequest(name, email, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            AuthResponse auth = response.body();
                            int userId = auth.getUser() != null ? auth.getUser().getId() : -1;
                            String userName = auth.getUser() != null ? auth.getUser().getName() : "";
                            String userEmail = auth.getUser() != null ? auth.getUser().getEmail() : "";
                            TokenManager.getInstance(RegisterActivity.this).saveAuth(auth.getToken(), userId, userName, userEmail);
                            
                            // Vamos directo a MainActivity. MapFragment se encargará del onboarding contextual.
                            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } else {
                            showError("Error al registrar. Email ya en uso o datos inválidos.");
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        setLoading(false);
                        showError("Error de red: " + t.getMessage());
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        SnackbarHelper.show(
                findViewById(android.R.id.content),
                msg,
                SnackbarHelper.Variant.ERROR
        );
    }
}
