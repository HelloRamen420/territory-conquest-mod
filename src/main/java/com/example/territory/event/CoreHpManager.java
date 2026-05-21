package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import com.example.territory.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages HP and dynamic BossBars for Main Cores and SubCores.
 * Core blocks have high hardness but also require multiple full break events (HP) to be destroyed.
 */
@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class CoreHpManager {

    public static final int MAX_MAIN_CORE_HP = 5;
    public static final int MAX_SUB_CORE_HP = 3;

    // Transient map storing current HP of cores (BlockPos long value -> HP)
    private static final Map<Long, Integer> CORE_HP = new HashMap<>();

    // Map storing BossBars per player (player UUID -> BossBar)
    private static final Map<UUID, ServerBossEvent> PLAYER_BOSS_BARS = new HashMap<>();

    // Map storing which core a player is currently viewing (player UUID -> Core BlockPos asLong)
    private static final Map<UUID, Long> PLAYER_CURRENT_CORE = new HashMap<>();

    public static int getMaxHp(boolean isSubCore) {
        return isSubCore ? MAX_SUB_CORE_HP : MAX_MAIN_CORE_HP;
    }

    public static int getHp(BlockPos pos, boolean isSubCore) {
        long posLong = pos.asLong();
        if (!CORE_HP.containsKey(posLong)) {
            CORE_HP.put(posLong, getMaxHp(isSubCore));
        }
        return CORE_HP.get(posLong);
    }

    public static void damage(BlockPos pos, int amount, boolean isSubCore) {
        long posLong = pos.asLong();
        int currentHp = getHp(pos, isSubCore);
        int newHp = Math.max(0, currentHp - amount);
        CORE_HP.put(posLong, newHp);
    }

    public static void removeHp(BlockPos pos) {
        CORE_HP.remove(pos.asLong());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isClientSide()) return;
        if (!(event.getWorld() instanceof ServerLevel level)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        boolean isMainCore = event.getState().is(ModBlocks.CORE_BLOCK.get());
        boolean isSubCore = event.getState().is(ModBlocks.SUB_CORE_BLOCK.get());

        if (!isMainCore && !isSubCore) return;

        TerritorySavedData data = TerritorySavedData.get(level);
        UUID ownerUuid = null;
        if (isMainCore) {
            ownerUuid = data.getCoreOwner(pos);
        } else {
            ChunkPos chunkPos = new ChunkPos(pos);
            ownerUuid = data.getClaimedChunks().get(chunkPos.toLong());
        }

        if (ownerUuid == null) return; // No owner, let it break

        // Cannot break your own cores
        if (ownerUuid.equals(player.getUUID())) {
            event.setCanceled(true);
            player.sendMessage(new TextComponent("§c自分のコアは破壊できません。"), player.getUUID());
            return;
        }

        // Handle HP system
        int hp = getHp(pos, isSubCore);
        if (hp > 1) {
            // Cancel break and subtract HP
            event.setCanceled(true);
            damage(pos, 1, isSubCore);
            int newHp = getHp(pos, isSubCore);
            
            // Sound and effects
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0f, 1.5f);
            level.playSound(null, pos, SoundEvents.ENDER_DRAGON_HURT, SoundSource.BLOCKS, 0.5f, 1.2f);
            
            String coreName = isMainCore ? "メインコア" : "サブコア";
            player.sendMessage(new TextComponent("§c§l" + coreName + "にダメージを与えました！ (HP: " + newHp + "/" + getMaxHp(isSubCore) + ")"), player.getUUID());
            
            // Update boss bars immediately
            updateBossBarsForCore(level, pos, isSubCore);
        } else {
            // Last hit! Let the block break normally
            removeHp(pos);
            // Clean up player boss bars viewing this core
            long posLong = pos.asLong();
            for (UUID pUuid : new java.util.ArrayList<>(PLAYER_CURRENT_CORE.keySet())) {
                if (Long.valueOf(posLong).equals(PLAYER_CURRENT_CORE.get(pUuid))) {
                    removeBossBarForPlayer(pUuid);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level.isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // Run check every 5 ticks for smooth UI updating
        if (player.tickCount % 5 != 0) return;

        ServerLevel level = player.getLevel();
        ChunkPos currentChunk = new ChunkPos(player.blockPosition());
        
        boolean[] outIsSubCore = new boolean[1];
        BlockPos corePos = getCoreInChunk(level, currentChunk, outIsSubCore);
        UUID playerUuid = player.getUUID();

        if (corePos != null) {
            boolean isSubCore = outIsSubCore[0];
            long coreLong = corePos.asLong();
            PLAYER_CURRENT_CORE.put(playerUuid, coreLong);

            // Fetch or create BossBar
            ServerBossEvent bossBar = PLAYER_BOSS_BARS.get(playerUuid);
            if (bossBar == null) {
                bossBar = new ServerBossEvent(
                        new TextComponent(""),
                        isSubCore ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED,
                        BossEvent.BossBarOverlay.PROGRESS
                );
                bossBar.addPlayer(player);
                PLAYER_BOSS_BARS.put(playerUuid, bossBar);
            }

            // Update BossBar details
            int hp = getHp(corePos, isSubCore);
            int maxHp = getMaxHp(isSubCore);
            float progress = (float) hp / maxHp;
            bossBar.setProgress(progress);

            TerritorySavedData data = TerritorySavedData.get(level);
            UUID ownerUuid = isSubCore ? data.getClaimedChunks().get(currentChunk.toLong()) : data.getCoreOwner(corePos);
            TerritorySavedData.PlayerState ownerState = ownerUuid != null ? data.getPlayers().get(ownerUuid) : null;
            String ownerName = ownerState != null ? ownerState.username : "不明";

            String title = (isSubCore ? "§b§lサブコア §7(防衛アンカー): §e" : "§c§lメインコア §7(聖域の核): §e") 
                    + ownerName + " §a[HP: " + hp + "/" + maxHp + "]";
            bossBar.setName(new TextComponent(title));
        } else {
            // No core in current chunk, remove BossBar if they have one
            if (PLAYER_CURRENT_CORE.containsKey(playerUuid)) {
                removeBossBarForPlayer(playerUuid);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            removeBossBarForPlayer(player.getUUID());
        }
    }

    private static void removeBossBarForPlayer(UUID playerUuid) {
        ServerBossEvent bossBar = PLAYER_BOSS_BARS.remove(playerUuid);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
        PLAYER_CURRENT_CORE.remove(playerUuid);
    }

    private static void updateBossBarsForCore(ServerLevel level, BlockPos corePos, boolean isSubCore) {
        long posLong = corePos.asLong();
        int hp = getHp(corePos, isSubCore);
        int maxHp = getMaxHp(isSubCore);
        float progress = (float) hp / maxHp;

        TerritorySavedData data = TerritorySavedData.get(level);
        ChunkPos chunkPos = new ChunkPos(corePos);
        UUID ownerUuid = isSubCore ? data.getClaimedChunks().get(chunkPos.toLong()) : data.getCoreOwner(corePos);
        TerritorySavedData.PlayerState ownerState = ownerUuid != null ? data.getPlayers().get(ownerUuid) : null;
        String ownerName = ownerState != null ? ownerState.username : "不明";

        String title = (isSubCore ? "§b§lサブコア §7(防衛アンカー): §e" : "§c§lメインコア §7(聖域の核): §e") 
                + ownerName + " §a[HP: " + hp + "/" + maxHp + "]";

        for (UUID playerUuid : PLAYER_CURRENT_CORE.keySet()) {
            if (Long.valueOf(posLong).equals(PLAYER_CURRENT_CORE.get(playerUuid))) {
                ServerBossEvent bossBar = PLAYER_BOSS_BARS.get(playerUuid);
                if (bossBar != null) {
                    bossBar.setProgress(progress);
                    bossBar.setName(new TextComponent(title));
                }
            }
        }
    }

    public static BlockPos getCoreInChunk(ServerLevel level, ChunkPos chunkPos, boolean[] outIsSubCore) {
        TerritorySavedData data = TerritorySavedData.get(level);
        
        // Check Main Cores
        for (long posLong : data.getCorePositions().keySet()) {
            BlockPos pos = BlockPos.of(posLong);
            if ((pos.getX() >> 4) == chunkPos.x && (pos.getZ() >> 4) == chunkPos.z) {
                outIsSubCore[0] = false;
                return pos;
            }
        }
        
        // Check Sub Cores
        for (TerritorySavedData.PlayerState state : data.getPlayers().values()) {
            for (BlockPos pos : state.subCores) {
                if ((pos.getX() >> 4) == chunkPos.x && (pos.getZ() >> 4) == chunkPos.z) {
                    outIsSubCore[0] = true;
                    return pos;
                }
            }
        }
        
        return null;
    }
}
