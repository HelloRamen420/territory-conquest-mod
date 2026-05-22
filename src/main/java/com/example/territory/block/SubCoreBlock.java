package com.example.territory.block;

import com.example.territory.TerritorySavedData;
import com.example.territory.network.ModMessages;
import com.example.territory.network.PacketSyncSubCoreInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/**
 * SubCoreBlock acts as a defense anchor (shield generator) and economic hub (gold deposit & flag trading).
 */
public class SubCoreBlock extends Block {
    public SubCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        TerritorySavedData data = TerritorySavedData.get(serverLevel);

        // Find owner based on the chunk it is placed in
        ChunkPos chunkPos = new ChunkPos(pos);
        UUID ownerUuid = data.getClaimedChunks().get(chunkPos.toLong());

        if (ownerUuid == null) {
            player.sendMessage(new TextComponent("§cこのサブコアはどの領地にも属していません。"), player.getUUID());
            return InteractionResult.CONSUME;
        }

        TerritorySavedData.PlayerState ownerState = data.getPlayers().get(ownerUuid);
        if (ownerState == null) {
            player.sendMessage(new TextComponent("§c所有者データが見つかりません。"), player.getUUID());
            return InteractionResult.CONSUME;
        }

        // Open SubCore GUI Screen
        if (player instanceof ServerPlayer serverPlayer) {
            ModMessages.sendToPlayer(new PacketSyncSubCoreInfo(
                    pos,
                    ownerState.username,
                    ownerState.teamColor,
                    ownerState.treasury,
                    ownerState.totalIronSold,
                    ownerState.totalGoldSold,
                    ownerState.totalEmeraldSold,
                    ownerState.doubleTradeLicense
            ), serverPlayer);
        }

        return InteractionResult.CONSUME;
    }
}
