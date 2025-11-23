package Maikol.feature.impl.combat;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Value;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.common.ForgeMod;

public class Reach extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private final Value<Double> Range = new Value<>("Range", 3.1);

    public Reach() {
        super("Reach");
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
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.player == null) return;
        if (!enabled) return;
        try {
            AttributeInstance reachAttribute = mc.player.getAttribute(ForgeMod.ENTITY_REACH.get());
            if (reachAttribute != null) {
                reachAttribute.setBaseValue(Range.getValue());
            }
        } catch (Exception ignored) {}
    }
}
