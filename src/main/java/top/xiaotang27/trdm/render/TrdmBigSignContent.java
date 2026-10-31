package top.xiaotang27.trdm.render;

import mtr.client.ClientCache;
import mtr.client.ClientData;
import mtr.data.RailwayData;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import top.xiaotang27.trdm.block.BlockTrdmBigStationSign;
import top.xiaotang27.trdm.control.ExitNames;
import top.xiaotang27.trdm.control.StationScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 大站名牌（墙挂 / 悬挂两款共用）的「内容」：站名、本站所有线路的编号块、出口字母、
 * 站名后缀（STATION / ZHAN）、无障碍电梯牌开关。
 * <p>
 * 两项数据和出口一样都是<b>整段牌共用</b>：段里哪一格被设置过就以它为准
 * （刚刚放下、还没设置过的格子不会把整段打回默认）。
 */
public final class TrdmBigSignContent {

    /**
     * @param stationName 本站（MTR 的「中文|English」）
     * @param badges      本站所有线路的编号标识，按线路名排序
     * @param exitName    出口名（空串 = 不画）
     * @param zhanStyle   true = 英文后缀 ZHAN
     * @param elevator    true = 画成「站名 + 无障碍电梯」牌
     */
    public record Content(String stationName, List<TrdmHeaderTextures.Badge> badges, String exitName,
                          boolean zhanStyle, boolean elevator) {
    }

    private TrdmBigSignContent() {
    }

    /** 解出一整段牌要画的内容；牌不在任何车站范围内时返回 null。 */
    public static Content resolve(Level world, List<BlockPos> run) {
        if (run.isEmpty()) {
            return null;
        }
        final Station station = resolveStation(run.get(0));
        if (station == null) {
            return null;
        }

        // 出口 / 后缀 / 无障碍电梯牌：段里任何一格被设置过就认它
        BlockTrdmBigStationSign.TileEntityTrdmBigStationSign source = null;
        for (final BlockPos member : run) {
            if (!(world.getBlockEntity(member) instanceof BlockTrdmBigStationSign.TileEntityTrdmBigStationSign sign)) {
                continue;
            }
            if (source == null) {
                // 兜底：整段都没设置过就用段首那格
                source = sign;
            }
            if (sign.isConfigured()) {
                source = sign;
                break;
            }
        }
        if (source == null) {
            return null;
        }

        final String stationName = station.name;
        String exitName = source.getExitName();
        // 出口被改名 / 删掉之后，存的旧名字在车站里就不存在了 —— 这时退到本站排序后的第一个出口，
        // 保证牌子上那个字母始终跟着 MTR 里的出口信息走。
        if (!exitName.isEmpty() && !ExitNames.isKnown(station, exitName)) {
            final List<String> exitNames = new ArrayList<>(station.exits.keySet());
            exitNames.sort(ExitNames::compare);
            if (!exitNames.isEmpty()) {
                exitName = exitNames.get(0);
            }
        }

        // 本站所有线路：按线路名排序，全部画进编号块（每块粗白描边）
        final Map<Integer, ClientCache.ColorNameTuple> stationRoutes = ClientData.DATA_CACHE.stationIdToRoutes.get(station.id);
        final List<TrdmHeaderTextures.Badge> badges = new ArrayList<>();
        if (stationRoutes != null && !stationRoutes.isEmpty()) {
            final List<ClientCache.ColorNameTuple> sorted = new ArrayList<>(stationRoutes.values());
            sorted.sort(Comparator.comparing((ClientCache.ColorNameTuple tuple) -> tuple.name, String.CASE_INSENSITIVE_ORDER));
            for (final ClientCache.ColorNameTuple tuple : sorted) {
                final String[] parts = splitRouteName(tuple.name);
                badges.add(new TrdmHeaderTextures.Badge(tuple.color, parts[0], parts[1]));
            }
        }

        return new Content(stationName, badges, exitName, source.isZhanStyle(), source.isElevator());
    }

    /** 找标牌所在车站：站区范围内的车站，没有就用最近站台所属车站。 */
    public static Station resolveStation(BlockPos pos) {
        final Station inArea = StationScope.stationAt(ClientData.STATIONS, pos);
        if (inArea != null) {
            return inArea;
        }
        final long closestPlatformId = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
        return closestPlatformId == 0 ? null : ClientData.DATA_CACHE.platformIdToStation.get(closestPlatformId);
    }

    /** 「3号线|Line 3」→「3号线」「Line 3」。 */
    public static String[] splitRouteName(String routeName) {
        final String name = routeName == null ? "" : routeName.trim();
        final String[] parts = name.split("\\|");
        if (parts.length == 1) {
            return new String[]{name, ""};
        }
        final StringBuilder english = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            final String part = parts[i].trim();
            if (!part.isEmpty()) {
                if (english.length() > 0) {
                    english.append(' ');
                }
                english.append(part);
            }
        }
        return new String[]{parts[0].trim(), english.toString()};
    }
}
