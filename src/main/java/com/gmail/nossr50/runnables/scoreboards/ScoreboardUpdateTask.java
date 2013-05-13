package com.gmail.nossr50.runnables.scoreboards;

import org.bukkit.scheduler.BukkitRunnable;
import com.gmail.nossr50.util.scoreboards.McmmoPlayerScoreboard;
import com.gmail.nossr50.util.scoreboards.ScoreboardManager;

public class ScoreboardUpdateTask extends BukkitRunnable {
    private String player;
    private SkillType skill;
    private boolean levelChange;

    public ScoreboardUpdateTask(String player, SkillType skill, boolean levelChange) {
        this.player = player;
        this.skill = skill;
        this.levelChange = levelChange;
    }

    @Override
    public void run() {
        if (levelChange) {
            ScoreboardManager.handleLevelChange(player, skill);
        } else {
            ScoreboardManager.handleXpChange(player, skill);
        }
    }
}
