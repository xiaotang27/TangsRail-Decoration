package top.xiaotang27.trdm.ticket;

import java.util.List;

/**
 * 内置通票目录。
 * <p>
 * v1 只有三种固定模板：一日票、三日票、青春18券；全部为“有效期内不限次、全网免费”。
 * 青春18的线路资格和自定义模板留到后续版本。
 */
public final class PassCatalog {

    /**
     * @param id            稳定 ID（写进物品 NBT）
     * @param nameKey       翻译键
     * @param priceEmeralds 售价（绿宝石）
     * @param days          连续自然日天数，从首次入闸当天起算
     */
    public record Definition(String id, String nameKey, int priceEmeralds, int days) {
    }

    public static final List<Definition> ALL = List.of(
            new Definition("day1", "item.trdm.pass.day1", 10, 1),
            new Definition("day3", "item.trdm.pass.day3", 25, 3),
            new Definition("seishun", "item.trdm.pass.seishun", 50, 5)
    );

    private PassCatalog() {
    }

    public static Definition byId(String id) {
        if (id == null) {
            return null;
        }
        for (final Definition definition : ALL) {
            if (definition.id().equals(id)) {
                return definition;
            }
        }
        return null;
    }
}
