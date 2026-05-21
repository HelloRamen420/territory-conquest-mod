package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects when a player moves into a new chunk and:
 * 1. Shows an action bar message indicating whose territory they entered.
 * 2. Spawns colored particles along the chunk border continuously for ~3 seconds.
 */
@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class TerritoryChunkEventHandler {

    // Track last known chunk per player to detect chunk crossings
    private static final Map<UUID, Long> lastChunkMap = new HashMap<>();

    // Track remaining ticks to display border particles: playerUUID -> [chunkLong, ticksRemaining]
    private static final Map<UUID, long[]> borderTimerMap = new HashMap<>();

    // Side map: playerUUID -> teamColor string for the active border display
    private static final Map<UUID, String> pendingColorMap = new HashMap<>();

    // Duration (in ticks) for border display: 20 ticks = 1 second, so 60 = 3 seconds
    private static final int BORDER_DISPLAY_TICKS = 60;

    // Particle spawn interval (every N ticks during the display period)
    private static final int PARTICLE_INTERVAL = 4;

    // Team color -> particle color (R, G, B in 0-1 range)
    private static final Map<String, float[]> COLOR_MAP = new HashMap<>();

    static {
        COLOR_MAP.put("RED",    new float[]{1.0f, 0.0f, 0.0f});
        COLOR_MAP.put("BLUE",   new float[]{0.0f, 0.3f, 1.0f});
        COLOR_MAP.put("GREEN",  new float[]{0.0f, 1.0f, 0.2f});
        COLOR_MAP.put("YELLOW", new float[]{1.0f, 0.9f, 0.0f});
        COLOR_MAP.put("PURPLE", new float[]{0.7f, 0.0f, 1.0f});
        COLOR_MAP.put("ORANGE", new float[]{1.0f, 0.5f, 0.0f});
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Clear cached chunk on dimension change
        if (event.getPlayer() instanceof ServerPlayer player) {
            lastChunkMap.remove(player.getUUID());
            borderTimerMap.remove(player.getUUID());
            pendingColorMap.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        ServerLevel level = player.getLevel();
        ChunkPos currentChunk = new ChunkPos(player.blockPosition());
        long currentChunkLong = currentChunk.toLong();
        UUID playerUuid = player.getUUID();

        // === Step 1: Check if player moved to a new chunk (every 10 ticks for performance) ===
        if (player.tickCount % 10 == 0) {
            Long lastChunkLong = lastChunkMap.get(playerUuid);
            if (lastChunkLong == null || lastChunkLong != currentChunkLong) {
                lastChunkMap.put(playerUuid, currentChunkLong);

                TerritorySavedData data = TerritorySavedData.get(level);
                UUID ownerUuid = data.getClaimedChunks().get(currentChunkLong);

                if (ownerUuid == null) {
                    // Unclaimed territory — show message if transitioning from claimed
                    if (lastChunkLong != null) {
                        UUID lastOwnerUuid = data.getClaimedChunks().get(lastChunkLong);
                        if (lastOwnerUuid != null) {
                            player.connection.send(new ClientboundSetActionBarTextPacket(
                                new TextComponent("§7未開拓の土地")
                            ));
                        }
                    }
                    return;
                }

                TerritorySavedData.PlayerState ownerState = data.getPlayers().get(ownerUuid);
                if (ownerState == null) return;

                // Check if moving from own territory to own territory
                if (lastChunkLong != null) {
                    UUID lastOwnerUuid = data.getClaimedChunks().get(lastChunkLong);
                    if (ownerUuid.equals(playerUuid) && playerUuid.equals(lastOwnerUuid)) {
                        boolean holdingFlag = player.getMainHandItem().is(com.example.territory.item.ModItems.TERRITORY_FLAG_ITEM.get()) ||
                                              player.getOffhandItem().is(com.example.territory.item.ModItems.TERRITORY_FLAG_ITEM.get());
                        if (!holdingFlag) {
                            return; // Skip both message and border particles
                        }
                    }
                }

                String colorCode = teamColorToCode(ownerState.teamColor);
                boolean isOwn = ownerUuid.equals(playerUuid);

                String message = isOwn
                    ? colorCode + "【自分の領土】 " + ownerState.username + " のチャンク"
                    : colorCode + "【" + ownerState.username + " の領土】";
                if (ownerState.isVassal) {
                    message += " §7(属国)";
                }

                // Add 2nd line with chunk coordinates
                message += "\n" + colorCode + "チャンク座標: [" + currentChunk.x + ", " + currentChunk.z + "]";

                player.connection.send(new ClientboundSetActionBarTextPacket(
                    new TextComponent(message)
                ));

                // Start border display timer when entering any claimed chunk
                if (lastChunkLong != null) {
                    startBorderDisplay(playerUuid, currentChunkLong, ownerState.teamColor);
                }
            }
        }

        // === Step 2: Continuously spawn border particles for the display duration ===
        long[] timerEntry = borderTimerMap.get(playerUuid);
        if (timerEntry == null) return;

        long timerChunk = timerEntry[0];
        long ticksLeft  = timerEntry[1];

        if (ticksLeft <= 0) {
            borderTimerMap.remove(playerUuid);
            pendingColorMap.remove(playerUuid);
            return;
        }

        // Count down timer each tick
        timerEntry[1]--;

        // Spawn particles every PARTICLE_INTERVAL ticks
        if (player.tickCount % PARTICLE_INTERVAL == 0) {
            ChunkPos borderChunk = new ChunkPos(ChunkPos.getX(timerChunk), ChunkPos.getZ(timerChunk));
            String teamColor = pendingColorMap.getOrDefault(playerUuid, "WHITE");
            spawnChunkBorderParticles(level, player, borderChunk, teamColor);
        }
    }

    /**
     * Spawns dust particles along the edges of the given chunk at the player's Y level.
     */
    private static void spawnChunkBorderParticles(ServerLevel level, ServerPlayer player,
                                                   ChunkPos chunk, String teamColor) {
        float[] color = COLOR_MAP.getOrDefault(teamColor, new float[]{1f, 1f, 1f});
        DustParticleOptions dustOptions = new DustParticleOptions(
            new com.mojang.math.Vector3f(color[0], color[1], color[2]), 1.2f
        );

        int minX = chunk.getMinBlockX();
        int maxX = chunk.getMaxBlockX();
        int minZ = chunk.getMinBlockZ();
        int maxZ = chunk.getMaxBlockZ();

        // Use the player's current Y + 1 so particles appear at ground level
        int playerY = (int) player.getY();

        // Spawn particles along all 4 edges — every 1 block for a solid line
        for (int x = minX; x <= maxX; x++) {
            spawnDust(level, dustOptions, x, playerY, minZ);
            spawnDust(level, dustOptions, x, playerY, maxZ);
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            spawnDust(level, dustOptions, minX, playerY, z);
            spawnDust(level, dustOptions, maxX, playerY, z);
        }
    }

    /**
     * Public API: Start showing border particles for a given player / chunk / team color.
     * Called from TerritoryPlacementEventHandler when a flag is placed.
     */
    public static void startBorderDisplay(UUID playerUuid, long chunkLong, String teamColor) {
        borderTimerMap.put(playerUuid, new long[]{ chunkLong, BORDER_DISPLAY_TICKS });
        pendingColorMap.put(playerUuid, teamColor);
    }

    private static void spawnDust(ServerLevel level, DustParticleOptions dust, int x, int y, int z) {
        level.sendParticles(dust, x + 0.5, y + 1.0, z + 0.5, 1, 0, 0, 0, 0);
    }

    private static String teamColorToCode(String teamColor) {
        return switch (teamColor) {
            case "RED"    -> "§c";
            case "BLUE"   -> "§9";
            case "GREEN"  -> "§a";
            case "YELLOW" -> "§e";
            case "PURPLE" -> "§5";
            case "ORANGE" -> "§6";
            default       -> "§f";
        };
    }
}
