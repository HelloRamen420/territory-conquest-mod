package com.example.territory.network;

import com.example.territory.TerritorySavedData;
import com.example.territory.util.TradeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketRequestTrade {
    private final BlockPos corePos;
    private final TradeManager.TradeItem tradeItem;
    private final int tradeCount;

    public PacketRequestTrade(BlockPos corePos, TradeManager.TradeItem tradeItem, int tradeCount) {
        this.corePos = corePos;
        this.tradeItem = tradeItem;
        this.tradeCount = tradeCount;
    }

    public PacketRequestTrade(FriendlyByteBuf buf) {
        this.corePos = buf.readBlockPos();
        this.tradeItem = buf.readEnum(TradeManager.TradeItem.class);
        this.tradeCount = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeEnum(tradeItem);
        buf.writeInt(tradeCount);
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

            if (tradeCount <= 0) return;

            TradeManager.TradeRate rate = TradeManager.getCurrentRate(tradeItem, state);
            Item mcItem = getMcItem(tradeItem);
            
            int totalRequiredItems = rate.requiredItems * tradeCount;
            int totalReward = rate.goldReward * tradeCount;

            int itemCount = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() == mcItem) {
                    itemCount += stack.getCount();
                }
            }

            if (itemCount < totalRequiredItems) {
                player.sendMessage(new TextComponent("§cアイテムが不足しています！ (必要: " + totalRequiredItems + "個)"), player.getUUID());
                return;
            }

            // Consume items
            int remainingToConsume = totalRequiredItems;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack stack = player.getInventory().items.get(i);
                if (stack.getItem() == mcItem) {
                    int consume = Math.min(stack.getCount(), remainingToConsume);
                    stack.shrink(consume);
                    remainingToConsume -= consume;
                    if (remainingToConsume <= 0) break;
                }
            }

            // Add gold and track stats
            state.treasury += totalReward;
            switch (tradeItem) {
                case IRON: state.totalIronSold += totalRequiredItems; break;
                case GOLD: state.totalGoldSold += totalRequiredItems; break;
                case EMERALD: state.totalEmeraldSold += totalRequiredItems; break;
                default: break; // Others don't change rates, but we could track if wanted
            }
            data.setDirty();

            player.sendMessage(new TextComponent("§a✔ 交易成功！ (+" + totalReward + "G, 現在: " + state.treasury + "G)"), player.getUUID());
            player.level.playSound(null, corePos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.2f);

            // Sync updated state back to client to refresh screen in real-time
            if (corePos.equals(state.subCores.contains(corePos) ? corePos : null)) {
                ModMessages.sendToPlayer(new PacketSyncSubCoreInfo(
                        corePos,
                        state.username,
                        state.teamColor,
                        state.treasury,
                        state.totalIronSold,
                        state.totalGoldSold,
                        state.totalEmeraldSold
                ), player);
            } else {
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
            }
        });
        return true;
    }

    private Item getMcItem(TradeManager.TradeItem item) {
        switch (item) {
            case IRON: return Items.IRON_INGOT;
            case GOLD: return Items.GOLD_INGOT;
            case DIAMOND: return Items.DIAMOND;
            case EMERALD: return Items.EMERALD;
            case BOOK: return Items.BOOK;
            case HAY_BLOCK: return Items.HAY_BLOCK;
            case NETHERITE_INGOT: return Items.NETHERITE_INGOT;
            case NETHER_STAR: return Items.NETHER_STAR;
            default: return Items.AIR;
        }
    }
}
