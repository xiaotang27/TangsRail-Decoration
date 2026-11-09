package top.xiaotang27.trdm.fare.script;

import java.util.HashSet;
import java.util.Set;

/**
 * 积木语句树的纯模型操作：分支链、嵌套、移动、删除和值引用清理。
 * <p>
 * 语句链按“帽子的 next → 普通语句 next / if 的 after”串起来；
 * if 的 {@code next} 是“那么”分支，{@code otherwise} 是“否则”分支。
 * 这里不涉及画布坐标和界面状态，界面与单元测试共用同一套结构操作。
 */
public final class ScriptTree {

    /** 语句链属于哪里。 */
    public enum Branch {
        /** 帽子块之后的主链。 */
        TOP,
        /** if 的“那么”分支。 */
        THEN,
        /** if 的“否则”分支。 */
        ELSE
    }

    /**
     * 一条语句链的起点。
     *
     * @param ownerId 分支拥有者：TOP 时为空串，THEN/ELSE 时是 if 节点 ID
     */
    public record Slot(String ownerId, Branch branch) {

        public static Slot top() {
            return new Slot("", Branch.TOP);
        }
    }

    /** 某个值节点被哪个节点的哪个输入引用。 */
    public record ValueOwner(String ownerId, String slot) {
    }

    private ScriptTree() {
    }

    /** 分支槽必须属于一个 if，且不能把语句塞进它自己的分支。 */
    private static boolean validSlot(FareScript script, Slot slot, String id) {
        if (slot.branch() == Branch.TOP) {
            return true;
        }
        if (slot.ownerId() == null || slot.ownerId().equals(id)) {
            return false;
        }
        final ScriptNode owner = script.node(slot.ownerId());
        if (owner == null || !"if".equals(owner.type)) {
            return false;
        }
        return !isDescendant(script, owner.id, id);
    }

    // ===== 链操作 =====

    public static String first(FareScript script, Slot slot) {
        if (script == null || slot == null) {
            return null;
        }
        if (slot.branch() == Branch.TOP) {
            final ScriptNode hat = script.node(script.hat);
            return hat == null ? null : blankToNull(hat.next);
        }
        final ScriptNode owner = script.node(slot.ownerId());
        if (owner == null) {
            return null;
        }
        return blankToNull(slot.branch() == Branch.THEN ? owner.next : owner.otherwise);
    }

    public static void setFirst(FareScript script, Slot slot, String id) {
        if (script == null || slot == null) {
            return;
        }
        if (slot.branch() == Branch.TOP) {
            final ScriptNode hat = script.node(script.hat);
            if (hat != null) {
                hat.next = id;
            }
            return;
        }
        final ScriptNode owner = script.node(slot.ownerId());
        if (owner == null) {
            return;
        }
        if (slot.branch() == Branch.THEN) {
            owner.next = id;
        } else {
            owner.otherwise = id;
        }
    }

    /** 同一语句链里的下一个语句；if 的整体后继是 after。 */
    public static String next(FareScript script, String statementId) {
        if (script == null || statementId == null) {
            return null;
        }
        final ScriptNode node = script.node(statementId);
        if (node == null) {
            return null;
        }
        return blankToNull("if".equals(node.type) ? node.after : node.next);
    }

    public static void setNext(FareScript script, String statementId, String id) {
        if (script == null || statementId == null) {
            return;
        }
        final ScriptNode node = script.node(statementId);
        if (node == null) {
            return;
        }
        if ("if".equals(node.type)) {
            node.after = id;
        } else {
            node.next = id;
        }
    }

    public static void clearLink(FareScript script, String statementId) {
        setNext(script, statementId, null);
    }

    /** 追加到分支末尾；节点若已在链上会先被摘下。 */
    public static void append(FareScript script, Slot slot, String id) {
        if (script == null || slot == null || script.node(id) == null || !validSlot(script, slot, id)) {
            return;
        }
        detach(script, id);
        final String firstId = first(script, slot);
        if (firstId == null) {
            setFirst(script, slot, id);
            clearLink(script, id);
            return;
        }
        String cursor = firstId;
        String last = firstId;
        int guard = 0;
        while (cursor != null && guard++ < FareScript.MAX_NODES) {
            last = cursor;
            cursor = next(script, cursor);
        }
        setNext(script, last, id);
        clearLink(script, id);
    }

    /** 插入到分支里的 beforeId 之前；beforeId 为空时追加。 */
    public static void insertBefore(FareScript script, Slot slot, String id, String beforeId) {
        if (script == null || slot == null || script.node(id) == null || !validSlot(script, slot, id)) {
            return;
        }
        detach(script, id);
        final String firstId = first(script, slot);
        if (firstId == null) {
            setFirst(script, slot, id);
            clearLink(script, id);
            return;
        }
        if (beforeId == null || beforeId.isBlank() || beforeId.equals(id)) {
            append(script, slot, id);
            return;
        }
        if (firstId.equals(beforeId)) {
            setFirst(script, slot, id);
            setNext(script, id, beforeId);
            return;
        }
        String previous = firstId;
        String cursor = next(script, firstId);
        int guard = 0;
        while (cursor != null && guard++ < FareScript.MAX_NODES) {
            if (cursor.equals(beforeId)) {
                setNext(script, previous, id);
                setNext(script, id, beforeId);
                return;
            }
            previous = cursor;
            cursor = next(script, cursor);
        }
        append(script, slot, id);
    }

    /**
     * 从语句树中摘下一个语句（保留它自己的分支和值），并清空它与旧链的连接。
     *
     * @return 是否找到并摘下
     */
    public static boolean detach(FareScript script, String id) {
        if (script == null || id == null) {
            return false;
        }
        final boolean found = detachFrom(script, Slot.top(), id, new HashSet<>());
        if (found) {
            clearLink(script, id);
        }
        return found;
    }

    private static boolean detachFrom(FareScript script, Slot slot, String id, Set<Slot> visited) {
        if (!visited.add(slot)) {
            return false;
        }
        final String firstId = first(script, slot);
        String previous = null;
        String cursor = firstId;
        int guard = 0;
        while (cursor != null && guard++ < FareScript.MAX_NODES) {
            final String next = next(script, cursor);
            if (cursor.equals(id)) {
                if (previous == null) {
                    setFirst(script, slot, next);
                } else {
                    setNext(script, previous, next);
                }
                return true;
            }
            previous = cursor;
            cursor = next;
        }
        cursor = firstId;
        guard = 0;
        while (cursor != null && guard++ < FareScript.MAX_NODES) {
            final ScriptNode node = script.node(cursor);
            final String next = next(script, cursor);
            if (node != null && "if".equals(node.type)) {
                if (detachFrom(script, new Slot(node.id, Branch.THEN), id, visited)
                        || detachFrom(script, new Slot(node.id, Branch.ELSE), id, visited)) {
                    return true;
                }
            }
            cursor = next;
        }
        return false;
    }

    /** candidate 是否在 ancestor 的“那么/否则”分支里（after 不算）。 */
    public static boolean isDescendant(FareScript script, String candidate, String ancestor) {
        if (script == null || candidate == null || ancestor == null) {
            return false;
        }
        final ScriptNode node = script.node(ancestor);
        if (node == null || !"if".equals(node.type)) {
            return false;
        }
        return descendantIn(script, ancestor, candidate, new HashSet<>());
    }

    private static boolean descendantIn(FareScript script, String statementId, String target, Set<String> seen) {
        if (statementId == null || !seen.add(statementId)) {
            return false;
        }
        final ScriptNode node = script.node(statementId);
        if (node == null) {
            return false;
        }
        for (final String child : new String[]{node.next, node.otherwise}) {
            if (child == null || child.isBlank()) {
                continue;
            }
            if (child.equals(target) || descendantIn(script, child, target, seen)) {
                return true;
            }
        }
        return false;
    }

    public static int countStatements(FareScript script) {
        final Set<String> seen = new HashSet<>();
        countFrom(script, first(script, Slot.top()), seen);
        return seen.size();
    }

    private static void countFrom(FareScript script, String firstId, Set<String> seen) {
        String cursor = firstId;
        int guard = 0;
        while (cursor != null && guard++ < FareScript.MAX_NODES) {
            if (!seen.add(cursor) || script.node(cursor) == null) {
                return;
            }
            final ScriptNode node = script.node(cursor);
            if ("if".equals(node.type)) {
                countFrom(script, node.next, seen);
                countFrom(script, node.otherwise, seen);
            }
            cursor = next(script, cursor);
        }
    }

    /** 收集一个语句及其嵌套分支、值槽（不包含 after 之后的兄弟）。 */
    public static void collectSubtree(FareScript script, String id, Set<String> out) {
        if (script == null || id == null || out == null) {
            return;
        }
        final ScriptNode node = script.node(id);
        if (node == null || !out.add(id)) {
            return;
        }
        for (final String valueId : node.inputs.values()) {
            collectValueTree(script, valueId, out);
        }
        if ("if".equals(node.type)) {
            collectSubtree(script, node.next, out);
            collectSubtree(script, node.otherwise, out);
        }
    }

    public static void collectValueTree(FareScript script, String id, Set<String> out) {
        if (script == null || id == null || out == null || !out.add(id)) {
            return;
        }
        final ScriptNode node = script.node(id);
        if (node == null) {
            return;
        }
        for (final String child : node.inputs.values()) {
            collectValueTree(script, child, out);
        }
    }

    /** 删除从帽子块不可达的语句和值节点。 */
    public static void prune(FareScript script) {
        if (script == null) {
            return;
        }
        final Set<String> reachable = new HashSet<>();
        final ScriptNode hat = script.node(script.hat);
        if (hat != null) {
            reachable.add(hat.id);
            collectReachable(script, hat.next, reachable);
        }
        script.nodes.removeIf(node -> !reachable.contains(node.id));
    }

    private static void collectReachable(FareScript script, String firstId, Set<String> reachable) {
        String cursor = firstId;
        int guard = 0;
        while (cursor != null && guard++ < FareScript.MAX_NODES) {
            if (!reachable.add(cursor)) {
                return;
            }
            final ScriptNode node = script.node(cursor);
            if (node == null) {
                return;
            }
            for (final String valueId : node.inputs.values()) {
                collectValueReachable(script, valueId, reachable);
            }
            if ("if".equals(node.type)) {
                collectReachable(script, node.next, reachable);
                collectReachable(script, node.otherwise, reachable);
            }
            cursor = next(script, cursor);
        }
    }

    private static void collectValueReachable(FareScript script, String valueId, Set<String> reachable) {
        if (valueId == null || !reachable.add(valueId)) {
            return;
        }
        final ScriptNode node = script.node(valueId);
        if (node == null) {
            return;
        }
        for (final String child : node.inputs.values()) {
            collectValueReachable(script, child, reachable);
        }
    }

    // ===== 值引用 =====

    public static ValueOwner ownerOfValue(FareScript script, String valueId) {
        if (script == null || valueId == null) {
            return null;
        }
        for (final ScriptNode node : script.nodes) {
            for (final java.util.Map.Entry<String, String> entry : node.inputs.entrySet()) {
                if (valueId.equals(entry.getValue())) {
                    return new ValueOwner(node.id, entry.getKey());
                }
            }
        }
        return null;
    }

    /** 找到引用 valueId 的最内层语句节点。 */
    public static String containingStatement(FareScript script, String valueId) {
        if (script == null || valueId == null) {
            return null;
        }
        return findContaining(script, first(script, Slot.top()), valueId,
                new HashSet<>(), new HashSet<>());
    }

    private static String findContaining(FareScript script, String statementId, String target,
                                         Set<String> visitedStatements, Set<String> visitedValues) {
        String cursor = statementId;
        int guard = 0;
        while (cursor != null && guard++ < FareScript.MAX_NODES) {
            if (!visitedStatements.add(cursor)) {
                return null;
            }
            final ScriptNode statement = script.node(cursor);
            if (statement == null) {
                return null;
            }
            for (final String valueId : statement.inputs.values()) {
                if (valueContains(script, valueId, target, visitedValues)) {
                    return cursor;
                }
            }
            if ("if".equals(statement.type)) {
                final String inThen = findContaining(script, statement.next, target,
                        visitedStatements, visitedValues);
                if (inThen != null) {
                    return inThen;
                }
                final String inElse = findContaining(script, statement.otherwise, target,
                        visitedStatements, visitedValues);
                if (inElse != null) {
                    return inElse;
                }
            }
            cursor = next(script, cursor);
        }
        return null;
    }

    private static boolean valueContains(FareScript script, String valueId, String target,
                                         Set<String> visitedValues) {
        if (valueId == null) {
            return false;
        }
        if (valueId.equals(target)) {
            return true;
        }
        if (!visitedValues.add(valueId)) {
            return false;
        }
        final ScriptNode node = script.node(valueId);
        if (node == null) {
            return false;
        }
        for (final String child : node.inputs.values()) {
            if (valueContains(script, child, target, visitedValues)) {
                return true;
            }
        }
        return false;
    }

    public static void removeValueReferences(FareScript script, String valueId) {
        if (script == null || valueId == null) {
            return;
        }
        for (final ScriptNode node : script.nodes) {
            node.inputs.entrySet().removeIf(entry -> valueId.equals(entry.getValue()));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
