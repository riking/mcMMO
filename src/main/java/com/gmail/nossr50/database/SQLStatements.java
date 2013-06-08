package com.gmail.nossr50.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.skills.SkillType;

public class SQLStatements {
    private static SQLStatements instance;
    private Map<String, PreparedStatement> statements;
    private Connection conn;
    private String tablePrefix;

    protected SQLStatements(Connection conn, String tablePrefix) throws SQLException {
        this.statements = new HashMap<String, PreparedStatement>();
        this.conn = conn;
        this.tablePrefix = tablePrefix;
        loadStatements(conn);
    }

    private void addStatement(String key, String sql) throws SQLException {
        statements.put(key, conn.prepareStatement(sql.replaceAll("{TAB}", tablePrefix)));
    }

    private void loadStatements(Connection conn) throws SQLException {
        addStatement("loadUser",
            "SELECT " +
            "s.taming, s.mining, s.repair, s.woodcutting, s.unarmed, s.herbalism, s.excavation, s.archery, s.swords, s.axes, s.acrobatics, s.fishing, " +
            "e.taming, e.mining, e.repair, e.woodcutting, e.unarmed, e.herbalism, e.excavation, e.archery, e.swords, e.axes, e.acrobatics, e.fishing, " +
            "c.taming, c.mining, c.repair, c.woodcutting, c.unarmed, c.herbalism, c.excavation, c.archery, c.swords, c.axes, c.acrobatics, c.blast_mining, " +
            "h.hudtype, h.mobhealthbar " +
            "FROM {TAB}users u " +
            "JOIN {TAB}skills s ON (u.id = s.user_id) " +
            "JOIN {TAB}experience e ON (u.id = e.user_id) " +
            "JOIN {TAB}cooldowns c ON (u.id = c.user_id) " +
            "JOIN {TAB}huds h ON (u.id = h.user_id) " +
            "WHERE u.user = ?"
        );
        addStatement("getId",
            "SELECT id FROM {TAB}users WHERE user = ?"
        );
        addStatement("missingSkills",
            "INSERT IGNORE INTO {TAB}skills (user_id) VALUES (?)"
        );
        addStatement("missingExperience",
            "INSERT IGNORE INTO {TAB}experience (user_id) VALUES (?)"
        );
        addStatement("missingCooldowns",
            "INSERT IGNORE INTO {TAB}cooldowns (user_id) VALUES (?)"
        );
        addStatement("missingHuds",
            "INSERT IGNORE INTO {TAB}huds (user_id, mobhealthbar) VALUES (? ,'" + Config.getInstance().getMobHealthbarDefault().name() + "')"
        );
        addStatement("saveLogin",
            "UPDATE {TAB}users SET lastlogin = ? WHERE id = ?"
        );
        addStatement("saveHuds",
            "UPDATE {TAB}huds SET hudtype = ?, mobhealthbar = ? WHERE user_id = ?"
        );
        addStatement("saveCooldowns",
            "UPDATE {TAB}cooldowns SET " +
            "  mining = ?, woodcutting = ?, unarmed = ?, herbalism = ?" +
            ", excavation = ?,  swords = ?, axes = ?, blast_mining = ? WHERE user_id = ?"
        );
        addStatement("saveSkills",
            "UPDATE {TAB}skills SET " +
            "  taming = ?, mining = ?, repair = ?, woodcutting = ?, unarmed = ?" +
            ", herbalism = ?, excavation = ?, archery = ?, swords = ?" +
            ", axes = ?, acrobatics = ?, fishing = ? WHERE user_id = ?"
        );
        addStatement("saveExperience",
            "UPDATE {TAB}experience SET " +
            "  taming = ?, mining = ?, repair = ?, woodcutting = ?, unarmed = ?" +
            ", herbalism = ?, excavation = ?, archery = ?, swords = ?" +
            ", axes = ?, acrobatics = ?, fishing = ? WHERE user_id = ?"
        );
        addStatement("newUser",
            "INSERT INTO {TAB}users (user, lastlogin) VALUES (?, ?)"
        );
        for (SkillType skill : SkillType.nonChildSkills()) {
            addStatement("mcrank_" + skill.name() + "_A",
                "SELECT COUNT(*) AS rank FROM {TAB}users JOIN {TAB}skills ON user_id = id WHERE " + skill.name().toLowerCase() + " > 0 " +
                "AND " + skill.name().toLowerCase() + " > (SELECT " + skill.name().toLowerCase() + " FROM {TAB}users JOIN {TAB}skills ON user_id = id " +
                "WHERE user = ?)"
            );
            addStatement("mcrank_" + skill.name() + "_B",
                "SELECT user, " + skill.name().toLowerCase() + " FROM {TAB}users JOIN {TAB}skills ON user_id = id WHERE " + skill.name().toLowerCase() + " > 0 " +
                "AND " + skill.name().toLowerCase() + " = (SELECT " + skill.name().toLowerCase() + " FROM {TAB}users JOIN {TAB}skills ON user_id = id " +
                "WHERE user = ?) ORDER BY user"
            );
            addStatement("mctop_" + skill.name(),
                "SELECT " + skill.name().toLowerCase() + ", user, NOW() FROM {TAB}users JOIN {TAB}skills ON (user_id = id) WHERE " +
                skill.name().toLowerCase() + " > 0 ORDER BY " + skill.name().toLowerCase() + " DESC, user LIMIT ?, ?"
            );
        }
        addStatement("mcrank_ALL_A",
            "SELECT COUNT(*) AS rank FROM {TAB}users JOIN {TAB}skills ON user_id = id " +
            "WHERE taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > 0 " +
            "AND taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > " +
            "(SELECT taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
            "FROM {TAB}users JOIN {TAB}skills ON user_id = id WHERE user = ?)"
        );
        addStatement("mcrank_ALL_B",
            "SELECT user, taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
            "FROM {TAB}users JOIN {TAB}skills ON user_id = id " +
            "WHERE taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > 0 " +
            "AND taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing = " +
            "(SELECT taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
            "FROM {TAB}users JOIN {TAB}skills ON user_id = id WHERE user = ?) ORDER BY user"
        );
        addStatement("mctop_ALL",
            "SELECT taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing, user, NOW() FROM {TAB}users JOIN {TAB}skills ON (user_id = id) WHERE " +
            "taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > 0 ORDER BY taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing DESC, user LIMIT ?, ?"
        );
        addStatement("powerlessPurge",
                "SELECT u.user FROM OLD TABLE (" +
                "DELETE FROM u, e, h, s, c USING {TAB}users u " +
                "JOIN {TAB}experience e ON (u.id = e.user_id) " +
                "JOIN {TAB}huds h ON (u.id = h.user_id) " +
                "JOIN {TAB}skills s ON (u.id = s.user_id) " +
                "JOIN {TAB}cooldowns c ON (u.id = c.user_id) " +
                "WHERE (s.taming+s.mining+s.woodcutting+s.repair+s.unarmed+s.herbalism+s.excavation+s.archery+s.swords+s.axes+s.acrobatics+s.fishing) = 0" +
                ")"
        );
        addStatement("oldPurge",
                "SELECT u.user FROM OLD TABLE (" +
                "DELETE FROM u, e, h, s, c USING {TAB}users u " +
                "JOIN {TAB}experience e ON (u.id = e.user_id) " +
                "JOIN {TAB}huds h ON (u.id = h.user_id) " +
                "JOIN {TAB}skills s ON (u.id = s.user_id) " +
                "JOIN {TAB}cooldowns c ON (u.id = c.user_id) " +
                "WHERE ((? - lastlogin*1000) > ?)" +
                ")"
        );
    }

    public static SQLStatements getInstance() {
        return instance;
    }

    public PreparedStatement getStatement(String key) {
        return statements.get(key);
    }

    public void closeStatements() {
        for (PreparedStatement statement : statements.values()) {
            try {
                statement.close();
            }
            catch (SQLException e) {
                // This only happens on shutdown so we can ignore these probably
            }
        }

        try {
            conn.close();
        }
        catch (SQLException e) {
            // Meh
        }
    }
}
