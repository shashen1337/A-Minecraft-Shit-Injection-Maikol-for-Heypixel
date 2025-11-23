package Maikol.feature.impl.render;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Value;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import org.joml.Matrix4f;

public class ESP extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final Value<Boolean> showBox = new Value<>("Box", true);
    private final Value<Boolean> showOutline = new Value<>("Outline", true);
    private final Value<Float> boxAlpha = new Value<>("BoxAlpha", 0.3F);
    private final Value<Float> outlineWidth = new Value<>("OutlineWidth", 2.0F);

    public ESP() {
        super("ESP");
        category = Category.render;
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                RenderLevelStageEvent.class,
                this::onRenderWorld
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    public enum GreenStyle {
        BRIGHT(0.0f, 1.0f, 0.0f),
        NEON(0.2f, 1.0f, 0.2f),
        LIME(0.5f, 1.0f, 0.0f);

        public final float red;
        public final float green;
        public final float blue;

        GreenStyle(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    private GreenStyle currentGreenStyle = GreenStyle.BRIGHT;

    public void onRenderWorld(RenderLevelStageEvent event) {
        if (mc.level == null || mc.player == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!enabled) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        try {
            if (showBox.getValue()) {
                RenderSystem.getModelViewStack().pushPose();
                RenderSystem.depthMask(false);
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);

                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof Player && entity != mc.player) {
                        renderFilledBox(poseStack, entity, event.getPartialTick(),
                                currentGreenStyle.red, currentGreenStyle.green, currentGreenStyle.blue, boxAlpha.getValue());
                    } else if (entity instanceof ItemEntity) {
                        renderFilledBox(poseStack, entity, event.getPartialTick(),
                                1.0F, 0.0F, 0.0F, boxAlpha.getValue());
                    }
                }

                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                RenderSystem.getModelViewStack().popPose();
                RenderSystem.applyModelViewMatrix();
            }
            if (showOutline.getValue()) {
                RenderSystem.getModelViewStack().pushPose();
                RenderSystem.depthMask(false);
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                RenderSystem.lineWidth(outlineWidth.getValue());

                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof Player && entity != mc.player) {
                        renderOutlineBox(poseStack, entity, event.getPartialTick(),
                                currentGreenStyle.red, currentGreenStyle.green, currentGreenStyle.blue, 1.0F);
                    } else if (entity instanceof ItemEntity) {
                        renderOutlineBox(poseStack, entity, event.getPartialTick(),
                                1.0F, 0.0F, 0.0F, 1.0F);
                    }
                }

                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.getModelViewStack().popPose();
                RenderSystem.applyModelViewMatrix();
            }
        } finally {
            poseStack.popPose();
        }
    }

    private void renderFilledBox(PoseStack poseStack, Entity entity, float partialTicks,
                                 float red, float green, float blue, float alpha) {
        double x = entity.xo + (entity.getX() - entity.xo) * partialTicks;
        double y = entity.yo + (entity.getY() - entity.yo) * partialTicks;
        double z = entity.zo + (entity.getZ() - entity.zo) * partialTicks;
        AABB bb = entity.getBoundingBox();
        double minX = bb.minX - entity.getX() + x;
        double minY = bb.minY - entity.getY() + y;
        double minZ = bb.minZ - entity.getZ() + z;
        double maxX = bb.maxX - entity.getX() + x;
        double maxY = bb.maxY - entity.getY() + y;
        double maxZ = bb.maxZ - entity.getZ() + z;

        drawFilledBoundingBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }

    private void renderOutlineBox(PoseStack poseStack, Entity entity, float partialTicks,
                                  float red, float green, float blue, float alpha) {
        double x = entity.xo + (entity.getX() - entity.xo) * partialTicks;
        double y = entity.yo + (entity.getY() - entity.yo) * partialTicks;
        double z = entity.zo + (entity.getZ() - entity.zo) * partialTicks;
        AABB bb = entity.getBoundingBox();
        double minX = bb.minX - entity.getX() + x;
        double minY = bb.minY - entity.getY() + y;
        double minZ = bb.minZ - entity.getZ() + z;
        double maxX = bb.maxX - entity.getX() + x;
        double maxY = bb.maxY - entity.getY() + y;
        double maxZ = bb.maxZ - entity.getZ() + z;

        drawOutlinedBoundingBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }

    private void drawFilledBoundingBox(PoseStack poseStack, double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ, float red, float green, float blue, float alpha) {
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = poseStack.last().pose();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        Tesselator.getInstance().end();
    }

    private void drawOutlinedBoundingBox(PoseStack poseStack, double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ, float red, float green, float blue, float alpha) {
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.setShaderColor(red, green, blue, alpha);
        Matrix4f matrix = poseStack.last().pose();
        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(red, green, blue, alpha).endVertex();
        Tesselator.getInstance().end();
    }
}