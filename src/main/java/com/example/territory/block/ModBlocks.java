package com.example.territory.block;

import com.example.territory.TerritoryConquest;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TerritoryConquest.MOD_ID);

    public static final RegistryObject<Block> CORE_BLOCK = BLOCKS.register("core_block",
            () -> new CoreBlock(BlockBehaviour.Properties.of(Material.METAL)
                    .strength(50.0f, 1200.0f)  // Very hard to break
                    .lightLevel(state -> 15)    // Emits full light (like beacon)
                    .requiresCorrectToolForDrops()
            ));

    public static final RegistryObject<Block> SUB_CORE_BLOCK = BLOCKS.register("sub_core_block",
            () -> new SubCoreBlock(BlockBehaviour.Properties.of(Material.METAL)
                    .strength(20.0f, 600.0f)   // Tough but easier to destroy than core
                    .lightLevel(state -> 10)   // Crying obsidian soft glow
                    .requiresCorrectToolForDrops()
            ));

    public static final RegistryObject<Block> TERRITORY_FLAG = BLOCKS.register("territory_flag",
            () -> new TerritoryFlagBlock(BlockBehaviour.Properties.of(Material.DECORATION)
                    .noCollission()            // Torch-like no collision
                    .instabreak()              // Instantly breakable
                    .lightLevel((state) -> 15)    // Torch-like soft glow
            ));

}
