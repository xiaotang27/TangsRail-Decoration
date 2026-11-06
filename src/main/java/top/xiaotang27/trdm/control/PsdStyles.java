package top.xiaotang27.trdm.control;

/**
 * 屏蔽门外观样式表：吊版（门楣上那几块板）和玻璃装饰线各自可选，样式 id 存在门楣的方块实体里。
 * <p>
 */
public final class PsdStyles {

    /** 吊版：0 = 天津地铁站台门吊板。 */
    public static final int HEADER_TIANJIN = 0;
    /** 吊版：1 = 津滨轻轨风格站台门吊板（参考 Frame 88–91，素材前缀 bmt）。 */
    public static final int HEADER_JINBIN = 1;
    /** 吊版：2 = 津滨轻轨风格站台门吊板（改造，参考 Frame 92–94，素材前缀 trt_bmt）。 */
    public static final int HEADER_JINBIN_RETROFIT = 2;
    /** 玻璃装饰线：0 = 天津地铁站台装饰线（样式1）。 */
    public static final int BAND_TIANJIN_1 = 0;
    /**
     * 玻璃装饰线：1 = 只有人字纹的版本 —— 没有整条底色带、没有中央 slogan，
     * 人字本身染成线路色；人字的形状、节距、位置和样式1 完全一样。
     */
    public static final int BAND_CHEVRON_ONLY = 1;

    public static final int DEFAULT_HEADER = HEADER_TIANJIN;
    public static final int DEFAULT_BAND = BAND_TIANJIN_1;

    /** 界面上可选的全部吊版样式（顺序就是列表顺序）。 */
    public static final int[] HEADER_STYLES = {HEADER_TIANJIN, HEADER_JINBIN, HEADER_JINBIN_RETROFIT};
    /** 界面上可选的全部玻璃装饰线样式。 */
    public static final int[] BAND_STYLES = {BAND_TIANJIN_1, BAND_CHEVRON_ONLY};

    private PsdStyles() {
    }

    /** 吊版样式的语言表键。 */
    public static String headerNameKey(int style) {
        return "gui.trdm.psd_style.header." + style;
    }

    /** 玻璃装饰线样式的语言表键。 */
    public static String bandNameKey(int style) {
        return "gui.trdm.psd_style.band." + style;
    }
}
