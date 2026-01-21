package com.frozenara.healpreviewhud.api;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;

import java.lang.reflect.Field;

public class player_api {

    public static boolean is_world_map_open(Player player)
    {
        if (player == null) return false;

        WorldMapTracker tracker = player.getWorldMapTracker();

        try {
            Field field = WorldMapTracker.class.getDeclaredField("clientHasWorldMapVisible");
            field.setAccessible(true);
            return field.getBoolean(tracker);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return false;
        }
    }

}
