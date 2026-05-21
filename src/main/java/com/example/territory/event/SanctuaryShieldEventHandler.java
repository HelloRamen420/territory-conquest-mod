package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Handles the Sanctuary Shield logic.
 * Bounces enemy players back if they try to enter a sanctuary chunk while the owner has active subcores.
 */
@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class SanctuaryShieldEventHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only run on Server and at start of tick
        if (event.phase != TickEvent.Phase.START) return;
        if (event.player.level.isClientSide()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        
        // Creative and spectator players can bypass the shield for admin purposes
        if (player.isCreative() || player.isSpectator()) return;

        ServerLevel level = (ServerLevel) player.level;
        TerritorySavedData data = TerritorySavedData.get(level);

        ChunkPos currentChunk = new ChunkPos(player.blockPosition());
        long chunkLong = currentChunk.toLong();

        UUID ownerUuid = data.getClaimedChunks().get(chunkLong);
        if (ownerUuid == null) return;

        // If it's the player's own sanctuary, it's fine!
        if (ownerUuid.equals(player.getUUID())) return;

        TerritorySavedData.PlayerState ownerState = data.getPlayers().get(ownerUuid);
        if (ownerState == null) return;

        // Check if this chunk is the Sanctuary Chunk of the owner
        ChunkPos sanctuaryChunk = new ChunkPos(ownerState.spawnX >> 4, ownerState.spawnZ >> 4);
        if (!sanctuaryChunk.equals(currentChunk)) return;

        // If the owner has no active subcores, the shield is deactivated!
        if (ownerState.subCores.isEmpty()) return;

        // Intruder alert! The sanctuary shield is active.
        // Push the player back outwards, away from the core block
        double dx = player.getX() - (ownerState.spawnX + 0.5);
        double dz = player.getZ() - (ownerState.spawnZ + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist == 0) {
            dx = 1.0;
            dist = 1.0;
        }

        // Apply knockback velocity (away from sanctuary center)
        double pushForce = 1.5;
        double pushX = (dx / dist) * pushForce;
        double pushZ = (dz / dist) * pushForce;

        player.setDeltaMovement(pushX, 0.4, pushZ);
        player.hurtMarked = true; // Triggers packet sync to client for smooth motion

        // Warning message
        player.sendMessage(
                new TextComponent("§c§lシールド起動: §cこの聖域は保護されています！ (健在なサブコア: §e" + ownerState.subCores.size() + "個§c)"),
                player.getUUID()
        );

        // Sound effect at player's location
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS,
                1.0f,
                0.8f
        );
    }
}
