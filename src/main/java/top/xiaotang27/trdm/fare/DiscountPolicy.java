package top.xiaotang27.trdm.fare;

/** 交通卡优惠政策定义：只有稳定 ID 和显示名称。 */
public record DiscountPolicy(String id, String name) {

    public DiscountPolicy copy() {
        return new DiscountPolicy(id, name);
    }
}
