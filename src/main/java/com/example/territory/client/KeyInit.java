package com.example.territory.client;

import com.example.territory.TerritoryConquest;
import com.example.territory.network.ModMessages;
import com.example.territory.network.PacketRequestTerritoryInfo;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TerritoryConquest.MOD_ID, value = Dist.CLIENT)
public class KeyInit {
    public static KeyMapping openTerritoryKey;

    public static void register() {
        openTerritoryKey = new KeyMapping(
                "key.territory_conquest.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "key.categories.territory_conquest"
        );

        ClientRegistry.registerKeyBinding(openTerritoryKey);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        if (Minecraft.getInstance().screen == null && openTerritoryKey != null && openTerritoryKey.consumeClick()) {
            // Request territory info from server
            ModMessages.sendToServer(new PacketRequestTerritoryInfo());
        }
    }
}
