package top.xiaotang27.trdm.fare.script;

/** 通票脚本求值结果。 */
public record PassSettings(int priceEmeralds, int days, int discountPercent, int dailyLimit) {

    public PassSettings {
        priceEmeralds = Math.max(0, priceEmeralds);
        days = Math.max(1, days);
        discountPercent = Math.max(0, Math.min(100, discountPercent));
        dailyLimit = Math.max(0, dailyLimit);
    }

    /** 0 表示每天不限次。 */
    public boolean unlimitedDaily() {
        return dailyLimit <= 0;
    }
}
