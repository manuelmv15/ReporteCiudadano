package com.bombayashi.reporteciudadano;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bombayashi.reporteciudadano.model.AuthResponse;
import com.bombayashi.reporteciudadano.model.GoogleLoginRequest;
import com.bombayashi.reporteciudadano.model.LoginRequest;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "auth_prefs";
    public static final String KEY_TOKEN = "token";

    private GoogleSignInClient googleSignInClient;
    private ProgressBar progressBar;
    private TextView tvError;
    private TextInputEditText etEmail, etPassword;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                handleGoogleSignInResult(task);
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.contains(KEY_TOKEN)) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        progressBar = findViewById(R.id.progress_bar);
        tvError = findViewById(R.id.tv_error);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);

        findViewById(R.id.btn_login).setOnClickListener(v -> doEmailLogin());
        findViewById(R.id.tv_go_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        SignInButton btnGoogle = findViewById(R.id.btn_google_sign_in);
        btnGoogle.setOnClickListener(v -> signInWithGoogle());
    }

    private void doEmailLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (email.isEmpty() || password.isEmpty()) {
            showError("Completá email y contraseña");
            return;
        }

        setLoading(true);
        ApiClient.getInstance().login(new LoginRequest(email, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            saveTokenAndGoMain(response.body().getToken());
                        } else {
                            showError("Credenciales incorrectas");
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        setLoading(false);
                        showError("Error de red: " + t.getMessage());
                    }
                });
    }

    private void signInWithGoogle() {
        setLoading(true);
        signInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            String idToken = account.getIdToken();
            ApiClient.getInstance().googleLogin(new GoogleLoginRequest(idToken))
                    .enqueue(new Callback<AuthResponse>() {
                        @Override
                        public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                            setLoading(false);
                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                saveTokenAndGoMain(response.body().getToken());
                            } else {
                                showError("Error del servidor: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(Call<AuthResponse> call, Throwable t) {
                            setLoading(false);
                            showError("Error de red: " + t.getMessage());
                        }
                    });
        } catch (ApiException e) {
            setLoading(false);
            showError("Google Sign-In falló: código " + e.getStatusCode());
        }
    }

    private void saveTokenAndGoMain(String token) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_TOKEN, token)
                .apply();
        goToMain();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
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
