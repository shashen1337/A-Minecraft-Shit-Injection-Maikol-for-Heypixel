package Maikol.feature.impl.render;


import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

public class NoBadEffect extends Module {

    public NoBadEffect() {
        super("NoBadEffect");
        category = Category.render;

    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                ViewportEvent.RenderFog.class,
                this::onRenderFog
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                ViewportEvent.ComputeFogColor.class,
                this::onComputeFogColor
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    public void onRenderFog(ViewportEvent.RenderFog event) {
        if (!this.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasEffect(MobEffects.BLINDNESS)) {
            event.setCanceled(true);
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(9999.0F);
        }
    }

    public void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!this.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasEffect(MobEffects.BLINDNESS)) {
            event.setRed(1.0f);
            event.setGreen(1.0f);
            event.setBlue(1.0f);
        }
    }
}