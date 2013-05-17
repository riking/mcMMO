package com.gmail.nossr50.runnables.database;

import java.io.BufferedReader;
import java.io.FileReader;

import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.database.DatabaseManager;

public class SQLConversionTask extends BukkitRunnable {

    @Override
    public void run() {
        String location = mcMMO.getUsersFilePath();

        try {
            FileReader file = new FileReader(location);
            BufferedReader in = new BufferedReader(file);
            String line = "";
            int theCount = 0;

            while ((line = in.readLine()) != null) {

                // Find if the line contains the player we want.
                String[] character = line.split(":");
                if (DatabaseManager.getInstance().convert(character)) {
                    theCount++;
                }
            }

            mcMMO.p.getLogger().info("[mcMMO] MySQL Updated from users file, " + theCount + " items added/updated to MySQL DB");
            in.close();
        }
        catch (Exception e) {
            mcMMO.p.getLogger().severe("Exception while reading " + location + " (Are you sure you formatted it correctly?)" + e.toString());
        }
    }
}
