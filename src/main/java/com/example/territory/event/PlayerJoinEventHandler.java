package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import com.example.territory.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class PlayerJoinEventHandler {

    // 6 colors for up to 6 players
    private static final String[] TEAM_COLORS = {"RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "ORANGE"};

    // Preset base coordinates to space out players by at least 1500 blocks, avoiding the initial world spawn (0,0)
    private static final int[][] BASE_COORDINATES = {
        {3000, 3000},
        {-3000, 3000},
        {3000, -3000},
        {-3000, -3000},
        {4500, 0},
        {-4500, 0}
    };

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.getLevel();
        TerritorySavedData data = TerritorySavedData.get(level);

        // 1. If game has not started, do not register, just notify the player
        if (!data.isGameStarted()) {
            player.sendMessage(new TextComponent("§6[Territory Conquest] §c陣取りゲームはまだ開始されていません。"), player.getUUID());
            player.sendMessage(new TextComponent("§c管理者がコマンド「/territory start」を実行するまでお待ちください。"), player.getUUID());
            return;
        }

        UUID uuid = player.getUUID();
        // 2. If the game has started, register if not already registered (late joiner case)
        if (!data.getPlayers().containsKey(uuid)) {
            registerAndSpawnPlayer(player, level, data);
        }
    }

    /**
     * Registers a player and spawns them at a plains biome.
     * Places the main core Y+2 blocks above their spawn position.
     * Returns true if successfully registered and spawned.
     */
    public static boolean registerAndSpawnPlayer(ServerPlayer player, ServerLevel level, TerritorySavedData data) {
        UUID uuid = player.getUUID();

        // 1. Check if the player is already registered
        if (data.getPlayers().containsKey(uuid)) {
            // Already registered, do nothing
            return false;
        }

        // 2. Limit to max 6 players
        int playerCount = data.getPlayers().size();
        if (playerCount >= 6) {
            player.sendMessage(new TextComponent("§cこのサーバーの陣取りゲームは最大人数（6人）に達しています。あなたは観戦者です。"), player.getUUID());
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            return false;
        }

        // 3. Register the new player
        String teamColor = TEAM_COLORS[playerCount];
        int[] baseCoords = BASE_COORDINATES[playerCount];

        // 4. Find plains spawn location progressively to ensure absolute plains biome spawn
        // while staying as close as possible to the base coordinate.
        BlockPos spawnPos = findPlainsSpawnLocationProgressively(level, baseCoords[0], baseCoords[1]);

        if (spawnPos == null) {
            // Absolute fallback only if progressive search failed (extremely rare, e.g. custom single-biome ocean world)
            level.getChunkSource().getChunk(baseCoords[0] >> 4, baseCoords[1] >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
            int fallbackY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, baseCoords[0], baseCoords[1]);
            if (fallbackY <= level.getMinBuildHeight() || fallbackY <= 0) {
                fallbackY = 64; // Safe default surface height
            }
            spawnPos = new BlockPos(baseCoords[0], fallbackY, baseCoords[1]);
            TerritoryConquest.LOGGER.warn("Could not find a plains biome even after progressive search up to 100,000 blocks. Using fallback at base coordinates.");
        }

        // Save player state
        TerritorySavedData.PlayerState playerState = new TerritorySavedData.PlayerState();
        playerState.uuid = uuid;
        playerState.username = player.getName().getString();
        playerState.teamColor = teamColor;
        playerState.spawnX = spawnPos.getX();
        playerState.spawnY = spawnPos.getY();
        playerState.spawnZ = spawnPos.getZ();
        playerState.isVassal = false;
        playerState.overlordUuid = null;

        data.getPlayers().put(uuid, playerState);

        // Claim initial sanctuary chunk
        ChunkPos sanctuaryChunk = new ChunkPos(spawnPos);
        data.getClaimedChunks().put(sanctuaryChunk.toLong(), uuid);

        // Place core block 2 blocks above spawn surface
        BlockPos corePos = spawnPos.above(2);
        level.setBlockAndUpdate(corePos, ModBlocks.CORE_BLOCK.get().defaultBlockState());
        data.addCore(corePos, uuid);

        data.setDirty(); // Mark saved data as dirty to ensure saving

        // 5. Teleport player and set respawn
        player.teleportTo(level, spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        player.setRespawnPosition(level.dimension(), spawnPos, 0.0F, true, false);

        // Send feedback
        player.sendMessage(new TextComponent("§a陣取りゲームに登録されました！"), player.getUUID());
        player.sendMessage(new TextComponent("§aあなたのチームカラー: §6" + teamColor), player.getUUID());
        player.sendMessage(new TextComponent("§a初期聖域座標: §e" + spawnPos.getX() + ", " + spawnPos.getY() + ", " + spawnPos.getZ()), player.getUUID());

        // Broadcast to all players
        level.getServer().getPlayerList().broadcastMessage(
            new TextComponent("§e" + player.getName().getString() + " が新規プレイヤーとして参加しました！ チーム: " + teamColor),
            net.minecraft.network.chat.ChatType.SYSTEM,
            UUID.randomUUID()
        );

        return true;
    }

    /**
     * Searches progressively in expanding ring zones to find the nearest plains biome,
     * ensuring players stay as close as possible to their assigned base coordinates.
     */
    public static BlockPos findPlainsSpawnLocationProgressively(ServerLevel level, int baseX, int baseZ) {
        // Stage 1: Radius 0 to 4,000, step size 128 (Detailed search)
        BlockPos pos = findPlainsSpawnLocationInRing(level, baseX, baseZ, 0, 4000, 128);
        if (pos != null) return pos;

        // Stage 2: Radius 4,000 to 13,000, step size 256 (Balanced search)
        pos = findPlainsSpawnLocationInRing(level, baseX, baseZ, 4000, 13000, 256);
        if (pos != null) return pos;

        // Stage 3: Radius 13,000 to 30,000, step size 512 (Expanded search)
        pos = findPlainsSpawnLocationInRing(level, baseX, baseZ, 13000, 30000, 512);
        if (pos != null) return pos;

        // Stage 4: Radius 30,000 to 100,000, step size 1024 (Global safety search)
        pos = findPlainsSpawnLocationInRing(level, baseX, baseZ, 30000, 100000, 1024);
        return pos;
    }

    /**
     * Performs a spiral search within the specified ring [minRadius, maxRadius] centered on baseX/baseZ.
     * Uses stepSize to optimize performance and prevent server freezing.
     */
    private static BlockPos findPlainsSpawnLocationInRing(ServerLevel level, int baseX, int baseZ, int minRadius, int maxRadius, int stepSize) {
        int gridX = 0;
        int gridZ = 0;
        int dx = 0;
        int dz = -1;

        int maxGridRadius = maxRadius / stepSize;
        int side = maxGridRadius * 2 + 1;
        long limit = (long) side * side;

        for (long i = 0; i < limit; i++) {
            int checkX = baseX + gridX * stepSize;
            int checkZ = baseZ + gridZ * stepSize;

            int distanceX = Math.abs(checkX - baseX);
            int distanceZ = Math.abs(checkZ - baseZ);
            int maxDistance = Math.max(distanceX, distanceZ);

            if (maxDistance >= minRadius && maxDistance <= maxRadius) {
                BlockPos checkPos = new BlockPos(checkX, 64, checkZ);

                if (level.getBiome(checkPos).is(Biomes.PLAINS) || 
                    level.getBiome(checkPos).is(Biomes.SUNFLOWER_PLAINS) || 
                    level.getBiome(checkPos).is(Biomes.MEADOW) ||
                    level.getBiome(checkPos).is(Biomes.FOREST) ||
                    level.getBiome(checkPos).is(Biomes.FLOWER_FOREST) ||
                    level.getBiome(checkPos).is(Biomes.BIRCH_FOREST) ||
                    level.getBiome(checkPos).is(Biomes.SAVANNA)) {
                    
                    level.getChunkSource().getChunk(checkX >> 4, checkZ >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, checkX, checkZ);
                    if (surfaceY > level.getMinBuildHeight() && surfaceY > 0) {
                        return new BlockPos(checkX, surfaceY, checkZ);
                    }
                }
            }

            if (gridX == gridZ || (gridX < 0 && gridX == -gridZ) || (gridX > 0 && gridX == 1 - gridZ)) {
                int temp = dx;
                dx = -dz;
                dz = temp;
            }
            gridX += dx;
            gridZ += dz;
        }

        return null;
    }
}
