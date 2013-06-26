package com.gmail.nossr50.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.MobHealthbarType;
import com.gmail.nossr50.datatypes.database.PlayerStat;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.AbilityType;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.datatypes.spout.huds.HudType;
import com.gmail.nossr50.runnables.database.SQLReconnectTask;
import com.gmail.nossr50.util.Misc;

public final class SQLDatabaseManager implements DatabaseManager {
    private String connectionString;
    protected final String tablePrefix = Config.getInstance().getMySQLTablePrefix();
    protected Connection connection = null;
    private SqlStatements statements;

    // Scale waiting time by this much per failed attempt
    private final double SCALING_FACTOR = 40.0;

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

    protected SQLDatabaseManager() {
        checkConnected();
        createAndFixStructure();
        statements = new SqlStatements(this);
        statements.init();
    }

    public void purgePowerlessUsers() {
        checkConnected();
        mcMMO.p.getLogger().info("Purging powerless users...");

        PreparedStatement readUsers = statements.purgePowerlessGet;
        PreparedStatement doPurge = statements.purgePowerlessRun;
        ResultSet result = null;

        try {
            ArrayList<String> usernames = new ArrayList<String>();
            result = readUsers.executeQuery();
            while (result.next()) {
                usernames.add(result.getString(1));
            }
            result.close();

            int count = doPurge.executeUpdate();

            for (String username : usernames) {
                Misc.profileCleanup(username);
            }

            if (count != usernames.size()) {
                mcMMO.p.getLogger().info("Purged either " + count + " or " + usernames.size() + " users from the database... It's probably the first one, though.");
            }
            else {
                mcMMO.p.getLogger().info("Purged " + count + " users from the database.");
            }
        }
        catch (SQLException e) {
            mcMMO.p.getLogger().log(Level.SEVERE, "Failed to purge powerless users.", e);
        }
        finally {
            tryClose(result);
        }
    }

    public void purgeOldUsers() {
        checkConnected();
        long currentTime = System.currentTimeMillis();

        mcMMO.p.getLogger().info("Purging old users...");

        Collection<ArrayList<String>> usernames = read("SELECT user FROM " + tablePrefix + "users WHERE ((" + currentTime + " - lastlogin * " + Misc.TIME_CONVERSION_FACTOR + ") > " + PURGE_TIME + ")").values();

        write("DELETE FROM u, e, h, s, c USING " + tablePrefix + "users u " +
                "JOIN " + tablePrefix + "experience e ON (u.id = e.user_id) " +
                "JOIN " + tablePrefix + "huds h ON (u.id = h.user_id) " +
                "JOIN " + tablePrefix + "skills s ON (u.id = s.user_id) " +
                "JOIN " + tablePrefix + "cooldowns c ON (u.id = c.user_id) " +
                "WHERE ((" + currentTime + " - lastlogin * " + Misc.TIME_CONVERSION_FACTOR + ") > " + PURGE_TIME + ")", false);

        processPurge(usernames);
        mcMMO.p.getLogger().info("Purged " + usernames.size() + " users from the database.");;
    }

    public boolean removeUser(String playerName) {
        checkConnected();
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

    public void saveUser(PlayerProfile profile) {
        checkConnected();
        int userId = readId(profile.getPlayerName());
        MobHealthbarType mobHealthbarType = profile.getMobHealthbarType();
        HudType hudType = profile.getHudType();

        saveLogin(userId, ((int) (System.currentTimeMillis() / Misc.TIME_CONVERSION_FACTOR)));
        saveHuds(userId, (hudType == null ? "STANDARD" : hudType.toString()), (mobHealthbarType == null ? Config.getInstance().getMobHealthbarDefault().toString() : mobHealthbarType.toString()));
        saveLongs(
                "UPDATE " + tablePrefix + "cooldowns SET "
                    + "  mining = ?, woodcutting = ?, unarmed = ?"
                    + ", herbalism = ?, excavation = ?, swords = ?"
                    + ", axes = ?, blast_mining = ? WHERE user_id = ?",
                userId,
                profile.getSkillDATS(AbilityType.SUPER_BREAKER),
                profile.getSkillDATS(AbilityType.TREE_FELLER),
                profile.getSkillDATS(AbilityType.BERSERK),
                profile.getSkillDATS(AbilityType.GREEN_TERRA),
                profile.getSkillDATS(AbilityType.GIGA_DRILL_BREAKER),
                profile.getSkillDATS(AbilityType.SERRATED_STRIKES),
                profile.getSkillDATS(AbilityType.SKULL_SPLITTER),
                profile.getSkillDATS(AbilityType.BLAST_MINING));
        saveIntegers(
                "UPDATE " + tablePrefix + "skills SET "
                    + " taming = ?, mining = ?, repair = ?, woodcutting = ?"
                    + ", unarmed = ?, herbalism = ?, excavation = ?"
                    + ", archery = ?, swords = ?, axes = ?, acrobatics = ?"
                    + ", fishing = ? WHERE user_id = ?",
                profile.getSkillLevel(SkillType.TAMING),
                profile.getSkillLevel(SkillType.MINING),
                profile.getSkillLevel(SkillType.REPAIR),
                profile.getSkillLevel(SkillType.WOODCUTTING),
                profile.getSkillLevel(SkillType.UNARMED),
                profile.getSkillLevel(SkillType.HERBALISM),
                profile.getSkillLevel(SkillType.EXCAVATION),
                profile.getSkillLevel(SkillType.ARCHERY),
                profile.getSkillLevel(SkillType.SWORDS),
                profile.getSkillLevel(SkillType.AXES),
                profile.getSkillLevel(SkillType.ACROBATICS),
                profile.getSkillLevel(SkillType.FISHING),
                userId);
        saveIntegers(
                "UPDATE " + tablePrefix + "experience SET "
                    + " taming = ?, mining = ?, repair = ?, woodcutting = ?"
                    + ", unarmed = ?, herbalism = ?, excavation = ?"
                    + ", archery = ?, swords = ?, axes = ?, acrobatics = ?"
                    + ", fishing = ? WHERE user_id = ?",
                profile.getSkillXpLevel(SkillType.TAMING),
                profile.getSkillXpLevel(SkillType.MINING),
                profile.getSkillXpLevel(SkillType.REPAIR),
                profile.getSkillXpLevel(SkillType.WOODCUTTING),
                profile.getSkillXpLevel(SkillType.UNARMED),
                profile.getSkillXpLevel(SkillType.HERBALISM),
                profile.getSkillXpLevel(SkillType.EXCAVATION),
                profile.getSkillXpLevel(SkillType.ARCHERY),
                profile.getSkillXpLevel(SkillType.SWORDS),
                profile.getSkillXpLevel(SkillType.AXES),
                profile.getSkillXpLevel(SkillType.ACROBATICS),
                profile.getSkillXpLevel(SkillType.FISHING),
                userId);
    }

    public List<PlayerStat> readLeaderboard(String skillName, int pageNumber, int statsPerPage) {
        List<PlayerStat> stats = new ArrayList<PlayerStat>();

        if (checkConnected()) {
            String query = skillName.equalsIgnoreCase("ALL") ? "taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing" : skillName;
            ResultSet resultSet = null;
            PreparedStatement statement = null;

            try {
                statement = connection.prepareStatement("SELECT user, " + query + ", NOW() FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON (user_id = id) WHERE " + query + " > 0 ORDER BY " + query + " DESC, user LIMIT ?, ?");
                statement.setInt(1, (pageNumber * statsPerPage) - statsPerPage);
                statement.setInt(2, statsPerPage);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    stats.add(new PlayerStat(resultSet.getString(1), resultSet.getInt(2)));
                }
            }
            catch (SQLException ex) {
                printErrors(ex);
            }
            finally {
                tryClose(statement);
            }
        }

        return stats;
    }

    public Map<String, Integer> readRank(String playerName) {
        Map<String, Integer> skills = new HashMap<String, Integer>();

        if (checkConnected()) {
            ResultSet resultSet = null;
            PreparedStatement statement = null;
            String sql;
            try {
                for (SkillType skillType : SkillType.nonChildSkills()) {
                    String skillName = skillType.name().toLowerCase();
                    sql = "SELECT COUNT(*) AS rank FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id WHERE " + skillName + " > 0 " +
                                 "AND " + skillName + " > (SELECT " + skillName + " FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id " +
                                 "WHERE user = ?)";

                    statement = connection.prepareStatement(sql);
                    statement.setString(1, playerName);
                    resultSet = statement.executeQuery();

                    resultSet.next();

                    int rank = resultSet.getInt("rank");

                    sql = "SELECT user, " + skillName + " FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id WHERE " + skillName + " > 0 " +
                          "AND " + skillName + " = (SELECT " + skillName + " FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id " +
                          "WHERE user = '" + playerName + "') ORDER BY user";

                    statement.close();

                    statement = connection.prepareStatement(sql);
                    resultSet = statement.executeQuery();

                    while (resultSet.next()) {
                        if (resultSet.getString("user").equalsIgnoreCase(playerName)) {
                            skills.put(skillType.name(), rank + resultSet.getRow());
                            break;
                        }
                    }

                    statement.close();
                }

                sql = "SELECT COUNT(*) AS rank FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id " +
                        "WHERE taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > 0 " +
                        "AND taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > " +
                        "(SELECT taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
                        "FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id WHERE user = ?)";

                statement = connection.prepareStatement(sql);
                statement.setString(1, playerName);
                resultSet = statement.executeQuery();

                resultSet.next();

                int rank = resultSet.getInt("rank");

                statement.close();

                sql = "SELECT user, taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
                        "FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id " +
                        "WHERE taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > 0 " +
                        "AND taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing = " +
                        "(SELECT taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
                        "FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id WHERE user = ?) ORDER BY user";

                statement = connection.prepareStatement(sql);
                statement.setString(1, playerName);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    if (resultSet.getString("user").equalsIgnoreCase(playerName)) {
                        skills.put("ALL", rank + resultSet.getRow());
                        break;
                    }
                }

                statement.close();
            }
            catch (SQLException ex) {
                printErrors(ex);
            }
            finally {
                tryClose(resultSet);
                tryClose(statement);
            }
        }

        return skills;
    }

    public void newUser(String playerName) {
        checkConnected();
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement("INSERT INTO " + tablePrefix + "users (user, lastlogin) VALUES (?, ?)");
            statement.setString(1, playerName);
            statement.setLong(2, System.currentTimeMillis() / Misc.TIME_CONVERSION_FACTOR);
            statement.execute();

            writeMissingRows(readId(playerName));
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            tryClose(statement);
        }
    }

    public PlayerProfile loadPlayerProfile(String playerName, boolean create) {
        checkConnected();
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement(
                    "SELECT "
                    + "s.taming, s.mining, s.repair, s.woodcutting, s.unarmed, s.herbalism, s.excavation, s.archery, s.swords, s.axes, s.acrobatics, s.fishing, "
                    + "e.taming, e.mining, e.repair, e.woodcutting, e.unarmed, e.herbalism, e.excavation, e.archery, e.swords, e.axes, e.acrobatics, e.fishing, "
                    + "c.taming, c.mining, c.repair, c.woodcutting, c.unarmed, c.herbalism, c.excavation, c.archery, c.swords, c.axes, c.acrobatics, c.blast_mining, "
                    + "h.hudtype, h.mobhealthbar "
                    + "FROM " + tablePrefix + "users u "
                    + "JOIN " + tablePrefix + "skills s ON (u.id = s.user_id) "
                    + "JOIN " + tablePrefix + "experience e ON (u.id = e.user_id) "
                    + "JOIN " + tablePrefix + "cooldowns c ON (u.id = c.user_id) "
                    + "JOIN " + tablePrefix + "huds h ON (u.id = h.user_id) "
                    + "WHERE u.user = ?");
            statement.setString(1, playerName);

            ResultSet result = statement.executeQuery();

            if (result.next()) {
                PlayerProfile ret = loadFromResult(playerName, result);
                result.close();
                return ret;
            }
            else {
                // Problem, no rows returned
                int userId = readId(playerName);

                if (userId == 0) {
                    if (!create) {
                        // Give up
                        return new PlayerProfile(playerName, false);
                    }
                    else {
                        newUser(playerName);
                        userId = readId(playerName);
                    }
                }

                writeMissingRows(userId);

                if (!create) {
                    // Give up
                    return new PlayerProfile(playerName, false);
                }
                else {
                    // Re-read data
                    return loadPlayerProfile(playerName, false);
                }
            }
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            tryClose(statement);
        }
        return new PlayerProfile(playerName, false);
    }

    public void convertUsers(DatabaseManager destination) {
        checkConnected();
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement(
                    "SELECT "
                    + "s.taming, s.mining, s.repair, s.woodcutting, s.unarmed, s.herbalism, s.excavation, s.archery, s.swords, s.axes, s.acrobatics, s.fishing, "
                    + "e.taming, e.mining, e.repair, e.woodcutting, e.unarmed, e.herbalism, e.excavation, e.archery, e.swords, e.axes, e.acrobatics, e.fishing, "
                    + "c.taming, c.mining, c.repair, c.woodcutting, c.unarmed, c.herbalism, c.excavation, c.archery, c.swords, c.axes, c.acrobatics, c.blast_mining, "
                    + "h.hudtype, h.mobhealthbar "
                    + "FROM " + tablePrefix + "users u "
                    + "JOIN " + tablePrefix + "skills s ON (u.id = s.user_id) "
                    + "JOIN " + tablePrefix + "experience e ON (u.id = e.user_id) "
                    + "JOIN " + tablePrefix + "cooldowns c ON (u.id = c.user_id) "
                    + "JOIN " + tablePrefix + "huds h ON (u.id = h.user_id) "
                    + "WHERE u.user = ?");
            List<String> usernames = getStoredUsers();
            ResultSet result = null;
            for (String playerName : usernames) {
                statement.setString(1, playerName);
                try {
                    result = statement.executeQuery();
                    result.next();
                    destination.saveUser(loadFromResult(playerName, result));
                    result.close();
                }
                catch (SQLException e) {
                    // Ignore
                }
                finally {
                    tryClose(result);
                }
            }
        }
        catch (SQLException e) {
            printErrors(e);
        }
        finally {
            tryClose(statement);
        }

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
    * processing loop due to attempting a database connection each
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
                statements.init();
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

    public List<String> getStoredUsers() {
        checkConnected();
        ArrayList<String> users = new ArrayList<String>();
        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            ResultSet result = stmt.executeQuery("SELECT user FROM " + tablePrefix + "users");
            while (result.next()) {
                users.add(result.getString("user"));
            }
            result.close();
        }
        catch (SQLException e) {
            printErrors(e);
        }
        finally {
            tryClose(stmt);
        }
        return users;
    }

    /**
     * Attempt to connect to the mySQL database.
     */
    private void connect() {
        connectionString = "jdbc:mysql://" + Config.getInstance().getMySQLServerName() + ":" + Config.getInstance().getMySQLServerPort() + "/" + Config.getInstance().getMySQLDatabaseName();

        try {
            mcMMO.p.getLogger().info("Attempting connection to MySQL...");

            // Force driver to load if not yet loaded
            Class.forName("com.mysql.jdbc.Driver");
            Properties connectionProperties = new Properties();
            connectionProperties.put("user", Config.getInstance().getMySQLUserName());
            connectionProperties.put("password", Config.getInstance().getMySQLUserPassword());
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
    private void createAndFixStructure() {
        write("CREATE TABLE IF NOT EXISTS `%1$susers` ("
                + "`id` int(10) unsigned NOT NULL AUTO_INCREMENT,"
                + "`user` varchar(40) NOT NULL,"
                + "`lastlogin` int(32) unsigned NOT NULL,"
                + "PRIMARY KEY (`id`),"
                + "UNIQUE KEY `user` (`user`)) DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;", false);
        write("CREATE TABLE IF NOT EXISTS `%1$shuds` ("
                + "`user_id` int(10) unsigned NOT NULL,"
                + "`hudtype` varchar(50) NOT NULL DEFAULT 'STANDARD',"
                + "`mobhealthbar` varchar(50) NOT NULL DEFAULT '" + Config.getInstance().getMobHealthbarDefault() + "',"
                + "PRIMARY KEY (`user_id`)) "
                + "DEFAULT CHARSET=latin1;", false);
        write("CREATE TABLE IF NOT EXISTS `%1$scooldowns` ("
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
                + "DEFAULT CHARSET=latin1;", false);
        write("CREATE TABLE IF NOT EXISTS `%1$sskills` ("
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
                + "`fishing` int(10) unsigned NOT NULL DEFAULT '0',"
                + "PRIMARY KEY (`user_id`)) "
                + "DEFAULT CHARSET=latin1;", false);
        write("CREATE TABLE IF NOT EXISTS `%1$sexperience` ("
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
                + "`fishing` int(10) unsigned NOT NULL DEFAULT '0',"
                + "PRIMARY KEY (`user_id`)) "
                + "DEFAULT CHARSET=latin1;", false);

        // Check Blast Mining cooldown column
        if (!write("SELECT * FROM `%1$scooldowns` ORDER BY `%1$scooldowns`.`blast_mining` ASC LIMIT 0 , 30", true)) {
            mcMMO.p.getLogger().info("Updating mcMMO MySQL tables for Blast Mining...");
            if (!write("ALTER TABLE `%1$scooldowns` ADD `blast_mining` int(32) NOT NULL DEFAULT '0' ;", false)) {
                mcMMO.p.getLogger().severe("Failed to fix SQL tables for Blast Mining, unpredictable results.");
            }
        }

        // Check for Fishing in the table
        if (!write("SELECT * FROM `%1$sexperience` ORDER BY `%1$sexperience`.`fishing` ASC LIMIT 0 , 30", true)) {
            mcMMO.p.getLogger().info("Updating mcMMO MySQL tables for Fishing...");
            boolean success = true;
            success &= write("ALTER TABLE `%1$sskills` ADD `fishing` int(10) NOT NULL DEFAULT '0' ;", false);
            success &= write("ALTER TABLE `%1$sexperience` ADD `fishing` int(10) NOT NULL DEFAULT '0' ;", false);
            if (!success) {
                mcMMO.p.getLogger().severe("Failed to fix SQL tables for Fishing, unpredictable results.");
            }
        }

        // Check for mob healthbars
        if (!write("SELECT * FROM `%1$shuds` ORDER BY `%1$shuds`.`mobhealthbar` ASC LIMIT 0 , 30", true)) {
            mcMMO.p.getLogger().info("Updating mcMMO MySQL tables for Mob Healthbars...");
            String sql = String.format("ALTER TABLE `%1$shuds` ADD `mobhealthbar` varchar(50) NOT NULL DEFAULT '%2$s' ;", tablePrefix, Config.getInstance().getMobHealthbarDefault().name());
            if (!write(sql, false)) {
                mcMMO.p.getLogger().severe("Failed to fix SQL tables for Mob Healthbars, unpredictable results.");
            }
        }

        // Check for party names
        // This one is considered "failure" if the write succeeds, sorta.
        if (write("ALTER TABLE `%1$susers` DROP COLUMN `party` ;", true)) {
            mcMMO.p.getLogger().info("Updated mcMMO MySQL tables for party names.");
        }

        // Check for skill table indices
        if (read("SHOW INDEX FROM %1$sskills").size() != 13) {
            mcMMO.p.getLogger().info("Indexing mcMMO MySQL tables for the first time... (this may take a while on larger databases)");
            if (!write("ALTER TABLE `%1$sskills` " +
                    "ADD INDEX `idx_taming` (`taming`) USING BTREE, " +
                    "ADD INDEX `idx_mining` (`mining`) USING BTREE, " +
                    "ADD INDEX `idx_woodcutting` (`woodcutting`) USING BTREE, " +
                    "ADD INDEX `idx_repair` (`repair`) USING BTREE, " +
                    "ADD INDEX `idx_unarmed` (`unarmed`) USING BTREE, " +
                    "ADD INDEX `idx_herbalism` (`herbalism`) USING BTREE, " +
                    "ADD INDEX `idx_excavation` (`excavation`) USING BTREE, " +
                    "ADD INDEX `idx_archery` (`archery`) USING BTREE, " +
                    "ADD INDEX `idx_swords` (`swords`) USING BTREE, " +
                    "ADD INDEX `idx_axes` (`axes`) USING BTREE, " +
                    "ADD INDEX `idx_acrobatics` (`acrobatics`) USING BTREE, " +
                    "ADD INDEX `idx_fishing` (`fishing`) USING BTREE;", false)) {
                mcMMO.p.getLogger().severe("Failed to index SQL tables. mcrank/mctop may take a long time until this is fixed.");
            }
        }

        killOrphans();
    }

    private void killOrphans() {
        mcMMO.p.getLogger().info("Killing orphans");
        write("DELETE FROM %1$sexperience " +
                 "WHERE NOT EXISTS (SELECT * FROM " +
                 "%1$susers u WHERE " +
                 "%1$sexperience.user_id = u.id);"
                 , false);
        write("DELETE FROM %1$shuds " +
                 "WHERE NOT EXISTS (SELECT * FROM " +
                 "%1$susers u WHERE " +
                 "%1$shuds.user_id = u.id);"
                 , false);
        write("DELETE FROM %1$scooldowns " +
                 "WHERE NOT EXISTS (SELECT * FROM " +
                 "%1$susers u WHERE " +
                 "%1$scooldowns.user_id = u.id);"
                 , false);
        write("DELETE FROM %1$sskills " +
                 "WHERE NOT EXISTS (SELECT * FROM " +
                 "%1$susers u WHERE " +
                 "%1$sskills.user_id = u.id);"
                 , false);
    }

    /**
     * Attempt to execute the SQL query, ignoring the return value.
     *
     * @param sql Query to write.
     * @param suppressErrors Whether to suppress any SQLExceptions from printing
     * @return true if the query was successfully written, false otherwise.
     */
    private boolean write(String sql, boolean suppressErrors) {
        if (!checkConnected()) {
            return false;
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(String.format(sql, tablePrefix));
            statement.executeUpdate();
            return true;
        }
        catch (SQLException ex) {
            if (!suppressErrors) {
                printErrors(ex);
            }
            return false;
        }
        finally {
            tryClose(statement);
        }
    }

    /**
     * Returns the number of rows affected by either a DELETE or UPDATE query
     *
     * @param sql SQL query to execute
     * @return the number of rows affected
     */
    private int update(String sql) {
        int rows = 0;

        if (checkConnected()) {
            PreparedStatement statement = null;

            try {
                statement = connection.prepareStatement(String.format(sql, tablePrefix));
                rows = statement.executeUpdate();
            }
            catch (SQLException ex) {
                printErrors(ex);
            }
            finally {
                tryClose(statement);
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
        HashMap<Integer, ArrayList<String>> rows = new HashMap<Integer, ArrayList<String>>();

        if (checkConnected()) {
            PreparedStatement statement = null;
            ResultSet resultSet;

            try {
                statement = connection.prepareStatement(String.format(sql, tablePrefix));
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
                tryClose(statement);
            }
        }

        return rows;
    }

    /**
     * Get the Integer. Only return first row / first field.
     *
     * @param sql SQL query to execute
     * @return the value in the first row / first field
     */
    private int readInt(PreparedStatement statement) {
        int result = 0;

        if (checkConnected()) {
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
                tryClose(statement);
            }
        }

        return result;
    }

    private void writeMissingRows(int id) {
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement("INSERT IGNORE INTO " + tablePrefix + "experience (user_id) VALUES (?)");
            statement.setInt(1, id);
            statement.execute();
            statement.close();

            statement = connection.prepareStatement("INSERT IGNORE INTO " + tablePrefix + "skills (user_id) VALUES (?)");
            statement.setInt(1, id);
            statement.execute();
            statement.close();

            statement = connection.prepareStatement("INSERT IGNORE INTO " + tablePrefix + "cooldowns (user_id) VALUES (?)");
            statement.setInt(1, id);
            statement.execute();
            statement.close();

            statement = connection.prepareStatement("INSERT IGNORE INTO " + tablePrefix + "huds (user_id, mobhealthbar) VALUES (? ,'" + Config.getInstance().getMobHealthbarDefault().name() + "')");
            statement.setInt(1, id);
            statement.execute();
            statement.close();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            tryClose(statement);
        }
    }

    private void processPurge(Collection<ArrayList<String>> usernames) {
        for (ArrayList<String> user : usernames) {
            Misc.profileCleanup(user.get(0));
        }
    }

    private void saveIntegers(String sql, int... args) {
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement(sql);
            int i = 1;

            for (int arg : args) {
                statement.setInt(i++, arg);
            }

            statement.execute();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            tryClose(statement);
        }
    }

    private void saveLongs(String sql, int id, long... args) {
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement(sql);
            int i = 1;

            for (long arg : args) {
                statement.setLong(i++, arg);
            }

            statement.setInt(i++, id);
            statement.execute();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            tryClose(statement);
        }
    }

    private int readId(String playerName) {
        int id = 0;
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement("SELECT id FROM " + tablePrefix + "users WHERE user = ?");
            statement.setString(1, playerName);
            id = readInt(statement);
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            tryClose(statement);
        }

        return id;
    }

    private void saveLogin(int id, long login) {
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement("UPDATE " + tablePrefix + "users SET lastlogin = ? WHERE id = ?");
            statement.setLong(1, login);
            statement.setInt(2, id);
            statement.execute();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            tryClose(statement);
        }
    }

    private void saveHuds(int userId, String hudType, String mobHealthBar) {
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement("UPDATE " + tablePrefix + "huds SET hudtype = ?, mobhealthbar = ? WHERE user_id = ?");
            statement.setString(1, hudType);
            statement.setString(2, mobHealthBar);
            statement.setInt(3, userId);
            statement.execute();
        }
        catch (SQLException ex) {
            printErrors(ex);
        }
        finally {
            tryClose(statement);
        }
    }

    /**
     * Loads a PlayerProfile from the current row of a ResultSet.
     */
    private PlayerProfile loadFromResult(String playerName, ResultSet result) throws SQLException {
        Map<SkillType, Integer>   skills     = new HashMap<SkillType, Integer>();   // Skill & Level
        Map<SkillType, Float>     skillsXp   = new HashMap<SkillType, Float>();     // Skill & XP
        Map<AbilityType, Integer> skillsDATS = new HashMap<AbilityType, Integer>(); // Ability & Cooldown
        HudType hudType;
        MobHealthbarType mobHealthbarType;

        final int OFFSET_SKILLS = 0; // TODO update these numbers when the query changes (a new skill is added)
        final int OFFSET_XP = 12;
        final int OFFSET_DATS = 24;
        final int OFFSET_OTHER = 36;

        skills.put(SkillType.TAMING, result.getInt(OFFSET_SKILLS + 1));
        skills.put(SkillType.MINING, result.getInt(OFFSET_SKILLS + 2));
        skills.put(SkillType.REPAIR, result.getInt(OFFSET_SKILLS + 3));
        skills.put(SkillType.WOODCUTTING, result.getInt(OFFSET_SKILLS + 4));
        skills.put(SkillType.UNARMED, result.getInt(OFFSET_SKILLS + 5));
        skills.put(SkillType.HERBALISM, result.getInt(OFFSET_SKILLS + 6));
        skills.put(SkillType.EXCAVATION, result.getInt(OFFSET_SKILLS + 7));
        skills.put(SkillType.ARCHERY, result.getInt(OFFSET_SKILLS + 8));
        skills.put(SkillType.SWORDS, result.getInt(OFFSET_SKILLS + 9));
        skills.put(SkillType.AXES, result.getInt(OFFSET_SKILLS + 10));
        skills.put(SkillType.ACROBATICS, result.getInt(OFFSET_SKILLS + 11));
        skills.put(SkillType.FISHING, result.getInt(OFFSET_SKILLS + 12));

        skillsXp.put(SkillType.TAMING, result.getFloat(OFFSET_XP + 1));
        skillsXp.put(SkillType.MINING, result.getFloat(OFFSET_XP + 2));
        skillsXp.put(SkillType.REPAIR, result.getFloat(OFFSET_XP + 3));
        skillsXp.put(SkillType.WOODCUTTING, result.getFloat(OFFSET_XP + 4));
        skillsXp.put(SkillType.UNARMED, result.getFloat(OFFSET_XP + 5));
        skillsXp.put(SkillType.HERBALISM, result.getFloat(OFFSET_XP + 6));
        skillsXp.put(SkillType.EXCAVATION, result.getFloat(OFFSET_XP + 7));
        skillsXp.put(SkillType.ARCHERY, result.getFloat(OFFSET_XP + 8));
        skillsXp.put(SkillType.SWORDS, result.getFloat(OFFSET_XP + 9));
        skillsXp.put(SkillType.AXES, result.getFloat(OFFSET_XP + 10));
        skillsXp.put(SkillType.ACROBATICS, result.getFloat(OFFSET_XP + 11));
        skillsXp.put(SkillType.FISHING, result.getFloat(OFFSET_XP + 12));

        // Taming - Unused - result.getInt(OFFSET_DATS + 1)
        skillsDATS.put(AbilityType.SUPER_BREAKER, result.getInt(OFFSET_DATS + 2));
        // Repair - Unused - result.getInt(OFFSET_DATS + 3)
        skillsDATS.put(AbilityType.TREE_FELLER, result.getInt(OFFSET_DATS + 4));
        skillsDATS.put(AbilityType.BERSERK, result.getInt(OFFSET_DATS + 5));
        skillsDATS.put(AbilityType.GREEN_TERRA, result.getInt(OFFSET_DATS + 6));
        skillsDATS.put(AbilityType.GIGA_DRILL_BREAKER, result.getInt(OFFSET_DATS + 7));
        // Archery - Unused - result.getInt(OFFSET_DATS + 8)
        skillsDATS.put(AbilityType.SERRATED_STRIKES, result.getInt(OFFSET_DATS + 9));
        skillsDATS.put(AbilityType.SKULL_SPLITTER, result.getInt(OFFSET_DATS + 10));
        // Acrobatics - Unused - result.getInt(OFFSET_DATS + 11)
        skillsDATS.put(AbilityType.BLAST_MINING, result.getInt(OFFSET_DATS + 12));

        try {
            hudType = HudType.valueOf(result.getString(OFFSET_OTHER + 1));
        }
        catch (Exception e) {
            hudType = HudType.STANDARD; // Shouldn't happen unless database is being tampered with
        }

        try {
            mobHealthbarType = MobHealthbarType.valueOf(result.getString(OFFSET_OTHER + 2));
        }
        catch (Exception e) {
            mobHealthbarType = Config.getInstance().getMobHealthbarDefault();
        }

        return new PlayerProfile(playerName, skills, skillsXp, skillsDATS, hudType, mobHealthbarType);
    }

    private void printErrors(SQLException ex) {
        mcMMO.p.getLogger().severe("SQLException: " + ex.getMessage());
        mcMMO.p.getLogger().severe("SQLState: " + ex.getSQLState());
        mcMMO.p.getLogger().severe("VendorError: " + ex.getErrorCode());
    }

    private void tryClose(Statement c) {
        if (c == null) return;

        try {
            c.close();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private void tryClose(ResultSet c) {
        if (c == null) return;

        try {
            c.close();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
}