package cn.summerrecall.replace_dig.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class ItemSwapHelper {

    private static class SwapState {
        final int toolSlotIndex;
        final int mainHandSlot;

        SwapState(int toolSlotIndex, int mainHandSlot) {
            this.toolSlotIndex = toolSlotIndex;
            this.mainHandSlot = mainHandSlot;
        }
    }

    private static final Map<Player, SwapState> pendingSwaps = Collections.synchronizedMap(new WeakHashMap<>());

    private static ItemStack getItemFromSlot(Player player, int slotIndex) {
        if (slotIndex == ToolSelector.OFFHAND_SLOT) {
            return player.getItemBySlot(EquipmentSlot.OFFHAND);
        }
        return player.getInventory().getItem(slotIndex);
    }

    private static void setItemToSlot(Player player, int slotIndex, ItemStack stack) {
        if (slotIndex == ToolSelector.OFFHAND_SLOT) {
            player.setItemSlot(EquipmentSlot.OFFHAND, stack);
        } else {
            player.getInventory().setItem(slotIndex, stack);
        }
    }

    public static void swapForBlockBreak(Player player, int slotIndex) {
        Inventory inv = player.getInventory();
        int mainHandSlot = inv.selected;

        if (mainHandSlot == slotIndex) {
            return;
        }

        ItemStack mainHand = inv.getItem(mainHandSlot);
        ItemStack tool = getItemFromSlot(player, slotIndex);

        inv.setItem(mainHandSlot, tool);
        setItemToSlot(player, slotIndex, mainHand);

        pendingSwaps.put(player, new SwapState(slotIndex, mainHandSlot));
    }

    public static void restore(Player player) {
        SwapState state = pendingSwaps.remove(player);
        if (state == null) {
            return;
        }

        Inventory inv = player.getInventory();

        ItemStack currentMainHand = inv.getItem(state.mainHandSlot);
        ItemStack currentToolSlot = getItemFromSlot(player, state.toolSlotIndex);

        inv.setItem(state.mainHandSlot, currentToolSlot);
        setItemToSlot(player, state.toolSlotIndex, currentMainHand);
    }

    public static void scheduleRestore(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.server.execute(() -> restore(player));
        } else {
            restore(player);
        }
    }

    private static void swapMainHandAttributes(Player player, ItemStack oldItem, ItemStack newItem) {
        EquipmentSlot slot = EquipmentSlot.MAINHAND;

        if (!oldItem.isEmpty()) {
            oldItem.getAttributeModifiers().forEach(slot, (attribute, modifier) -> {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                }
            });
            EnchantmentHelper.forEachModifier(oldItem, slot, (attribute, modifier) -> {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                }
            });
        }

        if (!newItem.isEmpty()) {
            newItem.getAttributeModifiers().forEach(slot, (attribute, modifier) -> {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null && instance.getModifier(modifier.id()) == null) {
                    instance.addTransientModifier(modifier);
                }
            });
            EnchantmentHelper.forEachModifier(newItem, slot, (attribute, modifier) -> {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null && instance.getModifier(modifier.id()) == null) {
                    instance.addTransientModifier(modifier);
                }
            });
        }
    }

    public static <T> T withSwappedTool(Player player, int slotIndex, java.util.function.Supplier<T> action) {
        Inventory inv = player.getInventory();
        int mainHandSlot = inv.selected;

        if (mainHandSlot == slotIndex) {
            return action.get();
        }

        ItemStack mainHand = inv.getItem(mainHandSlot);
        ItemStack tool = getItemFromSlot(player, slotIndex);

        try {
            inv.setItem(mainHandSlot, tool);
            setItemToSlot(player, slotIndex, mainHand);

            swapMainHandAttributes(player, mainHand, tool);

            return action.get();
        } finally {
            inv.setItem(mainHandSlot, mainHand);
            setItemToSlot(player, slotIndex, tool);

            swapMainHandAttributes(player, tool, mainHand);
        }
    }
}
