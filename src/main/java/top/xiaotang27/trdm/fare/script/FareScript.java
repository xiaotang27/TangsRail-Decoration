package top.xiaotang27.trdm.fare.script;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条积木脚本：一个帽子块 + 若干节点。
 * <p>
 * v1 只有一种帽子：{@code on_fare}（当计算票价）。
 */
public final class FareScript {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_NODES = 200;

    public int schemaVersion = SCHEMA_VERSION;
    /** 帽子块节点 ID。 */
    public String hat = "";
    public final List<ScriptNode> nodes = new ArrayList<>();

    public ScriptNode node(String id) {
        if (id == null) {
            return null;
        }
        for (final ScriptNode node : nodes) {
            if (id.equals(node.id)) {
                return node;
            }
        }
        return null;
    }

    public FareScript copy() {
        final FareScript copy = new FareScript();
        copy.schemaVersion = schemaVersion;
        copy.hat = hat;
        for (final ScriptNode node : nodes) {
            copy.nodes.add(node.copy());
        }
        return copy;
    }

    public boolean isEmpty() {
        return hat == null || hat.isBlank() || nodes.isEmpty();
    }
}
