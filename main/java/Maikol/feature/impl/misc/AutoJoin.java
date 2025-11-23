package Maikol.feature.impl.misc;

import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

public class AutoJoin extends Module {

    private final Minecraft mc;

    public AutoJoin() {
        super("AutoPlay");
        this.mc = Minecraft.getInstance();
        category = Category.misc;
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                InputEvent.Key.class,
                this::onKeyInput
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    public void onKeyInput(InputEvent.Key event) {
            if (!enabled) return;
            if (mc.screen != null) {
                return;
            }
            LocalPlayer player = mc.player;
            if (player != null) {
                player.connection.sendCommand("play swrsolo");
                player.sendSystemMessage(Component.literal("§a已跨服至空岛战争"));
                enabled = false;
            }
    }
}
