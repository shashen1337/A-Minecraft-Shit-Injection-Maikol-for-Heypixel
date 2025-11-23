package Maikol.feature;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.eventbus.api.EventPriority;

public class PacketInterceptor {
    private boolean injected = false;
    public PacketInterceptor() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                TickEvent.ClientTickEvent.class,
                this::onClientTick
        );
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !injected) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                injectPacketHandler(mc.getConnection().getConnection());
            }
        }
    }
    private void injectPacketHandler(Connection connection) {
        try {
            if (connection.channel().pipeline().get("packet_blocker") == null) {
                connection.channel().pipeline().addBefore(
                        "packet_handler",
                        "packet_blocker",
                        new PacketBlockerHandler()
                );
                injected = true;
            }
        } catch (Exception e) {
        }
    }
    private static class PacketBlockerHandler extends ChannelDuplexHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ServerboundCustomPayloadPacket) {
                ServerboundCustomPayloadPacket packet = (ServerboundCustomPayloadPacket) msg;
                promise.setSuccess();
                return;
            }
            super.write(ctx, msg, promise);
        }
    }
}