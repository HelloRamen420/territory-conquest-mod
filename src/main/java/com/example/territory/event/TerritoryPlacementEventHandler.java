package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import com.example.territory.block.ModBlocks;
import com.example.territory.block.TerritoryFlagBlock;
import com.example.territory.util.MonsterSpawnerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles placement and breaking events for SubCores and Territory Flags.
 * Enforces outdoor placement limits, adjacency rules, and handles territory state updates.
 */
@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class TerritoryPlacementEventHandler {

    private static final Random RANDOM = new Random();
    private static final double CURSED_LAND_CHANCE = 0.05; // 5%

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isClientSide()) return;

        BlockState placedState = event.getPlacedBlock();
        BlockPos pos = event.getPos();
        ServerLevel level = (ServerLevel) event.getWorld();
        TerritorySavedData data = TerritorySavedData.get(level);

        // Ensure placer is a player
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUuid = player.getUUID();
        TerritorySavedData.PlayerState playerState = data.getPlayers().get(playerUuid);
        if (playerState == null) return;

        // 1. HANDLE SUB_CORE_BLOCK PLACEMENT
        if (placedState.is(ModBlocks.SUB_CORE_BLOCK.get())) {
            // A. Dimension Restriction
            if (level.dimension() != Level.OVERWORLD) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§cサブコアは地上世界（オーバーワールド）にしか設置できません！"), playerUuid);
                return;
            }

            // B. Fluid Restriction (Cannot be placed in water)
            if (level.getFluidState(pos).getType() != Fluids.EMPTY) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§cサブコアを水中に設置することはできません！"), playerUuid);
                return;
            }

            // C. Cave/Underground Restriction (Must see sky)
            if (!level.canSeeSky(pos)) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§cサブコアは空が見える屋外にしか設置できません！"), playerUuid);
                return;
            }

            // D. Territory Restriction (Must be placed inside their own claimed territory)
            ChunkPos chunkPos = new ChunkPos(pos);
            UUID ownerUuid = data.getClaimedChunks().get(chunkPos.toLong());
            if (ownerUuid == null || !ownerUuid.equals(playerUuid)) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§cサブコアは自分自身の領土内にしか設置できません！"), playerUuid);
                return;
            }

            // E. Limit SubCores to 3 per player
            if (playerState.subCores.size() >= 3) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§cサブコアは1プレイヤーにつき3つまでしか設置できません！"), playerUuid);
                return;
            }

            // F. Cannot place SubCore in the same chunk as the Main Core (Sanctuary Chunk)
            ChunkPos sanctuaryChunk = new ChunkPos(playerState.spawnX >> 4, playerState.spawnZ >> 4);
            if (sanctuaryChunk.equals(chunkPos)) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§cサブコアをメインコアと同じチャンク（聖域）に設置することはできません！"), playerUuid);
                return;
            }

            // G. Cannot place multiple SubCores in the same chunk
            for (BlockPos existingSubCore : playerState.subCores) {
                if (new ChunkPos(existingSubCore).equals(chunkPos)) {
                    event.setCanceled(true);
                    player.sendMessage(new TextComponent("§cこのチャンクにはすでにサブコアが設置されています！"), playerUuid);
                    return;
                }
            }

            // All checks passed! Register the subcore
            playerState.subCores.add(pos);
            data.setDirty();

            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
            player.sendMessage(new TextComponent("§a✔ サブコアを設置しました！防衛シールドが有効化されました。"), playerUuid);
        }

        // 2. HANDLE TERRITORY_FLAG PLACEMENT
        else if (placedState.is(ModBlocks.TERRITORY_FLAG.get())) {
            // Dimension Restriction (Can only claim in Overworld)
            if (level.dimension() != Level.OVERWORLD) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§c開拓の旗は地上世界（オーバーワールド）にしか設置できません！"), playerUuid);
                return;
            }

            ChunkPos chunkPos = new ChunkPos(pos);
            long chunkLong = chunkPos.toLong();

            // A. Already Owned Restriction
            if (data.getClaimedChunks().containsKey(chunkLong)) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§cこのチャンクはすでに領有されています！"), playerUuid);
                return;
            }

            // B. Adjacency Restriction (Must be adjacent to existing territory)
            boolean isAdjacent = false;
            for (Map.Entry<Long, UUID> entry : data.getClaimedChunks().entrySet()) {
                if (entry.getValue().equals(playerUuid)) {
                    ChunkPos claimedChunk = new ChunkPos(entry.getKey());
                    if (isAdjacent(chunkPos, claimedChunk)) {
                        isAdjacent = true;
                        break;
                    }
                }
            }

            if (!isAdjacent) {
                event.setCanceled(true);
                player.sendMessage(new TextComponent("§c開拓の旗は、自分の既存領土に隣接した中立チャンクにしか設置できません！"), playerUuid);
                return;
            }

            // All checks passed! Register the claim
            data.getClaimedChunks().put(chunkLong, playerUuid);
            // Track flag position for upkeep system
            playerState.flags.add(pos);
            data.setDirty();

            // Set the block state color to match the player's team color
            int colorId = teamColorToInt(playerState.teamColor);
            BlockState coloredState = placedState.setValue(TerritoryFlagBlock.COLOR, colorId);
            level.setBlock(pos, coloredState, 3);

            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0f, 1.2f);
            player.sendMessage(new TextComponent("§a✔ 新たなチャンク [" + chunkPos.x + ", " + chunkPos.z + "] を領有しました！"), playerUuid);

            // 5% chance: cursed land ambush!
            if (RANDOM.nextDouble() < CURSED_LAND_CHANCE) {
                int spawnCount = 2 + RANDOM.nextInt(2); // 2 or 3
                MonsterSpawnerUtil.spawnCustomMonsters(level, pos, spawnCount);
                level.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.8f);
                player.sendMessage(new TextComponent("§c§l⚠ 呪われた土地！モンスターが現れた！"), playerUuid);
            }

            // Show border particles on the newly claimed chunk
            TerritoryChunkEventHandler.startBorderDisplay(playerUuid, chunkLong, playerState.teamColor);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isClientSide()) return;

        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        ServerLevel level = (ServerLevel) event.getWorld();
        TerritorySavedData data = TerritorySavedData.get(level);
        ServerPlayer breaker = (ServerPlayer) event.getPlayer();

        // 1. HANDLE SUB_CORE_BLOCK DESTRUCTION
        if (state.is(ModBlocks.SUB_CORE_BLOCK.get())) {
            ChunkPos chunkPos = new ChunkPos(pos);
            UUID ownerUuid = data.getClaimedChunks().get(chunkPos.toLong());

            if (ownerUuid != null) {
                TerritorySavedData.PlayerState ownerState = data.getPlayers().get(ownerUuid);
                if (ownerState != null) {
                    ownerState.subCores.remove(pos);
                    data.setDirty();

                    // Notify owner if broken by someone else
                    if (!ownerUuid.equals(breaker.getUUID())) {
                        ServerPlayer ownerPlayer = (ServerPlayer) level.getPlayerByUUID(ownerUuid);
                        if (ownerPlayer != null) {
                            ownerPlayer.sendMessage(new TextComponent("§c§l警告: " + breaker.getName().getString() + " によってサブコアが破壊されました！"), ownerPlayer.getUUID());
                        }
                        breaker.sendMessage(new TextComponent("§a✔ 敵のサブコアを破壊しました！"), breaker.getUUID());
                    } else {
                        breaker.sendMessage(new TextComponent("§eサブコアを撤去しました。"), breaker.getUUID());
                    }
                    level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            }
        }

        // 2. HANDLE TERRITORY_FLAG DESTRUCTION
        else if (state.is(ModBlocks.TERRITORY_FLAG.get())) {
            ChunkPos chunkPos = new ChunkPos(pos);
            long chunkLong = chunkPos.toLong();
            UUID ownerUuid = data.getClaimedChunks().get(chunkLong);

            if (ownerUuid != null) {
                // Prevent removing sanctuary chunk (sanctuary has core_block, but let's be double safe)
                TerritorySavedData.PlayerState ownerState = data.getPlayers().get(ownerUuid);
                if (ownerState != null) {
                    ChunkPos sanctuaryChunk = new ChunkPos(ownerState.spawnX >> 4, ownerState.spawnZ >> 4);
                    if (sanctuaryChunk.equals(chunkPos)) {
                        event.setCanceled(true);
                        breaker.sendMessage(new TextComponent("§c聖域チャンクの領有を解除することはできません！"), breaker.getUUID());
                        return;
                    }

                    // Remove the claim
                    data.getClaimedChunks().remove(chunkLong);
                    // Remove flag from tracking list
                    ownerState.flags.remove(pos);
                    data.setDirty();

                    level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);

                    // Notify owner if broken by someone else
                    if (!ownerUuid.equals(breaker.getUUID())) {
                        ServerPlayer ownerPlayer = (ServerPlayer) level.getPlayerByUUID(ownerUuid);
                        if (ownerPlayer != null) {
                            ownerPlayer.sendMessage(new TextComponent("§c§l警告: " + breaker.getName().getString() + " によって開拓の旗が破壊され、領土 [" + chunkPos.x + ", " + chunkPos.z + "] が失われました！"), ownerPlayer.getUUID());
                        }
                        breaker.sendMessage(new TextComponent("§a✔ 敵の開拓の旗を破壊し、領地を解放しました！"), breaker.getUUID());
                    } else {
                        breaker.sendMessage(new TextComponent("§e開拓の旗を撤去し、領土を解放しました。"), breaker.getUUID());
                    }
                }
            }
        }
    }

    private static boolean isAdjacent(ChunkPos c1, ChunkPos c2) {
        int dx = Math.abs(c1.x - c2.x);
        int dz = Math.abs(c1.z - c2.z);
        // 4-way adjacent
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    private static int teamColorToInt(String color) {
        return switch (color) {
            case "RED" -> 0;
            case "BLUE" -> 1;
            case "GREEN" -> 2;
            case "YELLOW" -> 3;
            case "PURPLE" -> 4;
            case "ORANGE" -> 5;
            default -> 0;
        };
    }
}
