package com.gmail.nossr50.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.Misc;

public class SqlStatements {
    /**
     * @param none
     * @return Many rows. Column 1 - usernames
     */
    public PreparedStatement purgePowerlessGet;
    /**
     * @param none
     * @return count - number deleted
     */
    public PreparedStatement purgePowerlessRun;
    /**
     * @param 1 - current time (in <b>seconds</b> since 1970)
     * @param 2 - purge time (seconds in 6 months)
     * @return Many rows. Column 1 - usernames
     */
    public PreparedStatement purgeOldGet;
    /**
     * @param 1 - current time (in <b>seconds</b> since 1970)
     * @param 2 - purge time (seconds in 6 months)
     * @return count - number deleted
     */
    public PreparedStatement purgeOldRun;

    /**
     * @param 1 - username
     * @return count - not 0 if successful
     */
    public PreparedStatement removeUser;
    /**
     * @param 1 - user name
     * @param 2 - current time (in <b>seconds</b>)
     * @return getGeneratedKeys() - user id
     */
    public PreparedStatement createUserAndReturnId;

    /**
     * @param 1 - user id
     */
    public PreparedStatement newHudsEntry;
    /**
     * @param 1 - user id
     */
    public PreparedStatement newCooldownsEntry;
    /**
     * @param 1 - user id
     */
    public PreparedStatement newExperienceEntry;
    /**
     * @param 1 - user id
     */
    public PreparedStatement newSkillsEntry;

    /**
     * @param 1 - user name
     */
    public PreparedStatement getUserId;

    /**
     * @param 1 - login time (in <b>seconds</b>)
     * @param 2 - user id
     */
    public PreparedStatement saveLogin;
    /**
     * @param 1 - hudtype
     * @param 2 - mob healthbar type
     * @param 3 - user id
     */
    public PreparedStatement saveHuds;
    /**
     * @param 1 - mining
     * @param 2 - woodcutting
     * @param 3 - unarmed
     * @param 4 - herbalism
     * @param 5 - excavation
     * @param 6 - swords
     * @param 7 - axes
     * @param 8 - blast mining
     * @param 9 - user id
     */
    public PreparedStatement saveCooldowns;
    /**
     * @param 1 - taming
     * @param 2 - mining
     * @param 3 - repair
     * @param 4 - woodcutting
     * @param 5 - unarmed
     * @param 6 - herbalism
     * @param 7 - excavation
     * @param 8 - archery
     * @param 9 - swords
     * @param 10 - axes
     * @param 11 - acrobatics
     * @param 12 - fishing
     * @param 13 - user id
     */
    public PreparedStatement saveExperience;
    /**
     * @param 1 - taming
     * @param 2 - mining
     * @param 3 - repair
     * @param 4 - woodcutting
     * @param 5 - unarmed
     * @param 6 - herbalism
     * @param 7 - excavation
     * @param 8 - archery
     * @param 9 - swords
     * @param 10 - axes
     * @param 11 - acrobatics
     * @param 12 - fishing
     * @param 13 - user id
     */
    public PreparedStatement saveSkills;

    /**
     * @return 1 - user name
     */
    public PreparedStatement getUserList;
    /**
     * The skill order within each return section is taming, mining, repair,
     * woodcutting, unarmed, herbalism, excavation, archery, swords, axes,
     * acrobatics, fishing
     *
     * @param 1 - user id
     * @return Single row; columns: <br>
     *         1 - 12: Skill level <br>
     *         13 - 24: Experience levels <br>
     *         25 - 36: Cooldowns <i>(36 is Blast Mining, not Fishing)</i> <br>
     *         37: Hud type <br>
     *         38: Mob healthbar type
     */
    public PreparedStatement getPlayerData;
    /**
     * The skill order within each return section is taming, mining, repair,
     * woodcutting, unarmed, herbalism, excavation, archery, swords, axes,
     * acrobatics, fishing
     *
     * @return Many rows; columns: <br>
     *         1: User name <br>
     *         2 - 13: Skill level <br>
     *         14 - 25: Experience levels <br>
     *         26 - 37: Cooldowns <i>(36 is Blast Mining, not Fishing)</i> <br>
     *         38: Hud type <br>
     *         39: Mob healthbar type
     */
    public PreparedStatement getAllPlayerData;

    public PreparedStatement readPowerLeaderboard;
    public PreparedStatement countHigherPowerRank;
    public PreparedStatement readSamePowerRank;

    // Don't bother preparing these, they're only used once
    // public PreparedStatement createTableUser;
    // public PreparedStatement createTableHuds;
    // public PreparedStatement createTableCooldowns;
    // public PreparedStatement createTableSkills;
    // public PreparedStatement createTableExperience;

    // Fixes can't be optimized by the DB because they're only valid on older versions.
    // Hence, preparing the fixes is useless and will result in a SQLException.
    // Additionally, the checks will result in a SQLException when they need to run, which will get thrown on creation.
    public PreparedStatement checkBlastMining;
    //public PreparedStatement fixBlastMining;
    public PreparedStatement checkFishing;
    //public PreparedStatement fixFishing;
    public PreparedStatement checkIndex;
    //public PreparedStatement fixIndex;
    public PreparedStatement checkHealthbars;
    //public PreparedStatement fixHealthbars;
    public PreparedStatement killOrphans;
    public PreparedStatement dropPartyName;

    public HashMap<SkillType, PerSkillStatementSet> perSkill;

    public class PerSkillStatementSet {
        public PreparedStatement readLeaderboard;
        public PreparedStatement countHigherRank;
        public PreparedStatement readSameRank;
    }

    private SQLDatabaseManager dbman;

    protected SqlStatements(SQLDatabaseManager dbman) {
        this.dbman = dbman;
    }

    public void init() {
        try {
            perSkill = new HashMap<SkillType, PerSkillStatementSet>();
            for (SkillType type : SkillType.nonChildSkills()) {
                initPerSkill(type);
            }

            // custom constructor
            createUserAndReturnId = dbman.connection.prepareStatement(String.format("INSERT INTO %1$susers (user, lastlogin) VALUES (?, ?)", dbman.tablePrefix), Statement.RETURN_GENERATED_KEYS);

            purgePowerlessGet = createStmt("SELECT u.user FROM %1$sskills AS s, %1$susers AS u WHERE s.user_id = u.id AND (s.taming+s.mining+s.woodcutting+s.repair+s.unarmed+s.herbalism+s.excavation+s.archery+s.swords+s.axes+s.acrobatics+s.fishing) = 0");
            purgeOldGet = createStmt("SELECT user FROM %1$susers WHERE ((? - lastlogin) > ?)");
            purgePowerlessRun = createStmt("DELETE FROM u, e, h, s, c USING %1$susers u " + "JOIN %1$sexperience e ON (u.id = e.user_id) " + "JOIN %1$shuds h ON (u.id = h.user_id) " + "JOIN %1$sskills s ON (u.id = s.user_id) " + "JOIN %1$scooldowns c ON (u.id = c.user_id) " + "WHERE (s.taming+s.mining+s.woodcutting+s.repair+s.unarmed+s.herbalism+s.excavation+s.archery+s.swords+s.axes+s.acrobatics+s.fishing) = 0");
            purgeOldRun = createStmt("DELETE FROM u, e, h, s, c USING %1$susers u " + "JOIN %1$sexperience e ON (u.id = e.user_id) " + "JOIN %1$shuds h ON (u.id = h.user_id) " + "JOIN %1$sskills s ON (u.id = s.user_id) " + "JOIN %1$scooldowns c ON (u.id = c.user_id) " + "WHERE ((? - lastlogin) > ?)");
            removeUser = createStmt("DELETE FROM u, e, h, s, c USING %1$susers u " + "JOIN %1$sexperience e ON (u.id = e.user_id) " + "JOIN %1$shuds h ON (u.id = h.user_id) " + "JOIN %1$sskills s ON (u.id = s.user_id) " + "JOIN %1$scooldowns c ON (u.id = c.user_id) " + "WHERE u.user = ?");

            saveLogin = createStmt("UPDATE %1$susers SET lastlogin = ? WHERE id = ?");
            saveHuds = createStmt("UPDATE %1$shuds SET hudtype = ?, mobhealthbar = ? WHERE user_id = ?");
            saveCooldowns = createStmt("UPDATE %1$scooldowns SET " + "  mining = ?, woodcutting = ?, unarmed = ?" + ", herbalism = ?, excavation = ?, swords = ?" + ", axes = ?, blast_mining = ? WHERE user_id = ?");
            saveSkills = createStmt("UPDATE %1$sskills SET " + " taming = ?, mining = ?, repair = ?, woodcutting = ?" + ", unarmed = ?, herbalism = ?, excavation = ?" + ", archery = ?, swords = ?, axes = ?, acrobatics = ?" + ", fishing = ? WHERE user_id = ?");
            saveExperience = createStmt("UPDATE %1$sexperience SET " + " taming = ?, mining = ?, repair = ?, woodcutting = ?" + ", unarmed = ?, herbalism = ?, excavation = ?" + ", archery = ?, swords = ?, axes = ?, acrobatics = ?" + ", fishing = ? WHERE user_id = ?");

            newExperienceEntry = createStmt("INSERT IGNORE INTO %1$sexperience (user_id) VALUES (?)");
            newSkillsEntry = createStmt("INSERT IGNORE INTO %1$sskills (user_id) VALUES (?)");
            newCooldownsEntry = createStmt("INSERT IGNORE INTO %1$scooldowns (user_id) VALUES (?)");
            newHudsEntry = createStmt("INSERT IGNORE INTO %1$shuds (user_id, mobhealthbar) VALUES (?, '%s')", Config.getInstance().getMobHealthbarDefault().name());
            getUserId = createStmt("SELECT id FROM %1$susers WHERE user = ?");
            getUserList = createStmt("SELECT user FROM %1$susers");
            getPlayerData = createStmt("SELECT " + "s.taming, s.mining, s.repair, s.woodcutting, s.unarmed, s.herbalism, s.excavation, s.archery, s.swords, s.axes, s.acrobatics, s.fishing, " + "e.taming, e.mining, e.repair, e.woodcutting, e.unarmed, e.herbalism, e.excavation, e.archery, e.swords, e.axes, e.acrobatics, e.fishing, " + "c.taming, c.mining, c.repair, c.woodcutting, c.unarmed, c.herbalism, c.excavation, c.archery, c.swords, c.axes, c.acrobatics, c.blast_mining, " + "h.hudtype, h.mobhealthbar " + "FROM %1$susers u " + "JOIN %1$sskills s ON (u.id = s.user_id) " + "JOIN %1$sexperience e ON (u.id = e.user_id) " + "JOIN %1$scooldowns c ON (u.id = c.user_id) " + "JOIN %1$shuds h ON (u.id = h.user_id) " + "WHERE u.user = ?");
        } catch (SQLException e) {

        }
    }

    private void initPerSkill(SkillType type) throws SQLException {
        PerSkillStatementSet set = new PerSkillStatementSet();
        String skill = type.toString().toLowerCase();
        set.readLeaderboard = createStmt("SELECT %2$s, user, NOW() FROM %1$susers JOIN %1$sskills ON (user_id = id) WHERE %2$s > 0 ORDER BY %2$s DESC, user LIMIT ?, ?", skill);
    }

    /**
     * Put %1$s in the sql string to replace in the table prefix.
     */
    private PreparedStatement createStmt(String sql) throws SQLException {
        return dbman.connection.prepareStatement(String.format(sql, dbman.tablePrefix));
    }

    private PreparedStatement createStmt(String sql, String arg2) throws SQLException {
        return dbman.connection.prepareStatement(String.format(sql, dbman.tablePrefix, arg2));
    }

    private PreparedStatement createStmt(String sql, String arg2, String arg3) throws SQLException {
        return dbman.connection.prepareStatement(String.format(sql, dbman.tablePrefix, arg2, arg3));
    }

    private PreparedStatement createStmt(String sql, String arg2, String arg3, String arg4) throws SQLException {
        return dbman.connection.prepareStatement(String.format(sql, dbman.tablePrefix, arg2, arg3, arg4));
    }

    private String replaceTableName(String fmt) {
        return String.format(fmt, dbman.tablePrefix);
    }
}
