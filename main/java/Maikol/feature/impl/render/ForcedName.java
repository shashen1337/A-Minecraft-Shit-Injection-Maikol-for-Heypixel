package Maikol.feature.impl.render;

import Maikol.feature.Category;
import Maikol.feature.Module;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

public class ForcedName extends Module {
    public ForcedName() {
        super("ForcedName");
        category = Category.render;
    }
    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                RenderLivingEvent.Post.class,
                this::onRenderLiving
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    public void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double distanceSq = mc.player.distanceToSqr(player);
        if (distanceSq > 4096.0D) return;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        Font font = mc.font;
        String name = player.getGameProfile().getName();
        if (KillerDetection.getInstance().isKiller(player)) {
            name = "§c[杀手]§f " + name;
        }
        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() + 0.5, 0.0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        int width = font.width(name);
        font.drawInBatch(name,
                -width / 2f,
                0,
                0xFFFFFF,
                false,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.NORMAL,
                0,
                0xF000F0
        );
        poseStack.popPose();
    }
}
