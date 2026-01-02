package org.demo.portalteleport;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.world.level.Level;

import org.demo.portalteleport.config.ModConfig;
import org.demo.portalteleport.util.I18n;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Mod(Portalteleport.MODID)
public class Portalteleport {
    public static final String MODID = "portalteleport";

    // 运行时开关（可通过命令临时修改，重启后恢复配置文件设置）
    private static volatile boolean gateBlockRuntimeEnabled = true;
    private static final Map<UUID, Long> NOTIFY_COOLDOWN = new ConcurrentHashMap<>();

    public Portalteleport(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置
        modContainer.registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);

        // 注册事件处理器
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new org.demo.portalteleport.event.FishingBonusHandler());
        NeoForge.EVENT_BUS.register(new org.demo.portalteleport.event.SleepStoryHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("gate")
                .then(Commands.literal("on").executes(ctx -> {
                    var source = ctx.getSource();
                    if (!source.hasPermission(2)) {
                        source.sendFailure(I18n.translate(I18n.GATE_NO_PERMISSION));
                        return 0;
                    }
                    gateBlockRuntimeEnabled = true;
                    source.sendSuccess(() -> I18n.translate(I18n.GATE_ENABLED), false);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    var source = ctx.getSource();
                    if (!source.hasPermission(2)) {
                        source.sendFailure(I18n.translate(I18n.GATE_NO_PERMISSION));
                        return 0;
                    }
                    gateBlockRuntimeEnabled = false;
                    source.sendSuccess(() -> I18n.translate(I18n.GATE_DISABLED), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    var source = ctx.getSource();
                    boolean configEnabled = ModConfig.COMMON.gateBlockEnabled.get();
                    String configStatus = configEnabled ?
                            I18n.translateString(I18n.GATE_STATUS_ENABLED) :
                            I18n.translateString(I18n.GATE_STATUS_DISABLED);
                    String runtimeStatus = gateBlockRuntimeEnabled ?
                            I18n.translateString(I18n.GATE_STATUS_ON) :
                            I18n.translateString(I18n.GATE_STATUS_OFF);
                    source.sendSuccess(() -> I18n.translate(I18n.GATE_STATUS, configStatus, runtimeStatus), false);
                    return gateBlockRuntimeEnabled ? 1 : 0;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    var source = ctx.getSource();
                    var cfg = ModConfig.COMMON;
                    StringBuilder entities = new StringBuilder();
                    if (cfg.gateBlockItems.get()) entities.append(I18n.translateString(I18n.GATE_INFO_ITEMS));
                    if (cfg.gateBlockBoats.get()) entities.append(I18n.translateString(I18n.GATE_INFO_BOATS));
                    if (cfg.gateBlockMinecarts.get()) entities.append(I18n.translateString(I18n.GATE_INFO_MINECARTS));
                    source.sendSuccess(() -> I18n.translate(I18n.GATE_INFO, entities.toString()), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    var source = ctx.getSource();
                    if (!source.hasPermission(2)) {
                        source.sendFailure(I18n.translate(I18n.GATE_NO_PERMISSION));
                        return 0;
                    }
                    // 重置运行时状态为配置文件状态
                    gateBlockRuntimeEnabled = ModConfig.COMMON.gateBlockEnabled.get();
                    source.sendSuccess(() -> I18n.translate(I18n.GATE_RELOADED), false);
                    return 1;
                }))
        );
    }

    @SuppressWarnings("resource")
    @SubscribeEvent
    public void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        var cfg = ModConfig.COMMON;

        // 检查功能是否启用
        if (!cfg.gateBlockEnabled.get() || !gateBlockRuntimeEnabled) return;

        var entity = event.getEntity();

        // 根据配置检查实体类型
        boolean shouldBlock = false;
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity && cfg.gateBlockItems.get()) {
            shouldBlock = true;
        } else if (entity instanceof net.minecraft.world.entity.vehicle.Boat && cfg.gateBlockBoats.get()) {
            shouldBlock = true;
        } else if (entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart && cfg.gateBlockMinecarts.get()) {
            shouldBlock = true;
        }

        if (!shouldBlock) return;

        final Level level = entity.level();
        var target = event.getDimension();
        var source = level.dimension();
        boolean sourceIsBlocked = source == Level.NETHER || source == Level.END;
        boolean targetIsBlocked = target == Level.NETHER || target == Level.END;

        if (sourceIsBlocked || targetIsBlocked) {
            event.setCanceled(true);
            var typeKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            double radius = cfg.gateNotifyRadius.get();
            double r2 = radius * radius;
            long now = System.currentTimeMillis();
            long cooldown = cfg.gateNotifyCooldownMs.get();

            for (var p : level.players()) {
                var dx = p.getX() - entity.getX();
                var dy = p.getY() - entity.getY();
                var dz = p.getZ() - entity.getZ();
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    var last = NOTIFY_COOLDOWN.getOrDefault(p.getUUID(), 0L);
                    if (now - last >= cooldown) {
                        NOTIFY_COOLDOWN.put(p.getUUID(), now);
                        p.sendSystemMessage(I18n.translate(I18n.GATE_BLOCKED,
                                typeKey.toString(),
                                source.location().toString(),
                                target.location().toString()));
                    }
                }
            }
        }
    }
}