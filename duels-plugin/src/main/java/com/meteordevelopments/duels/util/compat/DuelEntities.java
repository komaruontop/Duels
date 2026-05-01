package com.meteordevelopments.duels.util.compat;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

// AI generated
public final class DuelEntities {

    private static final String KEY_ARENA = "Duels-Entity-Arena";

    private DuelEntities() {
    }

    public static void mark(Entity entity, ArenaImpl arena) {
        mark(entity, arena.getName());
    }

    public static void mark(Entity entity, String arenaName) {
        DuelsPlugin plugin = DuelsPlugin.getInstance();
        if (plugin == null)
            return;
        entity.setMetadata(KEY_ARENA, new FixedMetadataValue(plugin, arenaName));
    }

    public static String getArenaName(Entity entity) {
        if (!entity.hasMetadata(KEY_ARENA))
            return null;
        List<MetadataValue> values = entity.getMetadata(KEY_ARENA);
        for (MetadataValue v: values) {
            if (v.getOwningPlugin() == DuelsPlugin.getInstance())
                return v.asString();
        }
        return null;
    }

    public static boolean isDuelEntity(Entity entity) {
        return getArenaName(entity) != null;
    }

    public static boolean isForeignTarget(Entity entity, ArenaImpl playerArena) {
        String marker = getArenaName(entity);
        if (marker == null)
            return false;
        return playerArena == null || !marker.equals(playerArena.getName());
    }
}
