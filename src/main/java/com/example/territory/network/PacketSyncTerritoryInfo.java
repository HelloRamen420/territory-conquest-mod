package com.example.territory.network;

import com.example.territory.client.gui.TerritoryStatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketSyncTerritoryInfo {
    public final String teamColor;
    public final boolean isVassal;
    public final String overlordName;
    public final int claimedCount;
    public final int vassalCount;
    public final List<String> vassals;
    public final int treasury;
    public final List<String> chunkList;

    public PacketSyncTerritoryInfo(String teamColor, boolean isVassal, String overlordName, int claimedCount,
                                   int vassalCount, List<String> vassals, int treasury, List<String> chunkList) {
        this.teamColor = teamColor;
        this.isVassal = isVassal;
        this.overlordName = overlordName;
        this.claimedCount = claimedCount;
        this.vassalCount = vassalCount;
        this.vassals = vassals;
        this.treasury = treasury;
        this.chunkList = chunkList;
    }

    public PacketSyncTerritoryInfo(FriendlyByteBuf buf) {
        this.teamColor = buf.readUtf();
        this.isVassal = buf.readBoolean();
        this.overlordName = buf.readUtf();
        this.claimedCount = buf.readInt();
        this.vassalCount = buf.readInt();
        
        int vSize = buf.readInt();
        this.vassals = new ArrayList<>();
        for (int i = 0; i < vSize; i++) {
            this.vassals.add(buf.readUtf());
        }

        this.treasury = buf.readInt();

        int cSize = buf.readInt();
        this.chunkList = new ArrayList<>();
        for (int i = 0; i < cSize; i++) {
            this.chunkList.add(buf.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(teamColor);
        buf.writeBoolean(isVassal);
        buf.writeUtf(overlordName);
        buf.writeInt(claimedCount);
        buf.writeInt(vassalCount);
        
        buf.writeInt(vassals.size());
        for (String vassal : vassals) {
            buf.writeUtf(vassal);
        }

        buf.writeInt(treasury);

        buf.writeInt(chunkList.size());
        for (String chunk : chunkList) {
            buf.writeUtf(chunk);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAccess.openScreen(this));
        });
        return true;
    }

    private static class ClientAccess {
        public static void openScreen(PacketSyncTerritoryInfo packet) {
            Minecraft.getInstance().setScreen(new TerritoryStatusScreen(packet));
        }
    }
}
