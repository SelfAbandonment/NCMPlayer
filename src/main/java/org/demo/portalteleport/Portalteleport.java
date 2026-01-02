package org.demo.portalteleport;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

import org.demo.portalteleport.config.ModConfig;
import org.demo.portalteleport.event.FishingBonusHandler;
import org.demo.portalteleport.event.SleepStoryHandler;
import org.demo.portalteleport.util.I18n;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PortalTeleport 模组主类
 * 功能模块：
 *     传送门限制 - 阻止特定实体穿越地狱门/末地门
 *     钓鱼加成 - 钓鱼时有几率获得额外奖励
 *     睡前故事 - 睡醒后显示温馨提示和轻微增益
 *     网易云音乐 - 客户端音乐播放器（见 client 包）
 *
 * @author PortalTeleport Team
 * @version 1.15.0
 */
@Mod(Portalteleport.MODID)
public class Portalteleport {

    /** 模组 ID */
    public static final String MODID = "portalteleport";

    /** 运行时开关（可通过命令临时修改，重启后恢复配置文件设置） */
    private static volatile boolean gateBlockRuntimeEnabled = true;

    /** 玩家通知冷却时间记录 */
    private static final Map<UUID, Long> NOTIFY_COOLDOWN = new ConcurrentHashMap<>();

    /**
     * 模组构造函数
     *
     * @param modEventBus  模组事件总线（未使用，保留用于未来扩展）
     * @param modContainer 模组容器，用于注册配置
     */
    @SuppressWarnings("unused")
    public Portalteleport(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置
        modContainer.registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);

        // 注册事件处理器
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new FishingBonusHandler());
        NeoForge.EVENT_BUS.register(new SleepStoryHandler());
    }

    /**
     * 注册 /gate 命令
     * 子命令：
     * /gate on - 开启传送限制（需要管理员权限）
     * /gate off - 关闭传送限制（需要管理员权限）
     * /gate status - 查看当前状态
     * /gate info - 查看限制信息
     * /gate reload - 重置为配置文件状态（需要管理员权限）
     *
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
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
                    String configStatus = configEnabled
                            ? I18n.translateString(I18n.GATE_STATUS_ENABLED)
                            : I18n.translateString(I18n.GATE_STATUS_DISABLED);
                    String runtimeStatus = gateBlockRuntimeEnabled
                            ? I18n.translateString(I18n.GATE_STATUS_ON)
                            : I18n.translateString(I18n.GATE_STATUS_OFF);
                    source.sendSuccess(() -> I18n.translate(I18n.GATE_STATUS, configStatus, runtimeStatus), false);
                    return gateBlockRuntimeEnabled ? 1 : 0;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    var source = ctx.getSource();
                    var cfg = ModConfig.COMMON;
                    StringBuilder entities = new StringBuilder();
                    if (cfg.gateBlockItems.get()) {
                        entities.append(I18n.translateString(I18n.GATE_INFO_ITEMS));
                    }
                    if (cfg.gateBlockBoats.get()) {
                        entities.append(I18n.translateString(I18n.GATE_INFO_BOATS));
                    }
                    if (cfg.gateBlockMinecarts.get()) {
                        entities.append(I18n.translateString(I18n.GATE_INFO_MINECARTS));
                    }
                    source.sendSuccess(() -> I18n.translate(I18n.GATE_INFO, entities.toString()), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    var source = ctx.getSource();
                    if (!source.hasPermission(2)) {
                        source.sendFailure(I18n.translate(I18n.GATE_NO_PERMISSION));
                        return 0;
                    }
                    gateBlockRuntimeEnabled = ModConfig.COMMON.gateBlockEnabled.get();
                    source.sendSuccess(() -> I18n.translate(I18n.GATE_RELOADED), false);
                    return 1;
                }))
        );
    }

    /**
     * 处理实体穿越维度事件
     * 根据配置阻止特定实体（掉落物、船、矿车）穿越地狱门/末地门，
     * 并通知附近玩家。
     *
     * @param event 实体穿越维度事件
     */
    @SubscribeEvent
    public void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        var cfg = ModConfig.COMMON;

        // 检查功能是否启用
        if (!cfg.gateBlockEnabled.get() || !gateBlockRuntimeEnabled) {
            return;
        }

        var entity = event.getEntity();

        // 根据配置检查实体类型
        boolean shouldBlock = false;
        if (entity instanceof ItemEntity && cfg.gateBlockItems.get()) {
            shouldBlock = true;
        } else if (entity instanceof Boat && cfg.gateBlockBoats.get()) {
            shouldBlock = true;
        } else if (entity instanceof AbstractMinecart && cfg.gateBlockMinecarts.get()) {
            shouldBlock = true;
        }

        if (!shouldBlock) {
            return;
        }

        final Level level = entity.level();
        var target = event.getDimension();
        var source = level.dimension();
        boolean sourceIsBlocked = source == Level.NETHER || source == Level.END;
        boolean targetIsBlocked = target == Level.NETHER || target == Level.END;

        if (sourceIsBlocked || targetIsBlocked) {
            event.setCanceled(true);
            notifyNearbyPlayers(entity, level, source, target, cfg);
        }
    }

    /**
     * 通知附近玩家实体传送被阻止
     */
    private void notifyNearbyPlayers(net.minecraft.world.entity.Entity entity, Level level,
                                      net.minecraft.resources.ResourceKey<Level> source,
                                      net.minecraft.resources.ResourceKey<Level> target,
                                      ModConfig.CommonConfig cfg) {
        var typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        double radius = cfg.gateNotifyRadius.get();
        double r2 = radius * radius;
        long now = System.currentTimeMillis();
        long cooldown = cfg.gateNotifyCooldownMs.get();

        for (var player : level.players()) {
            double dx = player.getX() - entity.getX();
            double dy = player.getY() - entity.getY();
            double dz = player.getZ() - entity.getZ();

            if (dx * dx + dy * dy + dz * dz <= r2) {
                long lastNotify = NOTIFY_COOLDOWN.getOrDefault(player.getUUID(), 0L);
                if (now - lastNotify >= cooldown) {
                    NOTIFY_COOLDOWN.put(player.getUUID(), now);
                    player.sendSystemMessage(I18n.translate(I18n.GATE_BLOCKED,
                            typeKey.toString(),
                            source.location().toString(),
                            target.location().toString()));
                }
            }
        }
    }
}