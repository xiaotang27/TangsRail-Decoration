package top.xiaotang27.trdm.ticket;

/** 可配置的通票模板：稳定 ID、显示名、售价（绿宝石）、有效小时数对应的天数。 */
public final class PassRule {

    public String id;
    public String name;
    public int priceEmeralds;
    public int days;

    public PassRule(String id, String name, int priceEmeralds, int days) {
        this.id = id;
        this.name = name;
        this.priceEmeralds = priceEmeralds;
        this.days = days;
    }

    public PassRule copy() {
        return new PassRule(id, name, priceEmeralds, days);
    }
}
