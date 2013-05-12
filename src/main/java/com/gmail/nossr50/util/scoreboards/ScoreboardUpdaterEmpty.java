package com.gmail.nossr50.util.scoreboards;

import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.util.scoreboards.McmmoPlayerScoreboard.McmmoScoreboardType;

public class ScoreboardUpdaterEmpty implements ScoreboardUpdater {
    private static ScoreboardUpdaterEmpty instance = new ScoreboardUpdaterEmpty();
    public static ScoreboardUpdaterEmpty get() {
        return instance;
    }
    private ScoreboardUpdaterEmpty() { }

    public McmmoScoreboardType getType() {
        return McmmoScoreboardType.EMPTY;
    }

    public boolean needsUpdatingEver() { return false; }
    public boolean needsUpdatingOnSelfLevelUp(SkillType skill) { return false; }
    public boolean needsUpdatingOnSelfXPChange(SkillType skill) { return false; }
    public boolean needsUpdatingOnPlayerLevelUp(String other, SkillType skill) { return false; }

    public String getObjectiveName() {
        return "mcmmo.empty";
    }

    public String getCriteriaName() {
        return "mcmmo.empty";
    }

    public String getHeader() {
        return "";
    }

    public void updateScores(McmmoPlayerScoreboard wrapper, Objective target) {
        Scoreboard scoreboard = wrapper.getScoreboard();
        scoreboard.clearSlot(DisplaySlot.SIDEBAR); // remove anything there
        wrapper.getBukkitPlayer().setScoreboard(scoreboard);
    }

    public boolean equals(Object other) {
        if (other == null) return false;
        return other == get();
    }
}
