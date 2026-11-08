package top.xiaotang27.trdm.fare;

/**
 * 计费快照里的一个车站。
 * <p>
 * 只保留算价需要的信息：车站 ID、显示名、收费区、XZ 坐标（1 格 = 1 米）。
 * 与 MTR 的 {@code mtr.data.Station} 解耦，这样计费引擎可以脱离客户端做单元测试，
 * 以后接服务端权威时也只需要把 {@code RailwayData} 转成同一份快照。
 */
public record FareStation(long id, String name, int zone, double x, double z) {

    public FareStation {
        name = name == null ? "" : name;
    }

    public String displayName() {
        return name.isBlank() ? Long.toString(id) : name;
    }
}
