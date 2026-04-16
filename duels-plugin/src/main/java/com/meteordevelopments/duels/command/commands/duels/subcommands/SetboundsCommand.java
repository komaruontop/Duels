package com.meteordevelopments.duels.command.commands.duels.subcommands;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.command.BaseCommand;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.util.StringUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

// AI GENERATED and some beautified by me.
public class SetboundsCommand extends BaseCommand {
    public SetboundsCommand(DuelsPlugin plugin) {
        super(plugin, "setbounds", "setbounds [arena] [min|max]", "Sets the bounding box of an arena.", 3, true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String corner = args[args.length - 1].toLowerCase();

        if (!corner.equals("min") && !corner.equals("max")) {
            sender.sendMessage(StringUtil.color("&cUsage: /" + label + " " + getUsage()));
            return;
        }

        String name = StringUtil.join(args, " ", 1, args.length - 1).replace("-", " ");
        ArenaImpl arena = arenaManager.get(name);

        if (arena == null) {
            lang.sendMessage(sender, "ERROR.arena.not-found", "name", name);
            return;
        }

        Player player = (Player) sender;
        Location location = player.getLocation().clone();
        arena.setBound(corner.equals("min"), location);

        sender.sendMessage(StringUtil.color("&aSet &e" + corner + "&a bound of arena &e" + arena.getName() + "&a to &f" + StringUtil.parse(location) + "&a."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2)
            return handleTabCompletion(args[1], arenaManager.getNames());
        if (args.length > 2)
            return Arrays.asList("min", "max");
        return null;
    }
}
