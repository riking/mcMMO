package com.gmail.nossr50.util.scoreboards;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.scoreboard.Objective;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.scoreboards.McmmoPlayerScoreboard.McmmoScoreboardType;
import com.gmail.nossr50.util.skills.SkillUtils;

public class ScoreboardUpdaterSkill implements ScoreboardUpdater {
    private SkillType skill;

    public ScoreboardUpdaterSkill(SkillType skill) {
        this.skill = skill;
    }

    /**
     * Two ScoreboardUpdaterSkills are considered equal if they are tracking
     * the same skill. The player we get the values from is not considered,
     * as it is always the player we're showing them to.
     */
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof ScoreboardUpdaterSkill)) return false;
        return this.skill == ((ScoreboardUpdaterSkill)other).skill;
    }

    public boolean needsUpdatingEver() { return true; }
    public boolean needsUpdatingOnSelfLevelUp(SkillType s) {
        return s == skill;
    }
    public boolean needsUpdatingOnSelfXPChange(SkillType s) {
        return s == skill;
    }
    public boolean needsUpdatingOnPlayerLevelUp(String other, SkillType s) { return false; }

    public void updateScores(McmmoPlayerScoreboard wrapper, Objective target) {
        PlayerProfile profile = wrapper.getMcmmoPlayer().getProfile();
        Server server = mcMMO.p.getServer();

        int level = profile.getSkillLevel(skill);
        int current = profile.getSkillXpLevel(skill);
        int remaining = profile.getXpToLevel(skill) - current;

        OfflinePlayer labelLevel = server.getOfflinePlayer("Level");
        OfflinePlayer labelCurrent = server.getOfflinePlayer("Current XP");
        OfflinePlayer labelRemaining = server.getOfflinePlayer("Remaining XP");

        System.out.println("Updating skill scoreboard (" + skill.toString() + ") for " + wrapper.getPlayerName());
        target.getScore(labelLevel).setScore(level);
        target.getScore(labelCurrent).setScore(current);
        target.getScore(labelRemaining).setScore(remaining);
    }

    public String getObjectiveName() {
        return "mosk." + SkillUtils.getSkillName(skill);
    }

    public String getCriteriaName() {
        return "mosk." + SkillUtils.getSkillName(skill);
    }

    public String getHeader() {
        return ChatColor.AQUA + SkillUtils.getSkillName(skill);
    }

    public McmmoScoreboardType getType() {
        return McmmoScoreboardType.PLAYER_SKILL;
    }
}
