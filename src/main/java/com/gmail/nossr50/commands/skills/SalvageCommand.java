package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.salvage.Salvage;
import com.gmail.nossr50.skills.salvage.SalvageManager;
import com.gmail.nossr50.util.Permissions;

public class SalvageCommand extends SkillCommand {
    private boolean canAdvancedSalvage;
    private boolean canArcaneSalvage;

    public SalvageCommand() {
        super(SkillType.SALVAGE);
    }

    @Override
    protected void dataCalculations() {
        // TODO Auto-generated method stub

    }

    @Override
    protected void permissionsCheck() {
        canAdvancedSalvage = Permissions.advancedSalvage(player);
        canArcaneSalvage = Permissions.arcaneSalvage(player);
    }

    @Override
    protected boolean effectsHeaderPermissions() {
        return canAdvancedSalvage || canArcaneSalvage;
    }

    @Override
    protected void effectsDisplay() {
        if (canAdvancedSalvage) {
            player.sendMessage(LocaleLoader.getString("Effects.Template", LocaleLoader.getString("Salvage.Effect.0"), LocaleLoader.getString("Salvage.Effect.1")));
        }

        if (canArcaneSalvage) {
            player.sendMessage(LocaleLoader.getString("Effects.Template", LocaleLoader.getString("Salvage.Effect.2"), LocaleLoader.getString("Salvage.Effect.3")));
        }
    }

    @Override
    protected boolean statsHeaderPermissions() {
        return canAdvancedSalvage || canArcaneSalvage;
    }

    @Override
    protected void statsDisplay() {
        if (canAdvancedSalvage) {
            if (skillValue < Salvage.advancedSalvageUnlockLevel) {
                player.sendMessage(LocaleLoader.getString("Ability.Generic.Template.Lock", LocaleLoader.getString("Salvage.Ability.Locked.0"), Salvage.advancedSalvageUnlockLevel));
            }
            else {
                player.sendMessage(LocaleLoader.getString("Ability.Generic.Template", LocaleLoader.getString("Salvage.Ability.Bonus.0"), LocaleLoader.getString("Salvage.Ability.Bonus.1")));
            }
        }

        if (canArcaneSalvage) {
            SalvageManager salvageManager = mcMMOPlayer.getSalvageManager();

            player.sendMessage(LocaleLoader.getString("Ability.Generic.Template", LocaleLoader.getString("Salvage.Arcane.Rank"), salvageManager.getArcaneSalvageRank()));

            if (Salvage.arcaneSalvageEnchantLoss) {
                player.sendMessage(LocaleLoader.getString("Ability.Generic.Template", LocaleLoader.getString("Salvage.Arcane.ExtractFull"), salvageManager.getExtractFullEnchantChance()));
            }

            if (Salvage.arcaneSalvageDowngrades) {
                player.sendMessage(LocaleLoader.getString("Ability.Generic.Template", LocaleLoader.getString("Salvage.Arcane.ExtractPartial"), salvageManager.getExtractPartialEnchantChance()));
            }
        }
    }
}
