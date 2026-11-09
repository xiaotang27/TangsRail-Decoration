package top.xiaotang27.trdm.ticket;

import java.util.ArrayList;
import java.util.List;

/** 通票模板的默认值与查找。 */
public final class PassRules {

    private PassRules() {
    }

    /** 一日票 / 三日票 / 青春18 的默认模板（与旧版一致）。 */
    public static List<PassRule> defaults() {
        final List<PassRule> rules = new ArrayList<>();
        rules.add(new PassRule("day1", "一日票", 10, 1));
        rules.add(new PassRule("day3", "三日票", 25, 3));
        rules.add(new PassRule("seishun", "青春18券", 50, 5));
        return rules;
    }

    public static PassRule byId(List<PassRule> rules, String id) {
        if (rules == null || id == null) {
            return null;
        }
        for (final PassRule rule : rules) {
            if (id.equals(rule.id)) {
                return rule;
            }
        }
        return null;
    }

    public static String uniqueId(List<PassRule> rules, String prefix) {
        int index = 1;
        while (byId(rules, prefix + index) != null) {
            index++;
        }
        return prefix + index;
    }
}
