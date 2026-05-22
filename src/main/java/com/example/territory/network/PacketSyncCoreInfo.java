package com.example.territory.network;

import com.example.territory.TerritorySavedData;
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
    public final int debuffLevel; // Legacy
    public final int treasury;
    public final int totalIronSold;
    public final int totalGoldSold;
    public final int totalEmeraldSold;

    // --- New v1.1.0 status variables ---
    public final int slownessLevel;
    public final int weaknessLevel;
    public final int miningFatigueLevel;
    public final int hungerLevel;
    public final int poisonLevel;
    
    public final boolean isVassal;
    public final boolean isRebelling;
    public final double vassalTaxRate;
    
    public final int debuffRangeUpgrade;
    public final boolean hasAlertUpgrade;
    public final boolean doubleTradeLicense;
    public final int passiveIncomeCount;

    public PacketSyncCoreInfo(BlockPos corePos, TerritorySavedData.PlayerState state) {
        this(
            corePos,
            state.username,
            state.teamColor,
            state.debuffLevel,
            state.treasury,
            state.totalIronSold,
            state.totalGoldSold,
            state.totalEmeraldSold,
            state.slownessLevel,
            state.weaknessLevel,
            state.miningFatigueLevel,
            state.hungerLevel,
            state.poisonLevel,
            state.isVassal,
            state.isRebelling,
            state.vassalTaxRate,
            state.debuffRangeUpgrade,
            state.hasAlertUpgrade,
            state.doubleTradeLicense,
            state.passiveIncomeSubCores.size()
        );
    }

    public PacketSyncCoreInfo(BlockPos corePos, String ownerName, String teamColor, int debuffLevel, int treasury, 
                              int ironSold, int goldSold, int emeraldSold,
                              int slownessLevel, int weaknessLevel, int miningFatigueLevel, int hungerLevel, int poisonLevel,
                              boolean isVassal, boolean isRebelling, double vassalTaxRate,
                              int debuffRangeUpgrade, boolean hasAlertUpgrade, boolean doubleTradeLicense, int passiveIncomeCount) {
        this.corePos = corePos;
        this.ownerName = ownerName;
        this.teamColor = teamColor;
        this.debuffLevel = debuffLevel;
        this.treasury = treasury;
        this.totalIronSold = ironSold;
        this.totalGoldSold = goldSold;
        this.totalEmeraldSold = emeraldSold;
        
        this.slownessLevel = slownessLevel;
        this.weaknessLevel = weaknessLevel;
        this.miningFatigueLevel = miningFatigueLevel;
        this.hungerLevel = hungerLevel;
        this.poisonLevel = poisonLevel;
        this.isVassal = isVassal;
        this.isRebelling = isRebelling;
        this.vassalTaxRate = vassalTaxRate;
        this.debuffRangeUpgrade = debuffRangeUpgrade;
        this.hasAlertUpgrade = hasAlertUpgrade;
        this.doubleTradeLicense = doubleTradeLicense;
        this.passiveIncomeCount = passiveIncomeCount;
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
        
        this.slownessLevel = buf.readInt();
        this.weaknessLevel = buf.readInt();
        this.miningFatigueLevel = buf.readInt();
        this.hungerLevel = buf.readInt();
        this.poisonLevel = buf.readInt();
        this.isVassal = buf.readBoolean();
        this.isRebelling = buf.readBoolean();
        this.vassalTaxRate = buf.readDouble();
        this.debuffRangeUpgrade = buf.readInt();
        this.hasAlertUpgrade = buf.readBoolean();
        this.doubleTradeLicense = buf.readBoolean();
        this.passiveIncomeCount = buf.readInt();
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
        
        buf.writeInt(slownessLevel);
        buf.writeInt(weaknessLevel);
        buf.writeInt(miningFatigueLevel);
        buf.writeInt(hungerLevel);
        buf.writeInt(poisonLevel);
        buf.writeBoolean(isVassal);
        buf.writeBoolean(isRebelling);
        buf.writeDouble(vassalTaxRate);
        buf.writeInt(debuffRangeUpgrade);
        buf.writeBoolean(hasAlertUpgrade);
        buf.writeBoolean(doubleTradeLicense);
        buf.writeInt(passiveIncomeCount);
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
                currentExchange.updateData(packet.treasury, packet.totalIronSold, packet.totalGoldSold, packet.totalEmeraldSold, packet.doubleTradeLicense);
            } else {
                Minecraft.getInstance().setScreen(new CoreBlockScreen(packet));
            }
        }
    }
}
