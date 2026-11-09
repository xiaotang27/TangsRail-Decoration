package top.xiaotang27.trdm.fare.script;

/** 报价时暴露给脚本的只读上下文。 */
public record ScriptContext(long intervals, long meters, int fromZone, int toZone) {

    public long stations() {
        return intervals + 1L;
    }

    public int zoneDiff() {
        return Math.abs(toZone - fromZone);
    }
}
