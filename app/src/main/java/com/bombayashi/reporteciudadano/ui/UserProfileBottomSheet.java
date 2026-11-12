package com.bombayashi.reporteciudadano.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.DialogInterface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bombayashi.reporteciudadano.databinding.BottomSheetUserProfileBinding;
import com.bombayashi.reporteciudadano.model.AuthResponse;
import com.bombayashi.reporteciudadano.model.AvatarUploadResponse;
import com.bombayashi.reporteciudadano.model.MyVotesResponse;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.model.UpdateProfileRequest;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.util.SettingsManager;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserProfileBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetUserProfileBinding binding;
    private OnLogoutListener logoutListener;
    private MyReportsAdapter reportsAdapter;
    private boolean reportsLoaded = false;
    private MyVotesAdapter votesAdapter;
    private boolean votesLoaded = false;
    private Uri cameraPhotoUri;

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) saveAvatar(uri);
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && cameraPhotoUri != null) saveAvatar(cameraPhotoUri);
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera();
                else if (getView() != null)
                    SnackbarHelper.show(getView(), "Permiso de cámara denegado", SnackbarHelper.Variant.WARNING);
            });

    public interface OnLogoutListener {
        void onLogout();
    }

    public void setLogoutListener(OnLogoutListener listener) {
        this.logoutListener = listener;
    }

    public interface OnSettingsDismissListener {
        void onDismiss();
    }

    private OnSettingsDismissListener settingsDismissListener;

    public void setOnSettingsDismissListener(OnSettingsDismissListener listener) {
        this.settingsDismissListener = listener;
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (settingsDismissListener != null) settingsDismissListener.onDismiss();
    }

    @Override
    public int getTheme() {
        // Contenedor transparente: el panel interno (bg_floating_panel) flota como la barra inferior
        return com.bombayashi.reporteciudadano.R.style.ThemeOverlay_App_BottomSheetDialog_Floating;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof com.google.android.material.bottomsheet.BottomSheetDialog) {
            com.google.android.material.bottomsheet.BottomSheetDialog d =
                    (com.google.android.material.bottomsheet.BottomSheetDialog) getDialog();
            d.getBehavior().setSkipCollapsed(true);
            d.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    /**
     * El menú y "Mi perfil" se ajustan a su contenido; las secciones con listas o ajustes usan
     * altura fija (~85% de pantalla) para que tengan espacio y no salten al cargar.
     */
    private void applyPanelHeight(View page) {
        ViewGroup.LayoutParams lp = binding.getRoot().getLayoutParams();
        if (lp == null) return;
        boolean fitsContent = page == binding.pageMenu || page == binding.pageProfile;
        int target = fitsContent
                ? ViewGroup.LayoutParams.WRAP_CONTENT
                : (int) (getResources().getDisplayMetrics().heightPixels * 0.85f);
        if (lp.height != target) {
            lp.height = target;
            binding.getRoot().setLayoutParams(lp);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetUserProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        displayUserInfo();
        setupMenuListeners();
        setupProfilePage();
        setupReportsPage();
        setupVotesPage();
        setupSettingsPage();
    }

    private void displayUserInfo() {
        TokenManager tm = TokenManager.getInstance(requireContext());
        binding.tvUserName.setText(tm.getUserName());
        binding.tvUserEmail.setText(tm.getUserEmail());
        loadAvatar(binding.ivUserAvatar);
    }

    private void loadAvatar(android.widget.ImageView target) {
        TokenManager tm = TokenManager.getInstance(requireContext());
        String avatarUrl = tm.getAvatarUrl();
        String avatarPath = tm.getAvatarPath();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(target.getDrawable())
                    .error(target.getDrawable())
                    .into(target);
        } else if (avatarPath != null) {
            Glide.with(this).load(new File(avatarPath)).into(target);
        }
    }

    private void setupMenuListeners() {
        binding.llMyProfile.setOnClickListener(v -> showPage(binding.pageProfile));
        binding.llMyReports.setOnClickListener(v -> {
            showPage(binding.pageReports);
            if (!reportsLoaded) loadMyReports();
        });
        binding.llMyVotes.setOnClickListener(v -> {
            showPage(binding.pageVotes);
            if (!votesLoaded) loadMyVotes();
        });
        binding.llSettings.setOnClickListener(v -> showPage(binding.pageSettings));

        binding.llLogout.setOnClickListener(v -> handleLogout());

        binding.btnCloseProfile.setOnClickListener(v -> dismiss());
    }

    // ===================== Navegación entre páginas =====================

    private void showPage(View page) {
        binding.pageMenu.setVisibility(View.GONE);
        binding.pageProfile.setVisibility(View.GONE);
        binding.pageReports.setVisibility(View.GONE);
        binding.pageVotes.setVisibility(View.GONE);
        binding.pageSettings.setVisibility(View.GONE);
        page.setVisibility(View.VISIBLE);
        applyPanelHeight(page);

        // Entrada suave: desde la derecha al abrir una sección, desde la izquierda al volver
        float dir = page == binding.pageMenu ? -1f : 1f;
        page.setAlpha(0f);
        page.setTranslationX(dir * 24 * getResources().getDisplayMetrics().density);
        page.animate().alpha(1f).translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void showMenu() {
        showPage(binding.pageMenu);
    }

    // ===================== Mi Perfil =====================

    private void setupProfilePage() {
        binding.btnBackProfile.setOnClickListener(v -> showMenu());

        TokenManager tm = TokenManager.getInstance(requireContext());
        binding.tvProfileName.setText(tm.getUserName());
        binding.tvProfileEmail.setText(tm.getUserEmail());
        binding.tvProfileLevel.setText(capitalize(tm.getLevel()));
        binding.tvProfileScore.setText(tm.getScore() + " pts");
        loadAvatar(binding.ivProfileAvatar);

        binding.btnEditAvatar.setOnClickListener(v -> showAvatarPickerDialog());
        binding.ivProfileAvatar.setOnClickListener(v -> showAvatarPickerDialog());
        binding.btnEditName.setOnClickListener(v -> showEditNameDialog());

        refreshProfile();
        loadProfileStats();
    }

    /** El nivel llega en minúsculas desde la API ("nuevo"); mostrarlo como etiqueta. */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void loadProfileStats() {
        TokenManager tm = TokenManager.getInstance(requireContext());

        ApiClient.getInstance().getMyReports("", 100)
                .enqueue(new Callback<ReportResponse>() {
                    @Override
                    public void onResponse(Call<ReportResponse> call, Response<ReportResponse> response) {
                        if (binding == null || !response.isSuccessful() || response.body() == null) return;

                        List<ReportResponse.ReportData> myReports = response.body().getData();
                        if (myReports == null) return;

                        int confirmations = 0;
                        int resolved = 0;
                        for (ReportResponse.ReportData report : myReports) {
                            confirmations += report.getVotesConfirm();
                            if ("resolved".equals(report.getStatus())) resolved++;
                        }

                        binding.tvStatReports.setText(String.valueOf(myReports.size()));
                        binding.tvStatConfirmations.setText(String.valueOf(confirmations));
                        binding.tvStatResolved.setText(String.valueOf(resolved));
                    }

                    @Override
                    public void onFailure(Call<ReportResponse> call, Throwable t) {
                        // sin conexión: dejar stats en 0
                    }
                });
    }

    private void refreshProfile() {
        TokenManager tm = TokenManager.getInstance(requireContext());
        ApiClient.getInstance().getMe()
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        if (binding == null || !response.isSuccessful() || response.body() == null) return;
                        AuthResponse.User user = response.body().getUser();
                        if (user == null) return;

                        tm.setUserName(user.getName());
                        tm.setScoreAndLevel(user.getScore(), user.getLevel());
                        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                            tm.setAvatarUrl(user.getAvatarUrl());
                        }

                        binding.tvProfileName.setText(user.getName());
                        binding.tvUserName.setText(user.getName());
                        binding.tvProfileLevel.setText(capitalize(user.getLevel()));
                        binding.tvProfileScore.setText(user.getScore() + " pts");
                        loadAvatar(binding.ivProfileAvatar);
                        loadAvatar(binding.ivUserAvatar);
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        // sin conexión: usar datos en caché ya mostrados
                    }
                });
    }

    private void showEditNameDialog() {
        EditText input = new EditText(requireContext());
        input.setText(TokenManager.getInstance(requireContext()).getUserName());
        input.setSelection(input.getText().length());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Editar nombre")
                .setView(input)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateProfileName(newName);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void updateProfileName(String newName) {
        TokenManager tm = TokenManager.getInstance(requireContext());
        ApiClient.getInstance().updateProfile(new UpdateProfileRequest(newName))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        if (binding == null) return;
                        if (response.isSuccessful()) {
                            tm.setUserName(newName);
                            binding.tvProfileName.setText(newName);
                            binding.tvUserName.setText(newName);
                            SnackbarHelper.show(getView(), "Nombre actualizado", SnackbarHelper.Variant.SUCCESS);
                        } else {
                            SnackbarHelper.show(getView(), "Error al actualizar nombre", SnackbarHelper.Variant.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        if (binding == null) return;
                        SnackbarHelper.show(getView(), "Sin conexión", SnackbarHelper.Variant.ERROR);
                    }
                });
    }

    private void showAvatarPickerDialog() {
        String[] options = {"Tomar foto", "Elegir de galería"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Foto de perfil")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED) {
                            launchCamera();
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                        }
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void launchCamera() {
        try {
            File photoFile = File.createTempFile("avatar_photo", ".jpg", requireContext().getCacheDir());
            cameraPhotoUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile);
            cameraLauncher.launch(cameraPhotoUri);
        } catch (Exception e) {
            if (getView() != null)
                SnackbarHelper.show(getView(), "Error al iniciar cámara", SnackbarHelper.Variant.ERROR);
        }
    }

    private void saveAvatar(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) return;
            File avatarFile = new File(requireContext().getFilesDir(), "avatar.jpg");
            try (FileOutputStream fos = new FileOutputStream(avatarFile)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
            }
            is.close();

            com.bumptech.glide.signature.ObjectKey signature =
                    new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis());
            Glide.with(this).load(avatarFile).signature(signature).into(binding.ivProfileAvatar);
            Glide.with(this).load(avatarFile).signature(signature).into(binding.ivUserAvatar);

            uploadAvatar(avatarFile);
        } catch (Exception e) {
            if (getView() != null)
                SnackbarHelper.show(getView(), "Error al guardar la foto", SnackbarHelper.Variant.ERROR);
        }
    }

    private void uploadAvatar(File avatarFile) {
        TokenManager tm = TokenManager.getInstance(requireContext());
        RequestBody requestFile = RequestBody.create(avatarFile, MediaType.parse("image/jpeg"));
        MultipartBody.Part avatarPart = MultipartBody.Part.createFormData("avatar", avatarFile.getName(), requestFile);

        ApiClient.getInstance().uploadAvatar(avatarPart)
                .enqueue(new Callback<AvatarUploadResponse>() {
                    @Override
                    public void onResponse(Call<AvatarUploadResponse> call, Response<AvatarUploadResponse> response) {
                        if (binding == null) return;
                        if (response.isSuccessful() && response.body() != null && response.body().getAvatarUrl() != null) {
                            tm.setAvatarUrl(response.body().getAvatarUrl());
                            loadAvatar(binding.ivProfileAvatar);
                            loadAvatar(binding.ivUserAvatar);
                            SnackbarHelper.show(getView(), "Foto de perfil actualizada", SnackbarHelper.Variant.SUCCESS);
                        } else {
                            SnackbarHelper.show(getView(), "Error al subir la foto", SnackbarHelper.Variant.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(Call<AvatarUploadResponse> call, Throwable t) {
                        if (binding == null) return;
                        SnackbarHelper.show(getView(), "Sin conexión, foto no subida", SnackbarHelper.Variant.ERROR);
                    }
                });
    }

    // ===================== Mis Reportes =====================

    private void setupReportsPage() {
        binding.btnBackReports.setOnClickListener(v -> showMenu());

        reportsAdapter = new MyReportsAdapter();
        binding.rvMyReports.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMyReports.setAdapter(reportsAdapter);
    }

    private void loadMyReports() {
        binding.progressBarReports.setVisibility(View.VISIBLE);
        binding.tvEmptyReports.setVisibility(View.GONE);

        TokenManager tm = TokenManager.getInstance(requireContext());

        ApiClient.getInstance().getMyReports("", 100)
                .enqueue(new Callback<ReportResponse>() {
                    @Override
                    public void onResponse(Call<ReportResponse> call, Response<ReportResponse> response) {
                        if (binding == null) return;
                        binding.progressBarReports.setVisibility(View.GONE);

                        List<ReportResponse.ReportData> myReports = new ArrayList<>();
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            myReports = response.body().getData();
                        }

                        reportsAdapter.setReports(myReports);
                        binding.tvEmptyReports.setVisibility(myReports.isEmpty() ? View.VISIBLE : View.GONE);
                        reportsLoaded = true;
                    }

                    @Override
                    public void onFailure(Call<ReportResponse> call, Throwable t) {
                        if (binding == null) return;
                        binding.progressBarReports.setVisibility(View.GONE);
                        binding.tvEmptyReports.setText("Error al cargar tus reportes");
                        binding.tvEmptyReports.setVisibility(View.VISIBLE);
                    }
                });
    }

    // ===================== Mis Votos =====================

    private void setupVotesPage() {
        binding.btnBackVotes.setOnClickListener(v -> showMenu());

        votesAdapter = new MyVotesAdapter();
        binding.rvMyVotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMyVotes.setAdapter(votesAdapter);
    }

    private void loadMyVotes() {
        binding.progressBarVotes.setVisibility(View.VISIBLE);
        binding.tvEmptyVotes.setVisibility(View.GONE);

        TokenManager tm = TokenManager.getInstance(requireContext());

        ApiClient.getInstance().getMyVotes(100)
                .enqueue(new Callback<MyVotesResponse>() {
                    @Override
                    public void onResponse(Call<MyVotesResponse> call, Response<MyVotesResponse> response) {
                        if (binding == null) return;
                        binding.progressBarVotes.setVisibility(View.GONE);

                        List<MyVotesResponse.VoteData> myVotes = new ArrayList<>();
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            myVotes = response.body().getData();
                        }

                        votesAdapter.setVotes(myVotes);
                        binding.tvEmptyVotes.setVisibility(myVotes.isEmpty() ? View.VISIBLE : View.GONE);
                        binding.tvVotesAccuracySummary.setText(buildAccuracySummary(myVotes));
                        votesLoaded = true;
                    }

                    @Override
                    public void onFailure(Call<MyVotesResponse> call, Throwable t) {
                        if (binding == null) return;
                        binding.progressBarVotes.setVisibility(View.GONE);
                        binding.tvEmptyVotes.setText("Error al cargar tus votos");
                        binding.tvEmptyVotes.setVisibility(View.VISIBLE);
                    }
                });
    }

    private String buildAccuracySummary(List<MyVotesResponse.VoteData> votes) {
        int confirmTotal = 0, confirmCorrect = 0;
        int resolveTotal = 0, resolveCorrect = 0;

        for (MyVotesResponse.VoteData vote : votes) {
            Boolean correct = vote.isCorrect();
            if (correct == null) continue;
            if ("confirm".equals(vote.getType())) {
                confirmTotal++;
                if (correct) confirmCorrect++;
            } else if ("resolve".equals(vote.getType())) {
                resolveTotal++;
                if (correct) resolveCorrect++;
            }
        }

        String confirmPct = confirmTotal > 0 ? (confirmCorrect * 100 / confirmTotal) + "%" : "—";
        String resolvePct = resolveTotal > 0 ? (resolveCorrect * 100 / resolveTotal) + "%" : "—";

        return "Precisión: Sigue ahí " + confirmPct + " · Ya se resolvió " + resolvePct;
    }

    // ===================== Configuración =====================

    private void setupSettingsPage() {
        binding.btnBackSettings.setOnClickListener(v -> showMenu());

        SettingsManager settingsManager = SettingsManager.getInstance(requireContext());

        switch (settingsManager.getThemeMode()) {
            case "light":
                binding.rbThemeLight.setChecked(true);
                break;
            case "dark":
                binding.rbThemeDark.setChecked(true);
                break;
            default:
                binding.rbThemeSystem.setChecked(true);
                break;
        }

        binding.rgTheme.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == binding.rbThemeLight.getId()) {
                settingsManager.setThemeMode("light");
            } else if (checkedId == binding.rbThemeDark.getId()) {
                settingsManager.setThemeMode("dark");
            } else {
                settingsManager.setThemeMode("system");
            }
        });

        switch (settingsManager.getMapPreset()) {
            case "dawn":  binding.rbMapDawn.setChecked(true);  break;
            case "day":   binding.rbMapDay.setChecked(true);   break;
            case "dusk":  binding.rbMapDusk.setChecked(true);  break;
            case "night": binding.rbMapNight.setChecked(true); break;
            default:      binding.rbMapAuto.setChecked(true);  break;
        }

        binding.rgMapPreset.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == binding.rbMapDawn.getId())       settingsManager.setMapPreset("dawn");
            else if (checkedId == binding.rbMapDay.getId())   settingsManager.setMapPreset("day");
            else if (checkedId == binding.rbMapDusk.getId())  settingsManager.setMapPreset("dusk");
            else if (checkedId == binding.rbMapNight.getId()) settingsManager.setMapPreset("night");
            else                                              settingsManager.setMapPreset("auto");
        });

        binding.swNotifications.setChecked(settingsManager.isNotificationsEnabled());
        binding.swNotifications.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsManager.setNotificationsEnabled(isChecked));

        // RF-23: alert radius seekbar
        int seekIndex = Math.max(0, Math.min(4, settingsManager.getAlertRadiusSeekIndex()));
        binding.seekAlertRadius.setValue(seekIndex);
        binding.tvAlertRadius.setText(settingsManager.getAlertRadiusMeters() + " m");
        binding.seekAlertRadius.addOnChangeListener((slider, value, fromUser) -> {
            int meters = settingsManager.seekIndexToRadius(Math.round(value));
            binding.tvAlertRadius.setText(meters + " m");
            if (fromUser) settingsManager.setAlertRadiusMeters(meters);
        });

        // RF-23: alert categories
        binding.cbCatBache.setChecked(settingsManager.isAlertCategoryEnabled(settingsManager.getAlertCatKeyBache()));
        binding.cbCatAlumbrado.setChecked(settingsManager.isAlertCategoryEnabled(settingsManager.getAlertCatKeyAlumbrado()));
        binding.cbCatBasura.setChecked(settingsManager.isAlertCategoryEnabled(settingsManager.getAlertCatKeyBasura()));
        binding.cbCatAgua.setChecked(settingsManager.isAlertCategoryEnabled(settingsManager.getAlertCatKeyAgua()));
        binding.cbCatAccidente.setChecked(settingsManager.isAlertCategoryEnabled(settingsManager.getAlertCatKeyAccidente()));
        binding.cbCatVandalo.setChecked(settingsManager.isAlertCategoryEnabled(settingsManager.getAlertCatKeyVandalo()));
        binding.cbCatOtro.setChecked(settingsManager.isAlertCategoryEnabled(settingsManager.getAlertCatKeyOtro()));

        binding.cbCatBache.setOnCheckedChangeListener((b, c) -> settingsManager.setAlertCategoryEnabled(settingsManager.getAlertCatKeyBache(), c));
        binding.cbCatAlumbrado.setOnCheckedChangeListener((b, c) -> settingsManager.setAlertCategoryEnabled(settingsManager.getAlertCatKeyAlumbrado(), c));
        binding.cbCatBasura.setOnCheckedChangeListener((b, c) -> settingsManager.setAlertCategoryEnabled(settingsManager.getAlertCatKeyBasura(), c));
        binding.cbCatAgua.setOnCheckedChangeListener((b, c) -> settingsManager.setAlertCategoryEnabled(settingsManager.getAlertCatKeyAgua(), c));
        binding.cbCatAccidente.setOnCheckedChangeListener((b, c) -> settingsManager.setAlertCategoryEnabled(settingsManager.getAlertCatKeyAccidente(), c));
        binding.cbCatVandalo.setOnCheckedChangeListener((b, c) -> settingsManager.setAlertCategoryEnabled(settingsManager.getAlertCatKeyVandalo(), c));
        binding.cbCatOtro.setOnCheckedChangeListener((b, c) -> settingsManager.setAlertCategoryEnabled(settingsManager.getAlertCatKeyOtro(), c));

        // RF-36: acceso al onboarding desde ajustes
        binding.btnViewTutorial.setOnClickListener(v -> {
            dismiss();
            com.bombayashi.reporteciudadano.OnboardingActivity.start(requireContext());
        });

        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            binding.tvVersion.setText("ReporteCiudadano — versión " + versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    // ===================== Logout =====================

    private void handleLogout() {
        android.util.Log.d("UserProfileBS", "🔓 Cerrando sesión...");

        TokenManager.getInstance(requireContext()).clearAuth();

        android.util.Log.d("UserProfileBS", "✓ Token eliminado");

        if (getView() != null) {
            SnackbarHelper.show(getView(), "Sesión cerrada", SnackbarHelper.Variant.SUCCESS);
        }

        dismiss();

        if (logoutListener != null) {
            logoutListener.onLogout();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
