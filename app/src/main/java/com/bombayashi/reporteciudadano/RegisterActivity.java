package com.bombayashi.reporteciudadano;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bombayashi.reporteciudadano.model.AuthResponse;
import com.bombayashi.reporteciudadano.model.RegisterRequest;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword, etPasswordConfirm;
    private ProgressBar progressBar;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etPasswordConfirm = findViewById(R.id.et_password_confirm);
        progressBar = findViewById(R.id.progress_bar);
        tvError = findViewById(R.id.tv_error);

        findViewById(R.id.btn_register).setOnClickListener(v -> doRegister());
        findViewById(R.id.tv_go_login).setOnClickListener(v -> finish());
    }

    private void doRegister() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirm = etPasswordConfirm.getText() != null ? etPasswordConfirm.getText().toString() : "";

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
                            String token = response.body().getToken();
                            getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE)
                                    .edit()
                                    .putString(LoginActivity.KEY_TOKEN, token)
                                    .apply();
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
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}
