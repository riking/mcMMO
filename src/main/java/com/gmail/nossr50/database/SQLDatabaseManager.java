package com.gmail.nossr50.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.MobHealthbarType;
import com.gmail.nossr50.datatypes.database.DatabaseUpdateType;
import com.gmail.nossr50.datatypes.database.PlayerStat;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.AbilityType;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.datatypes.spout.huds.HudType;
import com.gmail.nossr50.runnables.database.SQLReconnectTask;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.StringUtils;

public final class SQLDatabaseManager extends DatabaseManager {
    private String connectionString;

    private String tablePrefix = Config.getInstance().getMySQLTablePrefix();
    private Connection connection = null;

    // Scale waiting time by this much per failed attempt
    private final double SCALING_FACTOR = 40;

    // Minimum wait in nanoseconds (default 500ms)
    private final long MIN_WAIT = 500L * 1000000L;

    // Maximum time to wait between reconnects (default 5 minutes)
    private final long MAX_WAIT = 5L * 60L * 1000L * 1000000L;

    // How long to wait when checking if connection is valid (default 3 seconds)
    private final int VALID_TIMEOUT = 3;

    // When next to try connecting to Database in nanoseconds
    private long nextReconnectTimestamp = 0L;

    // How many connection attempts have failed
    private int reconnectAttempt = 0;

    private final long ONE_MONTH = 2630000000L;

    protected SQLDatabaseManager() {
        checkConnected();
        createStructure();
    }

    public void purgePowerlessUsers() {
        mcMMO.p.getLogger().info("Purging powerless users...");
        try {
            PreparedStatement statement = SQLStatements.getInstance().getStatement("powerlessPurge");
            // This does the deleting AND returns the usernames of who we deleted for further processing
            // All in one neat package
            statement.execute();
            ResultSet result = statement.getResultSet();
            int purgeCount = 0;
            while (result.next()) {
                Misc.profileCleanup(result.getString(0));
                purgeCount++;
            }
            mcMMO.p.getLogger().info("Purged " + purgeCount + " 0-leveled users from the database.");
        } catch (SQLException e) {
            printErrors(e);
        }
    }

    public void purgeOldUsers() {
        mcMMO.p.getLogger().info("Purging old users...");
        long currentTime = System.currentTimeMillis();
        long purgeTime = ONE_MONTH * Config.getInstance().getOldUsersCutoff();

        try {
            PreparedStatement statement = SQLStatements.getInstance().getStatement("purgeOld");
            ResultSet result;
            statement.setInt(0, (int) currentTime); // TODO: Convert column to either DATETIME or BIGINT
            statement.setInt(1, (int) purgeTime);
            statement.execute();
            result = statement.getResultSet();
            int purgeCount = 0;
            while (result.next()) {
                Misc.profileCleanup(result.getString(0));
                purgeCount++;
            }
            mcMMO.p.getLogger().info("Purged " + purgeCount + " old users from the database. (Old)");
        } catch (SQLException e) {
            printErrors(e);
        }
    }

    public boolean removeUser(String playerName) {
        boolean success = update("DELETE FROM u, e, h, s, c " +
                "USING " + tablePrefix + "users u " +
                "JOIN " + tablePrefix + "experience e ON (u.id = e.user_id) " +
                "JOIN " + tablePrefix + "huds h ON (u.id = h.user_id) " +
                "JOIN " + tablePrefix + "skills s ON (u.id = s.user_id) " +
                "JOIN " + tablePrefix + "cooldowns c ON (u.id = c.user_id) " +
                "WHERE u.user = '" + playerName + "'") != 0;

        Misc.profileCleanup(playerName);

        return success;
    }

    public void saveUser(PlayerProfile player) {
        int userId = readId(player.getPlayerName());
        saveLogin(userId, ((int) (System.currentTimeMillis() / Misc.TIME_CONVERSION_FACTOR)));
        MobHealthbarType mobHealthbarType = player.getMobHealthbarType();
        HudType hudType = player.getHudType();
        saveHuds(userId, (hudType == null ? "STANDARD" : hudType.toString()), (mobHealthbarType == null ? Config.getInstance().getMobHealthbarDefault().toString() : mobHealthbarType.toString()));
        saveIntegers(SQLStatements.getInstance().getStatement("saveCooldowns"),
                (int) player.getSkillDATS(AbilityType.SUPER_BREAKER),
                (int) player.getSkillDATS(AbilityType.TREE_FELLER),
                (int) player.getSkillDATS(AbilityType.BERSERK),
                (int) player.getSkillDATS(AbilityType.GREEN_TERRA),
                (int) player.getSkillDATS(AbilityType.GIGA_DRILL_BREAKER),
                (int) player.getSkillDATS(AbilityType.SERRATED_STRIKES),
                (int) player.getSkillDATS(AbilityType.SKULL_SPLITTER),
                (int) player.getSkillDATS(AbilityType.BLAST_MINING),
                userId);
        saveIntegers(SQLStatements.getInstance().getStatement("saveSkills"),
                player.getSkillLevel(SkillType.TAMING),
                player.getSkillLevel(SkillType.MINING),
                player.getSkillLevel(SkillType.REPAIR),
                player.getSkillLevel(SkillType.WOODCUTTING),
                player.getSkillLevel(SkillType.UNARMED),
                player.getSkillLevel(SkillType.HERBALISM),
                player.getSkillLevel(SkillType.EXCAVATION),
                player.getSkillLevel(SkillType.ARCHERY),
                player.getSkillLevel(SkillType.SWORDS),
                player.getSkillLevel(SkillType.AXES),
                player.getSkillLevel(SkillType.ACROBATICS),
                player.getSkillLevel(SkillType.FISHING),
                userId);
        saveIntegers(SQLStatements.getInstance().getStatement("saveExperience"),
                player.getSkillXpLevel(SkillType.TAMING),
                player.getSkillXpLevel(SkillType.MINING),
                player.getSkillXpLevel(SkillType.REPAIR),
                player.getSkillXpLevel(SkillType.WOODCUTTING),
                player.getSkillXpLevel(SkillType.UNARMED),
                player.getSkillXpLevel(SkillType.HERBALISM),
                player.getSkillXpLevel(SkillType.EXCAVATION),
                player.getSkillXpLevel(SkillType.ARCHERY),
                player.getSkillXpLevel(SkillType.SWORDS),
                player.getSkillXpLevel(SkillType.AXES),
                player.getSkillXpLevel(SkillType.ACROBATICS),
                player.getSkillXpLevel(SkillType.FISHING),
                userId);
    }

    public List<PlayerStat> readLeaderboard(String skill, int page, int entriesPer) {
        ResultSet resultSet = null;
        List<PlayerStat> stats = new ArrayList<PlayerStat>();

        if (checkConnected()) {
            try {
                PreparedStatement statement = SQLStatements.getInstance().getStatement("mctop_" + skill.toUpperCase());
                statement.setInt(1, (page * entriesPer) - entriesPer);
                statement.setInt(2, entriesPer);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    ArrayList<String> column = new ArrayList<String>();

                    for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                        column.add(resultSet.getString(i));
                    }

                    stats.add(new PlayerStat(column.get(1), Integer.valueOf(column.get(0))));
                }
            }
            catch (SQLException ex) {
                printErrors(ex);
            }
            finally {
                if (resultSet != null) {
                    try {
                        resultSet.close();
                    }
                    catch (SQLException e) {
                        printErrors(e);
                    }
                }
            }
        }

        return stats;
    }

    public Map<String, Integer> readRank(String playerName) {
        ResultSet resultSet;
        Map<String, Integer> skills = new HashMap<String, Integer>();

        if (checkConnected()) {
            try {
                for (SkillType skillType : SkillType.nonChildSkills()) {
                    PreparedStatement statement = SQLStatements.getInstance().getStatement("mcrank_" + skillType.name() + "_A");
                    statement.setString(1, playerName);
                    resultSet = statement.executeQuery();

                    resultSet.next();

                    int rank = resultSet.getInt("rank");

                    statement = SQLStatements.getInstance().getStatement("mcrank_" + skillType.name() + "_B");

                    resultSet = statement.executeQuery();

                    while (resultSet.next()) {
                        if (resultSet.getString("user").equalsIgnoreCase(playerName)) {
                            skills.put(skillType.name(), rank + resultSet.getRow());
                            break;
                        }
                    }

                    resultSet.close();
                }

                PreparedStatement statement = SQLStatements.getInstance().getStatement("mcrank_ALL_A");
                statement.setString(1, playerName);
                resultSet = statement.executeQuery();

                resultSet.next();

                int rank = resultSet.getInt("rank");

                SQLStatements.getInstance().getStatement("mcrank_ALL_B");

                statement.setString(1, playerName);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    if (resultSet.getString("user").equalsIgnoreCase(playerName)) {
                        skills.put("ALL", rank + resultSet.getRow());
                        break;
                    }
                }

                resultSet.close();
            }
            catch (SQLException ex) {
                printErrors(ex);
            }
        }

        return skills;
    }

    public void newUser(String playerName) {
        try {
            PreparedStatement statement = SQLStatements.getInstance().getStatement("newUser");
            statement.setString(1, playerName);
            statement.setLong(2, System.currentTimeMillis() / Misc.TIME_CONVERSION_FACTOR);
            statement.execute();

            writeMissingRows(readId(playerName));
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
    }

    public List<String> loadPlayerData(String playerName) {
        List<String> playerData = null;
        try {
            PreparedStatement statement = SQLStatements.getInstance().getStatement("loadUser");
            statement.setString(1, playerName);

            playerData = readRow(statement);

            if (playerData == null || playerData.size() == 0) {
                int userId = readId(playerName);

                // Check if user doesn't exist
                if (userId == 0) {
                    return playerData;
                }

                // Write missing table rows
                writeMissingRows(userId);

                // Re-read data
                playerData = loadPlayerData(playerName);
            }
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        return playerData;
    }

    public boolean convert(String[] character) throws Exception {
        String playerName = null;
        String mining = null;
        String woodcutting = null;
        String repair = null;
        String unarmed = null;
        String herbalism = null;
        String excavation = null;
        String archery = null;
        String swords = null;
        String axes = null;
        String acrobatics = null;
        String taming = null;
        String fishing = null;
        String miningXP = null;
        String woodCuttingXP = null;
        String repairXP = null;
        String unarmedXP = null;
        String herbalismXP = null;
        String excavationXP = null;
        String archeryXP = null;
        String swordsXP = null;
        String axesXP = null;
        String acrobaticsXP = null;
        String tamingXP = null;
        String fishingXP = null;
        int id = 0;
        playerName = character[0];

        // Check for things we don't want put in the DB
        if (playerName == null || playerName.equalsIgnoreCase("null") || playerName.equalsIgnoreCase("#Storage place for user information")) {
            return false;
        }

        if (character.length > 1) {
            mining = character[1];
        }

        if (character.length > 4) {
            miningXP = character[4];
        }

        if (character.length > 5) {
            woodcutting = character[5];
        }

        if (character.length > 6) {
            woodCuttingXP = character[6];
        }

        if (character.length > 7) {
            repair = character[7];
        }

        if (character.length > 8) {
            unarmed = character[8];
        }

        if (character.length > 9) {
            herbalism = character[9];
        }

        if (character.length > 10) {
            excavation = character[10];
        }

        if (character.length > 11) {
            archery = character[11];
        }

        if (character.length > 12) {
            swords = character[12];
        }

        if (character.length > 13) {
            axes = character[13];
        }

        if (character.length > 14) {
            acrobatics = character[14];
        }

        if (character.length > 15) {
            repairXP = character[15];
        }

        if (character.length > 16) {
            unarmedXP = character[16];
        }

        if (character.length > 17) {
            herbalismXP = character[17];
        }

        if (character.length > 18) {
            excavationXP = character[18];
        }

        if (character.length > 19) {
            archeryXP = character[19];
        }

        if (character.length > 20) {
            swordsXP = character[20];
        }

        if (character.length > 21) {
            axesXP = character[21];
        }

        if (character.length > 22) {
            acrobaticsXP = character[22];
        }

        if (character.length > 24) {
            taming = character[24];
        }

        if (character.length > 25) {
            tamingXP = character[25];
        }

        if (character.length > 34) {
            fishing = character[34];
        }

        if (character.length > 35) {
            fishingXP = character[35];
        }

        // Check to see if the user is in the DB
        id = readId(playerName);

        if (id > 0) {
            // Update the skill values
            saveLogin(id, 0L);

            saveIntegers(SQLStatements.getInstance().getStatement("saveSkills"),
                    StringUtils.getInt(taming), StringUtils.getInt(mining),
                    StringUtils.getInt(repair), StringUtils.getInt(woodcutting),
                    StringUtils.getInt(unarmed), StringUtils.getInt(herbalism),
                    StringUtils.getInt(excavation), StringUtils.getInt(archery),
                    StringUtils.getInt(swords), StringUtils.getInt(axes),
                    StringUtils.getInt(acrobatics), StringUtils.getInt(fishing),
                    id);
            saveIntegers(SQLStatements.getInstance().getStatement("saveExperience"),
                    StringUtils.getInt(tamingXP), StringUtils.getInt(miningXP),
                    StringUtils.getInt(repairXP), StringUtils.getInt(woodCuttingXP),
                    StringUtils.getInt(unarmedXP), StringUtils.getInt(herbalismXP),
                    StringUtils.getInt(excavationXP), StringUtils.getInt(archeryXP),
                    StringUtils.getInt(swordsXP), StringUtils.getInt(axesXP),
                    StringUtils.getInt(acrobaticsXP), StringUtils.getInt(fishingXP),
                    id);
        }
        else {
            // Create the user in the DB
            newUser(playerName);

            id = readId(playerName);

            // Update the skill values
            saveLogin(id, 0L);

            saveIntegers(SQLStatements.getInstance().getStatement("saveSkills"),
                    StringUtils.getInt(taming), StringUtils.getInt(mining),
                    StringUtils.getInt(repair), StringUtils.getInt(woodcutting),
                    StringUtils.getInt(unarmed), StringUtils.getInt(herbalism),
                    StringUtils.getInt(excavation), StringUtils.getInt(archery),
                    StringUtils.getInt(swords), StringUtils.getInt(axes),
                    StringUtils.getInt(acrobatics), StringUtils.getInt(fishing),
                    id);
            saveIntegers(SQLStatements.getInstance().getStatement("saveExperience"),
                    StringUtils.getInt(tamingXP), StringUtils.getInt(miningXP),
                    StringUtils.getInt(repairXP), StringUtils.getInt(woodCuttingXP),
                    StringUtils.getInt(unarmedXP), StringUtils.getInt(herbalismXP),
                    StringUtils.getInt(excavationXP), StringUtils.getInt(archeryXP),
                    StringUtils.getInt(swordsXP), StringUtils.getInt(axesXP),
                    StringUtils.getInt(acrobaticsXP), StringUtils.getInt(fishingXP),
                    id);
        }
        return true;
    }

    /**
     * Check connection status and re-establish if dead or stale.
     *
     * If the very first immediate attempt fails, further attempts
     * will be made in progressively larger intervals up to MAX_WAIT
     * intervals.
     *
     * This allows for MySQL to time out idle connections as needed by
     * server operator, without affecting McMMO, while still providing
     * protection against a database outage taking down Bukkit's tick
     * processing loop due to attemping a database connection each
     * time McMMO needs the database.
     *
     * @return the boolean value for whether or not we are connected
     */
    public boolean checkConnected() {
        boolean isClosed = true;
        boolean isValid = false;
        boolean exists = (connection != null);

        // If we're waiting for server to recover then leave early
        if (nextReconnectTimestamp > 0 && nextReconnectTimestamp > System.nanoTime()) {
            return false;
        }

        if (exists) {
            try {
                isClosed = connection.isClosed();
            }
            catch (SQLException e) {
                isClosed = true;
                e.printStackTrace();
                printErrors(e);
            }

            if (!isClosed) {
                try {
                    isValid = connection.isValid(VALID_TIMEOUT);
                }
                catch (SQLException e) {
                    // Don't print stack trace because it's valid to lose idle connections to the server and have to restart them.
                    isValid = false;
                }
            }
        }

        // Leave if all ok
        if (exists && !isClosed && isValid) {
            // Housekeeping
            nextReconnectTimestamp = 0;
            reconnectAttempt = 0;
            return true;
        }

        // Cleanup after ourselves for GC and MySQL's sake
        if (exists && !isClosed) {
            try {
                connection.close();
            }
            catch (SQLException ex) {
                // This is a housekeeping exercise, ignore errors
            }
        }

        // Try to connect again
        connect();

        // Leave if connection is good
        try {
            if (connection != null && !connection.isClosed()) {
                // Schedule a database save if we really had an outage
                if (reconnectAttempt > 1) {
                    new SQLReconnectTask().runTaskLater(mcMMO.p, 5);
                }
                nextReconnectTimestamp = 0;
                reconnectAttempt = 0;
                return true;
            }
        }
        catch (SQLException e) {
            // Failed to check isClosed, so presume connection is bad and attempt later
            e.printStackTrace();
            printErrors(e);
        }

        reconnectAttempt++;
        nextReconnectTimestamp = (long) (System.nanoTime() + Math.min(MAX_WAIT, (reconnectAttempt * SCALING_FACTOR * MIN_WAIT)));
        return false;
    }

    /**
     * Attempt to connect to the mySQL database.
     */
    private void connect() {
        Config configInstance = Config.getInstance();
        connectionString = "jdbc:mysql://" + configInstance.getMySQLServerName() + ":" + configInstance.getMySQLServerPort() + "/" + configInstance.getMySQLDatabaseName();

        try {
            mcMMO.p.getLogger().info("Attempting connection to MySQL...");

            // Force driver to load if not yet loaded
            Class.forName("com.mysql.jdbc.Driver");
            Properties connectionProperties = new Properties();
            connectionProperties.put("user", configInstance.getMySQLUserName());
            connectionProperties.put("password", configInstance.getMySQLUserPassword());
            connectionProperties.put("autoReconnect", "false");
            connectionProperties.put("maxReconnects", "0");
            connection = DriverManager.getConnection(connectionString, connectionProperties);

            mcMMO.p.getLogger().info("Connection to MySQL was a success!");
        }
        catch (SQLException ex) {
            connection = null;

            if (reconnectAttempt == 0 || reconnectAttempt >= 11) {
                mcMMO.p.getLogger().info("Connection to MySQL failed!");
            }
        }
        catch (ClassNotFoundException ex) {
            connection = null;

            if (reconnectAttempt == 0 || reconnectAttempt >= 11) {
                mcMMO.p.getLogger().info("MySQL database driver not found!");
            }
        }
    }

    /**
     * Attempt to create the database structure.
     */
    private void createStructure() {
        write("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "users` ("
                + "`id` int(10) unsigned NOT NULL AUTO_INCREMENT,"
                + "`user` varchar(40) NOT NULL,"
                + "`lastlogin` int(32) unsigned NOT NULL,"
                + "PRIMARY KEY (`id`),"
                + "UNIQUE KEY `user` (`user`)) DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;");
        write("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "huds` ("
                + "`user_id` int(10) unsigned NOT NULL,"
                + "`hudtype` varchar(50) NOT NULL DEFAULT 'STANDARD',"
                + "`mobhealthbar` varchar(50) NOT NULL DEFAULT '" + Config.getInstance().getMobHealthbarDefault() + "',"
                + "PRIMARY KEY (`user_id`)) "
                + "DEFAULT CHARSET=latin1;");
        write("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "cooldowns` ("
                + "`user_id` int(10) unsigned NOT NULL,"
                + "`taming` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`mining` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`woodcutting` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`repair` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`unarmed` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`herbalism` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`excavation` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`archery` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`swords` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`axes` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`acrobatics` int(32) unsigned NOT NULL DEFAULT '0',"
                + "`blast_mining` int(32) unsigned NOT NULL DEFAULT '0',"
                + "PRIMARY KEY (`user_id`)) "
                + "DEFAULT CHARSET=latin1;");
        write("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "skills` ("
                + "`user_id` int(10) unsigned NOT NULL,"
                + "`taming` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`mining` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`woodcutting` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`repair` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`unarmed` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`herbalism` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`excavation` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`archery` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`swords` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`axes` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`acrobatics` int(10) unsigned NOT NULL DEFAULT '0',"
                + "PRIMARY KEY (`user_id`)) "
                + "DEFAULT CHARSET=latin1;");
        write("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "experience` ("
                + "`user_id` int(10) unsigned NOT NULL,"
                + "`taming` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`mining` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`woodcutting` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`repair` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`unarmed` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`herbalism` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`excavation` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`archery` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`swords` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`axes` int(10) unsigned NOT NULL DEFAULT '0',"
                + "`acrobatics` int(10) unsigned NOT NULL DEFAULT '0',"
                + "PRIMARY KEY (`user_id`)) "
                + "DEFAULT CHARSET=latin1;");

        checkDatabaseStructure(DatabaseUpdateType.FISHING);
        checkDatabaseStructure(DatabaseUpdateType.BLAST_MINING);
        checkDatabaseStructure(DatabaseUpdateType.INDEX);
        checkDatabaseStructure(DatabaseUpdateType.MOB_HEALTHBARS);
        checkDatabaseStructure(DatabaseUpdateType.PARTY_NAMES);
        checkDatabaseStructure(DatabaseUpdateType.KILL_ORPHANS);

        try {
            new SQLStatements(connection, Config.getInstance().getMySQLTablePrefix());
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
    }

    /**
     * Check database structure for missing values.
     *
     * @param update Type of data to check updates for
     */
    private void checkDatabaseStructure(DatabaseUpdateType update) {
        String sql = null;
        ResultSet resultSet = null;
        HashMap<Integer, ArrayList<String>> rows = new HashMap<Integer, ArrayList<String>>();

        switch (update) {
            case BLAST_MINING:
                sql = "SELECT * FROM  `" + tablePrefix + "cooldowns` ORDER BY  `" + tablePrefix + "cooldowns`.`blast_mining` ASC LIMIT 0 , 30";
                break;

            case FISHING:
                sql = "SELECT * FROM  `" + tablePrefix + "experience` ORDER BY  `" + tablePrefix + "experience`.`fishing` ASC LIMIT 0 , 30";
                break;

            case INDEX:
                if (read("SHOW INDEX FROM " + tablePrefix + "skills").size() != 13 && checkConnected()) {
                    mcMMO.p.getLogger().info("Indexing tables, this may take a while on larger databases");
                    write("ALTER TABLE `" + tablePrefix + "skills` ADD INDEX `idx_taming` (`taming`) USING BTREE, "
                            + "ADD INDEX `idx_mining` (`mining`) USING BTREE, "
                            + "ADD INDEX `idx_woodcutting` (`woodcutting`) USING BTREE, "
                            + "ADD INDEX `idx_repair` (`repair`) USING BTREE, "
                            + "ADD INDEX `idx_unarmed` (`unarmed`) USING BTREE, "
                            + "ADD INDEX `idx_herbalism` (`herbalism`) USING BTREE, "
                            + "ADD INDEX `idx_excavation` (`excavation`) USING BTREE, "
                            + "ADD INDEX `idx_archery` (`archery`) USING BTREE, "
                            + "ADD INDEX `idx_swords` (`swords`) USING BTREE, "
                            + "ADD INDEX `idx_axes` (`axes`) USING BTREE, "
                            + "ADD INDEX `idx_acrobatics` (`acrobatics`) USING BTREE, "
                            + "ADD INDEX `idx_fishing` (`fishing`) USING BTREE;");
                }
                return;

            case MOB_HEALTHBARS:
                sql = "SELECT * FROM  `" + tablePrefix + "huds` ORDER BY  `" + tablePrefix + "huds`.`mobhealthbar` ASC LIMIT 0 , 30";
                break;

            case PARTY_NAMES:
                write("ALTER TABLE `" + tablePrefix + "users` DROP COLUMN `party` ;");
                return;

            case KILL_ORPHANS:
                mcMMO.p.getLogger().info("Killing orphans");
                write(
                        "DELETE FROM " + tablePrefix + "experience " +
                         "WHERE NOT EXISTS (SELECT * FROM " +
                         tablePrefix + "users u WHERE " +
                         tablePrefix + "experience.user_id = u.id);"
                         );
                write(
                        "DELETE FROM " + tablePrefix + "huds " +
                         "WHERE NOT EXISTS (SELECT * FROM " +
                         tablePrefix + "users u WHERE " +
                         tablePrefix + "huds.user_id = u.id);"
                         );
                write(
                        "DELETE FROM " + tablePrefix + "cooldowns " +
                         "WHERE NOT EXISTS (SELECT * FROM " +
                         tablePrefix + "users u WHERE " +
                         tablePrefix + "cooldowns.user_id = u.id);"
                         );
                write(
                        "DELETE FROM " + tablePrefix + "skills " +
                         "WHERE NOT EXISTS (SELECT * FROM " +
                         tablePrefix + "users u WHERE " +
                         tablePrefix + "skills.user_id = u.id);"
                         );
                return;

            default:
                break;
        }

        PreparedStatement statement = null;
        try {
            if (!checkConnected()) {
                return;
            }

            statement = connection.prepareStatement(sql);
            resultSet = statement.executeQuery();

            while (resultSet.next()) {
                ArrayList<String> column = new ArrayList<String>();

                for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                    column.add(resultSet.getString(i));
                }

                rows.put(resultSet.getRow(), column);
            }
        }
        catch (SQLException ex) {
            switch (update) {
                case BLAST_MINING:
                    mcMMO.p.getLogger().info("Updating mcMMO MySQL tables for Blast Mining...");
                    write("ALTER TABLE `"+tablePrefix + "cooldowns` ADD `blast_mining` int(32) NOT NULL DEFAULT '0' ;");
                    break;

                case FISHING:
                    mcMMO.p.getLogger().info("Updating mcMMO MySQL tables for Fishing...");
                    write("ALTER TABLE `"+tablePrefix + "skills` ADD `fishing` int(10) NOT NULL DEFAULT '0' ;");
                    write("ALTER TABLE `"+tablePrefix + "experience` ADD `fishing` int(10) NOT NULL DEFAULT '0' ;");
                    break;

                case MOB_HEALTHBARS:
                    mcMMO.p.getLogger().info("Updating mcMMO MySQL tables for mob healthbars...");
                    write("ALTER TABLE `" + tablePrefix + "huds` ADD `mobhealthbar` varchar(50) NOT NULL DEFAULT '" + Config.getInstance().getMobHealthbarDefault() + "' ;");
                    break;

                default:
                    break;
            }
        }
        finally {
            if (statement != null) {
                try {
                    statement.close();
                }
                catch (SQLException e) {
                    // Ignore the error, we're leaving
                }
            }
        }
    }

    /**
     * Attempt to write the SQL query.
     *
     * @param sql Query to write.
     * @return true if the query was successfully written, false otherwise.
     */
    private boolean write(String sql) {
        if (!checkConnected()) {
            return false;
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(sql);
            statement.executeUpdate();
            return true;
        }
        catch (SQLException ex) {
            if (!sql.equalsIgnoreCase("ALTER TABLE `" + tablePrefix + "users` DROP COLUMN `party` ;")) {
                printErrors(ex);
            }
            return false;
        }
        finally {
            if (statement != null) {
                try {
                    statement.close();
                }
                catch (SQLException e) {
                    printErrors(e);
                }
            }
        }
    }

    /**
     * Returns the number of rows affected by either a DELETE or UPDATE query
     *
     * @param sql SQL query to execute
     * @return the number of rows affected
     */
    private int update(String sql) {
        if (!checkConnected()) {
            return 0;
        }

        int rows = 0;

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(sql);
            rows = statement.executeUpdate();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            if (statement != null) {
                try {
                    statement.close();
                }
                catch (SQLException e) {
                    printErrors(e);
                }
            }
        }

        return rows;
    }

    /**
     * Read SQL query.
     *
     * @param sql SQL query to read
     * @return the rows in this SQL query
     */
    private HashMap<Integer, ArrayList<String>> read(String sql) {
        ResultSet resultSet;
        HashMap<Integer, ArrayList<String>> rows = new HashMap<Integer, ArrayList<String>>();

        if (checkConnected()) {
            PreparedStatement statement = null;

            try {
                statement = connection.prepareStatement(sql);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    ArrayList<String> column = new ArrayList<String>();

                    for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                        column.add(resultSet.getString(i));
                    }

                    rows.put(resultSet.getRow(), column);
                }
            }
            catch (SQLException ex) {
                printErrors(ex);
            }
            finally {
                if (statement != null) {
                    try {
                        statement.close();
                    }
                    catch (SQLException e) {
                        printErrors(e);
                    }
                }
            }
        }

        return rows;
    }

    private ArrayList<String> readRow(PreparedStatement statement) {
        ResultSet resultSet = null;
        ArrayList<String> playerData = new ArrayList<String>();

        if (checkConnected()) {
            try {
                resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                        playerData.add(resultSet.getString(i));
                    }
                }
            }
            catch (SQLException ex) {
                printErrors(ex);
            }
            finally {
                try {
                    resultSet.close();
                }
                catch (Exception e) {}
            }
        }

        return playerData;
    }

    /**
     * Get the Integer. Only return first row / first field.
     *
     * @param sql SQL query to execute
     * @return the value in the first row / first field
     */
    private int readInt(PreparedStatement statement) {
        if (!checkConnected()) {
            return 0;
        }

        int result = 0;

        ResultSet resultSet = null;

        try {
            resultSet = statement.executeQuery();

            if (resultSet.next()) {
                result = resultSet.getInt(1);
            }
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                }
                catch (Exception e) {
                    // Ignore
                }
            }
        }

        return result;
    }

    public void writeMissingRows(int id) {
        try {
            PreparedStatement statement = SQLStatements.getInstance().getStatement("missingExperience");
            statement.setInt(1, id);
            statement.execute();

            statement = SQLStatements.getInstance().getStatement("missingSkills");
            statement.setInt(1, id);
            statement.execute();

            statement = SQLStatements.getInstance().getStatement("missingCooldowns");
            statement.setInt(1, id);
            statement.execute();

            statement = SQLStatements.getInstance().getStatement("missingHuds");
            statement.setInt(1, id);
            statement.execute();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
    }

    private int processPurge(ArrayList<String> usernames) {
        int purgedUsers = 0;

        for (String user : usernames) {
            Misc.profileCleanup(user);
            purgedUsers++;
        }

        return purgedUsers;
    }

    private void saveIntegers(PreparedStatement statement, int... args) {
        try {
            int i = 1;

            for (int arg : args) {
                statement.setInt(i++, arg);
            }

            statement.execute();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
    }

    private int readId(String playerName) {
        try {
            PreparedStatement statement = SQLStatements.getInstance().getStatement("getId");
            statement.setString(1, playerName);
            return readInt(statement);
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        return 0;
    }

    private void saveLogin(int id, long login) {
        try {
            PreparedStatement statement = SQLStatements.getInstance().getStatement("saveLogin");
            statement.setLong(1, login);
            statement.setInt(2, id);
            statement.execute();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
    }

    private void saveHuds(int userId, String hudType, String mobHealthBar) {
        try {
            PreparedStatement statement = SQLStatements.getInstance().getStatement("saveHuds");
            statement.setString(1, hudType);
            statement.setString(2, mobHealthBar);
            statement.setInt(3, userId);
            statement.execute();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
    }

    private void printErrors(SQLException ex) {
        mcMMO.p.getLogger().severe("SQLException: " + ex.getMessage());
        mcMMO.p.getLogger().severe("SQLState: " + ex.getSQLState());
        mcMMO.p.getLogger().severe("VendorError: " + ex.getErrorCode());
    }
}
