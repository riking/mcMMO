package com.gmail.nossr50.runnables.commands;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.database.SQLDatabaseManager;

public class MctopCommandAsyncTask extends BukkitRunnable {
    private CommandSender sender;
    private String skill;
    private int page;

    public MctopCommandAsyncTask(int page, String skill, CommandSender sender) {
        this.page = page;
        this.skill = skill;
        this.sender = sender;
    }

    @Override
    public void run() { 
        final Collection<ArrayList<String>> userStats = SQLDatabaseManager.readLeaderboard(skill, page, 10).values();

        new MctopCommandDisplayTask(userStats, page, skill, sender).runTaskLater(mcMMO.p, 1);
    }
}
