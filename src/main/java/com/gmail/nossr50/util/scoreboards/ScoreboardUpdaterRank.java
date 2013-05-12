package com.gmail.nossr50.util.scoreboards;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.database.FlatfileDatabaseManager;
import com.gmail.nossr50.database.SQLDatabaseManager;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.scoreboards.McmmoPlayerScoreboard.McmmoScoreboardType;
import com.gmail.nossr50.util.skills.SkillUtils;


public class ScoreboardUpdaterRank implements ScoreboardUpdater {
    private static final ScoreboardUpdaterRank SELF = new ScoreboardUpdaterRank(null);
    private String target;

    public ScoreboardUpdaterRank(String player) {
        this.target = player;
    }
    public static ScoreboardUpdaterRank getSelfInstance() { return SELF; }

    public boolean needsUpdatingEver() { return true; }
    public boolean needsUpdatingOnSelfLevelUp(SkillType s) { return false; }
    public boolean needsUpdatingOnSelfXPChange(SkillType s) { return false; }
    public boolean needsUpdatingOnPlayerLevelUp(String other, SkillType s) {
        return other == target;
    }

    public void updateScores(McmmoPlayerScoreboard wrapper, Objective obj) {
        // null -> viewer
        String player = this.target == null ? wrapper.getPlayerName() : this.target;

        Map<String, Integer> skillranks = Config.getInstance().getUseMySQL() ? SQLDatabaseManager.readSQLRank(player) : FlatfileDatabaseManager.getPlayerRanks(player);

        Player pl = Bukkit.getPlayer(player); // may be null
        Server server = mcMMO.p.getServer();

        for (SkillType skill : SkillType.values()) {
            if (skill.isChildSkill() || (pl != null && !Permissions.skillEnabled(pl, skill))) {
                continue;
            }
            Integer rank = skillranks.get(skill.name());
            if (rank != null) {
                OfflinePlayer title = server.getOfflinePlayer(SkillUtils.getSkillName(skill));
                obj.getScore(title).setScore(rank);
            }
        }
        Integer rank = skillranks.get("ALL");
        if (rank != null) {
            OfflinePlayer title = server.getOfflinePlayer(ChatColor.GOLD + "Overall");
            obj.getScore(title).setScore(rank);
        }
    }

    public String getObjectiveName() {
        return "mork." + target;
    }

    public String getCriteriaName() {
        return "mork." + target;
    }

    public String getHeader() {
        return "mcMMO Rankings: " + target; // length 16+16 = 36 -- exact max
    }

    public McmmoScoreboardType getType() {
        return McmmoScoreboardType.PLAYER_RANK;
    }

}
