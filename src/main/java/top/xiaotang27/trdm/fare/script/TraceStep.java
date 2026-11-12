package top.xiaotang27.trdm.fare.script;

/** 单步执行轨迹。 */
public record TraceStep(int index, String nodeId, String type, String detail,
                        double fareBefore, double fareAfter) {
}
