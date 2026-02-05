package cn.summerrecall.replace_dig.event;

import cn.summerrecall.replace_dig.ReplaceDig;
import cn.summerrecall.replace_dig.util.ItemSwapHelper;
import cn.summerrecall.replace_dig.util.ToolSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = ReplaceDig.MODID)
public class ToolBorrowHandler {

    private static final Map<Player, CachedResult> cache = new WeakHashMap<>();

    private static class CachedResult {
        final BlockState blockState;
        final long gameTick;
        final ToolSelector.SelectionResult selectionResult;

        CachedResult(BlockState blockState, long gameTick, ToolSelector.SelectionResult selectionResult) {
            this.blockState = blockState;
            this.gameTick = gameTick;
            this.selectionResult = selectionResult;
        }
    }

    private static ToolSelector.SelectionResult getCachedResult(Player player, BlockState blockState, BlockPos pos) {
        long tick = player.level().getGameTime();
        CachedResult cached = cache.get(player);
        if (cached != null && cached.gameTick == tick && cached.blockState.equals(blockState)) {
            return cached.selectionResult;
        }
        ToolSelector.SelectionResult result = ToolSelector.selectBestTool(player, blockState, pos);
        cache.put(player, new CachedResult(blockState, tick, result));
        return result;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockState blockState = event.getState();

        if (!ToolSelector.isHoldingBlockItem(player)) {
            return;
        }

        ToolSelector.SelectionResult result = getCachedResult(player, blockState, event.getPosition().orElse(null));

        if (result.result() == ToolSelector.SelectResult.FOUND && result.tool() != null) {
            float newSpeed = ItemSwapHelper.withSwappedTool(player, result.tool().slotIndex(), () ->
                player.getDestroySpeed(blockState)
            );

            event.setNewSpeed(newSpeed);
        }
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        BlockState blockState = event.getTargetBlock();

        if (!ToolSelector.isHoldingBlockItem(player)) {
            return;
        }

        ToolSelector.SelectionResult result = getCachedResult(player, blockState, null);

        if (result.result() == ToolSelector.SelectResult.FOUND && result.tool() != null) {
            if (result.tool().stack().isCorrectToolForDrops(blockState)) {
                event.setCanHarvest(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        BlockState blockState = event.getState();

        if (!ToolSelector.isHoldingBlockItem(player)) {
            return;
        }

        ToolSelector.SelectionResult result = getCachedResult(player, blockState, event.getPos());

        if (result.result() == ToolSelector.SelectResult.LOW_DURABILITY) {
            player.displayClientMessage(Component.literal("§c工具耐久过低无法使用"), true);
            return;
        }

        if (result.result() == ToolSelector.SelectResult.FOUND && result.tool() != null) {
            ItemSwapHelper.swapForBlockBreak(player, result.tool().slotIndex());

            ItemSwapHelper.scheduleRestore(player);
        }
    }
}
