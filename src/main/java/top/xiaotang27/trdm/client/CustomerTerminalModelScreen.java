package top.xiaotang27.trdm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.block.BlockCustomerServiceTerminal;
import top.xiaotang27.trdm.block.BlockCustomerServiceTerminal.Model;

/** 刷子右键客服终端后的模型选择界面。 */
public final class CustomerTerminalModelScreen extends Screen {

    private static final int GREEN = 0xFF0A7A0A;
    private static final int GREY = 0xFF9E9E9E;
    private static final int INK = 0xFFFFFFFF;

    private final BlockPos pos;
    private Model current;
    private Model hovered;
    private float previewAngle;

    public CustomerTerminalModelScreen(BlockPos pos, Model current) {
        super(Component.translatable("gui.trdm.terminal_model.title"));
        this.pos = pos;
        this.current = current == null ? Model.ONE : current;
    }

    private int centreX() {
        return this.width / 2;
    }

    private int centreY() {
        return Math.round(this.height * 0.43F);
    }

    private int outerRadius() {
        return Math.round(Math.min(this.width, this.height) * 0.30F);
    }

    private int innerRadius() {
        return Math.round(outerRadius() * 0.62F);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE62A2A2A);
        previewAngle += 0.7F;
        final int cx = centreX();
        final int cy = centreY();
        final int outer = outerRadius();
        final int inner = innerRadius();
        hovered = sideAt(mouseX, mouseY, cx, cy, inner, outer);

        drawHalf(graphics, cx, cy, inner, outer, 90, 270,
                (hovered != null ? hovered == Model.ONE : current == Model.ONE) ? GREEN : GREY);
        drawHalf(graphics, cx, cy, inner, outer, -90, 90,
                (hovered != null ? hovered == Model.TWO : current == Model.TWO) ? GREEN : GREY);

        final float labelY = cy - 7F;
        graphics.drawString(this.font, "1", Math.round(cx - (inner + outer) / 2F - 4F),
                Math.round(labelY), 0xFFFFFFFF);
        graphics.drawString(this.font, "2", Math.round(cx + (inner + outer) / 2F - 4F),
                Math.round(labelY), 0xFFFFFFFF);

        drawPreview(graphics, hovered == null ? current : hovered, cx, cy);

        final PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(3.0F, 3.0F, 1F);
        graphics.drawString(this.font, this.title, 28, 20, INK);
        pose.popPose();

        final String selected = Component.translatable("gui.trdm.terminal_model.selected",
                Component.translatable(current == Model.ONE
                        ? "gui.trdm.terminal_model.one" : "gui.trdm.terminal_model.two").getString())
                .getString();
        graphics.drawString(this.font, selected,
                Math.round((this.width - this.font.width(selected)) / 2F),
                Math.round(cy + outer + 22F), INK);
        final String apply = Component.translatable("gui.trdm.terminal_model.apply").getString();
        graphics.drawString(this.font, apply,
                Math.round((this.width - this.font.width(apply)) / 2F),
                Math.round(cy + outer + 40F), 0xFFD0D0D0);

        final String version = "TangsRail Decoration Version " + FabricLoader.getInstance()
                .getModContainer("trdm").map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        graphics.drawString(this.font, version, 55, this.height - 28, 0xFFB8B8B8);
    }

    private Model sideAt(double mouseX, double mouseY, int cx, int cy, int inner, int outer) {
        final double dx = mouseX - cx;
        final double dy = mouseY - cy;
        final double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < inner || distance > outer) {
            return null;
        }
        return dx < 0 ? Model.ONE : Model.TWO;
    }

    private void drawHalf(GuiGraphics graphics, int cx, int cy, int inner, int outer,
                          double startDeg, double endDeg, int color) {
        final Matrix4f matrix = graphics.pose().last().pose();
        final Tesselator tesselator = Tesselator.getInstance();
        final BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        final int segments = 96;
        final float r = ((color >> 16) & 0xFF) / 255F;
        final float g = ((color >> 8) & 0xFF) / 255F;
        final float b = (color & 0xFF) / 255F;
        final float a = ((color >> 24) & 0xFF) / 255F;
        for (int index = 0; index < segments; index++) {
            final double t0 = Math.toRadians(startDeg + (endDeg - startDeg) * index / segments);
            final double t1 = Math.toRadians(startDeg + (endDeg - startDeg) * (index + 1) / segments);
            final float ox0 = cx + (float) Math.cos(t0) * outer;
            final float oy0 = cy + (float) Math.sin(t0) * outer;
            final float ox1 = cx + (float) Math.cos(t1) * outer;
            final float oy1 = cy + (float) Math.sin(t1) * outer;
            final float ix0 = cx + (float) Math.cos(t0) * inner;
            final float iy0 = cy + (float) Math.sin(t0) * inner;
            final float ix1 = cx + (float) Math.cos(t1) * inner;
            final float iy1 = cy + (float) Math.sin(t1) * inner;
            buffer.vertex(matrix, ox0, oy0, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, ix0, iy0, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, ox1, oy1, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, ox1, oy1, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, ix0, iy0, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, ix1, iy1, 0).color(r, g, b, a).endVertex();
        }
        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawPreview(GuiGraphics graphics, Model model, int cx, int cy) {
        final BlockState state = Trdm.CUSTOMER_SERVICE_TERMINAL.defaultBlockState()
                .setValue(BlockCustomerServiceTerminal.MODEL, model)
                .setValue(BlockCustomerServiceTerminal.FACING, Direction.NORTH);
        final float scale = model == Model.ONE ? 190F : 78F;
        final float height = model == Model.ONE ? 8.8347F / 16F : 2F;
        final PoseStack pose = graphics.pose();
        RenderSystem.enableDepthTest();
        pose.pushPose();
        pose.translate(cx, cy, 260F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(18F));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(previewAngle));
        pose.translate(-0.5F, -height / 2F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose,
                graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        graphics.flush();
        RenderSystem.disableDepthTest();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 0 && mouseX < this.width
                && mouseY >= 0 && mouseY < this.height) {
            final Model chosen = sideAt(mouseX, mouseY, centreX(), centreY(), innerRadius(), outerRadius());
            if (chosen != null) {
                current = chosen;
                TicketClientNetwork.setTerminalModel(pos, chosen);
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
