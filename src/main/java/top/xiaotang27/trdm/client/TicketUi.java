package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 售票机 / 客服终端共用的浅色窗口、带阴影的按钮、数字键盘和弹窗。 */
final class TicketUi {

    static final float PANEL_X = 90F;
    static final float PANEL_Y = 16F;
    static final float PANEL_W = 780F;
    static final float PANEL_H = 478F;
    static final float HEADER_H = 34F;

    static final float KEY_W = 52F;
    static final float KEY_H = 24F;
    static final float KEY_GAP = 6F;

    // 参考图的浅灰主题
    static final int BG = 0xFFD2D2D2;
    static final int BG_LIGHT = 0xFFF4F4F4;
    static final int BORDER = 0xFF6E6E6E;
    static final int HEADER = 0xFF6C6C6C;
    static final int TEXT_DARK = 0xFF1E1E1E;
    static final int TEXT_MUTED = 0xFF555555;
    static final int TEXT_LIGHT = 0xFFFFFFFF;
    static final int SHADOW = 0x50000000;
    static final int GREEN = 0xFF0A8A1E;
    static final int GRAY_BUTTON = 0xFFC2C2C2;
    static final int GRAY_BUTTON_HOVER = 0xFFD8D8D8;
    static final int ROW = 0xFFE6E6E6;
    static final int ROW_ALT = 0xFFDEDEDE;
    static final int ROW_HOVER = 0xFFEFEFEF;
    static final int ROW_HEADER = 0xFFC6C6C6;

    static final ResourceLocation EMERALD = new ResourceLocation("minecraft", "textures/item/emerald.png");

    private static final String[] KEYS = {
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "<", "0", "OK"
    };

    private TicketUi() {
    }

    static void panel(TrdmScreenBase host, GuiGraphics guiGraphics) {
        panel(host, guiGraphics, true);
    }

    static void panel(TrdmScreenBase host, GuiGraphics guiGraphics, boolean brand) {
        host.rect(guiGraphics, PANEL_X - 2F, PANEL_Y - 2F, PANEL_W + 4F, PANEL_H + 4F, 0xFF5A5A5A);
        host.rect(guiGraphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, BG);
        host.rect(guiGraphics, PANEL_X + 2F, PANEL_Y + 2F, PANEL_W - 4F, PANEL_H - 4F, BG_LIGHT);
        host.rect(guiGraphics, PANEL_X, PANEL_Y, PANEL_W, HEADER_H, HEADER);
        if (brand) {
            host.textRight(guiGraphics, Component.literal("TangsRail Decoration"),
                    PANEL_X + PANEL_W - 12F, PANEL_Y + 11F,
                    TrdmScreenBase.FONT_SUB, TEXT_LIGHT);
        }
    }

    static void tab(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y, float w, float h,
                    Component label, boolean active, boolean hovered) {
        option(host, guiGraphics, x, y, w, h, label, active, hovered, TrdmScreenBase.FONT_ROW);
    }

    static void option(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y, float w, float h,
                       Component label, boolean active, boolean hovered, float font) {
        shadow(host, guiGraphics, x, y, w, h, 2F);
        host.rect(guiGraphics, x, y, w, h, BORDER);
        host.rect(guiGraphics, x + 1F, y + 1F, w - 2F, h - 2F,
                active ? GREEN : (hovered ? GRAY_BUTTON_HOVER : GRAY_BUTTON));
        host.rect(guiGraphics, x + 1F, y + 1F, w - 2F, 2F, active ? 0x50FFFFFF : 0x70FFFFFF);
        host.textCentred(guiGraphics, label, x, w, y + (h - font) / 2F - 1F,
                font, active ? TEXT_LIGHT : TEXT_DARK);
    }

    static void button(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y, float w, float h,
                       Component label, boolean hovered, float font) {
        shadow(host, guiGraphics, x, y, w, h, 2F);
        host.rect(guiGraphics, x, y, w, h, BORDER);
        host.rect(guiGraphics, x + 1F, y + 1F, w - 2F, h - 2F, hovered ? GRAY_BUTTON_HOVER : GRAY_BUTTON);
        host.rect(guiGraphics, x + 1F, y + 1F, w - 2F, 2F, 0x80FFFFFF);
        host.textCentred(guiGraphics, label, x, w, y + (h - font) / 2F - 1F, font, TEXT_DARK);
    }

    static void keypad(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y,
                       float mx, float my) {
        for (int index = 0; index < KEYS.length; index++) {
            final int column = index % 3;
            final int row = index / 3;
            final float keyX = x + column * (KEY_W + KEY_GAP);
            final float keyY = y + row * (KEY_H + KEY_GAP);
            button(host, guiGraphics, keyX, keyY, KEY_W, KEY_H,
                    Component.literal(KEYS[index]), host.hit(mx, my, keyX, keyY, KEY_W, KEY_H),
                    TrdmScreenBase.FONT_ROW);
        }
    }

    /** 返回按下的键（"1".."9" / "<" / "OK"），没按到返回 null。 */
    static String keypadHit(float x, float y, float mx, float my) {
        for (int index = 0; index < KEYS.length; index++) {
            final int column = index % 3;
            final int row = index / 3;
            final float keyX = x + column * (KEY_W + KEY_GAP);
            final float keyY = y + row * (KEY_H + KEY_GAP);
            if (mx >= keyX && mx < keyX + KEY_W && my >= keyY && my < keyY + KEY_H) {
                return KEYS[index];
            }
        }
        return null;
    }

    static void dialog(TrdmScreenBase host, GuiGraphics guiGraphics, Component message, float mx, float my) {
        host.rect(guiGraphics, 0F, 0F, TrdmScreenBase.DESIGN_W, TrdmScreenBase.DESIGN_H, 0x66000000);
        final float width = 380F;
        final float height = 132F;
        final float x = (TrdmScreenBase.DESIGN_W - width) / 2F;
        final float y = (TrdmScreenBase.DESIGN_H - height) / 2F;
        shadow(host, guiGraphics, x, y, width, height, 4F);
        host.rect(guiGraphics, x, y, width, height, BORDER);
        host.rect(guiGraphics, x + 1F, y + 1F, width - 2F, height - 2F, BG_LIGHT);
        host.centred(guiGraphics, message, TrdmScreenBase.DESIGN_W / 2F, y + 30F,
                TrdmScreenBase.FONT_BODY, TEXT_DARK);
        button(host, guiGraphics, TrdmScreenBase.DESIGN_W / 2F - 50F, y + height - 40F, 100F, 24F,
                Component.translatable("gui.trdm.ticket.ok"),
                host.hit(mx, my, TrdmScreenBase.DESIGN_W / 2F - 50F, y + height - 40F, 100F, 24F),
                TrdmScreenBase.FONT_ROW);
    }

    static boolean dialogConfirmHit(float mx, float my) {
        final float width = 380F;
        final float height = 132F;
        final float x = (TrdmScreenBase.DESIGN_W - width) / 2F;
        final float y = (TrdmScreenBase.DESIGN_H - height) / 2F;
        return mx >= TrdmScreenBase.DESIGN_W / 2F - 50F && mx < TrdmScreenBase.DESIGN_W / 2F + 50F
                && my >= y + height - 40F && my < y + height - 16F;
    }

    /** 客服终端的三色业务卡：参考图里卡片右下角有明显投影。 */
    static void card(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y, float w, float h,
                     ResourceLocation icon, int color, Component title, Component subtitle,
                     float mx, float my) {
        final boolean hovered = host.hit(mx, my, x, y, w, h);
        shadow(host, guiGraphics, x, y, w, h, 4F);
        host.rect(guiGraphics, x, y, w, h, 0xFF6A6A6A);
        host.rect(guiGraphics, x + 1F, y + 1F, w - 2F, h - 2F, hovered ? brighten(color) : color);
        host.rect(guiGraphics, x + 1F, y + 1F, w - 2F, 3F, 0x35FFFFFF);
        if (icon != null) {
            final float iconSize = 58F;
            host.image(guiGraphics, icon, x + (w - iconSize) / 2F, y + 18F, iconSize, iconSize);
        }
        host.centred(guiGraphics, title, x + w / 2F, y + h - 48F,
                TrdmScreenBase.FONT_BODY, TEXT_LIGHT);
        if (subtitle != null && !subtitle.getString().isEmpty()) {
            host.centred(guiGraphics, subtitle, x + w / 2F, y + h - 26F,
                    TrdmScreenBase.FONT_SUB, 0xFFE8E8E8);
        }
    }

    static void shadow(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y, float w, float h,
                       float offset) {
        host.rect(guiGraphics, x + offset, y + offset, w, h, SHADOW);
    }

    static void emerald(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y, float size) {
        host.image(guiGraphics, EMERALD, x, y, size, size);
    }

    static int brighten(int color) {
        final int red = Math.min(255, ((color >> 16) & 0xFF) + 18);
        final int green = Math.min(255, ((color >> 8) & 0xFF) + 18);
        final int blue = Math.min(255, (color & 0xFF) + 18);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
