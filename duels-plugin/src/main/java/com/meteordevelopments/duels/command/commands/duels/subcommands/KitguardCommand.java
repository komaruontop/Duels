package com.meteordevelopments.duels.command.commands.duels.subcommands;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.command.BaseCommand;
import com.meteordevelopments.duels.util.StringUtil;
import com.meteordevelopments.duels.util.kitguard.KitGuardManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// AI-GENERATED (and some beautified by me..)
public class KitguardCommand extends BaseCommand {

    private static List<String> SUB_ARGS = Arrays.asList("on", "off", "status", "log", "clear");

    public KitguardCommand(DuelsPlugin plugin) {
        super(plugin, "kitguard", "kitguard [on|off|status|log [player]|clear [player]]", "Manage kit-item violation detection.", 1, false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        KitGuardManager guard = KitGuardManager.getInstance();

        if (guard == null) {
            sender.sendMessage(ChatColor.RED + "KitGuard is not initialised (protect-kit-items may be disabled).");
            return;
        }

        // Status
        if (args.length <= 1) {
            sendStatus(sender, guard);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "on" -> {
                guard.setDetectionEnabled(true);
                sender.sendMessage(StringUtil.color("&a[KitGuard] Detection &2ENABLED&a."));
            }
            case "off" -> {
                guard.setDetectionEnabled(false);
                sender.sendMessage(StringUtil.color("&c[KitGuard] Detection &4DISABLED&c."));
            }
            case "status" -> sendStatus(sender, guard);
            case "log" -> {
                if (args.length >= 3) {
                    sendPlayerLog(sender, guard, args[2]);
                } else {
                    sendFullLog(sender, guard);
                }
            }
            case "clear" -> {
                if (args.length >= 3)
                    clearPlayer(sender, guard, args[2]);
                else {
                    guard.clearAll();
                    sender.sendMessage(StringUtil.color("&a[KitGuard] All violation records cleared."));
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown sub-argument. Usage: " + getUsage());
        }
    }

    private void sendStatus(CommandSender sender, KitGuardManager guard) {
        sender.sendMessage(StringUtil.color("&b[KitGuard] Status"));
        sender.sendMessage(StringUtil.color("  Detection: " + (guard.isDetectionEnabled() ? "&aENABLED" : "&cDISABLED")));
        sender.sendMessage(StringUtil.color("  Total violations recorded: &f" + guard.totalCount()));
        int players = guard.getAllViolations().size();
        sender.sendMessage(StringUtil.color("  Unique players flagged: &f" + players));
    }

    private void sendFullLog(CommandSender sender, KitGuardManager guard) {
        Map<UUID, List<KitGuardManager.Violation>> all = guard.getAllViolations();
        if (all.isEmpty()) {
            sender.sendMessage(StringUtil.color("&a[KitGuard] No violations recorded."));
            return;
        }
        sender.sendMessage(StringUtil.color("&b[KitGuard] All violations (" + guard.totalCount() + " total):"));
        all.forEach((uuid, list) -> {
            Player online = Bukkit.getPlayer(uuid);
            String name = online != null ? online.getName() : uuid.toString().substring(0, 8) + "…";
            sender.sendMessage(StringUtil.color("  &e" + name + " &7(" + list.size() + " violations):"));
            List<KitGuardManager.Violation> recent = list.subList(Math.max(0, list.size() - 5), list.size());
            recent.forEach(v -> sender.sendMessage("    " + ChatColor.GRAY + v.format()));
        });
    }

    private void sendPlayerLog(CommandSender sender, KitGuardManager guard, String name) {
        UUID target = null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null)
            target = online.getUniqueId();
        else {
            for (UUID uuid: guard.getAllViolations().keySet()) {
                List<KitGuardManager.Violation> v = guard.getViolations(uuid);
                if (!v.isEmpty()) {
                    try {
                        var op = Bukkit.getOfflinePlayer(uuid);
                        if (name.equalsIgnoreCase(op.getName())) {
                            target = uuid;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "No violation records found for '" + name + "'.");
            return;
        }

        List<KitGuardManager.Violation> list = guard.getViolations(target);
        if (list.isEmpty()) {
            sender.sendMessage(StringUtil.color("&a[KitGuard] No violations recorded for " + name + "."));
            return;
        }

        sender.sendMessage(StringUtil.color("&b[KitGuard] Violations for " + name + " (" + list.size() + " total):"));
        List<KitGuardManager.Violation> recent = list.subList(Math.max(0, list.size() - 20), list.size());
        recent.forEach(v -> sender.sendMessage(ChatColor.GRAY + "  " + v.format()));
    }

    private void clearPlayer(CommandSender sender, KitGuardManager guard, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            guard.clear(online.getUniqueId());
            sender.sendMessage(StringUtil.color("&a[KitGuard] Cleared violations for " + online.getName() + "."));
            return;
        }
        boolean found = false;
        for (UUID uuid: guard.getAllViolations().keySet()) {
            try {
                if (name.equalsIgnoreCase(Bukkit.getOfflinePlayer(uuid).getName())) {
                    guard.clear(uuid);
                    sender.sendMessage(StringUtil.color("&a[KitGuard] Cleared violations for " + name + "."));
                    found = true;
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        if (!found)
            sender.sendMessage(ChatColor.RED + "No violation records found for '" + name + "'.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2) {
            return SUB_ARGS.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("log") || args[1].equalsIgnoreCase("clear"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
}
