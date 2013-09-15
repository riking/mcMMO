package com.gmail.nossr50.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.commands.CommandUtils;
import com.google.common.collect.ImmutableList;

public class McscoreboardCommand implements TabExecutor {
    private static final List<String> SCOREBOARD_TYPES = ImmutableList.of("clear", "rank", "stats", "top");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (CommandUtils.noConsoleUsage(sender)) {
            return true;
        }

        sender.sendMessage("This command is not available in your region. Sorry about that :\\");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        switch (args.length) {
            case 1:
                return StringUtil.copyPartialMatches(args[0], SCOREBOARD_TYPES, new ArrayList<String>(SCOREBOARD_TYPES.size()));
            case 2:
                if (args[0].equalsIgnoreCase("top")) {
                    return StringUtil.copyPartialMatches(args[1], SkillType.SKILL_NAMES, new ArrayList<String>(SkillType.SKILL_NAMES.size()));
                }
                // Fallthrough

            default:
                return ImmutableList.of();
        }
    }

    private void clearScoreboard(Player player) {
        player.setScoreboard(mcMMO.p.getServer().getScoreboardManager().getMainScoreboard());
        player.sendMessage("Your scoreboard has been cleared!"); //TODO: Locale
    }
}
