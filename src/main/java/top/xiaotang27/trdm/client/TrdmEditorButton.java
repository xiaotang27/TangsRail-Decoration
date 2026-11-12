package top.xiaotang27.trdm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** TRDM 风格的扁平按钮：深色半透明底、浅灰描边、悬停绿色。 */
public final class TrdmEditorButton extends Button {

    public TrdmEditorButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        final int border = 0xFF9A9A9A;
        final int fill = !this.active ? 0x66000000
                : (this.isHoveredOrFocused() ? 0xCC0A7A1E : 0x99000000);
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                this.getY() + this.getHeight(), border);
        graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.getWidth() - 1,
                this.getY() + this.getHeight() - 1, fill);
        graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                this.getX() + this.getWidth() / 2,
                this.getY() + (this.getHeight() - 8) / 2, 0xFFFFFFFF);
    }
}
