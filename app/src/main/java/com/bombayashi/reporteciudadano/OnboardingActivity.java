package com.bombayashi.reporteciudadano;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bombayashi.reporteciudadano.databinding.ActivityOnboardingBinding;
import com.bombayashi.reporteciudadano.util.SettingsManager;

public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private OnboardingAdapter adapter;

    private static final int[] ICONS = {
        R.drawable.warning_24px,
        R.drawable.warning_24px,
        R.drawable.warning_24px
    };

    private static final String[] TITLES = {
        "Reporta en segundos",
        "Vota con tu comunidad",
        "Alertas de proximidad"
    };

    private static final String[] BODIES = {
        "Mantén presionado el mapa para colocar un reporte en esa ubicación. Elige la categoría y listo — sin formularios.",
        "Cuando estés a menos de 500 m de un reporte, puedes votar si sigue ahí o ya se resolvió. Los reportes con más votos se verifican automáticamente.",
        "Recibirás notificaciones cuando haya reportes activos cerca de ti mientras caminas. Configura las categorías y el radio desde tu perfil."
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, OnboardingActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new OnboardingAdapter(ICONS, TITLES, BODIES);
        binding.viewPager.setAdapter(adapter);

        setupDots(0);

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                setupDots(position);
                boolean isLast = position == TITLES.length - 1;
                binding.btnNext.setText(isLast ? "Comenzar" : "Siguiente");
                binding.btnSkip.setVisibility(isLast ? View.GONE : View.VISIBLE);
            }
        });

        // RF-35: Omitir en cualquier momento
        binding.btnSkip.setOnClickListener(v -> finishOnboarding());

        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current < TITLES.length - 1) {
                binding.viewPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });
    }

    private void finishOnboarding() {
        SettingsManager.getInstance(this).setSlidesOnboardingCompleted(true);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupDots(int activeIndex) {
        binding.dotsContainer.removeAllViews();
        int size = (int) (8 * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < TITLES.length; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == activeIndex
                ? R.drawable.onboarding_dot_active
                : R.drawable.onboarding_dot_inactive);
            binding.dotsContainer.addView(dot);
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.VH> {

        private final int[] icons;
        private final String[] titles;
        private final String[] bodies;

        OnboardingAdapter(int[] icons, String[] titles, String[] bodies) {
            this.icons = icons;
            this.titles = titles;
            this.bodies = bodies;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.icon.setImageResource(icons[position]);
            holder.title.setText(titles[position]);
            holder.body.setText(bodies[position]);
        }

        @Override
        public int getItemCount() { return titles.length; }

        static class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title, body;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.ivOnboardingIcon);
                title = itemView.findViewById(R.id.tvOnboardingTitle);
                body = itemView.findViewById(R.id.tvOnboardingBody);
            }
        }
    }
}
