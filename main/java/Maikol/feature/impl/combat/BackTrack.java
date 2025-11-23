package Maikol.feature.impl.combat;


import Maikol.feature.Category;
import Maikol.feature.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import Maikol.feature.Module;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
public class BackTrack extends Module {
    private  final Minecraft mc = Minecraft.getInstance();
    private final Value<Long> timeframe = new Value<>("Timeframe", 250L);
    private  final Queue<DelayedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private  volatile boolean injected = false;
    private  volatile Channel channelRef = null;
    private  volatile boolean throttlingActive = false;
    private  volatile boolean releasing = false;
    private  volatile long windowStart = 0L;
    private  volatile ChannelHandlerContext backtrackCtx = null;


    public BackTrack () {
        super("BackTrack");
        category = Category.combat;
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
                RenderGuiOverlayEvent.Post.class,
                this::onRenderProgressBar
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {

        if (event.phase != TickEvent.Phase.START) return;
        if (mc.level == null || mc.player == null) return;
        if (!enabled) return;


        tryInjectIfNeeded();
        boolean should = shouldThrottle();
        long now = System.currentTimeMillis();
        if (should) {
            if (!throttlingActive) {
                throttlingActive = true;
                windowStart = now;
                //if (mc.player != null)
                //mc.player.displayClientMessage(Component.literal("§aBackTrack: 节流开始"), false);
            }
            long elapsed = now - windowStart;
            if (elapsed >= timeframe.getValue()) {
                int before = packetQueue.size();
                releaseAllQueued();
                //if (mc.player != null)
                //mc.player.displayClientMessage(Component.literal("§aBackTrack释放: " + before + " 包"), false);
                windowStart = now;
            }
        } else {
            if (throttlingActive) {
                throttlingActive = false;
                int left = packetQueue.size();
                releaseAllQueued();
                //if (mc.player != null)
                //mc.player.displayClientMessage(Component.literal("§cBackTrack停止,释放剩余: " + left + " 包"), false);
            }
        }
    }
    private  void tryInjectIfNeeded() {
        if (injected) return;
        if (mc.getConnection() == null) return;
        Connection conn = mc.getConnection().getConnection();
        if (conn == null) return;

        try {
            Channel ch = conn.channel();
            if (ch == null) return;
            channelRef = ch;
            ChannelDuplexHandler handler = new ChannelDuplexHandler() {
                @Override
                public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                    backtrackCtx = ctx;
                    super.handlerAdded(ctx);
                }
                @Override
                public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                    if (backtrackCtx == ctx) backtrackCtx = null;
                    super.handlerRemoved(ctx);
                }
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (throttlingActive && !releasing && msg instanceof Packet<?>) {
                        packetQueue.add(new DelayedPacket((Packet<?>) msg, true));
                        return;
                    }
                    super.channelRead(ctx, msg);
                }
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    if (throttlingActive && !releasing && msg instanceof Packet<?>) {
                        packetQueue.add(new DelayedPacket((Packet<?>) msg, false));
                        return;
                    }
                    super.write(ctx, msg, promise);
                }
            };
            try {
                ch.pipeline().addBefore("packet_handler", "backtrack_handler", handler);
            } catch (Exception ex) {
                ch.pipeline().addLast("backtrack_handler", handler);
            }
            injected = true;
            //if (mc.player != null)
            //mc.player.displayClientMessage(Component.literal("§aBackTrack: handler注入成功"), false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private  void releaseAllQueued() {
        if (channelRef == null) {
            packetQueue.clear();
            return;
        }
        ChannelHandlerContext ctx = backtrackCtx;
        if (ctx == null) {
            releasing = true;
            try {
                DelayedPacket dp;
                while ((dp = packetQueue.poll()) != null) {
                    try {
                        if (dp.inbound) {
                            channelRef.pipeline().fireChannelRead(dp.packet);
                        } else {
                            channelRef.writeAndFlush(dp.packet);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                channelRef.flush();
            } finally {
                releasing = false;
            }
            return;
        }
        releasing = true;
        int released = 0;
        try {
            DelayedPacket dp;
            while ((dp = packetQueue.poll()) != null) {
                try {
                    if (dp.inbound) {
                        ctx.fireChannelRead(dp.packet);
                    } else {
                        ctx.write(dp.packet);
                    }
                    released++;
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            ctx.flush();
        } finally {
            releasing = false;
        }
    }
    private  boolean shouldThrottle() {
        if (mc.player == null || mc.level == null) return false;
        for (var entity : mc.level.players()) {
            if (entity != mc.player && entity.distanceTo(mc.player) <= 4.0) {
                return true;
            }
        }
        return false;
    }
    private  class DelayedPacket {
        final Packet<?> packet;
        final boolean inbound;

        DelayedPacket(Packet<?> packet, boolean inbound) {
            this.packet = packet;
            this.inbound = inbound;
        }
    }
    public void onRenderProgressBar(RenderGuiOverlayEvent.Post event) {
        if (!throttlingActive) return;

        var guiGraphics = event.getGuiGraphics();
        long now = System.currentTimeMillis();
        long elapsed = now - windowStart;
        long total = timeframe.getValue();
        float progress = Math.min(1.0f, (float) elapsed / total);

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int barWidth = 140;
        int barHeight = 6;
        int x = width / 2 - barWidth / 2;
        int y = height / 2 - 20;

        String text = "Tracking...";
        int textWidth = mc.font.width(text);
        int textX = width / 2 - textWidth / 2;
        int textY = y - 12;

        guiGraphics.drawString(mc.font, text, textX + 1, textY + 1, 0x80000000, false);
        guiGraphics.drawString(mc.font, text, textX, textY, 0xFFFFFFFF, false);

        guiGraphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xAA000000);
        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF2A2A2A);

        if (progress > 0) {
            int progressWidth = (int) (barWidth * progress);
            int color = getGradientColor(progress);
            guiGraphics.fill(x, y, x + progressWidth, y + barHeight, color);
            int highlightColor = lightenColor(color, 0.3f);
            guiGraphics.fill(x, y, x + progressWidth, y + 2, highlightColor);
        }
        guiGraphics.fill(x - 1, y - 1, x + barWidth + 1, y, 0xFF000000); // 上
        guiGraphics.fill(x - 1, y + barHeight, x + barWidth + 1, y + barHeight + 1, 0xFF000000); // 下
        guiGraphics.fill(x - 1, y, x, y + barHeight, 0xFF000000); // 左
        guiGraphics.fill(x + barWidth, y, x + barWidth + 1, y + barHeight, 0xFF000000); // 右
    }

    private int getGradientColor(float progress) {
        int r, g;
        if (progress < 0.5f) {
            r = (int) (progress * 2 * 255);
            g = 255;
        } else {
            r = 255;
            g = (int) ((1.0f - progress) * 2 * 255);
        }
        int b = 0;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int lightenColor(int color, float amount) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = Math.min(255, (int)(r + (255 - r) * amount));
        g = Math.min(255, (int)(g + (255 - g) * amount));
        b = Math.min(255, (int)(b + (255 - b) * amount));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    /*public  void onRenderProgressBar(RenderGuiOverlayEvent.Post event) {
        if (!throttlingActive) return;
        var guiGraphics = event.getGuiGraphics();
        long now = System.currentTimeMillis();
        long elapsed = now - windowStart;
        long total = timeframe.getValue();
        float progress = Math.min(1.0f, (float) elapsed / total);
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int barWidth = 120;
        int barHeight = 8;
        int x = width / 2 - barWidth / 2;
        int y = height / 2 - 20;
        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF555555);
        int color = getGradientColor(progress);
        int progressWidth = (int) (barWidth * progress);
        guiGraphics.fill(x, y, x + progressWidth, y + barHeight, color);
        guiGraphics.fill(x, y, x + barWidth, y + 1, 0xFF000000);
        guiGraphics.fill(x, y + barHeight - 1, x + barWidth, y + barHeight, 0xFF000000);
        guiGraphics.fill(x, y, x + 1, y + barHeight, 0xFF000000);
        guiGraphics.fill(x + barWidth - 1, y, x + barWidth, y + barHeight, 0xFF000000);
    }
    private  int getGradientColor(float progress) {
        int r, g;
        if (progress < 0.5f) {
            r = (int) (progress / 0.5f * 255);
            g = 255;
        } else {
            r = 255;
            g = (int) ((1.0f - progress) / 0.5f * 255);
        }
        int b = 0;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }*/
}
