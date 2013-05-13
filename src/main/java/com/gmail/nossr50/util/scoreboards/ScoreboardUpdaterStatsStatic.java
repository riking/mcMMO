package com.gmail.nossr50.util.scoreboards;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.scoreboard.Objective;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.skills.SkillUtils;

public class ScoreboardUpdaterStatsStatic extends ScoreboardUpdaterStats {
    PlayerProfile profile;

    public ScoreboardUpdaterStatsStatic(PlayerProfile profile) {
        super(profile.getPlayerName());
        this.profile = profile;
    }

    @Override
    public boolean needsUpdatingEver() { return false;}
    @Override
    public boolean needsUpdatingOnPlayerLevelUp(String other, SkillType skill) { return false; }

    public void updateScores(McmmoPlayerScoreboard wrapper, Objective obj) {
        Server server = mcMMO.p.getServer();

        int powerLevel = 0; // lol calculate it
        for (SkillType skill : SkillType.values()) {
            if (skill.isChildSkill()) {
                continue;
            }
            OfflinePlayer title = server.getOfflinePlayer(SkillUtils.getSkillName(skill));
            int val = profile.getSkillLevel(skill);
            powerLevel += val;
            obj.getScore(title).setScore(val);
        }
        OfflinePlayer title = server.getOfflinePlayer(ChatColor.GOLD + "Power Level");
        obj.getScore(title).setScore(powerLevel);
    }
}
