package top.xiaotang27.trdm.fare.script;

import java.util.List;

/** 一次脚本试算的执行轨迹。 */
public record ExecutionTrace(List<TraceStep> steps, double finalFare) {

    public ExecutionTrace {
        steps = List.copyOf(steps);
    }
}
