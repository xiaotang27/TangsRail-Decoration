package top.xiaotang27.trdm.fare.script;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 积木脚本的校验、编译与执行。
 * <p>
 * v1 的块集合：
 * <ul>
 *   <li>帽子：{@code on_fare}</li>
 *   <li>语句：设置票价、增加票价、乘系数、封顶、保底、取整、如果/否则</li>
 *   <li>数值：数字、区间数、站数、里程、收费区差、加减乘除、最小/最大、区间阶梯表、里程阶梯表</li>
 *   <li>布尔：比较、且、或、非</li>
 * </ul>
 * 金额在脚本层用“元”（double），执行结束换算成分；执行有步数与深度上限。
 */
public final class ScriptCompiler {

    public static final int MAX_STEPS = 10_000;
    public static final int MAX_DEPTH = 64;

    private static final Set<String> STATEMENTS = Set.of(
            "on_fare", "set_fare", "add_fare", "multiply_fare", "cap_fare",
            "floor_fare", "round_fare", "if");
    private static final Set<String> NUMBER_VALUES = Set.of(
            "number", "context_intervals", "context_stations", "context_meters",
            "context_zone_diff", "add", "subtract", "multiply", "divide", "min", "max",
            "table_intervals", "table_meters");
    private static final Set<String> BOOLEAN_VALUES = Set.of("compare", "and", "or", "not");
    private static final Set<String> VALUE_STATEMENTS = Set.of(
            "set_fare", "add_fare", "multiply_fare", "cap_fare", "floor_fare");

    /** 编译结果：成功时 {@code compiled} 非空，失败时 {@code errors} 非空。 */
    public record Result(CompiledScript compiled, List<String> errors) {
    }

    private ScriptCompiler() {
    }

    public static Result compile(FareScript script) {
        final List<String> errors = new ArrayList<>();
        if (script == null) {
            errors.add("脚本为空");
            return new Result(null, List.copyOf(errors));
        }
        if (script.nodes.size() > FareScript.MAX_NODES) {
            errors.add("节点数量超过 " + FareScript.MAX_NODES);
            return new Result(null, List.copyOf(errors));
        }
        final java.util.Map<String, ScriptNode> nodes = new java.util.HashMap<>();
        int hats = 0;
        for (final ScriptNode node : script.nodes) {
            if (node.id == null || node.id.isBlank()) {
                errors.add("存在没有 ID 的节点");
                continue;
            }
            if (nodes.put(node.id, node) != null) {
                errors.add("节点 ID 重复: " + node.id);
            }
            if ("on_fare".equals(node.type)) {
                hats++;
            }
        }
        if (hats != 1) {
            errors.add("必须有且只有一个“当计算票价”帽子块");
        }
        final ScriptNode hat = nodes.get(script.hat);
        if (hat == null) {
            errors.add("帽子块 ID 无效: " + script.hat);
        } else if (!"on_fare".equals(hat.type)) {
            errors.add("帽子块类型必须是 on_fare");
        }
        if (!errors.isEmpty()) {
            return new Result(null, List.copyOf(errors));
        }

        final Validation validation = new Validation(nodes, errors);
        validation.validateStatement(hat);
        if (validation.reachableSetFare == 0) {
            errors.add("脚本没有执行到“设置票价”");
        }
        if (!errors.isEmpty()) {
            return new Result(null, List.copyOf(errors));
        }
        final FareScript copy = script.copy();
        final java.util.Map<String, ScriptNode> copyNodes = new java.util.HashMap<>();
        for (final ScriptNode node : copy.nodes) {
            copyNodes.put(node.id, node);
        }
        return new Result(new CompiledScript(copy, copyNodes), List.of());
    }

    // ===== 校验 =====

    private static final class Validation {

        private final java.util.Map<String, ScriptNode> nodes;
        private final List<String> errors;
        private final Set<String> visitingStatements = new LinkedHashSet<>();
        private final Set<String> visitedStatements = new LinkedHashSet<>();
        private final Set<String> visitingValues = new LinkedHashSet<>();
        private final java.util.Map<String, Boolean> visitedValues = new java.util.HashMap<>();
        private int reachableSetFare;

        private Validation(java.util.Map<String, ScriptNode> nodes, List<String> errors) {
            this.nodes = nodes;
            this.errors = errors;
        }

        private void validateStatement(ScriptNode node) {
            if (node == null) {
                return;
            }
            if (!STATEMENTS.contains(node.type)) {
                errors.add("未知的语句块: " + node.type + " (" + node.id + ")");
                return;
            }
            if (visitedStatements.contains(node.id)) {
                return;
            }
            if (!visitingStatements.add(node.id)) {
                errors.add("语句块存在环: " + node.id);
                return;
            }
            if ("set_fare".equals(node.type)) {
                reachableSetFare++;
            }
            if ("if".equals(node.type)) {
                validateInput(node, "condition", true);
            } else if (VALUE_STATEMENTS.contains(node.type)) {
                validateInput(node, "value", false);
            }
            if (node.next != null && !node.next.isBlank()) {
                final ScriptNode next = nodes.get(node.next);
                if (next == null) {
                    errors.add("语句块的下一块不存在: " + node.id + " -> " + node.next);
                } else if (!STATEMENTS.contains(next.type)) {
                    errors.add("next 必须指向语句块: " + node.id + " -> " + node.next);
                } else {
                    validateStatement(next);
                }
            }
            if ("if".equals(node.type) && node.otherwise != null && !node.otherwise.isBlank()) {
                final ScriptNode otherwise = nodes.get(node.otherwise);
                if (otherwise == null) {
                    errors.add("if 的否则分支不存在: " + node.id + " -> " + node.otherwise);
                } else if (!STATEMENTS.contains(otherwise.type)) {
                    errors.add("otherwise 必须指向语句块: " + node.id + " -> " + node.otherwise);
                } else {
                    validateStatement(otherwise);
                }
            }
            if ("if".equals(node.type) && node.after != null && !node.after.isBlank()) {
                final ScriptNode after = nodes.get(node.after);
                if (after == null) {
                    errors.add("if 之后的语句不存在: " + node.id + " -> " + node.after);
                } else if (!STATEMENTS.contains(after.type)) {
                    errors.add("after 必须指向语句块: " + node.id + " -> " + node.after);
                } else {
                    validateStatement(after);
                }
            }
            visitingStatements.remove(node.id);
            visitedStatements.add(node.id);
        }

        private void validateInput(ScriptNode statement, String slot, boolean booleanInput) {
            final String valueId = statement.inputs.get(slot);
            if (valueId == null || valueId.isBlank()) {
                if ("set_fare".equals(statement.type)) {
                    errors.add("“设置票价”缺少值: " + statement.id);
                } else {
                    errors.add("语句块缺少输入 " + slot + ": " + statement.id);
                }
                return;
            }
            validateValue(valueId, booleanInput);
        }

        private void validateValue(String id, boolean booleanInput) {
            final ScriptNode node = nodes.get(id);
            if (node == null) {
                errors.add("值块不存在: " + id);
                return;
            }
            if (visitedValues.containsKey(id)) {
                if (visitedValues.get(id) != booleanInput) {
                    errors.add("值块类型使用不一致: " + id);
                }
                return;
            }
            if (!visitingValues.add(id)) {
                errors.add("值块存在环: " + id);
                return;
            }
            if (booleanInput) {
                if (!BOOLEAN_VALUES.contains(node.type)) {
                    errors.add("这里需要布尔块，实际是: " + node.type + " (" + id + ")");
                } else {
                    validateBooleanFields(node);
                }
            } else {
                if (!NUMBER_VALUES.contains(node.type)) {
                    errors.add("这里需要数值块，实际是: " + node.type + " (" + id + ")");
                } else {
                    validateNumberFields(node);
                    for (final String slot : numberInputs(node.type)) {
                        final String child = node.inputs.get(slot);
                        if (child == null || child.isBlank()) {
                            errors.add("值块缺少输入 " + slot + ": " + id);
                        } else {
                            validateValue(child, false);
                        }
                    }
                }
            }
            visitingValues.remove(id);
            visitedValues.put(id, booleanInput);
        }

        private void validateNumberFields(ScriptNode node) {
            switch (node.type) {
                case "number" -> {
                    if (parseDouble(node.fields.get("value")) == null) {
                        errors.add("数字块的值无效: " + node.id);
                    }
                }
                case "table_intervals", "table_meters" -> validateTiers(node);
                default -> {
                }
            }
        }

        private void validateBooleanFields(ScriptNode node) {
            switch (node.type) {
                case "compare" -> {
                    final String op = node.fields.getOrDefault("op", "");
                    if (!Set.of("<", "<=", ">", ">=", "==", "!=").contains(op)) {
                        errors.add("比较运算符无效: " + op + " (" + node.id + ")");
                    }
                    for (final String slot : List.of("a", "b")) {
                        final String child = node.inputs.get(slot);
                        if (child == null || child.isBlank()) {
                            errors.add("比较块缺少输入 " + slot + ": " + node.id);
                        } else {
                            validateValue(child, false);
                        }
                    }
                }
                case "and", "or" -> {
                    for (final String slot : List.of("a", "b")) {
                        final String child = node.inputs.get(slot);
                        if (child == null || child.isBlank()) {
                            errors.add("逻辑块缺少输入 " + slot + ": " + node.id);
                        } else {
                            validateValue(child, true);
                        }
                    }
                }
                case "not" -> {
                    final String child = node.inputs.get("a");
                    if (child == null || child.isBlank()) {
                        errors.add("“非”缺少输入: " + node.id);
                    } else {
                        validateValue(child, true);
                    }
                }
                default -> {
                }
            }
        }

        private void validateTiers(ScriptNode node) {
            if (node.tiers.isEmpty()) {
                errors.add("阶梯表为空: " + node.id);
                return;
            }
            Double previous = null;
            boolean open = false;
            for (final ScriptTier tier : node.tiers) {
                if (tier.fareYuan() < 0) {
                    errors.add("阶梯表票价为负: " + node.id);
                }
                if (tier.upTo() == null) {
                    if (open) {
                        errors.add("阶梯表只能有一个“其它”行: " + node.id);
                    }
                    open = true;
                } else {
                    if (open) {
                        errors.add("“其它”行必须是最后一行: " + node.id);
                    }
                    if (previous != null && tier.upTo() <= previous) {
                        errors.add("阶梯表上限必须递增: " + node.id);
                    }
                    previous = tier.upTo();
                }
            }
        }

        private List<String> numberInputs(String type) {
            return switch (type) {
                case "add", "subtract", "multiply", "divide", "min", "max" -> List.of("a", "b");
                default -> List.of();
            };
        }
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

    // ===== 执行 =====

    public static final class CompiledScript {

        private final FareScript script;
        private final java.util.Map<String, ScriptNode> nodes;
        private int steps;

        private CompiledScript(FareScript script, java.util.Map<String, ScriptNode> nodes) {
            this.script = script;
            this.nodes = nodes;
        }

        public FareScript script() {
            return script;
        }

        /** @return 票价（元）；脚本决定不设置票价或运行失败时抛异常。 */
        public double run(ScriptContext context) {
            steps = 0;
            final State state = new State();
            final ScriptNode hat = nodes.get(script.hat);
            if (hat == null) {
                throw new ScriptException("缺少帽子块");
            }
            execute(hat.next, state, context, 0);
            if (!state.hasFare) {
                throw new ScriptException("脚本没有设置票价");
            }
            return state.fare;
        }

        private void execute(String statementId, State state, ScriptContext context, int depth) {
            if (depth > MAX_DEPTH) {
                throw new ScriptException("脚本递归过深");
            }
            String cursor = statementId;
            while (cursor != null && !cursor.isBlank()) {
                tick();
                final ScriptNode node = nodes.get(cursor);
                if (node == null) {
                    throw new ScriptException("语句块不存在: " + cursor);
                }
                switch (node.type) {
                    case "on_fare" -> cursor = node.next;
                    case "set_fare" -> {
                        state.fare = number(node.inputs.get("value"), context);
                        state.hasFare = true;
                        cursor = node.next;
                    }
                    case "add_fare" -> {
                        state.fare += number(node.inputs.get("value"), context);
                        state.hasFare = true;
                        cursor = node.next;
                    }
                    case "multiply_fare" -> {
                        state.fare *= number(node.inputs.get("value"), context);
                        state.hasFare = true;
                        cursor = node.next;
                    }
                    case "cap_fare" -> {
                        state.fare = Math.min(state.fare, number(node.inputs.get("value"), context));
                        state.hasFare = true;
                        cursor = node.next;
                    }
                    case "floor_fare" -> {
                        state.fare = Math.max(state.fare, number(node.inputs.get("value"), context));
                        state.hasFare = true;
                        cursor = node.next;
                    }
                    case "round_fare" -> {
                        state.fare = round(state.fare, node);
                        state.hasFare = true;
                        cursor = node.next;
                    }
                    case "if" -> {
                        final boolean condition = bool(node.inputs.get("condition"), context);
                        execute(condition ? node.next : node.otherwise, state, context, depth + 1);
                        cursor = node.after;
                    }
                    default -> throw new ScriptException("未知的语句块: " + node.type);
                }
            }
        }

        private double number(String nodeId, ScriptContext context) {
            tick();
            final ScriptNode node = nodes.get(nodeId);
            if (node == null) {
                throw new ScriptException("值块不存在: " + nodeId);
            }
            return switch (node.type) {
                case "number" -> {
                    final Double value = parseDouble(node.fields.get("value"));
                    if (value == null) {
                        throw new ScriptException("数字无效: " + nodeId);
                    }
                    yield value;
                }
                case "context_intervals" -> context.intervals();
                case "context_stations" -> context.stations();
                case "context_meters" -> context.meters();
                case "context_zone_diff" -> context.zoneDiff();
                case "add" -> number(node.inputs.get("a"), context) + number(node.inputs.get("b"), context);
                case "subtract" -> number(node.inputs.get("a"), context)
                        - number(node.inputs.get("b"), context);
                case "multiply" -> number(node.inputs.get("a"), context)
                        * number(node.inputs.get("b"), context);
                case "divide" -> {
                    final double divisor = number(node.inputs.get("b"), context);
                    if (Math.abs(divisor) < 1e-12) {
                        throw new ScriptException("除数为 0: " + nodeId);
                    }
                    yield number(node.inputs.get("a"), context) / divisor;
                }
                case "min" -> Math.min(number(node.inputs.get("a"), context),
                        number(node.inputs.get("b"), context));
                case "max" -> Math.max(number(node.inputs.get("a"), context),
                        number(node.inputs.get("b"), context));
                case "table_intervals" -> lookup(node, context.intervals());
                case "table_meters" -> lookup(node, context.meters());
                default -> throw new ScriptException("这里需要数值块: " + node.type);
            };
        }

        private boolean bool(String nodeId, ScriptContext context) {
            tick();
            final ScriptNode node = nodes.get(nodeId);
            if (node == null) {
                throw new ScriptException("布尔块不存在: " + nodeId);
            }
            return switch (node.type) {
                case "compare" -> compare(node, context);
                case "and" -> bool(node.inputs.get("a"), context) && bool(node.inputs.get("b"), context);
                case "or" -> bool(node.inputs.get("a"), context) || bool(node.inputs.get("b"), context);
                case "not" -> !bool(node.inputs.get("a"), context);
                default -> throw new ScriptException("这里需要布尔块: " + node.type);
            };
        }

        private boolean compare(ScriptNode node, ScriptContext context) {
            final double a = number(node.inputs.get("a"), context);
            final double b = number(node.inputs.get("b"), context);
            return switch (node.fields.getOrDefault("op", "<")) {
                case "<" -> a < b;
                case "<=" -> a <= b;
                case ">" -> a > b;
                case ">=" -> a >= b;
                case "==" -> Math.abs(a - b) < 1e-9;
                case "!=" -> Math.abs(a - b) >= 1e-9;
                default -> throw new ScriptException("比较运算符无效: " + node.fields.get("op"));
            };
        }

        private double lookup(ScriptNode node, long value) {
            double last = 0.0;
            for (final ScriptTier tier : node.tiers) {
                last = tier.fareYuan();
                if (tier.upTo() == null || value <= tier.upTo()) {
                    return tier.fareYuan();
                }
            }
            return last;
        }

        private double round(double fare, ScriptNode node) {
            final Double unitValue = parseDouble(node.fields.get("unit"));
            final double unit = unitValue == null || unitValue <= 0 ? 1.0 : unitValue;
            return switch (node.fields.getOrDefault("mode", "HALF")) {
                case "UP" -> Math.ceil(fare / unit) * unit;
                case "DOWN" -> Math.floor(fare / unit) * unit;
                default -> Math.floor(fare / unit + 0.5) * unit;
            };
        }

        private void tick() {
            if (++steps > MAX_STEPS) {
                throw new ScriptException("脚本执行步数超限");
            }
        }
    }

    private static final class State {
        private double fare;
        private boolean hasFare;
    }
}
