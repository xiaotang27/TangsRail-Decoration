package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.fare.script.ScriptNode;

/** 黄色注释浮窗的文字编辑。 */
public final class CommentEditScreen extends Screen {

    private final Screen parent;
    private final ScriptNode node;
    private EditBox field;

    CommentEditScreen(Screen parent, ScriptNode node) {
        super(Component.translatable("gui.trdm.script.comment.title"));
        this.parent = parent;
        this.node = node;
    }

    @Override
    protected void init() {
        field = new EditBox(this.font, this.width / 2 - 150, 76, 300, 20,
                Component.translatable("gui.trdm.script.comment.title"));
        field.setMaxLength(240);
        field.setValue(node.fields.getOrDefault("text", ""));
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.editor.save"),
                        button -> save())
                .bounds(this.width / 2 - 104, 112, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.back"),
                        button -> onClose())
                .bounds(this.width / 2 + 4, 112, 100, 20).build());
    }

    private void save() {
        node.fields.put("text", field.getValue().trim());
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 28, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.trdm.script.comment.hint"),
                this.width / 2 - 150, 56, 0xA0A0A0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
