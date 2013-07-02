package com.gmail.nossr50.runnables.commands;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.gmail.nossr50.datatypes.database.PlayerStat;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.StringUtils;
import com.google.common.base.Function;

public class MctopCommandDisplayTask implements Function<List<PlayerStat>, Void> {
    private CommandSender sender;
    private String skill;
    private int page;

    public MctopCommandDisplayTask(int page, String skill, CommandSender sender) {
        this.page = page;
        this.skill = skill;
        this.sender = sender;
    }

    @Override
    public Void apply(List<PlayerStat> userStats) {
        if (skill.equalsIgnoreCase("all")) {
            sender.sendMessage(LocaleLoader.getString("Commands.PowerLevel.Leaderboard"));
        }
        else {
            sender.sendMessage(LocaleLoader.getString("Commands.Skill.Leaderboard", StringUtils.getCapitalized(skill)));
        }

        int place = (page * 10) - 9;

        for (PlayerStat stat : userStats) {
            String digit = ((place < 10) ? "0" : "") + String.valueOf(place);

            // Format: 1. Playername - skill value
            sender.sendMessage(digit + ". " + ChatColor.GREEN + stat.name + " - " + ChatColor.WHITE + stat.statVal);
            place++;
        }

        sender.sendMessage(LocaleLoader.getString("Commands.mctop.Tip"));
        return null;
    }
}
