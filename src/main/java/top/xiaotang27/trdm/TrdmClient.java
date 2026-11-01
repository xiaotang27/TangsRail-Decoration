package top.xiaotang27.trdm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import top.xiaotang27.trdm.block.BlockTrdmPSDControl;
import top.xiaotang27.trdm.block.BlockTrdmPSDDoor;
import top.xiaotang27.trdm.block.BlockTrdmPSDGlass;
import top.xiaotang27.trdm.block.BlockTrdmPSDGlassEnd;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.client.TrdmControlClient;
import top.xiaotang27.trdm.render.RenderTrdmPSDDoor;
import top.xiaotang27.trdm.render.RenderTrdmPSDTop;
import top.xiaotang27.trdm.render.RenderTrdmBigStationSign;
import top.xiaotang27.trdm.render.RenderTrdmHungBigSign;
import top.xiaotang27.trdm.render.RenderTrdmPis;
import top.xiaotang27.trdm.render.RenderTrdmStationSign;

/**
 * 仅客户端入口。客户端专用类只能在这里引用，否则服务端加载主类时会 ClassNotFound。
 */
public class TrdmClient implements ClientModInitializer {

    /** 打开 {@code -Dtrdm.debugSign=true} 会把「蹲下 + 刷子」的命中情况打进日志。 */
    private static final boolean DEBUG = Boolean.getBoolean("trdm.debugSign");

    @Override
    public void onInitializeClient() {
        // 门扇：TRD 自己的渲染器（RenderTrdmPSDDoor）

        // /trdm config → 通用设置界面（客户端命令，装了 ModMenu 时也会多一个入口）
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher.register(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("trdm").then(
                                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("config").executes(context -> {
                                    top.xiaotang27.trdm.Trdm.LOGGER.info("[config-screen] 命令触发");
                                    final net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                                    // 和 MTR 一样把开界面丢到客户端线程队列里，避免在命令回调整帧中直接切屏
                                    client.execute(() -> {
                                        try {
                                            client.setScreen(new top.xiaotang27.trdm.client.TrdmConfigScreen(null));
                                            top.xiaotang27.trdm.Trdm.LOGGER.info("[config-screen] setScreen 完成，当前界面: {}", client.screen);
                                        } catch (Throwable e) {
                                            top.xiaotang27.trdm.Trdm.LOGGER.error("[config-screen] 打开界面失败", e);
                                        }
                                    });
                                    return 1;
                                }))));
        BlockEntityRendererRegistry.register(Trdm.PSD_DOOR_1_BE, context -> new RenderTrdmPSDDoor<>(null, 0));

        // 通用设置快捷键：默认不绑定按键，玩家在「选项-控制-按键绑定」里自己设一个；单人存档也能用
        final net.minecraft.client.KeyMapping configKey = new net.minecraft.client.KeyMapping(
                "key.trdm.config", -1, "key.categories.trdm");
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(configKey);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKey.consumeClick()) {
                client.setScreen(new top.xiaotang27.trdm.client.TrdmConfigScreen(null));
            }
        });

        // 门楣：线路图 / 站名 / 方向箭头 / 站台色带 + 玻璃上的装饰带
        BlockEntityRendererRegistry.register(Trdm.PSD_TOP_BE, context -> new RenderTrdmPSDTop(null));

        // 站名牌：本站 + 下一站 + 线路色带
        BlockEntityRendererRegistry.register(Trdm.STATION_SIGN_BE, context -> new RenderTrdmStationSign<>());

        // 大站名牌：深灰横牌（后缀和无障碍电梯牌由方块实体决定）
        BlockEntityRendererRegistry.register(Trdm.BIG_SIGN_1_BE, context -> new RenderTrdmBigStationSign<>());

        // 悬挂式站外站名牌：同一个内容，吊在天花板下、单面有字、自发光
        BlockEntityRendererRegistry.register(Trdm.HUNG_SIGN_BE, context -> new RenderTrdmHungBigSign<>());

        // PIS 乘客信息屏：16:9，实时到站数据 + 连续滚动的跑马灯
        BlockEntityRendererRegistry.register(Trdm.PIS_BE, context -> new RenderTrdmPis<>());

        BlockRenderLayerMap.INSTANCE.putBlock(Trdm.PSD_DOOR_1, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(Trdm.PSD_GLASS_1, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(Trdm.PSD_GLASS_END_1, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(Trdm.PSD_TOP, RenderType.cutout());

        // 「蹲下 + 刷子右键屏蔽门」→ 外观设置。
        // 走 UseBlockCallback 而不是只挂在方块的 use 上：这个回调在「方块自己的 use」之前触发，
        // 门被刷子/其他逻辑先吃掉的情况也能拦到；返回 SUCCESS 后原版不会再把这个交互发给服务端，
        UseBlockCallback.EVENT.register(TrdmClient::onUseBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (!world.isClientSide || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        final BlockPos pos = hitResult.getBlockPos();
        final Block block = world.getBlockState(pos).getBlock();
        final boolean psd = block instanceof BlockTrdmPSDDoor || block instanceof BlockTrdmPSDGlass
                || block instanceof BlockTrdmPSDGlassEnd || block instanceof BlockTrdmPSDTop;
        if (!psd) {
            return InteractionResult.PASS;
        }
        if (DEBUG) {
            Trdm.LOGGER.info("[psd appearance] sneak + {} on {} ({})", player.getMainHandItem(), pos,
                    block.getClass().getSimpleName());
        }
        if (!BlockTrdmPSDControl.isHoldingBrush(player)) {
            return InteractionResult.PASS;
        }
        if (DEBUG) {
            Trdm.LOGGER.info("[psd appearance] opening appearance screen for {}", pos);
        }
        TrdmControlClient.openPsdAppearance(pos);
        return InteractionResult.SUCCESS;
    }
}
