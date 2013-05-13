package com.gmail.nossr50.util.scoreboards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.scoreboards.McmmoPlayerScoreboard.McmmoScoreboardType;
import com.gmail.nossr50.util.skills.SkillUtils;

public class ScoreboardUpdaterStats implements ScoreboardUpdater {
    private static final ScoreboardUpdaterStats SELF = new ScoreboardUpdaterStats(null);
    private String target;

    public ScoreboardUpdaterStats(String name) {
        target = name;
    }
    public static ScoreboardUpdaterStats getSelfInstance() { return SELF; }


    public boolean needsUpdatingEver() { return true; }
    public boolean needsUpdatingOnSelfLevelUp(SkillType skill) { return false; }
    public boolean needsUpdatingOnSelfXPChange(SkillType skill) { return false; }
    public boolean needsUpdatingOnPlayerLevelUp(String other, SkillType skill) { return true; }

    public void updateScores(McmmoPlayerScoreboard wrapper, Objective obj) {
        // null -> viewer
        String player = this.target == null ? wrapper.getPlayerName() : this.target;

        Player pl = Bukkit.getPlayer(player); // may be null
        McMMOPlayer mcplayer = UserManager.getPlayer(player);
        PlayerProfile profile = mcplayer.getProfile();
        Server server = mcMMO.p.getServer();

        for (SkillType skill : SkillType.values()) {
            if (skill.isChildSkill() || (pl != null && !Permissions.skillEnabled(pl, skill))) {
                continue;
            }

            OfflinePlayer title = server.getOfflinePlayer(SkillUtils.getSkillName(skill));
            int val = profile.getSkillLevel(skill);
            obj.getScore(title).setScore(val);
        }

        OfflinePlayer title = server.getOfflinePlayer(ChatColor.GOLD + "Power Level");
        int pwrlvl = mcplayer.getPowerLevel();
        obj.getScore(title).setScore(pwrlvl);
    }

    public String getObjectiveName() {
        if (target == null) {
            return "most.@SELF";
        }
        return "most." + target;
    }

    public String getCriteriaName() {
        if (target == null) {
            return "most.@SELF";
        }
        return "most." + target;
    }

    public String getHeader() {
        if (target == null) {
            return "mcMMO Stats";
        }
        return "mcMMO Stats - " + target; // length 14+16 = 30 -- okay
    }

    public McmmoScoreboardType getType() {
        return McmmoScoreboardType.PLAYER_STATS;
    }
}
