package org.selfabandonment.ncmplayer.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.selfabandonment.ncmplayer.NcmPlayer;
import org.selfabandonment.ncmplayer.client.audio.MusicController;
import org.selfabandonment.ncmplayer.client.input.KeyMappings;
import org.selfabandonment.ncmplayer.client.screen.MusicScreen;
import org.selfabandonment.ncmplayer.config.ModConfig;

/**
 * 客户端事件处理器
 *
 * @author SelfAbandonment
 */
@EventBusSubscriber(modid = NcmPlayer.MODID, value = Dist.CLIENT)
public final class NcmPlayerClient {

    private NcmPlayerClient() {
    }

    /**
     * 注册快捷键
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyMappings.OPEN_MUSIC_UI);
    }

    /**
     * 客户端 Tick 事件处理
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return;
        }

        // 更新音乐播放器
        MusicController.tick();

        // 检测快捷键打开音乐界面
        if (KeyMappings.OPEN_MUSIC_UI.consumeClick()) {
            String apiUrl = ModConfig.COMMON.musicApiUrl.get();
            mc.setScreen(new MusicScreen(apiUrl));
        }
    }
}

