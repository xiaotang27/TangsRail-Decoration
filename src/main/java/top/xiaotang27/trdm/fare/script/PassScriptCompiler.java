package top.xiaotang27.trdm.fare.script;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 通票积木的校验与执行。
 * <p>
 * 通票脚本只负责售价、有效期、基础票价折扣和每日次数；适用票务规则范围在通用设置里。
 * 支持如果/否则、数字、四则运算、最小/最大和比较/与或非，不支持票价动作块。
 */
public final class PassScriptCompiler {

    public static final int MAX_STEPS = 10_000;
    public static final int MAX_DEPTH = 32;

    private static final Set<String> STATEMENTS = Set.of(
            "on_pass", "pass_price", "pass_days", "pass_discount", "pass_daily_limit", "if");
    private static final Set<String> VALUE_STATEMENTS = Set.of(
            "pass_price", "pass_days", "pass_discount", "pass_daily_limit");
    private static final Set<String> NUMBER_VALUES = Set.of(
            "number", "add", "subtract", "multiply", "divide", "min", "max");
    private static final Set<String> BOOLEAN_VALUES = Set.of("compare", "and", "or", "not");

    private PassScriptCompiler() {
    }

    public static PassSettings evaluate(FareScript script) {
        final Map<String, ScriptNode> nodes = validate(script);
        final ScriptNode hat = nodes.get(script.hat);
        final State state = new State();
        execute(nodes, hat.next, state, new Steps(), 0);
        if (!state.hasPrice || !state.hasDays) {
            throw new ScriptException("通票脚本必须设置售价和有效期");
        }
        return new PassSettings(state.price, state.days, state.discount, state.dailyLimit);
    }

    private static Map<String, ScriptNode> validate(FareScript script) {
        if (script == null) {
            throw new ScriptException("通票脚本为空");
        }
        final Map<String, ScriptNode> nodes = new HashMap<>();
        for (final ScriptNode node : script.nodes) {
            if (node.id == null || node.id.isBlank()) {
                throw new ScriptException("存在没有 ID 的通票节点");
            }
            if (nodes.put(node.id, node) != null) {
                throw new ScriptException("通票节点 ID 重复: " + node.id);
            }
        }
        final ScriptNode hat = nodes.get(script.hat);
        if (hat == null || !"on_pass".equals(hat.type)) {
            throw new ScriptException("通票缺少“当设置通票”帽子块");
        }
        final Set<String> checked = new HashSet<>();
        final Set<String> visiting = new HashSet<>();
        validateStatement(hat, nodes, checked, visiting);
        return nodes;
    }

    private static void validateStatement(ScriptNode node, Map<String, ScriptNode> nodes,
                                          Set<String> checked, Set<String> visiting) {
        if (node == null || checked.contains(node.id)) {
            return;
        }
        if (!STATEMENTS.contains(node.type)) {
            throw new ScriptException("通票脚本不支持的语句块: " + node.type);
        }
        if (!visiting.add(node.id)) {
            throw new ScriptException("通票语句存在环: " + node.id);
        }
        if (VALUE_STATEMENTS.contains(node.type)) {
            final String input = node.inputs.get("value");
            if (input == null || input.isBlank()) {
                throw new ScriptException("通票设置缺少数值: " + node.id);
            }
            validateNumber(input, nodes, new HashSet<>());
        }
        if ("if".equals(node.type)) {
            final String condition = node.inputs.get("condition");
            if (condition == null || condition.isBlank()) {
                throw new ScriptException("如果块缺少条件: " + node.id);
            }
            validateBoolean(condition, nodes, new HashSet<>());
            validateStatement(nodes.get(node.next), nodes, checked, visiting);
            validateStatement(nodes.get(node.otherwise), nodes, checked, visiting);
        } else if (node.next != null && !node.next.isBlank()) {
            final ScriptNode next = nodes.get(node.next);
            if (next == null) {
                throw new ScriptException("通票下一块不存在: " + node.next);
            }
            validateStatement(next, nodes, checked, visiting);
        }
        if ("if".equals(node.type) && node.after != null && !node.after.isBlank()) {
            validateStatement(nodes.get(node.after), nodes, checked, visiting);
        }
        visiting.remove(node.id);
        checked.add(node.id);
    }

    private static void validateNumber(String id, Map<String, ScriptNode> nodes, Set<String> visiting) {
        final ScriptNode node = nodes.get(id);
        if (node == null) {
            throw new ScriptException("通票数值块不存在: " + id);
        }
        if (!visiting.add(id)) {
            throw new ScriptException("通票数值块存在环: " + id);
        }
        if (!NUMBER_VALUES.contains(node.type)) {
            throw new ScriptException("通票这里需要数值块: " + node.type);
        }
        if ("number".equals(node.type)) {
            parseDouble(node.fields.get("value"));
        } else {
            for (final String slot : java.util.List.of("a", "b")) {
                final String child = node.inputs.get(slot);
                if (child == null || child.isBlank()) {
                    throw new ScriptException("通票数值块缺少输入: " + node.id);
                }
                validateNumber(child, nodes, visiting);
            }
        }
        visiting.remove(id);
    }

    private static void validateBoolean(String id, Map<String, ScriptNode> nodes, Set<String> visiting) {
        final ScriptNode node = nodes.get(id);
        if (node == null) {
            throw new ScriptException("通票条件块不存在: " + id);
        }
        if (!visiting.add(id)) {
            throw new ScriptException("通票条件块存在环: " + id);
        }
        if (!BOOLEAN_VALUES.contains(node.type)) {
            throw new ScriptException("通票这里需要条件块: " + node.type);
        }
        if ("compare".equals(node.type)) {
            for (final String slot : java.util.List.of("a", "b")) {
                final String child = node.inputs.get(slot);
                if (child == null || child.isBlank()) {
                    throw new ScriptException("通票比较块缺少输入: " + node.id);
                }
                validateNumber(child, nodes, visiting);
            }
        } else if ("and".equals(node.type) || "or".equals(node.type)) {
            for (final String slot : java.util.List.of("a", "b")) {
                final String child = node.inputs.get(slot);
                if (child == null || child.isBlank()) {
                    throw new ScriptException("通票条件块缺少输入: " + node.id);
                }
                validateBoolean(child, nodes, visiting);
            }
        } else {
            final String child = node.inputs.get("a");
            if (child == null || child.isBlank()) {
                throw new ScriptException("通票条件块缺少输入: " + node.id);
            }
            validateBoolean(child, nodes, visiting);
        }
        visiting.remove(id);
    }

    private static void execute(Map<String, ScriptNode> nodes, String cursor, State state,
                                Steps steps, int depth) {
        if (depth > MAX_DEPTH) {
            throw new ScriptException("通票脚本递归过深");
        }
        while (cursor != null && !cursor.isBlank()) {
            steps.tick();
            final ScriptNode node = nodes.get(cursor);
            if (node == null) {
                throw new ScriptException("通票语句块不存在: " + cursor);
            }
            switch (node.type) {
                case "on_pass" -> cursor = node.next;
                case "pass_price" -> {
                    state.price = (int) Math.round(number(nodes, node.inputs.get("value"), steps));
                    state.hasPrice = true;
                    cursor = node.next;
                }
                case "pass_days" -> {
                    state.days = (int) Math.round(number(nodes, node.inputs.get("value"), steps));
                    state.hasDays = true;
                    cursor = node.next;
                }
                case "pass_discount" -> {
                    state.discount = (int) Math.round(number(nodes, node.inputs.get("value"), steps));
                    cursor = node.next;
                }
                case "pass_daily_limit" -> {
                    state.dailyLimit = (int) Math.round(number(nodes, node.inputs.get("value"), steps));
                    cursor = node.next;
                }
                case "if" -> {
                    final boolean condition = bool(nodes, node.inputs.get("condition"), steps);
                    execute(nodes, condition ? node.next : node.otherwise, state, steps, depth + 1);
                    cursor = node.after;
                }
                default -> throw new ScriptException("未知通票语句块: " + node.type);
            }
        }
    }

    private static double number(Map<String, ScriptNode> nodes, String id, Steps steps) {
        steps.tick();
        final ScriptNode node = nodes.get(id);
        if (node == null) {
            throw new ScriptException("通票数值块不存在: " + id);
        }
        return switch (node.type) {
            case "number" -> {
                final Double value = parseDouble(node.fields.get("value"));
                if (value == null) {
                    throw new ScriptException("通票数字无效: " + id);
                }
                yield value;
            }
            case "add" -> number(nodes, node.inputs.get("a"), steps)
                    + number(nodes, node.inputs.get("b"), steps);
            case "subtract" -> number(nodes, node.inputs.get("a"), steps)
                    - number(nodes, node.inputs.get("b"), steps);
            case "multiply" -> number(nodes, node.inputs.get("a"), steps)
                    * number(nodes, node.inputs.get("b"), steps);
            case "divide" -> {
                final double divisor = number(nodes, node.inputs.get("b"), steps);
                if (Math.abs(divisor) < 1e-12) {
                    throw new ScriptException("通票除数为 0");
                }
                yield number(nodes, node.inputs.get("a"), steps) / divisor;
            }
            case "min" -> Math.min(number(nodes, node.inputs.get("a"), steps),
                    number(nodes, node.inputs.get("b"), steps));
            case "max" -> Math.max(number(nodes, node.inputs.get("a"), steps),
                    number(nodes, node.inputs.get("b"), steps));
            default -> throw new ScriptException("通票这里需要数值块: " + node.type);
        };
    }

    private static boolean bool(Map<String, ScriptNode> nodes, String id, Steps steps) {
        steps.tick();
        final ScriptNode node = nodes.get(id);
        if (node == null) {
            throw new ScriptException("通票条件块不存在: " + id);
        }
        return switch (node.type) {
            case "compare" -> {
                final double a = number(nodes, node.inputs.get("a"), steps);
                final double b = number(nodes, node.inputs.get("b"), steps);
                yield switch (node.fields.getOrDefault("op", ">=")) {
                    case "<" -> a < b;
                    case "<=" -> a <= b;
                    case ">" -> a > b;
                    case ">=" -> a >= b;
                    case "==" -> Math.abs(a - b) < 1e-9;
                    case "!=" -> Math.abs(a - b) >= 1e-9;
                    default -> false;
                };
            }
            case "and" -> bool(nodes, node.inputs.get("a"), steps)
                    && bool(nodes, node.inputs.get("b"), steps);
            case "or" -> bool(nodes, node.inputs.get("a"), steps)
                    || bool(nodes, node.inputs.get("b"), steps);
            case "not" -> !bool(nodes, node.inputs.get("a"), steps);
            default -> false;
        };
    }

    private static Double parseDouble(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static final class State {
        private int price;
        private int days = 1;
        private int discount = 100;
        private int dailyLimit;
        private boolean hasPrice;
        private boolean hasDays;
    }

    private static final class Steps {
        private int count;

        private void tick() {
            if (++count > MAX_STEPS) {
                throw new ScriptException("通票脚本执行步数超限");
            }
        }
    }
}
