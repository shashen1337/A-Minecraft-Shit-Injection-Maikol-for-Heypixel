package Maikol.feature.impl.player;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;

public class FastPlace extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private final Value<Integer> CPS = new Value<>("CPS", 20);

    private long lastPlaceTime = 0L;
    private long placeDelay = 50L;

    private Field rightClickDelayField = null;

    public FastPlace() {
        super("FastPlace");
        category = Category.player;
        initReflection();
    }

    private void initReflection() {
        try {
            try {
                rightClickDelayField = Minecraft.class.getDeclaredField("f_91011_");
            } catch (NoSuchFieldException ignored) {
            }
            rightClickDelayField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                TickEvent.ClientTickEvent.class,
                this::onClientTick
        );
        updatePlaceDelay();
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        setRightClickDelay(4);
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!enabled) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.screen != null) return;
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.isEmpty() || !(mainHandItem.getItem() instanceof BlockItem)) {
            return;
        }
        var hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        updatePlaceDelay();
        if (mc.options.keyUse.isDown()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPlaceTime >= placeDelay) {
                setRightClickDelay(0);
                lastPlaceTime = currentTime;
            }
        } else {
            lastPlaceTime = System.currentTimeMillis();
        }
    }

    private void setRightClickDelay(int value) {
        if (rightClickDelayField == null) return;
        try {
            rightClickDelayField.setInt(mc, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePlaceDelay() {
        int cps = CPS.getValue();
        if (cps <= 0) cps = 1;
        if (cps > 50) cps = 50;

        placeDelay = 1000L / cps;
    }
}