package Maikol.feature.impl.player;

import Maikol.feature.Category;
import Maikol.feature.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public class AutoTool extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private int originalSlot = -1;
    private boolean isMining = false;

    public AutoTool() {
        super("AutoTool");
        category = Category.player;
    }

    @Override
    protected void onEnable() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                PlayerInteractEvent.LeftClickBlock.class,
                this::onLeftClickBlock
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                BlockEvent.BreakEvent.class,
                this::onBlockBreak
        );
    }

    @Override
    protected void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        restoreOriginalSlot();
    }

    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!enabled) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        BlockPos pos = event.getPos();
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.AIR || block == Blocks.WATER || block == Blocks.LAVA) {
            return;
        }
        if (!isMining) {
            originalSlot = player.getInventory().selected;
            isMining = true;
        }
        int bestSlot = findBestTool(state);
        if (bestSlot != -1 && bestSlot != player.getInventory().selected) {
            player.getInventory().selected = bestSlot;
        }
    }

    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!enabled) return;
        restoreOriginalSlot();
    }

    private void restoreOriginalSlot() {
        if (isMining && originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().selected = originalSlot;
            originalSlot = -1;
            isMining = false;
        }
    }

    private int findBestTool(BlockState state) {
        LocalPlayer player = mc.player;
        if (player == null) return -1;

        Block block = state.getBlock();
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        if (block == Blocks.COBWEB) {
            int shearSlot = findItemInHotbar(Items.SHEARS);
            if (shearSlot != -1) return shearSlot;

            int swordSlot = findSwordInHotbar();
            if (swordSlot != -1) return swordSlot;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            if (isWoodLike(block) && isGoldenAxeWithSharpness(stack)) {
                continue;
            }

            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean isWoodLike(Block block) {
        String blockName = block.toString().toLowerCase();
        return blockName.contains("log") || blockName.contains("wood") ||
                blockName.contains("planks") || block == Blocks.CRAFTING_TABLE ||
                block == Blocks.BOOKSHELF || block == Blocks.CHEST;
    }

    private boolean isGoldenAxeWithSharpness(ItemStack stack) {
        if (stack.getItem() != Items.GOLDEN_AXE) return false;

        int sharpnessLevel = stack.getEnchantmentLevel(Enchantments.SHARPNESS);
        return sharpnessLevel >= 3;
    }

    private int findItemInHotbar(net.minecraft.world.item.Item item) {
        LocalPlayer player = mc.player;
        if (player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }
    private int findSwordInHotbar() {
        LocalPlayer player = mc.player;
        if (player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.WOODEN_SWORD || stack.getItem() == Items.STONE_SWORD ||
                    stack.getItem() == Items.IRON_SWORD || stack.getItem() == Items.GOLDEN_SWORD ||
                    stack.getItem() == Items.DIAMOND_SWORD || stack.getItem() == Items.NETHERITE_SWORD) {
                return i;
            }
        }
        return -1;
    }
}