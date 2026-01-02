package org.demo.portalteleport;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Mod(Portalteleport.MODID)
public class Portalteleport {
    public static final String MODID = "portalteleport";
    private static volatile boolean GATE_BLOCK_ENABLED = true;
    private static final Map<UUID, Long> NOTIFY_COOLDOWN = new ConcurrentHashMap<>();
    private static final long NOTIFY_INTERVAL_MS = 5_000L;

    public Portalteleport() {
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
                        source.sendFailure(Component.literal("无权限：仅管理员可使用该命令"));
                        return 0;
                    }
                    GATE_BLOCK_ENABLED = true;
                    source.sendSuccess(() -> Component.literal("已开启：地狱/末地传送限制"), false);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    var source = ctx.getSource();
                    if (!source.hasPermission(2)) {
                        source.sendFailure(Component.literal("无权限：仅管理员可使用该命令"));
                        return 0;
                    }
                    GATE_BLOCK_ENABLED = false;
                    source.sendSuccess(() -> Component.literal("已关闭：地狱/末地传送限制"), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    var source = ctx.getSource();
                    source.sendSuccess(() -> Component.literal("当前状态：" + (GATE_BLOCK_ENABLED ? "开启" : "关闭")), false);
                    return GATE_BLOCK_ENABLED ? 1 : 0;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    var source = ctx.getSource();
                    source.sendSuccess(() -> Component.literal("限制对象：掉落物、船、矿车；受限维度：地狱/末地（双向）"), false);
                    return 1;
                }))
        );
    }

    @SuppressWarnings("resource")
    @SubscribeEvent
    public void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!GATE_BLOCK_ENABLED) return;
        var entity = event.getEntity();
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity
                || entity instanceof net.minecraft.world.entity.vehicle.Boat
                || entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart) {
            final Level level = entity.level();
            var target = event.getDimension();
            var source = level.dimension();
            boolean sourceIsBlocked = source == net.minecraft.world.level.Level.NETHER || source == net.minecraft.world.level.Level.END;
            boolean targetIsBlocked = target == net.minecraft.world.level.Level.NETHER || target == net.minecraft.world.level.Level.END;
            if (sourceIsBlocked || targetIsBlocked) {
                event.setCanceled(true);
                var typeKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                double radius = 24.0;
                double r2 = radius * radius;
                long now = System.currentTimeMillis();
                for (var p : level.players()) {
                    var dx = p.getX() - entity.getX();
                    var dy = p.getY() - entity.getY();
                    var dz = p.getZ() - entity.getZ();
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        var last = NOTIFY_COOLDOWN.getOrDefault(p.getUUID(), 0L);
                        if (now - last >= NOTIFY_INTERVAL_MS) {
                            NOTIFY_COOLDOWN.put(p.getUUID(), now);
                            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "已阻止传送: " + typeKey + " (" + source.location() + " -> " + target.location() + ")"));
                        }
                    }
                }
            }
        }
    }
}