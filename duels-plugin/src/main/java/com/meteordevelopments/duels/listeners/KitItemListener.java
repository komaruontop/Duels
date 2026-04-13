package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.Permissions;
import com.meteordevelopments.duels.config.Config;
import com.meteordevelopments.duels.core.arena.ArenaManagerImpl;
import com.meteordevelopments.duels.core.kit.edit.KitEditManager;
import com.meteordevelopments.duels.util.Log;
import com.meteordevelopments.duels.util.StringUtil;
import com.meteordevelopments.duels.util.compat.Identifiers;
import com.meteordevelopments.duels.util.kitguard.KitGuardManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Prevents players from using kit items outside a duel by checking
 * for an NBT tag stored in the item by Duels.
 */
public class KitItemListener implements Listener {

    private static final String MSG_PLAYER  = "&4[Duels] &cKit item removed — kit contents cannot be used outside a duel."; // Some new AI messages, thx.
    private static final String MSG_CONSOLE = "[KitGuard] %s (%s) triggered %s with kit item '%s' at %s [%d,%d,%d] | item owner: %s (%s)";
    private static final String MSG_ADMIN   = "&c[KitGuard] &f%s &ctriggered &f%s &cwith kit item &f%s &c| owner: &f%s";

    private final DuelsPlugin plugin;
    private final ArenaManagerImpl arenaManager;
    private final Config config;
    private final KitGuardManager guard;

    public KitItemListener(final DuelsPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.config = plugin.getConfiguration();
        this.guard = KitGuardManager.init(plugin);

        if (config.isProtectKitItems())
            Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean isExcluded(final Player player) {
        return player.hasPermission(Permissions.ADMIN)
                || arenaManager.isInMatch(player)
                || KitEditManager.getInstance().isEditing(player);
    }

    private boolean isKitItem(final ItemStack item) {
        return item != null && item.getType() != Material.AIR && Identifiers.hasIdentifier(item);
    }

    private void handleViolation(Player player, String eventType, ItemStack item) {
        player.sendMessage(StringUtil.color(MSG_PLAYER));

        if (!guard.isDetectionEnabled())
            return;

        String ownerUUID = Identifiers.getOwnerUUID(item);
        String ownerName = Identifiers.getOwnerName(item);
        String itemName = item.getType().name();
        Location loc = player.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        if (config.isKitGuardLogDetections())
            Log.warn(String.format(MSG_CONSOLE, player.getName(), player.getUniqueId(), eventType, itemName, world, x, y, z, ownerName != null ? ownerName : "?", ownerUUID != null ? ownerUUID   : "?"));

        guard.logViolation(player, eventType, itemName, ownerUUID, ownerName, world, x, y, z);

        if (config.isKitGuardAlertAdmins()) {
            String adminMsg = StringUtil.color(String.format(MSG_ADMIN, player.getName(), eventType, itemName, ownerName != null ? ownerName : "?"));
            Bukkit.getOnlinePlayers().stream() // Notice
                    .filter(p -> !p.equals(player) && p.hasPermission(Permissions.ADMIN))
                    .forEach(p -> p.sendMessage(adminMsg));
        }
    }

    private void handleMachineViolation(Inventory source, ItemStack item) {
        String ownerUUID = Identifiers.getOwnerUUID(item);
        String ownerName = Identifiers.getOwnerName(item);
        String itemName = item.getType().name();

        if (config.isKitGuardLogDetections())
            Log.warn(String.format("[KitGuard] Mechanism attempted to move kit item '%s' | owner: %s (%s)", itemName, ownerName != null ? ownerName : "?", ownerUUID != null ? ownerUUID   : "?"));

        if (guard.isDetectionEnabled() && ownerUUID != null)
            guard.logMachineViolation(ownerUUID, ownerName, "MECHANISM_MOVE", itemName);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < source.getSize(); i++) {
                final ItemStack slot = source.getItem(i);
                if (isKitItem(slot))
                    source.setItem(i, null);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (isExcluded(player))
            return;

        Inventory clicked = event.getClickedInventory();

        // Player's inv
        if (clicked instanceof PlayerInventory) {
            ItemStack item = event.getCurrentItem();
            if (!isKitItem(item))
                return;
            event.setCancelled(true);
            event.setCurrentItem(null);
            handleViolation(player, "INV_CLICK", item);
            return;
        }

        // Another inv
        if (config.isKitGuardBlockChestTransfer() && clicked != null) {
            ItemStack cursor = event.getCursor();
            if (isKitItem(cursor)) {
                event.setCancelled(true);
                ItemStack snapshot = cursor.clone();
                Bukkit.getScheduler().runTask(plugin, () -> { // Burn it down, yeah
                    event.getView().setCursor(null);
                    player.updateInventory();
                });
                handleViolation(player, "CHEST_PLACE", snapshot);
                return;
            }
        }

        // Fast move (shift)
        if (config.isKitGuardBlockChestTransfer() // Idk why it highlights as always false
                && event.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY) && clicked instanceof PlayerInventory) {
            ItemStack item = event.getCurrentItem();
            if (!isKitItem(item))
                return;
            event.setCancelled(true);
            event.setCurrentItem(null);
            handleViolation(player, "SHIFT_TO_CHEST", item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (isExcluded(player)) return;
        final ItemStack item = event.getItem();
        if (!isKitItem(item)) return;
        event.setCancelled(true);
        player.getInventory().remove(item);
        handleViolation(player, "INTERACT", item); // Just handleViolation
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(final EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (isExcluded(player))
            return;
        Item itemEntity = event.getItem();
        if (!isKitItem(itemEntity.getItemStack())) //
            return;
        event.setCancelled(true);
        itemEntity.remove();
        handleViolation(player, "PICKUP", itemEntity.getItemStack());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(final BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack item = event.getItemInHand();
        if (!isKitItem(item))
            return;
        event.setCancelled(true);
        player.getInventory().remove(item);
        handleViolation(player, "BLOCK_PLACE", item); // Just handleViolation
    }

    // Drop
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerDropItemEvent event) {
        if (!config.isKitGuardBlockDrop())
            return;
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack item = event.getItemDrop().getItemStack();
        if (!isKitItem(item))
            return;
        event.setCancelled(true);
        player.getInventory().remove(item);
        handleViolation(player, "DROP", item);
    }

    // Swapping
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerSwapHandItemsEvent event) {
        if (!config.isKitGuardBlockOffhand())
            return;
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack main = event.getMainHandItem();
        ItemStack offhand = event.getOffHandItem();
        if (!isKitItem(main) && !isKitItem(offhand))
            return;
        event.setCancelled(true);
        ItemStack offending = null;
        if (isKitItem(main)) {
            offending = main.clone();
            player.getInventory().setItemInMainHand(null);
        }
        if (isKitItem(offhand)) {
            if (offending == null)
                offending = offhand.clone();
            player.getInventory().setItemInOffHand(null);
        }
        handleViolation(player, "OFFHAND_SWAP", offending);
    }

    // Armor_stand
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerArmorStandManipulateEvent event) {
        if (!config.isKitGuardBlockArmorStand())
            return;
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack playerItem = event.getPlayerItem();
        ItemStack standItem  = event.getArmorStandItem();
        if (!isKitItem(playerItem) && !isKitItem(standItem))
            return;
        event.setCancelled(true);
        ItemStack offending = null;
        if (isKitItem(playerItem)) {
            offending = playerItem.clone();
            player.getInventory().setItemInMainHand(null);
        }
        if (isKitItem(standItem)) {
            if (offending == null)
                offending = standItem.clone();
            event.getRightClicked().setItem(event.getSlot(), null);
        }
        handleViolation(player, "ARMOR_STAND", offending);
    }

    // Crafting
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PrepareItemCraftEvent event) {
        if (!config.isKitGuardBlockCraft())
            return;
        if (!(event.getView().getPlayer() instanceof Player player))
            return;
        if (isExcluded(player))
            return;
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (!Identifiers.anyHasIdentifier(matrix))
            return;

        event.getInventory().setResult(null);

        Bukkit.getScheduler().runTask(plugin, () -> { // Burn. It. Down.
            ItemStack[] current = event.getInventory().getMatrix();
            for (int i = 0; i < current.length; i++) {
                if (isKitItem(current[i]))
                    current[i] = null;
            }
            event.getInventory().setMatrix(current);
            player.updateInventory();
        });

        ItemStack offending = findFirst(matrix);
        if (offending != null)
            handleViolation(player, "CRAFT", offending);
    }

    // Mechanisms
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(InventoryMoveItemEvent event) {
        if (!config.isKitGuardBlockChestTransfer())
            return;
        if (!isKitItem(event.getItem()))
            return;
        event.setCancelled(true);
        handleMachineViolation(event.getSource(), event.getItem());
    }

    private static ItemStack findFirst(ItemStack[] items) {
        if (items == null)
            return null;
        for (ItemStack item: items) {
            if (Identifiers.hasIdentifier(item))
                return item;
        }
        return null;
    }
}
