package com.frozenara.healpreviewhud;

import com.frozenara.healpreviewhud.gui.HealPreviewHUDGUI;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import static com.frozenara.healpreviewhud.HealPreviewHUD.HEALTH;

/**
 * Per-player state container to avoid shared state issues in multiplayer
 */
class PlayerState {
    int tick_counter = 0;
    float current_health_percentage = 0.0f;
    float stored_current_health = 0.0f;
    float stored_max_health = 0.0f;
    float instant_heal_value_percent = 0.0f;
    Item item_in_hand = null;
    GameMode stored_gamemode = null;
}

public class PlayerTickSystemHealthPreview extends EntityTickingSystem<EntityStore>
{
    static PlayerTickSystemHealthPreview instance;
    @Nonnull
    private final Query<EntityStore> query;
    private static HealPreviewHUD healthPreview;
    private static final int TICK_RATE = 30;

    // Per-player state map to handle multiplayer correctly
    private final Map<PlayerRef, PlayerState> playerStates = new HashMap<>();

    public PlayerTickSystemHealthPreview() {
        this.query = Query.and(Player.getComponentType());
        healthPreview = HealPreviewHUD.getInstance();
        instance = this;
    }

    private PlayerState getOrCreatePlayerState(PlayerRef playerRef) {
        return playerStates.computeIfAbsent(playerRef, k -> new PlayerState());
    }

    public static PlayerTickSystemHealthPreview getInstance()
    {
        return instance;
    }

    public static void remove_player_state(PlayerRef playerRef)
    {
        instance.playerStates.remove(playerRef);
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        final Holder<EntityStore> holder = EntityUtils.toHolder(index, archetypeChunk);
        Player player = holder.getComponent(Player.getComponentType());
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }




        // Get or create per-player state
        PlayerState state = getOrCreatePlayerState(playerRef);


        state.tick_counter++;
        state.instant_heal_value_percent = 0.0f;
        boolean do_ui_update = false;
        boolean hotbar_selection_changed = false;

        HealPreviewHUDGUI healthPreviewGUI = HealPreviewHUD.getPlayerGuiMap().get(playerRef);


        if(state.stored_gamemode == null)
        {
            state.stored_gamemode = player.getGameMode();
        }

        if(player.getGameMode() != state.stored_gamemode)
        {
            state.stored_gamemode = player.getGameMode();
            // Game mode changed
            healthPreviewGUI.setCreativeMode(player.getGameMode() == GameMode.Creative);
        }
        if(state.stored_gamemode == GameMode.Creative)
        {
            // We dont need to do all this if the gamemode is creative
            return;
        }

        float max_health = healthPreview.getMaxHealth(player);
        float current_health = healthPreview.getCurrentHealth(player);
        state.current_health_percentage = current_health / max_health;

        // Update values before the function call so functions can work with new health values
        boolean current_health_changed = state.stored_current_health != current_health;
        boolean max_health_changed = state.stored_max_health != max_health;
        state.stored_current_health = current_health;
        state.stored_max_health = max_health;


        if(current_health_changed)
        {
            onCurrentHealthChanged(player, state, healthPreviewGUI);
            do_ui_update = true;
        }
        if(max_health_changed)
        {
            onMaxHealthChanged(player, state, healthPreviewGUI);
            do_ui_update = true;
        }

        Inventory player_inventory = player.getInventory();
        ItemStack heldItemStack = (player_inventory != null) ? player_inventory.getItemInHand() : null;
        if(heldItemStack != null && !heldItemStack.isEmpty())
        {
            Item current_held_item = heldItemStack.getItem();
            if(state.item_in_hand != current_held_item)
            {
                state.item_in_hand = current_held_item;
                if(is_player_holding_consumable(player))
                {
                    onSelectedConsumableOnHotbar(player, state, healthPreviewGUI);
                    do_ui_update = true;
                }
                else
                {
                    onSelectedNonConsumableOnHotbar(healthPreviewGUI);
                    do_ui_update = true;
                }
                hotbar_selection_changed = true;
            }
        }
        else if(state.item_in_hand != null)
        {
            state.item_in_hand = null;  // Mark that we've handled the empty state
            onSelectedEmptySlotOnHotbar(healthPreviewGUI);
            do_ui_update = true;
            hotbar_selection_changed = true;
        }

        if(state.tick_counter >= TICK_RATE || hotbar_selection_changed)
        {
            state.tick_counter = 0;
            on_buff_tick(player, playerRef, state, healthPreviewGUI);
            do_ui_update = true;
        }
        if(do_ui_update && healthPreviewGUI != null)
        {
            healthPreviewGUI.updateUI(true);
        }

    }

    private boolean is_player_holding_consumable(Player player) {
        Inventory inventory = player.getInventory();
        ItemStack heldItem = inventory.getItemInHand();
        if (heldItem == null || heldItem.isEmpty()) {
            return false;
        }
        return heldItem.getItem().isConsumable();
    }

    private boolean is_player_holding_health_buff_consumable(Player player) {
        return healthPreview.getBuffHealValueOfCurrentHeldItem(player) > 0;

    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    private void onMaxHealthChanged(Player player, PlayerState state, HealPreviewHUDGUI healthPreviewGUI)
    {
        update_health_preview_on_hud(player, state, healthPreviewGUI);
        update_current_health_on_hud(state, healthPreviewGUI);
        update_health_buff_preview_on_hud(player, state, healthPreviewGUI);
    }

    private void onCurrentHealthChanged(Player player, PlayerState state, HealPreviewHUDGUI healthPreviewGUI)
    {
        update_health_preview_on_hud(player, state, healthPreviewGUI);
        update_current_health_on_hud(state, healthPreviewGUI);
        update_health_buff_preview_on_hud(player, state, healthPreviewGUI);
    }

    private void onSelectedConsumableOnHotbar(Player player, PlayerState state, HealPreviewHUDGUI healthPreviewGUI)
    {
        clear_health_preview(healthPreviewGUI);
        update_health_preview_on_hud(player, state, healthPreviewGUI);
        update_health_buff_preview_on_hud(player, state, healthPreviewGUI);
    }

    private void onSelectedNonConsumableOnHotbar(HealPreviewHUDGUI healthPreviewGUI)
    {
        clear_health_preview(healthPreviewGUI);
    }

    private void onSelectedEmptySlotOnHotbar(HealPreviewHUDGUI healthPreviewGUI)
    {
        // Only reset the health preview once when switching to empty slot
        // Check if item_in_hand is not null (meaning we had an item before)
        clear_health_preview(healthPreviewGUI);
    }

    private void clear_health_preview(HealPreviewHUDGUI healthPreviewGUI)
    {
        if(healthPreviewGUI != null)
        {
            healthPreviewGUI.updateInstantHealthPreview(0);
            healthPreviewGUI.updateFillBuff(0);
        }
    }

    private void update_current_health_on_hud(PlayerState state, HealPreviewHUDGUI healthPreviewGUI)
    {
        if(healthPreviewGUI != null)
        {
            healthPreviewGUI.setCurrentHealth(state.current_health_percentage);
        }
    }

    // Takes member heal values and updates instant heal preview with it
    private void update_health_preview_on_hud(Player player, PlayerState state, HealPreviewHUDGUI healthPreviewGUI)
    {
        float heal_value_absolute = healthPreview.getInstantHealValueOfCurrentHeldItem(player);
        float heal_value_percent = (heal_value_absolute / state.stored_max_health) + state.current_health_percentage;
        if(heal_value_absolute != 0.0f)
        {
            if(healthPreviewGUI != null)
            {
                healthPreviewGUI.updateInstantHealthPreview(heal_value_percent);
                // Store only the heal portion without current_health_percentage to avoid double-counting
                // when combined with buff heal in on_buff_tick()
                state.instant_heal_value_percent = heal_value_absolute / state.stored_max_health;
            }
        }
    }

    // Takes member heal values and updates buff heal preview with it
    private void update_health_buff_preview_on_hud(Player player, PlayerState state, HealPreviewHUDGUI healthPreviewGUI)
    {
        if(player == null || state == null || healthPreviewGUI == null) return;
        float heal_value_absolute = healthPreview.getBuffHealValueOfCurrentHeldItem(player);
        float heal_value_percent = (heal_value_absolute / state.stored_max_health) + state.current_health_percentage + state.instant_heal_value_percent;
        if(heal_value_absolute != 0.0f)
        {
            healthPreviewGUI.updateFillBuff(heal_value_percent);
        }
    }

    private void on_buff_tick(Player player, PlayerRef playerRef, PlayerState state, HealPreviewHUDGUI healthPreviewGUI)
    {
        if(is_player_holding_health_buff_consumable(player)) return;
        if(player == null || playerRef == null || state == null || healthPreviewGUI == null) return;
        ActiveEntityEffect[] buff_array = healthPreview.getCurrentPlayerBuffs(playerRef);
        if(buff_array == null) return;
        for (ActiveEntityEffect buff : buff_array) {
            if(healthPreview.is_active_entity_effect_a_health_regen_buff(buff))
            {
                float remaining_buff_duration = buff.getRemainingDuration();
                EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(buff.getEntityEffectIndex());
                if (entityEffect != null) {
                    Int2FloatMap entityStatMap = entityEffect.getEntityStats();
                    if (entityStatMap != null) {
                        float item_heal_value = entityStatMap.get(HEALTH);
                        float interval = entityEffect.getDamageCalculatorCooldown();
                        float final_buff_heal_amount = healthPreview.get_final_buff_heal_value(entityEffect.getValueType(),item_heal_value, remaining_buff_duration, interval, state.stored_max_health);
                        float final_buff_heal_amount_percent = (final_buff_heal_amount / state.stored_max_health) + state.current_health_percentage;
                        update_health_preview_on_hud(player, state, healthPreviewGUI);
                        update_current_health_on_hud(state, healthPreviewGUI);
                        float final_heal_amount_percent = normalize_percent_for_ui(final_buff_heal_amount_percent + state.instant_heal_value_percent);
                        healthPreviewGUI.updateFillBuff(final_heal_amount_percent);
                    }
                }
                break;
            }
        }
    }

    private float normalize_percent_for_ui(float value)
    {
        double clamped = Math.max(0.0, Math.min(value, 1.0));
        // Round to nearest 0.05 (5%) to prevent visual jitter from small fluctuations
        // This avoids oscillation when buff heal decreases while current health increases
        double rounded = Math.round(clamped * 20.0) / 20.0;
        return (float) rounded;
    }
}
