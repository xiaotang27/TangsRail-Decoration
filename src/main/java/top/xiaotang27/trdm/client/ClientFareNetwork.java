package top.xiaotang27.trdm.client;

import mtr.client.ClientData;
import mtr.data.AreaBase;
import mtr.data.Route;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import top.xiaotang27.trdm.fare.FareNetwork;
import top.xiaotang27.trdm.fare.FareRoute;
import top.xiaotang27.trdm.fare.FareStation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 把客户端 MTR 数据转成计费快照。
 * <p>
 * 只在客户端线程调用。站台通过 {@code ClientData.DATA_CACHE.platformIdToStation} 映射到车站，
 * 相邻同站去重；环线在 {@link FareRoute#circular()} 里标记，由 {@code FareGraph} 补首尾边。
 */
final class ClientFareNetwork implements FareNetwork {

    private final List<FareStation> stations;
    private final List<FareRoute> routes;
    private final long signature;

    private ClientFareNetwork(List<FareStation> stations, List<FareRoute> routes, long signature) {
        this.stations = stations;
        this.routes = routes;
        this.signature = signature;
    }

    static ClientFareNetwork snapshot() {
        final List<FareStation> stationList = new ArrayList<>();
        for (final Station station : ClientData.STATIONS) {
            final BlockPos center = AreaBase.nonNullCorners(station) ? station.getCenter() : null;
            stationList.add(new FareStation(station.id, station.name, station.zone,
                    center == null ? 0.0 : center.getX() + 0.5,
                    center == null ? 0.0 : center.getZ() + 0.5));
        }
        stationList.sort(Comparator.comparingLong(FareStation::id));

        final List<FareRoute> routeList = new ArrayList<>();
        for (final Route route : ClientData.ROUTES) {
            final List<Long> stationIds = new ArrayList<>();
            for (final Route.RoutePlatform platform : route.platformIds) {
                final Station station = ClientData.DATA_CACHE.platformIdToStation.get(platform.platformId);
                if (station == null) {
                    continue;
                }
                if (stationIds.isEmpty() || stationIds.get(stationIds.size() - 1) != station.id) {
                    stationIds.add(station.id);
                }
            }
            if (stationIds.isEmpty()) {
                continue;
            }
            routeList.add(new FareRoute(route.id, route.name, stationIds,
                    route.circularState != Route.CircularState.NONE));
        }
        routeList.sort(Comparator.comparingLong(FareRoute::id));

        long signature = 17L;
        for (final FareStation station : stationList) {
            signature = signature * 31L + station.id();
            signature = signature * 31L + station.zone();
            signature = signature * 31L + Double.hashCode(station.x());
            signature = signature * 31L + Double.hashCode(station.z());
        }
        for (final FareRoute route : routeList) {
            signature = signature * 31L + route.id();
            for (final long stationId : route.stationIds()) {
                signature = signature * 31L + stationId;
            }
        }
        return new ClientFareNetwork(List.copyOf(stationList), List.copyOf(routeList), signature);
    }

    @Override
    public List<FareStation> stations() {
        return stations;
    }

    @Override
    public List<FareRoute> routes() {
        return routes;
    }

    long signature() {
        return signature;
    }
}
