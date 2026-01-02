package org.demo.portalteleport.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.demo.portalteleport.Portalteleport;
import org.demo.portalteleport.client.audio.ClientMusicController;
import org.demo.portalteleport.client.input.PortalTeleportKeyMappings;
import org.demo.portalteleport.client.screen.PortalTeleportMusicScreen;
import org.demo.portalteleport.config.ModConfig;

/**
 * 客户端事件处理器
 * 负责：
 * - 注册快捷键
 * - 处理客户端 Tick 事件
 * - 响应快捷键打开音乐界面
 *
 * @author SelfAbandonment
 */
@EventBusSubscriber(modid = Portalteleport.MODID, value = Dist.CLIENT)
public final class PortalTeleportClient {

    private PortalTeleportClient() {
        // 工具类，禁止实例化
    }

    /**
     * 注册快捷键
     *
     * @param event 快捷键注册事件
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PortalTeleportKeyMappings.OPEN_MUSIC_UI);
    }

    /**
     * 客户端 Tick 事件处理
     *
     * 每帧执行：
     * - 更新音乐播放器状态
     * - 检测快捷键并打开音乐界面
     *
     * @param event 客户端 Tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // 玩家未加入世界时不处理
        if (mc.player == null) {
            return;
        }

        // 更新音乐播放器
        ClientMusicController.tick();

        // 检测快捷键打开音乐界面
        if (PortalTeleportKeyMappings.OPEN_MUSIC_UI.consumeClick()) {
            String apiUrl = ModConfig.COMMON.musicApiUrl.get();
            mc.setScreen(new PortalTeleportMusicScreen(apiUrl));
        }
    }
}