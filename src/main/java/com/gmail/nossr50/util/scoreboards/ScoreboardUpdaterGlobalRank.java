package com.gmail.nossr50.util.scoreboards;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.scoreboard.Objective;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.database.FlatfileDatabaseManager;
import com.gmail.nossr50.database.SQLDatabaseManager;
import com.gmail.nossr50.datatypes.database.PlayerStat;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.scoreboards.McmmoPlayerScoreboard.McmmoScoreboardType;
import com.gmail.nossr50.util.skills.SkillUtils;

public class ScoreboardUpdaterGlobalRank implements ScoreboardUpdater {
    private SkillType type;
    private String skillName;
    private int page;
    public ScoreboardUpdaterGlobalRank(String skill, int pageNumber) {
        skillName = skill;
        if (!skill.equalsIgnoreCase("all")) {
            type = SkillType.getSkill(skill);
        } else {
            type = null;
        }
        page = pageNumber;
    }

    public boolean needsUpdatingEver() { return true; }
    public boolean needsUpdatingOnSelfLevelUp(SkillType skill) { return false; }
    public boolean needsUpdatingOnSelfXPChange(SkillType skill) { return false; }
    public boolean needsUpdatingOnPlayerLevelUp(String other, SkillType skill) {
        if (type == null) {
            return true;
        } else if (type == skill) {
            return true;
        }
        return false;
    }

    public void updateScores(McmmoPlayerScoreboard wrapper, Objective obj) {
        Server server = mcMMO.p.getServer();

        if (Config.getInstance().getUseMySQL()) {
            String tablePrefix = Config.getInstance().getMySQLTablePrefix();
            String query = (skillName.equalsIgnoreCase("all") ? "taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing" : skillName);
            // TODO move to util class (re: database overhaul)
            final Collection<ArrayList<String>> userStats = SQLDatabaseManager.read("SELECT " + query + ", user, NOW() FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON (user_id = id) WHERE " + query + " > 0 ORDER BY " + query + " DESC, user LIMIT " + ((page * 15) - 15) + ",15").values();

            for (ArrayList<String> stat : userStats) {
                String playerName = stat.get(1);
                playerName = (playerName.equals(wrapper.getPlayerName()) ? ChatColor.GOLD : "") + playerName;

                if (playerName.length() > 16) {
                    playerName = playerName.substring(0, 16);
                }

                OfflinePlayer title = server.getOfflinePlayer(playerName);
                int val = Integer.parseInt(stat.get(0));
                obj.getScore(title).setScore(val);
            }
        } else {
            for (PlayerStat stat : FlatfileDatabaseManager.retrieveInfo(skillName, page, 15)) {
                String playerName = stat.name;
                playerName = (playerName.equals(wrapper.getPlayerName()) ? ChatColor.GOLD : "") + playerName;

                if (playerName.length() > 16) {
                    playerName = playerName.substring(0, 16);
                }

                OfflinePlayer title = server.getOfflinePlayer(playerName);
                int val = stat.statVal;
                obj.getScore(title).setScore(val);
            }
        }
    }

    public String getObjectiveName() {
        if (type == null) {
            return "motp." + page;
        }
        return "motp." + page + "." + SkillUtils.getSkillName(type); // name at end because longest
    }

    public String getCriteriaName() {
        if (type == null) {
            return "motp";
        }
        return "motp." + SkillUtils.getSkillName(type);
    }

    public String getHeader() {
        String ret;
        if (type == null) {
            ret = ChatColor.GOLD + "Power Level" + " (" + page + ")";
        } else {
            ret = ChatColor.GOLD + SkillUtils.getSkillName(type) + " (" + page + ")";
        }
        return ret;
    }

    public McmmoScoreboardType getType() {
        return McmmoScoreboardType.GLOBAL_RANK;
    }
}
