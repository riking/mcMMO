package com.gmail.nossr50.runnables.database;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.runnables.Callback;
import com.google.common.base.Function;

public class ReadRankAsyncTask extends BukkitRunnable {
    public final String name;
    public final Function<Map<String, Integer>, ?> callback;

    public ReadRankAsyncTask(String playerName, Function<Map<String, Integer>, ?> callback) {
        this.name = playerName;
        this.callback = callback;
    }

    public void run() {
        Map<String, Integer> skills = mcMMO.getDatabaseManager().readRank(name);

        Bukkit.getScheduler().runTask(mcMMO.p, new Callback<Map<String, Integer>>(callback, skills));
    }
}
