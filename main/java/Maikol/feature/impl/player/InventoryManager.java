package Maikol.feature.impl.player;

import Maikol.feature.Category;
import Maikol.feature.Module;
import Maikol.feature.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.*;

public class InventoryManager extends Module {

    private final Minecraft mc;
    private boolean hasProcessedThisOpen = false;
    private final Value<Long> equipDelay = new Value<>("equipDelay", 100L);
    private final Value<Long> dropDelay = new Value<>("dropDelay", 250L);

    private Queue<InventoryAction> actionQueue = new LinkedList<>();

    private enum ActionType {
        EQUIP_ARMOR, DROP, MOVE_TO_OFFHAND, UNEQUIP_ARMOR
    }

    private static class InventoryAction {
        ActionType type;
        int slot;
        long executeTime;
        EquipmentSlot equipmentSlot;

        InventoryAction(ActionType type, int slot, long delay) {
            this(type, slot, delay, null);
        }

        InventoryAction(ActionType type, int slot, long delay, EquipmentSlot equipmentSlot) {
            this.type = type;
            this.slot = slot;
            this.executeTime = System.currentTimeMillis() + delay;
            this.equipmentSlot = equipmentSlot;
        }
    }

    public InventoryManager() {
        super("InventoryManager");
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

        if (mc.player == null) {
            return;
        }
        if (!enabled) return;

        Screen currentScreen = mc.screen;

        if (currentScreen instanceof InventoryScreen) {
            if (!hasProcessedThisOpen) {
                hasProcessedThisOpen = true;
                analyzeInventory();
            }

            processActionQueue();
        } else {
            hasProcessedThisOpen = false;
            actionQueue.clear();
        }
    }

    private void analyzeInventory() {
        actionQueue.clear();
        Inventory inventory = mc.player.getInventory();

        long currentDelay = 0;
        int bestHelmetSlot = findSlot(inventory, EquipmentSlot.HEAD);
        int bestChestplateSlot = findSlot(inventory, EquipmentSlot.CHEST);
        int bestLeggingsSlot = findSlot(inventory, EquipmentSlot.LEGS);
        int bestBootsSlot = findSlot(inventory, EquipmentSlot.FEET);
        int bestSwordSlot = findBestSwordSlot(inventory);
        int bestPickaxeSlot = findBestToolSlot(inventory, PickaxeItem.class);
        int bestAxeSlot = findBestNonGoldenAxeSlot(inventory);
        int bestBowSlot = findBestBowSlot(inventory);

        Set<Integer> slotsToKeep = new HashSet<>();
        if (bestHelmetSlot != -1) slotsToKeep.add(bestHelmetSlot);
        if (bestChestplateSlot != -1) slotsToKeep.add(bestChestplateSlot);
        if (bestLeggingsSlot != -1) slotsToKeep.add(bestLeggingsSlot);
        if (bestBootsSlot != -1) slotsToKeep.add(bestBootsSlot);
        if (bestSwordSlot != -1) slotsToKeep.add(bestSwordSlot);
        if (bestPickaxeSlot != -1) slotsToKeep.add(bestPickaxeSlot);
        if (bestAxeSlot != -1) slotsToKeep.add(bestAxeSlot);
        if (bestBowSlot != -1) slotsToKeep.add(bestBowSlot);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof AxeItem) {
                AxeItem axe = (AxeItem) stack.getItem();
                if (axe.getTier() == Tiers.GOLD) {
                    slotsToKeep.add(i);
                }
            }
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (shouldAlwaysKeep(stack)) {
                slotsToKeep.add(i);
            }
        }
        if (bestHelmetSlot != -1) {
            ItemStack current = mc.player.getItemBySlot(EquipmentSlot.HEAD);
            ItemStack best = inventory.getItem(bestHelmetSlot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, best)) {
                actionQueue.add(new InventoryAction(ActionType.UNEQUIP_ARMOR, -1, currentDelay, EquipmentSlot.HEAD));
                currentDelay += equipDelay.getValue();
            }
            if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, best)) {
                actionQueue.add(new InventoryAction(ActionType.EQUIP_ARMOR, bestHelmetSlot, currentDelay));
                currentDelay += equipDelay.getValue();
            }
        }

        if (bestChestplateSlot != -1) {
            ItemStack current = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            ItemStack best = inventory.getItem(bestChestplateSlot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, best)) {
                actionQueue.add(new InventoryAction(ActionType.UNEQUIP_ARMOR, -1, currentDelay, EquipmentSlot.CHEST));
                currentDelay += equipDelay.getValue();
            }
            if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, best)) {
                actionQueue.add(new InventoryAction(ActionType.EQUIP_ARMOR, bestChestplateSlot, currentDelay));
                currentDelay += equipDelay.getValue();
            }
        }

        if (bestLeggingsSlot != -1) {
            ItemStack current = mc.player.getItemBySlot(EquipmentSlot.LEGS);
            ItemStack best = inventory.getItem(bestLeggingsSlot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, best)) {
                actionQueue.add(new InventoryAction(ActionType.UNEQUIP_ARMOR, -1, currentDelay, EquipmentSlot.LEGS));
                currentDelay += equipDelay.getValue();
            }
            if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, best)) {
                actionQueue.add(new InventoryAction(ActionType.EQUIP_ARMOR, bestLeggingsSlot, currentDelay));
                currentDelay += equipDelay.getValue();
            }
        }

        if (bestBootsSlot != -1) {
            ItemStack current = mc.player.getItemBySlot(EquipmentSlot.FEET);
            ItemStack best = inventory.getItem(bestBootsSlot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, best)) {
                actionQueue.add(new InventoryAction(ActionType.UNEQUIP_ARMOR, -1, currentDelay, EquipmentSlot.FEET));
                currentDelay += equipDelay.getValue();
            }
            if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, best)) {
                actionQueue.add(new InventoryAction(ActionType.EQUIP_ARMOR, bestBootsSlot, currentDelay));
                currentDelay += equipDelay.getValue();
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (!(offhand.getItem() instanceof SnowballItem || offhand.getItem() instanceof EggItem)) {
            int snowballSlot = findFirstItemSlot(inventory, Items.SNOWBALL);
            if (snowballSlot != -1) {
                slotsToKeep.add(snowballSlot);
                actionQueue.add(new InventoryAction(ActionType.MOVE_TO_OFFHAND, snowballSlot, currentDelay));
                currentDelay += equipDelay.getValue();
            } else {
                int eggSlot = findFirstItemSlot(inventory, Items.EGG);
                if (eggSlot != -1) {
                    slotsToKeep.add(eggSlot);
                    actionQueue.add(new InventoryAction(ActionType.MOVE_TO_OFFHAND, eggSlot, currentDelay));
                    currentDelay += equipDelay.getValue();
                }
            }
        }
        List<Integer> rodSlots = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof FishingRodItem) {
                rodSlots.add(i);
            }
        }
        if (!rodSlots.isEmpty()) {
            slotsToKeep.add(rodSlots.get(0));
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (slotsToKeep.contains(i)) {
                continue;
            }

            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && shouldDropItem(stack)) {
                actionQueue.add(new InventoryAction(ActionType.DROP, i, currentDelay));
                currentDelay += dropDelay.getValue();
            }
        }
    }

    private boolean shouldAlwaysKeep(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE ||
                item == Items.TOTEM_OF_UNDYING || item == Items.END_CRYSTAL) {
            return true;
        }
        if (item instanceof SnowballItem || item instanceof EggItem) {
            return true;
        }
        if (item instanceof BlockItem) {
            Block block = ((BlockItem) item).getBlock();
            if (block == Blocks.STONE || isWoodenPlanks(block)) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldDropItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (shouldAlwaysKeep(stack)) {
            return false;
        }
        if (item == Items.BOOK || item == Items.WRITABLE_BOOK || item == Items.WRITTEN_BOOK ||
                item == Items.EXPERIENCE_BOTTLE || item == Items.ENCHANTED_BOOK) {
            return true;
        }
        if (item instanceof BlockItem) {
            return true;
        }
        if (item instanceof ArmorItem) {
            return true;
        }
        if (item instanceof SwordItem || item instanceof PickaxeItem ||
                item instanceof BowItem || item instanceof FishingRodItem) {
            return true;
        }
        if (item instanceof AxeItem) {
            AxeItem axe = (AxeItem) item;
            return axe.getTier() != Tiers.GOLD;
        }
        return false;
    }

    private boolean isWoodenPlanks(Block block) {
        return block == Blocks.OAK_PLANKS || block == Blocks.SPRUCE_PLANKS ||
                block == Blocks.BIRCH_PLANKS || block == Blocks.JUNGLE_PLANKS ||
                block == Blocks.ACACIA_PLANKS || block == Blocks.DARK_OAK_PLANKS ||
                block == Blocks.MANGROVE_PLANKS || block == Blocks.CHERRY_PLANKS ||
                block == Blocks.BAMBOO_PLANKS || block == Blocks.CRIMSON_PLANKS ||
                block == Blocks.WARPED_PLANKS;
    }

    private int findSlot(Inventory inventory, EquipmentSlot slotType) {
        int bestSlot = -1;
        double bestScore = Double.MIN_VALUE;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isArmorForSlot(stack, slotType)) {
                double score = getArmorScore(stack);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private boolean isArmorForSlot(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
            return false;
        }
        ArmorItem armor = (ArmorItem) stack.getItem();
        return armor.getEquipmentSlot() == slot;
    }

    private double getArmorScore(ItemStack stack) {
        ArmorItem armor = (ArmorItem) stack.getItem();
        ArmorMaterial material = armor.getMaterial();
        double materialBonus = 0;
        if (material == ArmorMaterials.NETHERITE) {
            materialBonus = 1000;
        } else if (material == ArmorMaterials.DIAMOND) {
            materialBonus = 800;
        } else if (material == ArmorMaterials.IRON) {
            materialBonus = 600;
        } else if (material == ArmorMaterials.CHAIN) {
            materialBonus = 400;
        } else if (material == ArmorMaterials.GOLD) {
            materialBonus = 200;
        } else if (material == ArmorMaterials.LEATHER) {
            materialBonus = 100;
        }

        double baseDefense = getBaseDefense(stack);
        double protectionBonus = getEnchantmentLevel(stack, Enchantments.ALL_DAMAGE_PROTECTION) * 0.75;
        return materialBonus + baseDefense + protectionBonus;
    }

    private double getBaseDefense(ItemStack stack) {
        Attribute defenseAttribute = Attributes.ARMOR;
        double baseDefense = 0;

        EquipmentSlot equipmentSlot = stack.getItem().getEquipmentSlot(stack);
        if (equipmentSlot != null) {
            for (AttributeModifier modifier : stack.getAttributeModifiers(equipmentSlot).get(defenseAttribute)) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                    baseDefense += modifier.getAmount();
                }
            }
        }

        return baseDefense;
    }

    private int findBestSwordSlot(Inventory inventory) {
        int bestSlot = -1;
        double bestScore = Double.MIN_VALUE;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof SwordItem) {
                double baseDamage = getBaseDamage(stack);
                double sharpnessBonus = getEnchantmentLevel(stack, Enchantments.SHARPNESS) * 1.25;
                double score = baseDamage + sharpnessBonus;

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private int findBestToolSlot(Inventory inventory, Class<? extends Item> toolClass) {
        int bestSlot = -1;
        double bestScore = Double.MIN_VALUE;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && toolClass.isInstance(stack.getItem())) {
                double baseDamage = getBaseDamage(stack);
                double efficiencyBonus = getEnchantmentLevel(stack, Enchantments.BLOCK_EFFICIENCY) * 1.5;
                double unbreakingBonus = getEnchantmentLevel(stack, Enchantments.UNBREAKING) * 0.5;
                double score = baseDamage + efficiencyBonus + unbreakingBonus;

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private int findBestNonGoldenAxeSlot(Inventory inventory) {
        int bestSlot = -1;
        double bestScore = Double.MIN_VALUE;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof AxeItem) {
                AxeItem axe = (AxeItem) stack.getItem();
                if (axe.getTier() == Tiers.GOLD) {
                    continue;
                }

                double baseDamage = getBaseDamage(stack);
                double efficiencyBonus = getEnchantmentLevel(stack, Enchantments.BLOCK_EFFICIENCY) * 1.5;
                double unbreakingBonus = getEnchantmentLevel(stack, Enchantments.UNBREAKING) * 0.5;
                double score = baseDamage + efficiencyBonus + unbreakingBonus;

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private int findBestBowSlot(Inventory inventory) {
        int bestSlot = -1;
        double bestScore = Double.MIN_VALUE;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BowItem) {
                double powerBonus = getEnchantmentLevel(stack, Enchantments.POWER_ARROWS) * 2.0;
                double punchBonus = getEnchantmentLevel(stack, Enchantments.PUNCH_ARROWS) * 1.5;
                double flameBonus = (getEnchantmentLevel(stack, Enchantments.FLAMING_ARROWS) > 0) ? 3.0 : 0;
                double unbreakingBonus = getEnchantmentLevel(stack, Enchantments.UNBREAKING) * 0.5;
                double score = powerBonus + punchBonus + flameBonus + unbreakingBonus;

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private double getBaseDamage(ItemStack stack) {
        Attribute attackDamageAttribute = Attributes.ATTACK_DAMAGE;
        double baseDamage = 0;

        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attackDamageAttribute)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                baseDamage += modifier.getAmount();
            }
        }

        return baseDamage;
    }

    private int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        return stack.getEnchantmentLevel(enchantment);
    }

    private int findFirstItemSlot(Inventory inventory, Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private void processActionQueue() {
        if (actionQueue.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        InventoryAction action = actionQueue.peek();

        if (currentTime >= action.executeTime) {
            actionQueue.poll();
            executeAction(action);
        }
    }

    private void executeAction(InventoryAction action) {
        if (mc.player == null) return;

        int containerSlot = action.slot;
        if (action.slot >= 0 && action.slot < 9) {
            containerSlot = action.slot + 36;
        }

        switch (action.type) {
            case UNEQUIP_ARMOR:
                int armorSlot = getArmorSlotId(action.equipmentSlot);
                mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId,
                        armorSlot,
                        0,
                        ClickType.QUICK_MOVE,
                        mc.player
                );
                break;

            case EQUIP_ARMOR:
                mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId,
                        containerSlot,
                        0,
                        ClickType.QUICK_MOVE,
                        mc.player
                );
                break;

            case DROP:
                mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId,
                        containerSlot,
                        1,
                        ClickType.THROW,
                        mc.player
                );
                break;

            case MOVE_TO_OFFHAND:
                mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId,
                        containerSlot,
                        40,
                        ClickType.SWAP,
                        mc.player
                );
                break;
        }
    }

    private int getArmorSlotId(EquipmentSlot slot) {
        switch (slot) {
            case HEAD: return 5;
            case CHEST: return 6;
            case LEGS: return 7;
            case FEET: return 8;
            default: return -1;
        }
    }
}
