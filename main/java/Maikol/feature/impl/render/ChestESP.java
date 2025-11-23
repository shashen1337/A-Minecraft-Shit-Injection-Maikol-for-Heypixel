package Maikol.feature.impl.render;

import Maikol.feature.Category;
import Maikol.feature.Module;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class ChestESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<BlockPos> openedChests = new HashSet<>();
    private Field openCountField = null;
    private boolean reflectionFailed = false;

    private Level lastWorld = null;

    public ChestESP() {
        super("ChestESP");
        initReflection();
        category = Category.render;
    }

    private void initReflection() {
        try {
            try {
                openCountField = ChestBlockEntity.class.getDeclaredField("openCount");
                openCountField.setAccessible(true);
            } catch (NoSuchFieldException e1) {
                try {
                    openCountField = ChestBlockEntity.class.getDeclaredField("f_59646_");
                    openCountField.setAccessible(true);
                } catch (NoSuchFieldException e2) {
                    reflectionFailed = true;
                }
            }
        } catch (Exception e) {
            reflectionFailed = true;
        }
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                RenderLevelStageEvent.class,
                this::onRenderWorld
        );
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
        openedChests.clear();
        lastWorld = null;
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        // 检测世界变化
        if (mc.level != lastWorld) {
            // 世界改变了，清除所有已开箱子记录
            openedChests.clear();
            lastWorld = mc.level;
        }
    }

    public void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!enabled) return;
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        int renderDistance = mc.options.renderDistance().get();
        BlockPos playerPos = mc.player.blockPosition();

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                int chunkX = (playerPos.getX() >> 4) + x;
                int chunkZ = (playerPos.getZ() >> 4) + z;

                if (!mc.level.hasChunk(chunkX, chunkZ)) continue;

                var chunk = mc.level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ChestBlockEntity chest)) continue;

                    BlockPos pos = chest.getBlockPos();
                    boolean isOpened = isChestOpened(chest);

                    if (isOpened && !openedChests.contains(pos)) {
                        openedChests.add(pos);
                    }

                    float[] color = openedChests.contains(pos) ?
                            new float[]{1.0f, 0.4f, 0.4f, 0.5f} :
                            new float[]{0.4f, 1.0f, 0.4f, 0.5f};
                    drawChestBox(poseStack, buffer, pos, camPos, color);
                }
            }
        }

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private void drawChestBox(PoseStack poseStack, BufferBuilder buffer, BlockPos pos, Vec3 camPos, float[] color) {
        double x = pos.getX() - camPos.x;
        double y = pos.getY() - camPos.y;
        double z = pos.getZ() - camPos.z;

        AABB box = new AABB(x - 0.002, y - 0.002, z - 0.002,
                x + 1.002, y + 1.002, z + 1.002);

        Matrix4f matrix = poseStack.last().pose();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawFilledBox(buffer, matrix, box, color[0], color[1], color[2], color[3] * 0.3f);
        Tesselator.getInstance().end();

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        drawBoxOutline(buffer, matrix, box, color[0], color[1], color[2], color[3] + 0.5f);
        Tesselator.getInstance().end();
    }

    private void drawFilledBox(BufferBuilder buffer, Matrix4f matrix, AABB box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
    }

    private void drawBoxOutline(BufferBuilder buffer, Matrix4f matrix, AABB box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
    }

    private boolean isChestOpened(ChestBlockEntity chest) {
        if (!reflectionFailed && openCountField != null) {
            try {
                int count = openCountField.getInt(chest);
                return count > 0;
            } catch (Exception e) {
                reflectionFailed = true;
            }
        }
        return chest.getOpenNess(1.0f) > 0.0f;
    }
}