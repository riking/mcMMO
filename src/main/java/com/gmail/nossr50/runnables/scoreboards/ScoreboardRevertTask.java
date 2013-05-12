package com.gmail.nossr50.runnables.scoreboards;

import org.bukkit.scheduler.BukkitRunnable;
import com.gmail.nossr50.util.scoreboards.McmmoPlayerScoreboard;
import com.gmail.nossr50.util.scoreboards.ScoreboardManager;

public class ScoreboardRevertTask extends BukkitRunnable {
    private McmmoPlayerScoreboard wrapper;

    public ScoreboardRevertTask(final McmmoPlayerScoreboard wrapper) {
        this.wrapper = wrapper;
    }

    @Override
    public void run() {
        ScoreboardManager.handleRevert(wrapper);
    }
}
