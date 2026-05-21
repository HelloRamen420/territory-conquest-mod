package com.example.territory.network;

import com.example.territory.TerritorySavedData;
import com.example.territory.client.ClientAuctionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketSyncAuctionList {
    private final List<TerritorySavedData.AuctionEntry> auctionList;

    public PacketSyncAuctionList(List<TerritorySavedData.AuctionEntry> auctionList) {
        this.auctionList = auctionList;
    }

    public PacketSyncAuctionList(FriendlyByteBuf buf) {
        this.auctionList = new ArrayList<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            int id = buf.readInt();
            UUID sellerUuid = buf.readUUID();
            String sellerUsername = buf.readUtf();
            ItemStack itemStack = buf.readItem();
            int price = buf.readInt();
            auctionList.add(new TerritorySavedData.AuctionEntry(id, sellerUuid, sellerUsername, itemStack, price));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(auctionList.size());
        for (TerritorySavedData.AuctionEntry entry : auctionList) {
            buf.writeInt(entry.id);
            buf.writeUUID(entry.sellerUuid);
            buf.writeUtf(entry.sellerUsername);
            buf.writeItem(entry.itemStack);
            buf.writeInt(entry.price);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // Update local client side auction list
            ClientAuctionData.setAuctionList(auctionList);
        });
        return true;
    }
}
