package com.example.territory.network;

import com.example.territory.client.gui.SubCoreScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Syncs subcore stats to opening screen client-side.
 */
public class PacketSyncSubCoreInfo {
    public final BlockPos corePos;
    public final String ownerName;
    public final String teamColor;
    public final int treasury;
    public final int totalIronSold;
    public final int totalGoldSold;
    public final int totalEmeraldSold;
    public final boolean doubleTradeLicense;

    public PacketSyncSubCoreInfo(BlockPos corePos, String ownerName, String teamColor, int treasury, int ironSold, int goldSold, int emeraldSold, boolean doubleTradeLicense) {
        this.corePos = corePos;
        this.ownerName = ownerName;
        this.teamColor = teamColor;
        this.treasury = treasury;
        this.totalIronSold = ironSold;
        this.totalGoldSold = goldSold;
        this.totalEmeraldSold = emeraldSold;
        this.doubleTradeLicense = doubleTradeLicense;
    }

    public PacketSyncSubCoreInfo(FriendlyByteBuf buf) {
        this.corePos = buf.readBlockPos();
        this.ownerName = buf.readUtf();
        this.teamColor = buf.readUtf();
        this.treasury = buf.readInt();
        this.totalIronSold = buf.readInt();
        this.totalGoldSold = buf.readInt();
        this.totalEmeraldSold = buf.readInt();
        this.doubleTradeLicense = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeUtf(ownerName);
        buf.writeUtf(teamColor);
        buf.writeInt(treasury);
        buf.writeInt(totalIronSold);
        buf.writeInt(totalGoldSold);
        buf.writeInt(totalEmeraldSold);
        buf.writeBoolean(doubleTradeLicense);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAccess.openScreen(this));
        });
        return true;
    }

    private static class ClientAccess {
        public static void openScreen(PacketSyncSubCoreInfo packet) {
            net.minecraft.client.gui.screens.Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof com.example.territory.client.gui.ExchangeScreen currentExchange) {
                currentExchange.updateData(packet.treasury, packet.totalIronSold, packet.totalGoldSold, packet.totalEmeraldSold, packet.doubleTradeLicense);
            } else {
                Minecraft.getInstance().setScreen(new SubCoreScreen(packet));
            }
        }
    }
}
