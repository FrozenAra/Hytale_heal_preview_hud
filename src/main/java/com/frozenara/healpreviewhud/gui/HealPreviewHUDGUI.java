package com.frozenara.healpreviewhud.gui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class HealPreviewHUDGUI extends CustomUIHud {

    private UICommandBuilder builder;

    // Cached state to restore on rebuild
    private float cachedNormalHealth = 1.0f;
    private float cachedInstantHealthPreview = 0.0f;
    private float cachedBuffHealthPreview = 0.0f;
    private boolean cachedIsCreativeMode = false;
    private boolean cachedIsVisible = true;

    public HealPreviewHUDGUI(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        this.builder = builder;
        builder.append("Hud/HealthPreviewHud.ui");

        /*
        * I need to use cached values here because multipleHUD runs build again everytime another mod updates their HUD
        * While I dont need to do that because the health hud is static in terms of its setup, it seems like we are forced to rebuild anyways
        * Using cached values is enough to work around that rebuild
        * */
        // Restore cached state on every build (handles MultipleHUD rebuilds)
        // Call all functions with their cached values
        updateHealth(cachedNormalHealth, cachedInstantHealthPreview, cachedBuffHealthPreview);
        setCurrentHealth(cachedNormalHealth);
        updateInstantHealthPreview(cachedInstantHealthPreview);
        updateRegenPreview(cachedBuffHealthPreview);
        setCreativeMode(cachedIsCreativeMode);
        setHealthHudVisible(cachedIsVisible);
        builder.set("#ProgressBarHealthCreative.Value", 1);
    }

    public void updateHealth(float normal_health, float instant_health_preview, float buff_health_preview) {
        // Cache values for rebuild
        cachedNormalHealth = normal_health;
        cachedInstantHealthPreview = instant_health_preview;
        cachedBuffHealthPreview = buff_health_preview;
        
        builder.set("#ProgressBarHealth.Value", normal_health);
        builder.set("#ProgressBarHealthPreview.Value", instant_health_preview);
        builder.set("#ProgressBarFillBuff.Value", buff_health_preview);
    }

    public void updateInstantHealthPreview(float instant_health_preview) {
        cachedInstantHealthPreview = instant_health_preview;
        builder.set("#ProgressBarHealthPreview.Value", instant_health_preview);
    }

    public void updateRegenPreview(float buff_health_preview) {
        cachedBuffHealthPreview = buff_health_preview;
        builder.set("#ProgressBarFillBuff.Value", buff_health_preview);
    }

    public void setCreativeMode(boolean is_creative_mode) {
        cachedIsCreativeMode = is_creative_mode;
        builder.set("#Icon.Visible", !is_creative_mode);
        builder.set("#IconCreative.Visible", is_creative_mode);
        builder.set("#ProgressBarHealth.Visible", !is_creative_mode);
        builder.set("#ProgressBarHealthCreative.Visible", is_creative_mode);
    }


    public void setCurrentHealth(float health) {
        cachedNormalHealth = health;
        builder.set("#ProgressBarHealth.Value", health);
    }

    public void setHealthHudVisible(boolean visible) {
        cachedIsVisible = visible;
        builder.set("#MainContainer.Visible", visible);
    }

    public void updateUI(boolean clear_ui){
        update(clear_ui, builder);
    }
}
