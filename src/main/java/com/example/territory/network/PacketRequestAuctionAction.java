package com.example.territory.network;

import com.example.territory.TerritorySavedData;
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

public class PacketRequestAuctionAction {
    public enum Action {
        SELL,
        BUY,
        CANCEL
    }

    private final BlockPos corePos;
    private final Action action;
    private final int price; // For SELL
    private final int auctionId; // For BUY, CANCEL

    public PacketRequestAuctionAction(BlockPos corePos, Action action, int price, int auctionId) {
        this.corePos = corePos;
        this.action = action;
        this.price = price;
        this.auctionId = auctionId;
    }

    public PacketRequestAuctionAction(FriendlyByteBuf buf) {
        this.corePos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        this.price = buf.readInt();
        this.auctionId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeEnum(action);
        buf.writeInt(price);
        buf.writeInt(auctionId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            TerritorySavedData data = TerritorySavedData.get(player.getLevel());
            TerritorySavedData.PlayerState state = data.getPlayers().get(player.getUUID());
            if (state == null) return;

            if (action == Action.SELL) {
                // Sell hand item
                ItemStack handItem = player.getMainHandItem();
                if (handItem.isEmpty()) {
                    player.sendMessage(new TextComponent("§cエラー: メインハンドにアイテムを持っていません。"), player.getUUID());
                    return;
                }
                if (price <= 0) {
                    player.sendMessage(new TextComponent("§cエラー: 希望価格は1G以上に設定してください。"), player.getUUID());
                    return;
                }
                // Count current active listings
                long activeListings = data.getAuctionList().stream()
                        .filter(entry -> entry.sellerUuid.equals(player.getUUID()))
                        .count();
                if (activeListings >= 5) {
                    player.sendMessage(new TextComponent("§cエラー: 同時に出品できるのは最大5スロットまでです。"), player.getUUID());
                    return;
                }

                // Remove from player inventory
                ItemStack listingStack = handItem.copy();
                handItem.setCount(0); // Drain main hand

                int nextId = data.getNextAuctionId();
                TerritorySavedData.AuctionEntry entry = new TerritorySavedData.AuctionEntry(
                        nextId,
                        player.getUUID(),
                        player.getScoreboardName(),
                        listingStack,
                        price
                );
                data.getAuctionList().add(entry);
                data.setDirty();

                player.sendMessage(new TextComponent("§a✔ " + listingStack.getHoverName().getString() + " × " + listingStack.getCount() + " を " + price + "G で出品しました！"), player.getUUID());
                player.level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1.0f, 1.0f);

            } else if (action == Action.CANCEL) {
                // Cancel listing
                TerritorySavedData.AuctionEntry targetEntry = null;
                for (TerritorySavedData.AuctionEntry entry : data.getAuctionList()) {
                    if (entry.id == auctionId) {
                        targetEntry = entry;
                        break;
                    }
                }

                if (targetEntry == null) {
                    player.sendMessage(new TextComponent("§cエラー: 指定された出品データが見つかりません。"), player.getUUID());
                    return;
                }

                if (!targetEntry.sellerUuid.equals(player.getUUID())) {
                    player.sendMessage(new TextComponent("§cエラー: 他人の出品を取り消すことはできません！"), player.getUUID());
                    return;
                }

                // Return item to player inventory or drop
                ItemStack returnedStack = targetEntry.itemStack.copy();
                if (!player.getInventory().add(returnedStack)) {
                    player.drop(returnedStack, false);
                }

                data.getAuctionList().remove(targetEntry);
                data.setDirty();

                player.sendMessage(new TextComponent("§e✔ 出品を取り消し、アイテムを回収しました。"), player.getUUID());
                player.level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);

            } else if (action == Action.BUY) {
                // Buy listing
                TerritorySavedData.AuctionEntry targetEntry = null;
                for (TerritorySavedData.AuctionEntry entry : data.getAuctionList()) {
                    if (entry.id == auctionId) {
                        targetEntry = entry;
                        break;
                    }
                }

                if (targetEntry == null) {
                    player.sendMessage(new TextComponent("§cエラー: 指定されたアイテムはすでに売り切れたか、取り消されました。"), player.getUUID());
                    return;
                }

                if (targetEntry.sellerUuid.equals(player.getUUID())) {
                    player.sendMessage(new TextComponent("§cエラー: 自分の出品アイテムを購入することはできません。取り消してください。"), player.getUUID());
                    return;
                }

                int finalPrice = targetEntry.price;
                if (state.treasury < finalPrice) {
                    player.sendMessage(new TextComponent("§cエラー: 国庫金が不足しています！ (所持: " + state.treasury + "G, 価格: " + finalPrice + "G)"), player.getUUID());
                    return;
                }

                // Perform transaction
                state.treasury -= finalPrice;
                state.addHistory("-" + finalPrice + "G", "外交交易購入 (" + targetEntry.itemStack.getHoverName().getString() + " x" + targetEntry.itemStack.getCount() + ", 売り手: " + targetEntry.sellerUsername + ")");

                // Calculate tax for vassal seller
                TerritorySavedData.PlayerState sellerState = data.getPlayers().get(targetEntry.sellerUuid);
                int finalReward = finalPrice;
                int tax = 0;
                TerritorySavedData.PlayerState sellerOverlordState = null;

                if (sellerState != null) {
                    if (sellerState.isVassal && !sellerState.isRebelling && sellerState.overlordUuid != null) {
                        sellerOverlordState = data.getPlayers().get(sellerState.overlordUuid);
                        if (sellerOverlordState != null) {
                            tax = (int) (finalPrice * sellerState.vassalTaxRate);
                            finalReward = finalPrice - tax;
                        }
                    }
                    sellerState.treasury += finalReward;
                    sellerState.addHistory("+" + finalReward + "G", "外交交易売上 (" + targetEntry.itemStack.getHoverName().getString() + " x" + targetEntry.itemStack.getCount() + ", 買い手: " + state.username + ")");
                    
                    if (sellerOverlordState != null && tax > 0) {
                        sellerOverlordState.treasury += tax;
                        sellerState.addHistory("-" + tax + "G", "属国上納金 (" + sellerOverlordState.username + " へ送金 / 外交交易売上より)");
                        sellerOverlordState.addHistory("+" + tax + "G", "属国上納金受領 (" + sellerState.username + " の外交交易売上より)");
                        
                        ServerPlayer overlordPlayer = player.getLevel().getServer().getPlayerList().getPlayer(sellerState.overlordUuid);
                        if (overlordPlayer != null) {
                            overlordPlayer.sendMessage(new TextComponent(
                                    "§e§l[税金徴収] 属国 " + sellerState.username + " の外交交易売上から上納金 " + tax + "G が国庫に納付されました！"
                            ), sellerState.overlordUuid);
                        }
                    }

                    // Notify online seller
                    ServerPlayer sellerPlayer = player.getLevel().getServer().getPlayerList().getPlayer(targetEntry.sellerUuid);
                    if (sellerPlayer != null) {
                        if (sellerOverlordState != null && tax > 0) {
                            sellerPlayer.sendMessage(new TextComponent("§a✔ §l[外交交易] §eあなたの出品した " + targetEntry.itemStack.getHoverName().getString() + " × " + targetEntry.itemStack.getCount() + " が " + state.username + " に購入されました！ (売上: +" + finalReward + "G, 上納金: -" + tax + "G)"), targetEntry.sellerUuid);
                        } else {
                            sellerPlayer.sendMessage(new TextComponent("§a✔ §l[外交交易] §eあなたの出品した " + targetEntry.itemStack.getHoverName().getString() + " × " + targetEntry.itemStack.getCount() + " が " + state.username + " に購入されました！ (売上: +" + finalReward + "G)"), targetEntry.sellerUuid);
                        }
                        sellerPlayer.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                    }
                }

                // Give item to buyer
                ItemStack boughtStack = targetEntry.itemStack.copy();
                if (!player.getInventory().add(boughtStack)) {
                    player.drop(boughtStack, false);
                }

                // Clean up listing
                data.getAuctionList().remove(targetEntry);
                data.setDirty();

                player.sendMessage(new TextComponent("§a✔ §l[外交交易] §e" + targetEntry.sellerUsername + " から " + boughtStack.getHoverName().getString() + " × " + boughtStack.getCount() + " を " + finalPrice + "G で購入しました！"), player.getUUID());
                player.level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, 1.0f, 1.0f);
            }

            // Sync updated info and auction list back
            syncDataToAll(player.getLevel(), data, corePos);
        });
        return true;
    }

    private void syncDataToAll(net.minecraft.world.level.Level level, TerritorySavedData data, BlockPos corePos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        
        // 1. Sync updated auction list to everyone online
        PacketSyncAuctionList syncAuctionPacket = new PacketSyncAuctionList(data.getAuctionList());
        for (ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
            ModMessages.sendToPlayer(syncAuctionPacket, sp);
            // Also sync player state
            TerritorySavedData.PlayerState spState = data.getPlayers().get(sp.getUUID());
            if (spState != null) {
                ModMessages.sendToPlayer(new PacketSyncCoreInfo(corePos, spState), sp);
            }
        }
    }
}
