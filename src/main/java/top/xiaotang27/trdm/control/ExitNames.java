package top.xiaotang27.trdm.control;

import mtr.data.Station;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 车站出口名的选项集。
 * <p>
 * MTR 里的出口经常是 {@code A1 / A2 / B1} 这种带数字后缀的（一个出入口分好几个口），
 * 但大站名牌上只有一个字母的位置，所以选择列表里除了 {@code A1}、{@code A2}，
 * 还要给出 {@code A}、{@code B} 这种「整个出入口」的选项；列表顺序也按
 * {@code A→A1→A2→B→B1} 排，而不是纯字符串排序把 {@code A1} 排在 {@code A} 前面。
 */
public final class ExitNames {

    private ExitNames() {
    }

    /** 出口名去掉数字后缀：{@code A1 → A}；本身没有数字后缀（{@code A}、{@code C}）返回自己。 */
    public static String groupKey(String exitName) {
        final String name = exitName == null ? "" : exitName.trim();
        int end = name.length();
        while (end > 0 && Character.isDigit(name.charAt(end - 1))) {
            end--;
        }
        if (end == 0 || end == name.length()) {
            return name;
        }
        return name.substring(0, end).trim();
    }

    /** 「整个出入口」那个名字：{@code A1 → A}；没有数字后缀或全是数字时返回空串（没有这种选项）。 */
    public static String baseOf(String exitName) {
        final String name = exitName == null ? "" : exitName.trim();
        int end = name.length();
        while (end > 0 && Character.isDigit(name.charAt(end - 1))) {
            end--;
        }
        if (end == 0 || end == name.length()) {
            return "";
        }
        return name.substring(0, end).trim();
    }

    /** 数字后缀：{@code A1 → 1}；没有数字后缀返回 null（排序时排在 {@code A1} 前面）。 */
    private static Integer numberPart(String exitName) {
        final String name = exitName == null ? "" : exitName.trim();
        int start = name.length();
        while (start > 0 && Character.isDigit(name.charAt(start - 1))) {
            start--;
        }
        if (start == name.length()) {
            return null;
        }
        try {
            return Integer.parseInt(name.substring(start));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 本车站所有可选的出口名：先按「字母 → 数字」排，{@code A} 在 {@code A1} 前面。 */
    public static List<String> options(Station station) {
        final List<String> result = new ArrayList<>();
        if (station == null || station.exits == null) {
            return result;
        }
        final Set<String> all = new LinkedHashSet<>();
        for (final String name : station.exits.keySet()) {
            all.add(name);
            final String base = baseOf(name);
            if (!base.isEmpty()) {
                all.add(base);
            }
        }
        result.addAll(all);
        result.sort(ExitNames::compare);
        return result;
    }

    /** 归到 {@code option} 这个出入口下面的真实出口名（{@code A → [A1, A2]}）。 */
    public static List<String> membersOf(Station station, String option) {
        final List<String> members = new ArrayList<>();
        if (station == null || station.exits == null || option == null) {
            return members;
        }
        for (final String name : station.exits.keySet()) {
            if (groupKey(name).equals(option)) {
                members.add(name);
            }
        }
        members.sort(ExitNames::compare);
        return members;
    }

    /** 这个出口名还算不算本站的有效选项（真实出口，或者 A1/A2 归并出来的「A」）。 */
    public static boolean isKnown(Station station, String exitName) {
        if (station == null || exitName == null || exitName.trim().isEmpty()) {
            return false;
        }
        return options(station).contains(exitName.trim());
    }

    /** 列表排序：按字母分组，组内「字母本身」在前，然后按数字大小。 */
    public static int compare(String a, String b) {
        final int byGroup = groupKey(a).compareToIgnoreCase(groupKey(b));
        if (byGroup != 0) {
            return byGroup;
        }
        final Integer numberA = numberPart(a);
        final Integer numberB = numberPart(b);
        if (numberA == null && numberB == null) {
            return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
        }
        if (numberA == null) {
            return -1;
        }
        if (numberB == null) {
            return 1;
        }
        if (!numberA.equals(numberB)) {
            return Integer.compare(numberA, numberB);
        }
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }
}
