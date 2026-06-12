package com.bombayashi.reporteciudadano;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bombayashi.reporteciudadano.databinding.ActivityLoginBinding;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.bombayashi.reporteciudadano.model.AuthResponse;
import com.bombayashi.reporteciudadano.model.GoogleLoginRequest;
import com.bombayashi.reporteciudadano.model.LoginRequest;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.bombayashi.reporteciudadano.ui.SnackbarHelper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "auth_prefs";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_USER_EMAIL = "user_email";

    private ActivityLoginBinding binding;
    private GoogleSignInClient googleSignInClient;

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

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> doEmailLogin());
        binding.tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
    }

    private void doEmailLogin() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";

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
                            saveAuthAndGoMain(response.body());
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
        // Cerrar sesión cacheada primero: si no, getSignInIntent() reusa la última
        // cuenta sin mostrar el selector de cuentas del dispositivo.
        googleSignInClient.signOut().addOnCompleteListener(task ->
                signInLauncher.launch(googleSignInClient.getSignInIntent()));
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
                                saveAuthAndGoMain(response.body());
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

    private void saveAuthAndGoMain(AuthResponse auth) {
        int userId = auth.getUser() != null ? auth.getUser().getId() : -1;
        String userName = auth.getUser() != null ? auth.getUser().getName() : "";
        String userEmail = auth.getUser() != null ? auth.getUser().getEmail() : "";
        TokenManager.getInstance(this).saveAuth(auth.getToken(), userId, userName, userEmail);
        goToMain();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
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
