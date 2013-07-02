package com.gmail.nossr50.runnables.commands;

import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.google.common.base.Function;

public class McrankCommandDisplayTask implements Function<Map<String, Integer>, Void> {
    private final CommandSender sender;
    private final String playerName;

    public McrankCommandDisplayTask(CommandSender sender, String playerName) {
        this.sender = sender;
        this.playerName = playerName;
    }

    @Override
    public Void apply(Map<String, Integer> skills) {
        Player player = mcMMO.p.getServer().getPlayer(playerName);
        Integer rank;

        sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Heading"));
        sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Player", playerName));

        for (SkillType skill : SkillType.nonChildSkills()) {
            if (player != null && !Permissions.skillEnabled(player, skill)) {
                continue;
            }

            rank = skills.get(skill.name());
            sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Skill", SkillUtils.getSkillName(skill), (rank == null ? LocaleLoader.getString("Commands.mcrank.Unranked") : rank)));
        }

        rank = skills.get("ALL");
        sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Overall", (rank == null ? LocaleLoader.getString("Commands.mcrank.Unranked") : rank)));

        return null;
    }
}
