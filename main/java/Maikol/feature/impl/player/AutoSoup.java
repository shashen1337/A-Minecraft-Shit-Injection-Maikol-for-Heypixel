package Maikol.feature.impl.player;


import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public class AutoSoup extends Module {
    private final Minecraft mc = Minecraft.getInstance();
    private boolean eating = false;
    private int eatDelay = 0;
    public AutoSoup() {
        super("AutoSoup");
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
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (eating) {
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty() || stack.getItem() != Items.MUSHROOM_STEW) {
                mc.options.keyUse.setDown(false);
                player.getInventory().selected = 0;
                eating = false;
            }
            return;
        }
        /*if (eatDelay > 0) {
            eatDelay--;
            return;
        }*/
        if (player.getHealth() < 14f) {
            int hotbarIndex = findHotbarMushroomStew(player);
            if (hotbarIndex != -1) {
                if (player.getInventory().selected != hotbarIndex) {
                    player.getInventory().selected = hotbarIndex;
                    eatDelay = 2;
                } else {
                    mc.options.keyUse.setDown(true);
                    eating = true;
                }
            }
        }
    }

    private int findHotbarMushroomStew(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.MUSHROOM_STEW) return i;
        }
        return -1;
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            onTick();
        }
    }
}
