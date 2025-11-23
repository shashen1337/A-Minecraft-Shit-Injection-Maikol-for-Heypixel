package Maikol.feature.impl.movement;

import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public class Eagle extends Module {

    private final Minecraft mc;
    private boolean eagleControllingShift = false;

    public Eagle() {
        super("Eagle");
        this.mc = Minecraft.getInstance();
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
        if (eagleControllingShift) {
            mc.options.keyShift.setDown(false);
            eagleControllingShift = false;
        }
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (!enabled) return;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.player.getXRot() > 60 && !mc.player.input.up) {
            BlockPos posUnder = BlockPos.containing(
                    mc.player.getX(),
                    mc.player.getY() - 1,
                    mc.player.getZ()
            );
            BlockState stateUnder = mc.level.getBlockState(posUnder);
            boolean unsafe = stateUnder.isAir() || stateUnder.canBeReplaced();
            if (unsafe) {
                mc.options.keyShift.setDown(true);
                eagleControllingShift = true;
                return;
            }
        }
        if (eagleControllingShift) {
            mc.options.keyShift.setDown(false);
            eagleControllingShift = false;
        }
    }
}