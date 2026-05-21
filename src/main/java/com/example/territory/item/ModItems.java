package com.example.territory.item;

import com.example.territory.TerritoryConquest;
import com.example.territory.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TerritoryConquest.MOD_ID);

    public static final RegistryObject<Item> CORE_BLOCK_ITEM = ITEMS.register("core_block",
            () -> new BlockItem(ModBlocks.CORE_BLOCK.get(),
                    new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)));

    public static final RegistryObject<Item> SUB_CORE_BLOCK_ITEM = ITEMS.register("sub_core_block",
            () -> new BlockItem(ModBlocks.SUB_CORE_BLOCK.get(),
                    new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)));

    public static final RegistryObject<Item> TERRITORY_FLAG_ITEM = ITEMS.register("territory_flag",
            () -> new BlockItem(ModBlocks.TERRITORY_FLAG.get(),
                    new Item.Properties().tab(CreativeModeTab.TAB_DECORATIONS)));

}
