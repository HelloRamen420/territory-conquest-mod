package com.example.territory;

import com.example.territory.block.ModBlocks;
import com.example.territory.item.ModItems;
import com.example.territory.network.ModMessages;
import com.example.territory.client.KeyInit;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TerritoryConquest.MOD_ID)
public class TerritoryConquest {
    public static final String MOD_ID = "territory_conquest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public TerritoryConquest() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register blocks and items
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        // Register the setup method for modloading
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Initializing Territory Conquest Mod for Forge 1.18.2...");
        ModMessages.register();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Registering client-side settings...");
        KeyInit.register();
        
        // Ensure flag block renders transparency properly
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.TERRITORY_FLAG.get(), RenderType.cutout());
    }
}
