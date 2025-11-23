package Maikol.feature.impl.misc;

import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;

public class Teams extends Module {

    private static Teams instance = null;

    public Teams() {
        super("Teams");
        instance = this;
        category = Category.misc;
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }
    public static Teams getInstance() {
        return instance;
    }
    public static boolean isTeammate(Player self, Player target) {
        if (self == null || target == null) return false;
        Integer selfColor = getLeatherArmorColor(self);
        if (selfColor == null) {
            return false;
        }
        Integer targetColor = getLeatherArmorColor(target);
        if (targetColor == null) {
            return false;
        }
        return selfColor.equals(targetColor);
    }
    private static Integer getLeatherArmorColor(Player player) {
        if (player == null) return null;
        for (ItemStack armorStack : player.getArmorSlots()) {
            if (armorStack.isEmpty()) continue;
            if (armorStack.getItem() instanceof ArmorItem) {
                if (armorStack.getItem() instanceof DyeableLeatherItem leatherItem) {
                    int color = leatherItem.getColor(armorStack);
                    return color;
                }
            }
        }
        return null;
    }
    public static boolean shouldSkipTarget(Player self, Player target) {
        Teams teamsModule = getInstance();
        if (teamsModule == null || !teamsModule.isEnabled()) {
            return false;
        }
        return isTeammate(self, target);
    }
}