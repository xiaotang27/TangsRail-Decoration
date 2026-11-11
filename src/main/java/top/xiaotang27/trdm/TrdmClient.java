package top.xiaotang27.trdm;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
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
import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate;
import top.xiaotang27.trdm.block.BlockTrdmPSDGlass;
import top.xiaotang27.trdm.block.BlockTrdmPSDGlassEnd;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.client.TrdmControlClient;
import top.xiaotang27.trdm.client.TrdmEndGateDrag;
import top.xiaotang27.trdm.client.TrdmEndGateHud;
import top.xiaotang27.trdm.client.TrdmMtrFonts;
import top.xiaotang27.trdm.render.RenderTrdmPSDDoor;
import top.xiaotang27.trdm.render.RenderTrdmPSDEndGate;
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

    /**
     * 「打开通用设置」的请求标记：聊天命令不能当场 {@code setScreen}，见命令注册处的注释。
     * 标记在 {@code END_CLIENT_TICK} 里消费，那时聊天界面已经关掉，不会再被它覆盖。
     */
    private static boolean configScreenRequested;

    @Override
    public void onInitializeClient() {
        // 票务交易结果接收
        top.xiaotang27.trdm.client.TicketClientNetwork.register();
        // 门扇：TRD 自己的渲染器（RenderTrdmPSDDoor）

        // /trdm config → 通用设置界面（客户端命令，装了 ModMenu 时也会多一个入口）
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher.register(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("trdm").then(
                                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("config").executes(context -> {
                                    top.xiaotang27.trdm.Trdm.LOGGER.info("[config-screen] 命令触发");
                                    // 这里绝对不能直接 setScreen，也不能用 client.execute：
                                    // 客户端命令是 ChatScreen.keyPressed 里同步执行的，执行完 ChatScreen 还会
                                    // setScreen(null) 把刚打开的界面关掉；而 Minecraft#execute 在渲染线程上
                                    // （非任务重入时）是立即执行，所以照样会被关掉——原来的 bug 就在这里。
                                    // 只记下请求，等本 tick 末尾聊天界面关掉后再开。
                                    configScreenRequested = true;
                                    return 1;
                                }))));
        BlockEntityRendererRegistry.register(Trdm.PSD_DOOR_1_BE, context -> new RenderTrdmPSDDoor<>(null, 0));

        // 通用设置快捷键：默认不绑定按键，玩家在「选项-控制-按键绑定」里自己设一个；单人存档也能用
        final net.minecraft.client.KeyMapping configKey = new net.minecraft.client.KeyMapping(
                "key.trdm.config", -1, "key.categories.trdm");
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(configKey);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 命令只负责排队，这里才是真正开界面：此时 ChatScreen 已经 setScreen(null) 过
            if (configScreenRequested) {
                configScreenRequested = false;
                openConfigScreen(client);
            }
            while (configKey.consumeClick()) {
                openConfigScreen(client);
            }
            // 端门「按住右键拖动调开度」的状态机
            TrdmEndGateDrag.tick(client);
            // 票务系统若使用游戏内时间，每 tick 同步一次
            if (client.level != null) {
                top.xiaotang27.trdm.ticket.TrdmClock.updateGameTime(client.level);
            }
            // 把 TRDM 字体灌进 MTR 的牌面缓存（MTR 初始化好 / 资源重载之后各来一次）
            TrdmMtrFonts.tick();
        });

        // 资源重载（F3+T、换资源包）之后，等下一 tick 再把字体灌回 MTR 的缓存
        net.fabricmc.fabric.api.resource.ResourceManagerHelper
                .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public net.minecraft.resources.ResourceLocation getFabricId() {
                        return Trdm.id("mtr_font_sync");
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        TrdmMtrFonts.requestSync();
                    }
                });

        // 门楣：线路图 / 站名 / 方向箭头 / 站台色带 + 玻璃上的装饰带
        BlockEntityRendererRegistry.register(Trdm.PSD_TOP_BE, context -> new RenderTrdmPSDTop(null));

        // 端门：关着走静态玻璃模型，开着由这个渲染器把「整段」玻璃当一扇大门摆开 90°
        BlockEntityRendererRegistry.register(Trdm.PSD_END_GATE_BE, context -> new RenderTrdmPSDEndGate<>(null));

        // 端门拖动时屏幕右侧的开度指示条（屏幕空间 UI）
        TrdmEndGateHud.register();

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
        // 端门也是玻璃：漏了这条会按 solid 画，透明像素变成白板
        BlockRenderLayerMap.INSTANCE.putBlock(Trdm.PSD_END_GATE, RenderType.cutout());
        // 技术方块（端门门扇模型）也按玻璃层画，透明处才不会变成实心白块
        BlockRenderLayerMap.INSTANCE.putBlock(Trdm.PSD_END_GATE_MODELS, RenderType.cutout());

        // 「蹲下 + 刷子右键屏蔽门」→ 外观设置。
        // 走 UseBlockCallback 而不是只挂在方块的 use 上：这个回调在「方块自己的 use」之前触发，
        // 门被刷子/其他逻辑先吃掉的情况也能拦到；返回 SUCCESS 后原版不会再把这个交互发给服务端，
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            top.xiaotang27.trdm.render.TrdmHeaderTextures.shutdown();
            top.xiaotang27.trdm.render.TrdmPisVideo.clear();
        });

        UseBlockCallback.EVENT.register(TrdmClient::onUseBlock);
    }

    /** 真正把通用设置界面推到最前面（只在客户端 tick 里调用）。 */
    private static void openConfigScreen(net.minecraft.client.Minecraft client) {
        try {
            client.setScreen(new top.xiaotang27.trdm.client.TrdmConfigScreen(null));
            Trdm.LOGGER.info("[config-screen] 已打开界面，当前界面: {}，画布 {}x{}", client.screen,
                    client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        } catch (Throwable e) {
            Trdm.LOGGER.error("[config-screen] 打开界面失败", e);
        }
    }

    private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (!world.isClientSide) {
            return InteractionResult.PASS;
        }
        final BlockPos pos = hitResult.getBlockPos();
        final Block block = world.getBlockState(pos).getBlock();
        // 端门：右键按下先记一次「候选拖动」——
        // 没拖动就松手 = 短按（开/关），按住向下拖 = 调开度（状态机见 TrdmEndGateDrag）。
        if (!player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND
                && block instanceof BlockTrdmPSDEndGate
                && !BlockTrdmPSDControl.isHoldingBrush(player)) {
            TrdmEndGateDrag.begin(pos, world.getBlockState(pos));
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        final boolean psd = block instanceof BlockTrdmPSDDoor || block instanceof BlockTrdmPSDGlass
                || block instanceof BlockTrdmPSDGlassEnd || block instanceof BlockTrdmPSDTop
                || block instanceof BlockTrdmPSDEndGate;
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
