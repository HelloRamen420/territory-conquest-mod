package com.example.territory.network;

import com.example.territory.TerritorySavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketRequestTerritoryInfo {
    public PacketRequestTerritoryInfo() {}

    public PacketRequestTerritoryInfo(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            TerritorySavedData data = TerritorySavedData.get(player.getLevel());
            UUID uuid = player.getUUID();
            TerritorySavedData.PlayerState state = data.getPlayers().get(uuid);

            if (state != null) {
                int claimedCount = (int) data.getClaimedChunks().values().stream()
                        .filter(u -> u.equals(uuid)).count();

                List<String> chunkList = new ArrayList<>();
                data.getClaimedChunks().forEach((chunkPosLong, ownerUuid) -> {
                    if (ownerUuid.equals(uuid)) {
                        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(chunkPosLong);
                        chunkList.add("[" + cp.x + ", " + cp.z + "]");
                    }
                });

                // Find vassals
                List<String> vassals = new ArrayList<>();
                data.getPlayers().forEach((u, s) -> {
                    if (s.isVassal && uuid.equals(s.overlordUuid)) {
                        vassals.add(s.username);
                    }
                });

                String overlordName = "なし";
                if (state.isVassal && state.overlordUuid != null) {
                    TerritorySavedData.PlayerState oState = data.getPlayers().get(state.overlordUuid);
                    if (oState != null) {
                        overlordName = oState.username;
                    }
                }

                // Send sync packet back
                ModMessages.sendToPlayer(new PacketSyncTerritoryInfo(
                        state.teamColor,
                        state.isVassal,
                        overlordName,
                        claimedCount,
                        vassals.size(),
                        vassals,
                        state.treasury,
                        chunkList
                ), player);
            }
        });
        return true;
    }
}
