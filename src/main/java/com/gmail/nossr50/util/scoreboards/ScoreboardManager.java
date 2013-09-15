package com.gmail.nossr50.util.scoreboards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.database.PlayerStat;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.google.common.collect.ImmutableMap;

public class ScoreboardManager {
    static final Map<String, ScoreboardWrapper> PLAYER_SCOREBOARDS = new HashMap<String, ScoreboardWrapper>();

    static final String SIDEBAR_OBJECTIVE = "mcmmo_sidebar"; // do not localize
    static final String POWER_OBJECTIVE = "mcmmo_pwrlvl";

    static final String HEADER_STATS = LocaleLoader.getString("Scoreboard.Header.PlayerStats");
    static final String HEADER_RANK = LocaleLoader.getString("Scoreboard.Header.PlayerRank");
    static final String TAG_POWER_LEVEL = LocaleLoader.getString("Scoreboard.Header.PowerLevel");

    static final String POWER_LEVEL = LocaleLoader.getString("Scoreboard.Misc.PowerLevel");

    static final OfflinePlayer LABEL_POWER_LEVEL = getOfflinePlayer(POWER_LEVEL);
    static final OfflinePlayer LABEL_LEVEL = getOfflinePlayer(LocaleLoader.getString("Scoreboard.Misc.Level"));
    static final OfflinePlayer LABEL_CURRENT_XP = getOfflinePlayer(LocaleLoader.getString("Scoreboard.Misc.CurrentXP"));
    static final OfflinePlayer LABEL_REMAINING_XP = getOfflinePlayer(LocaleLoader.getString("Scoreboard.Misc.RemainingXP"));
    static final OfflinePlayer LABEL_OVERALL = getOfflinePlayer(LocaleLoader.getString("Scoreboard.Misc.Overall"));

    static final Map<SkillType, OfflinePlayer> skillLabels;
    static {
        ImmutableMap.Builder<SkillType, OfflinePlayer> b = ImmutableMap.builder();
        for (SkillType type : SkillType.values()) {
            // Include child skills
            b.put(type, getOfflinePlayer(SkillUtils.getSkillName(type)));
        }
        skillLabels = b.build();
    }

    private static OfflinePlayer getOfflinePlayer(String name) {
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        return Bukkit.getOfflinePlayer(name);
    }

    public enum SidebarType {
        NONE,
        SKILL_BOARD,
        STATS_BOARD,
        RANK_BOARD,
        TOP_BOARD;
    }

    // Listener call-ins

    public static void setupPlayer(Player p) {
        PLAYER_SCOREBOARDS.put(p.getName(), ScoreboardWrapper.create(p));
        enablePowerLevelDisplay(p);
    }

    public static void teardownPlayer(Player p) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.remove(p.getName());
        if (wrapper.revertTask != null) {
            wrapper.revertTask.cancel();
        }
    }

    // Called by ScoreboardWrapper when its Player logs off and an action tries to be performed
    public static void cleanup(ScoreboardWrapper wrapper) {
        PLAYER_SCOREBOARDS.remove(wrapper.playerName);
        if (wrapper.revertTask != null) {
            wrapper.revertTask.cancel();
        }
    }

    public static void handleLevelUp(Player player, SkillType skill) {
        // Selfboards
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());
        if (wrapper.sidebarType == SidebarType.SKILL_BOARD && wrapper.targetSkill == skill && wrapper.isBoardShown()) {
            wrapper.doSidebarUpdateSoon();
        } else if (wrapper.sidebarType == SidebarType.STATS_BOARD && wrapper.isBoardShown()) {
            wrapper.doSidebarUpdateSoon();
        }

        // Otherboards
        String playerName = player.getName();
        for (ScoreboardWrapper w : PLAYER_SCOREBOARDS.values()) {
            if (w.sidebarType == SidebarType.STATS_BOARD && w.targetPlayer == playerName && wrapper.isBoardShown()) {
                wrapper.doSidebarUpdateSoon();
            }
        }
    }

    public static void handleXp(Player player, SkillType skill) {
        // Selfboards
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());
        if (wrapper.sidebarType == SidebarType.SKILL_BOARD && wrapper.targetSkill == skill && wrapper.isBoardShown()) {
            wrapper.doSidebarUpdateSoon();
        }
    }

    public static void enablePowerLevelDisplay(Player player) {
        if (!Config.getInstance().getPowerLevelTagsEnabled()) {
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        Objective objective;

        if (scoreboard.getObjective(DisplaySlot.BELOW_NAME) == null) {
            objective = scoreboard.registerNewObjective(TAG_POWER_LEVEL, "dummy");

            objective.getScore(player).setScore(UserManager.getPlayer(player).getPowerLevel());
            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        } else {
            objective = scoreboard.getObjective(TAG_POWER_LEVEL);

            if (scoreboard.getObjective(TAG_POWER_LEVEL) != null) {
                objective.getScore(player).setScore(UserManager.getPlayer(player).getPowerLevel());
            } else {
                mcMMO.p.debug("Another plugin is using this scoreboard slot, so power levels cannot be enabled."); //TODO: Locale
            }
        }
    }

    public static void enablePlayerSkillScoreboard(McMMOPlayer mcMMOPlayer, SkillType skill) {
        Player bukkitPlayer = mcMMOPlayer.getPlayer();
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(bukkitPlayer.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeSkill(skill);

        changeScoreboard(wrapper, Config.getInstance().getSkillScoreboardTime());
    }

    public static void enablePlayerStatsScoreboard(McMMOPlayer mcMMOPlayer) {
        Player bukkitPlayer = mcMMOPlayer.getPlayer();
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(bukkitPlayer.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeSelfStats();

        changeScoreboard(wrapper, Config.getInstance().getStatsScoreboardTime());
    }

    public static void enablePlayerInspectScoreboard(Player player, PlayerProfile targetProfile) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeInspectStats(targetProfile);

        changeScoreboard(wrapper, Config.getInstance().getInspectScoreboardTime());
    }

    public static void showPlayerRankScoreboard(Player bukkitPlayer, Map<SkillType, Integer> rank) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(bukkitPlayer.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeSelfRank();
        wrapper.acceptRankData(rank);

        changeScoreboard(wrapper, Config.getInstance().getRankScoreboardTime());
    }

    public static void showPlayerRankScoreboardOthers(Player bukkitPlayer, String targetName, Map<SkillType, Integer> rank) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(bukkitPlayer.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeInspectRank(targetName);
        wrapper.acceptRankData(rank);

        changeScoreboard(wrapper, Config.getInstance().getRankScoreboardTime());
    }

    public static void showTopScoreboard(Player player, SkillType skill, int pageNumber, List<PlayerStat> stats) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeTop(skill, pageNumber);
        wrapper.acceptLeaderboardData(stats);

        changeScoreboard(wrapper, Config.getInstance().getTopScoreboardTime());
    }

    public static void showTopPowerScoreboard(Player player, int pageNumber, List<PlayerStat> stats) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeTopPower(pageNumber);
        wrapper.acceptLeaderboardData(stats);

        changeScoreboard(wrapper, Config.getInstance().getTopScoreboardTime());
    }

    private static void changeScoreboard(ScoreboardWrapper wrapper, int displayTime) {
        wrapper.showBoardAndScheduleRevert(displayTime * Misc.TICK_CONVERSION_FACTOR);
    }

    public static void clearBoard(String playerName) {
        PLAYER_SCOREBOARDS.get(playerName).tryRevertBoard();
    }

    public static void keepBoard(String playerName) {
        if (Config.getInstance().getAllowKeepBoard()) {
            PLAYER_SCOREBOARDS.get(playerName).cancelRevert();
        }
    }
}
