package top.xiaotang27.trdm.client;

import mtr.block.IBlock;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.xiaotang27.trdm.block.BlockTrdmBigStationSign;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.block.SignRuns;
import top.xiaotang27.trdm.control.PsdStyles;
import top.xiaotang27.trdm.net.TrdmControlNetwork;

import java.util.List;

/**
 * 各个设置界面的客户端入口：集控装置 / 站名牌 / 大站名牌 / 屏蔽门外观。
 * 只能在客户端分支里调用（专用服务器上不能加载这个类）。
 */
public final class TrdmControlClient {

    /** 打开界面的是哪种方块（决定界面标题和底部控件）。 */
    public enum SelectMode {
        /** 屏蔽门集控装置：有隔离开关。 */
        CONTROL_DEVICE,
        /** 站名牌：只有站台列表。 */
        STATION_SIGN
    }

    private TrdmControlClient() {
    }

    /**
     * 方块所在的车站：先看它是不是落在某个车站范围内；不在范围内（集控室盖在站区外）
     * 就退一步用「离它最近的站台」的车站。服务端校验用的是同一套规矩。
     */
    public static mtr.data.Station resolveStation(BlockPos pos) {
        final mtr.data.Station station = top.xiaotang27.trdm.control.StationScope.stationAt(mtr.client.ClientData.STATIONS, pos);
        if (station != null) {
            return station;
        }
        final long closestPlatformId = mtr.data.RailwayData.getClosePlatformId(
                mtr.client.ClientData.PLATFORMS, mtr.client.ClientData.DATA_CACHE, pos);
        return closestPlatformId == 0 ? null
                : top.xiaotang27.trdm.control.StationScope.stationOf(
                        mtr.client.ClientData.DATA_CACHE.platformIdToStation, closestPlatformId);
    }

    public static void openPlatformSelect(BlockPos targetPos, long selectedPlatformId, boolean isolated, SelectMode mode) {
        Minecraft.getInstance().setScreen(new PsdControlScreen(targetPos, selectedPlatformId, isolated, mode));
    }

    /** PIS 屏幕：刷子右键打开设置界面。 */
    public static void openPisSettings(BlockPos targetPos) {
        Minecraft.getInstance().setScreen(new PisSettingsScreen(targetPos));
    }

    /** PIS 屏幕：把整份设置发回服务端（同时本地先改，界面里立刻能看到）。 */
    public static void sendPisSettings(BlockPos targetPos, String ticker, boolean adsEnabled,
                                       java.util.List<String> order, java.util.Map<String, Boolean> enabled,
                                       java.util.Map<String, Integer> times) {
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(targetPos)
                instanceof top.xiaotang27.trdm.block.BlockTrdmPis.TileEntityTrdmPis pis) {
            pis.setTicker(ticker);
            pis.setAdsEnabled(adsEnabled);
            pis.setAds(order, enabled, times);
        }
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(targetPos);
        buf.writeUtf(ticker == null ? "" : ticker, 512);
        buf.writeBoolean(adsEnabled);
        buf.writeVarInt(order.size());
        for (final String id : order) {
            buf.writeUtf(id, 128);
            buf.writeBoolean(!Boolean.FALSE.equals(enabled.get(id)));
            buf.writeVarInt(times.getOrDefault(id, 0));
        }
        ClientPlayNetworking.send(TrdmControlNetwork.PIS_SETTINGS, buf);
    }

    /** 大站名牌：刷子右键打开的设置界面（站名后缀 + 无障碍电梯牌 + 出口）。 */
    public static void openBigSignMenu(BlockPos targetPos, String selectedExit, boolean zhanStyle, boolean elevator) {
        Minecraft.getInstance().setScreen(new TrdmExitSelectScreen(targetPos, selectedExit, zhanStyle, elevator));
    }

    /** 屏蔽门：蹲下 + 刷子右键打开外观设置（往上找门楣，样式记在门楣上）。 */
    public static void openPsdAppearance(BlockPos clickedPos) {
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        final boolean debug = Boolean.getBoolean("trdm.debugSign");
        final BlockPos headerPos = BlockTrdmPSDTop.findHeaderAbove(level, clickedPos);
        if (debug) {
            top.xiaotang27.trdm.Trdm.LOGGER.info("[psd appearance] clicked={} headerAbove={}", clickedPos, headerPos);
        }
        if (headerPos == null) {
            top.xiaotang27.trdm.Trdm.LOGGER.warn("[psd appearance] 上方找不到门楣：clicked={}", clickedPos);
            final net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("gui.trdm.psd_style.no_header"), true);
            }
            return;
        }
        int headerStyle = PsdStyles.DEFAULT_HEADER;
        int bandStyle = PsdStyles.DEFAULT_BAND;
        final BlockState headerState = level.getBlockState(headerPos);
        if (headerState.getBlock() instanceof BlockTrdmPSDTop) {
            headerStyle = BlockTrdmPSDTop.headerStyle(headerState);
            bandStyle = BlockTrdmPSDTop.bandStyle(headerState);
        }
        if (debug) {
            top.xiaotang27.trdm.Trdm.LOGGER.info("[psd appearance] opening screen header={} band={}", headerStyle, bandStyle);
        }
        Minecraft.getInstance().setScreen(new PsdAppearanceScreen(headerPos, headerStyle, bandStyle));
    }

    /** 大站名牌：把选中的出口名发回服务端（空串 = 清除）。 */
    public static void sendExitSelection(BlockPos targetPos, String exitName) {
        final String safe = exitName == null ? "" : exitName;
        // 先在本地把整段牌的出口改掉：不等服务端回包，牌子当帧就换
        for (final BlockPos member : runOf(targetPos)) {
            if (Minecraft.getInstance().level != null
                    && Minecraft.getInstance().level.getBlockEntity(member)
                    instanceof BlockTrdmBigStationSign.TileEntityTrdmBigStationSign sign) {
                sign.setExitName(safe);
            }
        }
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(targetPos);
        buf.writeUtf(safe, 64);
        ClientPlayNetworking.send(TrdmControlNetwork.SELECT_EXIT, buf);
    }

    /** 大站名牌：站名后缀（ZHAN / STATION）和无障碍电梯牌，整段一起改。 */
    public static void sendBigSignOptions(BlockPos targetPos, boolean zhanStyle, boolean elevator) {
        for (final BlockPos member : runOf(targetPos)) {
            if (Minecraft.getInstance().level != null
                    && Minecraft.getInstance().level.getBlockEntity(member)
                    instanceof BlockTrdmBigStationSign.TileEntityTrdmBigStationSign sign) {
                sign.setOptions(zhanStyle, elevator);
            }
        }
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(targetPos);
        buf.writeBoolean(zhanStyle);
        buf.writeBoolean(elevator);
        ClientPlayNetworking.send(TrdmControlNetwork.BIG_SIGN_OPTIONS, buf);
    }

    /**
     * 门楣：吊版样式 + 玻璃装饰线样式，整排门楣一起改。
     * 样式是方块状态，服务端改完会自动同步回来（一个来回），客户端不预测。
     */
    public static void sendPsdAppearance(BlockPos headerPos, int headerStyle, int bandStyle) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(headerPos);
        buf.writeVarInt(headerStyle);
        buf.writeVarInt(bandStyle);
        ClientPlayNetworking.send(TrdmControlNetwork.PSD_APPEARANCE, buf);
    }

    public static void sendSelection(BlockPos targetPos, long platformId) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(targetPos);
        buf.writeLong(platformId);
        ClientPlayNetworking.send(TrdmControlNetwork.SELECT_PLATFORM, buf);
    }

    /**
     * 端门：把开度档位（0 = 全关）发给服务端。短按给 0 / 满档，拖动时按档位变化发。
     * 服务端改的是方块状态，会自动同步回来。
     */
    public static void sendEndGateOpen(BlockPos gatePos, int step) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(gatePos);
        buf.writeVarInt(step);
        ClientPlayNetworking.send(TrdmControlNetwork.END_GATE_OPEN, buf);
    }

    public static void sendToggleIsolate(BlockPos targetPos) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(targetPos);
        ClientPlayNetworking.send(TrdmControlNetwork.TOGGLE_ISOLATE, buf);
    }

    /**
     * 这一格所属的「合并段」（大站名牌 / 门楣）：整段共用的数据要整段一起改，
     * 因为整段只由段首那一格绘制 —— 只改被点中的那格，画面上不会变。
     */
    private static List<BlockPos> runOf(BlockPos pos) {
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of(pos);
        }
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockTrdmBigStationSign || state.getBlock() instanceof BlockTrdmPSDTop) {
            final Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);
            return SignRuns.collect(level, pos, state.getBlock(), HorizontalDirectionalBlock.FACING, facing);
        }
        return List.of(pos);
    }
}
