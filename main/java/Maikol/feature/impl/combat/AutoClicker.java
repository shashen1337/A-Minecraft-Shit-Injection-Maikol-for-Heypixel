package Maikol.feature.impl.combat;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;

public class AutoClicker extends Module {

    private static final Minecraft mc = Minecraft.getInstance();

    private static Field missTimeField;
    private static int clickCooldown = 0;
    private static long lastClickTime = 0;

    public AutoClicker() {
        super("AutoClicker");
        category = Category.combat;
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                TickEvent.ClientTickEvent.class,
                this::onClientTick
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    static {
        try {
            missTimeField = Minecraft.class.getDeclaredField("f_91078_");
            missTimeField.setAccessible(true);
        } catch (Exception ignored) {}
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.player == null || mc.level == null) return;
        if (!enabled) return;
        if (!mc.options.keyAttack.isDown()) {
            clickCooldown = 0;
            return;
        }
        int cps = Command.getAutoClickerCPS();
        long clickInterval = 1000L / cps;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < clickInterval) {
            return;
        }
        try {
            if (missTimeField != null) {
                missTimeField.setInt(mc, 0);
            }
        } catch (Exception ignored) {}

        if (!mc.gameMode.isDestroying()) {
            KeyMapping.click(mc.options.keyAttack.getKey());
            lastClickTime = currentTime;
        }
    }
}