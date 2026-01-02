package org.demo.portalteleport.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.demo.portalteleport.client.audio.ClientMusicController;
import org.demo.portalteleport.client.input.PortalTeleportKeyMappings;
import org.demo.portalteleport.client.screen.PortalTeleportMusicScreen;
import org.demo.portalteleport.config.ModConfig;

@EventBusSubscriber(modid = "portalteleport", value = Dist.CLIENT)
public final class PortalTeleportClient {

    private PortalTeleportClient() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PortalTeleportKeyMappings.OPEN_MUSIC_UI);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // required for streaming
        ClientMusicController.tick();

        if (PortalTeleportKeyMappings.OPEN_MUSIC_UI.consumeClick()) {
            String apiUrl = ModConfig.COMMON.musicApiUrl.get();
            mc.setScreen(new PortalTeleportMusicScreen(apiUrl));
        }
    }
}