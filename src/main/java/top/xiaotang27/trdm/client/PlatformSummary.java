package top.xiaotang27.trdm.client;

import mtr.client.ClientCache;
import mtr.client.ClientData;
import mtr.data.Platform;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 站台的一句话摘要：站台名 + 停靠线路（线路色块里的数字、线路名、终点站、线路色）。
 * <p>
 * 站台选择界面（{@link PsdControlScreen}）和 PIS 设置界面（{@link PisSettingsScreen}）共用，
 * 保证两处对同一个站台的描述完全一致 —— 选站台的地方和显示「当前选了哪个站台」的地方不会各写一套。
 */
final class PlatformSummary {

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /** 一条线路：色块里的线路号、线路全名、终点站那一行、线路色。 */
    record Line(String badgeText, String routeName, String terminus, int color) {
    }

    private PlatformSummary() {
    }

    /** 站台显示名（MTR 里就是站台编号，比如「1」）；找不到返回 null。 */
    static String name(long platformId) {
        for (final Platform platform : ClientData.PLATFORMS) {
            if (platform.id == platformId) {
                return platform.name;
            }
        }
        return null;
    }

    /**
     * 一个站台的线路列表（MTR 的 PlatformRouteDetails：线路色、线路名、站序）。
     * 终点站取这条线路的末站；本站就是末站时显示「终点站」。
     */
    static List<Line> lines(long platformId) {
        final List<ClientCache.PlatformRouteDetails> details = new ArrayList<>(
                ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId));
        details.sort(Comparator.comparing((ClientCache.PlatformRouteDetails route) -> route.routeName,
                String.CASE_INSENSITIVE_ORDER));

        final List<Line> lines = new ArrayList<>();
        for (final ClientCache.PlatformRouteDetails route : details) {
            final List<ClientCache.PlatformRouteDetails.StationDetails> stations = route.stationDetails;
            final String terminus;
            if (stations.isEmpty()) {
                terminus = "";
            } else if (route.currentStationIndex >= stations.size() - 1) {
                terminus = Component.translatable("gui.trdm.psd_control.terminal").getString();
            } else {
                terminus = Component.translatable("gui.trdm.psd_control.towards",
                        stations.get(stations.size() - 1).stationName).getString();
            }
            lines.add(new Line(badgeText(route.routeName), route.routeName, terminus, route.routeColor));
        }
        return lines;
    }

    /** 线路色块里那个数字：线路名里第一段连续数字（「1号线|Line 1」→「1」），没有就取头两个字符。 */
    static String badgeText(String routeName) {
        final String chinesePart = routeName == null ? "" : routeName.split("\\|")[0].trim();
        final Matcher matcher = DIGITS.matcher(chinesePart);
        if (matcher.find()) {
            return matcher.group();
        }
        return chinesePart.isEmpty() ? "#" : chinesePart.substring(0, Math.min(2, chinesePart.length()));
    }

    /** 色块上的字：线路色偏亮就用黑字，偏暗就用白字。 */
    static int textColor(int rgb) {
        final int red = (rgb >> 16) & 0xFF;
        final int green = (rgb >> 8) & 0xFF;
        final int blue = rgb & 0xFF;
        return 0.299 * red + 0.587 * green + 0.114 * blue > 155 ? 0xFF101010 : 0xFFFFFFFF;
    }
}
