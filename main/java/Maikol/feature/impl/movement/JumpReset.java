package Maikol.feature.impl.movement;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public class JumpReset extends Module {

    private static final Minecraft mc = Minecraft.getInstance();
    private final Value<Boolean> Debug = new Value<>("Debug", false);

    public JumpReset() {
        super("JumpReset");
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

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!enabled) return;
        if (mc.player == null) return;
        if (mc.player.hurtTime == 9) {
            if (mc.player.isInFluidType()) return;
            if (mc.player.onGround()) {
                mc.player.setJumping(true);
                if (Debug.getValue())
                    mc.player.sendSystemMessage(Component.literal("Jump"));
            }
        }
    }
}
