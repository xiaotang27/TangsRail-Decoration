package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 售票机 / 客服终端共用的窗口、数字键盘和弹窗。 */
final class TicketUi {

    static final float PANEL_X = 90F;
    static final float PANEL_Y = 16F;
    static final float PANEL_W = 780F;
    static final float PANEL_H = 478F;
    static final float HEADER_H = 34F;

    static final float KEY_W = 52F;
    static final float KEY_H = 24F;
    static final float KEY_GAP = 6F;

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
        host.rect(guiGraphics, PANEL_X - 1F, PANEL_Y - 1F, PANEL_W + 2F, PANEL_H + 2F, 0xFF9A9A9A);
        host.rect(guiGraphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 0xF0181818);
        host.rect(guiGraphics, PANEL_X, PANEL_Y, PANEL_W, HEADER_H, 0xFF2A2A2A);
        if (brand) {
            host.textRight(guiGraphics, Component.literal("TangsRail Decoration"),
                    PANEL_X + PANEL_W - 12F, PANEL_Y + 11F,
                    TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        }
    }

    static void keypad(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y,
                       float mx, float my) {
        for (int index = 0; index < KEYS.length; index++) {
            final int column = index % 3;
            final int row = index / 3;
            final float keyX = x + column * (KEY_W + KEY_GAP);
            final float keyY = y + row * (KEY_H + KEY_GAP);
            host.buttonText(guiGraphics, keyX, keyY, KEY_W, KEY_H,
                    Component.literal(KEYS[index]), false,
                    host.hit(mx, my, keyX, keyY, KEY_W, KEY_H), TrdmScreenBase.FONT_ROW);
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

    static float keypadWidth() {
        return 3F * KEY_W + 2F * KEY_GAP;
    }

    static float keypadHeight() {
        return 4F * KEY_H + 3F * KEY_GAP;
    }

    static void dialog(TrdmScreenBase host, GuiGraphics guiGraphics, Component message, float mx, float my) {
        host.rect(guiGraphics, 0F, 0F, TrdmScreenBase.DESIGN_W, TrdmScreenBase.DESIGN_H, 0x88000000);
        final float width = 380F;
        final float height = 132F;
        final float x = (TrdmScreenBase.DESIGN_W - width) / 2F;
        final float y = (TrdmScreenBase.DESIGN_H - height) / 2F;
        host.rect(guiGraphics, x - 1F, y - 1F, width + 2F, height + 2F, 0xFF9A9A9A);
        host.rect(guiGraphics, x, y, width, height, 0xF0181818);
        host.centred(guiGraphics, message, TrdmScreenBase.DESIGN_W / 2F, y + 32F,
                TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_TEXT);
        host.buttonText(guiGraphics, TrdmScreenBase.DESIGN_W / 2F - 50F, y + height - 40F, 100F, 24F,
                Component.translatable("gui.trdm.ticket.ok"), false,
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

    static void card(TrdmScreenBase host, GuiGraphics guiGraphics, float x, float y, float w, float h,
                     int color, Component title, Component subtitle, float mx, float my) {
        final boolean hovered = host.hit(mx, my, x, y, w, h);
        host.rect(guiGraphics, x, y, w, h, 0xFF9A9A9A);
        host.rect(guiGraphics, x + 1F, y + 1F, w - 2F, h - 2F, hovered ? brighten(color) : color);
        host.centred(guiGraphics, title, x + w / 2F, y + h / 2F - 12F,
                TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_TEXT);
        host.centred(guiGraphics, subtitle, x + w / 2F, y + h / 2F + 10F,
                TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
    }

    private static int brighten(int color) {
        final int red = Math.min(255, ((color >> 16) & 0xFF) + 22);
        final int green = Math.min(255, ((color >> 8) & 0xFF) + 22);
        final int blue = Math.min(255, (color & 0xFF) + 22);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
