package Maikol.feature.impl.movement;

import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint");
        category = Category.move;
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

    private final Minecraft mc = Minecraft.getInstance();

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.player == null) return;
        if (!enabled) return;
        mc.options.keySprint.setDown(true);
    }
}
