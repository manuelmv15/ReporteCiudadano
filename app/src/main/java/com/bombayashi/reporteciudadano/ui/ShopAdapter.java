package com.bombayashi.reporteciudadano.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.databinding.ItemShopBinding;
import com.bombayashi.reporteciudadano.model.ShopResponse;
import com.bombayashi.reporteciudadano.util.CosmeticStyler;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** Grilla de artículos de la tienda, filtrada por tipo (frame / title / theme). */
public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ViewHolder> {

    public interface Listener {
        /** Artículo no comprado y desbloqueado. */
        void onBuy(ShopResponse.Item item);
        /** Artículo comprado: equipar, o quitar si ya está equipado. */
        void onToggleEquip(ShopResponse.Item item);
    }

    private final Listener listener;
    private final List<ShopResponse.Item> items = new ArrayList<>();
    private int coins;
    /** Foto del usuario para la vista previa de marcos (URL o ruta local), puede ser null. */
    private Object avatarSource;

    public ShopAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<ShopResponse.Item> all, String type, int coins) {
        this.coins = coins;
        items.clear();
        if (all != null) {
            for (ShopResponse.Item item : all) {
                if (type.equals(item.getType())) items.add(item);
            }
        }
        notifyDataSetChanged();
    }

    public void setAvatarSource(Object avatarSource) {
        this.avatarSource = avatarSource;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemShopBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemShopBinding b;

        ViewHolder(ItemShopBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(ShopResponse.Item item) {
            b.tvItemName.setText(item.getName());
            b.tvItemDescription.setText(item.getDescription());

            b.ringPreview.setVisibility(View.GONE);
            b.tvPreviewTitle.setVisibility(View.GONE);
            b.llPreviewBar.setVisibility(View.GONE);
            switch (item.getType()) {
                case "frame":
                    b.ringPreview.setVisibility(View.VISIBLE);
                    CosmeticStyler.applyFrame(b.ringPreview, item.getValue());
                    if (avatarSource != null) {
                        Glide.with(b.ivPreviewAvatar).load(avatarSource)
                                .placeholder(R.drawable.person_24px).into(b.ivPreviewAvatar);
                    }
                    break;
                case "title":
                    CosmeticStyler.applyTitle(b.tvPreviewTitle, item.getValue());
                    break;
                case "theme":
                    b.llPreviewBar.setVisibility(View.VISIBLE);
                    CosmeticStyler.applyTheme(b.llPreviewBar, item.getValue());
                    break;
            }

            // Estado del botón: equipado / equipar / bloqueado por nivel / precio
            b.btnItemAction.setEnabled(true);
            b.btnItemAction.setIcon(null);
            int ctx = ContextCompat.getColor(b.getRoot().getContext(), R.color.md_theme_onSecondaryContainer);
            b.btnItemAction.setTextColor(ctx);
            b.btnItemAction.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(b.getRoot().getContext(), R.color.md_theme_secondaryContainer)));

            if (item.isEquipped()) {
                b.btnItemAction.setText("Equipado");
                b.btnItemAction.setIconResource(R.drawable.ic_check);
                b.btnItemAction.setOnClickListener(v -> listener.onToggleEquip(item));
            } else if (item.isOwned()) {
                b.btnItemAction.setText("Equipar");
                b.btnItemAction.setOnClickListener(v -> listener.onToggleEquip(item));
            } else if (item.isLocked()) {
                b.btnItemAction.setText("Nivel " + CosmeticStyler.levelLabel(item.getMinLevel()));
                b.btnItemAction.setEnabled(false);
                b.btnItemAction.setOnClickListener(null);
            } else {
                b.btnItemAction.setText(item.getPrice() + " pts");
                b.btnItemAction.setIconResource(R.drawable.ic_store);
                boolean affordable = coins >= item.getPrice();
                if (affordable) {
                    b.btnItemAction.setTextColor(ContextCompat.getColor(b.getRoot().getContext(), R.color.md_theme_onPrimary));
                    b.btnItemAction.setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(b.getRoot().getContext(), R.color.md_theme_primary)));
                }
                b.btnItemAction.setIconTint(ColorStateList.valueOf(b.btnItemAction.getCurrentTextColor()));
                b.btnItemAction.setOnClickListener(v -> listener.onBuy(item));
            }
            if (b.btnItemAction.getIcon() != null && !item.isLocked()) {
                b.btnItemAction.setIconTint(ColorStateList.valueOf(b.btnItemAction.getCurrentTextColor()));
            }
        }
    }
}
