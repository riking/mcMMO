package com.gmail.nossr50.runnables.database;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.datatypes.database.PlayerStat;
import com.gmail.nossr50.runnables.Callback;
import com.google.common.base.Function;

public class ReadLeaderboardAsyncTask extends BukkitRunnable {
    private Function<List<PlayerStat>, ?> callback;
    private String skill;
    private int page;

    public ReadLeaderboardAsyncTask(int page, String skill, Function<List<PlayerStat>, ?> callback) {
        this.page = page;
        this.skill = skill;
        this.callback = callback;
    }

    @Override
    public void run() {
        final List<PlayerStat> userStats = mcMMO.getDatabaseManager().readLeaderboard(skill, page, 10);

        Bukkit.getScheduler().runTaskAsynchronously(mcMMO.p, new Callback<List<PlayerStat>>(callback, userStats));
    }
}
