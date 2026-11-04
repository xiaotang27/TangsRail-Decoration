# 替换 MTR 原版字体（TRDM 是怎么做到的）

MTR 没有公开的「换字体」接口，但它的牌面文字并不是用原版 `Font` 画的，而是：

1. `mtr.client.ClientCache` 里缓存两个 `java.awt.Font` 字段 —— `font`（英文 Noto Sans）、`fontCjk`（中文 Noto Serif CJK）；
2. 懒加载时从资源管理器读 `mtr:font/noto-sans-semibold.ttf` / `mtr:font/noto-serif-cjk-tc-semibold.ttf`；
3. 所有动态牌面（站名牌、路线图、站台指示牌…）都是 `ClientCache.getTextPixels(...)` 用这两个字体
   画进 `NativeImage` 再注册成贴图的。

所以 TRDM 这样接管（`client/TrdmMtrFonts.java` + `mixin/ClientCacheFontAccessor.java`）：

```java
ClientData.DATA_CACHE.resetFonts();            // MTR 公开方法：清字体缓存 + 所有动态牌面排队重绘
ClientCacheFontAccessor acc = (…) ClientData.DATA_CACHE;
acc.trdm$setFont(latin);                       // @Accessor("font")
acc.trdm$setFontCjk(cjk);                      // @Accessor("fontCjk")
```

- 先 `resetFonts()` 再填字段，顺序很重要：MTR 的加载逻辑是「字段为 null 才去读资源」，
  填进去之后它就不会再懒加载回自己的字体；
- `resetFonts()` 同时把 `resourcesToRefresh` 全填满 = **强制重绘** MTR 原版所有动态牌面；
- 关掉开关时只调 `resetFonts()`，MTR 会懒加载回它自己的 Noto 字体。

## 为什么不再用「覆盖 assets/mtr/font/」的办法

以前是想把 TRDM 的字体文件放在 `assets/mtr/font/` 下覆盖 MTR 的那份 —— 但两个模组
各带一个资源包，**谁赢取决于加载顺序**，读出来的可能是 MTR 自己那份，字体开关就时灵时不灵。
现在字体放在 `assets/trdm/font/`（自己的命名空间，没人抢），替换靠上面的字段注入，确定性有保证。

## 需要注意

- MTR 只在资源重载时（`CustomResources.reload` → `resetFonts()`）会清掉注入的字体，
  所以 TRDM 注册了一个客户端重载监听，**下一 tick** 再灌一次（那时 MTR 的监听已经跑完）；
- 注入的是 `java.awt.Font` 原始对象，MTR 用 `deriveFont` 放大缩小，所以给的是基础字号的样子；
- TRDM 自己的吊板 / 站名牌 / PIS 走的是 `TrdmHeaderTextures`，跟这条链路无关。
