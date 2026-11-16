package top.xiaotang27.trdm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 可染色顶部灯（模型 3）的「设置颜色」界面。
 * <p>
 * 颜色编辑区做成常见的取色器：左边是<b>饱和度 × 亮度</b>方块（横轴白→纯色、纵轴亮→黑），
 * 右边一根<b>色相竖条</b>；点 / 拖动方块或竖条都会实时改颜色。右边还有十六进制输入框
 * 和一组常用颜色，方便直接填线路色 / 品牌色。
 * <p>
 * 颜色只作用在模型 3 的 {@code color} 贴图上 —— 也就是灯的<b>底座</b>；灯管本身始终是白色自发光。
 * 改颜色走 {@link TrdmControlClient}：拖动时只改客户端本地方块实体做逐帧预览，
 * 松手 / 关闭界面时才把最终颜色发回服务端持久化并广播给附近玩家。
 */
public class PsdLightColorScreen extends TrdmScreenBase {

    private static final float DONE_W = 84F;
    private static final float DONE_H = 24F;
    private static final float DONE_X = CONTENT_RIGHT - DONE_W;
    private static final float DONE_Y = TITLE_Y;

    private static final float CURRENT_Y = 90F;
    private static final float PREVIEW_Y = 114F;
    private static final float PREVIEW_W = 64F;
    private static final float PREVIEW_H = 40F;

    private static final float PICKER_SECTION_Y = 168F;
    private static final float SV_X = CONTENT_X;
    private static final float SV_Y = 194F;
    private static final float SV_W = 240F;
    private static final float SV_H = 240F;
    private static final float HUE_X = SV_X + SV_W + 8F;
    private static final float HUE_Y = SV_Y;
    private static final float HUE_W = 24F;
    private static final float HUE_H = SV_H;

    private static final float RIGHT_X = 360F;
    private static final float HEX_SECTION_Y = 168F;
    private static final float HEX_Y = 194F;
    private static final float HEX_W = 300F;
    private static final float HEX_H = 30F;
    private static final float HEX_HINT_Y = 232F;
    private static final float WARN_Y = 250F;

    private static final float PRESET_SECTION_Y = 280F;
    private static final float PRESET_TOP = 306F;
    private static final int PRESET_COLS = 6;
    private static final float PRESET_CELL_W = 50F;
    private static final float PRESET_CELL_H = 26F;
    private static final float PRESET_GAP_X = 4F;
    private static final float PRESET_GAP_Y = 4F;

    private static final Object FIELD_HEX = new Object();

    /** 取色器当前操作的区域，用来支持按住拖动。 */
    private enum Drag {
        NONE, SV, HUE
    }

    private final BlockPos lightPos;
    private int color;
    private boolean invalidHex;
    /** 拖动时只改本地；松手 / 关界面时若还有未同步的改动，再发一次包。 */
    private boolean dirty;

    private float hue;
    private float saturation;
    private float value;
    private Drag drag = Drag.NONE;

    public PsdLightColorScreen(BlockPos lightPos, int color) {
        super(null, Component.translatable("gui.trdm.psd_light_color.title"));
        this.lightPos = lightPos;
        setColorFromRgb(color);
    }

    // ===== 布局 =====

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        title(guiGraphics, title);
        hint(guiGraphics, "gui.trdm.psd_light_color.hint", CONTENT_X, SUB_Y);

        buttonText(guiGraphics, DONE_X, DONE_Y, DONE_W, DONE_H,
                Component.translatable("gui.trdm.psd_light_color.done"), false,
                hit(mx, my, DONE_X, DONE_Y, DONE_W, DONE_H), FONT_ROW);

        // ===== 当前底座颜色 =====
        section(guiGraphics, "gui.trdm.psd_light_color.current", CURRENT_Y);
        rect(guiGraphics, CONTENT_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H, COLOR_BORDER);
        rect(guiGraphics, CONTENT_X + 1F, PREVIEW_Y + 1F, PREVIEW_W - 2F, PREVIEW_H - 2F, 0xFF000000 | color);
        text(guiGraphics, Component.literal(hexText()), CONTENT_X + PREVIEW_W + 12F, PREVIEW_Y + 11F,
                FONT_SECTION, COLOR_TEXT);

        // ===== 取色器 =====
        section(guiGraphics, "gui.trdm.psd_light_color.palette", PICKER_SECTION_Y);
        final String hovered = hoveredHex(mx, my);
        if (hovered != null) {
            textRight(guiGraphics, Component.literal(hovered), CONTENT_RIGHT, PICKER_SECTION_Y, FONT_ROW, COLOR_SUB);
        }
        renderSvSquare(guiGraphics);
        renderHueStrip(guiGraphics);

        // ===== 十六进制 =====
        sectionAt(guiGraphics, "gui.trdm.psd_light_color.hex", RIGHT_X, HEX_SECTION_Y);
        field(guiGraphics, FIELD_HEX, RIGHT_X, HEX_Y, HEX_W, HEX_H, FONT_SECTION, hexText());
        hint(guiGraphics, "gui.trdm.psd_light_color.hex_hint", RIGHT_X, HEX_HINT_Y);
        if (invalidHex) {
            text(guiGraphics, Component.translatable("gui.trdm.psd_light_color.invalid"), RIGHT_X, WARN_Y,
                    FONT_SUB, COLOR_WARN);
        }

        // ===== 常用颜色 =====
        sectionAt(guiGraphics, "gui.trdm.psd_light_color.preset", RIGHT_X, PRESET_SECTION_Y);
        for (int index = 0; index < PRESETS.length; index++) {
            final float x = presetX(index);
            final float y = presetY(index);
            final int preset = PRESETS[index];
            final boolean selected = preset == color;
            final boolean hoveredCell = hit(mx, my, x, y, PRESET_CELL_W, PRESET_CELL_H);
            if (selected || hoveredCell) {
                rect(guiGraphics, x - 1F, y - 1F, PRESET_CELL_W + 2F, PRESET_CELL_H + 2F, COLOR_TEXT);
            }
            rect(guiGraphics, x, y, PRESET_CELL_W, PRESET_CELL_H, 0xFF000000 | preset);
        }
        hint(guiGraphics, "gui.trdm.psd_light_color.infect_hint", RIGHT_X, 438F);
    }

    // ===== 取色器绘制 =====

    /**
     * 饱和度 × 亮度方块：每一列固定在饱和度 s 上，从顶部的 {@code hsv(hue, s, 1)} 竖直渐变到黑。
     * 240 列正好覆盖 240 设计像素，逐列 fillGradient 比逐像素贴图省事，也足够平滑。
     */
    private void renderSvSquare(GuiGraphics guiGraphics) {
        rect(guiGraphics, SV_X - 1F, SV_Y - 1F, SV_W + 2F, SV_H + 2F, COLOR_FIELD_BORDER);
        for (int column = 0; column < (int) SV_W; column++) {
            final float s = column / (SV_W - 1F);
            final int top = 0xFF000000 | hsvToRgb(hue, s, 1F);
            final int x1 = screenX(SV_X + column);
            final int x2 = Math.max(x1 + 1, screenX(SV_X + column + 1F));
            final int y1 = screenY(SV_Y);
            final int y2 = screenY(SV_Y + SV_H);
            guiGraphics.fillGradient(x1, y1, x2, y2, top, 0xFF000000);
        }
        // 当前取色点：黑框 + 白圈 + 中间留空
        final float cx = SV_X + saturation * SV_W;
        final float cy = SV_Y + (1F - value) * SV_H;
        rect(guiGraphics, cx - 4F, cy - 4F, 9F, 9F, 0xFF101010);
        rect(guiGraphics, cx - 3F, cy - 3F, 7F, 7F, 0xFFFFFFFF);
        rect(guiGraphics, cx - 2F, cy - 2F, 5F, 5F, 0xFF101010);
        rect(guiGraphics, cx - 1F, cy - 1F, 3F, 3F, 0xFF101010);
    }

    /** 色相竖条：6 段红→黄→绿→青→蓝→品红→红，和取色方块的色相一一对应。 */
    private void renderHueStrip(GuiGraphics guiGraphics) {
        rect(guiGraphics, HUE_X - 1F, HUE_Y - 1F, HUE_W + 2F, HUE_H + 2F, COLOR_FIELD_BORDER);
        final int[] stops = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
        for (int index = 0; index < stops.length - 1; index++) {
            final int y1 = screenY(HUE_Y + HUE_H * index / (stops.length - 1F));
            final int y2 = screenY(HUE_Y + HUE_H * (index + 1) / (stops.length - 1F));
            guiGraphics.fillGradient(screenX(HUE_X), y1, screenX(HUE_X + HUE_W), Math.max(y1 + 1, y2),
                    stops[index], stops[index + 1]);
        }
        // 色相游标：黑条 + 中间白线
        final float markerY = HUE_Y + hue * HUE_H;
        rect(guiGraphics, HUE_X - 2F, markerY - 3F, HUE_W + 4F, 6F, 0xFF101010);
        rect(guiGraphics, HUE_X, markerY - 1F, HUE_W, 2F, 0xFFFFFFFF);
    }

    // ===== 交互 =====

    @Override
    protected boolean clickPage(float mx, float my) {
        if (hit(mx, my, DONE_X, DONE_Y, DONE_W, DONE_H)) {
            onClose();
            return true;
        }
        if (hit(mx, my, RIGHT_X, HEX_Y, HEX_W, HEX_H)) {
            beginField(FIELD_HEX, RIGHT_X, HEX_Y, HEX_W, HEX_H, hexText(), 7, FONT_SECTION, this::applyHex);
            return true;
        }
        if (hit(mx, my, SV_X, SV_Y, SV_W, SV_H)) {
            endField(false);
            drag = Drag.SV;
            updateFromPicker(mx, my, false);
            return true;
        }
        if (hit(mx, my, HUE_X, HUE_Y, HUE_W, HUE_H)) {
            endField(false);
            drag = Drag.HUE;
            updateFromPicker(mx, my, false);
            return true;
        }
        for (int index = 0; index < PRESETS.length; index++) {
            if (hit(mx, my, presetX(index), presetY(index), PRESET_CELL_W, PRESET_CELL_H)) {
                endField(false);
                applyRgb(PRESETS[index]);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && drag != Drag.NONE) {
            updateFromPicker(designX(mouseX), designY(mouseY), false);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && drag != Drag.NONE) {
            updateFromPicker(designX(mouseX), designY(mouseY), true);
            drag = Drag.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        // 拖动中途按 Esc 关界面时，最后一次本地预览也要落到服务端
        syncColor();
        super.onClose();
    }

    @Override
    public void removed() {
        // 被别的界面顶掉（死亡、切维度等）时 onClose 不一定走，这里再兜一次
        syncColor();
        super.removed();
    }

    private void updateFromPicker(float mx, float my, boolean syncNow) {
        if (drag == Drag.SV) {
            saturation = clamp01((mx - SV_X) / SV_W);
            value = clamp01(1F - (my - SV_Y) / SV_H);
        } else if (drag == Drag.HUE) {
            hue = clamp01((my - HUE_Y) / HUE_H);
        }
        color = hsvToRgb(hue, saturation, value);
        invalidHex = false;
        dirty = true;
        TrdmControlClient.applyPsdLightColorLocal(lightPos, color);
        if (syncNow) {
            syncColor();
        }
    }

    private void applyHex(String text) {
        final Integer parsed = parseHex(text);
        if (parsed == null) {
            invalidHex = true;
            return;
        }
        applyRgb(parsed);
    }

    private void applyRgb(int rgb) {
        setColorFromRgb(rgb);
        invalidHex = false;
        dirty = false;
        TrdmControlClient.sendPsdLightColor(lightPos, color);
    }

    private void syncColor() {
        if (!dirty) {
            return;
        }
        dirty = false;
        // 断线 / 被踢时 removed() 也会走到这里，没连接就别发包
        if (Minecraft.getInstance().level == null) {
            return;
        }
        TrdmControlClient.sendPsdLightColor(lightPos, color);
    }

    private void setColorFromRgb(int rgb) {
        color = rgb & 0xFFFFFF;
        final float r = ((color >> 16) & 0xFF) / 255F;
        final float g = ((color >> 8) & 0xFF) / 255F;
        final float b = (color & 0xFF) / 255F;
        final float max = Math.max(r, Math.max(g, b));
        final float min = Math.min(r, Math.min(g, b));
        final float delta = max - min;
        value = max;
        saturation = max <= 0F ? 0F : delta / max;
        // 灰色 / 黑色没有色相：保留上一次的色相，竖条和取色方块才不会突然跳回红色
        if (delta > 1.0E-4F) {
            if (max == r) {
                hue = ((g - b) / delta + 6F) % 6F;
            } else if (max == g) {
                hue = (b - r) / delta + 2F;
            } else {
                hue = (r - g) / delta + 4F;
            }
            hue /= 6F;
        }
        if (hue < 0F) {
            hue += 1F;
        }
        hue %= 1F;
    }

    private String hexText() {
        return String.format("#%06X", color);
    }

    private String hoveredHex(float mx, float my) {
        if (hit(mx, my, SV_X, SV_Y, SV_W, SV_H)) {
            final float s = clamp01((mx - SV_X) / SV_W);
            final float v = clamp01(1F - (my - SV_Y) / SV_H);
            return String.format("#%06X", hsvToRgb(hue, s, v));
        }
        if (hit(mx, my, HUE_X, HUE_Y, HUE_W, HUE_H)) {
            return String.format("#%06X", hsvToRgb(clamp01((my - HUE_Y) / HUE_H), 1F, 1F));
        }
        return null;
    }

    // ===== 几何 =====

    private int screenX(float designX) {
        return Math.round(originX + designX * scale);
    }

    private int screenY(float designY) {
        return Math.round(originY + designY * scale);
    }

    /** 右侧两列的分组标题：基类的 {@code section(String, y)} 固定画在 CONTENT_X，这里指定 x。 */
    private void sectionAt(GuiGraphics guiGraphics, String key, float x, float y) {
        text(guiGraphics, Component.translatable(key), x, y, FONT_SECTION, COLOR_TEXT);
    }

    private static float presetX(int index) {
        return RIGHT_X + (index % PRESET_COLS) * (PRESET_CELL_W + PRESET_GAP_X);
    }

    private static float presetY(int index) {
        return PRESET_TOP + (index / PRESET_COLS) * (PRESET_CELL_H + PRESET_GAP_Y);
    }

    private static float clamp01(float value) {
        return Math.max(0F, Math.min(1F, value));
    }

    // ===== 颜色数学 =====

    private static int hsvToRgb(float hue, float saturation, float value) {
        final float h = (hue - (float) Math.floor(hue)) * 6F;
        final int sector = (int) Math.floor(h);
        final float f = h - sector;
        final int p = Math.round(255F * value * (1F - saturation));
        final int q = Math.round(255F * value * (1F - saturation * f));
        final int t = Math.round(255F * value * (1F - saturation * (1F - f)));
        final int v = Math.round(255F * value);
        return switch (sector) {
            case 0 -> (v << 16) | (t << 8) | p;
            case 1 -> (q << 16) | (v << 8) | p;
            case 2 -> (p << 16) | (v << 8) | t;
            case 3 -> (p << 16) | (q << 8) | v;
            case 4 -> (t << 16) | (p << 8) | v;
            default -> (v << 16) | (p << 8) | q;
        };
    }

    /** 接受 {@code #RRGGBB} / {@code RRGGBB}，其它写法返回 null。 */
    private static Integer parseHex(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(text, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 常用颜色：白 / 灰 / 黑、暖白和地铁常见的线路色。 */
    private static final int[] PRESETS = {
            0xFFFFFF, 0xF2F2F2, 0xC0C0C0, 0x808080, 0x404040, 0x000000,
            0xFFF2D8, 0xFFE9C4, 0xFFD166, 0xFFA500, 0xFF6B4A, 0xE63946,
            0xB5179E, 0x7209B7, 0x3A0CA3, 0x4361EE, 0x4CC9F0, 0x00B4D8,
            0x2A9D8F, 0x57CC99, 0x80ED99, 0xC7F9CC, 0xE9EDC9, 0xF4F1DE,
    };
}
