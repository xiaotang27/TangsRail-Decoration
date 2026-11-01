package top.xiaotang27.trdm.net;

import mtr.block.IBlock;
import mtr.data.Station;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.block.BlockTrdmPSDControl;
import top.xiaotang27.trdm.block.BlockTrdmBigStationSign;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.block.BlockTrdmPis;
import top.xiaotang27.trdm.block.BlockTrdmStationSign;
import top.xiaotang27.trdm.block.SignRuns;
import top.xiaotang27.trdm.control.PsdDoorControl;
import top.xiaotang27.trdm.control.StationScope;

import java.util.List;

/**
 * 集控装置的网络包：只有一条 C2S —— 「把这个装置的站台设成 X」。
 * <p>
 */
public final class TrdmControlNetwork {

    /** C2S：BlockPos（装置位置）+ long（站台 id，0 = 清除）。 */
    public static final ResourceLocation SELECT_PLATFORM = Trdm.id("psd_control_select");

    /** C2S：BlockPos（装置位置）—— 切换这个装置的「屏蔽门隔离」。 */
    public static final ResourceLocation TOGGLE_ISOLATE = Trdm.id("psd_control_isolate");

    /** C2S：BlockPos（大站名牌位置）+ String（出口名，空串 = 清除）。 */
    public static final ResourceLocation SELECT_EXIT = Trdm.id("big_station_sign_exit");

    /** C2S：BlockPos（大站名牌位置）+ 是否 ZHAN + 是否无障碍电梯牌。 */
    public static final ResourceLocation BIG_SIGN_OPTIONS = Trdm.id("big_sign_options");

    /** C2S：BlockPos（门楣位置）+ 吊版样式 + 玻璃装饰线样式。 */
    public static final ResourceLocation PSD_APPEARANCE = Trdm.id("psd_appearance");
    public static final ResourceLocation PSD_END_GATE = Trdm.id("psd_end_gate");

    /** C2S：BlockPos（PIS 屏幕）+ 跑马文字 + 广告开关 + 广告覆盖项。 */
    public static final ResourceLocation PIS_SETTINGS = Trdm.id("pis_settings");

    /** 配置必须在装置旁边操作（防止改包远程改别人的装置）。 */
    private static final double MAX_SELECT_DISTANCE_SQR = 64 * 64;

    /** 打开 {@code -Dtrdm.debugSign=true} 会把大站名牌的出口选择打到日志里。 */
    private static final boolean DEBUG = Boolean.getBoolean("trdm.debugSign");

    private TrdmControlNetwork() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SELECT_PLATFORM, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final long platformId = buf.readLong();
            // 网络线程不能碰世界，丢回主线程
            server.execute(() -> handleSelection(player, pos, platformId));
        });
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_ISOLATE, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            server.execute(() -> handleIsolate(player, pos));
        });
        ServerPlayNetworking.registerGlobalReceiver(SELECT_EXIT, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final String exitName = buf.readUtf(64);
            server.execute(() -> handleExitSelection(player, pos, exitName));
        });
        ServerPlayNetworking.registerGlobalReceiver(BIG_SIGN_OPTIONS, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final boolean zhanStyle = buf.readBoolean();
            final boolean elevator = buf.readBoolean();
            server.execute(() -> handleBigSignOptions(player, pos, zhanStyle, elevator));
        });
        ServerPlayNetworking.registerGlobalReceiver(PSD_END_GATE, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final boolean endGate = buf.readBoolean();
            server.execute(() -> {
                final net.minecraft.server.level.ServerLevel world = (net.minecraft.server.level.ServerLevel) player.level();
                if (world.getBlockEntity(pos) instanceof top.xiaotang27.trdm.block.BlockTrdmPSDGlass.TileEntityTrdmPSDGlass glass) {
                    glass.setEndGate(endGate);
                    world.sendBlockUpdated(pos, glass.getBlockState(), glass.getBlockState(), 3);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(PSD_APPEARANCE, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final int headerStyle = buf.readVarInt();
            final int bandStyle = buf.readVarInt();
            server.execute(() -> handlePsdAppearance(player, pos, headerStyle, bandStyle));
        });
        ServerPlayNetworking.registerGlobalReceiver(PIS_SETTINGS, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final String ticker = buf.readUtf(512);
            final boolean adsEnabled = buf.readBoolean();
            final int count = buf.readVarInt();
            final java.util.List<String> order = new java.util.ArrayList<>();
            final java.util.Map<String, Boolean> enabled = new java.util.HashMap<>();
            final java.util.Map<String, Integer> times = new java.util.HashMap<>();
            for (int index = 0; index < count; index++) {
                final String id = buf.readUtf(128);
                final boolean adEnabled = buf.readBoolean();
                final int time = buf.readVarInt();
                order.add(id);
                enabled.put(id, adEnabled);
                times.put(id, time);
            }
            server.execute(() -> handlePisSettings(player, pos, ticker, adsEnabled, order, enabled, times));
        });
    }

    /** PIS 屏幕设置：跑马文字 + 广告开关 + 广告覆盖项，写进屏幕自己的方块实体。 */
    private static void handlePisSettings(ServerPlayer player, BlockPos pos, String ticker, boolean adsEnabled,
                                          java.util.List<String> order, java.util.Map<String, Boolean> enabled,
                                          java.util.Map<String, Integer> times) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_SELECT_DISTANCE_SQR) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(serverLevel.getBlockEntity(pos) instanceof BlockTrdmPis.TileEntityTrdmPis pis)) {
            return;
        }
        pis.setTicker(ticker);
        pis.setAdsEnabled(adsEnabled);
        pis.setAds(order, enabled, times);
        if (DEBUG) {
            Trdm.LOGGER.info("[pis] pos={} ticker='{}' ads={} entries={}", pos, ticker, adsEnabled, order.size());
        }
    }

    /** 大站名牌：站名后缀（STATION / ZHAN）和无障碍电梯牌，整段一起写。 */
    private static void handleBigSignOptions(ServerPlayer player, BlockPos pos, boolean zhanStyle, boolean elevator) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_SELECT_DISTANCE_SQR) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        applyToRun(serverLevel, pos, BlockTrdmBigStationSign.FACING,
                member -> {
                    if (serverLevel.getBlockEntity(member)
                            instanceof BlockTrdmBigStationSign.TileEntityTrdmBigStationSign sign) {
                        sign.setOptions(zhanStyle, elevator);
                    }
                });
        if (DEBUG) {
            Trdm.LOGGER.info("[big sign] pos={} zhan={} elevator={}", pos, zhanStyle, elevator);
        }
    }

    /** 门楣：吊版样式 + 玻璃装饰线样式，整排门楣一起写。 */
    private static void handlePsdAppearance(ServerPlayer player, BlockPos pos, int headerStyle, int bandStyle) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_SELECT_DISTANCE_SQR) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        applyToRun(serverLevel, pos, net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                member -> BlockTrdmPSDTop.applyStyles(serverLevel, member, headerStyle, bandStyle));
        player.displayClientMessage(Component.translatable("gui.trdm.psd_style.applied"), true);
    }

    /** 大站名牌：选「本车站的出口」，服务端只做长度/归属的最小校验。 */
    private static void handleExitSelection(ServerPlayer player, BlockPos pos, String exitName) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_SELECT_DISTANCE_SQR) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)
                || !(player.level().getBlockEntity(pos) instanceof BlockTrdmBigStationSign.TileEntityTrdmBigStationSign)) {
            return;
        }

        final String safe = exitName == null ? "" : exitName.trim();
        // 一整段合并牌共用同一个出口：只写被点中的那一格的话，段首（唯一真正绘制的那一格）
        // 读到的还是旧值，牌子上不会变，得拆掉一截才“突然”显示出来
        applyToRun(serverLevel, pos, BlockTrdmBigStationSign.FACING,
                member -> {
                    if (serverLevel.getBlockEntity(member)
                            instanceof BlockTrdmBigStationSign.TileEntityTrdmBigStationSign memberSign) {
                        memberSign.setExitName(safe);
                    }
                });

        if (DEBUG) {
            final Station station = PsdDoorControl.deviceStation(serverLevel, pos);
            Trdm.LOGGER.info("[big sign] pos={} exit='{}' station={} exits={}",
                    pos, safe,
                    station == null ? "null" : station.name,
                    station == null ? "?" : station.exits.keySet());
        }

        if (safe.isEmpty()) {
            player.displayClientMessage(Component.translatable("gui.trdm.psd_control.cleared"), false);
        } else {
            player.displayClientMessage(Component.translatable("gui.trdm.big_sign.exit_selected", safe), false);
        }
    }

    private static void handleIsolate(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_SELECT_DISTANCE_SQR) {
            return;
        }
        if (!(player.level().getBlockEntity(pos) instanceof BlockTrdmPSDControl.TileEntityTrdmPSDControl control)
                || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (control.getPlatformId() == 0) {
            player.displayClientMessage(Component.translatable("gui.trdm.psd_control.no_platform"), true);
            return;
        }

        final long platformId = control.getPlatformId();
        final String label = PsdDoorControl.platformLabel(serverLevel, platformId);
        final String platformName = label == null ? String.valueOf(platformId) : label;

        // 开隔离之前先确认这个站台真的找得到门，否则「隔离」了什么也没隔离
        if (!control.isIsolated()) {
            final List<BlockPos> doors = PsdDoorControl.findDoors(serverLevel, platformId);
            if (doors == null) {
                player.displayClientMessage(Component.translatable("gui.trdm.psd_control.missing_platform"), true);
                return;
            }
            if (doors.isEmpty()) {
                player.displayClientMessage(Component.translatable("gui.trdm.psd_control.no_doors", platformName), true);
                return;
            }
        }

        final boolean isolated = control.toggleIsolated(serverLevel);
        player.displayClientMessage(Component.translatable(
                isolated ? "gui.trdm.psd_control.isolated_on" : "gui.trdm.psd_control.isolated_off",
                platformName), false);
    }

    private static void handleSelection(ServerPlayer player, BlockPos pos, long platformId) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_SELECT_DISTANCE_SQR) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final net.minecraft.world.level.block.entity.BlockEntity entity = player.level().getBlockEntity(pos);

        if (entity instanceof BlockTrdmPSDControl.TileEntityTrdmPSDControl control) {
            if (platformId != 0 && !sameStation(serverLevel, pos, platformId, player)) {
                return;
            }
            control.setPlatformId(serverLevel, platformId);
            sendSelectedMessage(player, serverLevel, platformId);
            return;
        }

        if (entity instanceof BlockTrdmStationSign.TileEntityTrdmStationSign) {
            if (platformId != 0 && !sameStation(serverLevel, pos, platformId, player)) {
                return;
            }
            // 整段牌共用同一个站台，理由同大站名牌
            applyToRun(serverLevel, pos, BlockTrdmStationSign.FACING,
                    member -> {
                        if (serverLevel.getBlockEntity(member)
                                instanceof BlockTrdmStationSign.TileEntityTrdmStationSign memberSign) {
                            memberSign.setPlatformId(platformId);
                        }
                    });
            sendSelectedMessage(player, serverLevel, platformId);
        }
        // 大站名牌不用选站台，它选的是车站出口（见 handleExitSelection）
    }

    /** 把「整段合并牌共用」的数据按段写一遍：段里的每一格都要写，不然段首那格读不到。 */
    private static void applyToRun(ServerLevel level, BlockPos pos, DirectionProperty property,
                                   java.util.function.Consumer<BlockPos> action) {
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        final Direction facing = IBlock.getStatePropertySafe(state, property);
        for (final BlockPos member : SignRuns.collect(level, pos, state.getBlock(), property, facing)) {
            action.accept(member);
        }
    }

    /** 只允许选「方块所在车站」的站台；不通过时给玩家一条提示并返回 false。 */
    private static boolean sameStation(ServerLevel serverLevel, BlockPos pos, long platformId, ServerPlayer player) {
        final Station deviceStation = PsdDoorControl.deviceStation(serverLevel, pos);
        final Station platformStation = PsdDoorControl.platformStation(serverLevel, platformId);
        if (StationScope.sameStation(deviceStation, platformStation)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("gui.trdm.psd_control.wrong_station"), false);
        return false;
    }

    private static void sendSelectedMessage(ServerPlayer player, ServerLevel serverLevel, long platformId) {
        if (platformId == 0) {
            player.displayClientMessage(Component.translatable("gui.trdm.psd_control.cleared"), false);
            return;
        }
        final String label = PsdDoorControl.platformLabel(serverLevel, platformId);
        player.displayClientMessage(Component.translatable("gui.trdm.psd_control.selected",
                label == null ? String.valueOf(platformId) : label), false);
    }
}
