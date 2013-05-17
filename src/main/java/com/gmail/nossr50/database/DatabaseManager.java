package com.gmail.nossr50.database;

import java.util.List;
import java.util.Map;

import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.database.PlayerStat;
import com.gmail.nossr50.datatypes.player.PlayerProfile;

public abstract class DatabaseManager {

    private static DatabaseManager instance;

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = Config.getInstance().getUseMySQL() ? new SQLDatabaseManager() : new FlatfileDatabaseManager();
        }
        return instance;
    }

    public abstract void purgePowerlessUsers();

    public abstract void purgeOldUsers();

    public abstract boolean removeUser(String playerName);

    public abstract void saveUser(PlayerProfile player);

    public abstract List<PlayerStat> readLeaderboard(String skillName, int pageNumber, int i);

    public abstract Map<String, Integer> readRank(String playerName);

    public abstract void newUser(String playerName);

    public abstract List<String> loadPlayerData(String playerName);

    public abstract boolean convert(String[] character) throws Exception;

    public abstract boolean checkConnected();
}
