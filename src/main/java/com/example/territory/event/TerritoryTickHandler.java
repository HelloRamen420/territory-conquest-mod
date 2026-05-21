package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import com.example.territory.block.ModBlocks;
import com.example.territory.util.MonsterSpawnerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class TerritoryTickHandler {

    // Cost per chunk per day (1 Minecraft day = 24000 ticks)
    private static final int UPKEEP_PER_CHUNK = 2;

    private static final java.util.Random RANDOM = new java.util.Random();

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.world instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        // 現在のマインクラフト日数を計算
        long dayTime = level.getDayTime();
        long currentDay = dayTime / 24000;

        // currentDay が最後に処理した日より大きければ維持費を処理する。
        // timeOfDay < 20 の窓チェックは行わない。
        // → ベッドで寝ると dayTime が一気に次の朝へジャンプするため
        //   窓をすり抜けて維持費が処理されないバグを防ぐ。
        // 毎tick O(1) の比較のみなのでパフォーマンス影響はない。
        TerritorySavedData data = TerritorySavedData.get(level);
        long lastProcessedDay = data.getLastProcessedDay();
        if (currentDay > lastProcessedDay) {
            data.setLastProcessedDay(currentDay);
            processUpkeep(level);
        }

        // 5分ごと (6000 ticks) に不労所得サブコアのゴールド自動生産を処理
        long gameTime = level.getGameTime();
        if (gameTime > 0 && gameTime % 6000 == 0) {
            processPassiveIncome(level);
        }
    }

    private static void processPassiveIncome(ServerLevel level) {
        TerritorySavedData data = TerritorySavedData.get(level);
        boolean dirty = false;

        for (Map.Entry<UUID, TerritorySavedData.PlayerState> entry : data.getPlayers().entrySet()) {
            UUID playerUuid = entry.getKey();
            TerritorySavedData.PlayerState state = entry.getValue();

            int incomeCores = state.passiveIncomeSubCores.size();
            if (incomeCores > 0) {
                int earned = incomeCores * 50;
                state.treasury += earned;
                state.addHistory("+" + earned + "G", "不労所得自動精製 (サブコア x" + incomeCores + ")");
                dirty = true;

                // オンラインのプレイヤーに通知
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
                if (player != null) {
                    player.sendMessage(new TextComponent(
                            "§a§l[不労所得] 自動精製サブコア (" + incomeCores + "台) から " + earned + "G を回収しました！ (残高: " + state.treasury + "G)"
                    ), playerUuid);
                }
            }
        }
        if (dirty) {
            data.setDirty();
        }
    }

    private static void processUpkeep(ServerLevel level) {
        TerritorySavedData data = TerritorySavedData.get(level);
        int upkeepPerChunk = UPKEEP_PER_CHUNK;

        for (Map.Entry<UUID, TerritorySavedData.PlayerState> entry : data.getPlayers().entrySet()) {
            UUID playerUuid = entry.getKey();
            TerritorySavedData.PlayerState state = entry.getValue();

            // 聖域チャンクを除いた、追加で開拓したチャンク（旗の数）を維持費の計算対象とする
            int ownedChunks = state.flags.size();

            if (ownedChunks == 0) continue;

            int upkeepCost = ownedChunks * upkeepPerChunk;

            if (state.treasury >= upkeepCost) {
                // Pay upkeep normally
                state.treasury -= upkeepCost;
                state.addHistory("-" + upkeepCost + "G", "領土維持費 (" + ownedChunks + "チャンク)");
                data.setDirty();

                // Notify player if online
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
                if (player != null) {
                    player.sendMessage(new TextComponent(
                            "§e[維持費] 今日の維持費として " + upkeepCost + "G を支払いました。"
                            + " (" + upkeepPerChunk + "G×" + ownedChunks + "チャンク / 残高: " + state.treasury + "G)"
                    ), playerUuid);
                }
            } else {
                // Cannot afford upkeep → corruption!
                // Drain whatever gold is left (even if it's less than the full cost)
                int drained = state.treasury;
                state.treasury = 0;
                state.addHistory("-" + drained + "G", "領土維持費 (資金不足、残高全額没収 / " + ownedChunks + "チャンク)");
                data.setDirty(); // ← 必ずここで保存。これがないとGが引かれない

                // Find the flag furthest from core (spawnX/Y/Z is core location for this player)
                BlockPos corePos = new BlockPos(state.spawnX, state.spawnY, state.spawnZ);
                BlockPos farthestFlag = findFarthestFlag(state, corePos);

                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);

                if (farthestFlag != null) {
                    // Break the flag block physically
                    if (level.getBlockState(farthestFlag).is(ModBlocks.TERRITORY_FLAG.get())) {
                        level.destroyBlock(farthestFlag, false);
                    }

                    // Remove the claim from data
                    ChunkPos corruptedChunk = new ChunkPos(farthestFlag);
                    data.getClaimedChunks().remove(corruptedChunk.toLong());
                    state.flags.remove(farthestFlag);
                    data.setDirty();

                    // Spawn 3-6 custom monsters at the corrupted flag location
                    int spawnCount = 3 + RANDOM.nextInt(4); // 3, 4, 5, or 6
                    MonsterSpawnerUtil.spawnCustomMonsters(level, farthestFlag, spawnCount);

                    level.playSound(null, farthestFlag, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 1.0f);

                    String corruptMsg = "§c§l⚠ [領土腐敗] " + state.username + " の国庫が底をつき、"
                            + "領土 [" + corruptedChunk.x + ", " + corruptedChunk.z + "] が腐敗しました！"
                            + " (不足: " + (upkeepCost - drained) + "G / 国庫残高: 0G)";

                    // Notify the owner (even if offline, log to console)
                    TerritoryConquest.LOGGER.warn(corruptMsg);
                    if (player != null) {
                        player.sendMessage(new TextComponent(corruptMsg), playerUuid);
                    }
                } else {
                    // No flags to corrupt, but still notify and log
                    String msg = "§c§l⚠ [維持費不足] " + state.username + " の国庫が底をつきました！"
                            + " 維持費 " + upkeepCost + "G を支払えませんでした。"
                            + " (国庫から " + drained + "G を徴収 / 残高: 0G)";
                    TerritoryConquest.LOGGER.warn(msg);
                    if (player != null) {
                        player.sendMessage(new TextComponent(msg), playerUuid);
                    }
                }
            }
        }
    }

    /**
     * Finds the flag BlockPos in the player's flag list that is farthest from the core.
     */
    private static BlockPos findFarthestFlag(TerritorySavedData.PlayerState state, BlockPos corePos) {
        BlockPos farthest = null;
        double maxDist = -1;

        for (BlockPos flagPos : state.flags) {
            double dist = flagPos.distSqr(corePos);
            if (dist > maxDist) {
                maxDist = dist;
                farthest = flagPos;
            }
        }
        return farthest;
    }
}
