package com.gmail.nossr50.util.scoreboards;

import java.util.Set;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.runnables.scoreboards.ScoreboardRevertTask;
import com.gmail.nossr50.util.StringUtils;
import com.gmail.nossr50.util.player.UserManager;

/**
 * This class serves as a wrapper around the (Player, Scoreboard) pairing to
 * keep some extra information - such as the reversion to previous
 * scoreboards, the updating behavior, and the scoreboard we want to revert to.
 * <p>
 * Do not store - the information in here is considered transient to each
 * login session.
 */
public class McmmoPlayerScoreboard {
    protected String player;
    protected Scoreboard scoreboard;
    protected ScoreboardUpdater handler;

    protected Scoreboard oldScoreboard = null;
    protected BukkitTask revertTask = null;

    public enum McmmoScoreboardType {
        EMPTY,
        PLAYER_SKILL,
        PLAYER_STATS,
        PLAYER_RANK,
        GLOBAL_RANK,
        INSPECT_ONLINE,
        INSPECT_OFFLINE,
        ;
    }

    public McmmoPlayerScoreboard(String player, Scoreboard toUse) {
        this.player = player;
        scoreboard = toUse;
        handler = ScoreboardUpdaterEmpty.get();
    }

    public String getPlayerName() {
        return player;
    }

    public Player getBukkitPlayer() {
        return Bukkit.getPlayer(player);
    }

    public McMMOPlayer getMcmmoPlayer() {
        return UserManager.getPlayer(player);
    }

    public ScoreboardUpdater getUpdater() {
        return handler;
    }

    public void setUpdater(ScoreboardUpdater u) {
        Validate.notNull(u);
        handler = u;
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    /**
     * Schedule a task to revert the player's scoreboard from ours to its
     * predecessor.
     * <p>
     * After this call, {@link #getOldScoreboard()} will return
     * the revertTo parameter, except in the case that the provided
     * scoreboard is the one returned with {@link #getScoreboard()}, in which
     * case the value will not be changed.
     *
     * @param revertTo scoreboard to revert to when time is up or dismissed
     * @param delay ticks before reversion, or -1 to wait for dismissal
     */
    public void scheduleRevert(Scoreboard revertTo, long delay) {
        if (revertTask != null) {
            revertTask.cancel(); // no premature reversions
        }
        if (revertTo != scoreboard) {
            // don't want to revert to ourself. This will happen.
            oldScoreboard = revertTo;
        }
        if (delay != -1) { // Don't schedule a task if indefinite
            revertTask = Bukkit.getScheduler().runTaskLater(mcMMO.p, new ScoreboardRevertTask(this), delay * 20); // seconds * 20
        } else {
            revertTask = null;
        }
    }

    public void cancelRevertTask() {
        if (revertTask != null) {
            revertTask.cancel();
        }
    }

    public Scoreboard getOldScoreboard() {
        if (oldScoreboard == null) {
            return Bukkit.getScoreboardManager().getMainScoreboard();
        }
        return oldScoreboard;
    }

    public void clearOldScoreboard() {
        oldScoreboard = null;
    }

    /**
     * For use after a player leaves - we should discard our objects just in
     * case they stick around somehow.
     */
    public void clearReferences() {
        cancelRevertTask();
        scoreboard = null;
        oldScoreboard = null;
        handler = null;
    }
}