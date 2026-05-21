package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import com.example.territory.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/**
 * Handles main core block destruction.
 * When a core is broken:
 *  1. The owner becomes a vassal of the breaker.
 *  2. Their territory chunks transfer to the breaker.
 *  3. If all non-breaker players are vassals of the breaker, breaker wins.
 */
@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class CoreDestructionEventHandler {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCoreBreak(BlockEvent.BreakEvent event) {
        // Only process core blocks that weren't already canceled
        if (event.isCanceled()) return;
        if (!event.getState().is(ModBlocks.CORE_BLOCK.get())) return;
        if (!(event.getWorld() instanceof ServerLevel serverLevel)) return;
        if (!(event.getPlayer() instanceof ServerPlayer breaker)) return;

        BlockPos pos = event.getPos();
        TerritorySavedData data = TerritorySavedData.get(serverLevel);
        UUID loserUuid = data.getCoreOwner(pos);

        if (loserUuid == null) return; // No owner, just let it break normally
        if (loserUuid.equals(breaker.getUUID())) {
            // Can't destroy your own core
            event.setCanceled(true);
            breaker.sendMessage(new TextComponent("§c自分のコアは破壊できません。"), breaker.getUUID());
            return;
        }

        // --- Process defeat ---
        TerritorySavedData.PlayerState loserState = data.getPlayers().get(loserUuid);
        TerritorySavedData.PlayerState breakerState = data.getPlayers().get(breaker.getUUID());

        if (loserState == null || breakerState == null) return;

        // 1. Mark loser as vassal
        loserState.isVassal = true;
        loserState.overlordUuid = breaker.getUUID();

        // 2. Transfer all loser's chunks to breaker
        int transferredChunks = 0;
        for (Map.Entry<Long, UUID> entry : data.getClaimedChunks().entrySet()) {
            if (loserUuid.equals(entry.getValue())) {
                entry.setValue(breaker.getUUID());
                transferredChunks++;
            }
        }

        // 3. Remove the destroyed core from data
        data.removeCore(pos);
        data.setDirty();

        MinecraftServer server = serverLevel.getServer();

        // Announce defeat
        server.getPlayerList().broadcastMessage(
            new TextComponent("§c━━━━━━━━━━━━━━━━━━━━━━━━"),
            ChatType.SYSTEM, UUID.randomUUID()
        );
        server.getPlayerList().broadcastMessage(
            new TextComponent("§e⚔ §c" + loserState.username + " §eが §6" + breakerState.username + " §eに敗北しました！"),
            ChatType.SYSTEM, UUID.randomUUID()
        );
        server.getPlayerList().broadcastMessage(
            new TextComponent("§7" + transferredChunks + " チャンクが " + breakerState.username + " の領土になりました。"),
            ChatType.SYSTEM, UUID.randomUUID()
        );
        server.getPlayerList().broadcastMessage(
            new TextComponent("§c━━━━━━━━━━━━━━━━━━━━━━━━"),
            ChatType.SYSTEM, UUID.randomUUID()
        );

        // Set loser's game mode to spectator if they are online
        ServerPlayer loserPlayer = server.getPlayerList().getPlayer(loserUuid);
        if (loserPlayer != null) {
            loserPlayer.setGameMode(GameType.SPECTATOR);
            loserPlayer.sendMessage(new TextComponent("§cあなたのコアが破壊されました。属国状態になりました。"), loserPlayer.getUUID());
        }

        // 4. Check for victory condition
        checkVictory(server, data, breaker.getUUID());
    }

    /**
     * Checks if the given player has won (all other registered players are their vassals).
     */
    private static void checkVictory(MinecraftServer server, TerritorySavedData data, UUID winnerUuid) {
        Map<UUID, TerritorySavedData.PlayerState> players = data.getPlayers();
        if (players.size() < 2) return;

        boolean allVassals = players.entrySet().stream()
            .filter(e -> !e.getKey().equals(winnerUuid))
            .allMatch(e -> e.getValue().isVassal && winnerUuid.equals(e.getValue().overlordUuid));

        if (!allVassals) return;

        TerritorySavedData.PlayerState winner = players.get(winnerUuid);
        if (winner == null) return;

        // --- VICTORY ---
        server.getPlayerList().broadcastMessage(
            new TextComponent("§6§l━━━━━━━━━━━━━━━━━━━━━━━━"),
            ChatType.SYSTEM, UUID.randomUUID()
        );
        server.getPlayerList().broadcastMessage(
            new TextComponent("§e§l🏆 " + winner.username + " §6§lがすべての領土を制覇しました！"),
            ChatType.SYSTEM, UUID.randomUUID()
        );
        server.getPlayerList().broadcastMessage(
            new TextComponent("§a§lゲーム終了！おめでとうございます！"),
            ChatType.SYSTEM, UUID.randomUUID()
        );
        server.getPlayerList().broadcastMessage(
            new TextComponent("§6§l━━━━━━━━━━━━━━━━━━━━━━━━"),
            ChatType.SYSTEM, UUID.randomUUID()
        );

        TerritoryConquest.LOGGER.info("Victory! Player {} has conquered all territories.", winner.username);
    }
}
