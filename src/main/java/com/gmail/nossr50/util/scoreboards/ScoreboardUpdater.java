package com.gmail.nossr50.util.scoreboards;

import org.bukkit.scoreboard.Objective;

import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.scoreboards.McmmoPlayerScoreboard.McmmoScoreboardType;

/**
 * This class serves as a gateway to update the scores on a mcmmo Scoreboard
 * in a well-controlled manner.
 */
public interface ScoreboardUpdater {
    /**
     * May not return false if any of the other methods below ever return true.
     */
    public boolean needsUpdatingEver();
    public boolean needsUpdatingOnSelfLevelUp(SkillType skill);
    public boolean needsUpdatingOnSelfXPChange(SkillType skill);
    public boolean needsUpdatingOnPlayerLevelUp(String other, SkillType skill);

    public void updateScores(McmmoPlayerScoreboard wrapper, Objective target);

    public String getObjectiveName();
    public String getCriteriaName();
    public String getHeader();

    public McmmoScoreboardType getType();

    public boolean equals(Object other);
}
