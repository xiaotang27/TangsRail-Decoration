package top.xiaotang27.trdm.mixin;

import mtr.client.ClientCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.awt.Font;

/**
 * 把 MTR 缓存的那两个 AWT 字体拿出来改。
 * <p>
 * MTR 的牌面文字是在 {@code ClientCache} 里用 {@code font} / {@code fontCjk} 两个
 * {@link Font} 画进贴图的（它没有公开设置字体的接口），所以要在渲染前把这两个字段换成
 * TRDM 的字体，MTR 原版的指示牌、站名牌、路线图就会按新字体重画。
 * <p>
 * 换完记得调 {@code ClientData.DATA_CACHE.resetFonts()} —— 它会清缓存并把所有动态牌面
 * 排进重绘队列，也就是「强制重新绘制 MTR 原版内容」。
 */
@Mixin(ClientCache.class)
public interface ClientCacheFontAccessor {

    @Accessor("font")
    void trdm$setFont(Font font);

    @Accessor("fontCjk")
    void trdm$setFontCjk(Font font);
}
