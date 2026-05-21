package com.example.territory.block;

import com.example.territory.TerritorySavedData;
import com.example.territory.network.ModMessages;
import com.example.territory.network.PacketSyncCoreInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/**
 * The Core Block is the heart of each player's territory.
 * - Placed automatically on first join (in the sanctuary)
 * - Right-clicking shows territory/player info
 * - Cannot be broken by others (enforced by event)
 */
public class CoreBlock extends Block {

    public CoreBlock(Properties properties) {
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

        // Find which player owns this core
        UUID ownerUuid = data.getCoreOwner(pos);
        if (ownerUuid == null) {
            player.sendMessage(new TextComponent("§cこのコアはどのプレイヤーにも属していません。"), player.getUUID());
            return InteractionResult.CONSUME;
        }

        TerritorySavedData.PlayerState ownerState = data.getPlayers().get(ownerUuid);
        if (ownerState == null) {
            player.sendMessage(new TextComponent("§cコアオーナーのデータが見つかりません。"), player.getUUID());
            return InteractionResult.CONSUME;
        }

        // Open Core Block GUI Screen
        if (player instanceof ServerPlayer serverPlayer) {
            ModMessages.sendToPlayer(new PacketSyncCoreInfo(
                    pos,
                    ownerState.username,
                    ownerState.teamColor,
                    ownerState.debuffLevel,
                    ownerState.treasury,
                    ownerState.totalIronSold,
                    ownerState.totalGoldSold,
                    ownerState.totalEmeraldSold
            ), serverPlayer);
        }

        return InteractionResult.CONSUME;
    }
}
