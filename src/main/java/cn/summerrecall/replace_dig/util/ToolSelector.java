package cn.summerrecall.replace_dig.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ToolSelector {

    public static final int OFFHAND_SLOT = -1;
    public static final int MIN_DURABILITY = 3;

    public record ToolInfo(ItemStack stack, int slotIndex, float destroySpeed, int durability, float tierSpeed) {
    }

    public enum SelectResult {
        FOUND,
        NOT_FOUND,
        LOW_DURABILITY
    }

    public record SelectionResult(@Nullable ToolInfo tool, SelectResult result) {
    }

    public static SelectionResult selectBestTool(Player player, BlockState blockState, BlockPos pos) {
        Inventory inventory = player.getInventory();
        List<ToolInfo> candidates = new ArrayList<>();
        ToolInfo lowDurabilityTool = null;

        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        ToolInfo offhandTool = evaluateTool(offhand, OFFHAND_SLOT, blockState);
        if (offhandTool != null) {
            if (offhandTool.durability() >= MIN_DURABILITY) {
                return new SelectionResult(offhandTool, SelectResult.FOUND);
            } else {
                lowDurabilityTool = offhandTool;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            ToolInfo tool = evaluateTool(stack, i, blockState);
            if (tool != null) {
                if (tool.durability() >= MIN_DURABILITY) {
                    candidates.add(tool);
                } else if (lowDurabilityTool == null) {
                    lowDurabilityTool = tool;
                }
            }
        }

        if (!candidates.isEmpty()) {
            return new SelectionResult(selectBest(candidates), SelectResult.FOUND);
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            ToolInfo tool = evaluateTool(stack, i, blockState);
            if (tool != null) {
                if (tool.durability() >= MIN_DURABILITY) {
                    candidates.add(tool);
                } else if (lowDurabilityTool == null) {
                    lowDurabilityTool = tool;
                }
            }
        }

        if (!candidates.isEmpty()) {
            return new SelectionResult(selectBest(candidates), SelectResult.FOUND);
        }

        if (lowDurabilityTool != null) {
            return new SelectionResult(null, SelectResult.LOW_DURABILITY);
        }

        return new SelectionResult(null, SelectResult.NOT_FOUND);
    }

    @Nullable
    private static ToolInfo evaluateTool(ItemStack stack, int slotIndex, BlockState blockState) {
        if (stack.isEmpty()) {
            return null;
        }

        if (stack.getMaxDamage() <= 0) {
            return null;
        }

        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            return null;
        }

        float destroySpeed = stack.getDestroySpeed(blockState);

        if (!(stack.getItem() instanceof TieredItem) && destroySpeed <= 1.0f) {
            return null;
        }

        if (destroySpeed <= 1.0f) {
            return null;
        }

        int durability = stack.getMaxDamage() - stack.getDamageValue();
        float tierSpeed = 1.0f;
        if (stack.getItem() instanceof TieredItem tieredItem) {
            tierSpeed = tieredItem.getTier().getSpeed();
        }

        return new ToolInfo(stack, slotIndex, destroySpeed, durability, tierSpeed);
    }

    private static ToolInfo selectBest(List<ToolInfo> candidates) {
        ToolInfo best = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            ToolInfo current = candidates.get(i);
            if (current.tierSpeed() > best.tierSpeed()) {
                best = current;
            } else if (current.tierSpeed() == best.tierSpeed() && current.destroySpeed() > best.destroySpeed()) {
                best = current;
            }
        }
        return best;
    }

    public static boolean isHoldingBlockItem(Player player) {
        if (player.isCreative()) {
            return false;
        }

        ItemStack mainHand = player.getMainHandItem();

        if (mainHand.isEmpty()) {
            return false;
        }

        if (mainHand.getItem() instanceof TieredItem) {
            return false;
        }
//
        return mainHand.getMaxDamage() <= 0;
    }
}
