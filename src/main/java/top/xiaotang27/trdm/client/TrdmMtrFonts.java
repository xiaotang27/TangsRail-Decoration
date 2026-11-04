package top.xiaotang27.trdm.client;

import mtr.client.ClientCache;
import mtr.client.ClientData;
import net.minecraft.client.Minecraft;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.TrdmConfig;
import top.xiaotang27.trdm.mixin.ClientCacheFontAccessor;
import top.xiaotang27.trdm.render.TrdmHeaderTextures;

import java.awt.Font;

/**
 * 把 TRDM 的字体套到 <b>MTR 原版</b>的牌面上（TRDM 自己的吊板 / 站名牌 / PIS 用的是
 * {@link TrdmHeaderTextures}，不受这里影响）。
 * <p>
 * MTR 没有更换字体的接口，但它的牌面文字是从 {@code mtr.client.ClientCache} 里缓存的
 * 两个 {@link java.awt.Font} 画出来的，所以：
 * <ol>
 *     <li>用 {@code ClientCacheFontAccessor}（mixin）把 {@code font} / {@code fontCjk} 换掉；</li>
 *     <li>调 {@link ClientCache#resetFonts()} —— MTR 自己的公开方法，会清掉字体缓存并把
 *         所有动态牌面纹理排进重绘队列，等于「强制重新绘制」。这一步也保证了顺序：
 *         先让 MTR 清空，再把我们的字体填进去，它就不会懒加载回自己的字体。</li>
 * </ol>
 * 「使用TRDM字体」关掉时只调 resetFonts()，MTR 就会懒加载回它自己的 Noto 字体。
 */
public final class TrdmMtrFonts {

    /** 资源重载（F3+T / 换资源包）之后需要重新灌一次。 */
    private static boolean pendingSync;
    /** 已经成功同步过至少一次（MTR 的缓存是懒加载的，客户端初始化时可能还没准备好）。 */
    private static boolean everSynced;

    private TrdmMtrFonts() {
    }

    /** 资源重载监听里调：等下一 tick 再同步（那时 MTR 自己的重载监听已经跑完了）。 */
    public static void requestSync() {
        pendingSync = true;
    }

    /** 每客户端 tick 调：该同步的时候同步一次。 */
    public static void tick() {
        final boolean reloaded = pendingSync;
        pendingSync = false;
        if (!reloaded && everSynced) {
            return;
        }
        if (ClientData.DATA_CACHE == null) {
            // MTR 还没初始化，下一 tick 再来
            return;
        }
        everSynced = true;
        sync();
    }

    /** 立刻同步一次（配置界面切换开关时直接调）。 */
    public static void sync() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        try {
            final ClientCache cache = ClientData.DATA_CACHE;
            if (cache == null) {
                return;
            }
            // 先让 MTR 清空字体缓存 + 全部动态牌面排队重绘
            cache.resetFonts();
            if (!TrdmConfig.useTrdmFont()) {
                return;
            }
            final Font latin = TrdmHeaderTextures.trdmLatinFont();
            final Font cjk = TrdmHeaderTextures.trdmCjkFont();
            if (latin == null || cjk == null) {
                Trdm.LOGGER.warn("[font] TRDM 字体没读到，MTR 继续用他自己的字体");
                return;
            }
            final ClientCacheFontAccessor accessor = (ClientCacheFontAccessor) (Object) cache;
            accessor.trdm$setFont(latin);
            accessor.trdm$setFontCjk(cjk);
        } catch (Throwable t) {
            Trdm.LOGGER.warn("[font] 给 MTR 套用 TRDM 字体失败：{}", t.toString());
        }
    }
}
