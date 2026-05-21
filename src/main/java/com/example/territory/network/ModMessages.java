package com.example.territory.network;

import com.example.territory.TerritoryConquest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(TerritoryConquest.MOD_ID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        // Register packets
        net.messageBuilder(PacketRequestTerritoryInfo.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketRequestTerritoryInfo::new)
                .encoder(PacketRequestTerritoryInfo::toBytes)
                .consumer(PacketRequestTerritoryInfo::handle)
                .add();

        net.messageBuilder(PacketSyncTerritoryInfo.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketSyncTerritoryInfo::new)
                .encoder(PacketSyncTerritoryInfo::toBytes)
                .consumer(PacketSyncTerritoryInfo::handle)
                .add();

        net.messageBuilder(PacketSyncCoreInfo.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketSyncCoreInfo::new)
                .encoder(PacketSyncCoreInfo::toBytes)
                .consumer(PacketSyncCoreInfo::handle)
                .add();

        net.messageBuilder(PacketRequestCoreUpgrade.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketRequestCoreUpgrade::new)
                .encoder(PacketRequestCoreUpgrade::toBytes)
                .consumer(PacketRequestCoreUpgrade::handle)
                .add();

        net.messageBuilder(PacketSyncSubCoreInfo.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketSyncSubCoreInfo::new)
                .encoder(PacketSyncSubCoreInfo::toBytes)
                .consumer(PacketSyncSubCoreInfo::handle)
                .add();

        net.messageBuilder(PacketRequestDepositGold.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketRequestDepositGold::new)
                .encoder(PacketRequestDepositGold::toBytes)
                .consumer(PacketRequestDepositGold::handle)
                .add();

        net.messageBuilder(PacketRequestPurchase.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketRequestPurchase::new)
                .encoder(PacketRequestPurchase::toBytes)
                .consumer(PacketRequestPurchase::handle)
                .add();

        net.messageBuilder(PacketRequestTrade.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketRequestTrade::new)
                .encoder(PacketRequestTrade::toBytes)
                .consumer(PacketRequestTrade::handle)
                .add();

    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
