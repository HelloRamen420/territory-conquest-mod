package com.example.territory.network;

import com.example.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketRequestCoreUpgrade {

    public enum UpgradeType {
        SLOWNESS,
        WEAKNESS,
        MINING_FATIGUE,
        HUNGER,
        POISON,
        RANGE,
        ALERT,
        DOUBLE_TRADE,
        PASSIVE_INCOME,
        INDEPENDENCE
    }

    private final BlockPos corePos;
    private final UpgradeType upgradeType;
    private final BlockPos targetPos; // Subcore location if passive income, otherwise zero

    public PacketRequestCoreUpgrade(BlockPos corePos, UpgradeType type, BlockPos targetPos) {
        this.corePos = corePos;
        this.upgradeType = type;
        this.targetPos = targetPos != null ? targetPos : BlockPos.ZERO;
    }

    public PacketRequestCoreUpgrade(FriendlyByteBuf buf) {
        this.corePos = buf.readBlockPos();
        this.upgradeType = buf.readEnum(UpgradeType.class);
        this.targetPos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeEnum(upgradeType);
        buf.writeBlockPos(targetPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            TerritorySavedData data = TerritorySavedData.get(player.getLevel());
            UUID ownerUuid = data.getCoreOwner(corePos);

            if (ownerUuid == null) {
                player.sendMessage(new TextComponent("§cエラー: このコアの所有者データが見つかりません。"), player.getUUID());
                return;
            }

            if (!ownerUuid.equals(player.getUUID())) {
                player.sendMessage(new TextComponent("§cエラー: 自分のメインコアのみ操作できます！"), player.getUUID());
                return;
            }

            TerritorySavedData.PlayerState state = data.getPlayers().get(ownerUuid);
            if (state == null) return;

            int cost = 0;
            String msg = "";

            switch (upgradeType) {
                case SLOWNESS:
                    if (state.slownessLevel == 0) cost = 5000;
                    else if (state.slownessLevel == 1) cost = 15000;
                    else if (state.slownessLevel == 2) cost = 50000;
                    else {
                        player.sendMessage(new TextComponent("§c移動速度低下デバフはすでに最大ステージです！"), player.getUUID());
                        return;
                    }
                    break;
                case WEAKNESS:
                    if (state.weaknessLevel == 0) cost = 30000;
                    else if (state.weaknessLevel == 1) cost = 60000;
                    else {
                        player.sendMessage(new TextComponent("§c弱体化デバフはすでに最大ステージです！"), player.getUUID());
                        return;
                    }
                    break;
                case MINING_FATIGUE:
                    if (state.miningFatigueLevel == 0) cost = 50000;
                    else if (state.miningFatigueLevel == 1) cost = 100000;
                    else if (state.miningFatigueLevel == 2) cost = 200000;
                    else {
                        player.sendMessage(new TextComponent("§c採掘速度低下デバフはすでに最大ステージです！"), player.getUUID());
                        return;
                    }
                    break;
                case HUNGER:
                    if (state.hungerLevel == 0) cost = 5000;
                    else if (state.hungerLevel == 1) cost = 55000;
                    else {
                        player.sendMessage(new TextComponent("§c空腹デバフはすでに最大ステージです！"), player.getUUID());
                        return;
                    }
                    break;
                case POISON:
                    if (state.poisonLevel == 0) cost = 50000;
                    else {
                        player.sendMessage(new TextComponent("§c毒デバフ（虫食い）はすでに有効です！"), player.getUUID());
                        return;
                    }
                    break;
                case RANGE:
                    cost = 2000;
                    break;
                case ALERT:
                    if (state.hasAlertUpgrade) {
                        player.sendMessage(new TextComponent("§cアラート機能はすでに購入されています！"), player.getUUID());
                        return;
                    }
                    cost = 20000;
                    break;
                case DOUBLE_TRADE:
                    if (state.doubleTradeLicense) {
                        player.sendMessage(new TextComponent("§c売上2倍ライセンスはすでに購入されています！"), player.getUUID());
                        return;
                    }
                    cost = 50000;
                    break;
                case PASSIVE_INCOME:
                    if (targetPos == null || targetPos.equals(BlockPos.ZERO)) {
                        player.sendMessage(new TextComponent("§cエラー: 不労所得化するサブコアの位置が指定されていません。"), player.getUUID());
                        return;
                    }
                    if (!state.subCores.contains(targetPos)) {
                        player.sendMessage(new TextComponent("§cエラー: 指定された座標にあなたのサブコアが存在しません。"), player.getUUID());
                        return;
                    }
                    if (state.passiveIncomeSubCores.contains(targetPos)) {
                        player.sendMessage(new TextComponent("§cこのサブコアはすでに不労所得化されています！"), player.getUUID());
                        return;
                    }
                    cost = 100000;
                    break;
                case INDEPENDENCE:
                    if (!state.isVassal) {
                        player.sendMessage(new TextComponent("§cあなたは独立国です。独立金を支払う必要はありません。"), player.getUUID());
                        return;
                    }
                    cost = 500000;
                    break;
            }

            if (state.treasury < cost) {
                player.sendMessage(new TextComponent("§cゴールドが不足しています！ (必要: " + cost + "G, 所持: " + state.treasury + "G)"), player.getUUID());
                return;
            }

            // Deduct Gold
            state.treasury -= cost;

            // Apply Upgrade
            switch (upgradeType) {
                case SLOWNESS:
                    state.slownessLevel++;
                    msg = "§a✔ 移動速度低下デバフをアップグレードしました！ (ステージ: " + state.slownessLevel + ")";
                    break;
                case WEAKNESS:
                    state.weaknessLevel++;
                    msg = "§a✔ 弱体化デバフをアップグレードしました！ (ステージ: " + state.weaknessLevel + ")";
                    break;
                case MINING_FATIGUE:
                    state.miningFatigueLevel++;
                    msg = "§a✔ 採掘速度低下デバフをアップグレードしました！ (ステージ: " + state.miningFatigueLevel + ")";
                    break;
                case HUNGER:
                    state.hungerLevel++;
                    msg = "§a✔ 空腹デバフをアップグレードしました！ (ステージ: " + state.hungerLevel + ")";
                    break;
                case POISON:
                    state.poisonLevel = 1;
                    msg = "§a✔ 毒デバフ（虫食い）をアンロックしました！";
                    break;
                case RANGE:
                    state.debuffRangeUpgrade++;
                    msg = "§a✔ デバフ適用範囲を拡張しました！ (+1チャンク, 合計: 聖域隣接 " + (3 + state.debuffRangeUpgrade) + "チャンク)";
                    break;
                case ALERT:
                    state.hasAlertUpgrade = true;
                    msg = "§a✔ アラート機能を購入しました！侵入者が来るとWither咆哮音と警告が出ます。";
                    break;
                case DOUBLE_TRADE:
                    state.doubleTradeLicense = true;
                    msg = "§a✔ 売上2倍ライセンスを購入しました！交易売却額が永続的に2倍になります。";
                    break;
                case PASSIVE_INCOME:
                    state.passiveIncomeSubCores.add(targetPos);
                    msg = "§a✔ サブコアを自動ゴールド精製機に改造しました！ (5分ごとに50G)";
                    break;
                case INDEPENDENCE:
                    state.isVassal = false;
                    state.overlordUuid = null;
                    state.isRebelling = false;
                    state.vassalTaxRate = 0.25;
                    msg = "§a§l✔ 独立金を支払い、無条件独立を果たしました！";
                    // Broadcast independence
                    player.getLevel().getServer().getPlayerList().broadcastMessage(
                            new TextComponent("§a§l[独立] §e" + player.getScoreboardName() + " は独立金 500,000G を支払い、無条件独立を果たしました！"),
                            net.minecraft.network.chat.ChatType.SYSTEM,
                            net.minecraft.Util.NIL_UUID
                    );
                    }

            String historyReason = "";
            switch (upgradeType) {
                case SLOWNESS: historyReason = "デバフ強化 (移動速度低下 ステージ " + state.slownessLevel + ")"; break;
                case WEAKNESS: historyReason = "デバフ強化 (弱体化 ステージ " + state.weaknessLevel + ")"; break;
                case MINING_FATIGUE: historyReason = "デバフ強化 (採掘速度低下 ステージ " + state.miningFatigueLevel + ")"; break;
                case HUNGER: historyReason = "デバフ強化 (空腹 ステージ " + state.hungerLevel + ")"; break;
                case POISON: historyReason = "デバフ強化 (虫食いアンロック)"; break;
                case RANGE: historyReason = "国家強化 (デバフ範囲増加 +" + state.debuffRangeUpgrade + "c)"; break;
                case ALERT: historyReason = "国家強化 (侵入アラート)"; break;
                case DOUBLE_TRADE: historyReason = "国家強化 (売上2倍ライセンス)"; break;
                case PASSIVE_INCOME: historyReason = "国家強化 (サブコア不労所得化)"; break;
                case INDEPENDENCE: historyReason = "平和的独立 (独立金支払い)"; break;
            }
            state.addHistory("-" + cost + "G", historyReason);

            data.setDirty();
            player.sendMessage(new TextComponent(msg), player.getUUID());
            player.level.playSound(null, corePos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.2f);

            // Sync updated state back
            ModMessages.sendToPlayer(new PacketSyncCoreInfo(
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
            ), player);
        });
        return true;
    }
}
