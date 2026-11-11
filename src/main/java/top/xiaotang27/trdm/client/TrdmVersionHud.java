package top.xiaotang27.trdm.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 左上角版本与测试版提示。 */
public final class TrdmVersionHud {

    private static final int X = 6;
    private static final int Y = 6;

    private TrdmVersionHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((GuiGraphics graphics, float tickDelta) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        final String version = FabricLoader.getInstance().getModContainer("trdm")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("DEV");
        final Component line1 = Component.translatable("gui.trdm.hud.version", version);
        final Component line2 = Component.translatable("gui.trdm.hud.test_warning");
        final var font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.drawString(font, line1, X, Y, 0xFFFFFFFF, true);
        graphics.drawString(font, line2, X, Y + 12, 0xFFFFC85A, true);
    }
}
