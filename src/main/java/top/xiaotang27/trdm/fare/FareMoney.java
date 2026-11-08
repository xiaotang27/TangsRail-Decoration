package top.xiaotang27.trdm.fare;

import java.util.Locale;

/**
 * 金额显示与绿宝石换算。
 * <p>
 * 内部一律用“分”（{@code long}）；界面按元显示：整数元不带小数（{@code 2}），
 * 非整数元保留两位（{@code 2.50}）。购买/补票向上取整到 1 绿宝石，不找零。
 */
public final class FareMoney {

    private FareMoney() {
    }

    /** 不带货币符号的金额文本，用于 {@code ¥%s} 这类翻译键。 */
    public static String text(long cents) {
        final long value = Math.max(0L, cents);
        final long yuan = value / 100L;
        final long rest = value % 100L;
        if (rest == 0L) {
            return Long.toString(yuan);
        }
        return String.format(Locale.ROOT, "%d.%02d", yuan, rest);
    }

    public static String yuan(long cents) {
        return "¥" + text(cents);
    }

    /** 需要支付多少绿宝石：向上取整，1 绿宝石 = 1 元。 */
    public static long emeralds(long cents) {
        return Math.max(0L, (Math.max(0L, cents) + 99L) / 100L);
    }

    /**
     * 售票机键盘输入框的回填值：整数元才填，非整数元留空（键盘没有小数点，
     * 避免把 2.50 元显示成 2 元）。
     */
    public static String input(long cents) {
        final long value = Math.max(0L, cents);
        return value % 100L == 0L ? Long.toString(value / 100L) : "";
    }
}
