package com.meteordevelopments.duels.util.compat;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.util.compat.nbt.NBT;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public final class Identifiers {

    private static final String KEY_KIT_CONTENT  = "DuelsKitContent";
    private static final String KEY_OWNER_UUID   = "DuelsOwnerUUID"; // New NBTs
    private static final String KEY_OWNER_NAME   = "DuelsOwnerName";

    private Identifiers() {
    }

    public static ItemStack addIdentifier(final ItemStack item) {
        if (CompatUtil.isPre1_14()) {
            return NBT.setItemString(item, KEY_KIT_CONTENT, true);
        }
        final ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key(KEY_KIT_CONTENT), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean hasIdentifier(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) // Some checks
            return false;
        if (CompatUtil.isPre1_14()) {
            return NBT.hasItemKey(item, KEY_KIT_CONTENT);
        }
        return item.getItemMeta().getPersistentDataContainer().has(key(KEY_KIT_CONTENT), PersistentDataType.BYTE);
    }

    public static ItemStack removeIdentifier(final ItemStack item) {
        if (CompatUtil.isPre1_14()) {
            return NBT.removeItemTag(item, KEY_KIT_CONTENT);
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(key(KEY_KIT_CONTENT));
        pdc.remove(key(KEY_OWNER_UUID));
        pdc.remove(key(KEY_OWNER_NAME));
        item.setItemMeta(meta);
        return item;
    }

    public static void tagOwnerInInventory(Player player) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        for (ItemStack item: player.getInventory().getContents()) {
            if (hasIdentifier(item))
                addOwner(item, uuid, name);
        }
        player.updateInventory(); // Marked unstable..
    }

    public static ItemStack addOwner(final ItemStack item, final String uuid, final String name) {
        if (CompatUtil.isPre1_14()) return item; // pre-1.14 path: no PDC, skip owner tag
        final ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key(KEY_OWNER_UUID), PersistentDataType.STRING, uuid);
        pdc.set(key(KEY_OWNER_NAME), PersistentDataType.STRING, name);
        item.setItemMeta(meta);
        return item;
    }

    public static String getOwnerUUID(ItemStack item) {
        if (item == null || !item.hasItemMeta() || CompatUtil.isPre1_14()) // Idk, let it be here..
            return null;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(key(KEY_OWNER_UUID), PersistentDataType.STRING, null);
    }

    public static String getOwnerName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || CompatUtil.isPre1_14()) // Same as getOwnerUUID
            return null;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(key(KEY_OWNER_NAME), PersistentDataType.STRING, null);
    }

    private static NamespacedKey key(String id) {
        return new NamespacedKey(DuelsPlugin.getInstance(), id);
    }

    public static boolean anyHasIdentifier(ItemStack[] items) {
        if (items == null)
            return false;
        return Arrays.stream(items).anyMatch(Identifiers::hasIdentifier);
    }
}
