package top.xiaotang27.trdm.ticket;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * TRDM 票务统一时钟。
 * <p>
 * 系统时间模式直接使用现实时间；游戏时间模式把 Minecraft 的一个游戏日映射为 24 小时，
 * 这样通票有效期、交易记录和连续计费窗口都会按游戏内日期/时刻推进。
 * 服务端和客户端每个 tick 各更新一次游戏时间。
 */
public final class TrdmClock {

    public static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    public static final long TICKS_PER_DAY = 24_000L;

    private static final DateTimeFormatter SYSTEM_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static volatile TimeBasis basis = TimeBasis.SYSTEM;
    private static volatile long gameMillis;

    private TrdmClock() {
    }

    public static TimeBasis basis() {
        return basis;
    }

    public static void setBasis(TimeBasis value) {
        basis = value == null ? TimeBasis.SYSTEM : value;
    }

    public static long now() {
        return basis == TimeBasis.GAME ? gameMillis : System.currentTimeMillis();
    }

    public static synchronized void updateGameTime(long dayTime) {
        final long day = Math.floorDiv(dayTime, TICKS_PER_DAY);
        final long tickOfDay = Math.floorMod(dayTime, TICKS_PER_DAY);
        gameMillis = day * DAY_MILLIS + Math.round(tickOfDay * (double) DAY_MILLIS / TICKS_PER_DAY);
    }

    public static void updateGameTime(Level level) {
        if (level != null) {
            updateGameTime(level.getDayTime());
        }
    }

    /** 当前“日”编号；系统时间按本地日期，游戏时间按游戏日。 */
    public static long dayKey() {
        if (basis == TimeBasis.GAME) {
            return Math.floorDiv(gameMillis, DAY_MILLIS);
        }
        return LocalDate.now().toEpochDay();
    }

    /** 把票务时间戳格式化为当前时间依据下的可读文本。 */
    public static String format(long millis) {
        if (basis == TimeBasis.GAME) {
            final long day = Math.floorDiv(millis, DAY_MILLIS);
            final long rem = Math.floorMod(millis, DAY_MILLIS);
            final double hours = (rem * 24.0 / DAY_MILLIS + 6.0) % 24.0;
            final int hour = (int) hours;
            final int minute = (int) ((hours - hour) * 60.0);
            final String time = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
            return Component.translatable("gui.trdm.time.game_format", day + 1L, time).getString();
        }
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(SYSTEM_FORMAT);
    }
}
