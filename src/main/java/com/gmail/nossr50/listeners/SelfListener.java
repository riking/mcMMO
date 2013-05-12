package com.gmail.nossr50.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.events.experience.McMMOPlayerLevelUpEvent;
import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import com.gmail.nossr50.util.scoreboards.ScoreboardManager;
import com.gmail.nossr50.util.skills.ParticleEffectUtils;

public class SelfListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLevelUp(McMMOPlayerLevelUpEvent event) {
        if (!Config.getInstance().getLevelUpEffectsEnabled()) {
            return;
        }

        int tier = Config.getInstance().getLevelUpEffectsTier();

        if (tier <= 0) {
            return;
        }

        Player player = event.getPlayer();
        float skillValue = event.getSkillLevel();

        if ((skillValue % tier) == 0) {
            ParticleEffectUtils.runescapeModeCelebration(player, event.getSkill());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void updateScoreboardsLevelUp(McMMOPlayerLevelUpEvent event) {
        if (!Config.getInstance().getLiveScoreboardsEnabled()) return;
        final String player = event.getPlayer().getName();
        final SkillType type = event.getSkill();
        // hack because the info isn't updated until next tick
        Bukkit.getScheduler().runTask(mcMMO.p, new Runnable() {
            @Override
            public void run() {
                ScoreboardManager.handleLevelChange(player, type);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void updateScoreboardsXpChange(McMMOPlayerXpGainEvent event) {
        if (!Config.getInstance().getLiveScoreboardsEnabled()) return;
        final String player = event.getPlayer().getName();
        final SkillType type = event.getSkill();
        // hack because the info isn't updated until next tick
        Bukkit.getScheduler().runTask(mcMMO.p, new Runnable() {
            @Override
            public void run() {
                ScoreboardManager.handleXpChange(player, type);
            }
        });
    }
}
