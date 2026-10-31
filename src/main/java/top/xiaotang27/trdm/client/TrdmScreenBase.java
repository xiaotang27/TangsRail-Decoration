package top.xiaotang27.trdm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * TRDM 界面基类：960×540 设计坐标、等比缩放（480×270 保底）、左侧栏目栏、页脚版本号，
 * 以及开关 / 列表行 / 分组标题 / 灰字说明 / 单行输入框这些公共控件的绘制与命中判定。
 * <p>
 * 子类只需要实现 {@link #renderPage} 和 {@link #clickPage}，按设计坐标画内容即可。
 * 输入框不用 {@code EditBox}（那套是 GUI 坐标、不跟设计坐标缩放），基类自带一个
 * 设计坐标下的单行输入框：{@link #beginField} 激活，{@link #field} 绘制，
 * 回车提交、Esc 取消，输入与按键由基类接管。
 */
public abstract class TrdmScreenBase extends Screen {

    // ===== 设计坐标 =====
    protected static final float DESIGN_W = 960F;
    protected static final float DESIGN_H = 540F;
    private static final float MIN_SCALE = 0.5F;

    protected static final float SIDEBAR_W = 34F;
    protected static final float SIDEBAR_BTN_H = 26F;
    protected static final float CONTENT_X = 57F;
    protected static final float CONTENT_RIGHT = DESIGN_W - CONTENT_X;
    protected static final float CONTENT_W = CONTENT_RIGHT - CONTENT_X;
    protected static final float ROW_HEIGHT = 34F;

    /** 页面骨架：标题 / 副标题灰字 / 第一个分组。 */
    protected static final float TITLE_Y = 22F;
    protected static final float SUB_Y = 56F;
    protected static final float SUB_LINE = 15F;
    protected static final float SECTION_Y = 100F;
    protected static final float FOOTER_Y = 512F;

    // ===== 配色 =====
    protected static final int COLOR_SIDEBAR = 0x99000000;
    protected static final int COLOR_ACTIVE = 0xFF0A7A1E;
    protected static final int COLOR_INACTIVE = 0x40000000;
    protected static final int COLOR_BORDER = 0xFF9A9A9A;
    protected static final int COLOR_TEXT = 0xFFFFFFFF;
    protected static final int COLOR_SUB = 0xFFD8D8D8;
    protected static final int COLOR_WARN = 0xFFFFB27A;
    protected static final int COLOR_TOGGLE_ON = 0xFF0A7A1E;
    protected static final int COLOR_TOGGLE_OFF = 0xFFB0B0B0;
    protected static final int COLOR_ROW = 0x33FFFFFF;
    protected static final int COLOR_ROW_HOVER = 0x66FFFFFF;
    protected static final int COLOR_ROW_SELECTED = 0xFF0A7A1E;
    protected static final int COLOR_FIELD_BG = 0xFF161616;
    protected static final int COLOR_FIELD_BORDER = 0xFF8A8A8A;
    protected static final int COLOR_FIELD_FOCUS = 0xFFFFFFFF;

    // ===== 字号（设计稿 px）=====
    protected static final float FONT_TITLE = 24F;
    protected static final float FONT_SECTION = 17F;
    protected static final float FONT_BODY = 15F;
    protected static final float FONT_ROW = 13F;
    protected static final float FONT_SUB = 10F;
    protected static final float FONT_TINY = 9F;
    protected static final float FONT_FOOTER = 9F;

    private final Screen parentScreen;
    protected float scale = 1F;
    protected float originX;
    protected float originY;

    // ===== 单行输入框 =====
    private Object fieldId;
    private String fieldValue = "";
    private int fieldCursor;
    private int fieldMax = 256;
    private float fieldDesignFont = FONT_BODY;
    private float fieldX;
    private float fieldY;
    private float fieldW;
    private float fieldH;
    private float fieldScroll;
    private int scissorDepth;
    private Consumer<String> fieldCommit;

    protected TrdmScreenBase(Screen parent, Component title) {
        super(title);
        this.parentScreen = parent;
    }

    /** 左侧栏目（返回 null 表示这个界面没有栏目栏，比如方块设置页）。 */
    protected String[] pages() {
        return null;
    }

    /** 当前栏目序号。 */
    protected int currentPage() {
        return 0;
    }

    /** 画当前页内容（设计坐标，原点 = 设计稿左上角）。 */
    protected abstract void renderPage(GuiGraphics guiGraphics, float mouseDesignX, float mouseDesignY);

    /** 当前页点击（设计坐标）；返回 true 表示已处理。 */
    protected boolean clickPage(float mx, float my) {
        return false;
    }

    // ===== 生命周期 =====

    @Override
    protected void init() {
        final float fit = Math.min(width / DESIGN_W, height / DESIGN_H);
        scale = Math.max(MIN_SCALE, fit);
        originX = (width - DESIGN_W * scale) / 2F;
        originY = (height - DESIGN_H * scale) / 2F;
        fieldId = null;
        fieldCommit = null;
        onInit();
    }

    /** 页面初始化（缩放算好之后调用；改窗口大小也会走一遍）。 */
    protected void onInit() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        scissorDepth = 0;
        final float mx = designX(mouseX);
        final float my = designY(mouseY);

        final String[] pages = pages();
        if (pages != null) {
            rect(guiGraphics, 0, 0, SIDEBAR_W, DESIGN_H, COLOR_SIDEBAR);
            for (int index = 0; index < pages.length; index++) {
                final float top = 8F + index * (SIDEBAR_BTN_H + 2F);
                final boolean active = index == currentPage();
                final boolean hovered = mx >= 0 && mx < SIDEBAR_W && my >= top && my < top + SIDEBAR_BTN_H;
                button(guiGraphics, 0, top, SIDEBAR_W, SIDEBAR_BTN_H, active, hovered);
            }
        }

        renderPage(guiGraphics, mx, my);
        text(guiGraphics, Component.translatable("gui.trdm.config.footer", versionString()),
                CONTENT_X, FOOTER_Y, FONT_FOOTER, COLOR_SUB);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            final float mx = designX(mouseX);
            final float my = designY(mouseY);
            if (fieldId != null) {
                if (hit(mx, my, fieldX, fieldY, fieldW, fieldH)) {
                    return true;
                }
                endField(true);
            }
            if (clickPage(mx, my)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (fieldId != null) {
            if (codePoint >= ' ' && codePoint != 0xA7 && fieldValue.length() < fieldMax) {
                fieldValue = fieldValue.substring(0, fieldCursor) + codePoint + fieldValue.substring(fieldCursor);
                fieldCursor++;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (fieldId == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        switch (keyCode) {
            case 259 -> {   // 退格
                if (fieldCursor > 0) {
                    fieldValue = fieldValue.substring(0, fieldCursor - 1) + fieldValue.substring(fieldCursor);
                    fieldCursor--;
                }
            }
            case 261 -> {   // 删除
                if (fieldCursor < fieldValue.length()) {
                    fieldValue = fieldValue.substring(0, fieldCursor) + fieldValue.substring(fieldCursor + 1);
                }
            }
            case 263 -> fieldCursor = Math.max(0, fieldCursor - 1);                       // ←
            case 262 -> fieldCursor = Math.min(fieldValue.length(), fieldCursor + 1);     // →
            case 268 -> fieldCursor = 0;                                                 // Home
            case 269 -> fieldCursor = fieldValue.length();                               // End
            case 257, 335 -> endField(true);                                             // 回车提交
            case 256 -> endField(false);                                                 // Esc 取消
            default -> {
                // 输入中别的键一律吞掉，免得 WASD / E 触发别的动作
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        net.minecraft.client.Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== 设计坐标换算 =====

    protected float designX(double mouseX) {
        return (float) ((mouseX - originX) / scale);
    }

    protected float designY(double mouseY) {
        return (float) ((mouseY - originY) / scale);
    }

    protected static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected void rect(GuiGraphics guiGraphics, float x, float y, float w, float h, int color) {
        guiGraphics.fill(Math.round(originX + x * scale), Math.round(originY + y * scale),
                Math.round(originX + (x + w) * scale), Math.round(originY + (y + h) * scale), color);
    }

    protected void scissorOn(GuiGraphics guiGraphics, float x, float y, float w, float h) {
        if (scissorDepth++ == 0) {
            guiGraphics.enableScissor(Math.round(originX + x * scale), Math.round(originY + y * scale),
                    Math.round(originX + (x + w) * scale), Math.round(originY + (y + h) * scale));
        }
    }

    protected void scissorOff(GuiGraphics guiGraphics) {
        if (scissorDepth > 0 && --scissorDepth == 0) {
            guiGraphics.disableScissor();
        }
    }

    // ===== 公共控件（设计坐标）=====

    /** MC 风格方按钮：1px 边框 + 底色，选中绿、悬停提亮。 */
    protected void button(GuiGraphics guiGraphics, float x, float y, float w, float h, boolean active, boolean hovered) {
        rect(guiGraphics, x, y, w, h, COLOR_BORDER);
        rect(guiGraphics, x + 1F, y + 1F, w - 2F, h - 2F,
                active ? COLOR_ACTIVE : (hovered ? COLOR_ROW_HOVER : COLOR_INACTIVE));
    }

    /** 带文字的方按钮。 */
    protected void buttonText(GuiGraphics guiGraphics, float x, float y, float w, float h, Component label,
                              boolean active, boolean hovered, float designFont) {
        button(guiGraphics, x, y, w, h, active, hovered);
        textCentred(guiGraphics, label, x, w, y + (h - designFont) / 2F - 1F, designFont, COLOR_TEXT);
    }

    /** 开关尺寸（设计 px）。 */
    protected static final float TOGGLE_W = 44F;
    protected static final float TOGGLE_H = 24F;

    /** 开关：槽 + 圆点，开＝绿底且在右。 */
    protected void toggle(GuiGraphics guiGraphics, float x, float y, boolean on) {
        rect(guiGraphics, x, y, TOGGLE_W, TOGGLE_H, on ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF);
        rect(guiGraphics, x + (on ? TOGGLE_W - 22F : 2F), y + 2F, 20F, TOGGLE_H - 4F, 0xFFF2F2F2);
    }

    /** 勾选框：开＝绿底 + 白勾，关＝灰框。 */
    protected void checkbox(GuiGraphics guiGraphics, float x, float y, boolean on) {
        rect(guiGraphics, x, y, 14F, 14F, on ? COLOR_TOGGLE_ON : COLOR_FIELD_BORDER);
        if (on) {
            rect(guiGraphics, x + 3F, y + 6F, 3F, 3F, 0xFFFFFFFF);
            rect(guiGraphics, x + 5F, y + 8F, 7F, 3F, 0xFFFFFFFF);
        }
    }

    /** 列表行：整行底色（选中绿 / 悬停提亮 / 普通）。返回行顶 y。 */
    protected float listRow(GuiGraphics guiGraphics, int index, float listTop, boolean selected, boolean hovered) {
        final float top = listTop + index * ROW_HEIGHT;
        rect(guiGraphics, CONTENT_X, top, CONTENT_W, ROW_HEIGHT - 1F,
                selected ? COLOR_ROW_SELECTED : (hovered ? COLOR_ROW_HOVER : COLOR_ROW));
        return top;
    }

    /** 一行可点的条目（自定高度）。 */
    protected void row(GuiGraphics guiGraphics, float x, float y, float w, float h, boolean selected, boolean hovered) {
        rect(guiGraphics, x, y, w, h, selected ? COLOR_ROW_SELECTED : (hovered ? COLOR_ROW_HOVER : COLOR_ROW));
    }

    /** 列表右侧的滚动条（内容不超过一屏就不画）。 */
    protected void scrollbar(GuiGraphics guiGraphics, float x, float y, float h, float total, float view,
                             float scroll) {
        if (total <= view) {
            return;
        }
        rect(guiGraphics, x, y, 4F, h, 0x40FFFFFF);
        final float thumb = Math.max(24F, h * view / total);
        final float max = total - view;
        final float thumbY = y + (h - thumb) * (max <= 0F ? 0F : Math.min(1F, scroll / max));
        rect(guiGraphics, x, thumbY, 4F, thumb, 0xC8FFFFFF);
    }

    /** 分组标题（白）。 */
    protected void section(GuiGraphics guiGraphics, String key, float y) {
        text(guiGraphics, Component.translatable(key), CONTENT_X, y, FONT_SECTION, COLOR_TEXT);
    }

    /** 页面标题：太长（英文名）就自动缩到左侧一半宽，免得压到右上角的按钮。 */
    protected void title(GuiGraphics guiGraphics, Component label) {
        final float limit = CONTENT_W * 0.55F;
        float designPx = FONT_TITLE;
        final float measured = textWidth(label, designPx);
        if (measured > limit) {
            designPx = designPx * limit / measured;
        }
        text(guiGraphics, label, CONTENT_X, TITLE_Y, designPx, COLOR_TEXT);
    }

    protected void section(GuiGraphics guiGraphics, Component label, float y) {
        text(guiGraphics, label, CONTENT_X, y, FONT_SECTION, COLOR_TEXT);
    }

    /** 灰字说明。 */
    protected void hint(GuiGraphics guiGraphics, Component component, float x, float y) {
        text(guiGraphics, component, x, y, FONT_SUB, COLOR_SUB);
    }

    protected void hint(GuiGraphics guiGraphics, String key, float x, float y) {
        hint(guiGraphics, Component.translatable(key), x, y);
    }

    protected void text(GuiGraphics guiGraphics, Component component, float x, float y, float designPx, int color) {
        final float textScale = designPx * scale / 8F;
        final PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(originX + x * scale, originY + y * scale, 0F);
        pose.scale(textScale, textScale, 1F);
        guiGraphics.drawString(font, component, 0, 0, color, false);
        pose.popPose();
    }

    protected float textWidth(Component component, float designPx) {
        return font.width(component) * designPx / 8F;
    }

    /** 右对齐（right = 右边界）。 */
    protected void textRight(GuiGraphics guiGraphics, Component component, float right, float y, float designPx,
                             int color) {
        text(guiGraphics, component, right - textWidth(component, designPx), y, designPx, color);
    }

    /** 在 [left, left+w] 里居中。 */
    protected void textCentred(GuiGraphics guiGraphics, Component component, float left, float w, float y,
                               float designPx, int color) {
        text(guiGraphics, component, left + (w - textWidth(component, designPx)) / 2F, y, designPx, color);
    }

    protected void centred(GuiGraphics guiGraphics, Component component, float centreX, float y, float designPx,
                           int color) {
        text(guiGraphics, component, centreX - textWidth(component, designPx) / 2F, y, designPx, color);
    }

    protected void image(GuiGraphics guiGraphics, ResourceLocation texture, float x, float y, float w, float h) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(originX + x * scale, originY + y * scale, 0F);
        guiGraphics.pose().scale(scale, scale, 1F);
        guiGraphics.blit(texture, 0, 0, 0F, 0F, Math.round(w), Math.round(h), Math.round(w), Math.round(h));
        guiGraphics.pose().popPose();
    }

    protected String versionString() {
        return FabricLoader.getInstance().getModContainer("trdm")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    // ===== 单行输入框（设计坐标）=====

    protected boolean isEditing() {
        return fieldId != null;
    }

    protected boolean fieldActive(Object id) {
        return fieldId != null && fieldId.equals(id);
    }

    /** 正在编辑的实时文本（没在编辑返回 null），用来做边输边过滤。 */
    protected String editingValue() {
        return fieldId == null ? null : fieldValue;
    }

    /**
     * 激活一个输入框。
     *
     * @param onCommit 回车 / 点别处时回调（Esc 取消时不回调）
     */
    protected void beginField(Object id, float x, float y, float w, float h, String initial, int maxLength,
                              float designFont, Consumer<String> onCommit) {
        fieldId = id;
        fieldValue = initial == null ? "" : initial;
        fieldCursor = fieldValue.length();
        fieldMax = Math.max(1, maxLength);
        fieldDesignFont = designFont;
        fieldX = x;
        fieldY = y;
        fieldW = w;
        fieldH = h;
        fieldScroll = 0F;
        fieldCommit = onCommit;
    }

    /** 结束输入：commit = true 保存，false 丢弃。 */
    protected void endField(boolean commit) {
        if (fieldId == null) {
            return;
        }
        final Consumer<String> callback = fieldCommit;
        final String value = fieldValue;
        fieldId = null;
        fieldCommit = null;
        fieldScroll = 0F;
        if (commit && callback != null) {
            callback.accept(value);
        }
    }

    /**
     * 画输入框。{@code value} 是没在编辑时显示的内容（自己的状态），正在编辑时改显示实时输入值。
     */
    protected void field(GuiGraphics guiGraphics, Object id, float x, float y, float w, float h, float designFont,
                         String value) {
        final boolean active = fieldActive(id);
        rect(guiGraphics, x, y, w, h, active ? COLOR_FIELD_FOCUS : COLOR_FIELD_BORDER);
        rect(guiGraphics, x + 1F, y + 1F, w - 2F, h - 2F, COLOR_FIELD_BG);
        final float pad = 9F;
        final float textY = y + (h - designFont) / 2F - 1F;
        final String shown = active ? fieldValue : (value == null ? "" : value);
        if (!active) {
            scissorOn(guiGraphics, x + 2F, y + 2F, w - 4F, h - 4F);
            text(guiGraphics, Component.literal(shown), x + pad, textY, designFont, COLOR_TEXT);
            scissorOff(guiGraphics);
            return;
        }
        // 编辑中：光标保持可见，文字整段跟着滚
        final float cursorX = textWidth(Component.literal(fieldValue.substring(0, Math.min(fieldCursor,
                fieldValue.length()))), designFont);
        final float inner = w - pad * 2F;
        fieldScroll = Math.max(0F, Math.min(fieldScroll, Math.max(0F, cursorX - inner + 6F)));
        if (cursorX - fieldScroll > inner) {
            fieldScroll = cursorX - inner;
        }
        if (cursorX < fieldScroll) {
            fieldScroll = cursorX;
        }
        scissorOn(guiGraphics, x + 2F, y + 2F, w - 4F, h - 4F);
        text(guiGraphics, Component.literal(shown), x + pad - fieldScroll, textY, designFont, COLOR_TEXT);
        rect(guiGraphics, x + pad - fieldScroll + cursorX, y + h / 2F - designFont * 0.55F, 2F, designFont * 1.1F,
                0xFFFFFFFF);
        scissorOff(guiGraphics);
    }
}
