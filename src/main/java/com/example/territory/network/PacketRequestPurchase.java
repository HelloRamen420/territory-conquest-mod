package com.example.territory.network;

import com.example.territory.TerritorySavedData;
import com.example.territory.item.ModItems;
import com.example.territory.util.TradeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketRequestPurchase {
    private final BlockPos corePos;
    private final TradeManager.PurchaseItem purchaseItem;
    private final int amount;

    public PacketRequestPurchase(BlockPos corePos, TradeManager.PurchaseItem purchaseItem, int amount) {
        this.corePos = corePos;
        this.purchaseItem = purchaseItem;
        this.amount = amount;
    }

    public PacketRequestPurchase(FriendlyByteBuf buf) {
        this.corePos = buf.readBlockPos();
        this.purchaseItem = buf.readEnum(TradeManager.PurchaseItem.class);
        this.amount = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeEnum(purchaseItem);
        buf.writeInt(amount);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            TerritorySavedData data = TerritorySavedData.get(player.getLevel());

            // Use sender's own UUID — only the owner can open the exchange screen
            TerritorySavedData.PlayerState state = data.getPlayers().get(player.getUUID());
            if (state == null) return;

            if (amount <= 0) return;

            int pricePerItem = TradeManager.getPurchasePrice(purchaseItem);
            int totalCost = pricePerItem * amount;

            if (state.treasury < totalCost) {
                player.sendMessage(new TextComponent("§c国庫のゴールドが不足しています！ (必要: " + totalCost + "G, 現在: " + state.treasury + "G)"), player.getUUID());
                return;
            }

            // Deduct gold
            state.treasury -= totalCost;
            state.addHistory("-" + totalCost + "G", "アイテム購入 (" + purchaseItem.name() + " x" + amount + ")");
            data.setDirty();

            // Give items
            ItemStack stackToGive = ItemStack.EMPTY;
            if (purchaseItem == TradeManager.PurchaseItem.TERRITORY_FLAG) {
                stackToGive = new ItemStack(ModItems.TERRITORY_FLAG_ITEM.get(), amount);
            }

            if (!stackToGive.isEmpty()) {
                if (!player.getInventory().add(stackToGive)) {
                    player.drop(stackToGive, false); // Drop on floor if inventory is full
                }
            }

            player.sendMessage(new TextComponent("§a✔ 購入成功！ (-" + totalCost + "G, 残高: " + state.treasury + "G)"), player.getUUID());
            player.level.playSound(null, corePos, SoundEvents.NOTE_BLOCK_BELL, SoundSource.BLOCKS, 1.0f, 1.2f);

            // Sync updated state back to client
            boolean isMainCore = !state.subCores.contains(corePos);
            ModMessages.sendToPlayer(new PacketSyncCoreInfo(corePos, state, isMainCore), player);
        });
        return true;
    }
}
