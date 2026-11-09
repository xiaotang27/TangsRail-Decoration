package top.xiaotang27.trdm.fare.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积木脚本里的一个节点。
 * <p>
 * 语句块用 {@link #next}（以及 if 的 {@link #otherwise}）串成执行栈；
 * 值块通过 {@link #inputs}（槽位名 → 节点 ID）嵌套引用。
 * 节点 ID 必须稳定；{@link #x}/{@link #y} 只给画布用，执行时忽略。
 */
public final class ScriptNode {

    public String id;
    public String type;
    /** 语句块的下一个语句；值块忽略。 */
    public String next;
    /** if 的否则分支。 */
    public String otherwise;
    public int x;
    public int y;
    /** 槽位名 → 值块节点 ID。 */
    public final Map<String, String> inputs = new LinkedHashMap<>();
    /** 标量参数（数字/文本/枚举），由各块自己解释。 */
    public final Map<String, String> fields = new LinkedHashMap<>();
    /** 阶梯表块的行。 */
    public final List<ScriptTier> tiers = new ArrayList<>();

    public ScriptNode() {
    }

    public ScriptNode(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public ScriptNode copy() {
        final ScriptNode copy = new ScriptNode(id, type);
        copy.next = next;
        copy.otherwise = otherwise;
        copy.x = x;
        copy.y = y;
        copy.inputs.putAll(inputs);
        copy.fields.putAll(fields);
        copy.tiers.addAll(tiers);
        return copy;
    }
}
