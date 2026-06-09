package com.bombayashi.reporteciudadano.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bombayashi.reporteciudadano.databinding.BottomSheetUserProfileBinding;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class UserProfileBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetUserProfileBinding binding;
    private OnLogoutListener logoutListener;

    public interface OnLogoutListener {
        void onLogout();
    }

    public void setLogoutListener(OnLogoutListener listener) {
        this.logoutListener = listener;
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
    }

    private void displayUserInfo() {
        TokenManager tm = TokenManager.getInstance(requireContext());
        binding.tvUserName.setText(tm.getUserName());
        binding.tvUserEmail.setText(tm.getUserEmail());
    }

    private void setupMenuListeners() {
        // Mi Perfil
        binding.llMyProfile.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Mi Perfil - Próximamente", Toast.LENGTH_SHORT).show();
        });

        // Mis Reportes
        binding.llMyReports.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Mis Reportes - Próximamente", Toast.LENGTH_SHORT).show();
        });

        // Configuración
        binding.llSettings.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Configuración - Próximamente", Toast.LENGTH_SHORT).show();
        });

        // Cerrar Sesión
        binding.llLogout.setOnClickListener(v -> {
            handleLogout();
        });

        // Botón cerrar
        binding.btnCloseProfile.setOnClickListener(v -> {
            dismiss();
        });
    }

    private void handleLogout() {
        android.util.Log.d("UserProfileBS", "🔓 Cerrando sesión...");

        TokenManager.getInstance(requireContext()).clearAuth();

        android.util.Log.d("UserProfileBS", "✓ Token eliminado");

        Toast.makeText(getContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show();

        // Cerrar el bottom sheet
        dismiss();

        // Notificar al listener
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
