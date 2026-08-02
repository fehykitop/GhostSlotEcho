package com.ghostslotecho;

import com.ghostslotecho.config.GhostSlotConfig;
import com.ghostslotecho.config.GhostSlotConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GhostSlotEchoMod implements ClientModInitializer {
    public static final String MOD_ID = "ghostslotecho";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding openConfigKeyBinding;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Ghost Slot Echo (Client-Side)...");
        GhostSlotConfig.load();

        openConfigKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostslotecho.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.ghostslotecho"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKeyBinding.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(GhostSlotConfigScreen.create(null));
                }
            }
        });
    }
}
