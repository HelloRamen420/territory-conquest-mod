package com.example.territory.event;

import com.example.territory.TerritoryConquest;
import com.example.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID)
public class TerritoryDebuffEventHandler {

    // クールダウン管理: 領主UUID -> 最後にアラートを鳴らしたシステムミリ秒
    private static final Map<UUID, Long> ALERT_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 30000; // 30秒

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (event.player.level.isClientSide()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        if (player.isCreative() || player.isSpectator()) return;

        ServerLevel level = (ServerLevel) player.level;
        TerritorySavedData data = TerritorySavedData.get(level);
        UUID playerUuid = player.getUUID();

        ChunkPos currentChunk = new ChunkPos(player.blockPosition());
        long chunkLong = currentChunk.toLong();

        // 1. デバフ適用ロジック
        // プレイヤーが防衛デバフ範囲内にいるかをチェック
        // 全てのプレイヤーの国（メインコア）のデバフ範囲内に自分がいるかループ判定
        for (Map.Entry<UUID, TerritorySavedData.PlayerState> entry : data.getPlayers().entrySet()) {
            UUID ownerUuid = entry.getKey();
            TerritorySavedData.PlayerState ownerState = entry.getValue();

            // 自分の領土ならデバフ免除
            if (ownerUuid.equals(playerUuid)) continue;

            // 自分が宗主国で、相手が自分の属国ならデバフ免除
            if (ownerState.isVassal && playerUuid.equals(ownerState.overlordUuid)) continue;

            // メインコアのチャンク座標を取得
            int coreChunkX = ownerState.spawnX >> 4;
            int coreChunkZ = ownerState.spawnZ >> 4;

            // チェビシェフ距離を計算
            int dist = Math.max(Math.abs(currentChunk.x - coreChunkX), Math.abs(currentChunk.z - coreChunkZ));
            int maxRange = 3 + ownerState.debuffRangeUpgrade;

            if (dist <= maxRange) {
                // デバフ適用範囲内！10ticks（0.5秒）ごとにポーション効果を付与
                if (player.tickCount % 10 == 0) {
                    applyDebuffs(player, ownerState);
                }
            }
        }

        // 2. アラート検知ロジック
        // 侵入したチャンクが誰かの領土（聖域または開拓チャンク）であるか
        UUID claimOwnerUuid = data.getClaimedChunks().get(chunkLong);
        if (claimOwnerUuid == null) {
            // チャンクが登録されていなくても、聖域の可能性があるためチェック
            for (Map.Entry<UUID, TerritorySavedData.PlayerState> entry : data.getPlayers().entrySet()) {
                TerritorySavedData.PlayerState state = entry.getValue();
                ChunkPos sanctuaryChunk = new ChunkPos(state.spawnX >> 4, state.spawnZ >> 4);
                if (sanctuaryChunk.equals(currentChunk)) {
                    claimOwnerUuid = entry.getKey();
                    break;
                }
            }
        }

        if (claimOwnerUuid != null && !claimOwnerUuid.equals(playerUuid)) {
            TerritorySavedData.PlayerState ownerState = data.getPlayers().get(claimOwnerUuid);
            // 自分が相手の宗主国ならアラートは鳴らない
            boolean isOverlord = ownerState != null && ownerState.isVassal && playerUuid.equals(ownerState.overlordUuid);

            if (ownerState != null && ownerState.hasAlertUpgrade && !isOverlord) {
                // オンラインの所有者を取得
                ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(claimOwnerUuid);
                if (ownerPlayer != null) {
                    long now = System.currentTimeMillis();
                    long lastAlert = ALERT_COOLDOWNS.getOrDefault(claimOwnerUuid, 0L);
                    if (now - lastAlert >= COOLDOWN_MS) {
                        ALERT_COOLDOWNS.put(claimOwnerUuid, now);

                        // 画面中央に警告タイトルを表示
                        ownerPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                        ownerPlayer.connection.send(new ClientboundSetTitleTextPacket(new TextComponent("§c§l⚠ 侵入者検知！")));
                        
                        // チャット通知
                        ownerPlayer.sendMessage(
                                new TextComponent("§c§l⚠ [防衛警告] 領土（座標: " + player.blockPosition().toShortString() + "）に敵対プレイヤー " + player.getScoreboardName() + " が侵入しました！"),
                                claimOwnerUuid
                        );

                        // Witherの咆哮音を再生
                        ownerPlayer.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0F, 1.0F);
                    }
                }
            }
        }
    }

    private static void applyDebuffs(ServerPlayer player, TerritorySavedData.PlayerState ownerState) {
        // 1. 移動速度低下 (Slowness)
        if (ownerState.slownessLevel == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 0, false, false, true));
        } else if (ownerState.slownessLevel == 2) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1, false, false, true));
        } else if (ownerState.slownessLevel >= 3) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 3, false, false, true));
        }

        // 2. 弱体化 (Weakness)
        if (ownerState.weaknessLevel == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 0, false, false, true));
        } else if (ownerState.weaknessLevel >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 2, false, false, true));
        }

        // 3. 採掘速度低下 (Mining Fatigue)
        if (ownerState.miningFatigueLevel == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 25, 0, false, false, true));
        } else if (ownerState.miningFatigueLevel == 2) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 25, 1, false, false, true));
        } else if (ownerState.miningFatigueLevel >= 3) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 25, 3, false, false, true));
        }

        // 4. 吐き気 (空腹 Hunger)
        if (ownerState.hungerLevel == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 25, 0, false, false, true));
        } else if (ownerState.hungerLevel >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 25, 2, false, false, true));
        }

        // 5. 虫食い (毒 Poison)
        if (ownerState.poisonLevel >= 1) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 25, 0, false, false, true));
        }
    }
}
