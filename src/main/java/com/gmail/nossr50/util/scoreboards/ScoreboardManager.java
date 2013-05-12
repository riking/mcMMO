package com.gmail.nossr50.util.scoreboards;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.StringUtils;

public class ScoreboardManager {
    private static final Map<String, McmmoPlayerScoreboard> PLAYER_SCOREBOARDS = new HashMap<String, McmmoPlayerScoreboard>();

    public static void setupPlayerScoreboard(String playerName) {
        if (PLAYER_SCOREBOARDS.containsKey(playerName)) {
            return;
        }

        PLAYER_SCOREBOARDS.put(playerName, new McmmoPlayerScoreboard(playerName, mcMMO.p.getServer().getScoreboardManager().getNewScoreboard()));
    }

    public static void discardPlayerScoreboard(String playerName) {
        McmmoPlayerScoreboard tmp = PLAYER_SCOREBOARDS.remove(playerName);
        if (tmp == null) return;
        tmp.clearReferences(); // just in case
    }


    // UTILITY METHODS

    /**
     * Make sure that the right objective exists on this player's scoreboard.
     * Don't display it -- just create the objective.
     * @param wrapper player-scoreboard wrapper object
     * @return the objective we got or created
     */
    public static Objective ensureObjectiveExists(McmmoPlayerScoreboard wrapper) {
        Scoreboard sc = wrapper.getScoreboard();
        ScoreboardUpdater updater = wrapper.getUpdater();
        String objctv = StringUtils.truncate(updater.getObjectiveName(), 16);
        Objective primary = sc.getObjective(objctv);
        if (primary == null) {
            primary = sc.registerNewObjective(objctv, StringUtils.truncate(updater.getCriteriaName(), 16));
            primary.setDisplayName(StringUtils.truncate(updater.getHeader(), 32));
        }
        return primary;
    }

    private static void displayAndUpdate(McmmoPlayerScoreboard wrapper, ScoreboardUpdater updater) {
        wrapper.getBukkitPlayer().setScoreboard(wrapper.getScoreboard());
        wrapper.setUpdater(updater);
        Objective obj = ensureObjectiveExists(wrapper);
        updater.updateScores(wrapper, obj);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    // ENTRY POINTS

    public static void handleLevelChange(String player, SkillType skill) {
        for (McmmoPlayerScoreboard wrapper : PLAYER_SCOREBOARDS.values()) {
            ScoreboardUpdater updater = wrapper.getUpdater();
            if (!updater.needsUpdatingEver()) continue;
            if (updater.needsUpdatingOnPlayerLevelUp(player, skill) || (player == wrapper.getPlayerName() && updater.needsUpdatingOnSelfLevelUp(skill))) {
                Objective obj = ensureObjectiveExists(wrapper);
                updater.updateScores(wrapper, obj);
            }
        }
    }

    public static void handleXpChange(String player, SkillType skill) {
        McmmoPlayerScoreboard wrapper = PLAYER_SCOREBOARDS.get(player);
        if (wrapper == null) return;
        ScoreboardUpdater updater = wrapper.getUpdater();
        if (!updater.needsUpdatingEver()) return;
        if (updater.needsUpdatingOnSelfXPChange(skill)) {
            Objective obj = ensureObjectiveExists(wrapper);
            updater.updateScores(wrapper, obj);
        }
    }

    public static void enablePlayerSkillScoreboard(McMMOPlayer mcMMOPlayer, SkillType skill) {
        Player player = mcMMOPlayer.getPlayer();
        Scoreboard oldScoreboard = player.getScoreboard();
        McmmoPlayerScoreboard wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        displayAndUpdate(wrapper, new ScoreboardUpdaterSkill(skill));
        wrapper.scheduleRevert(oldScoreboard, Config.getInstance().getSkillScoreboardTime());
    }

    public static void enablePlayerStatsScoreboard(McMMOPlayer mcMMOPlayer) {
        Player player = mcMMOPlayer.getPlayer();
        Scoreboard oldScoreboard = player.getScoreboard();
        McmmoPlayerScoreboard wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        displayAndUpdate(wrapper, ScoreboardUpdaterStats.getSelfInstance());
        wrapper.scheduleRevert(oldScoreboard, Config.getInstance().getMcstatsScoreboardTime());
    }

    public static void enablePlayerRankScoreboard(Player player) {
        enablePlayerRankScoreboard(player, player.getName());
    }

    public static void enablePlayerRankScoreboard(Player player, String targetName) {
        Scoreboard oldScoreboard = player.getScoreboard();
        McmmoPlayerScoreboard wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        displayAndUpdate(wrapper, new ScoreboardUpdaterRank(targetName));
        wrapper.scheduleRevert(oldScoreboard, Config.getInstance().getMcrankScoreboardTime());
    }

    public static void enablePlayerInspectScoreboardOnline(Player player, String targetName) {
        Scoreboard oldScoreboard = player.getScoreboard();
        McmmoPlayerScoreboard wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        displayAndUpdate(wrapper, new ScoreboardUpdaterStats(targetName));
        wrapper.scheduleRevert(oldScoreboard, Config.getInstance().getInspectScoreboardTime());
    }

    public static void enablePlayerInspectScoreboardOffline(Player player, PlayerProfile targetProfile) {
        Scoreboard oldScoreboard = player.getScoreboard();
        McmmoPlayerScoreboard wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        displayAndUpdate(wrapper, new ScoreboardUpdaterStatsStatic(targetProfile));
        int delay = Config.getInstance().getInspectScoreboardTime();
        if (delay == -1) {
            delay = 90; // If they log in during this, the static gets messed up, so we don't want indefinite
        }
        wrapper.scheduleRevert(oldScoreboard, delay);
    }

    public static void enableGlobalStatsScoreboard(Player player, String skill, int pageNumber) {
        Scoreboard oldScoreboard = player.getScoreboard();
        McmmoPlayerScoreboard wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        displayAndUpdate(wrapper, new ScoreboardUpdaterGlobalRank(skill, pageNumber));
        wrapper.scheduleRevert(oldScoreboard, Config.getInstance().getMctopScoreboardTime());
    }

    public static void handleRevert(McmmoPlayerScoreboard wrapper) {
        Player p = wrapper.getBukkitPlayer();
        if (p != null) {
            p.setScoreboard(wrapper.getOldScoreboard());
        }
        wrapper.clearOldScoreboard();
        wrapper.setUpdater(ScoreboardUpdaterEmpty.get());
    }

    public static void doRevert(Player p) {
        McmmoPlayerScoreboard wrapper = PLAYER_SCOREBOARDS.get(p.getName());
        p.setScoreboard(wrapper.getOldScoreboard());
        wrapper.clearOldScoreboard();
        wrapper.setUpdater(ScoreboardUpdaterEmpty.get());
    }
}
