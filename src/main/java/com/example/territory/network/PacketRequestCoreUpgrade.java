package com.example.territory.network;

import com.example.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketRequestCoreUpgrade {
    private final BlockPos corePos;

    public PacketRequestCoreUpgrade(BlockPos corePos) {
        this.corePos = corePos;
    }

    public PacketRequestCoreUpgrade(FriendlyByteBuf buf) {
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
            UUID ownerUuid = data.getCoreOwner(corePos);

            if (ownerUuid == null) {
                player.sendMessage(new TextComponent("§cエラー: このコアの所有者データが見つかりません。"), player.getUUID());
                return;
            }

            if (!ownerUuid.equals(player.getUUID())) {
                player.sendMessage(new TextComponent("§cエラー: 自分のメインコアのみ強化できます！"), player.getUUID());
                return;
            }

            TerritorySavedData.PlayerState state = data.getPlayers().get(ownerUuid);
            if (state == null) return;

            int upgradeCost = 100;
            if (state.treasury < upgradeCost) {
                player.sendMessage(new TextComponent("§cゴールドが不足しています！ (必要: 100G, 所持: " + state.treasury + "G)"), player.getUUID());
                return;
            }

            // Perform upgrade
            state.treasury -= upgradeCost;
            state.debuffLevel += 1;
            data.setDirty();

            player.sendMessage(new TextComponent("§a✔ メインコアの防衛デバフをアップグレードしました！ (レベル: " + state.debuffLevel + ")"), player.getUUID());
            player.level.playSound(null, corePos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.2f);

            // Send sync packet back to update the open Screen in real-time
            ModMessages.sendToPlayer(new PacketSyncCoreInfo(
                    corePos,
                    state.username,
                    state.teamColor,
                    state.debuffLevel,
                    state.treasury,
                    state.totalIronSold,
                    state.totalGoldSold,
                    state.totalEmeraldSold
            ), player);
        });
        return true;
    }
}
