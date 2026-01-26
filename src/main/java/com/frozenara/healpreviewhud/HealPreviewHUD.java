package com.frozenara.healpreviewhud;

import com.buuz135.mhud.MultipleHUD;
import com.frozenara.healpreviewhud.gui.HealPreviewHUDGUI;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.ValueType;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.simple.ApplyEffectInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.Operation;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;


import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class HealPreviewHUD extends JavaPlugin {

    private static HealPreviewHUD instance;
    public static Map<PlayerRef, HealPreviewHUDGUI> playerGuiMap = new HashMap<>();

    public static Map<PlayerRef, HealPreviewHUDGUI> getPlayerGuiMap() {
        return playerGuiMap;
    }

    public static HealPreviewHUD getInstance() {
        return instance;
    }


    public EntityStatValue getPlayerHealthStatData(Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Store<EntityStore> store = ref.getStore();
        EntityStatMap player_entityStatMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (player_entityStatMap == null) {
            return null;
        }

        return player_entityStatMap.get(DefaultEntityStatTypes.getHealth());
    }

    public float getCurrentHealth(Player player) {
        return getPlayerHealthStatData(player).get();
    }

    public float getMaxHealth(Player player) {
        return getPlayerHealthStatData(player).getMax();
    }

    public PlayerRef getPlayerRef(Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        return store.getComponent(ref, PlayerRef.getComponentType());
    }

    public Player getPlayer(PlayerRef playerRef) {
        if (playerRef == null) {
            return null;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        return store.getComponent(ref, Player.getComponentType());
    }

    private record HeldItemEffectData(RootInteraction effect) {
    }

    private HeldItemEffectData getHeldItemEffectData(Player player) {
        Inventory inventory = player.getInventory();
        ItemStack heldItem = inventory.getItemInHand();
        if (heldItem == null || heldItem.isEmpty()) {
            return null;
        }

        Item item = heldItem.getItem();
        if (!item.isConsumable()) {
            return null;
        }

        String interaction_effect = item.getInteractionVars().get("Effect");
        if (interaction_effect == null) {
            return null;
        }

        RootInteraction effect = RootInteraction.getAssetMap().getAsset(interaction_effect);
        if (effect == null) {
            return null;
        }

        return new HeldItemEffectData(effect);
    }

    public HealPreviewHUD(@NonNullDecl JavaPluginInit init) {
        super(init);
        instance = this;
    }

    // FIXME: reduce code reuse with getBuffHealValueOfCurrentHeldItem
    public float getInstantHealValueOfCurrentHeldItem(Player player) {
        HeldItemEffectData data = getHeldItemEffectData(player);
        if (data == null) {
            return 0;
        }

        RootInteraction effect = data.effect();
        float max_player_health = getMaxHealth(player);
        float instant_heal_value = 0;

        for (int i = 0; i < effect.getOperationMax(); i++) {
            Operation operation = effect.getOperation(i);
            if (operation == null) {
                continue;
            }

            Operation inner = operation.getInnerOperation();
            if (inner instanceof ApplyEffectInteraction applyEffect) {
                try {
                    Field field = ApplyEffectInteraction.class.getDeclaredField("effectId");
                    field.setAccessible(true);
                    String effectId = (String) field.get(applyEffect);
                    EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
                    if (entityEffect != null) {
                        Int2FloatMap entityStatMap = entityEffect.getEntityStats();
                        if (entityStatMap != null) {
                            float item_heal_value = entityStatMap.get(DefaultEntityStatTypes.getHealth());
                            if (item_heal_value > 0) {
                                ValueType value_type = entityEffect.getValueType();
                                float buff_duration = entityEffect.getDuration();
                                boolean is_buff = buff_duration > 0.1f;
                                if (!is_buff) {
                                    switch (value_type) {
                                        case ValueType.Absolute -> {
                                            instant_heal_value += item_heal_value;
                                        }
                                        case ValueType.Percent -> {
                                            instant_heal_value += max_player_health * (item_heal_value / 100);
                                        }
                                        default -> {
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        this.getLogger().at(Level.INFO).log("Entity effect is null for effectId: " + effectId);
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    this.getLogger().at(Level.WARNING).log("Failed to access effectId field: " + e.getMessage());
                    continue;
                }
            }
        }

        return instant_heal_value;
    }

    private float check_and_return_max_health_buff(EntityEffect entityEffect)
    {
        // Currently there seems to be no way to actually fetch the buff values of the two max health boost buffs, so we have to do it this way.
        String MEAT_BUFF_T1 = "Meat_Buff_T1";
        String MEAT_BUFF_T2 = "Meat_Buff_T2";

        if(entityEffect.getId().equals(MEAT_BUFF_T1))
        {
            return 0.05f;
        } else if (entityEffect.getId().equals(MEAT_BUFF_T2)) {
            return 0.1f;
        }
        return 0;
    }

    public float getMaxHealthBuffOfCurrentHeldItem(Player player) {
        HeldItemEffectData data = getHeldItemEffectData(player);
        if (data == null) {
            return 0;
        }

        RootInteraction effect = data.effect();

        for (int i = 0; i < effect.getOperationMax(); i++) {
            Operation operation = effect.getOperation(i);
            if (operation == null) {
                continue;
            }

            Operation inner = operation.getInnerOperation();
            if (inner instanceof ApplyEffectInteraction applyEffect) {
                try {
                    Field field = ApplyEffectInteraction.class.getDeclaredField("effectId");
                    field.setAccessible(true);
                    String effectId = (String) field.get(applyEffect);
                    EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
                    if (entityEffect != null) {
                        float maxHealthBuff = check_and_return_max_health_buff(entityEffect);
                        if (maxHealthBuff > 0) {
                            return maxHealthBuff;
                        }
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    continue;
                }
            }
        }
        return 0;
    }

    public float getBuffHealValueOfCurrentHeldItem(Player player) {
        HeldItemEffectData data = getHeldItemEffectData(player);
        if (data == null) {
            return 0;
        }

        RootInteraction effect = data.effect();
        float max_player_health = getMaxHealth(player);
        float buff_heal_value = 0;

        for (int i = 0; i < effect.getOperationMax(); i++) {
            Operation operation = effect.getOperation(i);
            if (operation == null) {
                continue;
            }

            Operation inner = operation.getInnerOperation();
            if (inner instanceof ApplyEffectInteraction applyEffect) {
                try {
                    Field field = ApplyEffectInteraction.class.getDeclaredField("effectId");
                    field.setAccessible(true);
                    String effectId = (String) field.get(applyEffect);
                    EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
                    if (entityEffect != null) {
                        Int2FloatMap entityStatMap = entityEffect.getEntityStats();
                        if (entityStatMap != null) {
                            float item_heal_value = entityStatMap.get(DefaultEntityStatTypes.getHealth());
                            if (item_heal_value > 0) {
                                float buff_duration = entityEffect.getDuration();
                                boolean is_buff = buff_duration > 0.1f;
                                if (is_buff) {
                                    float interval = entityEffect.getDamageCalculatorCooldown();
                                    ValueType value_type = entityEffect.getValueType();
                                    return get_final_buff_heal_value(value_type, item_heal_value, buff_duration, interval, max_player_health);
                                }
                            }
                        }
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    continue;
                }
            }
        }
        return buff_heal_value;
    }


    public float get_final_buff_heal_value(ValueType value_type, float item_heal_value, float buff_duration, float interval, float max_player_health) {
        switch (value_type) {
            case ValueType.Absolute -> {
                float amount_steps = buff_duration / interval;
                return item_heal_value * amount_steps;
            }
            case ValueType.Percent -> {
                float heal_step = max_player_health * (item_heal_value / 100);
                float amount_steps = buff_duration / interval;
                return heal_step * amount_steps;
            }
            default -> {
                return 0;
            }
        }
    }

    public ActiveEntityEffect[] getCurrentPlayerBuffs(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref != null && ref.isValid()) {
            // Get the store from the reference
            Store<EntityStore> store = ref.getStore();

            // Get the EffectControllerComponent using the store
            EffectControllerComponent effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());


            if (effectController != null) {

                // Or get all active effects as an array
                return effectController.getAllActiveEntityEffects();
            }
        }
        return null;
    }

    public boolean is_active_entity_effect_a_health_regen_buff(ActiveEntityEffect buff) {
        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(buff.getEntityEffectIndex());
        if (entityEffect == null) {
            return false;
        }
        Int2FloatMap entityStatMap = entityEffect.getEntityStats();
        if (entityStatMap != null) {
            float item_heal_value = entityStatMap.get(DefaultEntityStatTypes.getHealth());
            if (item_heal_value > 0) {
                float buff_duration = entityEffect.getDuration();
                return buff_duration > 0.1f;
            }
        }
        return false;
    }

    public HealPreviewHUDGUI addCustomHealthHud(Player player) {
        PlayerRef playerRef = getPlayerRef(player);
        if (playerRef == null) {
            this.getLogger().at(Level.WARNING).log("PlayerRef is null");
            return null;
        }
        this.getLogger().at(Level.INFO).log("Adding heal preview hud to player");
        HealPreviewHUDGUI healthPreviewHud = new HealPreviewHUDGUI(playerRef);
        PluginBase plugin = PluginManager.get().getPlugin(PluginIdentifier.fromString("Buuz135:MultipleHUD"));
        if (plugin != null) {
            getInstance().getLogger().at(Level.INFO).log("MultipleHUD plugin found. Using MultipleHUD to add custom hud.");
            MultipleHUD.getInstance().setCustomHud(player, playerRef, "HealPreviewHUD", healthPreviewHud);
        }
        else
        {
            getInstance().getLogger().at(Level.INFO).log("MultipleHUD plugin not found. Using custom hud solo.");
            player.getHudManager().setCustomHud(playerRef, healthPreviewHud);
        }
        healthPreviewHud.show();
        return healthPreviewHud;

    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> playerRefStore = event.getPlayerRef();
        Store<EntityStore> store = playerRefStore.getStore();
        PlayerRef playerRef = store.getComponent(playerRefStore, PlayerRef.getComponentType());
        if (playerRef == null) {
            this.getLogger().at(Level.WARNING).log("Unable to find player.");
            return;
        }
        Player player = getPlayer(playerRef);
        if (player == null) {
            this.getLogger().at(Level.WARNING).log("Unable to find player.");
            return;
        }
        HealPreviewHUDGUI healthPreviewHud = this.addCustomHealthHud(player);
        if (healthPreviewHud != null) {
            healthPreviewHud.show();
            playerGuiMap.put(playerRef, healthPreviewHud);

            float current_health = getCurrentHealth(player);
            float max_health = getMaxHealth(player);
            healthPreviewHud.setCurrentHealth(current_health / max_health);
            player.getHudManager().hideHudComponents(playerRef, HudComponent.Health);
            if(player.getGameMode() == GameMode.Creative)
            {
                healthPreviewHud.setCreativeMode(true);
            }
            healthPreviewHud.updateUI(true);



        }
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        Ref<EntityStore> ref = playerRef.getReference();

        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                Player player = getPlayer(playerRef);
                if (player == null) {
                    this.getLogger().at(Level.WARNING).log("Unable to find player.");
                }
                PlayerTickSystemHealthPreview.remove_player_state(playerRef);
                playerGuiMap.remove(playerRef);
            });
        }
    }

    @Override
    protected void setup()
    {
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        this.getEntityStoreRegistry().registerSystem(new PlayerTickSystemHealthPreview());
    }
}
