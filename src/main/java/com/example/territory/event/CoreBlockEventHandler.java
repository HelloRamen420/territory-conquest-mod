package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import com.example.territory.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Handles core block break events.
 * - Prevents players from breaking their own core (or others') unless specific conditions are met.
 * - Removes core from saved data when broken.
 */
@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class CoreBlockEventHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // Only care about core blocks
        if (!event.getState().is(ModBlocks.CORE_BLOCK.get())) {
            return;
        }

        if (!(event.getWorld() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = event.getPos();
        TerritorySavedData data = TerritorySavedData.get(serverLevel);
        UUID ownerUuid = data.getCoreOwner(pos);

        // If no owner found in data, allow break (cleanup)
        if (ownerUuid == null) {
            return;
        }

        UUID breakerUuid = event.getPlayer().getUUID();

        // Players cannot break their own core
        if (ownerUuid.equals(breakerUuid)) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                player.sendMessage(
                    new TextComponent("§c自分のコアは破壊できません。"),
                    player.getUUID()
                );
            }
            return;
        }

        // Enemy cores CAN be broken - CoreDestructionEventHandler will process the defeat logic
    }
}
