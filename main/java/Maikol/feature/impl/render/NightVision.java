package Maikol.feature.impl.render;


import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public class NightVision extends Module {

    private static final Minecraft mc = Minecraft.getInstance();
    public NightVision() {
        super("NightVision");
        category = Category.render;
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
    public void onClientTick(TickEvent.ClientTickEvent event){
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.level == null || mc.player == null) return;
        if (mc.player.tickCount % 20 == 0) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 60, 0));
        }
    }
}