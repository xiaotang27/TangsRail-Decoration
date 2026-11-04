package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import top.xiaotang27.trdm.TrdmConfig;
import top.xiaotang27.trdm.render.TrdmHeaderTextures;
import top.xiaotang27.trdm.render.TrdmPisVideo;

/** 通用设置 / 关于。入口：/trdm config、快捷键、Mod Menu。 */
public class TrdmConfigScreen extends TrdmScreenBase {

    private static final String[] PAGES = {"gui.trdm.config.general", "gui.trdm.config.about"};
    private static final int PAGE_GENERAL = 0;
    private static final int PAGE_ABOUT = 1;

    private static final ResourceLocation LOGO_WIDE = new ResourceLocation("trdm", "textures/gui/logo_wide.png");
    private static final ResourceLocation BRAND = new ResourceLocation("trdm", "textures/gui/brand.png");

    private static final float FONT_ROW_Y = 100F;
    private static final float VIDEO_ROW_Y = 172F;
    private static final float TOGGLE_X = CONTENT_RIGHT - TOGGLE_W;

    private int page = PAGE_GENERAL;

    public TrdmConfigScreen(Screen parent) {
        super(parent, Component.translatable("gui.trdm.config.title"));
    }

    @Override
    protected String[] pages() {
        return PAGES;
    }

    @Override
    protected int currentPage() {
        return page;
    }

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        drawSidebarIcons(guiGraphics);
        if (page == PAGE_GENERAL) {
            title(guiGraphics, Component.translatable("gui.trdm.config.general"));
            toggleRow(guiGraphics, FONT_ROW_Y, "gui.trdm.config.font", "gui.trdm.config.font.sub",
                    TrdmConfig.useTrdmFont(), null);
            toggleRow(guiGraphics, VIDEO_ROW_Y, "gui.trdm.config.video", "gui.trdm.config.video.sub",
                    TrdmConfig.pisVideoEnabled(), Component.translatable("gui.trdm.config.video.warn"));
        } else {
            renderAbout(guiGraphics);
        }
    }

    private void toggleRow(GuiGraphics guiGraphics, float top, String titleKey, String subKey, boolean on,
                           Component warning) {
        text(guiGraphics, Component.translatable(titleKey), CONTENT_X, top, FONT_BODY, COLOR_TEXT);
        hint(guiGraphics, Component.translatable(subKey), CONTENT_X, top + 20F);
        if (warning != null) {
            text(guiGraphics, warning, CONTENT_X, top + 36F, FONT_SUB, COLOR_WARN);
        }
        final Component state = Component.translatable(on ? "gui.trdm.config.on" : "gui.trdm.config.off");
        textRight(guiGraphics, state, TOGGLE_X - 10F, top + 4F, FONT_ROW, COLOR_TEXT);
        toggle(guiGraphics, TOGGLE_X, top - 2F, on);
    }

    private void renderAbout(GuiGraphics guiGraphics) {
        title(guiGraphics, Component.translatable("gui.trdm.config.about"));
        final float logoW = 220F;
        image(guiGraphics, LOGO_WIDE, (DESIGN_W - logoW) / 2F, 92F, logoW, logoW * 270F / 610F);
        centred(guiGraphics, Component.literal("TangsRail Decoration"), DESIGN_W / 2F, 210F, FONT_BODY,
                COLOR_TEXT);
        centred(guiGraphics, Component.translatable("gui.trdm.config.version", versionString()),
                DESIGN_W / 2F, 232F, FONT_SUB, COLOR_SUB);
        centred(guiGraphics, Component.translatable("gui.trdm.config.credit"), DESIGN_W / 2F, 272F, FONT_SUB,
                COLOR_TEXT);
        centred(guiGraphics, Component.translatable("gui.trdm.config.aigc"), DESIGN_W / 2F, 292F, FONT_SUB,
                COLOR_TEXT);
        centred(guiGraphics, Component.translatable("gui.trdm.config.license"), DESIGN_W / 2F, 312F, FONT_SUB,
                COLOR_TEXT);
        final float brandW = 110F;
        image(guiGraphics, BRAND, (DESIGN_W - brandW) / 2F, 400F, brandW, brandW * 81F / 294F);
    }

    private void drawSidebarIcons(GuiGraphics guiGraphics) {
        for (int index = 0; index < PAGES.length; index++) {
            final float centreX = SIDEBAR_W / 2F;
            final float centreY = 8F + index * (SIDEBAR_BTN_H + 2F) + SIDEBAR_BTN_H / 2F;
            final int color = index == page ? 0xFFFFFFFF : 0xFF606060;
            if (index == 0) {
                rect(guiGraphics, centreX - 5F, centreY - 5F, 10F, 10F, color);
                rect(guiGraphics, centreX - 2F, centreY - 8F, 4F, 3F, color);
                rect(guiGraphics, centreX - 2F, centreY + 5F, 4F, 3F, color);
                rect(guiGraphics, centreX - 8F, centreY - 2F, 3F, 4F, color);
                rect(guiGraphics, centreX + 5F, centreY - 2F, 3F, 4F, color);
                rect(guiGraphics, centreX - 2F, centreY - 2F, 4F, 4F, 0xFF000000);
            } else {
                rect(guiGraphics, centreX - 1F, centreY - 7F, 2F, 3F, color);
                rect(guiGraphics, centreX - 1F, centreY - 3F, 2F, 10F, color);
            }
        }
    }

    @Override
    protected boolean clickPage(float mx, float my) {
        if (mx >= 0 && mx < SIDEBAR_W) {
            for (int index = 0; index < PAGES.length; index++) {
                final float top = 8F + index * (SIDEBAR_BTN_H + 2F);
                if (my >= top && my < top + SIDEBAR_BTN_H) {
                    page = index;
                    return true;
                }
            }
        }
        if (page != PAGE_GENERAL) {
            return false;
        }
        if (hit(mx, my, TOGGLE_X, FONT_ROW_Y - 2F, TOGGLE_W, TOGGLE_H)) {
            TrdmConfig.setUseTrdmFont(!TrdmConfig.useTrdmFont());
            TrdmHeaderTextures.resetFonts();
            // 同时把字体灌进 MTR 的牌面缓存，并强制它把所有动态牌面重画一遍
            TrdmMtrFonts.sync();
            return true;
        }
        if (hit(mx, my, TOGGLE_X, VIDEO_ROW_Y - 2F, TOGGLE_W, TOGGLE_H)) {
            TrdmConfig.setPisVideoEnabled(!TrdmConfig.pisVideoEnabled());
            TrdmPisVideo.clear();
            return true;
        }
        return false;
    }
}
