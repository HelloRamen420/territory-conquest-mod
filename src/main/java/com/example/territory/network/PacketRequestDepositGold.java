package com.example.territory.network;

import com.example.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import com.example.territory.block.ModBlocks;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Handles the gold deposit (conversion) request from the client.
 * Scans player inventory for Gold Nuggets (1G), Ingots (9G), and Blocks (81G),
 * consumes them, and adds the total to the player's treasury.
 */
public class PacketRequestDepositGold {
    private final BlockPos corePos;

    public PacketRequestDepositGold(BlockPos corePos) {
        this.corePos = corePos;
    }

    public PacketRequestDepositGold(FriendlyByteBuf buf) {
        this.corePos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            TerritorySavedData data = TerritorySavedData.get(player.getLevel());
            ChunkPos chunkPos = new ChunkPos(corePos);
            UUID ownerUuid = data.getClaimedChunks().get(chunkPos.toLong());

            if (ownerUuid == null) {
                player.sendMessage(new TextComponent("§cエラー: サブコアの領土所有者データが見つかりません。"), player.getUUID());
                return;
            }

            if (!ownerUuid.equals(player.getUUID())) {
                player.sendMessage(new TextComponent("§cエラー: 自分のサブコアにのみ金を預けることができます！"), player.getUUID());
                return;
            }

            TerritorySavedData.PlayerState state = data.getPlayers().get(ownerUuid);
            if (state == null) return;

            // Scan player's inventory for gold items
            Inventory inventory = player.getInventory();
            int totalGoldGained = 0;

            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(Items.GOLD_NUGGET)) {
                    totalGoldGained += stack.getCount() * 1;
                    stack.setCount(0);
                } else if (stack.is(Items.GOLD_INGOT)) {
                    totalGoldGained += stack.getCount() * 9;
                    stack.setCount(0);
                } else if (stack.is(Items.GOLD_BLOCK)) {
                    totalGoldGained += stack.getCount() * 81;
                    stack.setCount(0);
                }
            }

            if (totalGoldGained == 0) {
                player.sendMessage(new TextComponent("§c手持ちに納品できる金（金塊、金インゴット、金ブロック）がありません。"), player.getUUID());
                return;
            }

            // Apply gold deposit
            state.treasury += totalGoldGained;
            data.setDirty();

            player.sendMessage(new TextComponent("§a✔ 国庫に金を納品しました！ (+" + totalGoldGained + "G, 現在: " + state.treasury + "G)"), player.getUUID());
            player.level.playSound(null, corePos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.2f);

            // Sync the updated state back to client to refresh screen in real-time
            BlockState blockState = player.level.getBlockState(corePos);
            if (blockState.is(ModBlocks.CORE_BLOCK.get())) {
                ModMessages.sendToPlayer(new PacketSyncCoreInfo(corePos, state), player);
            } else {
                ModMessages.sendToPlayer(new PacketSyncSubCoreInfo(
                        corePos,
                        state.username,
                        state.teamColor,
                        state.treasury,
                        state.totalIronSold,
                        state.totalGoldSold,
                        state.totalEmeraldSold,
                        state.doubleTradeLicense
                ), player);
            }
        });
        return true;
    }
}
