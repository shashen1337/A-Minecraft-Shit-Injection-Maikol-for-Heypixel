package Maikol.feature.impl.player;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public class ChestStealer extends Module {

    private final Minecraft mc;
    private long lastStealTime = 0;
    private final Value<Long> Delay = new Value<>("Delay", 150L);
    private boolean isProcessing = false;

    public ChestStealer() {
        super("ChestStealer");
        this.mc = Minecraft.getInstance();
        category = Category.player;
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

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!enabled) return;

        if (mc.player == null) {
            return;
        }
        if (isProcessing) {
            return;
        }

        Screen currentScreen = mc.screen;
        if (!(currentScreen instanceof AbstractContainerScreen)) {
            return;
        }

        AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) currentScreen;
        AbstractContainerMenu container = containerScreen.getMenu();

        if (!isTargetContainer(container)) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStealTime < Delay.getValue()) {
            return;
        }
        isProcessing = true;

        try {
            boolean foundItem = stealNextItem(container);
            if (foundItem) {
                lastStealTime = currentTime;
            } else {
                mc.player.closeContainer();
            }
        } finally {
            isProcessing = false;
        }
    }

    private boolean isTargetContainer(AbstractContainerMenu container) {
        return container instanceof ChestMenu ||
                container instanceof FurnaceMenu ||
                container instanceof BrewingStandMenu;
    }

    private boolean stealNextItem(AbstractContainerMenu container) {
        int playerInventoryStart = getPlayerInventoryStart(container);
        for (int i = 0; i < playerInventoryStart; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                mc.gameMode.handleInventoryMouseClick(
                        container.containerId,
                        i,
                        0,
                        ClickType.QUICK_MOVE,
                        mc.player
                );
                return true;
            }
        }
        return false;
    }

    private int getPlayerInventoryStart(AbstractContainerMenu container) {
        if (container instanceof ChestMenu) {
            ChestMenu chestMenu = (ChestMenu) container;
            return chestMenu.getContainer().getContainerSize();
        } else if (container instanceof FurnaceMenu) {
            return 3;
        } else if (container instanceof BrewingStandMenu) {
            return 5;
        }
        return 0;
    }
}