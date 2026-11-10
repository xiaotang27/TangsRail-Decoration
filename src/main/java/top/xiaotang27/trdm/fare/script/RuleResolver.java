package top.xiaotang27.trdm.fare.script;

/**
 * 运行时解析“使用规则”积木引用的其他计费规则。
 * <p>
 * 返回 {@code null} 表示规则不存在、已停用或检测到循环引用；调用方会跳过该积木。
 */
@FunctionalInterface
public interface RuleResolver {

    /** @return 被引用规则在当前上下文下算出的票价（元），或 {@code null} 表示跳过。 */
    Double resolve(String ruleId, ScriptContext context);
}
