package com.gmail.nossr50.runnables.database;

import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.database.DatabaseManager;

public class UserPurgeTask extends BukkitRunnable {
    @Override
    public void run() {
        DatabaseManager.getInstance().purgePowerlessUsers();

        if (Config.getInstance().getOldUsersCutoff() != -1) {
            DatabaseManager.getInstance().purgeOldUsers();
        }
    }
}
