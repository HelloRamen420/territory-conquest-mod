package com.example.territory.network;

import com.example.territory.client.gui.CoreBlockScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncCoreInfo {
    public final BlockPos corePos;
    public final String ownerName;
    public final String teamColor;
    public final int debuffLevel;
    public final int treasury;
    public final int totalIronSold;
    public final int totalGoldSold;
    public final int totalEmeraldSold;

    public PacketSyncCoreInfo(BlockPos corePos, String ownerName, String teamColor, int debuffLevel, int treasury, int ironSold, int goldSold, int emeraldSold) {
        this.corePos = corePos;
        this.ownerName = ownerName;
        this.teamColor = teamColor;
        this.debuffLevel = debuffLevel;
        this.treasury = treasury;
        this.totalIronSold = ironSold;
        this.totalGoldSold = goldSold;
        this.totalEmeraldSold = emeraldSold;
    }

    public PacketSyncCoreInfo(FriendlyByteBuf buf) {
        this.corePos = buf.readBlockPos();
        this.ownerName = buf.readUtf();
        this.teamColor = buf.readUtf();
        this.debuffLevel = buf.readInt();
        this.treasury = buf.readInt();
        this.totalIronSold = buf.readInt();
        this.totalGoldSold = buf.readInt();
        this.totalEmeraldSold = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeUtf(ownerName);
        buf.writeUtf(teamColor);
        buf.writeInt(debuffLevel);
        buf.writeInt(treasury);
        buf.writeInt(totalIronSold);
        buf.writeInt(totalGoldSold);
        buf.writeInt(totalEmeraldSold);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAccess.openScreen(this));
        });
        return true;
    }

    private static class ClientAccess {
        public static void openScreen(PacketSyncCoreInfo packet) {
            net.minecraft.client.gui.screens.Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof com.example.territory.client.gui.ExchangeScreen currentExchange) {
                currentExchange.updateData(packet.treasury, packet.totalIronSold, packet.totalGoldSold, packet.totalEmeraldSold);
            } else {
                Minecraft.getInstance().setScreen(new CoreBlockScreen(packet));
            }
        }
    }
}
