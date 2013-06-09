package com.gmail.nossr50.runnables.database;

import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.database.DatabaseManager;
import com.gmail.nossr50.database.DatabaseManagerFactory;
import com.gmail.nossr50.database.SQLDatabaseManager;

public class FromSQLConversionTask extends BukkitRunnable {

    @Override
    public void run() {
        try {
            SQLDatabaseManager from = DatabaseManagerFactory.createSQLDatabaseManager();
            DatabaseManager to = mcMMO.getDatabaseManager();

            int converted = 0;
            for (String user : from.getStoredUsers()) {
                to.saveUser(from.loadPlayerData(user, false));
                converted++;
            }

            mcMMO.p.getLogger().info("Database updated from users file, " + converted + " items added/updated to DB");
        } catch (Exception e) {
            mcMMO.p.getLogger().severe("Exception while reading SQL Database (Did you set it up right?)" + e.toString());
        }
    }
}
