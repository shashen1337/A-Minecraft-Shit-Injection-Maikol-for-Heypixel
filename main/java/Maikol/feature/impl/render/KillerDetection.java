package Maikol.feature.impl.render;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Value;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class KillerDetection extends Module {
    private static final KillerDetection INSTANCE = new KillerDetection();
    public static KillerDetection getInstance() { return INSTANCE; }
    private final Set<UUID> detectiveList = new HashSet<>();
    private final Set<UUID> killerList = new HashSet<>();
    private boolean displayEnabled = true;
    private final Value<Boolean> Debug = new Value<>("Debug", false);

    public KillerDetection() {
        super("ShowKiller");
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
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                LevelEvent.Load.class,
                this::onWorldLoad
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                RenderLivingEvent.Post.class,
                this::onRenderLiving
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                RenderGuiOverlayEvent.Pre.class,
                this::onRenderOverlay
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        clearKillers();
    }

    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            clearKillers();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (Debug.getValue())
                    mc.player.displayClientMessage(
                            Component.literal("杀手、侦探名单已自动清空"), true);
            }
        }
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!enabled) return;
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player.getMainHandItem().getItem() == Items.DIAMOND_SWORD) {
                killerList.add(player.getUUID());
            }
            if (player.getMainHandItem().getItem() == Items.BOW) {
                detectiveList.add(player.getUUID());
            }
        }
    }

    public void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!displayEnabled) return;
        if (!(event.getEntity() instanceof Player player)) return;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String text = null;
        if (detectiveList.contains(player.getUUID())) {
            text = "§b[侦探]§f " + player.getName().getString();
        } else if (killerList.contains(player.getUUID())) {
            text = "§c[杀手]§f " + player.getName().getString();
        }
        if (text != null) {
            int width = font.width(text);
            poseStack.pushPose();
            poseStack.translate(0.0, player.getBbHeight() + 0.5, 0.0);
            poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            font.drawInBatch(text, -width / 2.0F, 0, 0xFFFFFF, false,
                    poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            poseStack.popPose();
        }
    }

    public void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            Component baseName = Component.literal(info.getProfile().getName());
            if (detectiveList.contains(id)) {
                info.setTabListDisplayName(Component.literal("§b[侦探]§f ").append(baseName));
            } else if (killerList.contains(id)) {
                info.setTabListDisplayName(Component.literal("§c[杀手]§f ").append(baseName));
            } else {
                info.setTabListDisplayName(null);
            }
        }
    }

    public void clearKillers() {
        killerList.clear();
        detectiveList.clear();
    }

    public boolean isKiller(Player player) {
        return killerList.contains(player.getUUID());
    }
}