package top.xiaotang27.trdm.ticket;

/**
 * 交通卡连续计费判定。
 * <p>
 * 上一段出站后 10 分钟内，在“同名但不同付费区”的另一个车站再次入闸，
 * 视为同一段连续行程。10 分钟按 {@link TrdmClock} 当前的时间依据计算。
 */
public final class JourneyRules {

    /** 10 分钟。 */
    public static final long WINDOW_MILLIS = 10L * 60L * 1000L;

    private JourneyRules() {
    }

    public static boolean canContinue(long lastExitStation, String lastName, int lastExitZone,
                                      long lastExitMillis, String lastDimension,
                                      long currentStation, String currentName, int currentZone,
                                      String currentDimension, long nowMillis) {
        if (lastExitStation == 0L || lastExitMillis <= 0L) {
            return false;
        }
        if (nowMillis < lastExitMillis || nowMillis - lastExitMillis > WINDOW_MILLIS) {
            return false;
        }
        if (lastExitStation == currentStation) {
            return false;
        }
        if (lastExitZone == currentZone) {
            return false;
        }
        if (!normalize(lastDimension).equals(normalize(currentDimension))) {
            return false;
        }
        final String previous = normalize(lastName);
        return !previous.isEmpty() && previous.equals(normalize(currentName));
    }

    /** 车站名去掉 MTR 的「|英文名/颜色」后缀后比较。 */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        final int bar = name.indexOf('|');
        return (bar >= 0 ? name.substring(0, bar) : name).trim();
    }
}
