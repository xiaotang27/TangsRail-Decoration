package top.xiaotang27.trdm.control;

import mtr.block.BlockPSDAPGDoorBase;
import mtr.data.Platform;
import mtr.data.RailwayData;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 屏蔽门集控装置的服务端逻辑：找门、按开/关值驱动门、算车站范围。
 * <p>
 */
public final class PsdDoorControl {

    /**
     * 以站台两端标记为基准，往四周扩展多少格来找门。
     * 站台标记通常就在屏蔽门旁边一两格，8 格足够覆盖站台宽度；再大就会摸到隔壁平行站台。
     */
    private static final int SCAN_SIDE = 8;
    private static final int SCAN_DOWN = 4;
    private static final int SCAN_UP = 4;
    /** 兜底：单个站台的扫描体积上限，防止有人把两个标记放到世界两端。 */
    private static final int MAX_SCAN_VOLUME = 400_000;

    private PsdDoorControl() {
    }

    /**
     * 一个站台全部屏蔽门的位置。
     *
     * @return 站台不存在时返回 null；存在但附近没有门时返回空表
     */
    public static List<BlockPos> findDoors(ServerLevel world, long platformId) {
        final RailwayData railwayData = RailwayData.getInstance(world);
        if (railwayData == null) {
            return null;
        }
        final Platform platform = findPlatform(railwayData, platformId);
        if (platform == null) {
            return null;
        }

        final List<BlockPos> markers = platform.getOrderedPositions(BlockPos.ZERO, false);
        if (markers.size() < 2) {
            return List.of();
        }
        final BlockPos marker1 = markers.get(0);
        final BlockPos marker2 = markers.get(1);

        final int minX = Math.min(marker1.getX(), marker2.getX()) - SCAN_SIDE;
        final int maxX = Math.max(marker1.getX(), marker2.getX()) + SCAN_SIDE;
        final int minY = Math.min(marker1.getY(), marker2.getY()) - SCAN_DOWN;
        final int maxY = Math.max(marker1.getY(), marker2.getY()) + SCAN_UP;
        final int minZ = Math.min(marker1.getZ(), marker2.getZ()) - SCAN_SIDE;
        final int maxZ = Math.max(marker1.getZ(), marker2.getZ()) + SCAN_SIDE;

        final long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_SCAN_VOLUME) {
            return List.of();
        }

        // 集控装置不一定在站台旁边（集控室可能在别处），扫到的区块先加载出来再数门
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }

        final List<BlockPos> doors = new ArrayList<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!(world.getBlockState(cursor).getBlock() instanceof BlockPSDAPGDoorBase)) {
                        continue;
                    }
                    if (nearestPlatformId(railwayData, cursor) != platform.id) {
                        continue;
                    }
                    doors.add(cursor.immutable());
                }
            }
        }
        return doors;
    }

    /**
     * 把一组门设成开 / 关。
     *
     * @return 真的找到并设置了的门扇数量
     */
    public static int apply(ServerLevel world, List<BlockPos> doors, boolean open) {
        return apply(world, doors, open ? BlockPSDAPGDoorBase.MAX_OPEN_VALUE : 0);
    }

    /**
     * 把一组门设成指定的开度（0 = 全关，{@link BlockPSDAPGDoorBase#MAX_OPEN_VALUE} = 全开）。
     * 集控装置按 tick 逐步调这个值，门才会像列车自动开关那样慢慢动。
     */
    public static int apply(ServerLevel world, List<BlockPos> doors, int openValue) {
        final int value = Mth.clamp(openValue, 0, BlockPSDAPGDoorBase.MAX_OPEN_VALUE);
        int count = 0;
        for (final BlockPos pos : doors) {
            final BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof BlockPSDAPGDoorBase.TileEntityPSDAPGDoorBase door) {
                door.setOpen(value);
                count++;
            }
        }
        return count;
    }

    /**
     * 集控装置所在的车站：先看它是不是落在某个车站范围内；
     * 不在范围内（比如集控室盖在站区外面）就退一步用「离它最近的站台」的车站。
     */
    public static Station deviceStation(ServerLevel world, BlockPos pos) {
        final RailwayData railwayData = RailwayData.getInstance(world);
        if (railwayData == null) {
            return null;
        }
        final Station station = StationScope.stationAt(railwayData.stations, pos);
        if (station != null) {
            return station;
        }
        final long closestPlatformId = RailwayData.getClosePlatformId(railwayData.platforms, railwayData.dataCache, pos);
        return closestPlatformId == 0 ? null : StationScope.stationOf(railwayData.dataCache.platformIdToStation, closestPlatformId);
    }

    /** 这个站台属于哪个车站（服务端）。 */
    public static Station platformStation(ServerLevel world, long platformId) {
        final RailwayData railwayData = RailwayData.getInstance(world);
        return railwayData == null ? null : StationScope.stationOf(railwayData.dataCache.platformIdToStation, platformId);
    }

    /**
     * 站台显示名（站名 + 站台号），找不到就返回 null。只用来给玩家发提示。
     */
    public static String platformLabel(ServerLevel world, long platformId) {
        final RailwayData railwayData = RailwayData.getInstance(world);
        if (railwayData == null) {
            return null;
        }
        final Platform platform = findPlatform(railwayData, platformId);
        if (platform == null) {
            return null;
        }
        final Station station = StationScope.stationOf(railwayData.dataCache.platformIdToStation, platformId);
        final String stationName = station == null ? "" : station.name;
        if (stationName == null || stationName.isEmpty()) {
            return platform.name;
        }
        return stationName + " · " + platform.name;
    }

    private static Platform findPlatform(RailwayData railwayData, long platformId) {
        for (final Platform platform : railwayData.platforms) {
            if (platform.id == platformId) {
                return platform;
            }
        }
        return null;
    }

    /**
     * 离这个坐标最近的站台 id（0 = 一个都没有）。距离按「到两个标记连成的线段」算，
     * 所以长站台中间的门也算得准，平行站台也不会互相抢门。
     */
    private static long nearestPlatformId(RailwayData railwayData, BlockPos pos) {
        final Vec3 point = Vec3.atCenterOf(pos);
        long nearestId = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (final Platform platform : railwayData.platforms) {
            final List<BlockPos> markers = platform.getOrderedPositions(BlockPos.ZERO, false);
            if (markers.size() < 2) {
                continue;
            }
            final double distance = distanceToSegment(point, Vec3.atCenterOf(markers.get(0)), Vec3.atCenterOf(markers.get(1)));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestId = platform.id;
            }
        }
        return nearestId;
    }

    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        final Vec3 segment = end.subtract(start);
        final double lengthSquared = segment.lengthSqr();
        if (lengthSquared < 1.0E-6) {
            return point.distanceTo(start);
        }
        final double t = Math.max(0, Math.min(1, point.subtract(start).dot(segment) / lengthSquared));
        return point.distanceTo(start.add(segment.scale(t)));
    }
}
