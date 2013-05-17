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

    protected SQLStatements(Connection conn, String tablePrefix) throws SQLException {
        this.statements = new HashMap<String, PreparedStatement>();
        loadStatements(conn, tablePrefix);
    }

    private void loadStatements(Connection conn, String tablePrefix) throws SQLException {
        statements.put("loadUser", conn.prepareStatement(
            "SELECT " +
            "s.taming, s.mining, s.repair, s.woodcutting, s.unarmed, s.herbalism, s.excavation, s.archery, s.swords, s.axes, s.acrobatics, s.fishing, " +
            "e.taming, e.mining, e.repair, e.woodcutting, e.unarmed, e.herbalism, e.excavation, e.archery, e.swords, e.axes, e.acrobatics, e.fishing, " +
            "c.taming, c.mining, c.repair, c.woodcutting, c.unarmed, c.herbalism, c.excavation, c.archery, c.swords, c.axes, c.acrobatics, c.blast_mining, " +
            "h.hudtype, h.mobhealthbar " +
            "FROM " + tablePrefix + "users u " +
            "JOIN " + tablePrefix + "skills s ON (u.id = s.user_id) " +
            "JOIN " + tablePrefix + "experience e ON (u.id = e.user_id) " +
            "JOIN " + tablePrefix + "cooldowns c ON (u.id = c.user_id) " +
            "JOIN " + tablePrefix + "huds h ON (u.id = h.user_id) " +
            "WHERE u.user = ?"
        ));
        statements.put("getId", conn.prepareStatement(
            "SELECT id FROM " + tablePrefix + "users WHERE user = ?"
        ));
        statements.put("missingSkills", conn.prepareStatement(
            "INSERT IGNORE INTO " + tablePrefix + "skills (user_id) VALUES (?)"
        ));
        statements.put("missingExperience", conn.prepareStatement(
            "INSERT IGNORE INTO " + tablePrefix + "experience (user_id) VALUES (?)"
        ));
        statements.put("missingCooldowns", conn.prepareStatement(
            "INSERT IGNORE INTO " + tablePrefix + "cooldowns (user_id) VALUES (?)"
        ));
        statements.put("missingHuds", conn.prepareStatement(
            "INSERT IGNORE INTO " + tablePrefix + "huds (user_id, mobhealthbar) VALUES (? ,'" + Config.getInstance().getMobHealthbarDefault().name() + "')"
        ));
        statements.put("saveLogin", conn.prepareStatement(
            "UPDATE " + tablePrefix + "users SET lastlogin = ? WHERE id = ?"
        ));
        statements.put("saveHuds", conn.prepareStatement(
            "UPDATE " + tablePrefix + "huds SET    hudtype = ?, mobhealthbar = ? WHERE user_id = ?"
        ));
        statements.put("saveCooldowns", conn.prepareStatement(
            "UPDATE " + tablePrefix + "cooldowns SET " +
            "  mining = ?, woodcutting = ?, unarmed = ?, herbalism = ?" +
            ", excavation = ?,  swords = ?, axes = ?, blast_mining = ? WHERE user_id = ?"
        ));
        statements.put("saveSkills", conn.prepareStatement(
            "UPDATE " + tablePrefix + "skills SET " +
            "  taming = ?, mining = ?, repair = ?, woodcutting = ?, unarmed = ?" +
            ", herbalism = ?, excavation = ?, archery = ?, swords = ?" +
            ", axes = ?, acrobatics = ?, fishing = ? WHERE user_id = ?"
        ));
        statements.put("saveExperience", conn.prepareStatement(
            "UPDATE " + tablePrefix + "experience SET " +
            "  taming = ?, mining = ?, repair = ?, woodcutting = ?, unarmed = ?" +
            ", herbalism = ?, excavation = ?, archery = ?, swords = ?" +
            ", axes = ?, acrobatics = ?, fishing = ? WHERE user_id = ?"
        ));
        statements.put("newUser", conn.prepareStatement(
            "INSERT INTO " + tablePrefix + "users (user, lastlogin) VALUES (?, ?)"
        ));
        for (SkillType skill : SkillType.nonChildSkills()) {
            statements.put("mcrank_" + skill.name() + "_A",  conn.prepareStatement(
                "SELECT COUNT(*) AS rank FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id WHERE " + skill.name().toLowerCase() + " > 0 " +
                "AND " + skill.name().toLowerCase() + " > (SELECT " + skill.name().toLowerCase() + " FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id " +
                "WHERE user = ?)"
            ));
            statements.put("mcrank_" + skill.name() + "_B",  conn.prepareStatement(
                "SELECT user, " + skill.name().toLowerCase() + " FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id WHERE " + skill.name().toLowerCase() + " > 0 " +
                "AND " + skill.name().toLowerCase() + " = (SELECT " + skill.name().toLowerCase() + " FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id " +
                "WHERE user = ?) ORDER BY user"
            ));
            statements.put("mctop_" + skill.name(),  conn.prepareStatement(
                "SELECT " + skill.name().toLowerCase() + ", user, NOW() FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON (user_id = id) WHERE " + 
                skill.name().toLowerCase() + " > 0 ORDER BY " + skill.name().toLowerCase() + " DESC, user LIMIT ?, ?"
            ));
        }
        statements.put("mcrank_ALL_A", conn.prepareStatement(
            "SELECT COUNT(*) AS rank FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id " +
            "WHERE taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > 0 " +
            "AND taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > " +
            "(SELECT taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
            "FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id WHERE user = ?)"
        ));
        statements.put("mcrank_ALL_B", conn.prepareStatement(
            "SELECT user, taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
            "FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id " +
            "WHERE taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > 0 " +
            "AND taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing = " +
            "(SELECT taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing " +
            "FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON user_id = id WHERE user = ?) ORDER BY user"
        ));
        statements.put("mctop_ALL",  conn.prepareStatement(
            "SELECT taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing, user, NOW() FROM " + tablePrefix + "users JOIN " + tablePrefix + "skills ON (user_id = id) WHERE " + 
            "taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing > 0 ORDER BY taming+mining+woodcutting+repair+unarmed+herbalism+excavation+archery+swords+axes+acrobatics+fishing DESC, user LIMIT ?, ?"
        ));
    }

    public static SQLStatements getInstance() {
        return instance;
    }

    public PreparedStatement getStatement(String key) {
        return statements.get(key);
    }
}
