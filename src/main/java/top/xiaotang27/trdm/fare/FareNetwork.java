package top.xiaotang27.trdm.fare;

import java.util.List;

/**
 * 计费图的输入：车站快照 + 线路车站序列。
 * <p>
 * 客户端由 {@code ClientFareNetwork} 从 MTR 数据生成；单元测试用假数据实现；
 * 以后服务端权威版本由 {@code RailwayData} 生成。
 */
public interface FareNetwork {

    List<FareStation> stations();

    List<FareRoute> routes();
}
