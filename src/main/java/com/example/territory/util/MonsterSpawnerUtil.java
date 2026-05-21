package com.example.territory.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

public class MonsterSpawnerUtil {
    private static final Random RANDOM = new Random();

    /**
     * スポーン候補位置を地形に沿って調整する。
     * ブロック内部や空中にスポーンしないよう、Heightmapで地表Y座標を取得する。
     */
    private static BlockPos findSafeSpawnPos(ServerLevel level, BlockPos center) {
        // 中心から -3〜+3 ブロックのランダムなオフセットを選ぶ
        int offX = RANDOM.nextInt(7) - 3;
        int offZ = RANDOM.nextInt(7) - 3;
        int candidateX = center.getX() + offX;
        int candidateZ = center.getZ() + offZ;

        // Heightmap で地表の Y 座標を取得（空気ブロックに接する最初のブロックの上）
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidateX, candidateZ);
        BlockPos spawnPos = new BlockPos(candidateX, surfaceY, candidateZ);

        // 取得した位置が空気（スポーンできる空間）かを確認
        if (level.getBlockState(spawnPos).isAir() || level.getBlockState(spawnPos.above()).isAir()) {
            return spawnPos;
        }

        // フォールバック：旗の1ブロック上
        return center.above();
    }

    /**
     * 指定された座標の周辺に、装備済みのゾンビ／スケルトンを count 体スポーンさせる。
     * ピースフルモードの場合はスポーンしない。
     */
    public static void spawnCustomMonsters(ServerLevel level, BlockPos pos, int count) {
        // ピースフルモードでは何もしない
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;

        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSafeSpawnPos(level, pos);

            if (RANDOM.nextBoolean()) {
                spawnZombie(level, spawnPos);
            } else {
                spawnSkeleton(level, spawnPos);
            }
        }
    }

    private static void spawnZombie(ServerLevel level, BlockPos spawnPos) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) return;

        zombie.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        // finalizeSpawn を呼んで AI などを初期化する
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);

        // 装備を上書き（finalizeSpawn が決めた装備を置き換える）
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 999999, 0, false, false));

        // 装備のドロップを無効化
        zombie.setDropChance(EquipmentSlot.HEAD, 0.0f);
        zombie.setDropChance(EquipmentSlot.CHEST, 0.0f);
        zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0f);

        // ナチュラルスポーン扱いにしないためフラグを設定
        zombie.setPersistenceRequired();

        level.addFreshEntityWithPassengers(zombie);
    }

    private static void spawnSkeleton(ServerLevel level, BlockPos spawnPos) {
        Skeleton skeleton = EntityType.SKELETON.create(level);
        if (skeleton == null) return;

        skeleton.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        // finalizeSpawn を呼んで AI などを初期化する
        skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);

        // 装備を上書き
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(Enchantments.PUNCH_ARROWS, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);

        // ドロップ無効化
        skeleton.setDropChance(EquipmentSlot.HEAD, 0.0f);
        skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);

        skeleton.setPersistenceRequired();

        level.addFreshEntityWithPassengers(skeleton);
    }
}
