package top.xiaotang27.trdm.block;

import net.minecraft.util.StringRepresentable;

/**
 * 门楣 / 延伸板上可以装的两类顶部灯：
 * <ul>
 *     <li>{@link #NONE}：没装；</li>
 *     <li>{@link #PLAIN}：站台门延伸板顶部灯（模型 2，金属色，不可染色）；</li>
 *     <li>{@link #COLOR}：可染色的顶部灯（模型 3，颜色由刷子右键界面设置）。</li>
 * </ul>
 * 墙上荧光灯管（模型 1）是单独的一格数据，见 {@link PsdLightHolder#hasTubeLight()}。
 */
public enum PsdLightKind implements StringRepresentable {

    NONE("none"),
    PLAIN("plain"),
    COLOR("color");

    private final String name;

    PsdLightKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** NBT 里读到未知值时退回 {@link #NONE}，旧存档 / 拆包不会让方块实体坏掉。 */
    public static PsdLightKind byName(String name) {
        for (final PsdLightKind kind : values()) {
            if (kind.name.equals(name)) {
                return kind;
            }
        }
        return NONE;
    }
}
